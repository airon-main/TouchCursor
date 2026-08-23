package dev.keymouse.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.EnumMap;
import java.util.Map;

public final class Settings {
    private static final String PREFS = "keymouse";
    private static final String PREFIX_KEY = "binding.key.";
    private static final String PREFIX_SCAN = "binding.scan.";
    private static final String CAPTURE_KEYS = "capture_keys";
    private static final String CURSOR_STYLE = "cursor_style";
    private static final String SPEED = "speed";

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    private void ensureDefaults() {
        if (prefs.contains(PREFIX_KEY + MouseAction.MOVE_UP.name())) return;
        SharedPreferences.Editor e = prefs.edit();
        putDefault(e, MouseAction.MOVE_UP, KeyEvent.KEYCODE_DPAD_UP);
        putDefault(e, MouseAction.MOVE_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
        putDefault(e, MouseAction.MOVE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        putDefault(e, MouseAction.MOVE_LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
        putDefault(e, MouseAction.LEFT_BUTTON, KeyEvent.KEYCODE_DPAD_CENTER);
        for (MouseAction action : MouseAction.values()) {
            if (!prefs.contains(PREFIX_KEY + action.name()) &&
                    action != MouseAction.MOVE_UP && action != MouseAction.MOVE_RIGHT &&
                    action != MouseAction.MOVE_DOWN && action != MouseAction.MOVE_LEFT &&
                    action != MouseAction.LEFT_BUTTON) {
                e.putInt(PREFIX_KEY + action.name(), -1);
                e.putInt(PREFIX_SCAN + action.name(), 0);
            }
        }
        e.apply();
    }

    private static void putDefault(SharedPreferences.Editor e, MouseAction action, int keyCode) {
        e.putInt(PREFIX_KEY + action.name(), keyCode);
        e.putInt(PREFIX_SCAN + action.name(), 0);
    }

    public Binding getBinding(MouseAction action) {
        return new Binding(
                prefs.getInt(PREFIX_KEY + action.name(), -1),
                prefs.getInt(PREFIX_SCAN + action.name(), 0));
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

    public boolean captureHardwareKeys() {
        return prefs.getBoolean(CAPTURE_KEYS, true);
    }

    public void setCaptureHardwareKeys(boolean value) {
        prefs.edit().putBoolean(CAPTURE_KEYS, value).apply();
    }

    public String cursorStyle() {
        return prefs.getString(CURSOR_STYLE, "CROSSHAIR");
    }

    public void setCursorStyle(String value) {
        prefs.edit().putString(CURSOR_STYLE, value).apply();
    }

    public int speedPxPerTick() {
        return prefs.getInt(SPEED, 10);
    }

    public void setSpeedPxPerTick(int value) {
        prefs.edit().putInt(SPEED, value).apply();
    }
}
