package ru.vasys.keymouse.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Settings {
    private static final String PREFS = "keymouse";
    private static final String PREFIX_KEY = "binding.key.";
    private static final String PREFIX_SCAN = "binding.scan.";
    private static final String CURSOR_STYLE = "cursor_style";
    private static final String CURSOR_ENABLED = "cursor_enabled";
    private static final String SPEED = "speed";

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
}
