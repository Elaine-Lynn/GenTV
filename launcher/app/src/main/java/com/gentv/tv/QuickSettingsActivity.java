package com.gentv.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
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

/**
 * Floating Apple tvOS-style Control Center panel opened from the top-right controls icon or the
 * remote's Settings button when {@code settings_key_panel} is enabled. Runs in a translucent
 * companion task so playback or Home behind it stays intact; Back or tapping outside dismisses it.
 */
public final class QuickSettingsActivity extends Activity {
    private static final int INK_DARK = 0xFF121418;
    private static final int INK_DARK_MUTED = 0x99121418;
    private static final int INK_LIGHT = 0xFFF5F7FA;
    private static final int INK_LIGHT_SOFT = 0xDBFFFFFF;
    private static final int INK_LIGHT_MUTED = 0xBDFFFFFF;
    private static final int INK_LIGHT_CAPTION = 0x70FFFFFF;
    private static final int FOCUS_WHITE = 0xF7FFFFFF;
    private static final int PRESSED_WHITE = 0xDDFFFFFF;

    private MemoryCleanupView memoryAnimation;
    private boolean animateFocus;
    private Typeface fontMedium;
    private Typeface fontRegular;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        Config appearance = Config.load(this);
        animateFocus = appearance.animations && !appearance.reducedMotion;
        fontMedium = UiFont.get(this, R.string.font_medium);
        fontRegular = UiFont.get(this, R.string.font_regular);

        View scrim = findViewById(R.id.scrim);
        scrim.setOnClickListener(v -> {
            if (memoryAnimation != null) memoryAnimation.close();
            else finish();
        });
        scrim.setFocusable(false);

        View glassPanel = findViewById(R.id.panel);
        glassPanel.setFocusable(false);
        CompatApi.disableDefaultFocusHighlight(glassPanel);
                final float panelRadius = dpF(30);
        CompatApi.roundClip(glassPanel, panelRadius);

