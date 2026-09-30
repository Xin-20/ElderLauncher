package com.elder.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 顶部“下拉进入子女设置”：只有从页面顶部区域（标题栏）按住并向下拉足够远才触发，
 * 老人随手点、轻划都不会误触。
 * 子视图约定：第 0 个是提示条（位于顶部，被内容盖住），第 1 个是页面内容。
 */
public class PullDownLayout extends FrameLayout {
    public interface Listener { void onPulled(); }

    private Listener listener;
    private final int touchSlop;
    private final float density;
    private float downX, downY;
    private boolean dragging, startInZone;
    private int startZonePx;

    public PullDownLayout(Context c) { this(c, null); }

    public PullDownLayout(Context c, AttributeSet a) {
        super(c, a);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        density = c.getResources().getDisplayMetrics().density;
        startZonePx = (int) (110 * density);
    }

    public void setListener(Listener l) { listener = l; }
    public void setStartZoneHeight(int px) { startZonePx = px; }

    /** 需要拉多远才触发 */
    int thresholdPx() { return (int) (130 * density); }

    private View content() { return getChildAt(1); }
    private TextView hint() { return (TextView) getChildAt(0); }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                startInZone = downY < startZonePx;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!startInZone) break;
                float dy = e.getY() - downY, dx = Math.abs(e.getX() - downX);
                if (dy > touchSlop * 2 && dy > dx * 1.5f) {
                    dragging = true;
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) {
            // 按在标题栏这类不可点击的区域时，事件会直接交给本布局，这里自己判断是否开始下拉
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    startInZone = downY < startZonePx;
                    return startInZone;
                case MotionEvent.ACTION_MOVE:
                    if (!startInZone) return false;
                    float ddy = e.getY() - downY, ddx = Math.abs(e.getX() - downX);
                    if (ddy > touchSlop * 2 && ddy > ddx * 1.5f) dragging = true;
                    else return true;
                    break;
                default:
                    return startInZone;
            }
        }
        float dy = Math.max(0, e.getY() - downY);
        float shift = dy * 0.55f; // 带阻尼
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                content().setTranslationY(shift);
                hint().setVisibility(VISIBLE);
                hint().setAlpha(Math.min(1f, shift / (thresholdPx() * 0.6f)));
                hint().setText(shift >= thresholdPx() ? "↑ 松手进入子女设置" : "↓ 继续往下拉，进入子女设置");
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean fire = e.getActionMasked() == MotionEvent.ACTION_UP && shift >= thresholdPx();
                dragging = false;
                content().animate().translationY(0).setDuration(200).start();
                hint().animate().alpha(0).setDuration(200).withEndAction(() -> hint().setVisibility(INVISIBLE)).start();
                if (fire && listener != null) listener.onPulled();
                return true;
            default:
                return true;
        }
    }
}
