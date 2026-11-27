package com.gentv.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Opens the Control Center from Home and draws its multi-layer Apple translucent smoked glass
 * surface with overhead sheen and directional rim. The wallpaper row's picture is
 * {@link WallpaperThumbnail}'s cached thumbnail.
 */
final class QuickSettingsGlass {
    private static java.lang.ref.WeakReference<MainActivity> home = new java.lang.ref.WeakReference<>(null);

    private QuickSettingsGlass() {}

    static void trimHomeMemory() {
        MainActivity source = home.get();
        if (source != null && !source.isDestroyed()) source.releaseMemoryForCleanup();
    }

    static void open(Activity activity) {
        if (activity instanceof MainActivity) home = new java.lang.ref.WeakReference<>((MainActivity) activity);
        activity.startActivity(new Intent(activity, QuickSettingsActivity.class));
    }

    /**
     * Multi-layer smoked Apple glass surface: ~75% opaque dark tint directly over the live
     * window, subtle top overhead sheen, and a directional specular rim brightest along the top edge.
     */
    static Drawable surface(float radiusPx, float strokePx, boolean translucent) {
        return new GlassSurfaceDrawable(radiusPx, strokePx,
                translucent ? 0xBA10141C : 0xD9141820,
                translucent ? 0xC40A0D14 : 0xE60D1017);
    }

    /** Denser smoked glass surface for centered foreground modal sheets. */
    static Drawable dialogSurface(float radiusPx, float strokePx) {
        return new GlassSurfaceDrawable(radiusPx, strokePx, 0xF0161A23, 0xF70E1118);
    }

    private static final class GlassSurfaceDrawable extends Drawable {
        private final float radius;
        private final float strokeWidth;
        private final int topColor;
        private final int bottomColor;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boundsF = new RectF();
        private final RectF rimRect = new RectF();

        GlassSurfaceDrawable(float radius, float strokeWidth, int topColor, int bottomColor) {
            this.radius = radius;
            this.strokeWidth = strokeWidth;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(strokeWidth);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            float h = Math.max(1f, bounds.height());
            boundsF.set(bounds);
            float half = strokeWidth * 0.5f;
            rimRect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half);
            fill.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                    topColor, bottomColor, Shader.TileMode.CLAMP));
            sheen.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.top + h * 0.30f,
                    0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                    new int[]{0x6BFFFFFF, 0x24FFFFFF, 0x30FFFFFF},
                    new float[]{0f, 0.42f, 1f},
                    Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(boundsF, radius, radius, fill);
            canvas.save();
            canvas.clipRect(boundsF.left, boundsF.top, boundsF.right, boundsF.top + boundsF.height() * 0.32f);
            canvas.drawRoundRect(boundsF, radius, radius, sheen);
            canvas.restore();
            float rimRadius = Math.max(0f, radius - strokeWidth * 0.5f);
            canvas.drawRoundRect(rimRect, rimRadius, rimRadius, rim);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            sheen.setAlpha(alpha);
            rim.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            sheen.setColorFilter(colorFilter);
            rim.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
