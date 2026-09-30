package com.ss.android.ugc.aweme;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.*;
public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i("MOCK_DOUYIN", "HOME_CREATED pid=" + Process.myPid());
        Button btn = new Button(this); btn.setText("抖音首页：点我进入二级页（模拟误触）");
        btn.setOnClickListener(v -> startActivity(new Intent(this, DeepActivity.class)));
        setContentView(btn);
    }
}
