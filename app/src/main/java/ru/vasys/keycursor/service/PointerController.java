package ru.vasys.keycursor.service;

import android.accessibilityservice.AccessibilityService;

import ru.vasys.keycursor.core.CursorColor;
import ru.vasys.keycursor.core.CursorStyle;
import ru.vasys.keycursor.core.MouseMode;

public final class PointerController {
    private final CursorOverlay overlay;
    private final ContinuousTouchController touch;
    private final AccessibilityScrollController scroller;

    private boolean leftHeld;
    private MouseMode mode = MouseMode.NORMAL;
    private MouseMode lastNonScrollMode = MouseMode.NORMAL;

    public PointerController(AccessibilityService service, CursorStyle cursorStyle, boolean wrapEdges,
                             CursorColor cursorColor, int cursorSizeDp) {
        overlay = new CursorOverlay(service, cursorStyle, wrapEdges, cursorColor, cursorSizeDp);
        touch = new ContinuousTouchController(service);
        scroller = new AccessibilityScrollController(service);
    }

    public void destroy() {
        hide();
        overlay.destroy();
    }

    public void move(float dx, float dy) {
        if (!overlay.isVisible() || mode == MouseMode.SCROLL) return;
        boolean needsRebase = overlay.moveBy(dx, dy);
        if (leftHeld || mode == MouseMode.HOLD) {
            if (needsRebase) touch.rebase(overlay.screenX(), overlay.screenY());
            else touch.moveTo(overlay.screenX(), overlay.screenY());
        }
    }

    public boolean scroll(float dx, float dy) {
        if (!overlay.isVisible() || mode != MouseMode.SCROLL) return false;
        return scroller.scrollAt(overlay.screenX(), overlay.screenY(), dx, dy);
    }

    public void setStrokeListener(ContinuousTouchController.StrokeListener listener) {
        touch.setStrokeListener(listener);
    }

    public void leftDown() {
        if (!overlay.isVisible() || leftHeld) return;
        leftHeld = true;
        syncTouchState();
    }

    public void leftUp() {
        if (!leftHeld) return;
        leftHeld = false;
        syncTouchState();
    }

    public void setMode(MouseMode next) {
        if (!overlay.isVisible() || next == null || mode == next) return;
        if (next == MouseMode.SCROLL) {
            if (mode != MouseMode.SCROLL) lastNonScrollMode = mode;
        } else {
            lastNonScrollMode = next;
        }
        mode = next;
        overlay.setMouseMode(mode);
        syncTouchState();
    }

    public void toggleNormalHoldMode() {
        if (mode == MouseMode.SCROLL) return;
        setMode(mode == MouseMode.NORMAL ? MouseMode.HOLD : MouseMode.NORMAL);
    }

    public void toggleScrollMode() {
        if (mode == MouseMode.SCROLL) setMode(lastNonScrollMode);
        else setMode(MouseMode.SCROLL);
    }

    public void show() {
        if (overlay.isVisible()) return;
        mode = MouseMode.NORMAL;
        lastNonScrollMode = MouseMode.NORMAL;
        leftHeld = false;
        overlay.setMouseMode(mode);
        overlay.setVisible(true);
    }

    public void hide() {
        leftHeld = false;
        mode = MouseMode.NORMAL;
        lastNonScrollMode = MouseMode.NORMAL;
        overlay.setMouseMode(mode);
        if (touch.isRequestedDown()) touch.release();
        overlay.setVisible(false);
    }

    public void toggleVisibility() {
        if (overlay.isVisible()) hide(); else show();
    }

    public boolean isVisible() {
        return overlay.isVisible();
    }

    public MouseMode mode() {
        return mode;
    }

    public void setCursorStyle(CursorStyle style) {
        overlay.setStyle(style);
    }

    public void setWrapEdges(boolean enabled) {
        overlay.setWrapEdges(enabled);
    }

    public void setCursorColor(CursorColor color) {
        overlay.setCursorColor(color);
    }

    public void setCursorSizeDp(int sizeDp) {
        overlay.setCursorSizeDp(sizeDp);
    }

    public void refreshDisplayBounds() {
        overlay.refreshDisplayBounds();
    }

    private void syncTouchState() {
        boolean shouldHold = overlay.isVisible() && (leftHeld || mode == MouseMode.HOLD);
        if (shouldHold && !touch.isRequestedDown()) {
            touch.press(overlay.screenX(), overlay.screenY());
        } else if (!shouldHold && touch.isRequestedDown()) {
            touch.release();
        }
    }
}
