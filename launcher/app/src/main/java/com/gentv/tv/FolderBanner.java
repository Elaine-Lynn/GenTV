package com.gentv.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.List;

/**
 * A folder's Home tile, drawn at 16:9: up to four of its apps in a 2 × 2 well on the left, its name and app
 * count on the right, on a deep slate glass. A folder reads as a folder, and as which one, at a glance.
 */
final class FolderBanner extends Drawable {
    private static final int WIDTH = 320, HEIGHT = 180;
    private final String name;
    private final String count;
    private final List<Drawable> icons;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint caption = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    FolderBanner(String name, String count, List<Drawable> icons, Typeface typeface) {
        this.name = name;
        this.count = count;
        this.icons = icons;
        fill.setShader(new LinearGradient(0, 0, WIDTH, HEIGHT, 0xFF3A4762, 0xFF1B2231, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, 0, 0, HEIGHT * .55f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cell.setColor(0x1FFFFFFF);
        title.setColor(0xFFF5F7FA);
        title.setTypeface(typeface);
        title.setTextSize(30);
        caption.setColor(0x99FFFFFF);
        caption.setTypeface(typeface);
        caption.setTextSize(17);
    }

    @Override public int getIntrinsicWidth() { return WIDTH; }

    @Override public int getIntrinsicHeight() { return HEIGHT; }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / (float) WIDTH, bounds.height() / (float) HEIGHT);
        canvas.drawRect(0, 0, WIDTH, HEIGHT, fill);
        canvas.drawRect(0, 0, WIDTH, HEIGHT * .55f, sheen);

        // The 2 × 2 well of app icons, vertically centred on the left.
        float size = 54, gap = 8, left = 26, top = (HEIGHT - size * 2 - gap) / 2;
        for (int index = 0; index < 4; index++) {
            float x = left + (index % 2) * (size + gap), y = top + (index / 2) * (size + gap);
            box.set(x, y, x + size, y + size);
            canvas.drawRoundRect(box, 13, 13, cell);
            if (index < icons.size() && icons.get(index) != null) {
                Drawable icon = icons.get(index);
                int inset = 6;
                icon.setBounds(Math.round(x) + inset, Math.round(y) + inset,
                        Math.round(x + size) - inset, Math.round(y + size) - inset);
                icon.draw(canvas);
            }
        }

        float textLeft = left + size * 2 + gap + 22, width = WIDTH - textLeft - 20;
        CharSequence fitted = TextUtils.ellipsize(name, title, width, TextUtils.TruncateAt.END);
        Paint.FontMetrics metrics = title.getFontMetrics();
        float titleHeight = metrics.descent - metrics.ascent;
        float block = titleHeight + 10 + caption.getTextSize();
        float baseline = (HEIGHT - block) / 2 - metrics.ascent;
        canvas.drawText(fitted, 0, fitted.length(), textLeft, baseline, title);
        canvas.drawText(count, textLeft, baseline + metrics.descent + 10 + caption.getTextSize(), caption);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        title.setAlpha(alpha);
        caption.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
