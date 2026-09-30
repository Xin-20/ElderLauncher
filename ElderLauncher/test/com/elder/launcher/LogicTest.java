package com.elder.launcher;

import java.util.Arrays;
import java.util.List;

/** 无依赖的小型单元测试：java -cp build/classes:build/test com.elder.launcher.LogicTest */
public class LogicTest {
    static int pass = 0, fail = 0;

    static void eq(Object exp, Object act, String name) {
        if ((exp == null && act == null) || (exp != null && exp.equals(act))) { pass++; }
        else { fail++; System.out.println("FAIL " + name + ": expected <" + exp + "> got <" + act + ">"); }
    }

    public static void main(String[] a) {
        // 电话号码
        eq("138 1234 5678", Logic.formatPhone("13812345678"), "format11");
        eq("138 1234 5678", Logic.formatPhone("138-1234 5678"), "formatDash");
        eq("01088886666", Logic.formatPhone("010 8888 6666"), "formatLandline");
        eq("+8613812345678", Logic.normalizePhone("+86 138 1234 5678"), "normalizePlus");
        eq(true, Logic.isValidPhone("13812345678"), "validPhone");
        eq(false, Logic.isValidPhone("ab"), "invalidPhone");
        eq(true, Logic.isValidPin("2580"), "pinOk");
        eq(false, Logic.isValidPin("258"), "pinShort");
        eq(false, Logic.isValidPin("25a0"), "pinAlpha");

        // 熄屏复位
        eq(true, Logic.shouldResetOnScreenOff(true, false), "offNormal");
        eq(false, Logic.shouldResetOnScreenOff(true, true), "offInCall");
        eq(false, Logic.shouldResetOnScreenOff(false, false), "offSetupNotDone");

        String me = "com.elder.launcher";
        eq(true, Logic.shouldResetOnScreenOn(true, false, "com.ss.android.ugc.aweme", "", me), "onDouyin");
        eq(true, Logic.shouldResetOnScreenOn(true, false, "com.android.settings", "", me), "onSettings");
        eq(false, Logic.shouldResetOnScreenOn(true, false, me, "", me), "onSelf");
        eq(false, Logic.shouldResetOnScreenOn(true, false, "com.tencent.mm", "com.tencent.mm.plugin.voip.ui.VideoActivity", me), "onWeChatIncomingCall");
        eq(false, Logic.shouldResetOnScreenOn(true, false, "com.tencent.mm", "com.tencent.mm.plugin.multitalk.ui.MultiTalkMainUI", me), "onWeChatGroupCall");
        eq(false, Logic.shouldResetOnScreenOn(true, false, "com.tencent.mm", "", me), "onWeChatUnknownPage");
        eq(true, Logic.shouldResetOnScreenOn(true, false, "com.tencent.mm", "com.tencent.mm.ui.chatting.ChattingUI", me), "onWeChatChatPage");
        eq(true, Logic.shouldResetOnScreenOn(true, false, "com.tencent.mm", "com.tencent.mm.ui.LauncherUI", me), "onWeChatHome");
        eq(true, Logic.shouldResetOnScreenOn(true, false, me, "com.elder.launcher.SetupActivity", me), "onSetupPage");
        eq(false, Logic.shouldResetOnScreenOn(true, false, me, "com.elder.launcher.MainActivity", me), "onElderPage");
        eq(true, Logic.shouldResetOnScreenOn(true, false, "com.android.settings", "com.android.settings.Settings", me), "onSystemSettings");
        eq(false, Logic.shouldResetOnScreenOn(true, false, "com.android.incallui", "", me), "onIncall");
        eq(false, Logic.shouldResetOnScreenOn(true, false, "com.android.systemui", "", me), "onKeyguard");
        eq(false, Logic.shouldResetOnScreenOn(true, true, "com.ss.android.ugc.aweme", "", me), "onBusy");
        eq(false, Logic.shouldResetOnScreenOn(false, false, "com.ss.android.ugc.aweme", "", me), "onDisabled");
        eq(false, Logic.shouldResetOnScreenOn(true, false, null, "", me), "onNull");

        // 微信搜索结果挑选（模拟微信搜索页的文字顺序）
        List<String> page1 = Arrays.asList("取消", "联系人", "小宝", "微信号: xiaobao123", "搜一搜 xiaobao123", "网络查找微信号：xiaobao123");
        eq(3, Logic.pickSearchResult(page1, "xiaobao123", "外孙"), "pickById");
        List<String> page2 = Arrays.asList("最常使用", "护工王阿姨", "联系人", "护工王阿姨", "群聊", "家庭群（护工王阿姨）", "搜索 护工王阿姨");
        eq(3, Logic.pickSearchResult(page2, "护工王阿姨", "护工"), "pickByRemarkPreferContacts");
        List<String> page3 = Arrays.asList("联系人", "外孙小宝", "搜一搜 小宝");
        eq(1, Logic.pickSearchResult(page3, "小宝", "外孙"), "pickContains");
        List<String> page4 = Arrays.asList("搜一搜 zzz", "网络查找微信号：zzz", "取消");
        eq(-1, Logic.pickSearchResult(page4, "zzz", "外孙"), "pickNone");
        eq(-1, Logic.pickSearchResult(page1, "  ", "外孙"), "pickEmptyKeyword");
        List<String> page5 = Arrays.asList("联系人", "XiaoBao123");
        eq(1, Logic.pickSearchResult(page5, "xiaobao123", "外孙"), "pickCaseInsensitive");

        // 录制步骤编码/解码
        List<int[]> steps = new java.util.ArrayList<>();
        steps.add(new int[]{100, 2000, 0}); steps.add(new int[]{500, 900, 1800}); steps.add(new int[]{980, 2200, 2500});
        String rec = Logic.encodeRecording(1080, 2340, steps);
        eq("1080,2340|100,2000,0;500,900,1800;980,2200,2500", rec, "encodeRec");
        List<int[]> d1 = Logic.decodeRecording(rec, 1080, 2340);
        eq(3, d1.size(), "decodeCount");
        eq(980, d1.get(2)[0], "decodeX");
        List<int[]> d2 = Logic.decodeRecording(rec, 720, 1560);
        eq(333, d2.get(1)[0], "scaleX");
        eq(600, d2.get(1)[1], "scaleY");
        eq(null, Logic.decodeRecording("garbage", 1080, 2340), "decodeBad");
        eq(null, Logic.decodeRecording("1080,2340|", 1080, 2340), "decodeEmpty");
        eq(1300L, Logic.replayDelay(200), "delayMin");
        eq(2500L, Logic.replayDelay(2500), "delayMid");
        eq(4500L, Logic.replayDelay(60000), "delayMax");

        // 免提按钮识别
        eq(true, Logic.isSpeakerLabel("免提"), "spk1");
        eq(true, Logic.isSpeakerLabel("扬声器"), "spk2");
        eq(true, Logic.isSpeakerLabel("Speaker"), "spk3");
        eq(false, Logic.isSpeakerLabel("静音"), "spk4");
        eq(false, Logic.isSpeakerLabel("拨号键盘"), "spk5");

        System.out.println("LogicTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
