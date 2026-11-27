package com.gentv.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Provides the pure live wallpaper thumbnail for the Control Center hero tile and draws the
 * multi-layer Apple translucent smoked glass surface with overhead sheen and directional rim.
 */
final class QuickSettingsGlass {
    private static java.lang.ref.WeakReference<MainActivity> home = new java.lang.ref.WeakReference<>(null);
    private static Bitmap pendingPreview;
    private static long capturedAt;

    private QuickSettingsGlass() {}

    static void trimHomeMemory() {
        MainActivity source = home.get();
        if (source != null && !source.isDestroyed()) source.releaseMemoryForCleanup();
    }

    static void open(Activity activity) {
        if (activity instanceof MainActivity) home = new java.lang.ref.WeakReference<>((MainActivity) activity);
        if (pendingPreview != null) {
            pendingPreview.recycle();
            pendingPreview = null;
        }
        pendingPreview = extractWallpaperPreview(activity);
        capturedAt = SystemClock.uptimeMillis();
        activity.startActivity(new Intent(activity, QuickSettingsActivity.class));
    }

    /** Grab a clean downscaled thumbnail directly from Home's wallpaper layer, free of app tiles. */
    private static Bitmap extractWallpaperPreview(Activity activity) {
        View rootView = activity.findViewById(R.id.root);
        if (!(rootView instanceof ViewGroup)) return null;
        ViewGroup root = (ViewGroup) rootView;
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            if (child.getId() == R.id.heroArt || child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof ImageView) {
                Drawable d = ((ImageView) child).getDrawable();
                if (d instanceof BitmapDrawable) {
                    Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                    if (bmp != null && !bmp.isRecycled() && bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                        int targetW = 160;
                        int targetH = Math.max(1, Math.round(targetW * (float) bmp.getHeight() / bmp.getWidth()));
                        return Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
                    }
                }
            }
        }
        return null;
    }

    static Bitmap takePreview() {
        Bitmap preview = pendingPreview;
        pendingPreview = null;
        if (preview == null) return null;
        if (SystemClock.uptimeMillis() - capturedAt > 2000) {
            preview.recycle();
            return null;
        }
        return preview;
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
