package net.kdt.pojavlaunch.liquid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Liquid Crystal backdrop.
 *
 * <p>Draws the deep-space aurora behind every launcher screen. The whole scene (void gradient,
 * aurora blobs, bokeh embers, voxel horizon haze and vignette) is rendered <b>once</b> into a
 * small bitmap (1:6 scale) and then blitted scaled-up, which both fakes a heavy gaussian blur
 * and costs a single hardware-accelerated bitmap draw per frame.</p>
 *
 * <p>Deliberately avoids {@code RenderEffect}/RealtimeBlurView so it works on every supported
 * API level (21+) and never runs a shader per frame. There is no continuous animation here:
 * the view invalidates only when its size changes, which keeps the launcher cool and stable.</p>
 */
public class AuroraBackdropView extends View {

    /** Downscale factor of the cached scene. Higher = cheaper but softer. */
    private static final int CACHE_SCALE = 6;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint mScenePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mDst = new Rect();

    private Bitmap mCache;
    private int mSeed = 0x5EED1;

    public AuroraBackdropView(Context context) {
        this(context, null);
    }

    public AuroraBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        // The backdrop is opaque: telling the framework avoids blending the whole screen.
        setBackgroundColor(0xFF06070F);
    }

    public AuroraBackdropView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setBackgroundColor(0xFF06070F);
    }

    /** Lets the theme pick a different aurora layout per screen without re-rendering cost. */
    public void setSceneSeed(int seed) {
        if (seed == mSeed) return;
        mSeed = seed;
        rebuildCache(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildCache(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        recycleCache();
    }

    private void recycleCache() {
        if (mCache != null && !mCache.isRecycled()) mCache.recycle();
        mCache = null;
    }

    private void rebuildCache(int w, int h) {
        if (w <= 0 || h <= 0) return;
        recycleCache();
        int cw = Math.max(2, w / CACHE_SCALE);
        int ch = Math.max(2, h / CACHE_SCALE);
        try {
            mCache = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            mCache = null;      // falls back to the flat void colour
            return;
        }
        Canvas c = new Canvas(mCache);
        Random rnd = new Random(mSeed);

        // 1. void gradient -----------------------------------------------------
        mScenePaint.setShader(new LinearGradient(0, 0, 0, ch,
                0xFF0B0D1E, 0xFF06070F, Shader.TileMode.CLAMP));
        mScenePaint.setAlpha(255);
        c.drawRect(0, 0, cw, ch, mScenePaint);

        // 2. aurora blobs ------------------------------------------------------
        int[][] blobs = {
                {0x6635E6FF, 18, 22, 78},   // cyan upper-left
                {0x558B6CFF, 56, 14, 88},   // violet top
                {0x44FF4FD8, 88, 34, 74},   // magenta right
                {0x337CFFCB, 34, 78, 70},   // mint lower-left
                {0x228B6CFF, 74, 88, 62},   // violet lower-right
        };
        for (int[] b : blobs) {
            float cx = cw * b[1] / 100f;
            float cy = ch * b[2] / 100f;
            float r = Math.max(cw, ch) * b[3] / 100f;
            mScenePaint.setShader(new RadialGradient(cx, cy, r,
                    b[0], 0x00000000, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, mScenePaint);
        }

        // 3. bokeh embers ------------------------------------------------------
        mScenePaint.setShader(null);
        for (int i = 0; i < 54; i++) {
            float x = rnd.nextFloat() * cw;
            float y = rnd.nextFloat() * ch;
            float r = 0.5f + rnd.nextFloat() * 1.9f;
            int a = 26 + rnd.nextInt(120);
            int tint = rnd.nextInt(3);
            mScenePaint.setColor(tint == 0 ? (a << 24) | 0x35E6FF
                    : tint == 1 ? (a << 24) | 0xFF4FD8
                    : (a << 24) | 0x7CFFCB);
            c.drawCircle(x, y, r, mScenePaint);
        }

        // 4. voxel horizon haze ------------------------------------------------
        for (int i = 0; i < 14; i++) {
            float x = rnd.nextFloat() * cw;
            float y = ch * (0.80f + rnd.nextFloat() * 0.16f);
            float r = 2f + rnd.nextFloat() * 7f;
            mScenePaint.setColor(0x33202A55);
            c.drawCircle(x, y, r, mScenePaint);
        }
        mScenePaint.setShader(new LinearGradient(0, ch * 0.72f, 0, ch,
                0x0006070F, 0xD906070F, Shader.TileMode.CLAMP));
        c.drawRect(0, ch * 0.72f, cw, ch, mScenePaint);

        // 5. vignette ----------------------------------------------------------
        mScenePaint.setShader(new RadialGradient(cw * 0.5f, ch * 0.5f,
                Math.max(cw, ch) * 0.72f,
                0x0006070F, 0xB306070F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, cw, ch, mScenePaint);

        mScenePaint.setShader(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCache == null || mCache.isRecycled()) return;
        mDst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(mCache, null, mDst, mBitmapPaint);
    }
}
