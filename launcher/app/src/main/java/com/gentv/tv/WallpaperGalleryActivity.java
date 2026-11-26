package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dark cinema-style Wallpaper & Screensaver gallery aligned with GenTV's settings aesthetic.
 * Displays 3 full 16:9 poster cards + a natural 4th-card right-edge peek with soft dark fade vignette,
 * live shelf position indicators, scoped shuffle choices, and inline screensaver controls at the bottom.
 */
public final class WallpaperGalleryActivity extends Activity {
    private static final String KEY_ACTIVE_CATALOG_ID = "wallpaper_active_catalog_id";
    private static final String KEY_LAST_SCREENSAVER_MINUTES = "screensaver_last_minutes";
    private static final int[] SCREENSAVER_OPTIONS = {2, 10, 60};

    private static final int INK_DARK = 0xFF121418;
    private static final int INK_LIGHT = 0xFFF5F7FA;
    private static final int INK_SOFT = 0xD9FFFFFF;
    private static final int INK_MUTED = 0x99DCE6F2;
    private static final int FOCUS_WHITE = 0xF7FFFFFF;
    private static final int EMERALD = 0xFF4CD98B;

    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Map<String, List<JSONObject>> playableByCategory = new HashMap<>();
    private final List<JSONObject> allPlayable = new ArrayList<>();
    private final List<TextView> timeChips = new ArrayList<>();

    private boolean animateFocus;
    private Typeface fontRegular;
    private Typeface fontMedium;
    private Typeface fontLight;
    private int screenW;
    private int padH;
    private int cardGap;
    private int cardW;
    private int cardH;

    private LinearLayout shelvesHost;
    private TextView statusPill;
    private TextView screensaverToggleLabel;
    private View screensaverToggleDot;
    private View screensaverTogglePill;
    private LinearLayout timeRow;
    private boolean applying;
    private String activeCatalogId = "";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Config appearance = Config.load(this);
        animateFocus = appearance.animations && !appearance.reducedMotion;
        fontRegular = UiFont.get(this, R.string.font_regular);
        fontMedium = UiFont.get(this, R.string.font_medium);
        fontLight = UiFont.get(this, R.string.font_light);

        screenW = Math.max(dp(640), getResources().getDisplayMetrics().widthPixels);
        padH = Math.round(screenW * 0.052f);
        cardGap = Math.round(screenW * 0.017f);
        // 3 full 16:9 cards inside safe viewport + ~28% of 4th card peeking naturally at the right edge.
        cardW = Math.round(screenW * 0.273f);
        cardH = Math.round(cardW * 9f / 16f);

