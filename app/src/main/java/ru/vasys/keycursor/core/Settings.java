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
    private static final int CURRENT_CURSOR_SET_VERSION = 3;
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

    public boolean wrapEdges() {
        return prefs.getBoolean(WRAP_EDGES, false);
    }

    public void setWrapEdges(boolean enabled) {
        prefs.edit().putBoolean(WRAP_EDGES, enabled).apply();
    }
}
