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
        LinearLayout top = U.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = U.tv(this, "微信", 18); title.setGravity(Gravity.CENTER);
        View padL = new View(this); top.addView(padL, new LinearLayout.LayoutParams(dp(96), 1));
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView search = new ImageView(this);
        search.setImageResource(android.R.drawable.ic_menu_search);
        search.setClickable(true);
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchUI.class)));
        top.addView(search, new LinearLayout.LayoutParams(dp(40), dp(40)));
        View gap = new View(this); top.addView(gap, new LinearLayout.LayoutParams(dp(1), 1));
        ImageView plus = new ImageView(this);
        plus.setImageResource(android.R.drawable.ic_menu_add);
        plus.setClickable(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(40), dp(40)); plp.rightMargin = dp(10);
        top.addView(plus, plp);
        
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(U.tv(this, "文件传输助手", 16));
        root.addView(U.tv(this, "家庭群", 16));
        View sp = new View(this); root.addView(sp, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tabs = U.row(this);
        for (String s : new String[]{"微信", "通讯录", "发现", "我"}) { TextView t = U.tv(this, s, 14); t.setGravity(Gravity.CENTER); tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); }
        root.addView(tabs);
        setContentView(root);
    }
}
