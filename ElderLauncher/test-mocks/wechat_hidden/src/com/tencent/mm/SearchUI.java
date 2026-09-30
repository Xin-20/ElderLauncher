package com.tencent.mm;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.widget.*;
import android.view.*;
public class SearchUI extends Activity {
    LinearLayout results;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        LinearLayout top = U.row(this);
        EditText et = new EditText(this);
        et.setHint("搜索");
        top.addView(et, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cancel = U.tv(this, "取消", 16); cancel.setOnClickListener(v -> finish());
        top.addView(cancel);
        root.addView(top);
        results = U.col(this);
        root.addView(results);
        setContentView(root);
        et.requestFocus();
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) { results.postDelayed(() -> show(s.toString()), 600); }
        });
    }
    void show(String q) {
        results.removeAllViews();
        if (q.isEmpty()) return;
        results.addView(U.tv(this, "联系人", 13));
        String[][] db = {{"小宝", "xiaobao123"}, {"王阿姨", "hugong888"}, {"小宝同学", "xb_friend"}};
        for (String[] c : db) {
            if (c[0].contains(q) || c[1].equals(q)) {
                LinearLayout item = U.col(this);
                item.setClickable(true);
                item.addView(U.tv(this, c[0], 18));
                if (c[1].equals(q)) item.addView(U.tv(this, "微信号: " + c[1], 13));
                final String name = c[0];
                item.setOnClickListener(v -> { Intent i = new Intent(this, ChattingUI.class); i.putExtra("name", name); startActivity(i); });
                results.addView(item);
            }
        }
        TextView s1 = U.tv(this, "搜一搜 " + q, 15); s1.setClickable(true); results.addView(s1);
        TextView s2 = U.tv(this, "网络查找微信号：" + q, 15); s2.setClickable(true); results.addView(s2);
    }
}
