package ru.vasys.keycursor.service;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import ru.vasys.keycursor.core.Binding;
import ru.vasys.keycursor.core.CursorStyle;
import ru.vasys.keycursor.core.DpadKeyNormalizer;
import ru.vasys.keycursor.core.MouseAction;
import ru.vasys.keycursor.core.MouseMode;
import ru.vasys.keycursor.core.PadAction;
import ru.vasys.keycursor.core.PadGestureEngine;
import ru.vasys.keycursor.core.Settings;
import ru.vasys.keycursor.core.SwipeDirection;

/**
 * Accessibility is used as the gesture backend and hardware-key router.
 * When the mouse is hidden, bound keys pass through untouched. While the mouse is visible,
 * mouse-control keys are consumed so Android focus/navigation does not run in parallel.
 * The Mouse key can optionally be captured while the cursor is visible, allowing the same
 * physical key to keep its normal app binding whenever the mouse is hidden.
 */
public final class MouseAccessibilityService extends AccessibilityService {
    private static volatile MouseAccessibilityService runningInstance;
    private static final long TICK_MS = 16;
    private static final long SCROLL_REPEAT_MS = 160;
    private static final long PASS_THROUGH_SETTLE_MS = 40;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumSet<MouseAction> pressed = EnumSet.noneOf(MouseAction.class);
    private final Map<Long, MouseAction> heldKeyActions = new HashMap<>();
    private final Map<Long, Boolean> heldTriggerCapture = new HashMap<>();

    private Settings settings;
    private PointerController pointer;
    private TouchpadOverlay pad;
    private Map<MouseAction, Binding> bindings;
    private boolean tickerRunning;
    private boolean calibrationMode;
    private boolean triggerHeld;
    private boolean triggerLongFired;
    private boolean triggerSecondTapCandidate;
    private boolean triggerSinglePending;
    private Toast feedbackToast;
    private float movementSpeed = 10f;
    private int displayRotation;
    private boolean rotateLandscapeControls;
    private boolean invertHorizontalPortrait;
    private boolean invertVerticalPortrait;
    private boolean invertHorizontalLandscape;
    private boolean invertVerticalLandscape;
    private boolean blendDirections;
    private boolean captureMouseKeyVisible;
    private final float[] resolvedDirection = new float[2];

    private final Runnable triggerLongPress = new Runnable() {
        @Override
        public void run() {
            if (!triggerHeld || pointer == null) return;
            triggerLongFired = true;
            triggerSecondTapCandidate = false;
            cancelPendingSinglePress();
            toggleMouseVisibility();
        }
    };

    private final Runnable triggerSinglePress = new Runnable() {
        @Override
        public void run() {
            triggerSinglePending = false;
            selectNextNormalHoldMode();
        }
    };

