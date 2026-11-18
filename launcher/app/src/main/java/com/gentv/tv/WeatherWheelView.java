package com.gentv.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/** One column of the province, city and district wheel; only the middle value is selected. */
public final class WeatherWheelView extends View {
    private static final float[] OPACITY = {1f, .72f, .35f, .12f, 0f};
    private static final float[] TEXT_SIZE = {31f, 27f, 23f, 20f, 18f};
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> items = new ArrayList<>();
    private float density;
    private int selected;
    private float animatedOffset;
    private ValueAnimator scrollAnimator;
    private String title = "";
    private Runnable onSelectionChanged;

    public WeatherWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        focusFill.setColor(0xF2FFFFFF);
        setFocusable(true);
        setClickable(true);
        setWillNotDraw(false);
    }

    void setTitle(String value) {
        title = value;
        describe();
    }

    void setItems(List<String> names, int index) {
        if (scrollAnimator != null) scrollAnimator.cancel();
        animatedOffset = 0f;
        items.clear();
        items.addAll(names);
        selected = Math.max(0, Math.min(index, Math.max(0, items.size() - 1)));
        describe();
        invalidate();
    }

    int selectedIndex() { return selected; }

    void setOnSelectionChanged(Runnable callback) { onSelectionChanged = callback; }

    private void describe() {
        setContentDescription(title + (items.isEmpty() ? "" : "：" + items.get(selected)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty()) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (hasFocus()) {
            canvas.drawRoundRect(new android.graphics.RectF(0, cy - 37 * density,
                    getWidth(), cy + 37 * density),
                    22 * density, 22 * density, focusFill);
        }
        float step = 62 * density;
        for (int offset = -4; offset <= 4; offset++) {
            int index = selected + offset;
            if (index < 0 || index >= items.size()) continue;
            float position = offset + animatedOffset;
            if (Math.abs(position) > 2.2f) continue;
            float distance = Math.min(4f, Math.abs(position));
            int near = Math.min(3, (int) distance);
            float blend = distance - near;
            text.setColor(hasFocus() && Math.abs(position) < .6f ? Color.BLACK : Color.WHITE);
            float alpha = OPACITY[near] + (OPACITY[near + 1] - OPACITY[near]) * blend;
            text.setAlpha(Math.round(255 * alpha * (distance < .5f && !hasFocus() ? .82f : 1f)));
            text.setTextSize((TEXT_SIZE[near] + (TEXT_SIZE[near + 1] - TEXT_SIZE[near]) * blend) * density);
            text.setTypeface(distance < .5f ? BOLD : NORMAL);
            String label = fit(items.get(index), getWidth() - 24 * density);
            Paint.FontMetrics metrics = text.getFontMetrics();
            float baseline = cy + position * step - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(label, cx, baseline, text);
        }
    }

    private String fit(String label, float width) {
        if (text.measureText(label) <= width) return label;
        String shortened = label;
        while (!shortened.isEmpty() && text.measureText(shortened + "…") > width)
            shortened = shortened.substring(0, shortened.length() - 1);
        return shortened + "…";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = selected + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            if (next >= 0 && next < items.size()) {
                int direction = next - selected;
                selected = next;
                describe();
                scrollByOne(direction);
                playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP
                        ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
                if (onSelectionChanged != null) onSelectionChanged.run();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void scrollByOne(int direction) {
        float from = Math.max(-2.5f, Math.min(2.5f, animatedOffset + direction));
        if (scrollAnimator != null) scrollAnimator.cancel();
        if (!CompatApi.animatorsEnabled()) {
            animatedOffset = 0f;
            invalidate();
            return;
        }
        scrollAnimator = ValueAnimator.ofFloat(from, 0f);
        scrollAnimator.setDuration(220);
        scrollAnimator.setInterpolator(new DecelerateInterpolator(1.2f));
        scrollAnimator.addUpdateListener(value -> {
            animatedOffset = (float) value.getAnimatedValue();
            invalidate();
        });
        scrollAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (scrollAnimator != null) scrollAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        invalidate();
    }
}
