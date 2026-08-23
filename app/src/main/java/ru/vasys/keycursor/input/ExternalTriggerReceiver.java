package ru.vasys.keycursor.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;

import ru.vasys.keycursor.core.MouseAction;
import ru.vasys.keycursor.core.Settings;
import ru.vasys.keycursor.service.MouseAccessibilityService;

/** Public Intent surface for Key Mapper / Tasker / Automate / ADB. */
public final class ExternalTriggerReceiver extends BroadcastReceiver {
    public static final String PREFIX = "ru.vasys.keycursor.";
    public static final String EXTRA_KEY = "key";

    public static void setComponentEnabled(Context context, boolean enabled) {
        ComponentName component = new ComponentName(context, ExternalTriggerReceiver.class);
        PackageManager pm = context.getPackageManager();
        int desired = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(component) == desired) return;
        pm.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Settings.isExternalControlEnabled(context)) return;
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if ((PREFIX + "action.MOUSE_TOGGLE").equals(action)) { service.externalMouseToggle(); return; }
        if ((PREFIX + "action.MODE_TOGGLE").equals(action)) { service.externalModeToggle(); return; }
        if ((PREFIX + "action.MOUSE_KEY_SHORT").equals(action)) { service.externalTriggerShort(); return; }
        if ((PREFIX + "action.MOUSE_KEY_LONG").equals(action)) { service.externalTriggerLong(); return; }
        if ((PREFIX + "action.MOUSE_KEY_DOWN").equals(action)) { service.externalDown(MouseAction.TRIGGER); return; }
        if ((PREFIX + "action.MOUSE_KEY_UP").equals(action)) { service.externalUp(MouseAction.TRIGGER); return; }
        if ((PREFIX + "action.CURSOR_NEXT").equals(action)) { service.externalCursorNext(); return; }
        if ((PREFIX + "action.LEFT_DOWN").equals(action)) { service.externalDown(MouseAction.LEFT_BUTTON); return; }
        if ((PREFIX + "action.LEFT_UP").equals(action)) { service.externalUp(MouseAction.LEFT_BUTTON); return; }
        if (action.startsWith(PREFIX + "action.MOVE_")) {
            try {
                service.externalPulse(MouseAction.valueOf(action.substring((PREFIX + "action.").length())));
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }

        String raw = intent.getStringExtra(EXTRA_KEY);
        if (raw == null) return;
        final MouseAction key;
        try {
            key = MouseAction.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return;
        }

        if ((PREFIX + "input.DOWN").equals(action)) {
            service.externalDown(key);
        } else if ((PREFIX + "input.UP").equals(action)) {
            service.externalUp(key);
        } else if ((PREFIX + "input.PULSE").equals(action)) {
            service.externalPulse(key);
        }
    }
}
