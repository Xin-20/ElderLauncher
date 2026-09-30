package com.tencent.mm;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
public class LauncherUI extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        LinearLayout top = U.row(this);
        TextView title = U.tv(this, "微信", 20);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView search = new ImageView(this);
        search.setImageResource(android.R.drawable.ic_menu_search);
        search.setContentDescription("搜索");
        search.setClickable(true);
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchUI.class)));
        top.addView(search, new LinearLayout.LayoutParams(120, 120));
        ImageView plus = new ImageView(this);
        plus.setImageResource(android.R.drawable.ic_menu_add);
        plus.setContentDescription("更多功能按钮");
        top.addView(plus, new LinearLayout.LayoutParams(120, 120));
        root.addView(top);
        root.addView(U.tv(this, "文件传输助手", 16));
        root.addView(U.tv(this, "家庭群", 16));
        View sp = new View(this); root.addView(sp, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tabs = U.row(this);
        for (String s : new String[]{"微信", "通讯录", "发现", "我"}) { TextView t = U.tv(this, s, 14); t.setGravity(Gravity.CENTER); tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); }
        root.addView(tabs);
        setContentView(root);
    }
}
