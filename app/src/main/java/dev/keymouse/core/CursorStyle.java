package dev.keymouse.core;

import dev.keymouse.R;

public enum CursorStyle {
    ARROW("ARROW", "Arrow", R.drawable.cursor_arrow, 0.25f, 0.08f),
    HAND("HAND", "Hand", R.drawable.cursor_hand, 0.34f, 0.10f),
    CROSSHAIR("CROSSHAIR", "Crosshair", R.drawable.cursor_crosshair, 0.50f, 0.50f),
    TARGET("TARGET", "Target", R.drawable.cursor_target, 0.50f, 0.50f),
    RING("RING", "Ring", R.drawable.cursor_ring, 0.50f, 0.50f),
    PLUS("PLUS", "Plus", R.drawable.cursor_plus, 0.50f, 0.50f);

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
