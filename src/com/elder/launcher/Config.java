package com.elder.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** 所有配置都存在 SharedPreferences 中。0、1 号是电话联系人；2、3 号是微信视频联系人。 */
public final class Config {
    public static final int COUNT = 4;
    public static final String[] DEFAULT_NAMES = {"女儿", "儿子", "外孙", "护工"};

    public static final String PKG_WECHAT = "com.tencent.mm";
    public static final String PKG_DOUYIN = "com.ss.android.ugc.aweme";

    private static final String PREFS = "elder_config";
    private final SharedPreferences sp;
    private final Context ctx;

    public Config(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isPhoneSlot(int i) { return i < 2; }

    public String name(int i) { return sp.getString("name" + i, DEFAULT_NAMES[i]); }
    /** 电话号码（0、1）或 微信号/备注名（2、3） */
    public String value(int i) { return sp.getString("value" + i, ""); }

    public File photoFile(int i) { return new File(ctx.getFilesDir(), "photo" + i + ".jpg"); }
    public boolean hasPhoto(int i) { return photoFile(i).isFile(); }
    public long photoVersion(int i) { return sp.getLong("photo_ver" + i, 0); }

    public String pin() { return sp.getString("pin", ""); }
    public boolean setupDone() { return sp.getBoolean("setup_done", false); }
    public boolean wechatLoginConfirmed() { return sp.getBoolean("wx_login", false); }
    public boolean douyinLoginConfirmed() { return sp.getBoolean("dy_login", false); }

    public void save(String[] names, String[] values, String pin, boolean wxLogin, boolean dyLogin) {
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < COUNT; i++) {
            e.putString("name" + i, names[i].trim());
            e.putString("value" + i, values[i].trim());
        }
        e.putString("pin", pin);
        e.putBoolean("wx_login", wxLogin);
        e.putBoolean("dy_login", dyLogin);
        e.putBoolean("setup_done", true);
        e.apply();
    }

    public void bumpPhotoVersion(int i) {
        sp.edit().putLong("photo_ver" + i, System.currentTimeMillis()).apply();
    }

    /**
     * 配置页在前台时暂停“熄屏复位”；离开配置页（例如去系统设置开权限）后再暂停 3 分钟，
     * 之后自动恢复，避免子女忘了关配置页导致老人手机不复位。填写内容有草稿保存，不怕被复位打断。
     */
    private static volatile boolean setupForeground = false;
    private static volatile long setupLeftAt = 0;

    public static void setSetupActive(boolean foreground) {
        setupForeground = foreground;
        if (!foreground) setupLeftAt = System.currentTimeMillis();
    }

    public static void setSetupFinished() {
        setupForeground = false;
        setupLeftAt = 0;
    }

    public static boolean isSetupActive() {
        return setupForeground || (setupLeftAt != 0 && System.currentTimeMillis() - setupLeftAt < 3 * 60 * 1000L);
    }

    /** 老人模式（默认开启）。关闭后 = 正常手机模式：不再作为桌面，熄屏也不复位。 */
    public boolean elderMode() { return sp.getBoolean("elder_mode", true); }

    public void setElderModeFlag(boolean on) { sp.edit().putBoolean("elder_mode", on).apply(); }

    /**
     * 熄屏复位是否生效：配置完成且处于老人模式即生效——无论当前在什么页面（包括配置页、系统设置页）。
     * 首次配置还没保存时不复位，否则没法完成配置。配置页填写的内容有草稿保存，被复位打断也不会丢。
     */
    public boolean resetEnabled() { return setupDone() && elderMode(); }

    // ---------------- “教一次”录制的微信视频拨打步骤（按联系人保存）
    /** 格式："屏宽,屏高|x,y,间隔毫秒;x,y,间隔毫秒;..."，没有录制返回空串 */
    public String recording(int slot) { return sp.getString("rec" + slot, ""); }

    public void setRecording(int slot, String v) { sp.edit().putString("rec" + slot, v).apply(); }

    public static int recordingSteps(String rec) {
        if (rec == null || !rec.contains("|")) return 0;
        String body = rec.substring(rec.indexOf('|') + 1);
        return body.isEmpty() ? 0 : body.split(";").length;
    }

    // ---------------- 配置页草稿：去系统设置开权限、被系统回收后回来，已填内容不丢
    public boolean hasDraft() { return sp.getBoolean("draft_on", false); }

    public String draft(String key, String def) { return sp.getString("draft_" + key, def); }

    public boolean draftBool(String key, boolean def) { return sp.getBoolean("draft_b_" + key, def); }

    public void putDraft(String key, String v) {
        sp.edit().putBoolean("draft_on", true).putString("draft_" + key, v).apply();
    }

    public void putDraftBool(String key, boolean v) {
        sp.edit().putBoolean("draft_on", true).putBoolean("draft_b_" + key, v).apply();
    }

    public void clearDraft() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith("draft_")) e.remove(k);
        e.apply();
    }
}
