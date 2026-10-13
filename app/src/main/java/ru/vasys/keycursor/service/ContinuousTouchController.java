package ru.vasys.keycursor.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

/**
 * Holds one virtual pointer down across multiple dispatchGesture calls.
 * API 26+ StrokeDescription.continueStroke() is the important bit: movement
 * does not become a sequence of independent swipes.
 */
public final class ContinuousTouchController {
    /**
     * Notified when a virtual finger goes down / is fully released, so the touchpad window can
     * stop intercepting touches while the injected stroke targets a point beneath it.
     */
    public interface StrokeListener {
        /**
         * A virtual finger is about to go down at (x, y). Returns how many milliseconds the first
         * injected touch should wait (0 for none), e.g. for a window flag change to take effect.
         */
        long onStrokeBegin(float x, float y);

        void onStrokeEnd();
    }

    private final AccessibilityService service;
    private StrokeListener strokeListener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private GestureDescription.StrokeDescription stroke;
    private boolean busy;
    private boolean requestedDown;
    private float currentX;
    private float currentY;
    private float targetX;
    private float targetY;
    private float dispatchedEndX;
    private float dispatchedEndY;
    private boolean restartAfterRelease;

    public ContinuousTouchController(AccessibilityService service) {
        this.service = service;
    }

    public void setStrokeListener(StrokeListener listener) {
        strokeListener = listener;
    }

    private long notifyBegin(float x, float y) {
        return strokeListener == null ? 0L : Math.max(0L, strokeListener.onStrokeBegin(x, y));
    }

    /** Reports the end of the stroke only once nothing is pending or in flight. */
    private void notifyEndIfIdle() {
        if (strokeListener != null && !requestedDown && !busy && stroke == null) {
            strokeListener.onStrokeEnd();
        }
    }

    public void press(float x, float y) {
        runOnMain(() -> {
            targetX = x;
            targetY = y;
            if (requestedDown) return;
            requestedDown = true;
            if (!busy && stroke == null) dispatchStart(x, y);
        });
    }

    public void moveTo(float x, float y) {
        runOnMain(() -> {
            targetX = x;
            targetY = y;
            if (!requestedDown || busy) return;
            if (stroke == null) {
                dispatchStart(targetX, targetY);
                return;
            }
            if (distance(currentX, currentY, targetX, targetY) >= 0.5f) dispatchMove();
        });
    }

    public void release() {
        runOnMain(() -> {
            requestedDown = false;
            restartAfterRelease = false;
            if (!busy && stroke != null) dispatchRelease();
            else notifyEndIfIdle();
        });
    }

    /**
     * Moves an active virtual finger to a non-contiguous position without
     * drawing a giant gesture across the display. Accessibility has no true
     * pointer teleport primitive, so edge wrapping is represented by a very
     * short release + press at the wrapped coordinate.
     */
    public void rebase(float x, float y) {
        runOnMain(() -> {
            targetX = x;
            targetY = y;
            if (!requestedDown) return;
            restartAfterRelease = true;
            if (!busy) {
                if (stroke != null) dispatchRelease();
                else {
                    restartAfterRelease = false;
                    dispatchStart(targetX, targetY);
                }
            }
        });
    }

    public boolean isRequestedDown() {
        return requestedDown;
    }

    private void dispatchStart(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription next =
                new GestureDescription.StrokeDescription(p, 0, 8, true);
        currentX = x;
        currentY = y;
        dispatchedEndX = x;
        dispatchedEndY = y;
        long settleMs = notifyBegin(x, y);
        if (settleMs > 0) {
            // Hold the controller busy so moves/releases queue up behind the first touch.
            busy = true;
            main.postDelayed(() -> dispatch(next, true), settleMs);
        } else {
            dispatch(next, true);
        }
    }

    private void dispatchMove() {
        float endX = targetX;
        float endY = targetY;
        Path p = new Path();
        p.moveTo(currentX, currentY);
        p.lineTo(endX, endY);
        long duration = Math.max(8, Math.min(32,
                Math.round(distance(currentX, currentY, endX, endY) / 2f)));
        GestureDescription.StrokeDescription next =
                stroke.continueStroke(p, 0, duration, true);
        dispatchedEndX = endX;
        dispatchedEndY = endY;
        dispatch(next, true);
    }

    private void dispatchRelease() {
        Path p = new Path();
        p.moveTo(currentX, currentY);
        GestureDescription.StrokeDescription next =
                stroke.continueStroke(p, 0, 1, false);
        dispatchedEndX = currentX;
        dispatchedEndY = currentY;
        dispatch(next, false);
    }

    private void dispatch(GestureDescription.StrokeDescription next, boolean continues) {
        busy = true;
        stroke = next;
        GestureDescription gesture = new GestureDescription.Builder().addStroke(next).build();
        boolean accepted = service.dispatchGesture(gesture,
                new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        busy = false;
                        currentX = dispatchedEndX;
                        currentY = dispatchedEndY;
                        if (!continues) {
                            stroke = null;
                            if (requestedDown && restartAfterRelease) {
                                restartAfterRelease = false;
                                dispatchStart(targetX, targetY);
                            } else {
                                notifyEndIfIdle();
                            }
                            return;
                        }
                        if (!requestedDown) {
                            dispatchRelease();
                        } else if (restartAfterRelease) {
                            dispatchRelease();
                        } else if (distance(currentX, currentY, targetX, targetY) >= 0.5f) {
                            dispatchMove();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        // Do not spin trying to immediately reclaim a cancelled gesture. A real
                        // touch, another accessibility service or SystemUI may have taken over.
                        // If the physical hold is still active, the next move will restart lazily.
                        busy = false;
                        stroke = null;
                        restartAfterRelease = false;
                        notifyEndIfIdle();
                    }
                }, main);
        if (!accepted) {
            busy = false;
            stroke = null;
            restartAfterRelease = false;
            notifyEndIfIdle();
        }
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else main.post(r);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
