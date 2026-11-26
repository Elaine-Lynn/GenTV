package com.gentv.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * Resolution-independent procedural canvas that renders pure main-content home-screen previews
 * for each extension (no repetitive dock chrome), tailored for Apple TV widescreen gallery cards.
 */
@SuppressLint("ViewConstructor")
final class ExtensionPreviewView extends View {
    enum Style {
        WEATHER, CONTINUE, DAILY, CALENDAR, CLOCK, SMART, LOCAL
    }

    private final Style style;
    private final String customLabel;
    private final boolean zh;
    private final Typeface fontLight;
    private final Typeface fontMedium;
    private final Typeface fontRegular;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF subRect = new RectF();
    private final Path path = new Path();

    ExtensionPreviewView(Context context, Style style, String customLabel) {
        super(context);
        this.style = style;
        this.customLabel = customLabel == null ? "" : customLabel;
        Locale locale = CompatApi.locale(context.getResources().getConfiguration());
        this.zh = locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
        this.fontLight = UiFont.get(context, R.string.font_light);
        this.fontMedium = UiFont.get(context, R.string.font_medium);
        this.fontRegular = UiFont.get(context, R.string.font_regular);
        stroke.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 1f || h <= 1f) return;
        float u = Math.min(w / 320f, h / 168f);

        drawBackdrop(canvas, w, h);