    public static MouseAccessibilityService getRunningInstance() {
        return runningInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        runningInstance = this;
        settings = new Settings(this);
        bindings = settings.allBindings();
        pointer = new PointerController(this, settings.cursorStyle(), settings.wrapEdges(), settings.cursorColor(), settings.cursorSizeDp());
        movementSpeed = settings.speedPxPerTick();
        reloadDirectionPreferences();
        captureMouseKeyVisible = settings.captureMouseKeyVisible();
        refreshDisplayRotation();

        pad = new TouchpadOverlay(this, settings, padListener);
        pointer.setStrokeListener(new ContinuousTouchController.StrokeListener() {
            @Override
            public long onStrokeBegin(float x, float y) {
                if (pad == null) return 0L;
                // Only step aside when the injected touch lands on the pad itself. A window flag
                // change is applied on the next layout pass, so delay the touch until it has.
                boolean inside = pad.containsScreenPoint(x, y);
                boolean changed = pad.setPassThrough(inside);
                return changed && inside ? PASS_THROUGH_SETTLE_MS : 0L;
            }

            @Override
            public void onStrokeEnd() {
                if (pad != null) pad.setPassThrough(false);
            }
        });
        PadTileService.requestRefresh(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty. Scroll mode retrieves the active node tree only on a user-requested
        // scroll tick; KeyCursor still does not subscribe to or process UI accessibility events.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshDisplayRotation();
        if (pointer != null) pointer.refreshDisplayBounds();
        if (pad != null) pad.refreshDisplayBounds();
    }

    @Override
    public void onInterrupt() {
        resetInputState();
        if (pad != null) pad.hide();
        if (pointer != null) pointer.hide();
        PadTileService.requestRefresh(this);
    }

    @Override
    public void onDestroy() {
        runningInstance = null;
        resetInputState();
        if (pad != null) pad.destroy();
        pad = null;
        if (pointer != null) pointer.destroy();
        pointer = null;
        PadTileService.requestRefresh(this);
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // During a bind capture MainActivity must see the key without KeyCursor acting on it.
        if (calibrationMode || settings == null) return false;

        final long keyToken = keyToken(event);
        MouseAction action;

        if (event.getAction() == KeyEvent.ACTION_UP) {
            // Android deliberately keeps the key-up keyCode equal to the preceding key-down even
            // if the display rotated while the key was held. Reuse the action resolved on DOWN so
            // a 90° <-> 270° flip cannot release a different logical direction.
            action = heldKeyActions.remove(keyToken);
            if (action == null) {
                refreshDisplayRotation();
                action = findAction(event);
            }
        } else {
            // A landscape-side flip (ROTATION_90 <-> ROTATION_270) can keep Configuration in
            // LANDSCAPE, so onConfigurationChanged() is not a reliable rotation signal. Reading
            // rotation per hardware key event is cheap and avoids any polling.
            refreshDisplayRotation();
            action = heldKeyActions.get(keyToken);
            if (action == null) action = findAction(event);
            if (action != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                heldKeyActions.put(keyToken, action);
            }
        }

        if (action == null) return false;

        if (action == MouseAction.TRIGGER) {
            boolean capture;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                // Decide once at DOWN and keep the same policy for the whole physical press. A
                // long press can show/hide the cursor before UP, and leaking only half of a key
                // sequence to another app would create stuck or phantom shortcuts.
                capture = captureMouseKeyVisible && pointer != null && pointer.isVisible();
                heldTriggerCapture.put(keyToken, capture);
                actionDown(action);
            } else {
                Boolean heldCapture = heldTriggerCapture.get(keyToken);
                capture = heldCapture != null
                        ? heldCapture
                        : captureMouseKeyVisible && pointer != null && pointer.isVisible();
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    actionUp(action);
                    heldTriggerCapture.remove(keyToken);
                }
            }
            return capture;
        }

