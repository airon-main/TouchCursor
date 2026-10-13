package ru.vasys.keycursor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import ru.vasys.keycursor.core.PadGestureEngine;

/**
 * The touchpad surface. Translates MotionEvents into {@link PadGestureEngine} calls and, in
 * "pad" (rectangle) mode, provides a move bar on top and a resize grip in the bottom-right
 * corner. Those two chrome areas never reach the gesture engine.
 */
public final class TouchpadView extends View {
    public static final int CHROME_NONE = 0;
    public static final int CHROME_MOVE = 1;
    public static final int CHROME_RESIZE = 2;

    /** Receives pad move/resize drags (deltas in raw screen pixels). */
    public interface Host {
        void onPadDrag(int chromeMode, float rawDx, float rawDy);

        void onPadDragEnd(int chromeMode);
    }

    private static final int ACCENT = 0x5B7CFA;

    private final PadGestureEngine engine;
    private final Host host;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            TouchpadView.this.engine.onTimer();
        }
    };
    private final float density;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean rectMode = true;
    private float opacity = 0.2f;

    private int primaryId = -1;

    private int chromeMode = CHROME_NONE;
    private float chromeLastX;
    private float chromeLastY;

    public TouchpadView(Context context, PadGestureEngine engine, Host host) {
        super(context);
        this.engine = engine;
        this.host = host;
        engine.setTimer(new PadGestureEngine.Timer() {
            @Override
            public void schedule(long delayMs) {
                timerHandler.removeCallbacks(timerTick);
                timerHandler.postDelayed(timerTick, delayMs);
            }

            @Override
            public void cancel() {
                timerHandler.removeCallbacks(timerTick);
            }
        });
        density = context.getResources().getDisplayMetrics().density;

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setStrokeWidth(3f * density);
        handlePaint.setColor(0xFFFFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** In rect mode the move bar and resize grip are shown; in full-screen mode they are not. */
    public void setRectMode(boolean value) {
        if (rectMode == value) return;
        rectMode = value;
        invalidate();
    }

    /** 0..1 */
    public void setOpacity(float value) {
        opacity = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float effective = rectMode ? opacity : opacity * 0.5f;
        fillPaint.setColor(0xFF000000 | ACCENT);
        fillPaint.setAlpha(Math.round(255f * effective));
        strokePaint.setColor(0xFF000000 | ACCENT);
        // The movable pad is never fully invisible (minimum opacity is enforced in settings). The
        // full-screen outline fades out with the fill so 0% really is invisible.
        strokePaint.setAlpha(rectMode ? 200 : Math.round(140f * Math.min(1f, opacity / 0.2f)));

        float inset = strokePaint.getStrokeWidth() / 2f;
        rect.set(inset, inset, w - inset, h - inset);
        float radius = rectMode ? 18f * density : 0f;
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        if (!rectMode) return;
        handlePaint.setAlpha(210);
        // Move bar: a short pill centred near the top.
        float barY = 14f * density;
        canvas.drawLine(w / 2f - 22f * density, barY, w / 2f + 22f * density, barY, handlePaint);
        // Resize grip: two diagonal strokes in the bottom-right corner.
        float g = 16f * density;
        float m = 12f * density;
        canvas.drawLine(w - m - g, h - m, w - m, h - m - g, handlePaint);
        canvas.drawLine(w - m - g / 2f, h - m, w - m, h - m - g / 2f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();

        if (chromeMode != CHROME_NONE) {
            return handleChrome(e, action);
        }
        if (rectMode && action == MotionEvent.ACTION_DOWN) {
            int hit = hitChrome(e.getX(), e.getY());
            if (hit != CHROME_NONE) {
                chromeMode = hit;
                chromeLastX = e.getRawX();
                chromeLastY = e.getRawY();
                return true;
            }
        }

        final long t = e.getEventTime();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                primaryId = e.getPointerId(0);
                engine.onDown(t, e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                feedPointerChange(t, e, -1);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                feedPointerChange(t, e, e.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE:
                resolvePrimary(e, -1);
                engine.onMove(t, e.getPointerCount(),
                        centroidX(e, -1), centroidY(e, -1), primaryX(e), primaryY(e));
                return true;
            case MotionEvent.ACTION_UP:
                engine.onUp(t);
                return true;
            case MotionEvent.ACTION_CANCEL:
                engine.onCancel();
                return true;
            default:
                return true;
        }
    }

    private boolean handleChrome(MotionEvent e, int action) {
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                float rx = e.getRawX();
                float ry = e.getRawY();
                if (host != null) host.onPadDrag(chromeMode, rx - chromeLastX, ry - chromeLastY);
                chromeLastX = rx;
                chromeLastY = ry;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int mode = chromeMode;
                chromeMode = CHROME_NONE;
                if (host != null) host.onPadDragEnd(mode);
                return true;
            }
            default:
                return true;
        }
    }

    private int hitChrome(float x, float y) {
        float w = getWidth();
        float h = getHeight();
        float grip = 48f * density;
        if (x >= w - grip && y >= h - grip) return CHROME_RESIZE;
        if (y <= 32f * density) return CHROME_MOVE;
        return CHROME_NONE;
    }

    private void feedPointerChange(long t, MotionEvent e, int skipIndex) {
        int count = e.getPointerCount() - (skipIndex >= 0 ? 1 : 0);
        if (count <= 0) return;
        resolvePrimary(e, skipIndex);
        engine.onPointers(t, count,
                centroidX(e, skipIndex), centroidY(e, skipIndex), primaryX(e), primaryY(e));
    }

    /** Keeps following the original finger; if it lifted, adopts the first remaining one. */
    private void resolvePrimary(MotionEvent e, int skipIndex) {
        int idx = e.findPointerIndex(primaryId);
        if (idx >= 0 && idx != skipIndex) return;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i != skipIndex) {
                primaryId = e.getPointerId(i);
                return;
            }
        }
    }

    private float primaryX(MotionEvent e) {
        int idx = e.findPointerIndex(primaryId);
        return e.getX(idx < 0 ? 0 : idx);
    }

    private float primaryY(MotionEvent e) {
        int idx = e.findPointerIndex(primaryId);
        return e.getY(idx < 0 ? 0 : idx);
    }

    private static float centroidX(MotionEvent e, int skipIndex) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skipIndex) continue;
            sum += e.getX(i);
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }

    private static float centroidY(MotionEvent e, int skipIndex) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skipIndex) continue;
            sum += e.getY(i);
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }
}
