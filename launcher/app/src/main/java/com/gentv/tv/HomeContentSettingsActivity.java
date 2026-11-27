package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
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
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimalist Apple TV-style Gen Extensions gallery and local GHCP 1 permission manager.
 * Emphasizes pure 1.9:1 main-content previews, generous whitespace, floating typography beneath
 * each poster, and explicit reversible consent for installed extensions.
 */
public final class HomeContentSettingsActivity extends Activity {
    private static final String PLAY_TV_PACKAGE = "com.ai.playtv";
    private static final String GEN_VIDEO_ID = "gen-video";
    private static final int INK_DARK = 0xFF121418;
    private static final int INK_LIGHT = 0xFFF5F7FA;
    private static final int INK_SOFT = 0xD6FFFFFF;
    private static final int INK_MUTED = 0x7AFFFFFF;
    private static final int FOCUS_WHITE = 0xF7FFFFFF;
    private static final int STAR_GOLD = 0xFFF6C358;
    private static final int EMERALD = 0xFF4CD98B;

    private enum Tab {
        FEATURED, OFFICIAL, COMMUNITY, MEDIA, LIFE, MINE
    }

    private static final class StoreEntry {
        final String id;
        final String name;
        final String subtitle;
        final String description;
        final String author;
        final String tags;
        final String rating;
        final String ratingCount;
        final boolean official;
        final Tab category;
        final ExtensionPreviewView.Style previewStyle;
        final int iconRes;
        final int accentStart;
        final int accentEnd;
        final HomeContentRegistry.Extension installed;

        StoreEntry(String id, String name, String subtitle, String description, String author,
                   String tags, String rating, String ratingCount, boolean official, Tab category,
                   ExtensionPreviewView.Style previewStyle, int iconRes, int accentStart,
                   int accentEnd, HomeContentRegistry.Extension installed) {
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
            this.description = description;
            this.author = author;
            this.tags = tags;
            this.rating = rating;
            this.ratingCount = ratingCount;
            this.official = official;
            this.category = category;
            this.previewStyle = previewStyle;
            this.iconRes = iconRes;
            this.accentStart = accentStart;
            this.accentEnd = accentEnd;
            this.installed = installed;
        }
    }

    private boolean animateFocus;
    private boolean childBlocked;
    private Typeface fontMedium;
    private Typeface fontRegular;
    private LinearLayout content;
    private LinearLayout tabBar;
    private LinearLayout galleryHost;
    private TextView masterLabel;
    private View masterDot;
    private Tab activeTab = Tab.FEATURED;

