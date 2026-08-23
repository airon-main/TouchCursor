package ru.vasys.keycursor.core;

import ru.vasys.keycursor.R;

public enum CursorStyle {
    ARROW("ARROW", "Arrow", R.drawable.cursor_arrow, 0.25f, 0.125f),
    HAND("HAND", "Hand", R.drawable.cursor_hand, 0.457f, 0.104f),
    CROSSHAIR("CROSSHAIR", "Crosshair", R.drawable.cursor_crosshair, 0.50f, 0.50f),
    TARGET("TARGET", "Target", R.drawable.cursor_target, 0.50f, 0.50f),
    RING("RING", "Ring", R.drawable.cursor_ring, 0.50f, 0.50f),
    PLUS("PLUS", "Plus", R.drawable.cursor_plus, 0.50f, 0.50f),
    CORNERS("CORNERS", "Corner brackets", R.drawable.cursor_corners, 0.50f, 0.50f),
    FOCUS("FOCUS", "Focus", R.drawable.cursor_focus, 0.50f, 0.50f),
    INWARD_CORNERS("INWARD_CORNERS", "Inward corners", R.drawable.cursor_inward_corners, 0.50f, 0.50f);

    public final String id;
    public final String label;
    public final int drawableRes;
    public final float hotspotX;
    public final float hotspotY;

    CursorStyle(String id, String label, int drawableRes, float hotspotX, float hotspotY) {
        this.id = id;
        this.label = label;
        this.drawableRes = drawableRes;
        this.hotspotX = hotspotX;
        this.hotspotY = hotspotY;
    }

    public static CursorStyle fromId(String id) {
        for (CursorStyle style : values()) if (style.id.equals(id)) return style;
        return ARROW;
    }
}
