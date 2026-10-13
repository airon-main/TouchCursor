package ru.vasys.keycursor.core;

/**
 * Touchpad gesture recognizer. Deliberately free of Android classes so it can be unit-tested on a
 * plain JVM; {@code TouchpadView} translates MotionEvents into the on* calls below and supplies a
 * {@link Timer}.
 *
 * <pre>
 * 1 finger        : relative cursor movement (held back until the touch slop is exceeded)
 * tap             : click, but only after {@link #tapDelayMs}; a second touch inside that window
 *                   cancels the click and starts a hold / drag instead
 * tap, touch again: if the second touch moves or is held, the button is held down and dragged
 *                   (a real touch drag, so it scrolls lists natively); the first tap never clicks.
 *                   If the second touch is released quickly it is a double tap = two clicks
 * 2 or 3 fingers  : one swipe event per touch, once swipeThresholdPx is exceeded
 * </pre>
 *
 * Once more than one finger has touched, the gesture never falls back to cursor movement when
 * fingers lift; it stays inert until every finger is up, so lifting one finger cannot make the
 * cursor jump. A tap that was waiting out its delay is discarded when the next touch turns out to
 * be a multi-finger gesture, so swipes never leave an accidental click behind.
 */
public final class PadGestureEngine {
    public interface Listener {
        /** Cursor delta in pixels, already scaled by sensitivity/acceleration. */
        void onMove(float dx, float dy);

        void onClick();
        
        void onDragStart();
        
        void onDragEnd();
        
        /** A two- or three-finger swipe; {@code fingers} is 2 or 3. */
        void onSwipe(int fingers, SwipeDirection direction);
    }

    /** One-shot timer owned by the host; scheduling replaces any pending callback. */
    public interface Timer {
        void schedule(long delayMs);

        void cancel();
    }

    private enum State { IDLE, ONE, HOLD_PENDING, DRAG, MULTI, DONE }
    
    private enum Timed { NONE, TAP_COMMIT, HOLD_CONFIRM }

    // ---- configuration (set by the owner; plain fields on purpose) ----
    public float sensitivity = 1.6f;
    public boolean acceleration = true;
    /** Tap-then-hold-to-drag. When off, every tap clicks immediately. */
    public boolean tapAndDrag = true;
    /** How long a tap waits for a second touch before it clicks. 0 = click immediately. */
    public long tapDelayMs = 150;
    public float touchSlopPx = 12f;
    public float swipeThresholdPx = 120f;
    /** Longest touch that still counts as a tap, and how long a second touch must be held. */
    public long tapTimeoutMs = 250;
    /** Gap between the two clicks of a double tap, so they are two separate touches. */
    public long doubleClickGapMs = 80;

    private final Listener listener;
    private Timer timer;

    private State state = State.IDLE;
    private Timed timed = Timed.NONE;
    private boolean tapPending;
    private long tapUpTime;

    private float downX;
    private float downY;
    private float lastPx;
    private float lastPy;
    private long downTime;
    private long lastMoveTime;
    private boolean slopExceeded;
    private boolean firstMoveAfterSlop;

    private int fingers;
    private float swipeStartX;
    private float swipeStartY;
    private boolean swipeFired;

