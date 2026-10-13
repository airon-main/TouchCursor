package ru.vasys.keycursor.core;

import android.os.Build;

/** Actions that can be bound to a two or three-finger swipe on the touchpad. */
public enum PadAction {
    NONE("Do nothing", 1),
    BACK("Back", 1),
    HOME("Home", 1),
    RECENTS("Recent apps", 1),
    NOTIFICATIONS("Notifications", 1),
    QUICK_SETTINGS("Quick settings", 1),
    POWER_DIALOG("Power menu", 1),
    LOCK_SCREEN("Lock screen", Build.VERSION_CODES.P),
    SCREENSHOT("Screenshot", Build.VERSION_CODES.P),
    TOGGLE_HOLD("Toggle Hold mode", 1),
    NEXT_CURSOR("Next cursor style", 1),
    HIDE_PAD("Hide touchpad", 1);

    public final String label;
    public final int minSdk;

    PadAction(String label, int minSdk) {
        this.label = label;
        this.minSdk = minSdk;
    }

    public boolean isAvailable() {
        return Build.VERSION.SDK_INT >= minSdk;
    }

    public static PadAction fromName(String name, PadAction fallback) {
        if (name == null) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
