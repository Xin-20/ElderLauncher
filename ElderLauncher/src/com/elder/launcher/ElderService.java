package com.elder.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍服务，负责两件事：
 * 1. 熄屏复位：熄屏时回到“常用联系”主页并关闭抖音后台；亮屏/解锁时兜底检查一次。
 * 2. 微信视频：自动打开微信 → 搜索联系人 → 进入聊天 → “+” → 视频通话 → 视频通话。
 */
public class ElderService extends AccessibilityService {
    private static final String TAG = "ElderService";
    public static volatile ElderService instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Config cfg;
    private String lastPkg = "";
    private String lastClass = "";

    // ------------------------------------------------------------------ 生命周期
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        cfg = new Config(this);
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        Log.i(TAG, "service connected");
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        cancelWeChat(false);
        cancelRecording(false);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String p = e.getPackageName().toString();
            if (!p.contains("inputmethod") && !Logic.isSystemUi(p)) {
                lastPkg = p;
                CharSequence cls = e.getClassName();
                String c = cls == null ? "" : cls.toString();
                // 只记录“页面”级别的类名，忽略弹出的小部件
                if (!c.startsWith("android.widget.") && !c.startsWith("android.view.")) lastClass = c;
            }
        }
        if (wxStage != WX_IDLE) scheduleTick(300);
    }

    // ------------------------------------------------------------------ 熄屏复位
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) onScreenOff();
            else if (Intent.ACTION_SCREEN_ON.equals(a)) h.postDelayed(ElderService.this::checkForegroundAfterWake, 700);
            else if (Intent.ACTION_USER_PRESENT.equals(a)) h.postDelayed(ElderService.this::checkForegroundAfterWake, 400);
        }
    };

    boolean audioBusy() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am != null && am.getMode() != AudioManager.MODE_NORMAL;
        } catch (Exception e) {
            return false;
        }
    }

    void onScreenOff() {
        boolean enabled = cfg.resetEnabled();
        boolean busy = audioBusy();
        Log.i(TAG, "screen off: enabled=" + enabled + " busy=" + busy + " last=" + lastPkg);
        if (isRecording()) cancelRecording(false);
        if (!Logic.shouldResetOnScreenOff(enabled, busy)) return;
        cancelWeChat(false);
        goHome(false);
        // 等抖音退到后台后再结束它的进程（安卓 14 及以上系统不允许结束别的应用，届时靠“重新打开时清空界面”实现复位）
        h.postDelayed(this::killDouyin, 1200);
        h.postDelayed(this::killDouyin, 3000);
    }

    void checkForegroundAfterWake() {
        String fg = foregroundPkg();
        String cls = fg.equals(lastPkg) ? lastClass : "";
        boolean go = Logic.shouldResetOnScreenOn(cfg.resetEnabled(), audioBusy(), fg, cls, getPackageName());
        Log.i(TAG, "wake check fg=" + fg + " cls=" + cls + " reset=" + go);
        if (go) goHome(true);
    }

    String foregroundPkg() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return "com.android.systemui";
        } catch (Exception ignored) {}
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null && r.getPackageName() != null) {
                String p = r.getPackageName().toString();
                if (!p.contains("inputmethod")) return p;
            }
        } catch (Exception ignored) {}
        return lastPkg;
    }

    /** 回到主页。screenOn=true 时额外按一下系统“主页键”。 */
    void goHome(boolean screenOn) {
        SetupActivity.closeIfOpen(); // 配置页（草稿已保存）一并关掉，最近任务里也不留
        if (screenOn) {
            try { performGlobalAction(GLOBAL_ACTION_HOME); } catch (Exception ignored) {}
        }
        h.postDelayed(() -> {
            try {
                Intent i = Actions.elderHomeIntent(this);
                i.putExtra(MainActivity.EXTRA_RESET, true);
                startActivity(i);
            } catch (Exception e) {
                Log.w(TAG, "goHome failed", e);
            }
        }, screenOn ? 150 : 0);
    }

    // ------------------------------------------------------------------ 通话时：音量最大 + 免提
    private long callWatchUntil;
    private boolean callWatchPhone, callBoosted, speakerTried;
    private long inCallSince;

    /** 拨号/视频后 60 秒内盯着：一旦接通（进入通话状态），把通话音量拉满；电话还会确保打开免提。 */
    public void watchCall(boolean phone) {
        callWatchPhone = phone;
        callBoosted = false;
        speakerTried = false;
        inCallSince = 0;
        callWatchUntil = SystemClock.uptimeMillis() + 60_000;
        h.removeCallbacks(callWatchRunnable);
        h.postDelayed(callWatchRunnable, 800);
    }

    private final Runnable callWatchRunnable = new Runnable() {
        @Override
        public void run() {
            if (SystemClock.uptimeMillis() > callWatchUntil) return;
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int mode = am == null ? AudioManager.MODE_NORMAL : am.getMode();
            boolean inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION;
            if (inCall) {
                if (inCallSince == 0) inCallSince = SystemClock.uptimeMillis();
                if (!callBoosted) {
                    Actions.maxVolume(ElderService.this); // 通话中再设一次：有的手机免提/听筒音量是分开记的
                    callBoosted = true;
                    Log.i(TAG, "call boost: volume max, mode=" + mode);
                }
                // 等系统先按“免提拨号”切好声音通道（约 2 秒），还不是免提再自己点
                if (callWatchPhone && !speakerTried && SystemClock.uptimeMillis() - inCallSince > 2000) {
                    speakerTried = ensureSpeaker(am);
                }
                if (callBoosted && (!callWatchPhone || speakerTried)) {
                    // 过几秒再补一次音量（部分机型接通瞬间会重置音量）
                    h.postDelayed(() -> Actions.maxVolume(ElderService.this), 3000);
                    return;
                }
            }
            h.postDelayed(this, 700);
        }
    };

    /** 确保电话走扬声器：先看是否已是免提；不是就尝试系统接口，再尝试点通话界面上的“免提/扬声器”按钮。 */
    private boolean ensureSpeaker(AudioManager am) {
        try {
            if (am.isSpeakerphoneOn()) { Log.i(TAG, "speaker already on"); return true; }
        } catch (Exception ignored) {}
        AccessibilityNodeInfo root = null;
        try { root = getRootInActiveWindow(); } catch (Exception ignored) {}
        if (root == null || root.getPackageName() == null) return false;
        String pkg = root.getPackageName().toString();
        if (!Logic.isCallPackage(pkg) && !pkg.contains("dialer") && !pkg.contains("phone") && !pkg.contains("contacts")) return false;
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            String t = n.getText() == null ? "" : n.getText().toString();
            String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
            if (!Logic.isSpeakerLabel(t) && !Logic.isSpeakerLabel(d)) continue;
            AccessibilityNodeInfo target = n;
            for (int i = 0; i < 3 && target != null && !target.isClickable(); i++) target = target.getParent();
            if (target == null) target = n;
            if ((target.isCheckable() && target.isChecked()) || target.isSelected()) { Log.i(TAG, "speaker button already on"); return true; }
            if ((n.isCheckable() && n.isChecked()) || n.isSelected()) return true;
            boolean ok = target.isClickable() ? target.performAction(AccessibilityNodeInfo.ACTION_CLICK) : tapCenter(n);
            Log.i(TAG, "speaker button clicked ok=" + ok + " label=" + t + "/" + d);
            return ok;
        }
        return false; // 通话界面还没出来，下次再试
    }

    void killDouyin() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isInteractive() && Config.PKG_DOUYIN.equals(foregroundPkg())) return; // 已亮屏且在看抖音，不要杀
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) am.killBackgroundProcesses(Config.PKG_DOUYIN);
        } catch (Exception e) {
            Log.w(TAG, "kill douyin failed", e);
        }
    }

    // ------------------------------------------------------------------ 微信视频自动化
    // 每一步都有多种找法：先按文字/描述找，找不到再按界面位置结构找，最后按坐标点，
    // 并把全过程记进诊断日志；失败时自动保存微信界面结构，子女可在配置页一键导出发给开发者。
    static final int WX_IDLE = 0, WX_FIND_SEARCH = 1, WX_TYPE = 2, WX_PICK = 3,
            WX_CHAT_MORE = 4, WX_PANEL = 5, WX_SHEET = 6;
    private static final long WX_TIMEOUT = 50_000;
    public static final String DIAG_FILE = "wechat_diag.txt";

    private int wxStage = WX_IDLE;
    private String wxName = "", wxKeyword = "";
    private long wxStart, stageStart, lastAction;
    private int retries, searchTries, moreTries;
    private Rect panelBounds;
    private boolean tickScheduled;
    private final Rect wxWin = new Rect();
    private final StringBuilder wxLog = new StringBuilder();

    /** 老人点“视频通话”：有“教一次”的录制就按录制步骤重放（最稳）；否则用自动识别方式。 */
    public void startWeChatVideo(int slot, String name, String keyword) {
        List<int[]> steps = Logic.decodeRecording(cfg.recording(slot), screenW(), screenH());
        if (steps != null && !steps.isEmpty()) startReplay(name, steps);
        else startWeChatAuto(name, keyword);
    }

    public void startWeChatAuto(String name, String keyword) {
        cancelWeChat(false);
        wxName = name == null ? "" : name;
        wxKeyword = keyword.trim();
        wxStart = SystemClock.uptimeMillis();
        retries = 0;
        searchTries = 0;
        moreTries = 0;
        panelBounds = null;
        wxLog.setLength(0);
        log("start name=" + wxName + " keyword=" + wxKeyword);
        showOverlay("正在给【" + wxName + "】打微信视频\n请稍等，不要点屏幕…", 0);
        if (!Actions.launchFresh(this, Config.PKG_WECHAT)) {
            finishWeChat(false, "打不开微信", null);
            return;
        }
        setStage(WX_FIND_SEARCH);
        scheduleTick(1500);
    }

    public void cancelWeChat(boolean showMsg) {
        if (wxStage != WX_IDLE && showMsg) showOverlay("已取消", 1500);
        else removeOverlay();
        wxStage = WX_IDLE;
        h.removeCallbacks(tickRunnable);
        tickScheduled = false;
        replayGen++;
    }

    // ------------------------------------------------------------------ “教一次”：录制与重放
    // 有些微信版本不向无障碍服务提供界面文字（读不到搜索框），自动识别就会失败。
    // 这时让子女在自己的手机上正常点一遍（例如：通讯录 → 星标朋友里的他 → 音视频通话 → 视频通话），
    // App 记下每一下点击的位置和间隔；老人点照片时按同样的顺序自动点，完全不依赖读取微信界面。
    private int replayGen = 0;

    private int screenW() { android.graphics.Point p = new android.graphics.Point(); ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p); return p.x; }

    private int screenH() { android.graphics.Point p = new android.graphics.Point(); ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p); return p.y; }

    private void startReplay(String name, List<int[]> steps) {
        cancelWeChat(false);
        cancelRecording(false);
        final int gen = ++replayGen;
        wxName = name == null ? "" : name;
        wxStart = SystemClock.uptimeMillis();
        wxLog.setLength(0);
        log("replay start name=" + wxName + " steps=" + steps.size());
        showOverlay("正在给【" + wxName + "】打微信视频\n请稍等，不要点屏幕…", 0);
        if (!Actions.launchFresh(this, Config.PKG_WECHAT)) {
            showOverlay("打不开微信", 5000);
            return;
        }
        waitWeChatThen(gen, SystemClock.uptimeMillis(), () -> replayStep(gen, steps, 0));
    }

    /** 等微信到前台并稳定 2 秒后再开始点。 */
    private void waitWeChatThen(int gen, long since, Runnable next) {
        if (gen != replayGen) return;
        if (Config.PKG_WECHAT.equals(foregroundPkg())) {
            h.postDelayed(() -> { if (gen == replayGen) next.run(); }, 2000);
        } else if (SystemClock.uptimeMillis() - since > 10_000) {
            log("replay: wechat not foreground");
            showOverlay("微信没有打开\n请稍后再试", 5000);
        } else {
            h.postDelayed(() -> waitWeChatThen(gen, since, next), 300);
        }
    }

    private void replayStep(int gen, List<int[]> steps, int i) {
        if (gen != replayGen) return;
        if (i >= steps.size()) {
            log("replay done");
            writeDiag(true, -1, null);
            showOverlay("正在呼叫【" + wxName + "】…", 3000);
            return;
        }
        String fg = foregroundPkg();
        if (!Config.PKG_WECHAT.equals(fg)) {
            log("replay abort: foreground=" + fg);
            showOverlay("微信被关掉了，已停止", 4000);
            return;
        }
        int[] st = steps.get(i);
        log("replay step " + (i + 1) + " tap " + st[0] + "," + st[1]);
        tap(st[0], st[1]);
        if (i + 1 < steps.size()) {
            long delay = Logic.replayDelay(steps.get(i + 1)[2]);
            h.postDelayed(() -> replayStep(gen, steps, i + 1), delay);
        } else {
            h.postDelayed(() -> replayStep(gen, steps, i + 1), 800);
        }
    }

    // ---------------- 录制
    private View recCatcher;      // 全屏透明层：接住子女的点击，记下位置后再“转点”给微信
    private LinearLayout recPanel;
    private TextView recText;
    private int recSlot = -1;
    private final List<int[]> recSteps = new ArrayList<>();
    private long recLastTap;
    private float recDownX, recDownY;
    private boolean recPanelTop = true;
    private String recName = "";
    private long recIgnoreUntil;

    public boolean isRecording() { return recSlot >= 0; }

    public void startRecording(int slot, String name) {
        cancelWeChat(false);
        cancelRecording(false);
        if (!Actions.launchFresh(this, Config.PKG_WECHAT)) {
            Actions.toast(this, "打不开微信");
            return;
        }
        recSlot = slot;
        recName = name;
        recSteps.clear();
        recLastTap = 0;
        h.postDelayed(() -> { if (recSlot == slot) showRecordingOverlay(name); }, 1800);
    }

    private void showRecordingOverlay(String name) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        recCatcher = new View(this);
        recCatcher.setBackgroundColor(0x14FF6F00);
        recCatcher.setOnTouchListener((v, e) -> {
            if (SystemClock.uptimeMillis() < recIgnoreUntil) return true; // 自己转发的点击，不重复记录
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                recDownX = e.getRawX();
                recDownY = e.getRawY();
            } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                float dx = e.getRawX() - recDownX, dy = e.getRawY() - recDownY;
                if (dx * dx + dy * dy < dp(12) * dp(12)) onRecordTap((int) e.getRawX(), (int) e.getRawY());
                else Actions.toast(this, "录制时请只“点”，不要滑动");
            }
            return true;
        });
        wm.addView(recCatcher, recLp(true));

        // 紧凑的一行录制条：只盖住微信标题栏，不挡列表和底部按钮
        recPanel = new LinearLayout(this);
        recPanel.setOrientation(LinearLayout.HORIZONTAL);
        recPanel.setGravity(Gravity.CENTER_VERTICAL);
        recPanel.setPadding(dp(10), dp(4), dp(6), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0E65100);
        bg.setCornerRadius(dp(12));
        recPanel.setBackground(bg);
        recText = new TextView(this);
        recText.setTextColor(Color.WHITE);
        recText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        recText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        recPanel.addView(recText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        recPanel.addView(panelBtn("完成", v -> finishRecording()));
        recPanel.addView(panelBtn("撤销", v -> {
            if (!recSteps.isEmpty()) recSteps.remove(recSteps.size() - 1);
            updateRecText(name);
        }));
        recPanel.addView(panelBtn("换位置", v -> {
            recPanelTop = !recPanelTop;
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(recPanel, panelLp()); } catch (Exception ignored) {}
        }));
        recPanel.addView(panelBtn("取消", v -> cancelRecording(true)));
        wm.addView(recPanel, panelLp());
        updateRecText(name);
    }

    private TextView panelBtn(String s, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextColor(0xFFE65100);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void updateRecText(String name) {
        if (recText == null) return;
        recText.setText("录制中 已记" + recSteps.size() + "步\n只点不滑，拨出后点完成");
    }

    private WindowManager.LayoutParams recLp(boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private WindowManager.LayoutParams panelLp() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (recPanelTop ? Gravity.TOP : Gravity.CENTER_VERTICAL) | Gravity.CENTER_HORIZONTAL;
        lp.y = recPanelTop ? statusBarHeight() : 0;
        return lp;
    }

    /** 记下一次点击，然后暂时让透明层“放行”，在同一位置替子女点下去。 */
    private void onRecordTap(int x, int y) {
        long now = SystemClock.uptimeMillis();
        int dt = recLastTap == 0 ? 0 : (int) (now - recLastTap);
        recLastTap = now;
        recSteps.add(new int[]{x, y, dt});
        updateRecText(recName);
        recIgnoreUntil = now + 1000;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try { wm.updateViewLayout(recCatcher, recLp(false)); } catch (Exception ignored) {}
        // 等透明层真正变成“不接收点击”后，再在同一位置替子女点下去
        h.postDelayed(() -> {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build();
            GestureResultCallback back = new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { restoreCatcher(); }
                @Override public void onCancelled(GestureDescription d) { restoreCatcher(); }
            };
            if (!dispatchGesture(g, back, null)) restoreCatcher();
        }, 200);
    }

    private void restoreCatcher() {
        h.postDelayed(() -> {
            if (recCatcher == null) return;
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(recCatcher, recLp(true)); } catch (Exception ignored) {}
        }, 350);
    }

    private void finishRecording() {
        if (recSteps.size() < 2) {
            Actions.toast(this, "至少要点 2 步，请继续点，或点“取消”");
            return;
        }
        String rec = Logic.encodeRecording(screenW(), screenH(), recSteps);
        cfg.setRecording(recSlot, rec);
        Log.i(TAG, "recording saved slot=" + recSlot + " " + rec);
        int n = recSteps.size();
        cancelRecording(false);
        Actions.toast(this, "已保存 " + n + " 步。请挂断视频，从“最近任务”回到配置页");
    }

    public void cancelRecording(boolean toast) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (recCatcher != null) { try { wm.removeView(recCatcher); } catch (Exception ignored) {} }
        if (recPanel != null) { try { wm.removeView(recPanel); } catch (Exception ignored) {} }
        recCatcher = null;
        recPanel = null;
        recText = null;
        if (recSlot >= 0 && toast) Actions.toast(this, "已取消录制");
        recSlot = -1;
    }

    private void log(String s) {
        long t = wxStart == 0 ? 0 : SystemClock.uptimeMillis() - wxStart;
        String line = String.format(java.util.Locale.US, "[%5.1fs] %s", t / 1000f, s);
        Log.i(TAG, "WX " + line);
        if (wxLog.length() < 20000) wxLog.append(line).append('\n');
    }

    private void setStage(int s) {
        wxStage = s;
        stageStart = SystemClock.uptimeMillis();
        log("stage -> " + s);
    }

    private void scheduleTick(long delay) {
        if (tickScheduled) return;
        tickScheduled = true;
        h.postDelayed(tickRunnable, delay);
    }

    private final Runnable tickRunnable = () -> {
        tickScheduled = false;
        if (wxStage == WX_IDLE) return;
        try {
            tick();
        } catch (Exception e) {
            log("tick error " + e);
        }
        if (wxStage != WX_IDLE) scheduleTick(500);
    };

    private void finishWeChat(boolean ok, String msg, List<AccessibilityNodeInfo> nodes) {
        log("finish ok=" + ok + " " + msg.replace('\n', ' '));
        int stage = wxStage;
        wxStage = WX_IDLE;
        h.removeCallbacks(tickRunnable);
        tickScheduled = false;
        writeDiag(ok, stage, nodes);
        showOverlay(msg, ok ? 2500 : 7000);
    }

    private void tick() {
        long now = SystemClock.uptimeMillis();
        long inStage = now - stageStart;
        List<AccessibilityNodeInfo> nodes = wechatNodes();
        if (now - wxStart > WX_TIMEOUT) {
            finishWeChat(false, "没能自动接通视频\n请子女在配置页“导出诊断”发给开发者", nodes);
            return;
        }
        if (nodes.isEmpty()) return; // 微信还没到前台
        if (now - lastAction < 800) return; // 给界面一点反应时间

        switch (wxStage) {
            case WX_FIND_SEARCH: {
                AccessibilityNodeInfo edit = findEdit(nodes);
                if (edit != null && inTop(edit, 0.25f)) { log("already on search page"); setStage(WX_TYPE); return; }
                if (inStage < 1200) return; // 等首页稳定
                if (searchTries >= 5) {
                    finishWeChat(false, "没找到微信的搜索按钮\n请子女在配置页“导出诊断”发给开发者", nodes);
                    return;
                }
                // A. 按描述/文字“搜索”
                AccessibilityNodeInfo n = findSearchByText(nodes);
                if (n != null) {
                    log("search by text/desc " + describe(n));
                    if (clickOrTap(n, true)) { searchTries++; setStage(WX_TYPE); }
                    return;
                }
                // B. 按控件 ID 含 search
                n = findByViewId(nodes, "search");
                if (n != null && inTop(n, 0.2f)) {
                    log("search by viewId " + describe(n));
                    if (tapCenter(n)) { searchTries++; setStage(WX_TYPE); }
                    return;
                }
                // C. 按位置：标题栏右侧的图标，从右数第二个是放大镜（最右是“+”）
                List<Rect> icons = topBarIcons(nodes);
                log("topbar icons=" + icons);
                if (icons.size() >= 2) {
                    Rect r = icons.get(1);
                    log("search by topbar structure " + r);
                    if (tap(r.centerX(), r.centerY())) { searchTries++; setStage(WX_TYPE); }
                    return;
                }
                // D. 按坐标：标题栏右侧放大镜的位置
                if (inStage > 2500) {
                    int x = wxWin.right - dp(71);
                    int y = Math.max(wxWin.top, statusBarHeight()) + dp(24);
                    log("search by coordinate " + x + "," + y);
                    if (tap(x, y)) { searchTries++; setStage(WX_TYPE); }
                }
                return;
            }
            case WX_TYPE: {
                AccessibilityNodeInfo edit = findEdit(nodes);
                if (edit != null) {
                    CharSequence cur = edit.getText();
                    if (cur != null && cur.toString().trim().equals(wxKeyword)) { setStage(WX_PICK); return; }
                    edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    Bundle b = new Bundle();
                    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, wxKeyword);
                    boolean ok = edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                    if (!ok) ok = pasteInto(edit, wxKeyword);
                    log("type keyword ok=" + ok + " edit=" + describe(edit));
                    if (ok) {
                        lastAction = now;
                        setStage(WX_PICK);
                    }
                    return;
                }
                if (inStage > 3500) {
                    log("no EditText after search click, retry");
                    setStage(WX_FIND_SEARCH);
                    stageStart -= 1200;
                }
                return;
            }
            case WX_PICK: {
                if (inStage < 1500) return; // 等搜索结果出来
                List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                AccessibilityNodeInfo edit = null;
                for (AccessibilityNodeInfo n : nodes) {
                    if (isEdit(n)) { edit = n; continue; }
                    CharSequence t = n.getText();
                    if (t == null || t.length() == 0) t = n.getContentDescription();
                    if (t == null || t.length() == 0) continue;
                    if (!n.isVisibleToUser()) continue;
                    textNodes.add(n);
                    texts.add(t.toString());
                }
                int idx = Logic.pickSearchResult(texts, wxKeyword, wxName);
                if (idx >= 0) {
                    log("pick result '" + texts.get(idx) + "' " + describe(textNodes.get(idx)));
                    if (clickOrTap(textNodes.get(idx), false)) {
                        lastAction = now;
                        setStage(WX_CHAT_MORE);
                    }
                    return;
                }
                // 界面没有文字可读时：点搜索框下面第一条结果
                if (inStage > 3000 && texts.size() <= 2) {
                    Rect r = firstRowBelow(nodes, edit);
                    if (r != null) {
                        log("pick by position " + r);
                        if (tap(r.centerX(), r.centerY())) { lastAction = now; setStage(WX_CHAT_MORE); }
                        return;
                    }
                }
                if (inStage > 8000) {
                    finishWeChat(false, "微信里没搜到“" + wxKeyword + "”\n请子女检查微信号/备注名", nodes);
                }
                return;
            }
            case WX_CHAT_MORE: {
                AccessibilityNodeInfo more = findMoreButton(nodes);
                if (more != null) {
                    log("more by desc " + describe(more));
                    if (clickOrTap(more, true)) { moreTries++; lastAction = now; setStage(WX_PANEL); }
                    return;
                }
                if (inStage > 2500) {
                    List<Rect> icons = bottomBarIcons(nodes);
                    log("bottom icons=" + icons);
                    if (!icons.isEmpty()) {
                        Rect r = icons.get(0);
                        if (tap(r.centerX(), r.centerY())) { moreTries++; lastAction = now; setStage(WX_PANEL); }
                        return;
                    }
                }
                if (inStage > 8000) finishWeChat(false, "没能打开聊天窗口的“+”\n请子女在配置页“导出诊断”", nodes);
                return;
            }
            case WX_PANEL: {
                AccessibilityNodeInfo v = findTextOrDesc(nodes, "视频通话", null);
                if (v != null) {
                    Rect r = new Rect();
                    v.getBoundsInScreen(r);
                    log("panel video " + describe(v));
                    if (clickOrTap(v, false)) { panelBounds = r; lastAction = now; setStage(WX_SHEET); return; }
                }
                if (inStage > 3500 && moreTries < 3) { // 面板没展开，再点一次“+”
                    log("panel not shown, click + again");
                    setStage(WX_CHAT_MORE);
                    stageStart -= 2500;
                    return;
                }
                if (inStage > 7000) finishWeChat(false, "没找到“视频通话”按钮\n请子女在配置页“导出诊断”", nodes);
                return;
            }
            case WX_SHEET: {
                if (containsText(nodes, "等待对方接受邀请") || containsText(nodes, "正在等待对方")) {
                    finishWeChat(true, "正在呼叫【" + wxName + "】…", nodes);
                    return;
                }
                boolean sheetShown = findTextOrDesc(nodes, "语音通话", null) != null;
                AccessibilityNodeInfo target = findTextOrDesc(nodes, "视频通话", panelBounds);
                if (sheetShown && target != null) {
                    log("sheet video " + describe(target));
                    if (clickOrTap(target, false)) finishWeChat(true, "正在呼叫【" + wxName + "】…", nodes);
                    return;
                }
                if (inStage > 6000) {
                    // 有的微信版本点“视频通话”后直接拨出
                    if (!sheetShown) finishWeChat(true, "正在呼叫【" + wxName + "】…", nodes);
                    else finishWeChat(false, "没能点到“视频通话”", nodes);
                }
                return;
            }
            default:
        }
    }

    // ---------------- 查找策略
    private boolean inTop(AccessibilityNodeInfo n, float frac) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r.centerY() < wxWin.top + wxWin.height() * frac;
    }

    private AccessibilityNodeInfo findSearchByText(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo n : nodes) {
            if (isEdit(n) || !n.isVisibleToUser()) continue;
            String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
            String t = n.getText() == null ? "" : n.getText().toString().trim();
            boolean hit = d.equals("搜索") || t.equals("搜索") || (d.startsWith("搜索") && d.length() <= 6);
            if (hit && inTop(n, 0.2f)) return n;
        }
        return null;
    }

    private static AccessibilityNodeInfo findByViewId(List<AccessibilityNodeInfo> nodes, String part) {
        for (AccessibilityNodeInfo n : nodes) {
            String id = n.getViewIdResourceName();
            if (id != null && id.toLowerCase().contains(part) && n.isVisibleToUser() && !isEdit(n)) return n;
        }
        return null;
    }

    /** 标题栏右侧的小图标（按从右到左排序）。 */
    private List<Rect> topBarIcons(List<AccessibilityNodeInfo> nodes) {
        List<Rect> out = new ArrayList<>();
        int topLimit = Math.max(wxWin.top, statusBarHeight()) + dp(64);
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isVisibleToUser()) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.width() < dp(18) || r.width() > dp(72) || r.height() < dp(18) || r.height() > dp(72)) continue;
            if (r.centerY() > topLimit || r.centerY() < wxWin.top) continue;
            if (r.centerX() < wxWin.left + wxWin.width() * 0.6f) continue;
            if (n.getText() != null && n.getText().length() > 0) continue; // 只要图标
            addDistinct(out, r);
        }
        java.util.Collections.sort(out, (a, b) -> b.centerX() - a.centerX());
        return out;
    }

    /** 聊天页底部输入栏右侧的图标（最右边的是“+”）。 */
    private List<Rect> bottomBarIcons(List<AccessibilityNodeInfo> nodes) {
        List<Rect> out = new ArrayList<>();
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isVisibleToUser() || isEdit(n)) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.width() < dp(18) || r.width() > dp(64) || r.height() < dp(18) || r.height() > dp(64)) continue;
            if (r.centerY() < wxWin.top + wxWin.height() * 0.75f) continue;
            if (r.centerX() < wxWin.left + wxWin.width() * 0.8f) continue;
            addDistinct(out, r);
        }
        java.util.Collections.sort(out, (a, b) -> b.centerX() != a.centerX() ? b.centerX() - a.centerX() : b.centerY() - a.centerY());
        return out;
    }

    private void addDistinct(List<Rect> out, Rect r) {
        for (Rect o : out) {
            if (Math.abs(o.centerX() - r.centerX()) < dp(12) && Math.abs(o.centerY() - r.centerY()) < dp(12)) return;
        }
        out.add(r);
    }

    private AccessibilityNodeInfo findMoreButton(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence d = n.getContentDescription();
            if (d == null || !n.isVisibleToUser()) continue;
            String s = d.toString().trim();
            if (!s.startsWith("更多功能")) continue;
            if (!inTop(n, 0.5f)) return n; // 聊天页底部的“+”（首页顶部的“+”不要）
        }
        return null;
    }

    /** 按文字或描述精确找；exclude 不为空时跳过同一位置的节点。 */
    private static AccessibilityNodeInfo findTextOrDesc(List<AccessibilityNodeInfo> nodes, String s, Rect exclude) {
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isVisibleToUser()) continue;
            CharSequence t = n.getText(), d = n.getContentDescription();
            boolean hit = (t != null && s.equals(t.toString().trim())) || (d != null && s.equals(d.toString().trim()));
            if (!hit) continue;
            if (exclude != null) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (exclude.equals(r)) continue;
            }
            return n;
        }
        return null;
    }

    private Rect firstRowBelow(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo edit) {
        int top = wxWin.top + dp(100);
        if (edit != null) {
            Rect er = new Rect();
            edit.getBoundsInScreen(er);
            top = er.bottom + dp(4);
        }
        Rect best = null;
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isClickable() || !n.isVisibleToUser()) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.top < top || r.height() < dp(40) || r.height() > dp(130) || r.width() < wxWin.width() / 2) continue;
            if (best == null || r.top < best.top) best = r;
        }
        return best;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    // ---------------- 节点工具
    private List<AccessibilityNodeInfo> wechatNodes() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        Rect biggest = new Rect();
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) {
                for (AccessibilityWindowInfo w : ws) {
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null && r.getPackageName() != null && Config.PKG_WECHAT.equals(r.getPackageName().toString())) {
                        Rect wb = new Rect();
                        w.getBoundsInScreen(wb);
                        if (wb.width() * wb.height() > biggest.width() * biggest.height()) biggest.set(wb);
                        collect(r, out, 0);
                    }
                }
            }
        } catch (Exception ignored) {}
        if (out.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null && r.getPackageName() != null && Config.PKG_WECHAT.equals(r.getPackageName().toString())) {
                r.getBoundsInScreen(biggest);
                collect(r, out, 0);
            }
        }
        if (biggest.isEmpty()) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            biggest.set(0, 0, dm.widthPixels, dm.heightPixels);
        }
        wxWin.set(biggest);
        return out;
    }

    private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 60 || out.size() > 3000) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    private static boolean isEdit(AccessibilityNodeInfo n) {
        CharSequence c = n.getClassName();
        return (c != null && c.toString().contains("EditText")) || n.isEditable();
    }

    private static AccessibilityNodeInfo findEdit(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo n : nodes) if (isEdit(n) && n.isVisibleToUser()) return n;
        return null;
    }

    private static boolean containsText(List<AccessibilityNodeInfo> nodes, String s) {
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n.getText();
            if (t != null && t.toString().contains(s)) return true;
            CharSequence d = n.getContentDescription();
            if (d != null && d.toString().contains(s)) return true;
        }
        return false;
    }

    private boolean pasteInto(AccessibilityNodeInfo edit, String text) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("kw", text));
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            return edit.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 点击节点：selfOnly=true 时只在节点自身可点击时用无障碍点击，否则在节点中心模拟手指点
     * （避免点到一整块父容器）；selfOnly=false 时可以向上找可点击的列表项/格子。
     */
    private boolean clickOrTap(AccessibilityNodeInfo n, boolean selfOnly) {
        AccessibilityNodeInfo p = n;
        int levels = selfOnly ? 1 : 6;
        for (int i = 0; i < levels && p != null; i++) {
            if (p.isClickable() && p.isEnabled() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                lastAction = SystemClock.uptimeMillis();
                return true;
            }
            p = p.getParent();
        }
        return tapCenter(n);
    }

    private boolean tapCenter(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (r.width() <= 0 || r.height() <= 0) return false;
        return tap(r.centerX(), r.centerY());
    }

    private boolean tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build();
        boolean ok = dispatchGesture(g, null, null);
        if (ok) lastAction = SystemClock.uptimeMillis();
        log("tap " + x + "," + y + " ok=" + ok);
        return ok;
    }

    private static String describe(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        String cls = n.getClassName() == null ? "?" : n.getClassName().toString();
        cls = cls.substring(cls.lastIndexOf('.') + 1);
        return cls + " t=" + n.getText() + " d=" + n.getContentDescription() + " id=" + n.getViewIdResourceName()
                + " " + r.toShortString() + (n.isClickable() ? " C" : "") + (n.isVisibleToUser() ? " V" : "");
    }

    /** 保存诊断：步骤日志 + 当前微信界面结构。子女可在配置页“导出诊断”。 */
    private void writeDiag(boolean ok, int stage, List<AccessibilityNodeInfo> nodes) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("常用联系 微信视频诊断\n");
            sb.append("time=").append(new java.util.Date()).append(" ok=").append(ok).append(" stage=").append(stage).append('\n');
            sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append(" android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n');
            try {
                android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(Config.PKG_WECHAT, 0);
                sb.append("wechat=").append(pi.versionName).append('\n');
            } catch (Exception ignored) {}
            sb.append("app=").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append('\n');
            sb.append("window=").append(wxWin.toShortString()).append(" statusBar=").append(statusBarHeight())
                    .append(" density=").append(getResources().getDisplayMetrics().density).append('\n');
            sb.append("---- steps\n").append(wxLog);
            if (nodes == null) nodes = wechatNodes();
            sb.append("---- wechat tree (").append(nodes.size()).append(" nodes)\n");
            int i = 0;
            for (AccessibilityNodeInfo n : nodes) {
                if (i++ > 1500) break;
                sb.append(describe(n)).append('\n');
            }
            java.io.File f = new java.io.File(getFilesDir(), DIAG_FILE);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                out.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "diag failed", e);
        }
    }

    // ---------------- 顶部提示浮层（不可触摸，不影响操作）
    private TextView overlay;
    private final Runnable removeOverlayRunnable = this::removeOverlay;

    private void showOverlay(String msg, long autoHideMs) {
        h.removeCallbacks(removeOverlayRunnable);
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (overlay == null) {
                overlay = new TextView(this);
                overlay.setTextColor(Color.WHITE);
                overlay.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                overlay.setGravity(Gravity.CENTER);
                overlay.setLineSpacing(0, 1.15f);
                int pad = dp(18);
                overlay.setPadding(pad, pad, pad, pad);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xEE128A38);
                bg.setCornerRadius(dp(18));
                overlay.setBackground(bg);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = dp(90);
                lp.horizontalMargin = 0.04f;
                wm.addView(overlay, lp);
            }
            overlay.setText(msg);
        } catch (Exception e) {
            Log.w(TAG, "overlay failed", e);
            overlay = null;
            Actions.toast(this, msg);
        }
        if (autoHideMs > 0) h.postDelayed(removeOverlayRunnable, autoHideMs);
    }

    private void removeOverlay() {
        if (overlay == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
        } catch (Exception ignored) {}
        overlay = null;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
