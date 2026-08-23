package ru.vasys.keymouse.view;

import android.content.Context;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.ImageView;

import ru.vasys.keymouse.core.CursorColor;
import ru.vasys.keymouse.core.CursorStyle;

/**
 * Shared vector cursor renderer used by both the accessibility overlay and UI previews.
 * The second copy is scaled by one device-independent pixel on each side to create a
 * thin inverse-color outline without maintaining separate stroked assets.
 */
public final class CursorGlyphView extends FrameLayout {
    private static final float OUTLINE_DP = 0.75f;
    private final ImageView outline;
    private final ImageView foreground;
    private CursorStyle style = CursorStyle.ARROW;
    private CursorColor color = CursorColor.WHITE;

    public CursorGlyphView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        outline = image(context);
        foreground = image(context);
        addView(outline, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(foreground, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        applyDrawable();
        applyColors();
        addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> updateOutlineScale());
    }

    public void setStyle(CursorStyle style) {
        this.style = style == null ? CursorStyle.ARROW : style;
        applyDrawable();
    }

    public void setCursorColor(CursorColor color) {
        this.color = color == null ? CursorColor.WHITE : color;
        applyColors();
    }

    public void setCursorAlpha(float alpha) {
        foreground.setAlpha(alpha);
        outline.setAlpha(alpha);
    }

    private void applyDrawable() {
        outline.setImageResource(style.drawableRes);
        foreground.setImageResource(style.drawableRes);
    }

    private void applyColors() {
        int main = color.argb;
        foreground.setColorFilter(main);
        outline.setColorFilter(inverseRgb(main));
    }

    private void updateOutlineScale() {
        int content = Math.max(1, Math.min(getWidth(), getHeight()));
        float outlinePx = Math.max(1f, getResources().getDisplayMetrics().density * OUTLINE_DP);
        float scale = (content + outlinePx * 2f) / content;
        outline.setScaleX(scale);
        outline.setScaleY(scale);
    }

    private static ImageView image(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    private static int inverseRgb(int color) {
        return Color.argb(Color.alpha(color),
                255 - Color.red(color),
                255 - Color.green(color),
                255 - Color.blue(color));
    }
}