    private final List<StoreEntry> catalog = new ArrayList<>();
    private final List<HomeContentRegistry.Extension> installedExtensions = new ArrayList<>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_gen_extensions);

        Config appearance = Config.load(this);
        animateFocus = appearance.animations && !appearance.reducedMotion;
        childBlocked = ChildLock.state(this).getBoolean(ChildSettingsActivity.ENABLED, false)
                || ChildLock.locked(this);
        fontMedium = UiFont.get(this, R.string.font_medium);
        fontRegular = UiFont.get(this, R.string.font_regular);

        findViewById(R.id.extRoot).setBackground(new AmbientBackdropDrawable());
        content = findViewById(R.id.extContent);

        seedCatalog();
        buildPageStructure();
        renderGallery(true);
        discoverInstalledExtensions();
    }

    private HomeContentRegistry.Extension findInstalledByPackage(String pkg) {
        for (HomeContentRegistry.Extension ext : installedExtensions) {
            if (pkg.equals(ext.activity.getPackageName())) return ext;
        }
        return null;
    }

    private void seedCatalog() {
        catalog.clear();
        catalog.add(new StoreEntry(
                "gen-weather",
                getString(R.string.ext_weather_name),
                getString(R.string.ext_weather_sub),
                getString(R.string.ext_weather_desc),
                getString(R.string.ext_weather_author),
                getString(R.string.ext_weather_tags),
                "4.9", "1.4k", true, Tab.LIFE,
                ExtensionPreviewView.Style.WEATHER,
                R.drawable.ic_ext_weather, 0xFF2B7CE9, 0xFF56CCF2,
                findInstalledByPackage("com.gentv.weather")));

        catalog.add(new StoreEntry(
                GEN_VIDEO_ID,
                getString(R.string.ext_video_name),
                getString(R.string.ext_video_sub),
                getString(R.string.ext_video_desc),
                getString(R.string.ext_video_author),
                getString(R.string.ext_video_tags),
                "", "", true, Tab.MEDIA,
                ExtensionPreviewView.Style.VIDEO,
                R.drawable.ic_ext_media, 0xFF7651D8, 0xFFE36D92,
                findInstalledByPackage(PLAY_TV_PACKAGE)));

        catalog.add(new StoreEntry(
                "continue-watching",
                getString(R.string.ext_continue_name),
                getString(R.string.ext_continue_sub),
                getString(R.string.ext_continue_desc),
                getString(R.string.ext_continue_author),
                getString(R.string.ext_continue_tags),
                "4.8", "960", false, Tab.MEDIA,
                ExtensionPreviewView.Style.CONTINUE,
                R.drawable.ic_ext_media, 0xFF5B63E8, 0xFF9C72F2, null));

        catalog.add(new StoreEntry(
                "daily-frame",
                getString(R.string.ext_daily_name),
                getString(R.string.ext_daily_sub),
                getString(R.string.ext_daily_desc),
                getString(R.string.ext_daily_author),
                getString(R.string.ext_daily_tags),
                "4.9", "1.1k", true, Tab.LIFE,
                ExtensionPreviewView.Style.DAILY,
                R.drawable.ic_qs_wallpaper, 0xFF2B6F8C, 0xFF72A7B8, null));

        catalog.add(new StoreEntry(
                "family-agenda",
                getString(R.string.ext_calendar_name),
                getString(R.string.ext_calendar_sub),
                getString(R.string.ext_calendar_desc),
                getString(R.string.ext_calendar_author),
                getString(R.string.ext_calendar_tags),
                "4.7", "520", false, Tab.LIFE,
                ExtensionPreviewView.Style.CALENDAR,
                R.drawable.ic_ext_calendar, 0xFFD95B7A, 0xFFF28B72, null));

        catalog.add(new StoreEntry(
                "standby-clock",
                getString(R.string.ext_clock_name),
                getString(R.string.ext_clock_sub),
                getString(R.string.ext_clock_desc),
                getString(R.string.ext_clock_author),
                getString(R.string.ext_clock_tags),
                "4.8", "780", false, Tab.MEDIA,
                ExtensionPreviewView.Style.CLOCK,
                R.drawable.ic_ext_clock, 0xFFC97B3A, 0xFFF0AD62, null));

        catalog.add(new StoreEntry(
                "home-climate",
                getString(R.string.ext_smart_name),
                getString(R.string.ext_smart_sub),
                getString(R.string.ext_smart_desc),
                getString(R.string.ext_smart_author),
                getString(R.string.ext_smart_tags),
                "4.6", "410", false, Tab.LIFE,
                ExtensionPreviewView.Style.SMART,
                R.drawable.ic_ext_smart, 0xFF1F8F75, 0xFF4CD98B, null));
    }

    private void discoverInstalledExtensions() {
        new Thread(() -> {
            List<HomeContentRegistry.Extension> found = HomeContentRegistry.all(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                installedExtensions.clear();
                installedExtensions.addAll(found);
                seedCatalog();
                rebuildTabBar();
                if (activeTab == Tab.MINE || !found.isEmpty()) {
                    renderGallery(false);
                }
            });
        }, "GenTV-content-discovery").start();
    }

    private void buildPageStructure() {
        content.removeAllViews();

        // Pure Apple TV header: quiet title on left, subtle borderless status toggle on right
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);

        TextView title = new TextView(this);
        title.setText(R.string.ext_store_title);
        title.setTextColor(0xFFF7F8FC);
        title.setTextSize(25f);
        title.setTypeface(fontMedium);
        CompatApi.letterSpacing(title, -0.025f);
        title.setSingleLine(true);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout masterPill = new LinearLayout(this);
        masterPill.setOrientation(LinearLayout.HORIZONTAL);
        masterPill.setGravity(Gravity.CENTER_VERTICAL);
        masterPill.setPadding(dp(14), 0, dp(15), 0);
        masterPill.setFocusable(true);
        masterPill.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(masterPill);
        masterPill.setBackground(pillSurface(0x14FFFFFF, 0, 17));

        masterDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.setMarginEnd(dp(8));
        masterPill.addView(masterDot, dotLp);

        masterLabel = new TextView(this);
        masterLabel.setTextSize(12f);
        masterLabel.setTypeface(fontMedium);
        masterLabel.setSingleLine(true);
        masterPill.addView(masterLabel);
        updateMasterVisual(false);

        masterPill.setOnFocusChangeListener((v, focused) -> {
            CompatApi.elevation(masterPill, focused ? dpF(8) : 0f);
            updateMasterVisual(focused);
            animateScale(masterPill, focused ? 1.04f : 1f);
        });
        masterPill.setOnClickListener(v -> {
            boolean next = !HomeContentRegistry.enabled(this);
            HomeContentRegistry.prefs(this).edit().putBoolean("enabled", next).apply();
            updateMasterVisual(masterPill.isFocused());
        });
        topBar.addView(masterPill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(33)));

        content.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Apple TV borderless category tab bar
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setClipChildren(false);
        tabBar.setClipToPadding(false);
        LinearLayout.LayoutParams tabBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabBarLp.topMargin = dp(14);
        content.addView(tabBar, tabBarLp);
        rebuildTabBar();

        // Child Lock notice pill when active
        if (childBlocked) {
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.HORIZONTAL);
            banner.setGravity(Gravity.CENTER_VERTICAL);
            banner.setPadding(dp(16), dp(10), dp(16), dp(10));
            banner.setBackground(roundRect(0x1CFFBC59, 0x38FFBC59, 14));
            TextView warn = new TextView(this);
            warn.setText(R.string.home_content_child_disabled);
            warn.setTextColor(0xFFFFD89B);
            warn.setTextSize(12f);
            warn.setTypeface(fontMedium);
            banner.addView(warn);
            LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bannerLp.topMargin = dp(14);
            content.addView(banner, bannerLp);
        }

        // Dynamic gallery host with generous top breathing space
        galleryHost = new LinearLayout(this);
        galleryHost.setOrientation(LinearLayout.VERTICAL);
        galleryHost.setClipChildren(false);
        galleryHost.setClipToPadding(false);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hostLp.topMargin = dp(22);
        content.addView(galleryHost, hostLp);
    }

    private void updateMasterVisual(boolean focused) {
        boolean on = HomeContentRegistry.enabled(this) && !childBlocked;
        masterLabel.setText(on ? R.string.ext_master_on : R.string.ext_master_off);
        masterLabel.setTextColor(focused ? INK_DARK : INK_SOFT);
        masterDot.setBackground(circleShape(on ? EMERALD : (focused ? 0x66121418 : 0x66FFFFFF), 0));
    }

    private void rebuildTabBar() {
        View focused = getCurrentFocus();
        Tab focusedTab = focused != null && focused.getTag() instanceof Tab ? (Tab) focused.getTag() : null;
        tabBar.removeAllViews();

        Tab[] tabs = {Tab.FEATURED, Tab.OFFICIAL, Tab.COMMUNITY, Tab.MEDIA, Tab.LIFE, Tab.MINE};
        View restore = null;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            TextView chip = new TextView(this);
            chip.setTag(tab);
            chip.setText(tabLabel(tab));
            chip.setTextSize(13f);
            chip.setTypeface(fontMedium);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            chip.setSingleLine(true);
            chip.setFocusable(true);
            chip.setClickable(true);
            CompatApi.disableDefaultFocusHighlight(chip);

            applyChipStyle(chip, tab == activeTab, false);

            chip.setOnFocusChangeListener((v, isFocused) -> {
                applyChipStyle(chip, tab == activeTab, isFocused);
                CompatApi.elevation(chip, isFocused ? dpF(8) : 0f);
                animateScale(chip, isFocused ? 1.05f : 1f);
            });
            chip.setOnClickListener(v -> {
                if (activeTab == tab) return;
                activeTab = tab;
                rebuildTabBar();
                renderGallery(false);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            if (i > 0) lp.setMarginStart(dp(6));
            tabBar.addView(chip, lp);
            if (tab == focusedTab) restore = chip;
        }
        if (restore != null) restore.requestFocus();
    }

    private String tabLabel(Tab tab) {
        switch (tab) {
            case FEATURED:
                return getString(R.string.ext_tab_featured);
            case OFFICIAL:
                return getString(R.string.ext_tab_official);
            case COMMUNITY:
                return getString(R.string.ext_tab_community);
            case MEDIA:
                return getString(R.string.ext_tab_media);
            case LIFE:
                return getString(R.string.ext_tab_life);
            case MINE:
                return getString(R.string.ext_tab_mine, installedExtensions.size());
        }
        return "";
    }

    /** Apple TV borderless pill aesthetic: quiet text when idle, subtle mist when selected, pure white on focus. */
    private void applyChipStyle(TextView chip, boolean selected, boolean focused) {
        if (focused) {
            chip.setBackground(roundRect(FOCUS_WHITE, 0, 16));
            chip.setTextColor(INK_DARK);
        } else if (selected) {
            chip.setBackground(roundRect(0x1FFFFFFF, 0, 16));
            chip.setTextColor(0xFFFFFFFF);
        } else {
            chip.setBackground(null);
            chip.setTextColor(0x88FFFFFF);
        }
    }

    private List<StoreEntry> installedAsEntries(boolean excludeCatalogLinked) {
        List<StoreEntry> list = new ArrayList<>();
        for (HomeContentRegistry.Extension ext : installedExtensions) {
            String pkg = ext.activity.getPackageName();
            boolean isWeather = "com.gentv.weather".equals(pkg);
            boolean isVideo = PLAY_TV_PACKAGE.equals(pkg);
            if (excludeCatalogLinked && (isWeather || isVideo)) continue;
            boolean allowed = HomeContentRegistry.allowed(this, ext);
            list.add(new StoreEntry(
                    ext.key(),
                    isVideo ? getString(R.string.ext_video_name) : ext.label,
                    pkg,
                    isVideo ? getString(R.string.ext_video_desc) : isWeather ? getString(R.string.ext_weather_desc) : getString(R.string.ext_meta_local_desc),
                    isVideo ? getString(R.string.ext_video_author) : isWeather ? getString(R.string.ext_weather_author) : pkg,
                    allowed ? getString(R.string.home_content_allowed) : getString(R.string.home_content_not_allowed),
                    "GHCP 1", "", isWeather || isVideo, Tab.MINE,
                    isVideo ? ExtensionPreviewView.Style.VIDEO : isWeather ? ExtensionPreviewView.Style.WEATHER : ExtensionPreviewView.Style.LOCAL,
                    isVideo ? R.drawable.ic_ext_media : isWeather ? R.drawable.ic_ext_weather : R.drawable.ic_qs_extensions,
                    isVideo ? 0xFF7651D8 : isWeather ? 0xFF2B7CE9 : 0xFF2B6CB0,
                    isVideo ? 0xFFE36D92 : isWeather ? 0xFF56CCF2 : 0xFF63B3ED, ext));
        }
        return list;
    }

    private void renderGallery(boolean focusFirstCard) {
        boolean hadGalleryFocus = focusFirstCard || galleryHost.hasFocus();
        galleryHost.removeAllViews();
        View firstFocusable = null;

        if (activeTab == Tab.FEATURED) {
            firstFocusable = addThreeColumnGrid(catalog);

            List<StoreEntry> locals = installedAsEntries(true);
            if (!locals.isEmpty()) {
                addSectionHeader(getString(R.string.ext_section_installed), String.valueOf(locals.size()));
                addThreeColumnGrid(locals);
            }
        } else if (activeTab == Tab.MINE) {
            List<StoreEntry> locals = installedAsEntries(false);
            if (locals.isEmpty()) {
                galleryHost.addView(buildEmptyInstalledCard());
            } else {
                firstFocusable = addThreeColumnGrid(locals);
            }
        } else {
            List<StoreEntry> filtered = new ArrayList<>();
            for (StoreEntry entry : catalog) {
                if (activeTab == Tab.OFFICIAL && entry.official) filtered.add(entry);
                else if (activeTab == Tab.COMMUNITY && !entry.official) filtered.add(entry);
                else if (activeTab == Tab.MEDIA && entry.category == Tab.MEDIA) filtered.add(entry);
                else if (activeTab == Tab.LIFE && entry.category == Tab.LIFE) filtered.add(entry);
            }
            firstFocusable = addThreeColumnGrid(filtered);
        }

        if (hadGalleryFocus && firstFocusable != null) {
            firstFocusable.requestFocus();
        }
    }

    private void addSectionHeader(String titleText, String countText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(28);
        lp.bottomMargin = dp(14);

        TextView heading = new TextView(this);
        heading.setText(titleText);
        heading.setTextColor(0xE8FFFFFF);
        heading.setTextSize(15.5f);
        heading.setTypeface(fontMedium);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(this);
        count.setText(countText);
        count.setTextColor(0x66FFFFFF);
        count.setTextSize(12f);
        count.setTypeface(fontRegular);
        row.addView(count);

        galleryHost.addView(row, lp);
    }

    private View addThreeColumnGrid(List<StoreEntry> items) {
        View first = null;
        int cols = 3;
        for (int i = 0; i < items.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = dp(22);

            for (int c = 0; c < cols; c++) {
                int idx = i + c;
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (c > 0) cellLp.setMarginStart(dp(24));
                if (idx < items.size()) {
                    View card = buildAppleTvCard(items.get(idx));
                    if (first == null) first = card;
                    row.addView(card, cellLp);
                } else {
                    row.addView(new View(this), cellLp);
                }
            }
            galleryHost.addView(row, rowLp);
        }
        return first;
    }

    /**
     * Pure Apple TV gallery card: uncluttered 1.9:1 floating main-content poster on top,
     * with generous, airy typography floating cleanly underneath (no heavy boxed footer).
     */
    private View buildAppleTvCard(StoreEntry entry) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setFocusable(true);
        cell.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(cell);

        // Floating 1.9:1 pure main-content preview poster
        final float radius = dpF(16);
        FrameLayout poster = new Aspect19x10Frame(this);
        CompatApi.roundClip(poster, radius);
        CompatApi.elevation(poster, dpF(4));
        poster.addView(new ExtensionPreviewView(this, entry.previewStyle, entry.name),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Subtle glass rim at rest, crisp luminous white rim on focus
        View idleRim = new View(this);
        idleRim.setBackground(strokeOnly(0x28FFFFFF, 16));
        poster.addView(idleRim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View focusRim = createFocusRim(16);
        poster.addView(focusRim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Only show a clean floating status pill for locally active installed extensions
        if (entry.installed != null && HomeContentRegistry.allowed(this, entry.installed)) {
            poster.addView(buildActiveStatusBadge(), badgeParams(Gravity.TOP | Gravity.END, 10, 10));
        }

        cell.addView(poster, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Airy floating text block beneath the poster (Apple TV+ style)
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(11);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(0xE8FFFFFF);
        name.setTextSize(14f);
        name.setTypeface(fontMedium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.setMarginEnd(dp(8));
        titleRow.addView(name, nameLp);

        if (!entry.rating.isEmpty()) titleRow.addView(buildRatingInline(entry.rating));
        caption.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String sourcePrefix = entry.installed != null
                ? getString(R.string.ext_badge_installed)
                : (entry.official ? getString(R.string.ext_badge_official) : getString(R.string.ext_badge_community));
        TextView meta = new TextView(this);
        meta.setText(String.format(java.util.Locale.getDefault(), "%s · %s", sourcePrefix, entry.subtitle));
        meta.setTextColor(INK_MUTED);
        meta.setTextSize(11.5f);
        meta.setTypeface(fontRegular);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(4);
        caption.addView(meta, metaLp);

        cell.addView(caption, capLp);

        cell.setOnFocusChangeListener((v, focused) -> {
            focusRim.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            idleRim.setVisibility(focused ? View.INVISIBLE : View.VISIBLE);
            CompatApi.elevation(poster, focused ? dpF(16) : dpF(4));
            name.setTextColor(focused ? 0xFFFFFFFF : 0xE8FFFFFF);
            meta.setTextColor(focused ? 0xB8FFFFFF : INK_MUTED);
            animateScale(poster, focused ? 1.045f : 1f);
            caption.animate().translationY(focused ? dpF(3f) : 0f)
                    .setDuration(animateFocus ? 160 : 0).start();
        });
        cell.setOnClickListener(v -> {
            if (GEN_VIDEO_ID.equals(entry.id)) openPlayTv();
            else showDetailDialog(entry);
        });
        return cell;
    }

    private void openPlayTv() {
        if (ChildLock.locked(this)) {
            finish();
            return;
        }
        Intent launch = CompatApi.leanbackLaunch(getPackageManager(), PLAY_TV_PACKAGE);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(PLAY_TV_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, R.string.ext_video_not_installed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private View buildEmptyInstalledCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(40), dp(44), dp(40), dp(44));
        card.setBackground(QuickSettingsGlass.surface(dpF(24), dpF(1), true));

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(circleShape(0x18FFFFFF, 0x22FFFFFF));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_qs_extensions);
        CompatApi.imageTint(icon, ColorStateList.valueOf(INK_LIGHT));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        card.addView(iconWrap, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView title = new TextView(this);
        title.setText(R.string.home_content_empty);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16f);
        title.setTypeface(fontMedium);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(16);
        card.addView(title, tLp);

        TextView hint = new TextView(this);
        hint.setText(R.string.ext_empty_mine_hint);
        hint.setTextColor(INK_MUTED);
        hint.setTextSize(13f);
        hint.setTypeface(fontRegular);
        hint.setLineSpacing(dpF(3), 1f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                dp(460), ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(10);
        card.addView(hint, hLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        card.setLayoutParams(lp);
        return card;
    }

    /** Airy Apple smoked-glass Extension Detail Sheet (left info & CTA, right large 1.9:1 preview). */
    private void showDetailDialog(StoreEntry entry) {
        View previousFocus = getCurrentFocus();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        sheet.setPadding(dp(30), dp(28), dp(30), dp(28));
        sheet.setBackground(QuickSettingsGlass.dialogSurface(dpF(28), dpF(1)));

        // Left column: identity, rating, description, tags, CTA buttons
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        left.setClipToPadding(false);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.addView(buildAppIconBadge(entry, 50, 13), new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.setMarginStart(dp(14));

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(20f);
        name.setTypeface(fontMedium);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(name);

        TextView author = new TextView(this);
        author.setText(entry.author);
        author.setTextColor(INK_MUTED);
        author.setTextSize(12.5f);
        author.setTypeface(fontRegular);
        author.setMaxLines(1);
        author.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(3);
        titleCol.addView(author, aLp);
        identity.addView(titleCol, tcLp);
        left.addView(identity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        ratingRow.addView(buildSourceBadge(entry));

        if (!entry.ratingCount.isEmpty()) {
            ImageView star = new ImageView(this);
            star.setImageResource(R.drawable.ic_ext_star);
            CompatApi.imageTint(star, ColorStateList.valueOf(STAR_GOLD));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(dp(13), dp(13));
            sLp.setMarginStart(dp(12));
            ratingRow.addView(star, sLp);

            TextView score = new TextView(this);
            score.setText(getString(R.string.ext_meta_ratings, entry.rating, entry.ratingCount));
            score.setTextColor(INK_SOFT);
            score.setTextSize(12f);
            score.setTypeface(fontMedium);
            LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scLp.setMarginStart(dp(5));
            ratingRow.addView(score, scLp);
        }
        LinearLayout.LayoutParams rrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rrLp.topMargin = dp(16);
        left.addView(ratingRow, rrLp);

        TextView desc = new TextView(this);
        desc.setText(entry.description);
        desc.setTextColor(0xD2FFFFFF);
        desc.setTextSize(13f);
        desc.setTypeface(fontRegular);
        desc.setLineSpacing(dpF(4), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(14);
        left.addView(desc, descLp);

        TextView tags = new TextView(this);
        tags.setText(entry.tags);
        tags.setTextColor(0x99FFFFFF);
        tags.setTextSize(11f);
        tags.setTypeface(fontMedium);
        tags.setPadding(dp(11), dp(6), dp(11), dp(6));
        tags.setBackground(roundRect(0x12FFFFFF, 0x18FFFFFF, 10));
        LinearLayout.LayoutParams tagsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagsLp.topMargin = dp(14);
        left.addView(tags, tagsLp);

        View flexSpacer = new View(this);
        left.addView(flexSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView primaryFocus;
        if (entry.installed != null) {
            HomeContentRegistry.Extension ext = entry.installed;
            boolean allowed = HomeContentRegistry.allowed(this, ext);
            TextView permBtn = dialogPillButton(
                    allowed ? R.string.ext_action_revoke : R.string.ext_action_allow, true);
            permBtn.setOnClickListener(v -> {
                if (childBlocked) {
                    Toast.makeText(this, R.string.home_content_child_disabled, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (allowed) {
                    HomeContentRegistry.allow(this, ext, false);
                    dialog.dismiss();
                    seedCatalog();
                    renderGallery(false);
                } else {
                    dialog.dismiss();
                    showConsentDialog(ext);
                }
            });
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            pLp.topMargin = dp(18);
            left.addView(permBtn, pLp);

            TextView openBtn = dialogPillButton(R.string.ext_action_open_app, false);
            openBtn.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN)
                            .setComponent(ext.activity)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            oLp.topMargin = dp(10);
            left.addView(openBtn, oLp);
            primaryFocus = permBtn;
        } else {
            TextView notice = new TextView(this);
            notice.setText(R.string.ext_catalog_notice);
            notice.setTextColor(0x88FFFFFF);
            notice.setTextSize(10.5f);
            notice.setTypeface(fontRegular);
            notice.setLineSpacing(dpF(2.5f), 1f);
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nLp.topMargin = dp(16);
            left.addView(notice, nLp);

            TextView closeBtn = dialogPillButton(R.string.ext_action_coming_soon, true);
            closeBtn.setOnClickListener(v -> dialog.dismiss());
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            cLp.topMargin = dp(14);
            left.addView(closeBtn, cLp);
            primaryFocus = closeBtn;
        }

        sheet.addView(left, new LinearLayout.LayoutParams(dp(272), ViewGroup.LayoutParams.MATCH_PARENT));

        // Right column: Large pure 1.9:1 main-content preview + 3-step onboarding strip
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(dp(26));

        FrameLayout bigPreview = new Aspect19x10Frame(this);
        final float previewR = dpF(18);
        CompatApi.roundClip(bigPreview, previewR);
        bigPreview.addView(new ExtensionPreviewView(this, entry.previewStyle, entry.name),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View bigBorder = new View(this);
        bigBorder.setBackground(strokeOnly(0x38FFFFFF, 18));
        bigPreview.addView(bigBorder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(bigPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView previewCaption = new TextView(this);
        previewCaption.setText(R.string.ext_preview_caption);
        previewCaption.setTextColor(0x78FFFFFF);
        previewCaption.setTextSize(11f);
        previewCaption.setTypeface(fontRegular);
        previewCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(12);
        right.addView(previewCaption, capLp);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.setGravity(Gravity.CENTER);
        steps.setPadding(dp(14), dp(11), dp(14), dp(11));
        steps.setBackground(roundRect(0x10FFFFFF, 0x16FFFFFF, 14));
        steps.addView(stepText(R.string.ext_step_install));
        steps.addView(stepArrow());
        steps.addView(stepText(R.string.ext_step_dock));
        steps.addView(stepArrow());
        steps.addView(stepText(R.string.ext_step_allow));
        LinearLayout.LayoutParams stepsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsLp.topMargin = dp(14);
        right.addView(steps, stepsLp);

        sheet.addView(right, rightLp);

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> {
            if (previousFocus != null) previousFocus.requestFocus();
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.58f);
            int available = getResources().getDisplayMetrics().widthPixels - dp(64);
            window.setLayout(Math.min(available, dp(756)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        primaryFocus.requestFocus();
    }

    /** Signer-bound GHCP 1 permission confirmation in matching Apple smoked-glass style. */
    private void showConsentDialog(HomeContentRegistry.Extension ext) {
        View previousFocus = getCurrentFocus();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setPadding(dp(28), dp(26), dp(28), dp(24));
        container.setBackground(QuickSettingsGlass.dialogSurface(dpF(26), dpF(1)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.home_content_allow_title, ext.label));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17.5f);
        title.setTypeface(fontMedium);
        title.setGravity(Gravity.CENTER);
        container.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.home_content_consent);
        body.setTextColor(0xC8FFFFFF);
        body.setTextSize(13f);
        body.setTypeface(fontRegular);
        body.setLineSpacing(dpF(3.5f), 1f);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(14);
        container.addView(body, bLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(22);

        TextView cancel = dialogPillButton(android.R.string.cancel, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cLp.setMarginEnd(dp(12));
        actions.addView(cancel, cLp);

        TextView allow = dialogPillButton(R.string.home_content_allow, true);
        allow.setOnClickListener(v -> {
            HomeContentRegistry.allow(this, ext, true);
            dialog.dismiss();
            seedCatalog();
            renderGallery(false);
        });
        actions.addView(allow, new LinearLayout.LayoutParams(0, dp(42), 1f));
        container.addView(actions, aLp);

        dialog.setContentView(container);
        dialog.setOnDismissListener(d -> {
            if (previousFocus != null) previousFocus.requestFocus();
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.58f);
            int available = getResources().getDisplayMetrics().widthPixels - dp(64);
            window.setLayout(Math.min(available, dp(428)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        allow.requestFocus();
    }

    private TextView stepText(int res) {
        TextView tv = new TextView(this);
        tv.setText(res);
        tv.setTextColor(INK_SOFT);
        tv.setTextSize(11f);
        tv.setTypeface(fontMedium);
        tv.setSingleLine(true);
        return tv;
    }

    private ImageView stepArrow() {
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_qs_chevron);
        CompatApi.imageTint(arrow, ColorStateList.valueOf(0x55FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(13), dp(13));
        lp.setMargins(dp(10), 0, dp(10), 0);
        arrow.setLayoutParams(lp);
        return arrow;
    }

    private TextView dialogPillButton(int labelRes, boolean emphasized) {
        TextView button = new TextView(this);
        button.setText(labelRes);
        button.setTextSize(13.5f);
        button.setTypeface(fontMedium);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setFocusable(true);
        button.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(button);
        button.setTextColor(INK_LIGHT);
        button.setBackground(pillSurface(emphasized ? 0x24FFFFFF : 0x12FFFFFF, 0x22FFFFFF, 21));
        button.setOnFocusChangeListener((v, focused) -> {
            button.setTextColor(focused ? INK_DARK : INK_LIGHT);
            CompatApi.elevation(button, focused ? dpF(8) : 0f);
            animateScale(button, focused ? 1.03f : 1f);
        });
        return button;
    }

    private View buildAppIconBadge(StoreEntry entry, int sizeDp, int radiusDp) {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{entry.accentStart, entry.accentEnd});
        bg.setCornerRadius(dpF(radiusDp));
        bg.setStroke(Math.max(1, Math.round(dpF(0.75f))), 0x38FFFFFF);
        box.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(entry.iconRes);
        CompatApi.imageTint(icon, ColorStateList.valueOf(0xFFFFFFFF));
        int glyphSize = Math.round(sizeDp * 0.54f);
        box.addView(icon, new FrameLayout.LayoutParams(dp(glyphSize), dp(glyphSize), Gravity.CENTER));
        return box;
    }

    private TextView buildSourceBadge(StoreEntry entry) {
        TextView badge = new TextView(this);
        if (entry.installed != null) {
            badge.setText(R.string.ext_badge_installed);
            badge.setBackground(roundRect(0x99123B2D, 0x554CD98B, 9));
            badge.setTextColor(0xFF9BF0C0);
        } else if (entry.official) {
            badge.setText(R.string.ext_badge_official);
            badge.setBackground(roundRect(0x9912325C, 0x556CC4FF, 9));
            badge.setTextColor(0xFFC9E9FF);
        } else {
            badge.setText(R.string.ext_badge_community);
            badge.setBackground(roundRect(0x99131722, 0x38FFFFFF, 9));
            badge.setTextColor(0xE0FFFFFF);
        }
        badge.setTextSize(10.5f);
        badge.setTypeface(fontMedium);
        badge.setPadding(dp(9), dp(4), dp(9), dp(4));
        badge.setSingleLine(true);
        return badge;
    }

    private View buildActiveStatusBadge() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(8), dp(3), dp(9), dp(3));
        pill.setBackground(roundRect(0xB30E261E, 0x664CD98B, 9));

        View dot = new View(this);
        dot.setBackground(circleShape(EMERALD, 0));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dLp.setMarginEnd(dp(5));
        pill.addView(dot, dLp);

        TextView label = new TextView(this);
        label.setText(R.string.ext_badge_enabled);
        label.setTextColor(0xFFB4F6D1);
        label.setTextSize(10f);
        label.setTypeface(fontMedium);
        pill.addView(label);
        return pill;
    }

    private View buildRatingInline(String scoreText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (!"GHCP 1".equals(scoreText)) {
            ImageView star = new ImageView(this);
            star.setImageResource(R.drawable.ic_ext_star);
            CompatApi.imageTint(star, ColorStateList.valueOf(STAR_GOLD));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(dp(11), dp(11));
            sLp.setMarginEnd(dp(4));
            row.addView(star, sLp);
        }

        TextView score = new TextView(this);
        score.setText(scoreText);
        score.setTextColor(0xD8FFFFFF);
        score.setTextSize(12f);
        score.setTypeface(fontMedium);
        score.setSingleLine(true);
        row.addView(score);
        return row;
    }

    private View createFocusRim(int radiusDp) {
        View rim = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x00000000);
        shape.setCornerRadius(dpF(radiusDp));
        shape.setStroke(Math.max(2, Math.round(dpF(2.2f))), FOCUS_WHITE);
        rim.setBackground(shape);
        rim.setVisibility(View.INVISIBLE);
        return rim;
    }

    private FrameLayout.LayoutParams badgeParams(int gravity, int marginH, int marginV) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(dp(marginH), dp(marginV), dp(marginH), dp(marginV));
        return lp;
    }


    private void animateScale(View view, float target) {
        if (!animateFocus) {
            view.setScaleX(target);
            view.setScaleY(target);
            return;
        }
        springAxis(view, DynamicAnimation.SCALE_X, R.id.extRoot, target);
        springAxis(view, DynamicAnimation.SCALE_Y, R.id.extContent, target);
    }

    private void springAxis(View view, DynamicAnimation.ViewProperty property, int tagKey, float target) {
        Object existing = view.getTag(tagKey);
        SpringAnimation anim;
        if (existing instanceof SpringAnimation) {
            anim = (SpringAnimation) existing;
        } else {
            anim = new SpringAnimation(view, property, target);
            anim.setSpring(new SpringForce(target).setDampingRatio(0.74f).setStiffness(480f));
            view.setTag(tagKey, anim);
        }
        anim.animateToFinalPosition(target);
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

    private GradientDrawable strokeOnly(int borderColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x00000000);
        shape.setCornerRadius(dpF(radiusDp));
        shape.setStroke(Math.max(1, Math.round(dpF(1f))), borderColor);
        return shape;
    }

    private GradientDrawable circleShape(int fillColor, int borderColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(fillColor);
        if (borderColor != 0) {
            shape.setStroke(Math.max(1, Math.round(dpF(0.75f))), borderColor);
        }
        return shape;
    }

    private StateListDrawable pillSurface(int idleFill, int idleBorder, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, roundRect(FOCUS_WHITE, 0, radiusDp));
        states.addState(new int[]{android.R.attr.state_pressed}, roundRect(0xDDFFFFFF, 0, radiusDp));
        states.addState(new int[]{}, roundRect(idleFill, idleBorder, radiusDp));
        return states;
    }

    /** Frame container locked to an Apple TV widescreen 1.9:1 main-content ratio (19:10). */
    private static final class Aspect19x10Frame extends FrameLayout {
        Aspect19x10Frame(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width > 0) {
                int height = Math.round(width * 10f / 19f);
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    /** Deep cinema-grade Apple obsidian backdrop with subtle ambient top-right sapphire glow. */
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
