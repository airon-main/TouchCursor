package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import ru.vasys.keymouse.core.CursorStyle;

public final class CursorOverlay {
    private static final int SIZE_DP = 42;

    private final AccessibilityService service;
    private final WindowManager wm;
    private final FrameLayout view;
    private final ImageView shadow;
    private final ImageView foreground;
    private final WindowManager.LayoutParams params;
    private int screenWidth;
    private int screenHeight;
    private float x;
    private float y;
    private boolean visible;
    private boolean added;
    private CursorStyle style;

    public CursorOverlay(AccessibilityService service, CursorStyle style) {
        this.service = service;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.view = new FrameLayout(service);
        this.shadow = image(service);
        this.foreground = image(service);
        shadow.setScaleX(1.13f);
        shadow.setScaleY(1.13f);
        shadow.setColorFilter(Color.BLACK);
        foreground.setColorFilter(Color.WHITE);
        view.addView(shadow, new FrameLayout.LayoutParams(-1, -1));
        view.addView(foreground, new FrameLayout.LayoutParams(-1, -1));

        int size = dp(SIZE_DP);
        this.params = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;

        updateScreenBounds();
        x = screenWidth / 2f;
        y = screenHeight / 2f;
        setStyle(style);
        updateLayoutPosition();
        view.setVisibility(View.GONE);
        wm.addView(view, params);
        added = true;
    }

    public void destroy() {
        if (!added) return;
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
        }
        added = false;
    }

    public void updateScreenBounds() {
        Rect r = new Rect();
        wm.getDefaultDisplay().getRectSize(r);
        screenWidth = Math.max(1, r.width());
        screenHeight = Math.max(1, r.height());
    }

    public void moveBy(float dx, float dy) {
        updateScreenBounds();
        x = clamp(x + dx, 0, screenWidth - 1);
        y = clamp(y + dy, 0, screenHeight - 1);
        updateLayoutPosition();
        applyLayout();
    }

    public void setVisible(boolean value) {
        visible = value;
        view.setVisibility(value ? View.VISIBLE : View.GONE);
        if (value) {
            updateScreenBounds();
            x = clamp(x, 0, screenWidth - 1);
            y = clamp(y, 0, screenHeight - 1);
            updateLayoutPosition();
            applyLayout();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public float x() { return x; }
    public float y() { return y; }

    public void setStyle(CursorStyle style) {
        this.style = style == null ? CursorStyle.ARROW : style;
        shadow.setImageResource(this.style.drawableRes);
        foreground.setImageResource(this.style.drawableRes);
        updateLayoutPosition();
        applyLayout();
    }

    public void setScrollMode(boolean scrollMode) {
        foreground.setColorFilter(scrollMode ? 0xffffc107 : Color.WHITE);
    }

    private void updateLayoutPosition() {
        CursorStyle resolved = style == null ? CursorStyle.ARROW : style;
        params.x = Math.round(x - params.width * resolved.hotspotX);
        params.y = Math.round(y - params.height * resolved.hotspotY);
    }

    private void applyLayout() {
        if (!added) return;
        try {
            wm.updateViewLayout(view, params);
        } catch (RuntimeException ignored) {
        }
    }

    private static ImageView image(Context context) {
        ImageView result = new ImageView(context);
        result.setScaleType(ImageView.ScaleType.FIT_CENTER);
        result.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        return result;
    }

    private int dp(int value) {
        return dp(service, value);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
