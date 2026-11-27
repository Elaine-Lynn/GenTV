package com.gentv.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

/** Small vector marks drawn on online wallpaper cards; no bitmaps, crisp at any density. */
final class WallpaperCardMarks {
    private WallpaperCardMarks() { }

    /**
     * Whether a motion wallpaper is on the TV: a solid white disc with a dark check once downloaded, a smoked
     * glass disc with a download arrow before. Sized by its layout (about 24dp).
     */
    static final class DownloadState extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph = new Path();
        private final boolean downloaded;
        private final float d;

        DownloadState(Context context, boolean downloaded) {
            super(context);
            this.downloaded = downloaded;
            d = context.getResources().getDisplayMetrics().density;
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight()), r = s / 2f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            glyph.reset();
            if (downloaded) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(0xF2FFFFFF);
                canvas.drawCircle(cx, cy, r, fill);
                glyph.moveTo(cx - r * .40f, cy + r * .02f);
                glyph.lineTo(cx - r * .10f, cy + r * .32f);
                glyph.lineTo(cx + r * .42f, cy - r * .28f);
                ink.setStrokeWidth(1.9f * d);
                ink.setColor(0xFF0B101A);
            } else {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(0xA60B101A);
                canvas.drawCircle(cx, cy, r, fill);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(Math.max(1f, .8f * d));
                fill.setColor(0x33FFFFFF);
                canvas.drawCircle(cx, cy, r - fill.getStrokeWidth() / 2f, fill);
                // A down arrow landing on a short tray line.
                glyph.moveTo(cx, cy - r * .42f);
                glyph.lineTo(cx, cy + r * .14f);
                glyph.moveTo(cx - r * .26f, cy - r * .10f);
                glyph.lineTo(cx, cy + r * .16f);
                glyph.lineTo(cx + r * .26f, cy - r * .10f);
                glyph.moveTo(cx - r * .34f, cy + r * .42f);
                glyph.lineTo(cx + r * .34f, cy + r * .42f);
                ink.setStrokeWidth(1.6f * d);
                ink.setColor(0xF5FFFFFF);
            }
            canvas.drawPath(glyph, ink);
        }
    }

    /**
     * The download veil laid over a card: the cover dims, a thin ring fills with the real progress (or sweeps
     * while the size is unknown) around the percentage, and on success the ring closes into a white disc that
     * draws its check before {@link #complete} hands control back.
     */
    static final class DownloadProgress extends View {
        private static final Interpolator SETTLE = new DecelerateInterpolator(2f);
        private static final Interpolator POP = new OvershootInterpolator(2.2f);

        private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint percent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Path checkPath = new Path();
        private final Path checkPart = new Path();
        private final float d;
        private final boolean motion;
        private final String label;
        private final long born = SystemClock.uptimeMillis();
        private float target = -1f;      // -1 while the size is unknown
        private float shown;
        private long doneAt;
        private Runnable onDone;

        DownloadProgress(Context context, String label, Typeface medium, Typeface regular, boolean motion) {
            super(context);
            this.label = label;
            this.motion = motion;
            d = context.getResources().getDisplayMetrics().density;
            veil.setColor(0xB3060910);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(2.6f * d);
            track.setColor(0x2EFFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(2.6f * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(0xFFFFFFFF);
            disc.setColor(0xFFFFFFFF);
            check.setStyle(Paint.Style.STROKE);
            check.setStrokeWidth(2.4f * d);
            check.setStrokeCap(Paint.Cap.ROUND);
            check.setStrokeJoin(Paint.Join.ROUND);
            check.setColor(0xFF0B101A);
            percent.setColor(0xF5FFFFFF);
            percent.setTextSize(12.5f * d);
            percent.setTypeface(medium);
            percent.setTextAlign(Paint.Align.CENTER);
            caption.setColor(0xB8DCE6F2);
            caption.setTextSize(11.5f * d);
            caption.setTypeface(regular);
            caption.setTextAlign(Paint.Align.CENTER);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Fraction 0..1 once the size is known; ignored after completion. */
        void setProgress(float fraction) {
            if (doneAt != 0) return;
            target = Math.max(target, Math.min(1f, fraction));
            postInvalidateOnAnimation();
        }

        /** Closes the ring, draws the check and then runs {@code then} (about half a second with motion). */
        void complete(Runnable then) {
            if (doneAt != 0) return;
            target = 1f;
            doneAt = SystemClock.uptimeMillis();
            onDone = then;
            if (!motion) { shown = 1f; postDelayed(this::finish, 260); }
            invalidate();
        }

        private void finish() {
            Runnable r = onDone;
            onDone = null;
            if (r != null) r.run();
        }

        @Override protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f - 6 * d;
            float fadeIn = motion ? SETTLE.getInterpolation(Math.min(1f, (now - born) / 220f)) : 1f;

            veil.setAlpha(Math.round(0xB3 * fadeIn));
            canvas.drawRect(0, 0, w, h, veil);

            float r = 21 * d;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            boolean animating = false;

            if (doneAt == 0 || shown < .999f) {
                if (target >= 0f) {
                    // Ease the drawn arc toward the real figure so chunky network reads still glide.
                    shown += motion ? (target - shown) * .22f : target - shown;
                    if (Math.abs(target - shown) > .002f) animating = true; else shown = target;
                }
                track.setAlpha(Math.round(0x2E * fadeIn));
                canvas.drawCircle(cx, cy, r, track);
                arc.setAlpha(Math.round(255 * fadeIn));
                if (target < 0f) {
                    float spin = ((now - born) % 1100) / 1100f * 360f;
                    float breathe = 70f + 40f * (float) Math.sin((now - born) / 420.0);
                    canvas.drawArc(oval, spin - 90f, breathe, false, arc);
                    animating = true;
                } else {
                    canvas.drawArc(oval, -90f, Math.max(4f, 360f * shown), false, arc);
                    percent.setAlpha(Math.round(245 * fadeIn));
                    canvas.drawText(Math.round(shown * 100f) + "%", cx,
                            cy - (percent.descent() + percent.ascent()) / 2f, percent);
                }
                caption.setAlpha(Math.round(0xB8 * fadeIn));
                canvas.drawText(label, cx, cy + r + 22 * d, caption);
                if (doneAt != 0) animating = true;
                if (doneAt != 0 && shown >= .999f) doneAt = now;   // the success beat starts when the ring closes
            } else {
                float t = Math.min(1f, (now - doneAt) / 420f);
                float pop = POP.getInterpolation(Math.min(1f, t / .55f));
                float radius = r * (.86f + .14f * pop);
                disc.setAlpha(Math.round(255 * Math.min(1f, t / .25f)));
                canvas.drawCircle(cx, cy, radius, disc);
                float stroke = SETTLE.getInterpolation(Math.max(0f, Math.min(1f, (t - .2f) / .55f)));
                if (stroke > 0f) {
                    checkPath.reset();
                    checkPath.moveTo(cx - r * .36f, cy + r * .02f);
                    checkPath.lineTo(cx - r * .10f, cy + r * .28f);
                    checkPath.lineTo(cx + r * .38f, cy - r * .24f);
                    PathMeasure measure = new PathMeasure(checkPath, false);
                    float total = 0f;
                    do total += measure.getLength(); while (measure.nextContour());
                    measure.setPath(checkPath, false);
                    checkPart.reset();
                    measure.getSegment(0f, total * stroke, checkPart, true);
                    canvas.drawPath(checkPart, check);
                }
                caption.setAlpha(Math.round(0xB8 * (1f - Math.min(1f, t / .4f))));
                canvas.drawText(label, cx, cy + r + 22 * d, caption);
                if (t < 1f) animating = true;
                else if (onDone != null) post(this::finish);
            }
            if (animating) postInvalidateOnAnimation();
        }
    }
}
