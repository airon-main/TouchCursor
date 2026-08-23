package ru.vasys.keymouse.service;

import android.content.Context;
import android.provider.Settings;

import ru.vasys.keymouse.core.LegacyPolicyControl;

/**
 * Mirrors the legacy Settings.Global policy_control immersive navigation filter
 * onto KeyMouse's accessibility overlay. Accessibility overlays are not
 * application windows, so a global filter such as immersive.navigation=apps
 * does not automatically apply to them.
 */
final class ImmersiveNavigationPolicy {
    private static final String POLICY_CONTROL = "policy_control";

    private final Context context;

    ImmersiveNavigationPolicy(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean shouldHideNavigationFor(String foregroundPackage) {
        String value;
        try {
            value = Settings.Global.getString(context.getContentResolver(), POLICY_CONTROL);
        } catch (RuntimeException ignored) {
            return false;
        }
        return LegacyPolicyControl.hidesNavigation(value, foregroundPackage);
    }
}
