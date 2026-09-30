package com.tencent.mm;
import android.content.Context;
import android.widget.*;
import android.view.*;
public class U {
    static TextView tv(Context c, String s, int size) { TextView t = new TextView(c); t.setText(s); t.setTextSize(size); t.setPadding(20,20,20,20); return t; }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
}
