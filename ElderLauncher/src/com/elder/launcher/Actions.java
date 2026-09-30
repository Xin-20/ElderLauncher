package com.elder.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.List;

public final class Actions {
    private Actions() {}

    public static final String WEB_WECHAT = "https://weixin.qq.com/";
    public static final String WEB_DOUYIN = "https://www.douyin.com/";

    public static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Drawable appIcon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean canCallDirectly(Context c) {
        return c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    /** 直接拨号（有权限时），否则打开拨号盘并填好号码。 */
    public static void call(Activity a, String number) {
        String n = Logic.normalizePhone(number);
        if (TextUtils.isEmpty(n)) {
            toast(a, "还没有设置电话号码，请让子女设置");
            return;
        }
        Uri uri = Uri.parse("tel:" + Uri.encode(n));
        maxVolume(a);
        ElderService s = ElderService.instance;
        if (s != null) s.watchCall(true); // 接通后再确认一次：音量最大 + 免提
        try {
            if (canCallDirectly(a)) {
                // 让系统一开始就用免提（扬声器）拨出
                boolean placed = false;
                try {
                    android.telecom.TelecomManager tm = (android.telecom.TelecomManager) a.getSystemService(Context.TELECOM_SERVICE);
                    Bundle extras = new Bundle();
                    extras.putBoolean(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
                    if (tm != null) { tm.placeCall(uri, extras); placed = true; }
                } catch (Exception ignored) {}
                if (!placed) {
                    Intent call = new Intent(Intent.ACTION_CALL, uri);
                    call.putExtra(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
                    a.startActivity(call);
                }
            } else {
                a.startActivity(new Intent(Intent.ACTION_DIAL, uri));
            }
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Intent.ACTION_DIAL, uri));
            } catch (Exception ignored) {
                toast(a, "无法拨打电话");
            }
        }
    }

    /** 以“全新”方式启动一个 App：清空它原来的界面栈，回到它的首页。 */
    public static boolean launchFresh(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openDouyin(Activity a) {
        if (!isInstalled(a, Config.PKG_DOUYIN)) {
            askInstall(a, "抖音", Config.PKG_DOUYIN, WEB_DOUYIN);
            return;
        }
        maxVolume(a); // 看抖音前把媒体音量（扬声器外放）调到最大
        if (!launchFresh(a, Config.PKG_DOUYIN)) { toast(a, "打不开抖音"); return; }
        // 抖音启动后再补一次，防止个别机型切换应用时音量被重置
        final Context app = a.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> maxVolume(app), 2500);
    }

    public static void wechatVideo(Activity a, int slot, String name, String keyword) {
        if (!isInstalled(a, Config.PKG_WECHAT)) {
            askInstall(a, "微信", Config.PKG_WECHAT, WEB_WECHAT);
            return;
        }
        boolean recorded = Config.recordingSteps(new Config(a).recording(slot)) > 0;
        if (TextUtils.isEmpty(keyword) && !recorded) {
            toast(a, "还没有设置微信号，请让子女设置");
            return;
        }
        ElderService s = ElderService.instance;
        if (s == null) {
            // 无障碍服务没开：只能打开微信，让老人/子女手动操作
            launchFresh(a, Config.PKG_WECHAT);
            toast(a, "自动视频功能未开启，请让子女在设置里打开“无障碍服务”");
            return;
        }
        maxVolume(a);
        s.watchCall(false); // 视频接通后再把通话音量拉满
        s.startWeChatVideo(slot, name, keyword);
    }

    /** 把通话、铃声、媒体音量都调到最大（勿扰模式下系统可能不允许改铃声，忽略即可）。 */
    public static void maxVolume(Context c) {
        android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int[] streams = {android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.STREAM_RING, android.media.AudioManager.STREAM_SYSTEM};
        for (int st : streams) {
            try { am.setStreamVolume(st, am.getStreamMaxVolume(st), 0); } catch (Exception ignored) {}
        }
    }

    /** 大字提示：是否去安装。 */
    /** 最近弹出的对话框；熄屏复位时由主页关闭，避免老人亮屏后看到残留弹窗。 */
    public static java.lang.ref.WeakReference<AlertDialog> lastDialog;

    public static void askInstall(Activity a, String appName, String pkg, String web) {
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("手机上还没有" + appName)
                .setMessage("需要先安装" + appName + "，现在去下载安装吗？\n（安装后请让子女帮忙登录）")
                .setPositiveButton("去安装", (dlg, w) -> openStore(a, pkg, web))
                .setNegativeButton("以后再说", null)
                .show();
        lastDialog = new java.lang.ref.WeakReference<>(d);
    }

    /** 优先打开手机自带应用商店的详情页（华为/小米/OPPO/vivo/荣耀等都支持 market://），没有商店则打开官网下载页。 */
    public static void openStore(Context c, String pkg, String web) {
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        List<?> handlers = c.getPackageManager().queryIntentActivities(market, 0);
        if (handlers != null && !handlers.isEmpty()) {
            try {
                c.startActivity(market);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        openUrl(c, web);
    }

    public static void openUrl(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            toast(c, "没有找到浏览器");
        }
    }

    public static boolean isAccessibilityEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        ComponentName me = new ComponentName(c, ElderService.class);
        for (String s : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    public static boolean isDefaultHome(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** 切换 老人模式 / 正常手机模式。正常模式下禁用“桌面”入口，系统会回到原来的桌面。 */
    public static void setElderMode(Context c, boolean on) {
        new Config(c).setElderModeFlag(on);
        ComponentName home = new ComponentName(c, c.getPackageName() + ".HomeAlias");
        c.getPackageManager().setComponentEnabledSetting(home,
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** 打开老人页：老人模式下走“桌面”入口（和按主页键是同一个页面），正常模式下当普通应用打开。 */
    public static void openElderHome(Context c) {
        try { c.startActivity(elderHomeIntent(c)); } catch (Exception ignored) {}
    }

    /**
     * 回老人页的 Intent。已是默认桌面时发“标准的回桌面” Intent（与按主页键完全相同，复用同一个页面，不会多开）；
     * 还没设为默认桌面时直接指定本应用的桌面入口。
     */
    public static Intent elderHomeIntent(Context c) {
        Intent i;
        if (new Config(c).elderMode()) {
            i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            if (!isDefaultHome(c)) i.setClassName(c.getPackageName(), c.getPackageName() + ".HomeAlias");
        } else {
            i = new Intent(c, MainActivity.class);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return i;
    }

    /** 回到系统桌面（正常模式下就是手机原来的桌面）。 */
    public static void goSystemHome(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }
}
