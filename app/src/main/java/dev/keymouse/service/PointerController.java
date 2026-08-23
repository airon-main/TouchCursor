package dev.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

public final class PointerController {
    private final AccessibilityService service;
    private final CursorOverlay overlay;
    private final ContinuousTouchController touch;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean leftHeld;
    private boolean touchMode;

    public PointerController(AccessibilityService service, String cursorStyle) {
        this.service = service;
        overlay = new CursorOverlay(service, cursorStyle);
        touch = new ContinuousTouchController(service);
    }

    public void destroy() {
        touch.release();
        overlay.destroy();
    }

    public void move(float dx, float dy) {
        overlay.moveBy(dx, dy);
        if (leftHeld || touchMode) touch.moveTo(overlay.x(), overlay.y());
    }

    public void leftDown() {
        if (leftHeld) return;
        leftHeld = true;
        syncTouchState();
    }

    public void leftUp() {
        if (!leftHeld) return;
        leftHeld = false;
        syncTouchState();
    }

    public void toggleTouchMode() {
        touchMode = !touchMode;
        syncTouchState();
    }

    public void contextClick() {
        if (leftHeld || touchMode) return;
        Path path = new Path();
        path.moveTo(overlay.x(), overlay.y());
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 550);
        service.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, main);
    }

    public void toggleCursor() {
        overlay.setVisible(!overlay.isVisible());
    }

    public void setCursorStyle(String style) {
        overlay.setStyle(style);
    }

    public boolean isTouchMode() {
        return touchMode;
    }

    private void syncTouchState() {
        boolean shouldHold = leftHeld || touchMode;
        overlay.setTouchActive(shouldHold);
        if (shouldHold && !touch.isRequestedDown()) {
            touch.press(overlay.x(), overlay.y());
        } else if (!shouldHold && touch.isRequestedDown()) {
            touch.release();
        }
    }
}
