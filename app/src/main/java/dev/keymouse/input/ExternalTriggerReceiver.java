package dev.keymouse.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.keymouse.core.MouseAction;
import dev.keymouse.service.MouseAccessibilityService;

/**
 * Public Intent surface for Key Mapper/Tasker/Automate/ADB.
 *
 * action = dev.keymouse.input.DOWN / UP / PULSE
 * string extra "key" = MouseAction enum name, e.g. MOVE_LEFT or CURSOR_TOGGLE.
 */
public final class ExternalTriggerReceiver extends BroadcastReceiver {
    public static final String EXTRA_KEY = "key";

    @Override
    public void onReceive(Context context, Intent intent) {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service == null || intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        // Zero-configuration aliases are convenient in Key Mapper: only the
        // broadcast action and explicit receiver component are needed.
        if ("dev.keymouse.action.CURSOR_TOGGLE".equals(action)) { service.externalPulse(MouseAction.CURSOR_TOGGLE); return; }
        if ("dev.keymouse.action.TOUCH_TOGGLE".equals(action)) { service.externalPulse(MouseAction.TOUCH_MODE); return; }
        if ("dev.keymouse.action.LEFT_DOWN".equals(action)) { service.externalDown(MouseAction.LEFT_BUTTON); return; }
        if ("dev.keymouse.action.LEFT_UP".equals(action)) { service.externalUp(MouseAction.LEFT_BUTTON); return; }
        if ("dev.keymouse.action.RIGHT_CLICK".equals(action)) { service.externalPulse(MouseAction.RIGHT_BUTTON); return; }
        if (action.startsWith("dev.keymouse.action.MOVE_")) {
            try {
                service.externalPulse(MouseAction.valueOf(action.substring("dev.keymouse.action.".length())));
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

        if ("dev.keymouse.input.DOWN".equals(action)) {
            service.externalDown(key);
        } else if ("dev.keymouse.input.UP".equals(action)) {
            service.externalUp(key);
        } else if ("dev.keymouse.input.PULSE".equals(action)) {
            service.externalPulse(key);
        }
    }
}
