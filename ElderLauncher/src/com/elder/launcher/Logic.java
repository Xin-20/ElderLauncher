package com.elder.launcher;

import java.util.List;

/** 与安卓系统无关的纯逻辑，方便在电脑上做单元测试。 */
public final class Logic {
    private Logic() {}

    /** 只保留数字、+、*、# */
    public static String normalizePhone(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '+' || c == '*' || c == '#') b.append(c);
        }
        return b.toString();
    }

    /** 13812345678 -> "138 1234 5678"；其它长度原样返回（去掉空格等） */
    public static String formatPhone(String s) {
        String n = normalizePhone(s);
        if (n.length() == 11 && n.charAt(0) == '1') {
            return n.substring(0, 3) + " " + n.substring(3, 7) + " " + n.substring(7);
        }
        return n;
    }

    public static boolean isValidPhone(String s) {
        String n = normalizePhone(s);
        return n.length() >= 3 && n.length() <= 20;
    }

    public static boolean isValidPin(String s) {
        return s != null && s.matches("\\d{4}");
    }

    /** 与通话相关的系统界面包名：这些界面在前台时绝不能复位 */
    private static final String[] CALL_PKGS = {
            "com.android.incallui", "com.android.phone", "com.android.server.telecom",
            "com.android.dialer", "com.google.android.dialer", "com.samsung.android.incallui",
            "com.huawei.contacts", "com.hihonor.contacts", "com.android.contacts",
            "com.oplus.incallui", "com.coloros.incallui", "com.vivo.incallui", "com.miui.voip",
    };

    public static boolean isCallPackage(String pkg) {
        if (pkg == null) return false;
        for (String p : CALL_PKGS) if (p.equals(pkg)) return true;
        return pkg.contains("incallui");
    }

    public static boolean isSystemUi(String pkg) {
        return pkg != null && (pkg.equals("com.android.systemui") || pkg.contains("keyguard"));
    }

    /**
     * 熄屏时是否复位。
     * @param enabled    已完成配置且不在配置界面
     * @param audioBusy  音频处于通话/响铃/网络通话状态（打电话、微信视频中）
     */
    public static boolean shouldResetOnScreenOff(boolean enabled, boolean audioBusy) {
        return enabled && !audioBusy;
    }

    /**
     * 亮屏/解锁时是否复位（兜底：熄屏那次没复位成功时补一次）。
     * 前台是微信（可能是来电视频）、电话界面、锁屏时都不动。
     */
    public static boolean shouldResetOnScreenOn(boolean enabled, boolean audioBusy, String fgPkg, String fgClass, String ourPkg) {
        if (!enabled || audioBusy) return false;
        if (fgPkg == null || fgPkg.isEmpty()) return false;
        if (fgPkg.equals(ourPkg)) return fgClass != null && fgClass.endsWith("SetupActivity"); // 停在配置页也回老人页
        if (isSystemUi(fgPkg)) return false;
        if (isCallPackage(fgPkg)) return false;
        if (fgPkg.contains("inputmethod") || fgPkg.contains(".ime")) return false;
        // 微信：只有“来电/通话界面”不动（可能是有人打视频进来），其它微信页面一律回老人页
        if (fgPkg.equals(Config.PKG_WECHAT)) return !(fgClass == null || fgClass.isEmpty() || isWeChatCallScreen(fgClass));
        return true;
    }

    /** 通话界面上“免提/扬声器”按钮的文字 */
    public static boolean isSpeakerLabel(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        if (t.isEmpty() || t.length() > 12) return false;
        return t.equals("免提") || t.equals("扬声器") || t.equals("speaker") || t.equals("speakerphone")
                || t.startsWith("免提") || t.startsWith("扬声器") || t.startsWith("打开免提") || t.startsWith("打开扬声器");
    }

    /** 微信的音视频通话/来电界面 */
    public static boolean isWeChatCallScreen(String cls) {
        if (cls == null) return false;
        String c = cls.toLowerCase();
        return c.contains("voip") || c.contains("multitalk") || c.contains("videoactivity") || c.contains("talkroom");
    }

    private static final String[] EXCLUDE = {
            "搜一搜", "网络查找", "搜索网络", "查找微信号", "查找手机", "朋友圈", "公众号", "小程序",
            "视频号", "文章", "聊天记录", "更多联系人", "最常使用", "群聊", "取消", "问一问", "百科",
    };

    public static boolean isExcludedResultText(String t) {
        if (t == null) return true;
        String s = t.trim();
        if (s.isEmpty()) return true;
        if (s.startsWith("搜索")) return true;
        for (String e : EXCLUDE) if (s.contains(e)) return true;
        return false;
    }

    /**
     * 在微信搜索结果里挑出要点击的那一项。
     * @param texts   按界面遍历顺序收集的文字（已去掉输入框自身）
     * @param keyword 子女配置的“微信号或备注名”
     * @param displayName 主页上显示的称呼（如“外孙”）
     * @return 最佳匹配在 texts 中的下标，没有则 -1
     */
    public static int pickSearchResult(List<String> texts, String keyword, String displayName) {
        if (texts == null || keyword == null) return -1;
        String kw = keyword.trim();
        if (kw.isEmpty()) return -1;
        String kwLower = kw.toLowerCase();
        int best = -1, bestScore = 0;
        boolean afterContactHeader = false;
        for (int i = 0; i < texts.size(); i++) {
            String raw = texts.get(i);
            if (raw == null) continue;
            String t = raw.trim();
            if (t.equals("联系人")) { afterContactHeader = true; continue; }
            if (isExcludedResultText(t)) continue;
            String tl = t.toLowerCase();
            int score = 0;
            if (tl.equals(kwLower)) score = 100;
            else if (t.contains("微信号") && tl.contains(kwLower)) score = 90;
            else if (displayName != null && !displayName.trim().isEmpty() && t.equals(displayName.trim())) score = 70;
            else if (tl.contains(kwLower)) score = 50;
            if (score > 0 && afterContactHeader) score += 5;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    // ---------------- “教一次”录制步骤的编码/解码
    /** steps 每项 {x, y, 距上一步的毫秒数} */
    public static String encodeRecording(int screenW, int screenH, List<int[]> steps) {
        StringBuilder b = new StringBuilder();
        b.append(screenW).append(',').append(screenH).append('|');
        for (int i = 0; i < steps.size(); i++) {
            int[] st = steps.get(i);
            if (i > 0) b.append(';');
            b.append(st[0]).append(',').append(st[1]).append(',').append(st[2]);
        }
        return b.toString();
    }

    /** 解码并按当前屏幕尺寸等比换算；格式不对返回 null */
    public static List<int[]> decodeRecording(String rec, int curW, int curH) {
        try {
            if (rec == null || !rec.contains("|")) return null;
            String[] head = rec.substring(0, rec.indexOf('|')).split(",");
            int w = Integer.parseInt(head[0].trim()), h = Integer.parseInt(head[1].trim());
            String body = rec.substring(rec.indexOf('|') + 1);
            if (body.isEmpty() || w <= 0 || h <= 0) return null;
            List<int[]> out = new java.util.ArrayList<>();
            for (String part : body.split(";")) {
                String[] f = part.split(",");
                int x = Integer.parseInt(f[0].trim()), y = Integer.parseInt(f[1].trim()), dt = Integer.parseInt(f[2].trim());
                out.add(new int[]{Math.round(x * (float) curW / w), Math.round(y * (float) curH / h), dt});
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 重放时两步之间的等待：按录制时的间隔，但至少 1.3 秒（等界面切换），最多 4.5 秒 */
    public static long replayDelay(int recordedDt) {
        return Math.max(1300, Math.min(4500, recordedDt));
    }
}
