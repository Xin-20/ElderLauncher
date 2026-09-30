package com.elder.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/** 老人看到的主页（同时也是手机桌面）。 */
public class MainActivity extends Activity {
    public static final String EXTRA_RESET = "reset";

    private Config cfg;
    private final View[] cards = new View[Config.COUNT];
    private final long[] shownPhotoVer = {-1, -1, -1, -1};
    private long lastTap;
    private AlertDialog dialog;

    private static final int[] CARD_IDS = {R.id.card0, R.id.card1, R.id.card2, R.id.card3};
    private static final int[] CARD_BG = {R.drawable.bg_card_pink, R.drawable.bg_card_blue,
            R.drawable.bg_card_green, R.drawable.bg_card_purple};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < Config.COUNT; i++) {
            final int idx = i;
            View card = findViewById(CARD_IDS[i]);
            cards[i] = card;
            card.setBackgroundResource(CARD_BG[i]);
            ImageView photo = card.findViewById(R.id.photo);
            View photoBox = card.findViewById(R.id.photo_box);
            photo.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> faceCrop(photo));
            photoBox.setClipToOutline(true);
            photoBox.setOnClickListener(v -> onContact(idx));
            photoBox.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
                }
            });
            View.OnClickListener l = v -> onContact(idx);
            photo.setOnClickListener(l);
            card.findViewById(R.id.button).setOnClickListener(l);
            card.setOnClickListener(l);
            if (!Config.isPhoneSlot(i)) {
                ((ImageView) card.findViewById(R.id.button_icon)).setImageResource(R.drawable.ic_video_white);
                ((TextView) card.findViewById(R.id.button_text)).setText("视频");
                ((TextView) card.findViewById(R.id.hint)).setText("点照片就能视频");
            }
        }
        findViewById(R.id.douyin).setOnClickListener(v -> {
            if (debounce()) Actions.openDouyin(this);
        });
        // 文字按卡片实际宽度自动缩放，保证号码、按钮文字完整显示
        findViewById(R.id.root).addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (r - l != ol - or || bt - t != ob - ot) v.post(this::fitAll);
        });
        // 子女入口：按住顶部标题区域往下拉 → 输入管理密码（老人随手点不会误触）
        PullDownLayout pull = findViewById(R.id.pull);
        View header = findViewById(R.id.header);
        header.post(() -> pull.setStartZoneHeight(header.getBottom() + (int) dp(8)));
        pull.setListener(() -> askPin(false));
        // 正常模式下的提示条：点一下切回老人模式
        findViewById(R.id.normal_banner).setOnClickListener(v -> askPin(true));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_RESET, false)) dismissDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!cfg.setupDone()) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        bind();
    }

    @Override
    public void onBackPressed() {
        // 老人模式（作为桌面）下按返回键不做任何事，防止误退出；正常模式下就是普通应用
        if (!cfg.elderMode()) super.onBackPressed();
    }

    private void bind() {
        findViewById(R.id.normal_banner).setVisibility(cfg.elderMode() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < Config.COUNT; i++) {
            View card = cards[i];
            ((TextView) card.findViewById(R.id.name)).setText(cfg.name(i));
            String v = cfg.value(i);
            ((TextView) card.findViewById(R.id.value)).setText(
                    Config.isPhoneSlot(i) ? Logic.formatPhone(v) : (v.isEmpty() ? "" : "微信号 " + v));
            long ver = cfg.photoVersion(i) + (cfg.hasPhoto(i) ? 1 : 0);
            if (ver != shownPhotoVer[i]) {
                shownPhotoVer[i] = ver;
                ImageView iv = card.findViewById(R.id.photo);
                Bitmap bm = cfg.hasPhoto(i) ? BitmapFactory.decodeFile(cfg.photoFile(i).getAbsolutePath()) : null;
                if (bm != null) iv.setImageBitmap(bm);
                else iv.setImageResource(R.drawable.ic_person);
                faceCrop(iv);
            }
        }
        Drawable wx = Actions.appIcon(this, Config.PKG_WECHAT);
        ImageView wxIcon = findViewById(R.id.wechat_icon);
        if (wx != null) wxIcon.setImageDrawable(wx); else wxIcon.setImageResource(R.drawable.ic_chat_green);
        Drawable dy = Actions.appIcon(this, Config.PKG_DOUYIN);
        ImageView dyIcon = findViewById(R.id.douyin_icon);
        if (dy != null) dyIcon.setImageDrawable(dy); else dyIcon.setImageResource(R.drawable.ic_music);
        findViewById(R.id.root).post(this::fitAll);
    }

    /** 像 centerCrop 一样铺满，但裁剪时多保留上方（人脸通常在照片上半部分），避免把头顶/脸裁掉。 */
    private void faceCrop(ImageView iv) {
        android.graphics.drawable.Drawable d = iv.getDrawable();
        int vw = iv.getWidth(), vh = iv.getHeight();
        if (d == null || vw <= 0 || vh <= 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return;
        float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        float scale = Math.max(vw / dw, vh / dh);
        float dx = (vw - dw * scale) / 2f;
        float dy = (vh - dh * scale) * 0.3f; // 多裁下方，少裁上方
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        iv.setImageMatrix(m);
    }

    private void fitAll() {
        for (View card : cards) {
            View row = card.findViewById(R.id.name_row);
            View btn = card.findViewById(R.id.button);
            fit(card.findViewById(R.id.button_text), 13, 10, -1);
            fit(card.findViewById(R.id.name), 19, 12, row.getWidth() - btn.getWidth() - (int) dp(4));
            fit(card.findViewById(R.id.value), 11, 8, -1);
            fit(card.findViewById(R.id.hint), 11, 8, -1);
        }
    }

    /** 从 maxDp 开始逐步缩小字号，直到文字在一行内放得下（最小 minDp）。 */
    private void fit(TextView tv, float maxDp, float minDp, int availPx) {
        int avail = availPx > 0 ? availPx : tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
        if (avail <= 0) return;
        String s = tv.getText().toString();
        Paint p = new Paint(tv.getPaint());
        float size = maxDp;
        while (size > minDp) {
            p.setTextSize(dp(size));
            if (p.measureText(s) <= avail) break;
            size -= 0.5f;
        }
        if (Math.abs(tv.getTextSize() - dp(size)) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    private boolean debounce() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTap < 1500) return false;
        lastTap = now;
        return true;
    }

    private void onContact(int i) {
        if (!debounce()) return;
        if (Config.isPhoneSlot(i)) Actions.call(this, cfg.value(i));
        else Actions.wechatVideo(this, i, cfg.name(i), cfg.value(i));
    }

    /** restoreElder=true：切回老人模式；否则进入子女配置页。 */
    private void askPin(boolean restoreElder) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        et.setGravity(Gravity.CENTER);
        et.setHint("4位数字");
        dismissDialog();
        dialog = new AlertDialog.Builder(this)
                .setTitle(restoreElder ? "切回老人模式：请输入管理密码" : "子女设置：请输入管理密码")
                .setView(et)
                .setPositiveButton(restoreElder ? "切回老人模式" : "进入设置", (d, w) -> {
                    if (!et.getText().toString().equals(cfg.pin())) {
                        Actions.toast(this, "密码不对");
                    } else if (restoreElder) {
                        Actions.setElderMode(this, true);
                        bind();
                        Actions.toast(this, "已切回老人模式，请把“常用联系”设为默认桌面");
                        startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_ASK_HOME, true));
                    } else {
                        startActivity(new Intent(this, SetupActivity.class));
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            try { dialog.dismiss(); } catch (Exception ignored) {}
        }
        dialog = null;
        AlertDialog d = Actions.lastDialog == null ? null : Actions.lastDialog.get();
        if (d != null && d.isShowing()) {
            try { d.dismiss(); } catch (Exception ignored) {}
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
