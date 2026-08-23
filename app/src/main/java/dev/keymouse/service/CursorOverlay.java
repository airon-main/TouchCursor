package dev.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public final class CursorOverlay {
    private static final int SIZE_DP = 42;

    private final AccessibilityService service;
    private final WindowManager wm;
    private final CursorView view;
    private final WindowManager.LayoutParams params;
    private int screenWidth;
    private int screenHeight;
    private float x;
    private float y;
    private boolean visible = true;

    public CursorOverlay(AccessibilityService service, String style) {
        this.service = service;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.view = new CursorView(service);
        this.view.setStyle(style);
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
        syncParams();
        wm.addView(view, params);
    }

    public void destroy() {
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
        }
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
        syncParams();
    }

    private void syncParams() {
        int half = params.width / 2;
        params.x = Math.round(x) - half;
        params.y = Math.round(y) - half;
        if (visible) {
            try {
                wm.updateViewLayout(view, params);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void setVisible(boolean value) {
        visible = value;
        view.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    public boolean isVisible() {
        return visible;
    }

    public float x() { return x; }
    public float y() { return y; }

    public void setStyle(String style) {
        view.setStyle(style);
    }

    public void setTouchActive(boolean active) {
        view.setTouchActive(active);
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CursorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String style = "CROSSHAIR";
        private boolean touchActive;

        CursorView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 2.4f));
            paint.setColor(0xffffffff);
            paint.setStrokeCap(Paint.Cap.ROUND);
            shadow.setStyle(Paint.Style.STROKE);
            shadow.setStrokeWidth(dp(context, 4.5f));
            shadow.setColor(0xff111111);
            shadow.setStrokeCap(Paint.Cap.ROUND);
        }

        void setStyle(String style) {
            this.style = style == null ? "CROSSHAIR" : style;
            invalidate();
        }

        void setTouchActive(boolean active) {
            touchActive = active;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) * 0.28f;
            drawShape(c, shadow, cx, cy, r);
            drawShape(c, paint, cx, cy, r);
            if (touchActive) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(getContext(), 2f));
                p.setColor(0xffffffff);
                c.drawCircle(cx, cy, r * 1.35f, p);
            }
        }

        private void drawShape(Canvas c, Paint p, float cx, float cy, float r) {
            switch (style) {
                case "ARROW":
                    Path arrow = new Path();
                    arrow.moveTo(cx - r * 0.65f, cy - r);
                    arrow.lineTo(cx + r * 0.55f, cy + r * 0.15f);
                    arrow.lineTo(cx + r * 0.05f, cy + r * 0.15f);
                    arrow.lineTo(cx + r * 0.55f, cy + r);
                    arrow.moveTo(cx + r * 0.05f, cy + r * 0.15f);
                    arrow.lineTo(cx - r * 0.65f, cy - r);
                    c.drawPath(arrow, p);
                    break;
                case "DOT":
                    Paint fill = new Paint(p);
                    fill.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, r * 0.42f, fill);
                    break;
                case "RING":
                    c.drawCircle(cx, cy, r * 0.75f, p);
                    break;
                case "PLUS":
                    c.drawLine(cx - r, cy, cx + r, cy, p);
                    c.drawLine(cx, cy - r, cx, cy + r, p);
                    break;
                case "CROSSHAIR":
                default:
                    c.drawCircle(cx, cy, r * 0.72f, p);
                    c.drawLine(cx - r, cy, cx - r * 0.3f, cy, p);
                    c.drawLine(cx + r * 0.3f, cy, cx + r, cy, p);
                    c.drawLine(cx, cy - r, cx, cy - r * 0.3f, p);
                    c.drawLine(cx, cy + r * 0.3f, cx, cy + r, p);
                    break;
            }
        }

        private static float dp(Context c, float v) {
            return v * c.getResources().getDisplayMetrics().density;
        }
    }
}