        Bitmap wallpaperPreview = QuickSettingsGlass.takePreview();
        // True ~50% translucent Apple smoked glass directly over the live translucent window.
        glassPanel.setBackground(QuickSettingsGlass.surface(panelRadius, dpF(1), true));

        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            applyLeftEdgePosition(glassPanel);
        }

        if (animateFocus) {
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(200)
                    .setInterpolator(CompatApi.easeOut()).start();
            glassPanel.setAlpha(0f);
            glassPanel.setTranslationY(-dpF(8));
            glassPanel.setScaleX(0.97f);
            glassPanel.setScaleY(0.97f);
            glassPanel.post(() -> {
                boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
                glassPanel.setPivotX(rtl ? 0f : glassPanel.getWidth());
                glassPanel.setPivotY(0f);
                glassPanel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(CompatApi.easeOut())
                        .start();
            });
        }

        LinearLayout rows = findViewById(R.id.rows);

        // Editorial minimal header
        rows.addView(buildHeader());

        // Zone 1: 4 floating Apple Control Center circular medallions
        LinearLayout quickStrip = horizontalRow(rows, 14);
        Intent inputSource = SystemSettings.inputSourceIntent(this);
        View firstFocus = medallion(quickStrip, R.drawable.ic_qs_input, R.string.inputs,
                () -> launch(inputSource != null ? inputSource
                        : new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS)));
        medallion(quickStrip, R.drawable.ic_qs_wifi, R.string.quick_network,
                () -> launch(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
        medallion(quickStrip, R.drawable.ic_qs_display, R.string.quick_display,
                () -> launch(SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS)));
        medallion(quickStrip, R.drawable.ic_qs_memory, R.string.quick_memory, this::showMemory);

        // Zone 2: Wallpaper & screensaver hero tile with live thumbnail preview
        rows.addView(buildWallpaperHero(wallpaperPreview));

        // Zone 3: 2x2 core bento control tiles
        // Gen extensions take the wide top tile; GenTV settings sits beside TV settings below it.
        boolean extensions = HomeContentRegistry.supported();
        LinearLayout coreRow1 = horizontalRow(rows, 10);
        if (extensions) {
            bentoTile(coreRow1, R.drawable.ic_qs_extensions, R.string.quick_extensions,
                    () -> launch(new Intent(this, HomeContentSettingsActivity.class)));
        } else {
            bentoTile(coreRow1, R.drawable.ic_qs_sliders, R.string.quick_gentv_settings,
                    () -> openLauncher(null));
        }

        LinearLayout coreRow2 = horizontalRow(rows, 8);
        if (extensions) {
            bentoTile(coreRow2, R.drawable.ic_qs_sliders, R.string.quick_gentv_settings,
                    () -> openLauncher(null));
        }
        bentoTile(coreRow2, R.drawable.ic_qs_system, R.string.tv_settings,
                () -> launch(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));

        // Zone 4: Unified frosted utility plate (3x2 borderless grouped actions)
        rows.addView(buildUtilityPlate());

        firstFocus.requestFocus();
        if (state != null) {
            int selected = state.getInt("toolboxFocus", 0);
            rows.post(() -> {
                java.util.ArrayList<View> choices = rows.getFocusables(View.FOCUS_FORWARD);
                if (selected >= 0 && selected < choices.size()) choices.get(selected).requestFocus();
            });
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        ViewGroup rows = findViewById(R.id.rows);
        state.putInt("toolboxFocus", rows.getFocusables(View.FOCUS_FORWARD).indexOf(getCurrentFocus()));
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        // Home supplies the blurred live backdrop; keep this panel's smoked-glass surface.
        ToolboxBackdrop.visible(true);
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 701 && result == RESULT_OK && data != null
                && data.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(LauncherSettingsActivity.EXTRA_REORDER, true));
        }
    }

    @SuppressLint("RtlHardcoded")
    private void applyLeftEdgePosition(View glassPanel) {
        FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) glassPanel.getLayoutParams();
        position.gravity = Gravity.TOP | Gravity.LEFT;
        int inset = position.getMarginEnd();
        position.setMarginEnd(0);
        position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
        glassPanel.setLayoutParams(position);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(2), dp(6), dp(2));

        TextView title = new TextView(this);
        title.setText(R.string.desktop_control_center);
        title.setTextColor(INK_LIGHT);
        title.setTextSize(15.5f);
        title.setTypeface(fontMedium);
        CompatApi.letterSpacing(title, -0.01f);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView brand = new TextView(this);
        brand.setText(String.format(java.util.Locale.US, "%s  %s",
                getString(R.string.app_name), BuildConfig.VERSION_NAME));
        brand.setTextColor(INK_LIGHT_CAPTION);
        brand.setTextSize(11f);
        brand.setTypeface(fontRegular);
        CompatApi.letterSpacing(brand, 0.03f);
        brand.setSingleLine(true);
        header.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private LinearLayout horizontalRow(LinearLayout parent, int topMarginDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        parent.addView(row, params);
        return row;
    }

    /** Floating circular Apple Control Center medallion with caption underneath. */
    private View medallion(LinearLayout parent, int iconRes, int labelRes, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setId(View.generateViewId());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setPadding(dp(2), dp(2), dp(2), dp(2));
        cell.setFocusable(true);
        cell.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(cell);

        FrameLayout orb = new FrameLayout(this);
        GradientDrawable orbIdle = circleShape(0x1FFFFFFF, 0x2AFFFFFF);
        GradientDrawable orbFocused = circleShape(FOCUS_WHITE, 0x00FFFFFF);
        orb.setBackground(orbIdle);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        cell.addView(orb, orbParams);

        ImageView glyph = new ImageView(this);
        glyph.setImageResource(iconRes);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(INK_LIGHT));
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER);
        orb.addView(glyph, glyphParams);

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextColor(INK_LIGHT_MUTED);
        label.setTextSize(11f);
        label.setTypeface(fontMedium);
        CompatApi.letterSpacing(label, -0.01f);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(7);
        cell.addView(label, labelParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(6));
        parent.addView(cell, params);

        cell.setOnFocusChangeListener((view, focused) -> {
            orb.setBackground(focused ? orbFocused : orbIdle);
            CompatApi.elevation(orb, focused ? dpF(8) : 0f);
            CompatApi.imageTint(glyph, ColorStateList.valueOf(focused ? INK_DARK : INK_LIGHT));
            label.setTextColor(focused ? 0xFFFFFFFF : INK_LIGHT_MUTED);
            animateScale(orb, focused ? 1.10f : 1f);
            label.animate().translationY(focused ? dpF(1.5f) : 0f)
                    .setDuration(animateFocus ? 140 : 0).start();
        });
        cell.setOnClickListener(v -> { v.requestFocusFromTouch(); action.run(); });
        return cell;
    }

    /** Full-width Wallpaper & screensaver hero module with framed live wallpaper thumbnail. */
    private View buildWallpaperHero(Bitmap livePreview) {
        LinearLayout card = new LinearLayout(this);
        card.setId(View.generateViewId());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setFocusable(true);
        card.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(card);
        card.setBackground(pillSurface(0x18FFFFFF, 0x24FFFFFF, 16));

        FrameLayout badge = new FrameLayout(this);
        GradientDrawable badgeIdle = circleShape(0x18FFFFFF, 0x16FFFFFF);
        GradientDrawable badgeFocused = circleShape(0x12000000, 0x00000000);
        badge.setBackground(badgeIdle);
        card.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_qs_wallpaper);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(INK_LIGHT));
        badge.addView(glyph, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));

        TextView title = new TextView(this);
        title.setText(R.string.quick_wallpaper);
        title.setTextColor(INK_LIGHT);
        title.setTextSize(14f);
        title.setTypeface(fontMedium);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(12));
        titleParams.setMarginEnd(dp(10));
        card.addView(title, titleParams);

        // Miniature framed wallpaper preview pill (live wallpaper when opened from Home)
        FrameLayout thumbFrame = new FrameLayout(this);
        final float thumbRadius = dpF(9);
                CompatApi.roundClip(thumbFrame, thumbRadius);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap previewBitmap = livePreview != null ? livePreview : decodeFallbackWallpaper();
        if (previewBitmap != null) {
            thumb.setImageBitmap(previewBitmap);
        }
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View thumbBorder = new View(this);
        GradientDrawable thumbBorderIdle = strokeOnly(0x45FFFFFF, 9);
        GradientDrawable thumbBorderFocused = strokeOnly(0x28000000, 9);
        thumbBorder.setBackground(thumbBorderIdle);
        thumbFrame.addView(thumbBorder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(64), dp(38));
        thumbParams.setMarginEnd(dp(8));
        card.addView(thumbFrame, thumbParams);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_qs_chevron);
        CompatApi.imageTint(chevron, ColorStateList.valueOf(0x70FFFFFF));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(15), dp(15)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        params.topMargin = dp(14);
        card.setLayoutParams(params);

        card.setOnFocusChangeListener((view, focused) -> {
            CompatApi.elevation(view, focused ? dpF(8) : 0f);
            badge.setBackground(focused ? badgeFocused : badgeIdle);
            CompatApi.imageTint(glyph, ColorStateList.valueOf(focused ? INK_DARK : INK_LIGHT));
            title.setTextColor(focused ? INK_DARK : INK_LIGHT);
            CompatApi.imageTint(chevron, ColorStateList.valueOf(focused ? INK_DARK_MUTED : 0x70FFFFFF));
            thumbBorder.setBackground(focused ? thumbBorderFocused : thumbBorderIdle);
            animateScale(view, focused ? 1.022f : 1f);
        });
        card.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            launch(new Intent(this, WallpaperGalleryActivity.class));
        });
        return card;
    }

    /** 2-column core bento glass module (clean, chevron-free Apple Control Center style). */
    private void bentoTile(LinearLayout parent, int iconRes, int labelRes, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setId(View.generateViewId());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(8), dp(11), dp(8));
        card.setFocusable(true);
        card.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(card);
        card.setBackground(pillSurface(0x17FFFFFF, 0x1EFFFFFF, 15));

        ImageView glyph = new ImageView(this);
        glyph.setImageResource(iconRes);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(INK_LIGHT));
        card.addView(glyph, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView title = new TextView(this);
        title.setText(labelRes);
        title.setTextColor(INK_LIGHT);
        title.setTextSize(13.5f);
        title.setTypeface(fontMedium);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(10));
        card.addView(title, textParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(45), 1f);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(9));
        parent.addView(card, params);

        card.setOnFocusChangeListener((view, focused) -> {
            CompatApi.elevation(view, focused ? dpF(8) : 0f);
            CompatApi.imageTint(glyph, ColorStateList.valueOf(focused ? INK_DARK : INK_LIGHT));
            title.setTextColor(focused ? INK_DARK : INK_LIGHT);
            animateScale(view, focused ? 1.028f : 1f);
        });
        card.setOnClickListener(v -> { v.requestFocusFromTouch(); action.run(); });
    }

    /** Unified translucent grouped plate holding the 6 secondary system & utility actions. */
    private View buildUtilityPlate() {
        LinearLayout plate = new LinearLayout(this);
        plate.setOrientation(LinearLayout.VERTICAL);
        plate.setClipChildren(false);
        plate.setClipToPadding(false);
        plate.setBackground(roundRect(0x14FFFFFF, 0x1AFFFFFF, 18));
        plate.setPadding(dp(5), dp(5), dp(5), dp(5));

        LinearLayout.LayoutParams plateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plateParams.topMargin = dp(10);
        plate.setLayoutParams(plateParams);

        LinearLayout r1 = plateRow(plate);
        utilityItem(r1, R.drawable.ic_qs_startup, R.string.quick_start_app,
                () -> OpenOnStart.showPicker(this));
        utilityItem(r1, R.drawable.ic_qs_home, R.string.quick_default_home,
                () -> launch(new Intent(this, HomeSettingsActivity.class)));

        addHairlineDivider(plate);

        LinearLayout r2 = plateRow(plate);
        utilityItem(r2, R.drawable.ic_qs_shield, R.string.quick_child,
                () -> launch(new Intent(this, ChildSettingsActivity.class)));
        utilityItem(r2, R.drawable.ic_qs_share_code, R.string.quick_share_code,
                () -> {
                    startActivity(new Intent(this, ShareCodeActivity.class));
                    finish();
                });

        addHairlineDivider(plate);

        LinearLayout r3 = plateRow(plate);
        utilityItem(r3, R.drawable.ic_qs_update, R.string.cloud_settings,
                () -> launch(new Intent(this, CloudSettingsActivity.class)));
        utilityItem(r3, R.drawable.ic_qs_info, R.string.quick_about, this::showAbout);

        return plate;
    }

    private LinearLayout plateRow(LinearLayout plate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        plate.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addHairlineDivider(LinearLayout plate) {
        View divider = new View(this);
        divider.setBackgroundColor(0x12FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dpF(0.6f))));
        params.setMargins(dp(12), dp(2), dp(12), dp(2));
        plate.addView(divider, params);
    }

    private void utilityItem(LinearLayout row, int iconRes, int labelRes, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setId(View.generateViewId());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(6), dp(8), dp(6));
        item.setFocusable(true);
        item.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(item);
        item.setBackground(pillSurface(0x00000000, 0x00000000, 12));

        ImageView glyph = new ImageView(this);
        glyph.setImageResource(iconRes);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(0xCCFFFFFF));
        item.addView(glyph, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView title = new TextView(this);
        title.setText(labelRes);
        title.setTextColor(INK_LIGHT_SOFT);
        title.setTextSize(12.5f);
        title.setTypeface(fontRegular);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(9));
        item.addView(title, textParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        if (row.getChildCount() > 0) params.setMarginStart(dp(4));
        row.addView(item, params);

        item.setOnFocusChangeListener((view, focused) -> {
            CompatApi.elevation(view, focused ? dpF(6) : 0f);
            CompatApi.imageTint(glyph, ColorStateList.valueOf(focused ? INK_DARK : 0xCCFFFFFF));
            title.setTextColor(focused ? INK_DARK : INK_LIGHT_SOFT);
            title.setTypeface(focused ? fontMedium : fontRegular);
            animateScale(view, focused ? 1.024f : 1f);
        });
        item.setOnClickListener(v -> { v.requestFocusFromTouch(); action.run(); });
    }

    private void animateScale(View view, float target) {
        if (!animateFocus) {
            view.setScaleX(target);
            view.setScaleY(target);
            return;
        }
        springAxis(view, DynamicAnimation.SCALE_X, R.id.panel, target);
        springAxis(view, DynamicAnimation.SCALE_Y, R.id.rows, target);
    }

    private void springAxis(View view, DynamicAnimation.ViewProperty property, int tagKey, float target) {
        Object existing = view.getTag(tagKey);
        SpringAnimation anim;
        if (existing instanceof SpringAnimation) {
            anim = (SpringAnimation) existing;
        } else {
            anim = new SpringAnimation(view, property, target);
            SpringForce force = new SpringForce(target)
                    .setDampingRatio(0.74f)
                    .setStiffness(480f);
            anim.setSpring(force);
            view.setTag(tagKey, anim);
        }
        anim.animateToFinalPosition(target);
    }

    private Bitmap decodeFallbackWallpaper() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        return BitmapFactory.decodeResource(getResources(), R.raw.wallpaper_default, options);
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
        shape.setStroke(Math.max(1, Math.round(dpF(0.75f))), borderColor);
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
        states.addState(new int[]{android.R.attr.state_pressed}, roundRect(PRESSED_WHITE, 0, radiusDp));
        states.addState(new int[]{}, roundRect(idleFill, idleBorder, radiusDp));
        return states;
    }

    private void launch(Intent target) {
        try {
            startActivity(target);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void openLauncher(String focus) {
        startActivityForResult(new Intent(this, LauncherSettingsActivity.class)
                .putExtra("quick_settings_focus", focus), 701);
    }

    private void showMemory() {
        if (memoryAnimation != null || isFinishing()) return;
        startMemoryCleanup(getPreferences(MODE_PRIVATE).getInt("memory_adb_port", 0));
    }

    private void startMemoryCleanup(int port) {
        if (memoryAnimation != null) return;
        View previousFocus = getCurrentFocus();
        FrameLayout panel = findViewById(R.id.panel);
        ViewGroup panelScroll = findViewById(R.id.panelScroll);
        // A match-parent animation must not stretch the wrap-content glass panel.
        int previousPanelHeight = panel.getLayoutParams().height;
        if (panel.getHeight() > 0) {
            panel.getLayoutParams().height = panel.getHeight();
            panel.requestLayout();
        }
        android.animation.TimeInterpolator ease = CompatApi.easeOut();
        // The cleanup is a translucent layer over the panel: what was there stays visible underneath,
        // only its focus is held until the layer goes away.
        panelScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        memoryAnimation = new MemoryCleanupView(this, animateFocus, port, () -> {
            MemoryCleanupView layer = memoryAnimation;
            if (layer == null) return;
            if (!layer.succeeded()) getPreferences(MODE_PRIVATE).edit().remove("memory_adb_port").apply();
            memoryAnimation = null;
            layer.setFocusable(false);
            Runnable removeLayer = () -> {
                panel.removeView(layer);
                panel.getLayoutParams().height = previousPanelHeight;
                panel.requestLayout();
            };
            if (animateFocus) {
                layer.animate().cancel();
                layer.animate().alpha(0f).setDuration(200).setInterpolator(ease).withEndAction(removeLayer).start();
            } else removeLayer.run();
            panelScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (previousFocus != null) previousFocus.requestFocus();
        });
        panel.addView(memoryAnimation, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animateFocus) {
            memoryAnimation.setAlpha(0f);
            memoryAnimation.animate().alpha(1f).setDuration(200).setInterpolator(ease).start();
        }
        memoryAnimation.requestFocus();
    }

    @Override public void onBackPressed() {
        if (memoryAnimation != null) memoryAnimation.close();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (memoryAnimation != null) memoryAnimation.dispose();
        super.onDestroy();
    }

    @Override protected void onStop() {
        ToolboxBackdrop.visible(false);
        super.onStop();
    }



    private void showAbout() {
        showGlassDialog(
                R.drawable.ic_qs_info,
                R.string.quick_about,
                getString(R.string.quick_about_detail, BuildConfig.VERSION_NAME),
                android.R.string.ok,
                null,
                0
        );
    }

    /** Shared with the system Home setup prompts. */
    private void showGlassDialog(int iconRes, int titleRes, CharSequence message,
                                 int primaryRes, Runnable primaryAction, int secondaryRes) {
        View previousFocus = getCurrentFocus();
        Dialog dialog = GlassInfoDialog.create(this, iconRes, titleRes, message,
                primaryRes, primaryAction, secondaryRes, null);
        dialog.setOnDismissListener(d -> {
            if (previousFocus != null) previousFocus.requestFocus();
        });
        dialog.show();
    }
}
