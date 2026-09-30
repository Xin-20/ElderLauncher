package com.tencent.mm;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
public class VoipUI extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String name = getIntent().getStringExtra("name");
        Log.i("MOCK_WECHAT", "VIDEO_CALL_STARTED to=" + name);
        setContentView(U.tv(this, "正在视频呼叫 " + name + "\n等待对方接受邀请", 22));
    }
}
