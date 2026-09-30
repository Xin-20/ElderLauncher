package com.ss.android.ugc.aweme;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
public class DeepActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i("MOCK_DOUYIN", "DEEP_PAGE");
        TextView t = new TextView(this); t.setText("抖音二级页面（老人误触进来了）"); t.setTextSize(24);
        setContentView(t);
    }
}
