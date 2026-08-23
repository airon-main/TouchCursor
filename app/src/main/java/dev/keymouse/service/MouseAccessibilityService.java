package dev.keymouse.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.EnumSet;
import java.util.Map;

import dev.keymouse.core.Binding;
import dev.keymouse.core.MouseAction;
import dev.keymouse.core.Settings;

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
        applyKeyCaptureFlag();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No view-tree scraping is needed. This service only provides input + overlay.
    }

    @Override
    public void onInterrupt() {
        pressed.clear();
        stopTicker();
        if (pointer != null) pointer.leftUp();
    }

    @Override
    public void onDestroy() {
        runningInstance = null;
        pressed.clear();
        stopTicker();
        if (pointer != null) pointer.destroy();
        pointer = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (settings == null || !settings.captureHardwareKeys()) return false;
        MouseAction action = findAction(event);
        if (action == null) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) actionDown(action);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            actionUp(action);
        }
        return true;
    }

    public void externalDown(MouseAction action) {
        main.post(() -> actionDown(action));
    }

    public void externalUp(MouseAction action) {
        main.post(() -> actionUp(action));
    }

    public void externalPulse(MouseAction action) {
        main.post(() -> {
            if (action.movement) {
                float speed = settings == null ? 10 : settings.speedPxPerTick();
                moveForActions(EnumSet.of(action), speed);
            } else {
                actionDown(action);
                if (action == MouseAction.LEFT_BUTTON) actionUp(action);
            }
        });
    }

    public void setCalibrationMode(boolean enabled) {
        main.post(() -> {
            calibrationMode = enabled;
            applyKeyCaptureFlag();
        });
    }

    public void reloadSettings() {
        main.post(() -> {
            if (settings == null) settings = new Settings(this);
            bindings = settings.allBindings();
            if (pointer != null) pointer.setCursorStyle(settings.cursorStyle());
            applyKeyCaptureFlag();
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
        if (action.movement) {
            if (pressed.add(action)) startTicker();
            return;
        }

        switch (action) {
            case LEFT_BUTTON:
                pointer.leftDown();
                break;
            case RIGHT_BUTTON:
                pointer.contextClick();
                break;
            case TOUCH_MODE:
                pointer.toggleTouchMode();
                break;
            case CURSOR_TOGGLE:
                pointer.toggleCursor();
                break;
            default:
                break;
        }
    }

    private void actionUp(MouseAction action) {
        if (pointer == null) return;
        if (action.movement) {
            pressed.remove(action);
            if (!hasMovementPressed()) stopTicker();
            return;
        }
        if (action == MouseAction.LEFT_BUTTON) pointer.leftUp();
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
            if (!tickerRunning || pointer == null) return;
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
        if (vx == 0 && vy == 0) return;
        vx = Integer.signum(vx);
        vy = Integer.signum(vy);
        float scale = (vx != 0 && vy != 0) ? 0.70710678f : 1f;
        pointer.move(vx * speed * scale, vy * speed * scale);
    }

    private void applyKeyCaptureFlag() {
        if (settings == null) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if (settings.captureHardwareKeys() && !calibrationMode) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            pressed.clear();
            stopTicker();
        }
        setServiceInfo(info);
    }
}
