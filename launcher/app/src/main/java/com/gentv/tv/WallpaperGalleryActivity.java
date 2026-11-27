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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import android.util.AtomicFile;
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

    private VerticalGridView shelvesGrid;
    private View galleryHeader;
    private final List<GalleryShelf> shelfModels = new ArrayList<>();
    private final ShelfAdapter shelfAdapter = new ShelfAdapter();
    private final android.util.LruCache<String, Bitmap> previewCache =
            new android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
            };
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

        LinearLayout root = new LinearLayout(this);
        root.setBackground(new AmbientBackdropDrawable());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, dp(26), 0, 0);
        galleryHeader = buildHeader();

        // Title, shelves and screensaver controls share the same scrolling viewport.
        FrameLayout viewport = new FrameLayout(this);
        viewport.setClipChildren(true);
        root.addView(viewport, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shelvesGrid = new VerticalGridView(this);
        shelvesGrid.setNumColumns(1);
        shelvesGrid.setPadding(0, dp(8), 0, dp(30));
        shelvesGrid.setVerticalSpacing(dp(18));
        shelvesGrid.setClipChildren(false);
        shelvesGrid.setClipToPadding(false);
        shelvesGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelvesGrid.setVerticalScrollBarEnabled(false);
        shelvesGrid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        shelvesGrid.setWindowAlignmentOffsetPercent(50f);
        shelvesGrid.setItemAlignmentOffsetPercent(50f);
        shelvesGrid.setSaveChildrenPolicy(BaseGridView.SAVE_ALL_CHILD);
        shelvesGrid.setItemAnimator(null);
        shelvesGrid.setAdapter(shelfAdapter);
        viewport.addView(shelvesGrid, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
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
            AtomicFile cache = new AtomicFile(new File(getFilesDir(), "wallpaper-gallery-catalog.json"));
            JSONArray saved = null;
            try {
                byte[] bytes = TvCloud.read(cache.openRead(), WallpaperCatalogSnapshot.MAX_BYTES);
                saved = WallpaperCatalogSnapshot.decode(TvCloud.BASE, new String(bytes, Charset.forName("UTF-8")));
            } catch (Exception ignored) { /* A missing or invalid cache must never block a fresh request. */ }
            final JSONArray cached = saved;
            if (cached != null) runOnUiThread(() -> {
                if (isDestroyed()) return;
                render(cached);
                showStatus(copy("已显示缓存 · 正在更新…", "Cached wallpapers · Updating…"), false);
            });
            try {
                JSONObject response = TvCloud.json("wallpaper?view=catalog");
                JSONArray groups = WallpaperCatalogSnapshot.categories(response);
                // Cache failures must not turn a successful online response into an offline error.
                try {
                    byte[] bytes = WallpaperCatalogSnapshot.encode(TvCloud.BASE, response,
                            System.currentTimeMillis()).getBytes(Charset.forName("UTF-8"));
                    FileOutputStream stream = cache.startWrite();
                    try { stream.write(bytes); cache.finishWrite(stream); }
                    catch (IOException failure) { cache.failWrite(stream); throw failure; }
                } catch (Exception failure) {
                    android.util.Log.w("GenTV.Wallpaper", "Unable to save catalog cache", failure);
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (cached == null || !cached.toString().equals(groups.toString())) render(groups);
                    statusPill.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                android.util.Log.w("GenTV.Wallpaper", "Catalog refresh failed", error);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (cached != null) {
                        showStatus(copy("网络不可用 · 显示上次内容", "Offline · Showing cached wallpapers"), true);
                    } else {
                        showStatus(copy("离线模式 · 仅显示本地默认壁纸", "Offline · Default wallpaper only"), true);
                        renderOfflineFallback();
                    }
                });
            }
        });
    }

    private void renderOfflineFallback() {
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
        Map<String, GalleryShelf> previous = new HashMap<>();
        for (GalleryShelf shelf : shelfModels) previous.put(shelf.id, shelf);
        boolean restoreFocus = !previous.isEmpty() && shelvesGrid.hasFocus();
        int priorPosition = shelvesGrid.getSelectedPosition();
        boolean priorFooter = !shelfModels.isEmpty() && priorPosition == shelfModels.size() + 1;
        String priorShelfId = priorPosition > 0 && priorPosition <= shelfModels.size()
                ? shelfModels.get(priorPosition - 1).id : null;
        if (rawGroups.length() > 1) {
            statusPill.setVisibility(View.GONE);
        }
        shelfModels.clear();
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
            JSONArray items = visibleItems(group.optJSONArray("items"));
            if (items.length() == 0) continue;
            GalleryShelf model = new GalleryShelf(catId, shelfTitle, items);
            GalleryShelf old = previous.get(catId);
            if (old != null) {
                model.selected = Math.min(old.selected, items.length() - 1);
                JSONObject selected = old.items.optJSONObject(old.selected);
                if (selected != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject candidate = items.optJSONObject(i);
                    if (candidate != null && selected.optString("id").equals(candidate.optString("id"))) {
                        model.selected = i;
                        break;
                    }
                }
            }
            shelfModels.add(model);
        }
        int nextPosition = !restoreFocus ? (shelfModels.isEmpty() ? 0 : 1) : 0;
        if (restoreFocus && priorFooter) nextPosition = shelfModels.size() + 1;
        else if (restoreFocus && priorShelfId != null) {
            nextPosition = Math.min(Math.max(1, priorPosition), shelfModels.size());
            for (int i = 0; i < shelfModels.size(); i++)
                if (priorShelfId.equals(shelfModels.get(i).id)) { nextPosition = i + 1; break; }
        }
        final int focusPosition = nextPosition;
        shelfAdapter.notifyDataSetChanged();
        shelvesGrid.post(() -> {
            if (isDestroyed()) return;
            shelvesGrid.setSelectedPosition(focusPosition);
            shelvesGrid.requestFocus();
        });
    }

    /** Real wallpapers only: no "coming soon" placeholders, and no Shuffle choice whose scope has nothing yet. */
    private JSONArray visibleItems(JSONArray items) {
        JSONArray visible = new JSONArray();
        if (items == null) return visible;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String kind = item.optString("kind");
            if ("placeholder".equals(kind)) continue;
            if ("shuffle".equals(kind) && candidatesForScope(item.optString("scope", "all")).isEmpty()) continue;
            visible.put(item);
        }
        return visible;
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

    private static final class GalleryShelf {
        final String id;
        final String title;
        final JSONArray items;
        int selected;
        GalleryShelf(String id, String title, JSONArray items) {
            this.id = id; this.title = title; this.items = items;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        RowHolder(FrameLayout view) { super(view); }
    }

    private static RecyclerView.LayoutParams gridParams(ViewGroup parent, int width) {
        // Leanback requires its own GridLayoutManager.LayoutParams, including on RecyclerView 1.3.
        RecyclerView.LayoutParams params = ((RecyclerView) parent).getLayoutManager().generateDefaultLayoutParams();
        params.width = width;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        return params;
    }

    private final class ShelfAdapter extends RecyclerView.Adapter<RowHolder> {
        @Override public int getItemViewType(int position) {
            return position == 0 ? 0 : position == shelfModels.size() + 1 ? 2 : 1;
        }
        @Override public int getItemCount() { return shelfModels.size() + 2; }
        @Override public RowHolder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout holder = new FrameLayout(WallpaperGalleryActivity.this);
            holder.setClipChildren(false);
            holder.setClipToPadding(false);
            holder.setLayoutParams(gridParams(parent, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RowHolder(holder);
        }
        @Override public void onBindViewHolder(RowHolder holder, int position) {
            FrameLayout frame = (FrameLayout) holder.itemView;
            frame.removeAllViews();
            View content;
            if (position == 0) {
                content = galleryHeader;
                if (content.getParent() instanceof ViewGroup) ((ViewGroup) content.getParent()).removeView(content);
            } else if (position == shelfModels.size() + 1) {
                content = buildScreensaverSection();
            } else {
                content = buildShelf(shelfModels.get(position - 1));
            }
            frame.addView(content, new FrameLayout.LayoutParams(-1, -2));
        }
    }

    private final class PosterAdapter extends RecyclerView.Adapter<RowHolder> {
        private final GalleryShelf shelf;
        private final TextView countPill;
        private final HorizontalGridView grid;
        PosterAdapter(GalleryShelf shelf, TextView countPill, HorizontalGridView grid) {
            this.shelf = shelf; this.countPill = countPill; this.grid = grid;
        }
        @Override public int getItemCount() { return shelf.items.length(); }
        @Override public RowHolder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout frame = new FrameLayout(WallpaperGalleryActivity.this);
            frame.setClipChildren(false);
            frame.setClipToPadding(false);
            frame.setLayoutParams(gridParams(parent, cardW));
            return new RowHolder(frame);
        }
        @Override public void onBindViewHolder(RowHolder holder, int position) {
            FrameLayout frame = (FrameLayout) holder.itemView;
            frame.removeAllViews();
            JSONObject item = shelf.items.optJSONObject(position);
            if (item != null) frame.addView(buildCard(shelf.id, item, position,
                    getItemCount(), countPill, grid), new FrameLayout.LayoutParams(-1, -2));
        }
    }

    private View buildShelf(GalleryShelf model) {
        String catId = model.id;
        String headingText = model.title;
        JSONArray items = model.items;
        int total = items.length();
        boolean overflows = total > 3;

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);

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

        HorizontalGridView row = new HorizontalGridView(this);
        row.setNumRows(1);
        row.setHorizontalSpacing(cardGap);
        row.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        row.setWindowAlignmentOffsetPercent(50f);
        row.setItemAlignmentOffsetPercent(50f);
        row.setSaveChildrenPolicy(BaseGridView.SAVE_ALL_CHILD);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(padH, dp(12), padH, dp(12));
        row.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setHorizontalScrollBarEnabled(false);
        row.setItemAnimator(null);
        row.setAdapter(new PosterAdapter(model, countPill, row));
        row.setSelectedPosition(model.selected);
        row.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override public void onChildViewHolderSelected(RecyclerView parent,
                    RecyclerView.ViewHolder child, int position, int subposition) {
                if (position >= 0) model.selected = position;
            }
        });
        shelfFrame.addView(row, new FrameLayout.LayoutParams(-1, cardH + dp(54)));

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
            rightFade.setAlpha(row.canScrollHorizontally(1) ? 1f : 0f);
            leftFade.setAlpha(row.canScrollHorizontally(-1) ? 1f : 0f);
            if (overflows) chevron.setAlpha(row.canScrollHorizontally(1) ? 1f : 0.25f);
        };
        row.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) { updateFades.run(); }
        });
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateFades.run());

        section.addView(shelfFrame, new LinearLayout.LayoutParams(-1, -2));
        return section;
    }

    private View buildCard(String catId, JSONObject item, int index, int total,
                           TextView countPill, HorizontalGridView row) {
        String id = item.optString("id");
        String kind = item.optString("kind");
        String name = title(item);
        boolean isVideo = "video".equals(kind);
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


        // Poster frame with rounded outline clipping and crisp focus halo overlay.
        FrameLayout posterWrap = new FrameLayout(this);
        posterWrap.setClipChildren(false);
        posterWrap.setClipToPadding(false);

        final float radius = dpF(16);
        FrameLayout cover = new FrameLayout(this);
        CompatApi.roundClip(cover, radius);
        cover.setBackground(cardSurface());
        posterWrap.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (isDefault) {
            image.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.raw.wallpaper_default_preview));
        } else if (isShuffle) {
            // Covers are rendered ahead of time (tools/make_shuffle_covers.py) and ship with the app.
            image.setImageResource(shuffleCover(item.optString("scope", "all")));
        }

        // Subtle dark gradient at bottom of real photos/videos for badge legibility.
        if (!isShuffle) {
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

            // Bottom-right: whether this motion wallpaper is already on the TV.
            boolean downloaded = WallpaperDownloads.local(this, id, item.optString("path")) != null;
            View state = new WallpaperCardMarks.DownloadState(this, downloaded);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(24), dp(24),
                    Gravity.BOTTOM | Gravity.END);
            slp.setMargins(0, 0, dp(10), dp(10));
            cover.addView(state, slp);
            card.setContentDescription(name + "，" + copy(downloaded ? "已下载" : "未下载",
                    downloaded ? "downloaded" : "not downloaded"));
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

        // Crisp border overlay (idle subtle hairline -> focused luminous pure white ring).
        View ring = new View(this);
        ring.setBackground(borderDrawable(0x22FFFFFF, 0.75f, 16));
        posterWrap.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(posterWrap, new LinearLayout.LayoutParams(cardW, cardH));

        // Floating borderless caption beneath poster.
        TextView caption = new TextView(this);
        caption.setText(name);
        caption.setTextColor(0xB8DCE6F2);
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
                    ? borderDrawable(FOCUS_WHITE, 2.2f, 16)
                    : borderDrawable(0x22FFFFFF, 0.75f, 16));
            caption.setTextColor(focused ? 0xFFFFFFFF : 0xB8DCE6F2);
            caption.setTypeface(focused ? fontMedium : fontRegular);
            CompatApi.elevation(posterWrap, focused ? dpF(14) : 0f);
            springScale(card, focused ? 1.045f : 1f);
            if (focused) {
                countPill.setText((index + 1) + " / " + total);
                countPill.setTextColor(0xF0FFFFFF);
                countPill.setBackground(roundRect(0x24FFFFFF, 0x36FFFFFF, 11));
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

        card.setOnClickListener(v -> apply(item, kind, isDefault, cover));

        String preview = item.optString("preview");
        if (!preview.isEmpty() && !isDefault && !isShuffle) {
            worker.execute(() -> {
                try {
                    Bitmap cached = previewCache.get(preview);
                    if (cached != null) {
                        runOnUiThread(() -> { if (!isDestroyed()) image.setImageBitmap(cached); });
                        return;
                    }
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
                        if (bitmap != null) previewCache.put(preview, bitmap);
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

    private List<JSONObject> candidatesForScope(String scope) {
        if ("all".equals(scope) || scope == null || scope.isEmpty()) return allPlayable;
        List<JSONObject> list = playableByCategory.get(scope);
        return list == null ? new ArrayList<>() : list;
    }

    private void apply(JSONObject item, String kind, boolean fallback, FrameLayout cover) {
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
            List<JSONObject> pool = candidatesForScope(item.optString("scope", "all"));
            if (pool.isEmpty()) return;
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
        final String path = target.optString("path");

        // Already on the TV: apply it now, no network.
        String local = WallpaperDownloads.local(this, targetId, path);
        if (local != null) {
            saveActiveCatalogId(targetId);
            BundledWallpapers.select(this, local);
            finish();
            return;
        }

        applying = true;
        String label = shuffle ? copy("正在下载「" + title(target) + "」", "Downloading " + title(target))
                : copy("正在下载", "Downloading");
        WallpaperCardMarks.DownloadProgress progress = new WallpaperCardMarks.DownloadProgress(
                this, label, fontMedium, fontRegular, animateFocus);
        cover.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        worker.execute(() -> {
            String selection = null;
            try {
                if ("default".equals(targetId) && path.isEmpty()) {
                    selection = "classic";
                } else {
                    HttpURLConnection connection = TvCloud.open(assetUrl(path));
                    try {
                        long size = connection.getContentLength();
                        java.io.InputStream counted = new java.io.FilterInputStream(connection.getInputStream()) {
                            private long read;
                            private int reported = -1;

                            @Override public int read() throws IOException {
                                int b = super.read();
                                if (b >= 0) advance(1);
                                return b;
                            }

                            @Override public int read(byte[] buffer, int offset, int count) throws IOException {
                                int n = super.read(buffer, offset, count);
                                if (n > 0) advance(n);
                                return n;
                            }

                            private void advance(int n) {
                                read += n;
                                if (size <= 0) return;
                                int permille = (int) Math.min(1000, read * 1000 / size);
                                if (permille == reported) return;
                                reported = permille;
                                progress.post(() -> progress.setProgress(permille / 1000f));
                            }
                        };
                        selection = UserWallpaper.importDownload(getApplicationContext(), counted, targetIsVideo);
                        WallpaperDownloads.remember(getApplicationContext(), targetId, path, selection);
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
                if (applied == null) {
                    applying = false;
                    progress.animate().alpha(0f).setDuration(180)
                            .withEndAction(() -> cover.removeView(progress)).start();
                    showStatus(copy("下载失败，请重试", "Download failed. Try again."), true);
                    return;
                }
                progress.complete(() -> {
                    if (isDestroyed()) return;
                    applying = false;
                    saveActiveCatalogId(targetId);
                    BundledWallpapers.select(this, applied);
                    finish();
                });
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

    /** The smoked surface a card shows until its preview arrives. */
    private GradientDrawable cardSurface() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF161E2C, 0xFF0E131D});
        d.setCornerRadius(dpF(16));
        return d;
    }

    private static int shuffleCover(String scope) {
        switch (scope == null ? "" : scope) {
            case "pets": return R.drawable.wallpaper_shuffle_pets;
            case "nature": return R.drawable.wallpaper_shuffle_nature;
            case "dreamy": return R.drawable.wallpaper_shuffle_dreamy;
            case "images": return R.drawable.wallpaper_shuffle_images;
            default: return R.drawable.wallpaper_shuffle_all;
        }
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
