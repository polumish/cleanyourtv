package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** The theme's glass colour (0xRRGGBB), -1 for plain white; what setAccent(0) goes back to. */
    private int baseRgb = -1;
    /** One light band crossing the slab as focus lands: 0 = not running, otherwise 0..1 across. */
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix sweepMatrix = new android.graphics.Matrix();
    private android.animation.ValueAnimator sweeper;
    private float sweep;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** The theme's glass colour: the glass returns to it whenever no other accent is set; -1 = plain white. */
    void setBaseAccent(int rgb) {
        baseRgb = rgb;
        setAccent(0);
    }

    /** Tints the rim and the outer glow; 0 restores the base glass (white unless the theme sets a colour). */
    void setAccent(int argb) {
        if (argb == 0 && baseRgb != -1) {
            argb = 0xFF000000 | baseRgb;
        }
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    /**
     * One soft band of light crossing the glass left to right in 520 ms, started when focus lands on a tile.
     * Only with a theme colour; the layer is redrawn per frame for that half second, nothing at rest.
     */
    void sheen() {
        if (baseRgb == -1 || getWidth() <= 0 || !isAttachedToWindow()) return;
        if (sweeper != null) sweeper.cancel();
        sweeper = android.animation.ValueAnimator.ofFloat(0f, 1f);
        sweeper.setDuration(520);
        sweeper.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        sweeper.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidate();
        });
        sweeper.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                sweep = 0f;
                invalidate();
            }
        });
        sweeper.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sweeper != null) sweeper.cancel();
        super.onDetachedFromWindow();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        // The band: transparent, bright in the middle, transparent; moved across with a matrix (no new shader per frame).
        float band = Math.max(w * 0.35f, 40f * density);
        sweepPaint.setShader(new LinearGradient(-band / 2f, 0, band / 2f, 0,
                new int[]{0x00FFFFFF, 0x70FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        int rings = 12;
        float step = inset / rings;
        glow.setStrokeWidth(step + 0.5f);
        for (int i = 0; i < rings; i++) {
            float o = step * (i + 0.5f);
            float t = 1f - (float) i / rings;
            glow.setColor(Math.round(glowAlpha * t) << 24 | glowRgb);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        if (sweep > 0f && sweep < 1f && sweepPaint.getShader() != null) {
            float band = Math.max(getWidth() * 0.35f, 40f * density);
            sweepMatrix.setTranslate(rect.left - band / 2f + (rect.width() + band) * sweep, 0);
            sweepPaint.getShader().setLocalMatrix(sweepMatrix);
            canvas.drawRoundRect(rect, radius, radius, sweepPaint);
        }

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
