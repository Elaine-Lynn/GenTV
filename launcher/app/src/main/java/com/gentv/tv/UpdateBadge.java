package com.gentv.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The software update sheet's status mark: a glass disc with a glyph. While checking it carries a
 * turning arc; while downloading the arc becomes the download's progress.
 */
final class UpdateBadge extends FrameLayout {
    private final ImageView glyph;
    private final GradientDrawable disc = new GradientDrawable();
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final float stroke;
    private ValueAnimator spin;
    private float angle;
    /** Below 0: indeterminate; 0 to 1: download progress; NaN: no ring. */
    private float progress = Float.NaN;

    UpdateBadge(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        stroke = 3 * density;
        setWillNotDraw(false);
        disc.setShape(GradientDrawable.OVAL);
        glyph = new ImageView(context);
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(glyph, new LayoutParams(Math.round(30 * density), Math.round(30 * density), Gravity.CENTER));
        for (Paint paint : new Paint[]{track, arc}) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x1FFFFFFF);
    }

    /** A settled state: glyph and accent, no ring. */
    void show(int glyphRes, int accent) {
        progress = Float.NaN;
        stopSpin();
        paint(glyphRes, accent);
        invalidate();
    }

    /** Checking or verifying: the arc turns. */
    void busy(int glyphRes, int accent) {
        paint(glyphRes, accent);
        progress = -1;
        startSpin();
        invalidate();
    }

    /** Downloading: the arc shows how much has arrived. */
    void progress(int glyphRes, int accent, float fraction) {
        paint(glyphRes, accent);
        progress = Math.max(0f, Math.min(1f, fraction));
        stopSpin();
        invalidate();
    }

    private void paint(int glyphRes, int accent) {
        glyph.setImageResource(glyphRes);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(accent));
        // A disc tinted by the state's accent, lifted from the sheet by a fine rim.
        disc.setColor((accent & 0x00FFFFFF) | 0x24000000);
        disc.setStroke(Math.max(1, Math.round(stroke / 3)), (accent & 0x00FFFFFF) | 0x33000000);
        // The disc sits inside the ring with a little air between them.
        setBackground(new InsetDrawable(disc, Math.round(stroke * 2.4f)));
        arc.setColor(accent);
    }

    private void startSpin() {
        // A progress indicator, not decoration: it turns even when Home's motion effects are reduced.
        if (spin != null) return;
        spin = ValueAnimator.ofFloat(0f, 360f);
        spin.setDuration(1100);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue();
            invalidate();
        });
        spin.start();
    }

    private void stopSpin() {
        if (spin != null) spin.cancel();
        spin = null;
    }

    @Override protected void onDetachedFromWindow() {
        stopSpin();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (Float.isNaN(progress)) return;
        float half = stroke / 2;
        ring.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawOval(ring, track);
        if (progress < 0) canvas.drawArc(ring, angle - 90, 96, false, arc);
        else canvas.drawArc(ring, -90, 360 * progress, false, arc);
    }
}
