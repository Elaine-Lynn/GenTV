package com.gentv.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Reference-proportioned lower-left clock and place caption; shown once per saver entry. */
final class ScreensaverInfo extends View implements AerialPlayer.Listener {
    static final long SHOW_MS = 8000;
    private final TextPaint clockPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final boolean chinese;
    private AerialCaptions captions;
    private String clock = "", caption = "";
    private boolean shownOnce;
    private StaticLayout captionLayout;
    private final Runnable hide = this::hideInfo;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock = android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()).toString();
            updateDescription(); invalidate();
            postDelayed(this, 1000);
        }
    };

    ScreensaverInfo(Context context) {
        super(context);
        chinese = "zh".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        clockPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        captionPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockPaint.setColor(Color.WHITE); captionPaint.setColor(Color.WHITE);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setVisibility(INVISIBLE);
    }

    @Override public void onFrame(AerialCaptions captions, int positionMs) {
        this.captions = captions;
        if (shownOnce) { onPosition(positionMs); return; }
        shownOnce = true;
        setVisibility(VISIBLE);
        onPosition(positionMs);
        tick.run();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            setAlpha(0f); animate().alpha(1f).setDuration(600).start();
        }
        postDelayed(hide, SHOW_MS);
    }

    @Override public void onPosition(int positionMs) {
        if (captions == null || getVisibility() != VISIBLE) return;
        String next = captions.at(positionMs, chinese);
        if (!next.equals(caption)) {
            caption = next; captionLayout = null;
            updateDescription(); invalidate();
        }
    }

    private void updateDescription() { setContentDescription(clock + "\n" + caption); }

    private void hideInfo() {
        removeCallbacks(tick);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            animate().alpha(0f).setDuration(600).withEndAction(() -> {
                setVisibility(INVISIBLE); setContentDescription(null);
            }).start();
        } else { setVisibility(INVISIBLE); setContentDescription(null); }
    }

    void stop() { removeCallbacks(hide); removeCallbacks(tick); animate().cancel(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clockPaint.setTextSize(h * .125f);
        captionPaint.setTextSize(h * .032f);
        float shadow = h / 1080f;
        clockPaint.setShadowLayer(4 * shadow, 0, shadow, 0x80000000);
        captionPaint.setShadowLayer(3 * shadow, 0, shadow, 0x99000000);
        captionLayout = null;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = getWidth() * .055f;
        canvas.drawText(clock, x, getHeight() * .835f, clockPaint);
        if (captionLayout == null) captionLayout = StaticLayout.Builder.obtain(caption, 0, caption.length(),
                captionPaint, Math.max(1, Math.round(getWidth() * .89f)))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save();
        canvas.translate(x, getHeight() * .91f + captionPaint.ascent());
        captionLayout.draw(canvas);
        canvas.restore();
    }
}
