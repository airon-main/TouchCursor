package ru.vasys.keycursor.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

import ru.vasys.keycursor.core.PadGestureEngine;
import ru.vasys.keycursor.core.Settings;
import ru.vasys.keycursor.view.TouchpadView;

/**
 * Hosts the touchpad surface in its own accessibility-overlay window: either the whole screen or a
 * movable / resizable rectangle (geometry is stored as screen fractions so it survives rotation).
 *
 * Injected gestures are hit-tested like real touches, so a touchable pad sitting on top of the
 * cursor would swallow the clicks KeyCursor injects. While an injected stroke is active and its
 * target lies inside the pad, the window is made FLAG_NOT_TOUCHABLE (see {@link #setPassThrough}).
 */
public final class TouchpadOverlay implements TouchpadView.Host {
    private static final int MIN_WIDTH_DP = 140;
    private static final int MIN_HEIGHT_DP = 100;

    private final AccessibilityService service;
    private final WindowManager wm;
    private final Settings settings;
    private final PadGestureEngine engine;
    private final TouchpadView view;
    private final WindowManager.LayoutParams params;
    private final float density;

    private boolean shown;
    private boolean added;
    private boolean passThrough;
    private boolean fullScreen;

    private int screenW = 1;
    private int screenH = 1;
    // Pad rectangle in screen pixels (floats so slow drags do not lose sub-pixel movement).
    private float padX;
    private float padY;
    private float padW;
    private float padH;

    public TouchpadOverlay(AccessibilityService service, Settings settings,
                           PadGestureEngine.Listener listener) {
        this.service = service;
        this.settings = settings;
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        density = service.getResources().getDisplayMetrics().density;
        engine = new PadGestureEngine(listener);
        view = new TouchpadView(service, engine, this);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                baseFlags(),
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        applySettings();
    }

    public boolean isShown() {
        return shown && added;
    }

    public void show() {
        if (shown) return;
        refreshScreenSize();
        applySettings();
        try {
            wm.addView(view, params);
            added = true;
            shown = true;
        } catch (RuntimeException e) {
            added = false;
            shown = false;
        }
    }

    public void hide() {
        if (!shown && !added) return;
        // Release a held drag so no virtual finger is left down.
        engine.onCancel();
        shown = false;
        passThrough = false;
        if (added) {
            try {
                wm.removeViewImmediate(view);
            } catch (RuntimeException ignored) {
            }
            added = false;
        }
    }

    public void destroy() {
        hide();
    }

    /** Re-reads every touchpad preference. Safe to call whether or not the pad is showing. */
    public void applySettings() {
        fullScreen = settings.padFullScreen();

        engine.sensitivity = settings.padSensitivityPercent() / 100f;
        engine.acceleration = settings.padAcceleration();
        engine.tapAndDrag = settings.padTapAndDrag();
        engine.tapDelayMs = settings.padTapDelayMs();
        engine.touchSlopPx = 8f * density;
        engine.swipeThresholdPx = 56f * density;

        view.setRectMode(!fullScreen);
        view.setOpacity(settings.padOpacityPercent() / 100f);

        refreshScreenSize();
        loadRectFromSettings();
        pushLayout();
    }

    /** Call after a rotation / configuration change. */
    public void refreshDisplayBounds() {
        refreshScreenSize();
        loadRectFromSettings();
        pushLayout();
    }

    /**
     * While true the pad ignores touches so an injected gesture can reach the app beneath it.
     * Idempotent; only touches the window when the state actually changes.
     *
     * @return true if the state changed (the new flag needs a frame or two to take effect)
     */
    public boolean setPassThrough(boolean value) {
        if (passThrough == value) return false;
        passThrough = value;
        pushLayout();
        return true;
    }

    /** True if the screen point lies on the touchable pad surface. */
    public boolean containsScreenPoint(float sx, float sy) {
        if (!isShown()) return false;
        if (fullScreen) return true;
        return sx >= padX && sx <= padX + padW && sy >= padY && sy <= padY + padH;
    }

    // ---- TouchpadView.Host ----

    @Override
    public void onPadDrag(int chromeMode, float rawDx, float rawDy) {
        if (fullScreen) return;
        if (chromeMode == TouchpadView.CHROME_MOVE) {
            padX += rawDx;
            padY += rawDy;
        } else if (chromeMode == TouchpadView.CHROME_RESIZE) {
            padW += rawDx;
            padH += rawDy;
        }
        clampRect();
        pushLayout();
    }

    @Override
    public void onPadDragEnd(int chromeMode) {
        if (fullScreen) return;
        settings.setPadRect(padX / screenW, padY / screenH, padW / screenW, padH / screenH);
    }

    // ---- internals ----

    private static int baseFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    private void pushLayout() {
        params.flags = baseFlags()
                | (passThrough ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0);
        if (fullScreen) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.x = 0;
            params.y = 0;
        } else {
            params.width = Math.round(padW);
            params.height = Math.round(padH);
            params.x = Math.round(padX);
            params.y = Math.round(padY);
        }
        if (!added) return;
        try {
            wm.updateViewLayout(view, params);
        } catch (RuntimeException ignored) {
        }
    }

    private void loadRectFromSettings() {
        float[] f = settings.padRect();
        padX = f[0] * screenW;
        padY = f[1] * screenH;
        padW = f[2] * screenW;
        padH = f[3] * screenH;
        clampRect();
    }

    private void clampRect() {
        float minW = Math.min(MIN_WIDTH_DP * density, screenW);
        float minH = Math.min(MIN_HEIGHT_DP * density, screenH);
        padW = Math.max(minW, Math.min(screenW, padW));
        padH = Math.max(minH, Math.min(screenH, padH));
        padX = Math.max(0f, Math.min(screenW - padW, padX));
        padY = Math.max(0f, Math.min(screenH - padH, padY));
    }

    @SuppressWarnings("deprecation")
    private void refreshScreenSize() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        screenW = Math.max(1, m.widthPixels);
        screenH = Math.max(1, m.heightPixels);
    }
}
