package net.kdt.pojavlaunch.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.annotation.Nullable;

/** Builds a crisp, front-facing full-body sprite from a standard Minecraft skin atlas. */
public final class MinecraftSkinPreviewRenderer {
    private static final int PIXEL_SCALE = 4;
    private static final int BODY_WIDTH = 16;
    private static final int BODY_HEIGHT = 32;

    private MinecraftSkinPreviewRenderer() {
    }

    /**
     * Compose the front faces of the head, body, arms, and legs. The outer skin layers are drawn
     * over the base body so hats, jackets, and sleeves remain visible in the home preview.
     *
     * @param skin a classic 64x64 skin or legacy 64x32 skin
     * @param slim true for Alex/slim arms when a modern skin is supplied
     * @return a transparent 64x128 bitmap, or {@code null} for an unsupported texture
     */
    @Nullable
    public static Bitmap renderFront(@Nullable Bitmap skin, boolean slim) {
        if (skin == null || skin.isRecycled() || skin.getWidth() < 64 || skin.getHeight() < 32) {
            return null;
        }

        boolean modern = skin.getHeight() >= 64;
        int armWidth = modern && slim ? 3 : 4;
        Bitmap preview = Bitmap.createBitmap(BODY_WIDTH * PIXEL_SCALE,
                BODY_HEIGHT * PIXEL_SCALE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(preview);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);

        // Legs sit behind the torso and arms.
        drawPart(canvas, skin, paint, modern ? 20 : 4, modern ? 52 : 20,
                4, 12, 8, 20, 4, 12, !modern);
        drawPart(canvas, skin, paint, 4, 20, 4, 12, 4, 20, 4, 12, false);

        // Arms: keep the pixels nearest the centerline to align slim textures naturally.
        int legacyArmX = 44 + Math.max(0, 4 - armWidth);
        drawPart(canvas, skin, paint, modern ? 36 : legacyArmX, modern ? 52 : 20,
                armWidth, 12, 12 + (4 - armWidth) / 2, 8, armWidth, 12, !modern);
        drawPart(canvas, skin, paint, 44, 20, armWidth, 12,
                (4 - armWidth) / 2, 8, armWidth, 12, false);

        // Torso and head base.
        drawPart(canvas, skin, paint, 20, 20, 8, 12, 4, 8, 8, 12, false);
        drawPart(canvas, skin, paint, 8, 8, 8, 8, 4, 0, 8, 8, false);

        // Modern second layers: trousers, sleeves, jacket and hat/helmet.
        if (modern) {
            drawPart(canvas, skin, paint, 4, 52, 4, 12, 8, 20, 4, 12, false);
            drawPart(canvas, skin, paint, 4, 36, 4, 12, 4, 20, 4, 12, false);

            int leftSleeveX = 52;
            int rightSleeveX = 44;
            drawPart(canvas, skin, paint, leftSleeveX, 52, armWidth, 12,
                    12 + (4 - armWidth) / 2, 8, armWidth, 12, false);
            drawPart(canvas, skin, paint, rightSleeveX, 36, armWidth, 12,
                    (4 - armWidth) / 2, 8, armWidth, 12, false);
            drawPart(canvas, skin, paint, 20, 36, 8, 12, 4, 8, 8, 12, false);
        }

        // The hat layer is present in both modern and legacy skins.
        if (skin.getWidth() >= 48 && skin.getHeight() >= 16) {
            drawPart(canvas, skin, paint, 40, 8, 8, 8, 4, 0, 8, 8, false);
        }
        return preview;
    }

    private static void drawPart(Canvas canvas, Bitmap skin, Paint paint,
                                 int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                 int targetX, int targetY, int targetWidth, int targetHeight,
                                 boolean mirror) {
        if (sourceX < 0 || sourceY < 0 || sourceX + sourceWidth > skin.getWidth() ||
                sourceY + sourceHeight > skin.getHeight()) return;
        Rect source = new Rect(sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight);
        int left = targetX * PIXEL_SCALE;
        int top = targetY * PIXEL_SCALE;
        Rect destination = new Rect(left, top,
                left + targetWidth * PIXEL_SCALE, top + targetHeight * PIXEL_SCALE);
        if (mirror) {
            canvas.save();
            canvas.scale(-1f, 1f, destination.exactCenterX(), destination.exactCenterY());
        }
        canvas.drawBitmap(skin, source, destination, paint);
        if (mirror) canvas.restore();
    }
}
