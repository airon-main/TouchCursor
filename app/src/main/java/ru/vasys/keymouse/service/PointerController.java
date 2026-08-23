package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;

import ru.vasys.keymouse.core.CursorStyle;

public final class PointerController {
    private final CursorOverlay overlay;
    private final ContinuousTouchController touch;

    private boolean leftHeld;
    private boolean scrollMode;

    public PointerController(AccessibilityService service, CursorStyle cursorStyle) {
        overlay = new CursorOverlay(service, cursorStyle);
        touch = new ContinuousTouchController(service);
    }

    public void destroy() {
        hide();
        overlay.destroy();
    }

    public void move(float dx, float dy) {
        if (!overlay.isVisible()) return;
        overlay.moveBy(dx, dy);
        if (leftHeld || scrollMode) touch.moveTo(overlay.x(), overlay.y());
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

    public boolean isScrollMode() {
        return scrollMode;
    }

    private void syncTouchState() {
        boolean shouldHold = overlay.isVisible() && (leftHeld || scrollMode);
        if (shouldHold && !touch.isRequestedDown()) {
            touch.press(overlay.x(), overlay.y());
        } else if (!shouldHold && touch.isRequestedDown()) {
            touch.release();
        }
    }
}
