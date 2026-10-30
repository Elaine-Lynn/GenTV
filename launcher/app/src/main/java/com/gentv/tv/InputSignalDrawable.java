package com.gentv.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** The Settings input glyph with a connection badge; animated only while connected Home is visible. */
final class InputSignalDrawable extends Drawable {
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable input;
    private final int size;
    private final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
    private float phase;
    private int alpha = 255;

    InputSignalDrawable(Context context, float density) {
        input = context.getDrawable(R.drawable.ic_input).mutate();
        size = Math.round(18.2f * density);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a -> { phase = (float)a.getAnimatedValue(); invalidateSelf(); });
    }
    void start() { if (!animator.isStarted()) animator.start(); }
    void stop() { animator.cancel(); phase = 0; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 28f, getBounds().height() / 28f);
        // Reuse the exact vector shown in Settings; reserve the lower right corner for status.
        input.setBounds(0, 0, 28, 28);
        input.draw(canvas);
        badge.setColor(0xFF17212B);
        badge.setAlpha(alpha);
        canvas.drawCircle(23, 23, 3.6f, badge);
        badge.setColor(0xFF56D998);
        badge.setAlpha(Math.round(alpha * (0.8f + 0.2f * (float)Math.sin(phase * Math.PI * 2))));
        canvas.drawCircle(23, 23, 2.5f, badge);
        canvas.restore();
    }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int value) { alpha = value; input.setAlpha(value); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        // Header tint applies to the shared glyph; the green badge retains its connection meaning.
        input.setColorFilter(filter); invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