        switch (style) {
            case WEATHER:
                drawWeatherContent(canvas, w, h, u);
                break;
            case CONTINUE:
                drawContinueContent(canvas, w, h, u);
                break;
            case DAILY:
                drawDailyFrameContent(canvas, w, h, u);
                break;
            case CALENDAR:
                drawCalendarContent(canvas, w, h, u);
                break;
            case CLOCK:
                drawClockContent(canvas, w, h, u);
                break;
            case SMART:
                drawSmartHomeContent(canvas, w, h, u);
                break;
            case LOCAL:
                drawLocalExtensionContent(canvas, w, h, u);
                break;
        }
    }

    private void drawBackdrop(Canvas canvas, float w, float h) {
        fill.setShader(null);
        switch (style) {
            case WEATHER:
                fill.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{0xFF0A1D3D, 0xFF154482, 0xFF1E67B0},
                        new float[]{0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.78f, h * 0.22f, w * 0.65f,
                        0x665AC8FA, 0x00103060, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                path.reset();
                path.moveTo(0, h * 0.76f);
                path.cubicTo(w * 0.28f, h * 0.44f, w * 0.62f, h * 0.88f, w, h * 0.34f);
                path.lineTo(w, h);
                path.lineTo(0, h);
                path.close();
                fill.setShader(new LinearGradient(0, h * 0.34f, w, h, 0x4438A1FF, 0x2208162D, Shader.TileMode.CLAMP));
                canvas.drawPath(path, fill);
                break;

            case CONTINUE:
                fill.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{0xFF0B0E1A, 0xFF161E36, 0xFF0E1322},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.82f, h * 0.14f, w * 0.64f,
                        0x4D6E7BFF, 0x000A0D18, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.20f, h * 0.76f, w * 0.54f,
                        0x33FF9F5A, 0x000A0D18, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                break;

            case DAILY:
                fill.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{0xFF13253E, 0xFF2B4D70, 0xFFC28668, 0xFF152938},
                        new float[]{0f, 0.46f, 0.68f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                path.reset();
                path.moveTo(0, h * 0.74f);
                path.lineTo(w * 0.18f, h * 0.54f);
                path.lineTo(w * 0.36f, h * 0.66f);
                path.lineTo(w * 0.62f, h * 0.44f);
                path.lineTo(w * 0.82f, h * 0.60f);
                path.lineTo(w, h * 0.48f);
                path.lineTo(w, h);
                path.lineTo(0, h);
                path.close();
                fill.setShader(new LinearGradient(0, h * 0.44f, 0, h, 0xAA102134, 0xEE091420, Shader.TileMode.CLAMP));
                canvas.drawPath(path, fill);
                break;

            case CALENDAR:
                fill.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{0xFF12162A, 0xFF212645, 0xFF171B32},
                        new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.18f, h * 0.20f, w * 0.58f,
                        0x45FF6B8B, 0x0012162A, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.84f, h * 0.78f, w * 0.52f,
                        0x3A5E9CFF, 0x0012162A, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                break;

            case CLOCK:
                fill.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{0xFF0A0B10, 0xFF161317, 0xFF281B16},
                        new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.50f, h * 0.96f, w * 0.62f,
                        0x6BFF9E53, 0x00100D0C, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                break;

            case SMART:
                fill.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{0xFF0C1E23, 0xFF16363D, 0xFF0F232B},
                        new float[]{0f, 0.54f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.28f, h * 0.22f, w * 0.56f,
                        0x4464D2A0, 0x000C1D22, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                break;

            case LOCAL:
                fill.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{0xFF0E192D, 0xFF1C3357, 0xFF13233C},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                fill.setShader(new RadialGradient(w * 0.74f, h * 0.24f, w * 0.55f,
                        0x4475B8FF, 0x000E182B, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, fill);
                break;
        }
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x1A000000, 0x38000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    private void drawGlassCard(Canvas canvas, RectF box, float radius, int fillColor, int borderColor, float u) {
        fill.setShader(null);
        fill.setColor(fillColor);
        canvas.drawRoundRect(box, radius, radius, fill);
        stroke.setColor(borderColor);
        stroke.setStrokeWidth(Math.max(1f, 0.9f * u));
        canvas.drawRoundRect(box, radius, radius, stroke);
    }

    private void drawWeatherContent(Canvas canvas, float w, float h, float u) {
        float cardW = Math.min(w * 0.72f, 224f * u);
        float cardH = 104f * u;
        float left = (w - cardW) * 0.5f;
        float top = (h - cardH) * 0.5f;
        rect.set(left, top, left + cardW, top + cardH);
        drawGlassCard(canvas, rect, 16f * u, 0x3E152847, 0x45FFFFFF, u);

        text.setTypeface(fontMedium);
        text.setTextSize(12f * u);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xD4FFFFFF);
        canvas.drawText(zh ? "杭州 · 多云" : "Hangzhou · Clear", left + 18f * u, top + 25f * u, text);

        text.setTypeface(fontLight);
        text.setTextSize(42f * u);
        text.setColor(0xFFFFFFFF);
        canvas.drawText("18°", left + 16f * u, top + 68f * u, text);

        text.setTypeface(fontRegular);
        text.setTextSize(10f * u);
        text.setColor(0xB8FFFFFF);
        canvas.drawText(zh ? "今晚微风 · 最高 22°  最低 14°" : "Gentle breeze · H 22°  L 14°",
                left + 18f * u, top + 88f * u, text);

        float cx = left + cardW - 44f * u;
        float cy = top + 46f * u;
        fill.setShader(new RadialGradient(cx + 8f * u, cy - 8f * u, 20f * u,
                0xFFFFD66B, 0x00FFD66B, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx + 8f * u, cy - 8f * u, 20f * u, fill);
        fill.setShader(null);
        fill.setColor(0xFFFFCA4F);
        canvas.drawCircle(cx + 8f * u, cy - 8f * u, 10.5f * u, fill);

        fill.setColor(0xF5F4F8FF);
        canvas.drawCircle(cx - 5f * u, cy + 4f * u, 10f * u, fill);
        canvas.drawCircle(cx + 5f * u, cy + 1f * u, 12f * u, fill);
        canvas.drawCircle(cx + 15f * u, cy + 5f * u, 8.5f * u, fill);
        subRect.set(cx - 15f * u, cy + 4f * u, cx + 23.5f * u, cy + 14f * u);
        canvas.drawRoundRect(subRect, 5f * u, 5f * u, fill);
    }

    private void drawContinueContent(Canvas canvas, float w, float h, float u) {
        float shelfW = w * 0.84f;
        float shelfH = 104f * u;
        float left = (w - shelfW) * 0.5f;
        float top = (h - shelfH) * 0.5f;
        rect.set(left, top, left + shelfW, top + shelfH);
        drawGlassCard(canvas, rect, 14f * u, 0x3D0D1322, 0x38FFFFFF, u);

        text.setTypeface(fontMedium);
        text.setTextSize(10.5f * u);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xCCFFFFFF);
        canvas.drawText(zh ? "接着看" : "Continue Watching", left + 13f * u, top + 20f * u, text);

        int count = 3;
        float gap = 9f * u;
        float pad = 13f * u;
        float cardW = (shelfW - pad * 2f - gap * (count - 1)) / count;
        float cardH = shelfH - 36f * u;
        int[][] palettes = {
                {0xFF284B7A, 0xFF101C33},
                {0xFF9C523E, 0xFF2B1822},
                {0xFF265C4B, 0xFF0E211E}
        };
        float[] progress = {0.68f, 0.34f, 0.82f};
        String[] labels = zh
                ? new String[]{"星际漫游", "日落之前", "山海纪"}
                : new String[]{"Odyssey", "Before Dusk", "Highlands"};

        for (int i = 0; i < count; i++) {
            float cx = left + pad + i * (cardW + gap);
            float cy = top + 27f * u;
            subRect.set(cx, cy, cx + cardW, cy + cardH);
            fill.setShader(new LinearGradient(cx, cy, cx + cardW, cy + cardH,
                    palettes[i][0], palettes[i][1], Shader.TileMode.CLAMP));
            canvas.drawRoundRect(subRect, 8f * u, 8f * u, fill);
            fill.setShader(null);
            stroke.setColor(i == 0 ? 0x99FFFFFF : 0x2EFFFFFF);
            stroke.setStrokeWidth(Math.max(0.8f, 0.9f * u));
            canvas.drawRoundRect(subRect, 8f * u, 8f * u, stroke);

            text.setTypeface(fontMedium);
            text.setTextSize(8.8f * u);
            text.setColor(0xF0FFFFFF);
            canvas.drawText(labels[i], cx + 7f * u, cy + cardH - 12f * u, text);

            float barL = cx + 7f * u;
            float barR = cx + cardW - 7f * u;
            float barT = cy + cardH - 7f * u;
            fill.setColor(0x40FFFFFF);
            canvas.drawRoundRect(new RectF(barL, barT, barR, barT + 2.6f * u), 1.3f * u, 1.3f * u, fill);
            fill.setColor(0xF2FFFFFF);
            canvas.drawRoundRect(new RectF(barL, barT, barL + (barR - barL) * progress[i], barT + 2.6f * u),
                    1.3f * u, 1.3f * u, fill);
        }
    }

    private void drawDailyFrameContent(Canvas canvas, float w, float h, float u) {
        float frameW = w * 0.74f;
        float frameH = 108f * u;
        float left = (w - frameW) * 0.5f;
        float top = (h - frameH) * 0.5f;
        rect.set(left, top, left + frameW, top + frameH);

        fill.setShader(new LinearGradient(left, top, left, top + frameH,
                new int[]{0xFF285582, 0xFF6B8FB2, 0xFFE6A578, 0xFF1D3B54},
                new float[]{0f, 0.45f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 14f * u, 14f * u, fill);
        fill.setShader(null);

        fill.setShader(new RadialGradient(left + frameW * 0.30f, top + frameH * 0.56f, frameW * 0.34f,
                0x99FFE0A3, 0x00FFE0A3, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 14f * u, 14f * u, fill);
        fill.setShader(null);

        canvas.save();
        path.reset();
        path.addRoundRect(rect, 14f * u, 14f * u, Path.Direction.CW);
        canvas.clipPath(path);
        path.reset();
        path.moveTo(left, top + frameH * 0.72f);
        path.lineTo(left + frameW * 0.24f, top + frameH * 0.48f);
        path.lineTo(left + frameW * 0.46f, top + frameH * 0.64f);
        path.lineTo(left + frameW * 0.70f, top + frameH * 0.34f);
        path.lineTo(left + frameW, top + frameH * 0.60f);
        path.lineTo(left + frameW, top + frameH);
        path.lineTo(left, top + frameH);
        path.close();
        fill.setColor(0xCC12263A);
        canvas.drawPath(path, fill);

        fill.setColor(0x7008111A);
        subRect.set(left + 10f * u, top + frameH - 25f * u, left + frameW * 0.58f, top + frameH - 8f * u);
        canvas.drawRoundRect(subRect, 8f * u, 8f * u, fill);
        text.setTypeface(fontMedium);
        text.setTextSize(8.8f * u);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xF0FFFFFF);
        canvas.drawText(zh ? "阿尔卑斯晨光 · 今日摄影" : "Alpine Dawn · Daily Photo",
                left + 16f * u, top + frameH - 13.5f * u, text);
        canvas.restore();

        stroke.setColor(0x66FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 1.1f * u));
        canvas.drawRoundRect(rect, 14f * u, 14f * u, stroke);
    }

    private void drawCalendarContent(Canvas canvas, float w, float h, float u) {
        float cardW = w * 0.78f;
        float cardH = 106f * u;
        float left = (w - cardW) * 0.5f;
        float top = (h - cardH) * 0.5f;
        rect.set(left, top, left + cardW, top + cardH);
        drawGlassCard(canvas, rect, 14f * u, 0x4214192C, 0x3DFFFFFF, u);

        String[] times = {"19:30", zh ? "周六 10:00" : "Sat 10:00", zh ? "周日 15:00" : "Sun 15:00"};
        String[] events = zh
                ? new String[]{"家庭电影夜", "西溪公园野餐", "钢琴音乐课"}
                : new String[]{"Family Movie Night", "Park Picnic", "Piano Lesson"};
        int[] accents = {0xFFFF6B8B, 0xFF5AC8FA, 0xFF58D68D};

        float rowH = (cardH - 20f * u) / 3f;
        for (int i = 0; i < 3; i++) {
            float ry = top + 10f * u + i * rowH;
            fill.setColor(accents[i]);
            canvas.drawRoundRect(new RectF(left + 14f * u, ry + 4f * u, left + 17f * u, ry + rowH - 6f * u),
                    1.5f * u, 1.5f * u, fill);

            text.setTypeface(fontMedium);
            text.setTextSize(10.5f * u);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xF5FFFFFF);
            canvas.drawText(events[i], left + 26f * u, ry + rowH * 0.62f, text);

            text.setTypeface(fontRegular);
            text.setTextSize(9.2f * u);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xA0FFFFFF);
            canvas.drawText(times[i], left + cardW - 15f * u, ry + rowH * 0.62f, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawClockContent(Canvas canvas, float w, float h, float u) {
        text.setTypeface(fontLight);
        text.setTextSize(56f * u);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xF7FFFFFF);
        float centerY = h * 0.56f;
        canvas.drawText("20:24", w * 0.5f, centerY, text);

        text.setTypeface(fontMedium);
        text.setTextSize(10.5f * u);
        text.setColor(0xC4FFCC99);
        canvas.drawText(zh ? "11月24日 · 农历十月十五" : "NOV 24 · AMBIENT STANDBY",
                w * 0.5f, centerY + 21f * u, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSmartHomeContent(Canvas canvas, float w, float h, float u) {
        float totalW = w * 0.84f;
        float tileH = 82f * u;
        float left = (w - totalW) * 0.5f;
        float top = (h - tileH) * 0.5f;
        float gap = 10f * u;
        float tileW = (totalW - gap * 2f) / 3f;

        String[] titles = zh ? new String[]{"客厅灯", "空调", "空气"} : new String[]{"Lights", "Climate", "Air"};
        String[] vals = zh ? new String[]{"60%", "24°", "优 18"} : new String[]{"60%", "24°", "AQI 18"};
        int[] dots = {0xFFFFC95C, 0xFF5AC8FA, 0xFF4CD98B};

        for (int i = 0; i < 3; i++) {
            float tx = left + i * (tileW + gap);
            rect.set(tx, top, tx + tileW, top + tileH);
            drawGlassCard(canvas, rect, 14f * u, 0x4512262C, 0x40FFFFFF, u);

            fill.setColor(dots[i]);
            canvas.drawCircle(tx + 14f * u, top + 17f * u, 4.2f * u, fill);

            text.setTypeface(fontMedium);
            text.setTextSize(18f * u);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFFFFFFFF);
            canvas.drawText(vals[i], tx + 12f * u, top + 50f * u, text);

            text.setTypeface(fontRegular);
            text.setTextSize(9.5f * u);
            text.setColor(0xB0FFFFFF);
            canvas.drawText(titles[i], tx + 12f * u, top + 68f * u, text);
        }
    }

    private void drawLocalExtensionContent(Canvas canvas, float w, float h, float u) {
        float cardW = w * 0.76f;
        float cardH = 94f * u;
        float left = (w - cardW) * 0.5f;
        float top = (h - cardH) * 0.5f;
        rect.set(left, top, left + cardW, top + cardH);
        drawGlassCard(canvas, rect, 15f * u, 0x45172844, 0x48FFFFFF, u);

        text.setTypeface(fontMedium);
        text.setTextSize(9.8f * u);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xB89ED2FF);
        canvas.drawText("GHCP 1", left + 16f * u, top + 24f * u, text);

        text.setTypeface(fontMedium);
        text.setTextSize(15f * u);
        text.setColor(0xFFFFFFFF);
        String name = customLabel.isEmpty() ? (zh ? "本地扩展" : "Local Extension") : customLabel;
        if (name.length() > 18) name = name.substring(0, 17) + "…";
        canvas.drawText(name, left + 16f * u, top + 49f * u, text);

        fill.setColor(0x36FFFFFF);
        canvas.drawRoundRect(new RectF(left + 16f * u, top + 61f * u, left + cardW * 0.74f, top + 67f * u),
                3f * u, 3f * u, fill);
        fill.setColor(0x24FFFFFF);
        canvas.drawRoundRect(new RectF(left + 16f * u, top + 73f * u, left + cardW * 0.52f, top + 79f * u),
                3f * u, 3f * u, fill);
    }
}