        activeCatalogId = resolveActiveCatalogId();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new AmbientBackdropDrawable());
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, dp(26), 0, dp(48));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader());

        shelvesHost = new LinearLayout(this);
        shelvesHost.setOrientation(LinearLayout.VERTICAL);
        shelvesHost.setClipChildren(false);
        shelvesHost.setClipToPadding(false);
        root.addView(shelvesHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildScreensaverSection());

        setContentView(scroll);
        load();
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(padH, 0, padH, dp(10));

        TextView heading = new TextView(this);
        heading.setText(getString(R.string.quick_wallpaper));
        heading.setTextColor(INK_LIGHT);
        heading.setTextSize(28f);
        heading.setTypeface(fontMedium);
        heading.setSingleLine(true);
        heading.setIncludeFontPadding(false);
        bar.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusPill = new TextView(this);
        statusPill.setText(copy("正在加载在线壁纸…", "Loading wallpapers…"));
        statusPill.setTextColor(INK_MUTED);
        statusPill.setTextSize(13f);
        statusPill.setTypeface(fontRegular);
        statusPill.setSingleLine(true);
        statusPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        statusPill.setBackground(roundRect(0x16FFFFFF, 0x22FFFFFF, 14));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.setMarginStart(dp(16));
        bar.addView(statusPill, statusLp);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Right-aligned single frosted pill: Local wallpapers (no Shuffle button in header).
        TextView localBtn = new TextView(this);
        localBtn.setText(copy("本地壁纸", "Local wallpapers"));
        localBtn.setTextSize(13.5f);
        localBtn.setTypeface(fontMedium);
        localBtn.setTextColor(INK_SOFT);
        localBtn.setGravity(Gravity.CENTER);
        localBtn.setSingleLine(true);
        localBtn.setPadding(dp(18), 0, dp(18), 0);
        localBtn.setFocusable(true);
        localBtn.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(localBtn);
        localBtn.setBackground(pillSurface(0x16FFFFFF, 0x26FFFFFF, 18));
        localBtn.setOnFocusChangeListener((v, focused) -> {
            localBtn.setTextColor(focused ? INK_DARK : INK_SOFT);
            CompatApi.elevation(localBtn, focused ? dpF(8) : 0f);
            springScale(localBtn, focused ? 1.05f : 1f);
        });
        localBtn.setOnClickListener(v -> startActivity(new Intent(this, PersonalizationActivity.class)));
        bar.addView(localBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        return bar;
    }

    private void load() {
        worker.execute(() -> {
            try {
                JSONArray groups = TvCloud.json("wallpaper?view=catalog").getJSONArray("categories");
                runOnUiThread(() -> {
                    if (!isDestroyed()) render(groups);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    showStatus(copy("离线模式 · 仅显示本地默认壁纸", "Offline · Default wallpaper only"), true);
                    renderOfflineFallback();
                });
            }
        });
    }

    private void renderOfflineFallback() {
        shelvesHost.removeAllViews();
        playableByCategory.clear();
        allPlayable.clear();
        try {
            JSONArray fallback = new JSONArray();
            JSONObject def = new JSONObject()
                    .put("id", "default")
                    .put("title", "默认壁纸")
                    .put("titleEn", "Default wallpaper")
                    .put("kind", "image");
            fallback.put(new JSONObject()
                    .put("id", "images")
                    .put("title", "图片")
                    .put("titleEn", "Pictures")
                    .put("items", new JSONArray().put(def)));
            render(fallback);
        } catch (Exception ignored) { }
    }

    private void render(JSONArray rawGroups) {
        if (rawGroups.length() > 1) {
            statusPill.setVisibility(View.GONE);
        }
        shelvesHost.removeAllViews();
        playableByCategory.clear();
        allPlayable.clear();

        // Index playable items per category first so Shuffle cards know available counts.
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < rawGroups.length(); i++) {
            JSONObject g = rawGroups.optJSONObject(i);
            if (g == null) continue;
            String id = g.optString("id");
            byId.put(id, g);
            JSONArray items = g.optJSONArray("items");
            if (items == null) continue;
            List<JSONObject> list = new ArrayList<>();
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                String kind = item.optString("kind");
                if ("video".equals(kind) || "image".equals(kind)) {
                    list.add(item);
                    allPlayable.add(item);
                }
            }
            playableByCategory.put(id, list);
        }

        // Render categories in strict order: 萌宠, 自然, 唯美, 图片, 随机.
        String[] order = {"pets", "nature", "dreamy", "images", "shuffle"};
        View firstFocus = null;
        for (String catId : order) {
            JSONObject group = byId.get(catId);
            if ("shuffle".equals(catId)) {
                group = buildShuffleGroup();
            } else if (group == null) {
                continue;
            }
            String shelfTitle = "shuffle".equals(catId)
                    ? copy("随机", "Shuffle")
                    : title(group);
            JSONArray items = group.optJSONArray("items");
            if (items == null || items.length() == 0) continue;
            View candidate = addShelf(catId, shelfTitle, items);
            if (firstFocus == null && candidate != null) firstFocus = candidate;
        }

        if (firstFocus != null) {
            final View target = firstFocus;
            target.post(target::requestFocus);
        }
    }

    /** Ensures the 5 requested shuffle options always appear cleanly under the "随机" shelf. */
    private JSONObject buildShuffleGroup() {
        try {
            JSONArray items = new JSONArray();
            items.put(shuffleItem("shuffle-all", "全部随机", "Shuffle all", "all"));
            items.put(shuffleItem("shuffle-pets", "随机播放萌宠", "Shuffle pets", "pets"));
            items.put(shuffleItem("shuffle-nature", "随机播放自然", "Shuffle nature", "nature"));
            items.put(shuffleItem("shuffle-dreamy", "随机播放唯美", "Shuffle dreamy", "dreamy"));
            items.put(shuffleItem("shuffle-images", "随机播放图片", "Shuffle pictures", "images"));
            return new JSONObject()
                    .put("id", "shuffle")
                    .put("title", "随机")
                    .put("titleEn", "Shuffle")
                    .put("items", items);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONObject shuffleItem(String id, String zh, String en, String scope) throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("title", zh)
                .put("titleEn", en)
                .put("kind", "shuffle")
                .put("scope", scope);
    }

    private View addShelf(String catId, String headingText, JSONArray items) {
        int total = items.length();
        boolean overflows = total > 3;

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = dp(18);
        shelvesHost.addView(section, secLp);

        // Shelf header: category title + subtle count pill + faint right chevron hint when >3 items.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(padH, 0, padH, dp(4));

        TextView heading = new TextView(this);
        heading.setText(headingText);
        heading.setTextColor(INK_LIGHT);
        heading.setTextSize(19f);
        heading.setTypeface(fontMedium);
        heading.setSingleLine(true);
        header.addView(heading);

        TextView countPill = new TextView(this);
        countPill.setText(String.valueOf(total));
        countPill.setTextColor(0xB8DCE6F2);
        countPill.setTextSize(12f);
        countPill.setTypeface(fontMedium);
        countPill.setPadding(dp(9), dp(2), dp(9), dp(2));
        countPill.setBackground(roundRect(0x16FFFFFF, 0x1EFFFFFF, 11));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.setMarginStart(dp(10));
        header.addView(countPill, countLp);

        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(0x66FFFFFF);
        chevron.setTextSize(22f);
        chevron.setTypeface(fontLight);
        chevron.setVisibility(overflows ? View.VISIBLE : View.GONE);
        header.addView(chevron);

        section.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Edge-to-edge horizontal shelf with right/left dark fade vignettes.
        FrameLayout shelfFrame = new FrameLayout(this);
        shelfFrame.setClipChildren(false);
        shelfFrame.setClipToPadding(false);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFocusable(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(padH, dp(10), padH, dp(8));
        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shelfFrame.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Soft dark obsidian edge-fade vignettes so the peeking 4th card melts cleanly into the screen edge.
        View rightFade = new View(this);
        rightFade.setFocusable(false);
        rightFade.setClickable(false);
        rightFade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rightFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x0007090F, 0xB807090F, 0xF507090F}));
        rightFade.setAlpha(overflows ? 1f : 0f);
        shelfFrame.addView(rightFade, new FrameLayout.LayoutParams(
                Math.round(screenW * 0.115f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        View leftFade = new View(this);
        leftFade.setFocusable(false);
        leftFade.setClickable(false);
        leftFade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        leftFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE807090F, 0x0007090F}));
        leftFade.setAlpha(0f);
        shelfFrame.addView(leftFade, new FrameLayout.LayoutParams(
                padH, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        Runnable updateFades = () -> {
            int maxScroll = Math.max(0, row.getWidth() - scroller.getWidth());
            int sx = scroller.getScrollX();
            float rightAlpha = maxScroll > dp(6)
                    ? Math.max(0f, Math.min(1f, (maxScroll - sx) / (float) dp(36)))
                    : 0f;
            float leftAlpha = Math.max(0f, Math.min(1f, sx / (float) dp(36)));
            rightFade.setAlpha(rightAlpha);
            leftFade.setAlpha(leftAlpha);
            if (overflows) chevron.setAlpha(0.25f + 0.75f * rightAlpha);
        };
        // ViewTreeObserver works on API 17; View#setOnScrollChangeListener needs API 23.
        scroller.getViewTreeObserver().addOnScrollChangedListener(updateFades::run);
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateFades.run());

        View firstCard = null;
        for (int i = 0; i < total; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            final int index = i;
            View card = buildCard(catId, item, index, total, countPill, scroller, row);
            if (firstCard == null) firstCard = card;
        }

        section.addView(shelfFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return firstCard;
    }

    private View buildCard(String catId, JSONObject item, int index, int total,
                           TextView countPill, HorizontalScrollView scroller, LinearLayout row) {
        String id = item.optString("id");
        String kind = item.optString("kind");
        String name = title(item);
        boolean isVideo = "video".equals(kind);
        boolean isPlaceholder = "placeholder".equals(kind);
        boolean isShuffle = "shuffle".equals(kind);
        boolean isDefault = "default".equals(id);
        boolean isActive = !activeCatalogId.isEmpty() && activeCatalogId.equals(id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setFocusable(true);
        card.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(card);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index < total - 1) cardLp.setMarginEnd(cardGap);
        row.addView(card, cardLp);

        // Poster frame with rounded outline clipping and crisp focus halo overlay.
        FrameLayout posterWrap = new FrameLayout(this);
        posterWrap.setClipChildren(false);
        posterWrap.setClipToPadding(false);

        final float radius = dpF(16);
        FrameLayout cover = new FrameLayout(this);
        CompatApi.roundClip(cover, radius);
        cover.setBackground(themedCardSurface(catId, id, isPlaceholder, isShuffle));
        posterWrap.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (isDefault) {
            image.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.raw.wallpaper_default_preview));
        }

        // Subtle dark gradient at bottom of real photos/videos for badge legibility.
        if (!isPlaceholder && !isShuffle) {
            View bottomScrim = new View(this);
            bottomScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x55060910}));
            cover.addView(bottomScrim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM));
        }

        // Bottom-left frosted badge: "▶ 动态" on video cards.
        if (isVideo) {
            TextView badge = new TextView(this);
            badge.setText(copy("▶  动态", "▶  Motion"));
            badge.setTextColor(0xF5FFFFFF);
            badge.setTextSize(11f);
            badge.setTypeface(fontMedium);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            badge.setBackground(roundRect(0x9E0B101A, 0x2AFFFFFF, 12));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            blp.setMargins(dp(10), 0, 0, dp(10));
            cover.addView(badge, blp);
        }

        // Top-right frosted badge with emerald dot: "使用中" on active wallpaper.
        if (isActive) {
            LinearLayout activePill = new LinearLayout(this);
            activePill.setOrientation(LinearLayout.HORIZONTAL);
            activePill.setGravity(Gravity.CENTER_VERTICAL);
            activePill.setPadding(dp(9), dp(4), dp(10), dp(4));
            activePill.setBackground(roundRect(0xA60B101A, 0x2AFFFFFF, 12));

            View dot = new View(this);
            dot.setBackground(circleShape(EMERALD));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
            dlp.setMarginEnd(dp(6));
            activePill.addView(dot, dlp);

            TextView activeLabel = new TextView(this);
            activeLabel.setText(copy("使用中", "Active"));
            activeLabel.setTextColor(0xF5FFFFFF);
            activeLabel.setTextSize(11f);
            activeLabel.setTypeface(fontMedium);
            activePill.addView(activeLabel);

            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            alp.setMargins(0, dp(10), dp(10), 0);
            cover.addView(activePill, alp);
        }

        // Upcoming placeholder card center pill: "即将上线".
        if (isPlaceholder) {
            TextView coming = new TextView(this);
            coming.setText(copy("即将上线", "Coming soon"));
            coming.setTextColor(0xCCDCE6F2);
            coming.setTextSize(12f);
            coming.setTypeface(fontMedium);
            coming.setPadding(dp(14), dp(5), dp(14), dp(5));
            coming.setBackground(roundRect(0x1EFFFFFF, 0x26FFFFFF, 14));
            cover.addView(coming, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }

        // Shuffle card centered circular emblem + subtle scope count hint.
        if (isShuffle) {
            String scope = item.optString("scope", "all");
            int available = candidatesForScope(scope).size();

            LinearLayout centerBox = new LinearLayout(this);
            centerBox.setOrientation(LinearLayout.VERTICAL);
            centerBox.setGravity(Gravity.CENTER);

            TextView icon = new TextView(this);
            icon.setText("↻");
            icon.setTextColor(available > 0 ? 0xF2FFFFFF : 0x80FFFFFF);
            icon.setTextSize(22f);
            icon.setTypeface(fontRegular);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(circleShape(available > 0 ? 0x22FFFFFF : 0x12FFFFFF));
            centerBox.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView sub = new TextView(this);
            sub.setText(available > 0
                    ? copy(available + " 款可随机", available + " available")
                    : copy("即将上线", "Coming soon"));
            sub.setTextColor(available > 0 ? 0xBDDCE6F2 : 0x80DCE6F2);
            sub.setTextSize(11.5f);
            sub.setTypeface(fontRegular);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(8);
            centerBox.addView(sub, slp);

            cover.addView(centerBox, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }

        // Crisp border overlay (idle subtle hairline -> focused luminous pure white ring).
        View ring = new View(this);
        ring.setBackground(borderDrawable(0x22FFFFFF, 0.75f, 16));
        posterWrap.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(posterWrap, new LinearLayout.LayoutParams(cardW, cardH));

        // Floating borderless caption beneath poster.
        TextView caption = new TextView(this);
        caption.setText(name);
        caption.setTextColor(isPlaceholder ? 0x80DCE6F2 : 0xB8DCE6F2);
        caption.setTextSize(14f);
        caption.setTypeface(fontRegular);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(9);
        card.addView(caption, capLp);

        card.setOnFocusChangeListener((v, focused) -> {
            ring.setBackground(focused
                    ? borderDrawable(isPlaceholder ? 0xB8FFFFFF : FOCUS_WHITE, 2.2f, 16)
                    : borderDrawable(0x22FFFFFF, 0.75f, 16));
            caption.setTextColor(focused ? 0xFFFFFFFF : (isPlaceholder ? 0x80DCE6F2 : 0xB8DCE6F2));
            caption.setTypeface(focused ? fontMedium : fontRegular);
            CompatApi.elevation(posterWrap, focused ? dpF(14) : 0f);
            springScale(card, focused ? 1.045f : 1f);
            if (focused) {
                countPill.setText((index + 1) + " / " + total);
                countPill.setTextColor(0xF0FFFFFF);
                countPill.setBackground(roundRect(0x24FFFFFF, 0x36FFFFFF, 11));
                scrollCardComfortablyIntoView(scroller, card, index);
            } else {
                // Defer reverting pill text until we know focus left this entire row.
                row.post(() -> {
                    if (!row.hasFocus()) {
                        countPill.setText(String.valueOf(total));
                        countPill.setTextColor(0xB8DCE6F2);
                        countPill.setBackground(roundRect(0x16FFFFFF, 0x1EFFFFFF, 11));
                    }
                });
            }
        });

        card.setOnClickListener(v -> {
            if (isPlaceholder) {
                Toast.makeText(this, copy(name + " 即将上线", name + " coming soon"), Toast.LENGTH_SHORT).show();
                return;
            }
            apply(item, kind, isDefault);
        });

        String preview = item.optString("preview");
        if (!preview.isEmpty() && !isDefault && !isPlaceholder && !isShuffle) {
            worker.execute(() -> {
                try {
                    HttpURLConnection connection = TvCloud.open(assetUrl(preview));
                    try {
                        byte[] data = TvCloud.read(connection.getInputStream(), 8 * 1024 * 1024);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = 1;
                        while (options.outWidth / options.inSampleSize > 640
                                || options.outHeight / options.inSampleSize > 640) {
                            options.inSampleSize *= 2;
                        }
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        runOnUiThread(() -> {
                            if (!isDestroyed() && bitmap != null) image.setImageBitmap(bitmap);
                        });
                    } finally {
                        connection.disconnect();
                    }
                } catch (Exception ignored) { /* Keep atmospheric placeholder surface. */ }
            });
        }
        return card;
    }

    private void scrollCardComfortablyIntoView(HorizontalScrollView scroller, View card, int index) {
        scroller.post(() -> {
            int targetX;
            if (index == 0) {
                targetX = 0;
            } else {
                int cardLeft = card.getLeft();
                int cardRight = card.getRight();
                int viewW = scroller.getWidth();
                int current = scroller.getScrollX();
                int safeRightMargin = Math.round(screenW * 0.13f);
                if (cardLeft - padH < current) {
                    targetX = Math.max(0, cardLeft - padH);
                } else if (cardRight + safeRightMargin > current + viewW) {
                    targetX = Math.max(0, cardRight + safeRightMargin - viewW);
                } else {
                    return;
                }
            }
            if (animateFocus) scroller.smoothScrollTo(targetX, 0);
            else scroller.scrollTo(targetX, 0);
        });
    }

    private List<JSONObject> candidatesForScope(String scope) {
        if ("all".equals(scope) || scope == null || scope.isEmpty()) return allPlayable;
        List<JSONObject> list = playableByCategory.get(scope);
        return list == null ? new ArrayList<>() : list;
    }

    private void apply(JSONObject item, String kind, boolean fallback) {
        if (applying) return;
        if (fallback && item.optString("path").isEmpty()) {
            saveActiveCatalogId("default");
            BundledWallpapers.select(this, "classic");
            finish();
            return;
        }
        boolean shuffle = "shuffle".equals(kind);
        JSONObject chosen = item;
        if (shuffle) {
            String scope = item.optString("scope", "all");
            List<JSONObject> pool = candidatesForScope(scope);
            if (pool.isEmpty()) {
                Toast.makeText(this, copy("该分类素材即将上线", "Coming soon for this category"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // Prefer picking a different wallpaper than the currently active one when multiple exist.
            List<JSONObject> fresh = new ArrayList<>();
            for (JSONObject candidate : pool) {
                if (!candidate.optString("id").equals(activeCatalogId)) fresh.add(candidate);
            }
            List<JSONObject> source = fresh.isEmpty() ? pool : fresh;
            chosen = source.get(new Random().nextInt(source.size()));
        }

        final JSONObject target = chosen;
        final boolean targetIsVideo = "video".equals(target.optString("kind"));
        final String targetId = target.optString("id");
        applying = true;
        showStatus(copy("正在下载「" + title(target) + "」…", "Downloading " + title(target) + "…"), false);

        worker.execute(() -> {
            String selection = null;
            try {
                String path = target.optString("path");
                if ("default".equals(targetId) && path.isEmpty()) {
                    selection = "classic";
                } else {
                    HttpURLConnection connection = TvCloud.open(assetUrl(path));
                    try {
                        selection = UserWallpaper.importDownload(getApplicationContext(),
                                connection.getInputStream(), targetIsVideo);
                    } finally {
                        connection.disconnect();
                    }
                }
            } catch (Exception error) {
                if (fallback) selection = "classic";
            }
            final String applied = selection;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                applying = false;
                if (applied == null) {
                    showStatus(copy("下载失败，请重试", "Download failed. Try again."), true);
                } else {
                    saveActiveCatalogId(targetId);
                    BundledWallpapers.select(this, applied);
                    finish();
                }
            });
        });
    }

    /** Bottom screensaver switch and start-time selector, aligned with GenTV's frosted settings glass. */
    private View buildScreensaverSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        section.setPadding(padH, dp(26), padH, 0);

        TextView heading = new TextView(this);
        heading.setText(copy("屏保设置", "Screensaver"));
        heading.setTextColor(INK_LIGHT);
        heading.setTextSize(19f);
        heading.setTypeface(fontMedium);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(12);
        section.addView(heading, hlp);

        LinearLayout plate = new LinearLayout(this);
        plate.setOrientation(LinearLayout.VERTICAL);
        plate.setClipChildren(false);
        plate.setClipToPadding(false);
        plate.setPadding(dp(22), dp(16), dp(22), dp(16));
        plate.setBackground(roundRect(0x12FFFFFF, 0x1EFFFFFF, 20));

        // Row 1: Screensaver ON/OFF switch
        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setClipChildren(false);
        switchRow.setClipToPadding(false);

        LinearLayout switchMeta = new LinearLayout(this);
        switchMeta.setOrientation(LinearLayout.VERTICAL);
        TextView switchTitle = new TextView(this);
        switchTitle.setText(copy("屏保开关", "Screensaver"));
        switchTitle.setTextColor(INK_LIGHT);
        switchTitle.setTextSize(15.5f);
        switchTitle.setTypeface(fontMedium);
        switchMeta.addView(switchTitle);

        TextView switchDesc = new TextView(this);
        switchDesc.setText(copy("桌面空闲达到设定时间后自动进入航拍屏保",
                "Automatically launch aerial screensaver while Home is idle"));
        switchDesc.setTextColor(INK_MUTED);
        switchDesc.setTextSize(12.5f);
        switchDesc.setTypeface(fontRegular);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(2);
        switchMeta.addView(switchDesc, dLp);
        switchRow.addView(switchMeta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout togglePill = new LinearLayout(this);
        togglePill.setOrientation(LinearLayout.HORIZONTAL);
        togglePill.setGravity(Gravity.CENTER_VERTICAL);
        togglePill.setPadding(dp(16), 0, dp(18), 0);
        togglePill.setFocusable(true);
        togglePill.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(togglePill);
        togglePill.setBackground(pillSurface(0x18FFFFFF, 0x26FFFFFF, 18));

        screensaverToggleDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.setMarginEnd(dp(8));
        togglePill.addView(screensaverToggleDot, dotLp);

        screensaverToggleLabel = new TextView(this);
        screensaverToggleLabel.setTextSize(13.5f);
        screensaverToggleLabel.setTypeface(fontMedium);
        screensaverToggleLabel.setSingleLine(true);
        togglePill.addView(screensaverToggleLabel);

        screensaverTogglePill = togglePill;
        togglePill.setOnFocusChangeListener((v, focused) -> {
            updateScreensaverUi();
            CompatApi.elevation(togglePill, focused ? dpF(8) : 0f);
            springScale(togglePill, focused ? 1.05f : 1f);
        });
        togglePill.setOnClickListener(v -> toggleScreensaver());
        switchRow.addView(togglePill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        plate.addView(switchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Hairline divider
        View divider = new View(this);
        divider.setBackgroundColor(0x16FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        divLp.topMargin = dp(14);
        divLp.bottomMargin = dp(14);
        plate.addView(divider, divLp);

        // Row 2: Screensaver start delay time chips (2 min / 10 min / 60 min)
        timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setClipChildren(false);
        timeRow.setClipToPadding(false);

        TextView timeTitle = new TextView(this);
        timeTitle.setText(copy("启动时间", "Start after"));
        timeTitle.setTextColor(INK_LIGHT);
        timeTitle.setTextSize(15.5f);
        timeTitle.setTypeface(fontMedium);
        timeRow.addView(timeTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setClipChildren(false);
        chips.setClipToPadding(false);

        timeChips.clear();
        for (int i = 0; i < SCREENSAVER_OPTIONS.length; i++) {
            final int mins = SCREENSAVER_OPTIONS[i];
            TextView chip = new TextView(this);
            chip.setTag(mins);
            chip.setText(copy(mins + " 分钟", mins + " min"));
            chip.setTextSize(13.5f);
            chip.setTypeface(fontMedium);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setFocusable(true);
            chip.setClickable(true);
            CompatApi.disableDefaultFocusHighlight(chip);
            chip.setOnFocusChangeListener((v, focused) -> {
                updateScreensaverUi();
                CompatApi.elevation(chip, focused ? dpF(8) : 0f);
                springScale(chip, focused ? 1.05f : 1f);
            });
            chip.setOnClickListener(v -> setScreensaverMinutes(mins));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            if (i > 0) clp.setMarginStart(dp(10));
            chips.addView(chip, clp);
            timeChips.add(chip);
        }
        timeRow.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        plate.addView(timeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section.addView(plate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        updateScreensaverUi();
        return section;
    }

    private SharedPreferences settingsPrefs() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
    }

    private int currentScreensaverMinutes() {
        return ScreensaverIdle.minutes(settingsPrefs().getInt(
                ScreensaverIdle.MINUTES_KEY, ScreensaverIdle.DEFAULT_MINUTES));
    }

    private void toggleScreensaver() {
        SharedPreferences prefs = settingsPrefs();
        int current = currentScreensaverMinutes();
        if (current > 0) {
            prefs.edit()
                    .putInt(KEY_LAST_SCREENSAVER_MINUTES, current)
                    .putInt(ScreensaverIdle.MINUTES_KEY, 0)
                    .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                    .apply();
        } else {
            int restore = ScreensaverIdle.minutes(prefs.getInt(
                    KEY_LAST_SCREENSAVER_MINUTES, ScreensaverIdle.DEFAULT_MINUTES));
            if (restore == 0) restore = ScreensaverIdle.DEFAULT_MINUTES;
            setScreensaverMinutes(restore);
            return;
        }
        updateScreensaverUi();
    }

    private void setScreensaverMinutes(int minutes) {
        SharedPreferences prefs = settingsPrefs();
        prefs.edit()
                .putInt(ScreensaverIdle.MINUTES_KEY, minutes)
                .putInt(KEY_LAST_SCREENSAVER_MINUTES, minutes)
                .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
        updateScreensaverUi();
    }

    private void updateScreensaverUi() {
        int current = currentScreensaverMinutes();
        boolean enabled = current > 0;
        boolean toggleFocused = screensaverTogglePill != null && screensaverTogglePill.isFocused();

        if (screensaverToggleLabel != null) {
            screensaverToggleLabel.setText(enabled ? copy("已开启", "On") : copy("已关闭", "Off"));
            screensaverToggleLabel.setTextColor(toggleFocused ? INK_DARK : INK_SOFT);
        }
        if (screensaverToggleDot != null) {
            screensaverToggleDot.setBackground(circleShape(
                    enabled ? EMERALD : (toggleFocused ? 0x55121418 : 0x55FFFFFF)));
        }
        if (timeRow != null) {
            timeRow.setAlpha(enabled ? 1f : 0.55f);
        }
        for (TextView chip : timeChips) {
            int mins = (Integer) chip.getTag();
            boolean selected = enabled && mins == current;
            boolean focused = chip.isFocused();
            if (focused) {
                chip.setBackground(roundRect(FOCUS_WHITE, 0, 18));
                chip.setTextColor(INK_DARK);
            } else if (selected) {
                chip.setBackground(roundRect(0x2EFFFFFF, 0x5CFFFFFF, 18));
                chip.setTextColor(INK_LIGHT);
            } else {
                chip.setBackground(roundRect(0x12FFFFFF, 0x20FFFFFF, 18));
                chip.setTextColor(INK_MUTED);
            }
        }
    }

    private String resolveActiveCatalogId() {
        String selected = BundledWallpapers.selected(this);
        if ("classic".equals(selected)) return "default";
        if (UserWallpaper.isVideo(selected) || UserWallpaper.isImage(selected)) {
            return settingsPrefs().getString(KEY_ACTIVE_CATALOG_ID, "");
        }
        return "";
    }

    private void saveActiveCatalogId(String id) {
        activeCatalogId = id == null ? "" : id;
        settingsPrefs().edit().putString(KEY_ACTIVE_CATALOG_ID, activeCatalogId).apply();
    }

    private void showStatus(String message, boolean error) {
        statusPill.setVisibility(View.VISIBLE);
        statusPill.setText(message);
        statusPill.setTextColor(error ? 0xFFFFD199 : 0xE6FFFFFF);
        statusPill.setBackground(roundRect(
                error ? 0x24FFAD42 : 0x1EFFFFFF,
                error ? 0x44FFAD42 : 0x2EFFFFFF, 14));
    }

    /** Atmospheric dark smoked-glass gradients for upcoming & shuffle cards. */
    private GradientDrawable themedCardSurface(String catId, String itemId,
                                               boolean placeholder, boolean shuffle) {
        int start = 0xFF161E2C;
        int end = 0xFF0E131D;
        if (placeholder || shuffle) {
            if ("pet-dog".equals(itemId) || "shuffle-pets".equals(itemId)) {
                start = 0xFF2B241E; end = 0xFF171412;
            } else if ("pet-rabbit".equals(itemId)) {
                start = 0xFF1D2C21; end = 0xFF111914;
            } else if ("nature-sea".equals(itemId) || "shuffle-nature".equals(itemId)) {
                start = 0xFF122B35; end = 0xFF0C1820;
            } else if ("nature-forest".equals(itemId)) {
                start = 0xFF122C22; end = 0xFF0B1813;
            } else if ("nature-mountain".equals(itemId)) {
                start = 0xFF1D203C; end = 0xFF101324;
            } else if ("dreamy-sakura".equals(itemId) || "shuffle-dreamy".equals(itemId)) {
                start = 0xFF2F1D2C; end = 0xFF18111B;
            } else if ("dreamy-starlight".equals(itemId)) {
                start = 0xFF1B1C38; end = 0xFF0F1021;
            } else if ("dreamy-clouds".equals(itemId)) {
                start = 0xFF1A283A; end = 0xFF0F1722;
            } else if ("shuffle-all".equals(itemId)) {
                start = 0xFF182A44; end = 0xFF0E1626;
            } else if ("shuffle-images".equals(itemId)) {
                start = 0xFF1B2B36; end = 0xFF101820;
            } else if ("nature".equals(catId)) {
                start = 0xFF122A32; end = 0xFF0C171E;
            } else if ("dreamy".equals(catId)) {
                start = 0xFF261C34; end = 0xFF141020;
            }
        }
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        d.setCornerRadius(dpF(16));
        return d;
    }

    private void springScale(View view, float target) {
        if (!animateFocus) {
            view.setScaleX(target);
            view.setScaleY(target);
            return;
        }
        springAxis(view, DynamicAnimation.SCALE_X, R.id.panel, target);
        springAxis(view, DynamicAnimation.SCALE_Y, R.id.rows, target);
    }

    private void springAxis(View view, DynamicAnimation.ViewProperty prop, int tagKey, float target) {
        Object existing = view.getTag(tagKey);
        SpringAnimation anim;
        if (existing instanceof SpringAnimation) {
            anim = (SpringAnimation) existing;
        } else {
            anim = new SpringAnimation(view, prop, target);
            SpringForce force = new SpringForce(target);
            force.setStiffness(SpringForce.STIFFNESS_MEDIUM);
            force.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
            anim.setSpring(force);
            view.setTag(tagKey, anim);
        }
        anim.animateToFinalPosition(target);
    }

    private static String assetUrl(String path) throws IOException {
        if (!path.matches("wallpapers/[A-Za-z0-9_%.-]+")
                || android.net.Uri.decode(path.substring(11)).matches(".*[/\\\\].*")) {
            throw new IOException("Invalid wallpaper path");
        }
        return TvCloud.BASE + path;
    }

    private String title(JSONObject item) {
        return copy(item.optString("title"), item.optString("titleEn"));
    }

    private String copy(String zh, String en) {
        return "zh".equals(CompatApi.locale(getResources().getConfiguration()).getLanguage()) ? zh : en;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private GradientDrawable roundRect(int fillColor, int borderColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setCornerRadius(dpF(radiusDp));
        if (borderColor != 0) {
            shape.setStroke(Math.max(1, Math.round(dpF(0.75f))), borderColor);
        }
        return shape;
    }

    private GradientDrawable borderDrawable(int borderColor, float strokeDp, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setCornerRadius(dpF(radiusDp));
        shape.setStroke(Math.max(1, Math.round(dpF(strokeDp))), borderColor);
        return shape;
    }

    private GradientDrawable circleShape(int fillColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(fillColor);
        return shape;
    }

    private StateListDrawable pillSurface(int idleFill, int idleBorder, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, roundRect(FOCUS_WHITE, 0, radiusDp));
        states.addState(new int[]{android.R.attr.state_pressed}, roundRect(0xDDFFFFFF, 0, radiusDp));
        states.addState(new int[]{}, roundRect(idleFill, idleBorder, radiusDp));
        return states;
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Deep cinema-grade obsidian backdrop matching Gen Extensions & Control Center. */
    private static final class AmbientBackdropDrawable extends Drawable {
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowTop = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            float w = Math.max(1f, bounds.width());
            float h = Math.max(1f, bounds.height());
            base.setShader(new LinearGradient(0f, 0f, 0f, h,
                    0xFF0A0D14, 0xFF06080C, Shader.TileMode.CLAMP));
            glowTop.setShader(new RadialGradient(w * 0.82f, h * 0.08f, w * 0.56f,
                    0x2A255294, 0x000A0D14, Shader.TileMode.CLAMP));
            glowBottom.setShader(new RadialGradient(w * 0.14f, h * 0.88f, w * 0.46f,
                    0x183D2E6B, 0x0006080C, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b, base);
            canvas.drawRect(b, glowTop);
            canvas.drawRect(b, glowBottom);
        }

        @Override
        public void setAlpha(int alpha) {
            base.setAlpha(alpha);
            glowTop.setAlpha(alpha);
            glowBottom.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            base.setColorFilter(colorFilter);
            glowTop.setColorFilter(colorFilter);
            glowBottom.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
