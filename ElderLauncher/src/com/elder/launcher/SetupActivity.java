package com.elder.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 子女配置页：联系人、照片、微信/抖音安装登录、各项权限、管理密码。 */
public class SetupActivity extends Activity {
    private static final int REQ_PHOTO = 100;
    private static final int REQ_CALL_PERM = 200;
    private static final int REQ_HOME_ROLE = 201;

    private Config cfg;
    private LinearLayout root;
    private final EditText[] nameEt = new EditText[Config.COUNT];
    private final EditText[] valueEt = new EditText[Config.COUNT];
    private final ImageView[] photoIv = new ImageView[Config.COUNT];
    private EditText pinEt;
    private CheckBox wxLogin, dyLogin;
    private TextView wxStatus, dyStatus, stCall, stAcc, stHome, stBattery;
    private Button wxBtn, dyBtn;
    private boolean installPrompted;
    private TextView modeStatus;
    private final TextView[] recStatus = new TextView[Config.COUNT];
    private Button modeBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);
        current = new java.lang.ref.WeakReference<>(this);
        Config.setSetupActive(true);
        getWindow().setStatusBarColor(0xFFFFF6E9);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusableInTouchMode(true); // 打开配置页时不自动弹出键盘
        root.setPadding(dp(14), dp(14), dp(14), dp(28));
        sv.addView(root);
        setContentView(sv);

        TextView title = text("子女配置", 26, 0xFF1C1C1C, true);
        root.addView(title);
        root.addView(text("配置好后，老人每次点亮屏幕都会回到“常用联系”主页。\n以后想修改：在主页顶部“常用联系”标题处按住往下拉，输入管理密码即可回到这里。", 14, 0xFF5A5A5A, false), lp(0, 6, 0, 10));
        // 产品声明
        TextView free = text("本产品完全免费，由大鼻子狗开发", 15, 0xFF128A38, true);
        free.setGravity(Gravity.CENTER);
        free.setPadding(dp(10), dp(8), dp(10), dp(8));
        free.setBackgroundResource(R.drawable.bg_free_badge);
        root.addView(free, lp(0, 0, 0, 4));

        // ① 联系人
        LinearLayout sec1 = section("① 设置联系人和照片");
        for (int i = 0; i < Config.COUNT; i++) sec1.addView(contactRow(i));

        // ② 微信、抖音
        LinearLayout sec2 = section("② 安装并登录 微信、抖音（必须）");
        wxStatus = text("", 15, 0xFF1C1C1C, true);
        wxBtn = button("", true);
        wxLogin = check("我已在这台手机上登录好老人的微信", cfg.wechatLoginConfirmed());
        sec2.addView(appRow("微信", wxStatus, wxBtn));
        sec2.addView(wxLogin);
        sec2.addView(text("提示：微信里要能搜到上面填写的两位视频联系人（必须是好友）。", 12.5f, 0xFF8A8A8A, false), lp(0, 0, 0, 12));
        dyStatus = text("", 15, 0xFF1C1C1C, true);
        dyBtn = button("", true);
        dyLogin = check("我已在这台手机上登录好抖音账号", cfg.douyinLoginConfirmed());
        sec2.addView(appRow("抖音", dyStatus, dyBtn));
        sec2.addView(dyLogin);
        wxBtn.setOnClickListener(v -> onAppButton(Config.PKG_WECHAT, "微信", Actions.WEB_WECHAT));
        dyBtn.setOnClickListener(v -> onAppButton(Config.PKG_DOUYIN, "抖音", Actions.WEB_DOUYIN));

        // ③ 权限
        LinearLayout sec3 = section("③ 打开必要权限（每项都要开）");
        stCall = text("", 14, 0, true);
        sec3.addView(permRow("1. 直接拨打电话", "点照片后不用再按拨号键。", stCall, "去允许",
                v -> requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PERM)));
        stAcc = text("", 14, 0, true);
        sec3.addView(permRow("2. 无障碍服务「常用联系」", "负责“熄屏后回到主页、关闭抖音”和“自动拨微信视频”。在列表中找到“常用联系”（有的手机在“已下载的应用/已安装的服务”里）并打开。", stAcc, "去打开",
                v -> startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        stHome = text("", 14, 0, true);
        sec3.addView(permRow("3. 设为默认桌面", "按主页键也会回到“常用联系”，老人不会迷路。弹窗里选“常用联系”并设为默认/始终。", stHome, "去设置",
                v -> requestHome()));
        stBattery = text("", 14, 0, true);
        sec3.addView(permRow("4. 允许后台运行", "防止系统清理后台时把本应用关掉。", stBattery, "去允许",
                v -> requestBattery()));
        sec3.addView(permRow("5. 手机厂商额外设置（小米/华为/OPPO/vivo 等）", "在应用详情里打开：自启动、后台弹出界面、锁屏显示；电池设为“无限制”。", null, "应用详情",
                v -> startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
        sec3.addView(permRow("6.（建议）关闭锁屏密码", "设为“无”或“滑动解锁”，老人一按电源键就能直接看到主页。", null, "锁屏设置",
                v -> startSafe(new Intent(Settings.ACTION_SECURITY_SETTINGS))));

        // ④ 密码
        LinearLayout sec4 = section("④ 管理密码（4 位数字，子女进入本页时使用）");
        pinEt = input(cfg.pin(), "例如 2580", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinEt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        sec4.addView(pinEt);

        // ⑤ 使用模式
        LinearLayout sec5 = section("⑤ 使用模式");
        modeStatus = text("", 15, 0xFF1C1C1C, true);
        sec5.addView(modeStatus);
        sec5.addView(text("正常手机模式：不再作为桌面，按主页键回到手机原来的桌面，熄屏也不会跳回本页；“常用联系”仍可在桌面上当普通应用打开。", 12, 0xFF6A6A6A, false), lp(0, 2, 0, 6));
        modeBtn = button("", false);
        modeBtn.setOnClickListener(v -> onModeButton());
        sec5.addView(modeBtn, lp(0, 0, 0, 0, dp(48)));

        // ⑥ 微信视频自检
        LinearLayout sec6 = section("⑥ 微信视频自检");
        sec6.addView(text("填好“微信视频 1”并开启无障碍服务后，点下面按钮试打一次。如果没成功，点“导出诊断”，把内容发给开发者即可定位问题。", 12, 0xFF6A6A6A, false), lp(0, 0, 0, 6));
        Button testWx = button("测试：给“微信视频 1”打视频", true);
        testWx.setOnClickListener(v -> testWeChat(2));
        sec6.addView(testWx, lp(0, 0, 0, 0, dp(48)));
        Button testWx2 = button("测试：给“微信视频 2”打视频", true);
        testWx2.setOnClickListener(v -> testWeChat(3));
        sec6.addView(testWx2, lp(0, 8, 0, 0, dp(48)));
        Button diag = button("导出诊断（发给开发者）", false);
        diag.setOnClickListener(v -> exportDiag());
        sec6.addView(diag, lp(0, 8, 0, 0, dp(48)));

        Button save = button("保存，进入老人主页", true);
        save.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        save.setOnClickListener(v -> onSave());
        root.addView(save, lp(0, 18, 0, 0, dp(58)));
        if (cfg.setupDone()) {
            Button cancel = button("不保存，返回主页", false);
            cancel.setOnClickListener(v -> { cfg.clearDraft(); finishToHome(); });
            root.addView(cancel, lp(0, 10, 0, 0, dp(50)));
            Button sys = button("打开手机系统设置", false);
            sys.setOnClickListener(v -> startSafe(new Intent(Settings.ACTION_SETTINGS)));
            root.addView(sys, lp(0, 10, 0, 0, dp(50)));
        }
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView footer = text("常用联系 v" + ver + "\n本产品完全免费，由大鼻子狗开发", 12, 0xFF9A9A9A, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, lp(0, 20, 0, 0));
        restoreDraftAndWatch();
        if (getIntent().getBooleanExtra(EXTRA_ASK_HOME, false)) root.post(this::requestHome);
    }

    // ------------------------------------------------------------ 草稿：离开本页去开权限再回来，内容不丢
    private void restoreDraftAndWatch() {
        boolean d = cfg.hasDraft();
        for (int i = 0; i < Config.COUNT; i++) {
            if (d) {
                nameEt[i].setText(cfg.draft("name" + i, nameEt[i].getText().toString()));
                valueEt[i].setText(cfg.draft("value" + i, valueEt[i].getText().toString()));
            }
            watch(nameEt[i], "name" + i);
            watch(valueEt[i], "value" + i);
        }
        if (d) {
            pinEt.setText(cfg.draft("pin", pinEt.getText().toString()));
            wxLogin.setChecked(cfg.draftBool("wx", wxLogin.isChecked()));
            dyLogin.setChecked(cfg.draftBool("dy", dyLogin.isChecked()));
        }
        watch(pinEt, "pin");
        wxLogin.setOnCheckedChangeListener((b, c) -> cfg.putDraftBool("wx", c));
        dyLogin.setOnCheckedChangeListener((b, c) -> cfg.putDraftBool("dy", c));
    }

    private void watch(EditText et, String key) {
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                cfg.putDraft(key, s.toString());
                Config.setSetupActive(true);
            }
        });
    }

    // ------------------------------------------------------------ 模式切换
    private void refreshMode() {
        boolean elder = cfg.elderMode();
        modeStatus.setText(elder ? "当前：老人模式 ✓" : "当前：正常手机模式");
        modeStatus.setTextColor(elder ? 0xFF128A38 : 0xFFD9480F);
        modeBtn.setText(elder ? "切换到正常手机模式" : "切回老人模式");
    }

    private void onModeButton() {
        if (cfg.elderMode()) {
            new AlertDialog.Builder(this).setTitle("切换到正常手机模式？")
                    .setMessage("切换后按主页键会回到手机原来的桌面，熄屏不再跳回“常用联系”。\n以后想切回：在桌面打开“常用联系”，点顶部绿色提示条，输入管理密码。")
                    .setPositiveButton("切换", (d, w) -> {
                        cfg.clearDraft();
                        Actions.setElderMode(this, false);
                        Config.setSetupFinished();
                        Actions.toast(this, "已切换为正常手机模式");
                        finish();
                        Actions.goSystemHome(this);
                    })
                    .setNegativeButton("取消", null).show();
        } else {
            Actions.setElderMode(this, true);
            refreshMode();
            refreshStatus();
            Actions.toast(this, "已切回老人模式，请在弹出的窗口里把“常用联系”设为默认桌面");
            requestHome();
        }
    }

    // ------------------------------------------------------------ 教一次：录制拨打步骤
    private void askRecord(int slot) {
        if (ElderService.instance == null) { Actions.toast(this, "请先打开第③步里的“无障碍服务”"); return; }
        if (!Actions.isInstalled(this, Config.PKG_WECHAT)) { Actions.toast(this, "请先安装并登录微信"); return; }
        String name = nameEt[slot].getText().toString().trim();
        boolean has = Config.recordingSteps(cfg.recording(slot)) > 0;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("教一次：给【" + name + "】打微信视频")
                .setMessage("有的微信版本不让 App 读取界面文字，自动识别会失败。教一次后，老人点照片时会按你点的顺序自动点，最稳定。\n\n"
                        + "准备：先在微信里把他设为“星标朋友”（通讯录 → 点他 → 右上角“…” → 设为星标朋友），这样他在通讯录里的位置固定。\n\n"
                        + "开始后会打开微信，屏幕上方有橙色录制条。请依次点：\n"
                        + "① 底部“通讯录”\n② 星标朋友里的【" + name + "】\n③ “音视频通话”\n④ “视频通话”\n"
                        + "拨出后点橙色录制条上的“完成”。（录制条挡住要点的地方时，点“换位置”）\n\n注意：只点不要滑动；最后一步会真的拨出视频，可直接挂断。")
                .setPositiveButton("开始录制", (d, w) -> ElderService.instance.startRecording(slot, name))
                .setNegativeButton("取消", null);
        if (has) b.setNeutralButton("清除已教的步骤", (d, w) -> { cfg.setRecording(slot, ""); refreshStatus(); });
        b.show();
    }

    // ------------------------------------------------------------ 微信自检与诊断
    private void testWeChat(int slot) {
        String kw = valueEt[slot].getText().toString().trim();
        boolean recorded = Config.recordingSteps(cfg.recording(slot)) > 0;
        if (kw.isEmpty() && !recorded) { Actions.toast(this, "请先填写微信号/备注名，或点“教一次拨打步骤”"); return; }
        if (ElderService.instance == null) { Actions.toast(this, "请先打开第③步里的“无障碍服务”"); return; }
        Actions.wechatVideo(this, slot, nameEt[slot].getText().toString().trim(), kw);
    }

    private void exportDiag() {
        java.io.File f = new java.io.File(getFilesDir(), ElderService.DIAG_FILE);
        if (!f.isFile()) { Actions.toast(this, "还没有诊断信息，请先点“测试”试打一次"); return; }
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), 200_000)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            }
            String txt = new String(buf, "UTF-8");
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("诊断", txt));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, txt);
            startActivity(Intent.createChooser(send, "诊断信息已复制，选择发送方式"));
        } catch (Exception e) {
            Actions.toast(this, "导出失败：" + e.getMessage());
        }
    }

    public static final String EXTRA_ASK_HOME = "ask_home";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_ASK_HOME, false)) requestHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.setSetupActive(true);
        refreshStatus();
        if (!installPrompted) {
            installPrompted = true;
            if (!Actions.isInstalled(this, Config.PKG_WECHAT)) {
                Actions.askInstall(this, "微信", Config.PKG_WECHAT, Actions.WEB_WECHAT);
            } else if (!Actions.isInstalled(this, Config.PKG_DOUYIN)) {
                Actions.askInstall(this, "抖音", Config.PKG_DOUYIN, Actions.WEB_DOUYIN);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) Config.setSetupFinished(); else Config.setSetupActive(false);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) Config.setSetupFinished();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (cfg.setupDone()) {
            new AlertDialog.Builder(this).setTitle("要保存修改吗？")
                    .setPositiveButton("保存", (d, w) -> onSave())
                    .setNegativeButton("不保存，返回主页", (d, w) -> { cfg.clearDraft(); finishToHome(); })
                    .setNeutralButton("继续编辑", null).show();
        }
        else Actions.toast(this, "请先完成配置并点最下面的“保存”");
    }

    /** 回到老人页，并把配置页连同它的任务一起关掉（最近任务里不再残留配置页）。 */
    private void finishToHome() {
        Config.setSetupFinished();
        Actions.openElderHome(this);
        finishAndRemoveTask();
    }

    // 当前打开的配置页（熄屏复位时由无障碍服务关闭）
    private static java.lang.ref.WeakReference<SetupActivity> current;

    /** 熄屏复位时调用：已完成过配置时关闭配置页（草稿已自动保存，下次进来内容还在）。 */
    public static void closeIfOpen() {
        SetupActivity a = current == null ? null : current.get();
        if (a == null || a.isFinishing()) return;
        if (!a.cfg.setupDone()) return; // 首次配置还没完成，不打断
        try { a.finishAndRemoveTask(); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ 状态刷新
    private void refreshStatus() {
        if (modeStatus != null) refreshMode();
        for (int i = 0; i < Config.COUNT; i++) {
            if (recStatus[i] == null) continue;
            int n = Config.recordingSteps(cfg.recording(i));
            recStatus[i].setText(n > 0 ? "已教会：" + n + " 步 ✓（老人点照片时按这些步骤自动点）" : "还没教：将尝试自动识别微信界面");
            recStatus[i].setTextColor(n > 0 ? 0xFF128A38 : 0xFFD9480F);
        }
        boolean wx = Actions.isInstalled(this, Config.PKG_WECHAT);
        wxStatus.setText(wx ? "微信：已安装 ✓" : "微信：未安装");
        wxStatus.setTextColor(wx ? 0xFF128A38 : 0xFFD9480F);
        wxBtn.setText(wx ? "打开微信去登录" : "下载安装微信");
        wxLogin.setEnabled(wx);
        boolean dy = Actions.isInstalled(this, Config.PKG_DOUYIN);
        dyStatus.setText(dy ? "抖音：已安装 ✓" : "抖音：未安装");
        dyStatus.setTextColor(dy ? 0xFF128A38 : 0xFFD9480F);
        dyBtn.setText(dy ? "打开抖音去登录" : "下载安装抖音");
        dyLogin.setEnabled(dy);

        setOk(stCall, Actions.canCallDirectly(this));
        setOk(stAcc, Actions.isAccessibilityEnabled(this));
        setOk(stHome, Actions.isDefaultHome(this));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        setOk(stBattery, pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
    }

    private void setOk(TextView tv, boolean ok) {
        tv.setText(ok ? "已开启 ✓" : "未开启");
        tv.setTextColor(ok ? 0xFF128A38 : 0xFFD9480F);
    }

    private void onAppButton(String pkg, String name, String web) {
        if (Actions.isInstalled(this, pkg)) {
            if (!Actions.launchFresh(this, pkg)) Actions.toast(this, "打不开" + name);
            else Actions.toast(this, "登录好" + name + "后，返回本页勾选“已登录”");
        } else {
            Actions.openStore(this, pkg, web);
        }
    }

    private void requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE);
                    return;
                }
            } catch (Exception ignored) {}
        }
        if (!startSafe(new Intent(Settings.ACTION_HOME_SETTINGS))) {
            startSafe(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void requestBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            startSafe(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    /** 系统设置页用独立任务打开：切后台/熄屏后不会把设置页“挂”在本应用里。 */
    private boolean startSafe(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            Actions.toast(this, "这台手机打不开该设置页，请在系统设置里手动查找");
            return false;
        }
    }

    // ------------------------------------------------------------ 照片
    private void pickPhoto(int i) {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(it, "选择照片"), REQ_PHOTO + i);
        } catch (Exception e) {
            Actions.toast(this, "没有找到相册");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req >= REQ_PHOTO && req < REQ_PHOTO + Config.COUNT && res == RESULT_OK && data != null && data.getData() != null) {
            final int i = req - REQ_PHOTO;
            final Uri uri = data.getData();
            Actions.toast(this, "正在处理照片…");
            new Thread(() -> {
                boolean ok = PhotoUtil.importPhoto(this, uri, cfg.photoFile(i));
                if (ok) cfg.bumpPhotoVersion(i);
                runOnUiThread(() -> {
                    if (ok) showThumb(i);
                    else Actions.toast(this, "照片读取失败，请换一张");
                });
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        refreshStatus();
    }

    private void showThumb(int i) {
        Bitmap bm = cfg.hasPhoto(i) ? BitmapFactory.decodeFile(cfg.photoFile(i).getAbsolutePath()) : null;
        if (bm != null) photoIv[i].setImageBitmap(bm);
        else photoIv[i].setImageResource(R.drawable.ic_person);
    }

    // ------------------------------------------------------------ 保存
    private void onSave() {
        String[] names = new String[Config.COUNT];
        String[] values = new String[Config.COUNT];
        StringBuilder err = new StringBuilder();
        for (int i = 0; i < Config.COUNT; i++) {
            names[i] = nameEt[i].getText().toString().trim();
            values[i] = valueEt[i].getText().toString().trim();
            String slot = Config.isPhoneSlot(i) ? "打电话" + (i + 1) : "微信视频" + (i - 1);
            if (names[i].isEmpty()) err.append("· ").append(slot).append("：请填写称呼\n");
            if (Config.isPhoneSlot(i)) {
                if (!Logic.isValidPhone(values[i])) err.append("· ").append(slot).append("：电话号码不对\n");
                else values[i] = Logic.normalizePhone(values[i]);
            } else if (values[i].isEmpty() && Config.recordingSteps(cfg.recording(i)) == 0) {
                err.append("· ").append(slot).append("：请填写微信号或备注名，或“教一次拨打步骤”\n");
            }
        }
        String pin = pinEt.getText().toString().trim();
        if (!Logic.isValidPin(pin)) err.append("· 管理密码需要 4 位数字\n");
        if (!Actions.isInstalled(this, Config.PKG_WECHAT)) err.append("· 还没有安装微信\n");
        else if (!wxLogin.isChecked()) err.append("· 请登录微信后勾选“已登录”\n");
        if (!Actions.isInstalled(this, Config.PKG_DOUYIN)) err.append("· 还没有安装抖音\n");
        else if (!dyLogin.isChecked()) err.append("· 请登录抖音后勾选“已登录”\n");
        if (err.length() > 0) {
            new AlertDialog.Builder(this).setTitle("还有几项没完成").setMessage(err.toString().trim())
                    .setPositiveButton("好的", null).show();
            return;
        }

        StringBuilder warn = new StringBuilder();
        if (!Actions.canCallDirectly(this)) warn.append("· 直接拨打电话（未开启时会先打开拨号盘）\n");
        if (!Actions.isAccessibilityEnabled(this)) warn.append("· 无障碍服务（未开启时熄屏不会回主页，微信也不能自动视频）\n");
        if (cfg.elderMode() && !Actions.isDefaultHome(this)) warn.append("· 默认桌面（未设置时按主页键会离开本应用）\n");
        if (warn.length() > 0) {
            new AlertDialog.Builder(this).setTitle("以下权限还没开，会影响使用")
                    .setMessage(warn.toString().trim() + "\n\n建议先开启再保存。")
                    .setPositiveButton("先去开启", null)
                    .setNegativeButton("仍然保存", (d, w) -> doSave(names, values, pin))
                    .show();
            return;
        }
        doSave(names, values, pin);
    }

    private void doSave(String[] names, String[] values, String pin) {
        cfg.save(names, values, pin, wxLogin.isChecked(), dyLogin.isChecked());
        cfg.clearDraft();
        Actions.toast(this, "已保存。熄屏再亮屏即可回到本主页");
        finishToHome();
    }

    // ------------------------------------------------------------ UI 小工具
    private View contactRow(int i) {
        boolean phone = Config.isPhoneSlot(i);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(10));

        LinearLayout photoCol = new LinearLayout(this);
        photoCol.setOrientation(LinearLayout.VERTICAL);
        photoCol.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundResource(R.drawable.bg_photo);
        iv.setClipToOutline(true);
        photoIv[i] = iv;
        showThumb(i);
        iv.setOnClickListener(v -> pickPhoto(i));
        photoCol.addView(iv, new LinearLayout.LayoutParams(dp(78), dp(104)));
        TextView pick = text("点此换照片", 12, 0xFF128A38, true);
        pick.setOnClickListener(v -> pickPhoto(i));
        photoCol.addView(pick, lp(0, 4, 0, 0));
        row.addView(photoCol);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text(phone ? "打电话 " + (i + 1) : "微信视频 " + (i - 1), 15, 0xFF1C1C1C, true));
        nameEt[i] = input(cfg.name(i), "称呼，如：" + Config.DEFAULT_NAMES[i], InputType.TYPE_CLASS_TEXT);
        nameEt[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        col.addView(nameEt[i], lp(0, 6, 0, 0));
        if (phone) {
            valueEt[i] = input(cfg.value(i), "电话号码", InputType.TYPE_CLASS_PHONE);
        } else {
            valueEt[i] = input(cfg.value(i), "对方的微信号 或 备注名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }
        col.addView(valueEt[i], lp(0, 6, 0, 0));
        if (!phone) {
            col.addView(text("会在微信里搜索这个词并自动发起视频。建议填对方微信号或你设置的备注名，保证搜出来第一个就是他。", 11.5f, 0xFF8A8A8A, false), lp(0, 4, 0, 0));
            recStatus[i] = text("", 13, 0, true);
            col.addView(recStatus[i], lp(0, 6, 0, 0));
            Button rec = button("教一次拨打步骤（推荐）", true);
            rec.setOnClickListener(v -> askRecord(i));
            col.addView(rec, lp(0, 4, 0, 0, dp(44)));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View appRow(String name, TextView status, Button btn) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(2));
        row.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(btn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return row;
    }

    private View permRow(String title, String desc, TextView status, String btnText, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 15, 0xFF1C1C1C, true));
        col.addView(text(desc, 12, 0xFF6A6A6A, false), lp(0, 2, 0, 0));
        if (status != null) col.addView(status, lp(0, 2, 0, 0));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button b = button(btnText, status != null);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        blp.leftMargin = dp(8);
        row.addView(b, blp);
        return row;
    }

    private LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_setup_block);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(text(title, 18, 0xFF1C1C1C, true), lp(0, 0, 0, 4));
        root.addView(box, lp(0, 12, 0, 0));
        return box;
    }

    private TextView text(String s, float sizeDp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        if (color != 0) t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private EditText input(String val, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        e.setTextColor(0xFF1C1C1C);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : 0xFF333333);
        b.setBackgroundResource(primary ? R.drawable.bg_btn_setup : R.drawable.bg_btn_setup_gray);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setStateListAnimator(null);
        return b;
    }

    private CheckBox check(String s, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(checked);
        c.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        c.setTextColor(0xFF1C1C1C);
        return c;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        return lp(l, t, r, b, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
