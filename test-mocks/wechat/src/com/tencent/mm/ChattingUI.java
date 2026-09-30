package com.tencent.mm;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
public class ChattingUI extends Activity {
    String name; LinearLayout panel;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        name = getIntent().getStringExtra("name");
        LinearLayout root = U.col(this);
        root.addView(U.tv(this, name, 20));
        View sp = new View(this); root.addView(sp, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = U.row(this);
        bar.addView(new EditText(this), new LinearLayout.LayoutParams(0, -2, 1));
        ImageView more = new ImageView(this);
        more.setImageResource(android.R.drawable.ic_input_add);
        more.setContentDescription("更多功能按钮，已折叠");
        more.setClickable(true);
        more.setOnClickListener(v -> panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        bar.addView(more, new LinearLayout.LayoutParams(120, 120));
        root.addView(bar);
        panel = U.row(this);
        panel.setVisibility(View.GONE);
        for (String s : new String[]{"相册", "拍摄", "视频通话", "位置"}) {
            LinearLayout cell = U.col(this); cell.setClickable(true); cell.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this); ic.setImageResource(android.R.drawable.ic_menu_camera); cell.addView(ic);
            cell.addView(U.tv(this, s, 13));
            if (s.equals("视频通话")) cell.setOnClickListener(v -> sheet());
            panel.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(panel);
        setContentView(root);
    }
    void sheet() {
        LinearLayout l = U.col(this);
        AlertDialog d = new AlertDialog.Builder(this).setView(l).create();
        TextView v = U.tv(this, "视频通话", 18); v.setClickable(true);
        v.setOnClickListener(x -> { d.dismiss(); Intent i = new Intent(this, VoipUI.class); i.putExtra("name", name); startActivity(i); });
        TextView a = U.tv(this, "语音通话", 18); a.setClickable(true);
        TextView c = U.tv(this, "取消", 18); c.setClickable(true); c.setOnClickListener(x -> d.dismiss());
        l.addView(v); l.addView(a); l.addView(c);
        d.show();
    }
}