        // Hidden mouse means the keypad behaves exactly like a normal Android keypad.
        if (pointer == null || !pointer.isVisible()) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) actionDown(action);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            actionUp(action);
        }

        // A visible mouse is an exclusive interaction mode. Consume movement/click/cursor
        // controls so Android does not move focus or activate a focused view at the same time.
        return true;
    }

    private long keyToken(KeyEvent event) {
        long physicalCode = event.getScanCode() > 0
                ? (0x80000000L | (event.getScanCode() & 0x7fffffffL))
                : (event.getKeyCode() & 0xffffffffL);
        return (((long) event.getDeviceId()) << 32) ^ physicalCode;
    }

    public void externalDown(MouseAction action) {
        main.post(() -> actionDown(action));
    }

    public void externalUp(MouseAction action) {
        main.post(() -> actionUp(action));
    }

    public void externalPulse(MouseAction action) {
        main.post(() -> {
            if (action == MouseAction.TRIGGER) {
                triggerShortPulse();
            } else if (action == MouseAction.CURSOR_NEXT) {
                cycleCursor();
            } else if (action.movement) {
                if (pointer != null && pointer.isVisible()) {
                    if (pointer.mode() == MouseMode.SCROLL) scrollForActions(EnumSet.of(action));
                    else moveForActions(EnumSet.of(action), movementSpeed);
                }
            } else if (action == MouseAction.LEFT_BUTTON) {
                actionDown(action);
                actionUp(action);
            }
        });
    }

    public void externalTriggerShort() {
        main.post(this::triggerShortPulse);
    }

    public void externalTriggerLong() {
        main.post(() -> {
            cancelTriggerTapState();
            toggleMouseVisibility();
        });
    }

    public void externalMouseToggle() {
        main.post(() -> {
            cancelTriggerTapState();
            toggleMouseVisibility();
        });
    }

    public void externalModeToggle() {
        main.post(() -> {
            cancelTriggerTapState();
            selectNextNormalHoldMode();
        });
    }

    public void externalCursorNext() {
        main.post(this::cycleCursor);
    }

    public void setCalibrationMode(boolean enabled) {
        main.post(() -> {
            calibrationMode = enabled;
            if (enabled) resetInputState();
        });
    }

    public void previewCursorSizeDp(int sizeDp) {
        main.post(() -> {
            if (pointer != null) pointer.setCursorSizeDp(sizeDp);
        });
    }

    public void reloadSettings() {
        main.post(() -> {
            if (settings == null) settings = new Settings(this);
            bindings = settings.allBindings();
            movementSpeed = settings.speedPxPerTick();
            reloadDirectionPreferences();
            captureMouseKeyVisible = settings.captureMouseKeyVisible();
            if (pointer != null) {
                pointer.setCursorStyle(settings.cursorStyle());
                pointer.setWrapEdges(settings.wrapEdges());
                pointer.setCursorColor(settings.cursorColor());
                pointer.setCursorSizeDp(settings.cursorSizeDp());
            }
            if (pad != null) pad.applySettings();
        });
    }

    // ---- touchpad ----

    private final PadGestureEngine.Listener padListener = new PadGestureEngine.Listener() {
        @Override
        public void onMove(float dx, float dy) {
            if (pointer != null && pointer.isVisible()) pointer.move(dx, dy);
        }

        @Override
        public void onClick() {
            if (pointer == null || !pointer.isVisible()) return;
            pointer.leftDown();
            pointer.leftUp();
        }

        @Override
        public void onDragStart() {
            if (pointer != null && pointer.isVisible()) pointer.leftDown();
        }

        @Override
        public void onDragEnd() {
            if (pointer != null) pointer.leftUp();
        }

        @Override
        public void onSwipe(int fingers, SwipeDirection direction) {
            if (settings != null) performPadAction(settings.padSwipeAction(fingers, direction));
        }
    };

    public boolean isPadVisible() {
        return pad != null && pad.isShown();
    }

    /** Shows or hides the touchpad together with the cursor. */
    public void setPadVisible(boolean visible) {
        if (pad == null || pointer == null) return;
        if (visible == pad.isShown()) return;
        cancelTriggerTapState();
        if (visible) {
            if (!pointer.isVisible()) pointer.show();
            pad.show();
            if (!pad.isShown()) {
                // The overlay window could not be added; do not leave a lone cursor behind.
                pointer.hide();
                showToast("Touchpad unavailable");
            }
        } else {
            pad.hide();
            pressed.clear();
            stopTicker();
            if (pointer.isVisible()) pointer.hide();
        }
        PadTileService.requestRefresh(this);
    }

    /** Quick Settings tile entry point: toggles the pad and closes the shade when turning it on. */
    public void togglePadFromTile() {
        main.post(() -> {
            boolean turnOn = !isPadVisible();
            setPadVisible(turnOn);
            if (turnOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
        });
    }

    public void externalPadToggle() {
        main.post(() -> setPadVisible(!isPadVisible()));
    }

    private void performPadAction(PadAction action) {
        if (action == null) return;
        switch (action) {
            case BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case HOME:
                performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case RECENTS:
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case NOTIFICATIONS:
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case QUICK_SETTINGS:
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case POWER_DIALOG:
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                break;
            case LOCK_SCREEN:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                }
                break;
            case SCREENSHOT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                }
                break;
            case TOGGLE_HOLD:
                selectNextNormalHoldMode();
                break;
            case NEXT_CURSOR:
                cycleCursor();
                break;
            case HIDE_PAD:
                setPadVisible(false);
                break;
            case NONE:
            default:
                break;
        }
    }

    private MouseAction findAction(KeyEvent event) {
        if (bindings == null) return null;

        // A calibrated scan code is physical and therefore wins over Android's rotated
        // DPAD keyCode. This is also what keeps diagonal/custom keys untouched.
        for (Map.Entry<MouseAction, Binding> e : bindings.entrySet()) {
            if (e.getValue().matchesScanCode(event)) return e.getKey();
        }

        int physicalKeyCode = DpadKeyNormalizer.toPhysicalDirection(event.getKeyCode(), displayRotation);
        for (Map.Entry<MouseAction, Binding> e : bindings.entrySet()) {
            if (e.getValue().matchesKeyCode(physicalKeyCode)) return e.getKey();
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private void refreshDisplayRotation() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        displayRotation = wm == null ? 0 : wm.getDefaultDisplay().getRotation();
    }

    private void actionDown(MouseAction action) {
        if (pointer == null) return;
        if (action == MouseAction.TRIGGER) {
            if (triggerHeld) return;
            triggerHeld = true;
            triggerLongFired = false;
            triggerSecondTapCandidate = triggerSinglePending;
            if (triggerSecondTapCandidate) cancelPendingSinglePress();
            main.postDelayed(triggerLongPress, ViewConfiguration.getLongPressTimeout());
            return;
        }
        if (!pointer.isVisible()) return;
        if (action.movement) {
            if (pressed.add(action)) startTicker();
            return;
        }
        if (action == MouseAction.LEFT_BUTTON) {
            pointer.leftDown();
        } else if (action == MouseAction.CURSOR_NEXT) {
            cycleCursor();
        }
    }

    private void actionUp(MouseAction action) {
        if (pointer == null) return;
        if (action == MouseAction.TRIGGER) {
            if (!triggerHeld) return;
            triggerHeld = false;
            main.removeCallbacks(triggerLongPress);
            if (!triggerLongFired) finishHardwareTriggerShortPress();
            triggerLongFired = false;
            return;
        }
        if (action.movement) {
            pressed.remove(action);
            if (!hasMovementPressed()) stopTicker();
            return;
        }
        if (action == MouseAction.LEFT_BUTTON) pointer.leftUp();
    }

    private void finishHardwareTriggerShortPress() {
        if (pointer == null || !pointer.isVisible()) {
            triggerSecondTapCandidate = false;
            cancelPendingSinglePress();
            return;
        }

        if (triggerSecondTapCandidate) {
            triggerSecondTapCandidate = false;
            toggleScrollMode();
        } else {
            scheduleSinglePress();
        }
    }

    private void triggerShortPulse() {
        if (pointer == null || !pointer.isVisible()) {
            cancelTriggerTapState();
            return;
        }
        if (triggerSinglePending) {
            cancelPendingSinglePress();
            toggleScrollMode();
        } else {
            scheduleSinglePress();
        }
    }

    private void scheduleSinglePress() {
        cancelPendingSinglePress();
        triggerSinglePending = true;
        main.postDelayed(triggerSinglePress, ViewConfiguration.getDoubleTapTimeout());
    }

    private void cancelPendingSinglePress() {
        if (!triggerSinglePending) return;
        triggerSinglePending = false;
        main.removeCallbacks(triggerSinglePress);
    }

    private void cancelTriggerTapState() {
        triggerSecondTapCandidate = false;
        cancelPendingSinglePress();
    }

    private void selectNextNormalHoldMode() {
        if (pointer == null || !pointer.isVisible() || pointer.mode() == MouseMode.SCROLL) return;
        pointer.toggleNormalHoldMode();
        showToast(pointer.mode() == MouseMode.HOLD ? "Mouse mode: Hold" : "Mouse mode: Normal");
    }

    private void toggleScrollMode() {
        if (pointer == null || !pointer.isVisible()) return;
        pointer.toggleScrollMode();
        showToast("Mouse mode: " + modeLabel(pointer.mode()));
    }

    private String modeLabel(MouseMode mode) {
        if (mode == MouseMode.HOLD) return "Hold";
        if (mode == MouseMode.SCROLL) return "Scroll";
        return "Normal";
    }

    private void toggleMouseVisibility() {
        if (pointer == null) return;
        cancelTriggerTapState();
        if (pointer.isVisible()) {
            pressed.clear();
            stopTicker();
        }
        pointer.toggleVisibility();
        if (!pointer.isVisible() && pad != null && pad.isShown()) {
            // A hidden cursor with a live pad would be a dead surface that eats touches.
            pad.hide();
            PadTileService.requestRefresh(this);
        }
        showToast(pointer.isVisible() ? "Mouse: on · Normal" : "Mouse: off");
    }

    private void cycleCursor() {
        if (pointer == null || !pointer.isVisible() || settings == null) return;
        CursorStyle next = settings.cycleCursorStyle();
        pointer.setCursorStyle(next);
    }

    private void startTicker() {
        if (tickerRunning) return;
        tickerRunning = true;
        main.post(moveTick);
    }

    private void stopTicker() {
        tickerRunning = false;
        main.removeCallbacks(moveTick);
    }

    private boolean hasMovementPressed() {
        for (MouseAction a : pressed) if (a.movement) return true;
        return false;
    }

    private final Runnable moveTick = new Runnable() {
        @Override
        public void run() {
            if (!tickerRunning || pointer == null || !pointer.isVisible()) {
                stopTicker();
                return;
            }
            if (pointer.mode() == MouseMode.SCROLL) {
                scrollForActions(pressed);
                main.postDelayed(this, SCROLL_REPEAT_MS);
            } else {
                moveForActions(pressed, movementSpeed);
                main.postDelayed(this, TICK_MS);
            }
        }
    };

    private void moveForActions(Iterable<MouseAction> actions, float speed) {
        if (pointer == null || !resolveDirection(actions, resolvedDirection)) return;
        pointer.move(resolvedDirection[0] * speed, resolvedDirection[1] * speed);
    }

    private void scrollForActions(Iterable<MouseAction> actions) {
        if (pointer == null || !resolveDirection(actions, resolvedDirection)) return;
        pointer.scroll(resolvedDirection[0], resolvedDirection[1]);
    }

    private boolean resolveDirection(Iterable<MouseAction> actions, float[] out) {
        float vx = 0f;
        float vy = 0f;
        for (MouseAction action : actions) {
            if (!action.movement) continue;
            // In blend mode each bound direction contributes a unit vector. This makes e.g.
            // UP + UP_LEFT resolve to the exact 22.5-degree direction between them.
            float actionScale = blendDirections && action.dx != 0 && action.dy != 0
                    ? 0.70710678f : 1f;
            vx += action.dx * actionScale;
            vy += action.dy * actionScale;
        }

        if (!blendDirections) {
            vx = Math.signum(vx);
            vy = Math.signum(vy);
        }

        float length = (float) Math.sqrt(vx * vx + vy * vy);
        if (length < 0.0001f) return false;
        vx /= length;
        vy /= length;

        boolean landscape = displayRotation == Surface.ROTATION_90
                || displayRotation == Surface.ROTATION_270;
        if (landscape && rotateLandscapeControls) {
            float originalX = vx;
            if (displayRotation == Surface.ROTATION_90) {
                vx = vy;
                vy = -originalX;
            } else {
                vx = -vy;
                vy = originalX;
            }
        }

        boolean invertX = landscape ? invertHorizontalLandscape : invertHorizontalPortrait;
        boolean invertY = landscape ? invertVerticalLandscape : invertVerticalPortrait;
        if (invertX) vx = -vx;
        if (invertY) vy = -vy;

        out[0] = vx;
        out[1] = vy;
        return true;
    }

    private void reloadDirectionPreferences() {
        if (settings == null) return;
        rotateLandscapeControls = settings.rotateLandscapeControls();
        invertHorizontalPortrait = settings.invertHorizontalPortrait();
        invertVerticalPortrait = settings.invertVerticalPortrait();
        invertHorizontalLandscape = settings.invertHorizontalLandscape();
        invertVerticalLandscape = settings.invertVerticalLandscape();
        blendDirections = settings.blendDirections();
    }

    private void showToast(String message) {
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        feedbackToast.show();
    }

    private void resetInputState() {
        pressed.clear();
        heldKeyActions.clear();
        heldTriggerCapture.clear();
        stopTicker();
        triggerHeld = false;
        triggerLongFired = false;
        cancelTriggerTapState();
        main.removeCallbacks(triggerLongPress);
    }

}
