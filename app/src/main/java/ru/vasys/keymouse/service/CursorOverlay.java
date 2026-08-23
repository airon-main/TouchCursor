package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import ru.vasys.keymouse.core.CursorColor;
import ru.vasys.keymouse.core.CursorStyle;

/**
 * Draws the cursor in one fixed full-screen accessibility overlay.
 *
 * The logical hotspot (x/y) is the only pointer coordinate in the app. The
 * cursor image is rendered around that exact coordinate and Accessibility
 * gestures use the same value directly.
 *
 * The overlay is attached only while the mouse is visible. Display bounds are
 * cached and refreshed when the overlay is shown or Android reports a
 * configuration change; movement itself does not query WindowManager.
 */
public final class CursorOverlay {
    private static final int IMAGE_PADDING_DP = 2;

    private final AccessibilityService service;
    private final WindowManager wm;
    private final FrameLayout root;
    private final FrameLayout cursor;
    private final ImageView shadow;
    private final ImageView foreground;
    private final WindowManager.LayoutParams params;
    private int cursorSizePx;
    private final int imagePaddingPx;

    private int screenWidth;
    private int screenHeight;
    private float x;
    private float y;
    private boolean visible;
    private boolean added;
    private boolean wrapEdges;
    private CursorStyle style;
    private CursorColor color;

    public CursorOverlay(AccessibilityService service, CursorStyle style, boolean wrapEdges,
                         CursorColor color, int cursorSizeDp) {
        this.service = service;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.cursorSizePx = dp(cursorSizeDp);
        this.imagePaddingPx = dp(IMAGE_PADDING_DP);

        root = new FrameLayout(service);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        cursor = new FrameLayout(service);
        cursor.setClipChildren(false);
        cursor.setClipToPadding(false);
        FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(cursorSizePx, cursorSizePx);
        root.addView(cursor, cursorParams);

        shadow = image(service, imagePaddingPx);
        foreground = image(service, imagePaddingPx);
        shadow.setScaleX(1.13f);
        shadow.setScaleY(1.13f);
        cursor.addView(shadow, new FrameLayout.LayoutParams(-1, -1));
        cursor.addView(foreground, new FrameLayout.LayoutParams(-1, -1));

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;

        this.wrapEdges = wrapEdges;
        this.color = color == null ? CursorColor.WHITE : color;
        updateScreenBounds();
        x = (screenWidth - 1) / 2f;
        y = (screenHeight - 1) / 2f;
        setStyle(style);
        applyTint();
        applyCursorPosition();
    }

    public void destroy() {
        removeOverlayWindow();
        visible = false;
    }

    /** Refresh cached real-display bounds after rotation / configuration changes. */
    public void refreshDisplayBounds() {
        updateScreenBounds();
        x = normalizeForCurrentMode(x, screenWidth);
        y = normalizeForCurrentMode(y, screenHeight);
        applyCursorPosition();
    }

    private void updateScreenBounds() {
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        screenWidth = Math.max(1, size.x);
        screenHeight = Math.max(1, size.y);
    }

    /**
     * Moves the logical pointer. Returns true when either axis wrapped to the
     * opposite edge.
     */
    public boolean moveBy(float dx, float dy) {
        float rawX = x + dx;
        float rawY = y + dy;
        boolean wrapped = false;
        if (wrapEdges) {
            wrapped = rawX < 0 || rawX >= screenWidth || rawY < 0 || rawY >= screenHeight;
            x = wrap(rawX, screenWidth);
            y = wrap(rawY, screenHeight);
        } else {
            x = clamp(rawX, 0, screenWidth - 1);
            y = clamp(rawY, 0, screenHeight - 1);
        }
        applyCursorPosition();
        return wrapped;
    }

    public void setVisible(boolean value) {
        if (visible == value) return;
        visible = value;
        if (value) {
            refreshDisplayBounds();
            addOverlayWindow();
        } else {
            removeOverlayWindow();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    /** Exact screen-space pointer coordinate used for Accessibility gestures. */
    public float screenX() {
        return x;
    }

    /** Exact screen-space pointer coordinate used for Accessibility gestures. */
    public float screenY() {
        return y;
    }

    public void setStyle(CursorStyle style) {
        this.style = style == null ? CursorStyle.ARROW : style;
        shadow.setImageResource(this.style.drawableRes);
        foreground.setImageResource(this.style.drawableRes);
        applyCursorPosition();
    }

    public void setCursorColor(CursorColor color) {
        this.color = color == null ? CursorColor.WHITE : color;
        applyTint();
    }

    public void setCursorSizeDp(int sizeDp) {
        int next = dp(sizeDp);
        if (next == cursorSizePx) return;
        cursorSizePx = next;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cursor.getLayoutParams();
        lp.width = cursorSizePx;
        lp.height = cursorSizePx;
        cursor.setLayoutParams(lp);
        applyCursorPosition();
    }

    public void setWrapEdges(boolean enabled) {
        wrapEdges = enabled;
        x = normalizeForCurrentMode(x, screenWidth);
        y = normalizeForCurrentMode(y, screenHeight);
        applyCursorPosition();
    }

    public void setScrollMode(boolean scrollMode) {
        // Keep the user-selected cursor color in every mode. Runtime mode is
        // already surfaced through a Toast when Normal/Scroll changes.
        foreground.setAlpha(scrollMode ? 0.80f : 1.0f);
    }

    private void addOverlayWindow() {
        if (added) return;
        try {
            wm.addView(root, params);
            added = true;
        } catch (RuntimeException ignored) {
            added = false;
        }
    }

    private void removeOverlayWindow() {
        if (!added) return;
        try {
            wm.removeViewImmediate(root);
        } catch (RuntimeException ignored) {
        }
        added = false;
    }

    private void applyCursorPosition() {
        cursor.setTranslationX(x - hotspotOffsetX());
        cursor.setTranslationY(y - hotspotOffsetY());
    }

    private void applyTint() {
        int foregroundColor = color == null ? Color.WHITE : color.argb;
        foreground.setColorFilter(foregroundColor);
        shadow.setColorFilter(isLight(foregroundColor) ? Color.BLACK : Color.WHITE);
    }

    private float hotspotOffsetX() {
        CursorStyle resolved = style == null ? CursorStyle.ARROW : style;
        int content = Math.max(1, cursorSizePx - imagePaddingPx * 2);
        return imagePaddingPx + content * resolved.hotspotX;
    }

    private float hotspotOffsetY() {
        CursorStyle resolved = style == null ? CursorStyle.ARROW : style;
        int content = Math.max(1, cursorSizePx - imagePaddingPx * 2);
        return imagePaddingPx + content * resolved.hotspotY;
    }

    private float normalizeForCurrentMode(float value, int size) {
        return wrapEdges ? wrap(value, size) : clamp(value, 0, size - 1);
    }

    private static ImageView image(Context context, int paddingPx) {
        ImageView result = new ImageView(context);
        result.setScaleType(ImageView.ScaleType.FIT_CENTER);
        result.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static boolean isLight(int argb) {
        double r = Color.red(argb) / 255.0;
        double g = Color.green(argb) / 255.0;
        double b = Color.blue(argb) / 255.0;
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance >= 0.52;
    }

    private static float wrap(float value, int size) {
        if (size <= 1) return 0;
        float result = value % size;
        if (result < 0) result += size;
        return result;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
