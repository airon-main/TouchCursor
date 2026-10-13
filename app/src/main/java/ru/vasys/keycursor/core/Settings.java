package ru.vasys.keycursor.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Settings {
    private static final String PREFS = "keycursor";
    private static final String PREFIX_KEY = "binding.key.";
    private static final String PREFIX_SCAN = "binding.scan.";
    private static final String CURSOR_STYLE = "cursor_style";
    private static final String CURSOR_ENABLED = "cursor_enabled";
    private static final String SPEED = "speed";
    private static final String WRAP_EDGES = "wrap_edges";
    private static final String CURSOR_COLOR = "cursor_color";
    private static final String CURSOR_SET_VERSION = "cursor_set_version";
    private static final String CURSOR_SIZE = "cursor_size_dp";
    private static final String EXTERNAL_CONTROL = "external_control";
    private static final String ROTATE_LANDSCAPE_CONTROLS = "rotate_landscape_controls";
    private static final String INVERT_X_PORTRAIT = "invert_x_portrait";
    private static final String INVERT_Y_PORTRAIT = "invert_y_portrait";
    private static final String INVERT_X_LANDSCAPE = "invert_x_landscape";
    private static final String INVERT_Y_LANDSCAPE = "invert_y_landscape";
    private static final String BLEND_DIRECTIONS = "blend_directions";
    private static final String CAPTURE_MOUSE_KEY_VISIBLE = "capture_mouse_key_visible";
    private static final String PAD_FULL_SCREEN = "pad_full_screen";
    private static final String PAD_LEFT = "pad_left_f";
    private static final String PAD_TOP = "pad_top_f";
    private static final String PAD_WIDTH = "pad_width_f";
    private static final String PAD_HEIGHT = "pad_height_f";
    private static final String PAD_SENSITIVITY = "pad_sensitivity_pct";
    private static final String PAD_ACCELERATION = "pad_acceleration";
    private static final String PAD_TAP_AND_DRAG = "pad_tap_and_drag";
    private static final String PAD_TAP_DELAY = "pad_tap_delay_ms";
    private static final String PAD_OPACITY = "pad_opacity_pct";
    // Three-finger keys keep their original name so existing choices survive the upgrade.
    private static final String PAD_SWIPE3_PREFIX = "pad_swipe.";
    private static final String PAD_SWIPE2_PREFIX = "pad_swipe2.";
    private static final int CURRENT_CURSOR_SET_VERSION = 3;
    public static final int MIN_PAD_SENSITIVITY = 50;
    public static final int MAX_PAD_SENSITIVITY = 400;
    public static final int MIN_PAD_TAP_DELAY_MS = 0;
    public static final int MAX_PAD_TAP_DELAY_MS = 500;
    /** The movable pad must stay visible so it can be found; the full-screen pad may be invisible. */
    public static final int MIN_PAD_OPACITY_MOVABLE = 5;
    public static final int MIN_PAD_OPACITY_FULL_SCREEN = 0;
    public static final int MAX_PAD_OPACITY = 60;
    public static final int DEFAULT_CURSOR_SIZE_DP = 42;
    public static final int MIN_CURSOR_SIZE_DP = 24;
    public static final int MAX_CURSOR_SIZE_DP = 72;

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;
        for (MouseAction action : MouseAction.values()) {
            String keyName = PREFIX_KEY + action.name();
            String scanName = PREFIX_SCAN + action.name();
            if (!prefs.contains(keyName)) {
                e.putInt(keyName, defaultKeyCode(action));
                changed = true;
            }
            if (!prefs.contains(scanName)) {
                e.putInt(scanName, 0);
                changed = true;
            }
        }
        if (!prefs.contains(CURSOR_STYLE)) {
            e.putString(CURSOR_STYLE, CursorStyle.ARROW.id);
            changed = true;
        }
        if (!prefs.contains(CURSOR_ENABLED)) {
            LinkedHashSet<String> all = new LinkedHashSet<>();
            for (CursorStyle style : CursorStyle.values()) all.add(style.id);
            e.putStringSet(CURSOR_ENABLED, all);
            changed = true;
        }
        if (!prefs.contains(CURSOR_COLOR)) {
            e.putString(CURSOR_COLOR, CursorColor.WHITE.id);
            changed = true;
        }
        if (!prefs.contains(CURSOR_SIZE)) {
            e.putInt(CURSOR_SIZE, DEFAULT_CURSOR_SIZE_DP);
            changed = true;
        }
        if (prefs.getInt(CURSOR_SET_VERSION, 0) < CURRENT_CURSOR_SET_VERSION) {
            LinkedHashSet<String> enabled = new LinkedHashSet<>();
            Set<String> stored = prefs.getStringSet(CURSOR_ENABLED, null);
            if (stored != null) enabled.addAll(stored);
            else for (CursorStyle style : CursorStyle.values()) enabled.add(style.id);
            LinkedHashSet<String> valid = new LinkedHashSet<>();
            for (CursorStyle style : CursorStyle.values()) {
                if (enabled.contains(style.id) || style == CursorStyle.INWARD_CORNERS) valid.add(style.id);
            }
            e.putStringSet(CURSOR_ENABLED, valid);
            e.putInt(CURSOR_SET_VERSION, CURRENT_CURSOR_SET_VERSION);
            changed = true;
        }
        if (changed) e.apply();
        repairCursorSelection();
    }

    public static int defaultKeyCode(MouseAction action) {
        switch (action) {
            case MOVE_UP: return KeyEvent.KEYCODE_DPAD_UP;
            case MOVE_RIGHT: return KeyEvent.KEYCODE_DPAD_RIGHT;
            case MOVE_DOWN: return KeyEvent.KEYCODE_DPAD_DOWN;
            case MOVE_LEFT: return KeyEvent.KEYCODE_DPAD_LEFT;
            case LEFT_BUTTON: return KeyEvent.KEYCODE_DPAD_CENTER;
            default: return -1;
        }
    }

    public Binding getBinding(MouseAction action) {
        return new Binding(
                prefs.getInt(PREFIX_KEY + action.name(), defaultKeyCode(action)),
                prefs.getInt(PREFIX_SCAN + action.name(), 0));
    }

    public BindingState bindingState(MouseAction action) {
        Binding binding = getBinding(action);
        if (!binding.isBound()) return BindingState.NONE;
        int defaultCode = defaultKeyCode(action);
        if (defaultCode >= 0 && binding.keyCode == defaultCode) return BindingState.DEFAULT;
        return BindingState.CUSTOM;
    }

    public void setBinding(MouseAction action, int keyCode, int scanCode) {
        prefs.edit()
                .putInt(PREFIX_KEY + action.name(), keyCode)
                .putInt(PREFIX_SCAN + action.name(), scanCode)
                .apply();
    }

    public void clearBinding(MouseAction action) {
        setBinding(action, -1, 0);
    }

    public Map<MouseAction, Binding> allBindings() {
        Map<MouseAction, Binding> result = new EnumMap<>(MouseAction.class);
        for (MouseAction action : MouseAction.values()) result.put(action, getBinding(action));
        return result;
    }

    public CursorStyle cursorStyle() {
        return CursorStyle.fromId(prefs.getString(CURSOR_STYLE, CursorStyle.ARROW.id));
    }

    public void setCursorStyle(CursorStyle value) {
        Set<String> enabled = enabledCursorStyleIds();
        if (!enabled.contains(value.id)) {
            enabled.add(value.id);
            prefs.edit().putStringSet(CURSOR_ENABLED, enabled).apply();
        }
        prefs.edit().putString(CURSOR_STYLE, value.id).apply();
    }

    public Set<String> enabledCursorStyleIds() {
        Set<String> stored = prefs.getStringSet(CURSOR_ENABLED, null);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (stored != null) result.addAll(stored);
        if (result.isEmpty()) result.add(CursorStyle.ARROW.id);
        return result;
    }

    public boolean isCursorStyleEnabled(CursorStyle style) {
        return enabledCursorStyleIds().contains(style.id);
    }

    public void setCursorStyleEnabled(CursorStyle style, boolean enabled) {
        LinkedHashSet<String> values = new LinkedHashSet<>(enabledCursorStyleIds());
        if (enabled) {
            values.add(style.id);
        } else {
            if (values.size() <= 1 && values.contains(style.id)) return;
            values.remove(style.id);
        }
        SharedPreferences.Editor e = prefs.edit().putStringSet(CURSOR_ENABLED, values);
        if (!values.contains(cursorStyle().id)) {
            for (CursorStyle candidate : CursorStyle.values()) {
                if (values.contains(candidate.id)) {
                    e.putString(CURSOR_STYLE, candidate.id);
                    break;
                }
            }
        }
        e.apply();
    }

    public CursorStyle cycleCursorStyle() {
        Set<String> enabled = enabledCursorStyleIds();
        CursorStyle current = cursorStyle();
        CursorStyle[] all = CursorStyle.values();
        int currentIndex = 0;
        for (int i = 0; i < all.length; i++) if (all[i] == current) currentIndex = i;
        for (int offset = 1; offset <= all.length; offset++) {
            CursorStyle next = all[(currentIndex + offset) % all.length];
            if (enabled.contains(next.id)) {
                setCursorStyle(next);
                return next;
            }
        }
        return current;
    }

    private void repairCursorSelection() {
        Set<String> enabled = enabledCursorStyleIds();
        CursorStyle selected = cursorStyle();
        if (enabled.contains(selected.id)) return;
        for (CursorStyle style : CursorStyle.values()) {
            if (enabled.contains(style.id)) {
                prefs.edit().putString(CURSOR_STYLE, style.id).apply();
                return;
            }
        }
    }

    public int speedPxPerTick() {
        return prefs.getInt(SPEED, 10);
    }

    public void setSpeedPxPerTick(int value) {
        prefs.edit().putInt(SPEED, value).apply();
    }

    public CursorColor cursorColor() {
        return CursorColor.fromId(prefs.getString(CURSOR_COLOR, CursorColor.WHITE.id));
    }

    public void setCursorColor(CursorColor color) {
        prefs.edit().putString(CURSOR_COLOR, color.id).apply();
    }

    public int cursorSizeDp() {
        return Math.max(MIN_CURSOR_SIZE_DP, Math.min(MAX_CURSOR_SIZE_DP, prefs.getInt(CURSOR_SIZE, DEFAULT_CURSOR_SIZE_DP)));
    }

    public void setCursorSizeDp(int value) {
        int safe = Math.max(MIN_CURSOR_SIZE_DP, Math.min(MAX_CURSOR_SIZE_DP, value));
        prefs.edit().putInt(CURSOR_SIZE, safe).apply();
    }

    public static boolean isExternalControlEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(EXTERNAL_CONTROL, false);
    }

    public boolean externalControlEnabled() {
        return prefs.getBoolean(EXTERNAL_CONTROL, false);
    }

    public void setExternalControlEnabled(boolean enabled) {
        prefs.edit().putBoolean(EXTERNAL_CONTROL, enabled).apply();
    }


    public boolean rotateLandscapeControls() {
        return prefs.getBoolean(ROTATE_LANDSCAPE_CONTROLS, false);
    }

    public void setRotateLandscapeControls(boolean enabled) {
        prefs.edit().putBoolean(ROTATE_LANDSCAPE_CONTROLS, enabled).apply();
    }

    public boolean invertHorizontalPortrait() {
        return prefs.getBoolean(INVERT_X_PORTRAIT, false);
    }

    public void setInvertHorizontalPortrait(boolean enabled) {
        prefs.edit().putBoolean(INVERT_X_PORTRAIT, enabled).apply();
    }

    public boolean invertVerticalPortrait() {
        return prefs.getBoolean(INVERT_Y_PORTRAIT, false);
    }

    public void setInvertVerticalPortrait(boolean enabled) {
        prefs.edit().putBoolean(INVERT_Y_PORTRAIT, enabled).apply();
    }

    public boolean invertHorizontalLandscape() {
        return prefs.getBoolean(INVERT_X_LANDSCAPE, false);
    }

    public void setInvertHorizontalLandscape(boolean enabled) {
        prefs.edit().putBoolean(INVERT_X_LANDSCAPE, enabled).apply();
    }

    public boolean invertVerticalLandscape() {
        return prefs.getBoolean(INVERT_Y_LANDSCAPE, false);
    }

    public void setInvertVerticalLandscape(boolean enabled) {
        prefs.edit().putBoolean(INVERT_Y_LANDSCAPE, enabled).apply();
    }

    public boolean blendDirections() {
        return prefs.getBoolean(BLEND_DIRECTIONS, false);
    }

    public void setBlendDirections(boolean enabled) {
        prefs.edit().putBoolean(BLEND_DIRECTIONS, enabled).apply();
    }

    public boolean captureMouseKeyVisible() {
        return prefs.getBoolean(CAPTURE_MOUSE_KEY_VISIBLE, true);
    }

    public void setCaptureMouseKeyVisible(boolean enabled) {
        prefs.edit().putBoolean(CAPTURE_MOUSE_KEY_VISIBLE, enabled).apply();
    }

    // ---- touchpad ----

    public boolean padFullScreen() {
        return prefs.getBoolean(PAD_FULL_SCREEN, false);
    }

    public void setPadFullScreen(boolean enabled) {
        prefs.edit().putBoolean(PAD_FULL_SCREEN, enabled).apply();
    }

    /** Pad rectangle as fractions of the screen: {left, top, width, height}. */
    public float[] padRect() {
        float w = clampF(prefs.getFloat(PAD_WIDTH, 0.9f), 0.15f, 1f);
        float h = clampF(prefs.getFloat(PAD_HEIGHT, 0.4f), 0.1f, 1f);
        float l = clampF(prefs.getFloat(PAD_LEFT, 0.05f), 0f, 1f - w);
        float t = clampF(prefs.getFloat(PAD_TOP, 0.55f), 0f, 1f - h);
        return new float[]{l, t, w, h};
    }

    public void setPadRect(float left, float top, float width, float height) {
        prefs.edit()
                .putFloat(PAD_LEFT, left)
                .putFloat(PAD_TOP, top)
                .putFloat(PAD_WIDTH, width)
                .putFloat(PAD_HEIGHT, height)
                .apply();
    }

    public void resetPadRect() {
        prefs.edit()
                .remove(PAD_LEFT).remove(PAD_TOP).remove(PAD_WIDTH).remove(PAD_HEIGHT)
                .apply();
    }

    public int padSensitivityPercent() {
        return clampI(prefs.getInt(PAD_SENSITIVITY, 160), MIN_PAD_SENSITIVITY, MAX_PAD_SENSITIVITY);
    }

    public void setPadSensitivityPercent(int value) {
        prefs.edit().putInt(PAD_SENSITIVITY, clampI(value, MIN_PAD_SENSITIVITY, MAX_PAD_SENSITIVITY)).apply();
    }

    public boolean padAcceleration() {
        return prefs.getBoolean(PAD_ACCELERATION, true);
    }

    public void setPadAcceleration(boolean enabled) {
        prefs.edit().putBoolean(PAD_ACCELERATION, enabled).apply();
    }

    public boolean padTapAndDrag() {
        return prefs.getBoolean(PAD_TAP_AND_DRAG, true);
    }

    public void setPadTapAndDrag(boolean enabled) {
        prefs.edit().putBoolean(PAD_TAP_AND_DRAG, enabled).apply();
    }

    /**
     * How long (ms) a tap waits before it clicks, so a second touch can turn it into a hold/drag
     * without the first tap having clicked. 0 = click immediately (no tap-then-drag).
     */
    public int padTapDelayMs() {
        return clampI(prefs.getInt(PAD_TAP_DELAY, 150), MIN_PAD_TAP_DELAY_MS, MAX_PAD_TAP_DELAY_MS);
    }

    public void setPadTapDelayMs(int value) {
        prefs.edit().putInt(PAD_TAP_DELAY, clampI(value, MIN_PAD_TAP_DELAY_MS, MAX_PAD_TAP_DELAY_MS)).apply();
    }

    /** Lowest opacity allowed for the current pad area. */
    public int padOpacityMinPercent() {
        return padFullScreen() ? MIN_PAD_OPACITY_FULL_SCREEN : MIN_PAD_OPACITY_MOVABLE;
    }

    /**
     * Effective opacity for the current area. The stored value is kept as chosen (so 0% on the
     * full-screen pad survives a trip through the movable pad) and only clamped when read.
     */
    public int padOpacityPercent() {
        return clampI(prefs.getInt(PAD_OPACITY, 20), padOpacityMinPercent(), MAX_PAD_OPACITY);
    }

    public void setPadOpacityPercent(int value) {
        prefs.edit().putInt(PAD_OPACITY, clampI(value, MIN_PAD_OPACITY_FULL_SCREEN, MAX_PAD_OPACITY)).apply();
    }

    /** Three-finger swipes do nothing until the user assigns an action; two-finger have defaults. */
    public static PadAction defaultSwipeAction(int fingers, SwipeDirection direction) {
        if (fingers != 2) return PadAction.NONE;
        switch (direction) {
            case UP: return PadAction.HOME;
            case DOWN: return PadAction.NOTIFICATIONS;
            case LEFT: return PadAction.BACK;
            case RIGHT: return PadAction.RECENTS;
            default: return PadAction.NONE;
        }
    }

    private static String swipeKey(int fingers, SwipeDirection direction) {
        return (fingers == 2 ? PAD_SWIPE2_PREFIX : PAD_SWIPE3_PREFIX) + direction.name();
    }

    /** @param fingers 2 or 3 */
    public PadAction padSwipeAction(int fingers, SwipeDirection direction) {
        PadAction stored = PadAction.fromName(
                prefs.getString(swipeKey(fingers, direction), null),
                defaultSwipeAction(fingers, direction));
        return stored.isAvailable() ? stored : PadAction.NONE;
    }

    /** @param fingers 2 or 3 */
    public void setPadSwipeAction(int fingers, SwipeDirection direction, PadAction action) {
        prefs.edit().putString(swipeKey(fingers, direction), action.name()).apply();
    }

    private static int clampI(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public boolean wrapEdges() {
        return prefs.getBoolean(WRAP_EDGES, false);
    }

    public void setWrapEdges(boolean enabled) {
        prefs.edit().putBoolean(WRAP_EDGES, enabled).apply();
    }
}
