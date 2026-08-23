package ru.vasys.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.EnumSet;
import java.util.Map;

import ru.vasys.keymouse.core.Binding;
import ru.vasys.keymouse.core.CursorStyle;
import ru.vasys.keymouse.core.MouseAction;
import ru.vasys.keymouse.core.Settings;

/**
 * Accessibility is used as a gesture backend and a non-consuming hardware-key observer.
 * Bound hardware keys are intentionally returned as false from onKeyEvent(), so Android
 * and other applications can still receive the same physical key events.
 */
public final class MouseAccessibilityService extends AccessibilityService {
    private static volatile MouseAccessibilityService runningInstance;
    private static final long TICK_MS = 16;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumSet<MouseAction> pressed = EnumSet.noneOf(MouseAction.class);

    private Settings settings;
    private PointerController pointer;
    private Map<MouseAction, Binding> bindings;
    private boolean tickerRunning;
    private boolean calibrationMode;
    private boolean triggerHeld;
    private boolean triggerLongFired;
    private Toast feedbackToast;

    private final Runnable triggerLongPress = new Runnable() {
        @Override
        public void run() {
            if (!triggerHeld || pointer == null) return;
            triggerLongFired = true;
            toggleMouseVisibility();
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
        pointer = new PointerController(this, settings.cursorStyle());
        enableKeyObservation();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No view-tree scraping. Accessibility is used only for gestures and key observation.
    }

    @Override
    public void onInterrupt() {
        resetInputState();
        if (pointer != null) pointer.hide();
    }

    @Override
    public void onDestroy() {
        runningInstance = null;
        resetInputState();
        if (pointer != null) pointer.destroy();
        pointer = null;
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // During a bind capture MainActivity must see the key without KeyMouse acting on it.
        if (calibrationMode || settings == null) return false;

        MouseAction action = findAction(event);
        if (action == null) return false;

        if (action == MouseAction.TRIGGER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) actionDown(action);
            else if (event.getAction() == KeyEvent.ACTION_UP) actionUp(action);
            return false;
        }

        // Hidden mouse means the keypad behaves exactly like a normal Android keypad.
        if (pointer == null || !pointer.isVisible()) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) actionDown(action);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            actionUp(action);
        }

        // Never consume/remap the physical event. Other apps may use the same key.
        return false;
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
                triggerShort();
            } else if (action == MouseAction.CURSOR_NEXT) {
                cycleCursor();
            } else if (action.movement) {
                if (pointer != null && pointer.isVisible()) {
                    float speed = settings == null ? 10 : settings.speedPxPerTick();
                    moveForActions(EnumSet.of(action), speed);
                }
            } else if (action == MouseAction.LEFT_BUTTON) {
                actionDown(action);
                actionUp(action);
            }
        });
    }

    public void externalTriggerShort() {
        main.post(this::triggerShort);
    }

    public void externalTriggerLong() {
        main.post(this::toggleMouseVisibility);
    }

    public void externalMouseToggle() {
        main.post(this::toggleMouseVisibility);
    }

    public void externalModeToggle() {
        main.post(this::triggerShort);
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

    public void reloadSettings() {
        main.post(() -> {
            if (settings == null) settings = new Settings(this);
            bindings = settings.allBindings();
            if (pointer != null) pointer.setCursorStyle(settings.cursorStyle());
            enableKeyObservation();
        });
    }

    private MouseAction findAction(KeyEvent event) {
        if (bindings == null) return null;
        for (Map.Entry<MouseAction, Binding> e : bindings.entrySet()) {
            if (e.getValue().matches(event)) return e.getKey();
        }
        return null;
    }

    private void actionDown(MouseAction action) {
        if (pointer == null) return;
        if (action == MouseAction.TRIGGER) {
            if (triggerHeld) return;
            triggerHeld = true;
            triggerLongFired = false;
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
            if (!triggerLongFired) triggerShort();
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

    private void triggerShort() {
        if (pointer == null || !pointer.isVisible()) return;
        pointer.toggleScrollMode();
        showToast(pointer.isScrollMode() ? "Mouse mode: Scroll" : "Mouse mode: Normal");
    }

    private void toggleMouseVisibility() {
        if (pointer == null) return;
        if (pointer.isVisible()) {
            pressed.clear();
            stopTicker();
        }
        pointer.toggleVisibility();
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
            float speed = settings == null ? 10 : settings.speedPxPerTick();
            moveForActions(pressed, speed);
            main.postDelayed(this, TICK_MS);
        }
    };

    private void moveForActions(Iterable<MouseAction> actions, float speed) {
        int vx = 0;
        int vy = 0;
        for (MouseAction action : actions) {
            if (!action.movement) continue;
            vx += action.dx;
            vy += action.dy;
        }
        if ((vx == 0 && vy == 0) || pointer == null) return;
        vx = Integer.signum(vx);
        vy = Integer.signum(vy);
        float scale = (vx != 0 && vy != 0) ? 0.70710678f : 1f;
        pointer.move(vx * speed * scale, vy * speed * scale);
    }

    private void showToast(String message) {
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        feedbackToast.show();
    }

    private void resetInputState() {
        pressed.clear();
        stopTicker();
        triggerHeld = false;
        triggerLongFired = false;
        main.removeCallbacks(triggerLongPress);
    }

    private void enableKeyObservation() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }
}
