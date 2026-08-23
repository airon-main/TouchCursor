package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import ru.vasys.keymouse.core.CursorColor;
import ru.vasys.keymouse.core.CursorStyle;
import ru.vasys.keymouse.view.CursorGlyphView;

/**
 * Draws the cursor in one fixed accessibility overlay.
 *
 * Pointer coordinates are local to the actual laid-out overlay viewport. Gesture
 * coordinates are derived from that same point plus the viewport's real screen origin.
 * This matters on landscape OEM builds where system bars can offset or shrink an
 * accessibility overlay relative to the app-visible display metrics.
 */
public final class CursorOverlay {
    private final AccessibilityService service;
    private final WindowManager wm;
    private final FrameLayout root;
    private final CursorGlyphView cursor;
    private final WindowManager.LayoutParams params;

    private int cursorSizePx;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int viewportScreenX;
    private int viewportScreenY;
    private float x;
    private float y;
    private boolean visible;
    private boolean added;
    private boolean wrapEdges;
    private boolean viewportChangedSinceMove;
    private CursorStyle style;

    public CursorOverlay(AccessibilityService service, CursorStyle style, boolean wrapEdges,
                         CursorColor color, int cursorSizeDp) {
        this.service = service;
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        cursorSizePx = dp(cursorSizeDp);

        root = new FrameLayout(service);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> refreshViewportFromRoot());

        cursor = new CursorGlyphView(service);
        root.addView(cursor, new FrameLayout.LayoutParams(cursorSizePx, cursorSizePx));

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
        updateFallbackViewport();
        x = (viewportWidth - 1) / 2f;
        y = (viewportHeight - 1) / 2f;
        setStyle(style);
        setCursorColor(color);
        applyCursorPosition();
    }

    public void destroy() {
        removeOverlayWindow();
        visible = false;
    }

    /** Refresh viewport after rotation/configuration changes. */
    public void refreshDisplayBounds() {
        if (added && root.getWidth() > 0 && root.getHeight() > 0) refreshViewportFromRoot();
        else updateFallbackViewport();
        normalizePointer();
        applyCursorPosition();
    }

    private void updateFallbackViewport() {
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        viewportWidth = Math.max(1, metrics.widthPixels);
        viewportHeight = Math.max(1, metrics.heightPixels);
        viewportScreenX = 0;
        viewportScreenY = 0;
    }

    private void refreshViewportFromRoot() {
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) return;
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        if (viewportWidth != width || viewportHeight != height
                || viewportScreenX != location[0] || viewportScreenY != location[1]) {
            viewportChangedSinceMove = true;
        }
        viewportWidth = width;
        viewportHeight = height;
        viewportScreenX = location[0];
        viewportScreenY = location[1];
        normalizePointer();
        applyCursorPosition();
    }

    /** Moves the logical pointer. Returns true when an active touch must be rebased. */
    public boolean moveBy(float dx, float dy) {
        float rawX = x + dx;
        float rawY = y + dy;
        boolean needsRebase = viewportChangedSinceMove;
        viewportChangedSinceMove = false;
        if (wrapEdges) {
            needsRebase |= rawX < 0 || rawX >= viewportWidth || rawY < 0 || rawY >= viewportHeight;
            x = wrap(rawX, viewportWidth);
            y = wrap(rawY, viewportHeight);
        } else {
            x = clamp(rawX, 0, viewportWidth - 1);
            y = clamp(rawY, 0, viewportHeight - 1);
        }
        applyCursorPosition();
        return needsRebase;
    }

    public void setVisible(boolean value) {
        if (visible == value) return;
        visible = value;
        if (value) addOverlayWindow(); else removeOverlayWindow();
    }

    public boolean isVisible() {
        return visible;
    }

    /** Exact screen-space coordinate used for Accessibility gestures. */
    public float screenX() {
        return viewportScreenX + x;
    }

    /** Exact screen-space coordinate used for Accessibility gestures. */
    public float screenY() {
        return viewportScreenY + y;
    }

    public void setStyle(CursorStyle style) {
        this.style = style == null ? CursorStyle.ARROW : style;
        cursor.setStyle(this.style);
        applyCursorPosition();
    }

    public void setCursorColor(CursorColor color) {
        cursor.setCursorColor(color);
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
        normalizePointer();
        applyCursorPosition();
    }

    public void setScrollMode(boolean scrollMode) {
        cursor.setCursorAlpha(scrollMode ? 0.80f : 1.0f);
    }

    private void addOverlayWindow() {
        if (added) return;
        try {
            wm.addView(root, params);
            added = true;
            root.post(this::refreshViewportFromRoot);
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

    private float hotspotOffsetX() {
        CursorStyle resolved = style == null ? CursorStyle.ARROW : style;
        return cursorSizePx * resolved.hotspotX;
    }

    private float hotspotOffsetY() {
        CursorStyle resolved = style == null ? CursorStyle.ARROW : style;
        return cursorSizePx * resolved.hotspotY;
    }

    private void normalizePointer() {
        x = wrapEdges ? wrap(x, viewportWidth) : clamp(x, 0, viewportWidth - 1);
        y = wrapEdges ? wrap(y, viewportHeight) : clamp(y, 0, viewportHeight - 1);
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
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
