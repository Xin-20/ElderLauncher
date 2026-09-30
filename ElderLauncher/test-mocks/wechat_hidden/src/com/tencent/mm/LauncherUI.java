package com.tencent.mm;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
public class LauncherUI extends Activity {
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout root = U.col(this);
        // 整个标题栏是一个自绘 View：无障碍树里看不到任何图标（模拟最坏情况）
        View top = new View(this) {
            android.graphics.Paint p = new android.graphics.Paint();
            @Override protected void onDraw(android.graphics.Canvas c) {
                p.setTextSize(dp(18)); c.drawText("微信", getWidth() / 2f - dp(18), dp(30), p);
                c.drawCircle(getWidth() - dp(71), dp(24), dp(10), p); c.drawCircle(getWidth() - dp(30), dp(24), dp(10), p);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float fromRight = getWidth() - e.getX();
                    if (fromRight > dp(51) && fromRight < dp(91)) startActivity(new Intent(LauncherUI.this, SearchUI.class));
                }
                return true;
            }
        };
        top.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(U.tv(this, "文件传输助手", 16));
        root.addView(U.tv(this, "家庭群", 16));
        TextView xb = U.tv(this, "小宝（星标）", 18); xb.setClickable(true);
        xb.setOnClickListener(v -> { Intent i = new Intent(this, ChattingUI.class); i.putExtra("name", "小宝"); startActivity(i); });
        root.addView(xb, new LinearLayout.LayoutParams(-1, dp(72)));

        View sp = new View(this); root.addView(sp, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tabs = U.row(this);
        for (String s : new String[]{"微信", "通讯录", "发现", "我"}) { TextView t = U.tv(this, s, 14); t.setGravity(Gravity.CENTER); tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); }
        root.addView(tabs);
        setContentView(root);
    }
}
