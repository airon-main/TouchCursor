package ru.vasys.keymouse.core;

import java.util.HashSet;
import java.util.Set;

/** Pure-Java parser for the legacy Settings.Global policy_control format. */
public final class LegacyPolicyControl {
    private LegacyPolicyControl() {
    }

    public static boolean hidesNavigation(String value, String foregroundPackage) {
        if (value == null || value.trim().isEmpty()) return false;

        String navFilter = null;
        String[] pairs = value.split(":");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            if (equals < 0) continue;
            String name = pair.substring(0, equals).trim();
            String filter = pair.substring(equals + 1).trim();
            if ("immersive.full".equals(name) || "immersive.navigation".equals(name)) {
                navFilter = filter;
            }
        }
        return matchesFilter(navFilter, foregroundPackage);
    }

    static boolean matchesFilter(String filter, String foregroundPackage) {
        if (filter == null) return false;
        Set<String> allow = new HashSet<>();
        Set<String> deny = new HashSet<>();
        for (String raw : filter.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            if (token.charAt(0) == '-' && token.length() > 1) deny.add(token.substring(1));
            else allow.add(token);
        }

        String pkg = foregroundPackage == null ? "" : foregroundPackage;
        if (deny.contains("*") || deny.contains(pkg) || deny.contains("apps")) return false;
        if (allow.contains("*") || allow.contains("apps")) return true;
        return !pkg.isEmpty() && allow.contains(pkg);
    }
}
