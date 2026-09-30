package com.elder.launcher;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

/**
 * 兜底：无障碍服务没开时，本应用作为默认桌面进程通常常驻，
 * 熄屏时也尝试回到主页（部分系统会拦截后台启动界面，所以正式使用仍需开启无障碍服务）。
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (ElderService.instance != null) return; // 服务在，由服务处理
                Config cfg = new Config(c);
                AudioManager am = (AudioManager) c.getSystemService(AUDIO_SERVICE);
                boolean busy = am != null && am.getMode() != AudioManager.MODE_NORMAL;
                if (!Logic.shouldResetOnScreenOff(cfg.resetEnabled(), busy)) return;
                try {
                    Intent i = Actions.elderHomeIntent(c);
                    i.putExtra(MainActivity.EXTRA_RESET, true);
                    c.startActivity(i);
                } catch (Exception ignored) {}
            }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }
}