    public PadGestureEngine(Listener listener) {
        this.listener = listener;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    /** First finger touched down. */
    public void onDown(long t, float x, float y) {
        if (state != State.IDLE) onCancel();

        boolean chained = false;
        if (timed == Timed.TAP_COMMIT) {
            if (delayedTaps() && t - tapUpTime <= tapDelayMs) {
                chained = true;
            } else {
                cancelTimer();
                tapPending = false;
                listener.onClick();
            }
        }

        downX = x;
        downY = y;
        lastPx = x;
        lastPy = y;
        downTime = t;
        lastMoveTime = t;
        slopExceeded = false;
        firstMoveAfterSlop = false;
        swipeFired = false;
        fingers = 1;

        if (chained) {
            // Undecided: drag/hold (first tap suppressed), or a double tap (two clicks).
            state = State.HOLD_PENDING;
            arm(Timed.HOLD_CONFIRM, tapTimeoutMs);
        } else {
            state = State.ONE;
        }
    }

    /**
     * The number of fingers changed. {@code count} is the number of fingers down after the change,
     * (cx, cy) their centroid and (px, py) the position of the primary finger.
     */
    public void onPointers(long t, int count, float cx, float cy, float px, float py) {
        if (state == State.IDLE) return;
        lastPx = px;
        lastPy = py;
        lastMoveTime = t;
        switch (state) {
            case ONE:
            case HOLD_PENDING:
                if (count >= 2) {
                    discardPendingTap();
                    state = State.MULTI;
                    fingers = Math.min(3, count);
                    swipeStartX = cx;
                    swipeStartY = cy;
                }
                break;
            case MULTI:
                if (count <= 1) {
                    state = State.DONE;
                } else if (Math.min(3, count) != fingers) {
                    // Only re-baseline if the centroid has shifted significantly
                    // to avoid cancelling a swipe during a finger transition.
                    float ddx = cx - swipeStartX;
                    float ddy = cy - swipeStartY;
                    if ((ddx * ddx + ddy * ddy) > (touchSlopPx * touchSlopPx)) {
                        fingers = Math.min(3, count);
                        swipeStartX = cx;
                        swipeStartY = cy;
                    } else {
                        fingers = Math.min(3, count);
                    }
                }
                break;
            default:
                break;
        }
    }

    public void onMove(long t, int count, float cx, float cy, float px, float py) {
        switch (state) {
            case ONE:
            case DRAG:
                moveSingle(t, px, py);
                break;
            case HOLD_PENDING: {
                float ddx = px - downX;
                float ddy = py - downY;
                if ((ddx * ddx + ddy * ddy) < (touchSlopPx * touchSlopPx)) {
                    lastMoveTime = t;
                    break;
                }
                cancelTimer();
                tapPending = false;
                state = State.DRAG;
                listener.onDragStart();
                moveSingle(t, px, py);
                break;
            }
            case MULTI:
                swipe(cx, cy);
                break;
            default:
                break;
        }
    }

    /** The last finger lifted. */
    public void onUp(long t) {
        State s = state;
        state = State.IDLE;
        switch (s) {
            case ONE: {
                boolean quick = !slopExceeded && (t - downTime) <= tapTimeoutMs;
                if (!quick) break;
                if (delayedTaps()) {
                    tapPending = true;
                    tapUpTime = t;
                    arm(Timed.TAP_COMMIT, tapDelayMs);
                } else {
                    listener.onClick();
                }
                break;
            }
            case HOLD_PENDING: {
                cancelTimer();
                tapPending = false;
                if (t - downTime > tapTimeoutMs) {
                    listener.onDragStart();
                    listener.onDragEnd();
                } else {
                    listener.onClick();
                }
                break;
            }
            case DRAG:
                listener.onDragEnd();
                break;
            case DONE:
                break;
            default:
                break;
        }
    }

    public void onCancel() {
        if (state == State.DRAG) listener.onDragEnd();
        cancelTimer();
        tapPending = false;
        state = State.IDLE;
    }

    public void onTimer() {
        Timed kind = timed;
        timed = Timed.NONE;
        switch (kind) {
            case TAP_COMMIT:
                if (tapPending) {
                    tapPending = false;
                    listener.onClick();
                }
                break;
            case HOLD_CONFIRM:
                if (state == State.HOLD_PENDING) {
                    tapPending = false;
                    state = State.DRAG;
                    listener.onDragStart();
                }
                break;
            default:
                break;
        }
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    private boolean delayedTaps() {
        return tapAndDrag && tapDelayMs > 0 && timer != null;
    }

    private void arm(Timed kind, long delayMs) {
        timed = kind;
        if (timer != null) timer.schedule(delayMs);
    }

    private void cancelTimer() {
        timed = Timed.NONE;
        if (timer != null) timer.cancel();
    }

    private void discardPendingTap() {
        if (timed == Timed.HOLD_CONFIRM) cancelTimer();
        tapPending = false;
    }

    private void moveSingle(long t, float px, float py) {
        if (!slopExceeded) {
            float ddx = px - downX;
            float ddy = py - downY;
            if ((ddx * ddx + ddy * ddy) < (touchSlopPx * touchSlopPx)) {
                lastMoveTime = t;
                return;
            }
            slopExceeded = true;
            firstMoveAfterSlop = true;
        }
        float dx = px - lastPx;
        float dy = py - lastPy;
        long dt = Math.max(1, Math.min(64, t - lastMoveTime));
        float gain = sensitivity;
        if (acceleration && !firstMoveAfterSlop) {
            float speed = (float) Math.sqrt(dx * dx + dy * dy) / dt;
            gain *= 1f + 1.5f * Math.min(speed / 1.5f, 1f);
        }
        firstMoveAfterSlop = false;
        lastPx = px;
        lastPy = py;
        lastMoveTime = t;
        if (dx != 0f || dy != 0f) listener.onMove(dx * gain, dy * gain);
    }

    private void swipe(float cx, float cy) {
        if (swipeFired) return;
        float dx = cx - swipeStartX;
        float dy = cy - swipeStartY;
        if (Math.max(Math.abs(dx), Math.abs(dy)) < swipeThresholdPx) return;
        swipeFired = true;
        SwipeDirection dir;
        if (Math.abs(dy) >= Math.abs(dx)) dir = dy < 0 ? SwipeDirection.UP : SwipeDirection.DOWN;
        else dir = dx < 0 ? SwipeDirection.LEFT : SwipeDirection.RIGHT;
        listener.onSwipe(fingers, dir);
    }
}
