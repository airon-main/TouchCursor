package dev.keymouse.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.keymouse.core.MouseAction;
import dev.keymouse.service.MouseAccessibilityService;

/**
 * Public Intent surface for Key Mapper / Tasker / Automate / ADB.
 * Generic actions use string extra "key" with a MouseAction enum name.
 */
public final class ExternalTriggerReceiver extends BroadcastReceiver {
    public static final String EXTRA_KEY = "key";

    @Override
    public void onReceive(Context context, Intent intent) {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service == null || intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if ("dev.keymouse.action.TRIGGER_SHORT".equals(action)) { service.externalTriggerShort(); return; }
        if ("dev.keymouse.action.TRIGGER_LONG".equals(action)) { service.externalTriggerLong(); return; }
        if ("dev.keymouse.action.TRIGGER_DOWN".equals(action)) { service.externalDown(MouseAction.TRIGGER); return; }
        if ("dev.keymouse.action.TRIGGER_UP".equals(action)) { service.externalUp(MouseAction.TRIGGER); return; }
        if ("dev.keymouse.action.LEFT_DOWN".equals(action)) { service.externalDown(MouseAction.LEFT_BUTTON); return; }
        if ("dev.keymouse.action.LEFT_UP".equals(action)) { service.externalUp(MouseAction.LEFT_BUTTON); return; }
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
