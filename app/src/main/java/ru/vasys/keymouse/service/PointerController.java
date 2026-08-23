package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;

import ru.vasys.keymouse.core.CursorColor;
import ru.vasys.keymouse.core.CursorStyle;

public final class PointerController {
    private final CursorOverlay overlay;
    private final ContinuousTouchController touch;

    private boolean leftHeld;
    private boolean scrollMode;

    public PointerController(AccessibilityService service, CursorStyle cursorStyle, boolean wrapEdges, CursorColor cursorColor, int cursorSizeDp) {
        overlay = new CursorOverlay(service, cursorStyle, wrapEdges, cursorColor, cursorSizeDp);
        touch = new ContinuousTouchController(service);
    }

    public void destroy() {
        hide();
        overlay.destroy();
    }

    public void move(float dx, float dy) {
        if (!overlay.isVisible()) return;
        boolean wrapped = overlay.moveBy(dx, dy);
        if (leftHeld || scrollMode) {
            if (wrapped) touch.rebase(overlay.screenX(), overlay.screenY());
            else touch.moveTo(overlay.screenX(), overlay.screenY());
        }
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

    public void toggleScrollMode() {
        if (!overlay.isVisible()) return;
        scrollMode = !scrollMode;
        overlay.setScrollMode(scrollMode);
        syncTouchState();
    }

    public void show() {
        if (overlay.isVisible()) return;
        scrollMode = false;
        leftHeld = false;
        overlay.setScrollMode(false);
        overlay.setVisible(true);
    }

    public void hide() {
        leftHeld = false;
        scrollMode = false;
        overlay.setScrollMode(false);
        if (touch.isRequestedDown()) touch.release();
        overlay.setVisible(false);
    }

    public void toggleVisibility() {
        if (overlay.isVisible()) hide(); else show();
    }

    public boolean isVisible() {
        return overlay.isVisible();
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

    public void setForegroundPackage(String packageName) {
        overlay.setForegroundPackage(packageName);
    }

    public boolean isScrollMode() {
        return scrollMode;
    }

    private void syncTouchState() {
        boolean shouldHold = overlay.isVisible() && (leftHeld || scrollMode);
        if (shouldHold && !touch.isRequestedDown()) {
            touch.press(overlay.screenX(), overlay.screenY());
        } else if (!shouldHold && touch.isRequestedDown()) {
            touch.release();
        }
    }
}
