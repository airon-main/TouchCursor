package ru.vasys.keycursor.core;

import android.view.KeyEvent;

public final class Binding {
    public final int keyCode;
    public final int scanCode;

    public Binding(int keyCode, int scanCode) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
    }

    public boolean matchesScanCode(KeyEvent event) {
        return scanCode > 0 && event.getScanCode() > 0 && scanCode == event.getScanCode();
    }

    public boolean matchesKeyCode(int deliveredKeyCode) {
        return scanCode <= 0 && keyCode >= 0 && keyCode == deliveredKeyCode;
    }

    public boolean isBound() {
        return keyCode >= 0 || scanCode > 0;
    }

    public String displayName() {
        if (!isBound()) return "unbound";
        String key = keyCode >= 0 ? KeyEvent.keyCodeToString(keyCode) : "KEYCODE_UNKNOWN";
        return scanCode > 0 ? key + " / scan " + scanCode : key;
    }
}
