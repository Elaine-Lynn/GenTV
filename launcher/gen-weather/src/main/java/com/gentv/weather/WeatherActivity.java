package com.gentv.weather;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gen Weather TV Activity — Apple tvOS flagship minimalist weather experience.
 *
 * Unified 6-tier DPI-independent 10-foot TV typography scale (normalized to 540u viewport height,
 * so 240dpi / 320dpi / 480dpi TVs all render identical crisp living-room proportions):
 *   1. TYPE_HERO      = 112u (224px @ 1080p) — Monumental current temperature
 *   2. TYPE_METRIC    =  28u ( 56px @ 1080p) — Primary atmospheric KPI numerals
 *   3. TYPE_TITLE     =  24u ( 48px @ 1080p) — Active city name & modal heading
 *   4. TYPE_BODY      =  18u ( 36px @ 1080p) — City tabs, hourly temps, 7-day rows, High/Low
 *   5. TYPE_SECONDARY =  15u ( 30px @ 1080p) — Insights, unit/time, KPI subtitles, modal controls
 *   6. TYPE_CAPTION   =  13u ( 26px @ 1080p) — Tracked kickers, hourly times, footer actions
 */
public final class WeatherActivity extends Activity {
    private static final long STALE_MS = 10 * 60 * 1000L;
    private static final long CITY_FOCUS_SWITCH_DELAY_MS = 220L;
    private static final int FOCUS_WHITE = 0xF7FFFFFF;
    private static final int INK_DARK = 0xFF0E1420;
    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator(1.8f);
    // Signature Apple tvOS silky ease-out curve (weightless takeoff, feather-soft landing)
    private static final PathInterpolator APPLE_GLIDE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    private static final long PAGE_GLIDE_MS = 540L;

    // Unified 6-tier Apple tvOS 10-foot type scale (in viewport-normalized units; 1u = 2px at 1080p)
    private static final float TYPE_HERO = 112f;
    private static final float TYPE_METRIC = 28f;
    private static final float TYPE_TITLE = 24f;
    private static final float TYPE_BODY = 18f;
    private static final float TYPE_SECONDARY = 15f;
    private static final float TYPE_CAPTION = 13f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Map<String, TextView> cityTabViews = new HashMap<>();

    private float uiScale;
    private int viewportW;
    private int viewportH;
    private boolean zh;
    private boolean useFahrenheit;
    private Typeface fontExtraLight;
    private Typeface fontLight;
    private Typeface fontRegular;
    private Typeface fontMedium;

    private WeatherCanvasViews.BackdropView backdropView;
    private ScrollView mainScroll;
    private LinearLayout pageOneView;
    private HorizontalScrollView cityScroll;
    private ValueAnimator pageGlideAnimator;
    private boolean pageGlideAnimating;
    private LinearLayout cityTabsRow;
    private TextView addCityButton;
    private TextView unitButton;
    private TextView localTimeText;

    private LinearLayout heroBlock;
    private TextView conditionKicker;
    private TextView heroTempText;
    private TextView heroCityText;
    private TextView heroHighLowText;
    private TextView heroInsightText;
    private TextView statusHintText;

    private WeatherCanvasViews.HourlyCurveView hourlyCurveView;
    private LinearLayout hourlyColumnsRow;

    private TextView aqiValueText;
    private View aqiDotView;
    private TextView aqiLabelText;
    private TextView windValueText;
    private TextView windSubText;
    private TextView sunsetValueText;
    private TextView sunsetSubText;

    private LinearLayout sevenDayTrigger;
    private ImageView sevenDayIcon;
    private TextView sevenDayLabel;
    private TextView refreshBtn;

    private LinearLayout forecastSection;
    private LinearLayout dailyRowsContainer;
    private TextView footnoteText;

    private WeatherModels.City activeCity;
    private WeatherModels.WeatherData activeData;
    private String currentPoeticInsight = "";
    private String lastCityListSignature = "";
    private boolean initialFocusApplied;
    private int requestSeq;
    private Runnable pendingCitySwitch;
    private Runnable pendingRefreshLabelReset;
    private int debugPreviewWmo = -1;
    private boolean debugPreviewDay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewportW = getResources().getDisplayMetrics().widthPixels;
        viewportH = getResources().getDisplayMetrics().heightPixels;
        // Viewport-normalized scale (540 design units high) ensures identical crisp 10-foot sizing across 240/320/480dpi TVs
        uiScale = Math.max(1f, Math.min(viewportW / 960f, viewportH / 540f));
        zh = WeatherModels.isChinese(getResources().getConfiguration().getLocales().get(0));
        useFahrenheit = WeatherStore.useFahrenheit(this);
        readDebugPreviewExtras(getIntent());
        loadFonts();
        buildUi();
        loadActiveCityAndRefresh(false, false);
        maybeAutoLocateFirstLaunch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readDebugPreviewExtras(intent);
        loadActiveCityAndRefresh(false, false);
    }

    private void readDebugPreviewExtras(Intent intent) {
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable || intent == null || !intent.hasExtra("debug_wmo")) {
            debugPreviewWmo = -1;
            return;
        }
        debugPreviewWmo = intent.getIntExtra("debug_wmo", -1);
        debugPreviewDay = intent.getBooleanExtra("debug_day", true);
    }

    @Override
    public void onBackPressed() {
        // Smart Back: if user scrolled into 7-Day Forecast, glide back to Page 1 instead of exiting.
        if (mainScroll != null && mainScroll.getScrollY() > u(32)) {
            returnToHeroTop(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (pageGlideAnimator != null) pageGlideAnimator.cancel();
        main.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        super.onDestroy();
    }

    private void loadFonts() {
        fontExtraLight = safeFont(R.font.outfit_extralight, "sans-serif-thin");
        fontLight = safeFont(R.font.outfit_light, "sans-serif-light");
        fontRegular = safeFont(R.font.outfit_regular, "sans-serif");
        fontMedium = safeFont(R.font.outfit_medium, "sans-serif-medium");
    }

    private Typeface safeFont(int resId, String fallback) {
        try {
            return getResources().getFont(resId);
        } catch (Exception ignored) {
            return Typeface.create(fallback, Typeface.NORMAL);
        }
    }

    private int u(float units) {
        return Math.round(units * uiScale);
    }

    private float uF(float units) {
        return units * uiScale;
    }

    private void setTvType(TextView tv, float tierUnits) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tierUnits * uiScale);
    }

    private void animateFocusScale(View v, boolean focused, float targetScale) {
        v.animate().cancel();
        v.animate()
                .scaleX(focused ? targetScale : 1f)
                .scaleY(focused ? targetScale : 1f)
                .setDuration(180)
                .setInterpolator(EASE_OUT)
                .start();
    }

    private void buildUi() {
        // Generous Apple TV safe-area breathing room consistent across all TV densities
        int padH = u(74);
        int padTop = u(36);
        int padBottom = u(30);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A1428);

        backdropView = new WeatherCanvasViews.BackdropView(this);
        root.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));

        mainScroll = new ScrollView(this) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                // Prevent Android's abrupt ScrollView focus-snap from fighting our silky Apple page glide.
                if (pageGlideAnimating) return true;
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mainScroll.setSmoothScrollingEnabled(true);
        mainScroll.setClipChildren(false);
        mainScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = mainScroll.getScrollY();
            float frac = Math.max(0f, Math.min(1f, scrollY / Math.max(1f, viewportH * 0.72f)));
            backdropView.setScrollDim(frac);
            if (pageOneView != null) {
                pageOneView.setAlpha(1f - frac * 0.42f);
                float s = 1f - frac * 0.018f;
                pageOneView.setScaleX(s);
                pageOneView.setScaleY(s);
            }
            if (forecastSection != null) {
                forecastSection.setAlpha(0.55f + frac * 0.45f);
            }
        });

        LinearLayout contentStack = new LinearLayout(this);
        contentStack.setOrientation(LinearLayout.VERTICAL);
        contentStack.setClipChildren(false);
        contentStack.setClipToPadding(false);

        // ============================================================
        // PAGE 1 — Seamless Full-Viewport Hero + Interactive Horizon Strip
        // ============================================================
        LinearLayout pageOne = new LinearLayout(this);
        pageOneView = pageOne;
        pageOne.setOrientation(LinearLayout.VERTICAL);
        pageOne.setPadding(padH, padTop, padH, padBottom);
        pageOne.setClipChildren(false);
        pageOne.setClipToPadding(false);
        pageOne.setLayoutParams(new LinearLayout.LayoutParams(-1, viewportH));

        // 1) Top borderless navigation bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);

        cityScroll = new HorizontalScrollView(this);
        cityScroll.setHorizontalScrollBarEnabled(false);
        cityScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cityScroll.setClipChildren(false);
        cityScroll.setClipToPadding(false);
        cityScroll.setPadding(u(2), u(4), u(10), u(4));
        cityTabsRow = new LinearLayout(this);
        cityTabsRow.setOrientation(LinearLayout.HORIZONTAL);
        cityTabsRow.setGravity(Gravity.CENTER_VERTICAL);
        cityTabsRow.setClipChildren(false);
        cityTabsRow.setClipToPadding(false);
        cityScroll.addView(cityTabsRow, new HorizontalScrollView.LayoutParams(-2, -2));
        topBar.addView(cityScroll, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout topRight = new LinearLayout(this);
        topRight.setOrientation(LinearLayout.HORIZONTAL);
        topRight.setGravity(Gravity.CENTER_VERTICAL);
        topRight.setClipChildren(false);
        topRight.setClipToPadding(false);
        topRight.setPadding(u(16), 0, 0, 0);

        unitButton = new TextView(this);
        unitButton.setId(View.generateViewId());
        setTvType(unitButton, TYPE_SECONDARY);
        unitButton.setTypeface(fontMedium);
        unitButton.setPadding(u(14), u(6), u(14), u(6));
        unitButton.setFocusable(true);
        unitButton.setFocusableInTouchMode(true);
        unitButton.setClickable(true);
        unitButton.setDefaultFocusHighlightEnabled(false);
        unitButton.setOnFocusChangeListener((v, f) -> {
            styleWhiteFocusPill(v, f, 16);
            updateUnitButtonLabel(f);
            animateFocusScale(v, f, 1.06f);
        });
        unitButton.setOnClickListener(v -> toggleUnit());
        updateUnitButtonLabel(false);
        topRight.addView(unitButton);

        TextView dotSep = new TextView(this);
        dotSep.setText("·");
        setTvType(dotSep, TYPE_SECONDARY);
        dotSep.setTextColor(0x52FFFFFF);
        dotSep.setPadding(u(8), 0, u(8), 0);
        topRight.addView(dotSep);

        localTimeText = new TextView(this);
        setTvType(localTimeText, TYPE_SECONDARY);
        localTimeText.setTypeface(fontRegular);
        localTimeText.setTextColor(0xA6FFFFFF);
        localTimeText.setText("—");
        topRight.addView(localTimeText);

        topBar.addView(topRight, new LinearLayout.LayoutParams(-2, -2));
        pageOne.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        // 2) Open-sky monumental hero block (floating left, leaving right 60%+ pure Apple sky)
        heroBlock = new LinearLayout(this);
        heroBlock.setOrientation(LinearLayout.VERTICAL);
        heroBlock.setGravity(Gravity.CENTER_VERTICAL);
        heroBlock.setPadding(0, u(2), 0, u(6));

        conditionKicker = new TextView(this);
        setTvType(conditionKicker, TYPE_CAPTION);
        conditionKicker.setTypeface(fontMedium);
        conditionKicker.setTextColor(0xC2FFFFFF);
        conditionKicker.setLetterSpacing(0.20f);
        conditionKicker.setAllCaps(true);
        conditionKicker.setText("—");
        heroBlock.addView(conditionKicker);

        heroTempText = new TextView(this);
        setTvType(heroTempText, TYPE_HERO);
        heroTempText.setTypeface(fontExtraLight);
        heroTempText.setTextColor(0xFCFFFFFF);
        heroTempText.setIncludeFontPadding(false);
        heroTempText.setShadowLayer(u(14), 0, u(3), 0x38061428);
        heroTempText.setText("—");
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(-2, -2);
        tempLp.topMargin = -u(6);
        heroBlock.addView(heroTempText, tempLp);

        WeatherCanvasViews.FadingRuleView heroRule = new WeatherCanvasViews.FadingRuleView(
                this, WeatherCanvasViews.FadingRuleView.HORIZONTAL_FADE_RIGHT, 0x42FFFFFF);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(u(156), Math.max(1, u(0.75f)));
        ruleLp.topMargin = u(6);
        ruleLp.bottomMargin = u(10);
        heroBlock.addView(heroRule, ruleLp);

        LinearLayout cityMetaRow = new LinearLayout(this);
        cityMetaRow.setOrientation(LinearLayout.HORIZONTAL);
        cityMetaRow.setGravity(Gravity.CENTER_VERTICAL);

        heroCityText = new TextView(this);
        setTvType(heroCityText, TYPE_TITLE);
        heroCityText.setTypeface(fontRegular);
        heroCityText.setTextColor(0xF8FFFFFF);
        heroCityText.setShadowLayer(u(6), 0, u(1), 0x38040A14);
        cityMetaRow.addView(heroCityText);

        heroHighLowText = new TextView(this);
        setTvType(heroHighLowText, TYPE_BODY);
        heroHighLowText.setTypeface(fontLight);
        heroHighLowText.setTextColor(0xC4FFFFFF);
        LinearLayout.LayoutParams hlLp = new LinearLayout.LayoutParams(-2, -2);
        hlLp.setMarginStart(u(18));
        cityMetaRow.addView(heroHighLowText, hlLp);
        heroBlock.addView(cityMetaRow);

        heroInsightText = new TextView(this);
        setTvType(heroInsightText, TYPE_SECONDARY);
        heroInsightText.setTypeface(fontLight);
        heroInsightText.setTextColor(0xB4FFFFFF);
        heroInsightText.setMaxLines(1);
        heroInsightText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams insLp = new LinearLayout.LayoutParams(u(540), -2);
        insLp.topMargin = u(6);
        heroBlock.addView(heroInsightText, insLp);

        statusHintText = new TextView(this);
        setTvType(statusHintText, TYPE_CAPTION);
        statusHintText.setTypeface(fontRegular);
        statusHintText.setTextColor(0xB0E0F0FF);
        statusHintText.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-2, -2);
        stLp.topMargin = u(4);
        heroBlock.addView(statusHintText, stLp);

        pageOne.addView(heroBlock, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 3) Single fading atmospheric hairline separating sky from lower horizon strip
        WeatherCanvasViews.FadingRuleView horizonLine = new WeatherCanvasViews.FadingRuleView(
                this, WeatherCanvasViews.FadingRuleView.HORIZONTAL_FADE_EDGES, 0x38FFFFFF);
        LinearLayout.LayoutParams hlRuleLp = new LinearLayout.LayoutParams(-1, Math.max(1, u(0.75f)));
        hlRuleLp.bottomMargin = u(12);
        pageOne.addView(horizonLine, hlRuleLp);

        // 4) Borderless lower horizon strip: Interactive Hourly Curve (left 54%) | 3 Unboxed Metrics (right 46%)
        LinearLayout horizonStrip = new LinearLayout(this);
        horizonStrip.setOrientation(LinearLayout.HORIZONTAL);
        horizonStrip.setGravity(Gravity.CENTER_VERTICAL);
        horizonStrip.setClipChildren(false);
        horizonStrip.setClipToPadding(false);

        FrameLayout hourlyContainer = new FrameLayout(this);
        hourlyContainer.setClipChildren(false);
        hourlyContainer.setClipToPadding(false);
        hourlyCurveView = new WeatherCanvasViews.HourlyCurveView(this);
        FrameLayout.LayoutParams curveLp = new FrameLayout.LayoutParams(-1, u(20));
        curveLp.gravity = Gravity.BOTTOM;
        curveLp.bottomMargin = u(31);
        hourlyContainer.addView(hourlyCurveView, curveLp);

        hourlyColumnsRow = new LinearLayout(this);
        hourlyColumnsRow.setOrientation(LinearLayout.HORIZONTAL);
        hourlyColumnsRow.setGravity(Gravity.CENTER_VERTICAL);
        hourlyColumnsRow.setClipChildren(false);
        hourlyColumnsRow.setClipToPadding(false);
        hourlyContainer.addView(hourlyColumnsRow, new FrameLayout.LayoutParams(-1, -2));

        horizonStrip.addView(hourlyContainer, new LinearLayout.LayoutParams(0, -2, 54f));

        WeatherCanvasViews.FadingRuleView vertDivider = new WeatherCanvasViews.FadingRuleView(
                this, WeatherCanvasViews.FadingRuleView.VERTICAL_FADE_EDGES, 0x32FFFFFF);
        LinearLayout.LayoutParams vdLp = new LinearLayout.LayoutParams(Math.max(1, u(0.75f)), u(74));
        vdLp.setMarginStart(u(16));
        vdLp.setMarginEnd(u(22));
        horizonStrip.addView(vertDivider, vdLp);

        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.setGravity(Gravity.CENTER_VERTICAL);

        // Metric 1: Air Quality
        LinearLayout aqiCol = buildMetricColumn(getString(R.string.air_quality));
        aqiValueText = metricValueText("—");
        aqiCol.addView(aqiValueText);
        LinearLayout aqiSubRow = new LinearLayout(this);
        aqiSubRow.setOrientation(LinearLayout.HORIZONTAL);
        aqiSubRow.setGravity(Gravity.CENTER_VERTICAL);
        aqiDotView = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF5EE39B);
        aqiDotView.setBackground(dotBg);
        LinearLayout.LayoutParams aqiDotLp = new LinearLayout.LayoutParams(u(6), u(6));
        aqiDotLp.setMarginEnd(u(6));
        aqiSubRow.addView(aqiDotView, aqiDotLp);
        aqiLabelText = metricSubText("—");
        aqiSubRow.addView(aqiLabelText);
        LinearLayout.LayoutParams aqiSubLp = new LinearLayout.LayoutParams(-2, -2);
        aqiSubLp.topMargin = u(4);
        aqiCol.addView(aqiSubRow, aqiSubLp);
        metricsRow.addView(aqiCol, new LinearLayout.LayoutParams(0, -2, 1f));

        // Metric 2: Wind
        LinearLayout windCol = buildMetricColumn(getString(R.string.wind));
        windValueText = metricValueText("—");
        windCol.addView(windValueText);
        windSubText = metricSubText("—");
        LinearLayout.LayoutParams wSubLp = new LinearLayout.LayoutParams(-2, -2);
        wSubLp.topMargin = u(4);
        windCol.addView(windSubText, wSubLp);
        metricsRow.addView(windCol, new LinearLayout.LayoutParams(0, -2, 1f));

        // Metric 3: Sunset
        LinearLayout sunsetCol = buildMetricColumn(getString(R.string.sunset));
        sunsetValueText = metricValueText("—");
        sunsetCol.addView(sunsetValueText);
        sunsetSubText = metricSubText("—");
        LinearLayout.LayoutParams sSubLp = new LinearLayout.LayoutParams(-2, -2);
        sSubLp.topMargin = u(4);
        sunsetCol.addView(sunsetSubText, sSubLp);
        metricsRow.addView(sunsetCol, new LinearLayout.LayoutParams(0, -2, 1f));

        horizonStrip.addView(metricsRow, new LinearLayout.LayoutParams(0, -2, 46f));
        pageOne.addView(horizonStrip, new LinearLayout.LayoutParams(-1, -2));

        // 5) Quiet footer: Synced indicator (left) | 7-Day Forecast trigger (center) | Refresh (right)
        FrameLayout footer = new FrameLayout(this);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = u(12);

        LinearLayout syncRow = new LinearLayout(this);
        syncRow.setOrientation(LinearLayout.HORIZONTAL);
        syncRow.setGravity(Gravity.CENTER_VERTICAL);
        View syncDot = new View(this);
        GradientDrawable syncDotBg = new GradientDrawable();
        syncDotBg.setShape(GradientDrawable.OVAL);
        syncDotBg.setColor(0xFF5EE39B);
        syncDot.setBackground(syncDotBg);
        LinearLayout.LayoutParams sdLp = new LinearLayout.LayoutParams(u(5.5f), u(5.5f));
        sdLp.setMarginEnd(u(7));
        syncRow.addView(syncDot, sdLp);
        TextView syncText = new TextView(this);
        syncText.setText(R.string.synced_to_home);
        setTvType(syncText, TYPE_CAPTION);
        syncText.setTypeface(fontRegular);
        syncText.setTextColor(0x8AFFFFFF);
        syncRow.addView(syncText);
        footer.addView(syncRow, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        sevenDayTrigger = new LinearLayout(this);
        sevenDayTrigger.setOrientation(LinearLayout.HORIZONTAL);
        sevenDayTrigger.setGravity(Gravity.CENTER_VERTICAL);
        sevenDayTrigger.setPadding(u(12), u(4), u(12), u(4));
        sevenDayTrigger.setFocusable(false);
        sevenDayTrigger.setFocusableInTouchMode(false);
        sevenDayTrigger.setClickable(false);
        sevenDayIcon = new ImageView(this);
        sevenDayIcon.setImageResource(R.drawable.ic_w_down);
        sevenDayIcon.setAlpha(0.72f);
        LinearLayout.LayoutParams diLp = new LinearLayout.LayoutParams(u(14), u(14));
        diLp.setMarginEnd(u(7));
        sevenDayTrigger.addView(sevenDayIcon, diLp);
        sevenDayLabel = new TextView(this);
        sevenDayLabel.setText(R.string.seven_day_forecast);
        setTvType(sevenDayLabel, TYPE_CAPTION);
        sevenDayLabel.setTypeface(fontRegular);
        sevenDayLabel.setTextColor(0x90FFFFFF);
        sevenDayTrigger.addView(sevenDayLabel);
        footer.addView(sevenDayTrigger, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        refreshBtn = new TextView(this);
        refreshBtn.setId(View.generateViewId());
        refreshBtn.setText(R.string.refresh);
        setTvType(refreshBtn, TYPE_CAPTION);
        refreshBtn.setTypeface(fontMedium);
        refreshBtn.setTextColor(0x9CFFFFFF);
        refreshBtn.setPadding(u(14), u(6), u(14), u(6));
        refreshBtn.setFocusable(true);
        refreshBtn.setFocusableInTouchMode(true);
        refreshBtn.setClickable(true);
        refreshBtn.setDefaultFocusHighlightEnabled(false);
        refreshBtn.setOnFocusChangeListener((v, f) -> {
            styleWhiteFocusPill(v, f, 16);
            refreshBtn.setTextColor(f ? INK_DARK : 0x9CFFFFFF);
            animateFocusScale(v, f, 1.06f);
        });
        refreshBtn.setOnClickListener(v -> triggerManualRefresh());
        refreshBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                scrollToForecast();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (unitButton != null) unitButton.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && hourlyColumnsRow.getChildCount() > 0) {
                hourlyColumnsRow.getChildAt(hourlyColumnsRow.getChildCount() - 1).requestFocus();
                return true;
            }
            return false;
        });
        footer.addView(refreshBtn, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));

        pageOne.addView(footer, footerLp);
        contentStack.addView(pageOne);

        // ============================================================
        // PAGE 2 — Below-the-Fold Seamless 7-Day Forecast
        // ============================================================
        forecastSection = new LinearLayout(this);
        forecastSection.setOrientation(LinearLayout.VERTICAL);
        forecastSection.setPadding(padH, padTop, padH, padBottom + u(14));
        forecastSection.setClipChildren(false);
        forecastSection.setClipToPadding(false);

        TextView forecastHeader = new TextView(this);
        forecastHeader.setText(R.string.seven_day_forecast);
        setTvType(forecastHeader, TYPE_CAPTION);
        forecastHeader.setTypeface(fontMedium);
        forecastHeader.setTextColor(0xB8FFFFFF);
        forecastHeader.setLetterSpacing(0.20f);
        forecastHeader.setAllCaps(true);
        forecastSection.addView(forecastHeader);

        WeatherCanvasViews.FadingRuleView forecastRule = new WeatherCanvasViews.FadingRuleView(
                this, WeatherCanvasViews.FadingRuleView.HORIZONTAL_FADE_EDGES, 0x30FFFFFF);
        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(-1, Math.max(1, u(0.75f)));
        frLp.topMargin = u(12);
        frLp.bottomMargin = u(8);
        forecastSection.addView(forecastRule, frLp);

        dailyRowsContainer = new LinearLayout(this);
        dailyRowsContainer.setOrientation(LinearLayout.VERTICAL);
        dailyRowsContainer.setClipChildren(false);
        dailyRowsContainer.setClipToPadding(false);
        forecastSection.addView(dailyRowsContainer, new LinearLayout.LayoutParams(-1, -2));

        footnoteText = new TextView(this);
        setTvType(footnoteText, TYPE_CAPTION);
        footnoteText.setTypeface(fontRegular);
        footnoteText.setTextColor(0x8AFFFFFF);
        LinearLayout.LayoutParams fnLp = new LinearLayout.LayoutParams(-2, -2);
        fnLp.topMargin = u(22);
        forecastSection.addView(footnoteText, fnLp);

        contentStack.addView(forecastSection, new LinearLayout.LayoutParams(-1, -2));
        mainScroll.addView(contentStack, new ScrollView.LayoutParams(-1, -2));
        root.addView(mainScroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private LinearLayout buildMetricColumn(String title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(title);
        setTvType(label, TYPE_CAPTION);
        label.setTypeface(fontMedium);
        label.setTextColor(0x94FFFFFF);
        label.setLetterSpacing(0.14f);
        label.setAllCaps(true);
        label.setMaxLines(1);
        col.addView(label);
        return col;
    }

    private TextView metricValueText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        setTvType(tv, TYPE_METRIC);
        tv.setTypeface(fontLight);
        tv.setTextColor(0xFCFFFFFF);
        tv.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = u(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView metricSubText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        setTvType(tv, TYPE_SECONDARY);
        tv.setTypeface(fontRegular);
        tv.setTextColor(0xB4FFFFFF);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private void glideScrollTo(int targetY, Runnable focusAction) {
        if (pageGlideAnimator != null) {
            pageGlideAnimator.cancel();
            pageGlideAnimator = null;
        }
        int startY = mainScroll.getScrollY();
        pageGlideAnimating = true;
        if (focusAction != null) focusAction.run();

        if (Math.abs(targetY - startY) < 2) {
            mainScroll.scrollTo(0, targetY);
            pageGlideAnimating = false;
            return;
        }

        pageGlideAnimator = ValueAnimator.ofInt(startY, targetY);
        pageGlideAnimator.setDuration(PAGE_GLIDE_MS);
        pageGlideAnimator.setInterpolator(APPLE_GLIDE);
        pageGlideAnimator.addUpdateListener(anim -> mainScroll.scrollTo(0, (int) anim.getAnimatedValue()));
        pageGlideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pageGlideAnimating = false;
                if (pageGlideAnimator == animation) pageGlideAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                pageGlideAnimating = false;
                if (pageGlideAnimator == animation) pageGlideAnimator = null;
            }
        });
        pageGlideAnimator.start();
    }

    private void scrollToForecast() {
        glideScrollTo(forecastSection.getTop(), () -> {
            if (dailyRowsContainer.getChildCount() > 0) {
                dailyRowsContainer.getChildAt(0).requestFocus();
            }
        });
    }

    private void returnToHeroTop(boolean focusActiveCity) {
        glideScrollTo(0, () -> {
            if (focusActiveCity) {
                focusActiveCityTab();
            } else {
                focusFirstHourlyOrActiveCity();
            }
        });
    }

    private void focusActiveCityTab() {
        if (activeCity != null) {
            TextView tab = cityTabViews.get(activeCity.id);
            if (tab != null) {
                tab.requestFocus();
                return;
            }
        }
        if (cityTabsRow.getChildCount() > 0) {
            cityTabsRow.getChildAt(0).requestFocus();
        }
    }

    private void focusFirstHourlyOrActiveCity() {
        if (hourlyColumnsRow.getChildCount() > 0) {
            hourlyColumnsRow.getChildAt(0).requestFocus();
        } else {
            focusActiveCityTab();
        }
    }

    private void focusDownFromTopBar() {
        if (hourlyColumnsRow.getChildCount() > 0) {
            hourlyColumnsRow.getChildAt(0).requestFocus();
        } else {
            scrollToForecast();
        }
    }

    private String cityListSignature(List<WeatherModels.City> cities) {
        StringBuilder sb = new StringBuilder();
        for (WeatherModels.City c : cities) {
            sb.append(c.id).append(':').append(c.name).append(':').append(c.autoLocated).append('|');
        }
        return sb.toString();
    }

    private void syncCityTabs(boolean focusActiveAfterRebuild) {
        List<WeatherModels.City> cities = WeatherStore.cities(this);
        String sig = cityListSignature(cities);
        if (sig.equals(lastCityListSignature) && !cityTabViews.isEmpty()) {
            refreshCityTabVisuals();
            if (focusActiveAfterRebuild || !initialFocusApplied) {
                initialFocusApplied = true;
                focusActiveCityTab();
            }
            return;
        }

        lastCityListSignature = sig;
        cityTabsRow.removeAllViews();
        cityTabViews.clear();
        TextView activeTabView = null;

        for (int i = 0; i < cities.size(); i++) {
            final WeatherModels.City city = cities.get(i);
            boolean isActive = activeCity != null && city.id.equals(activeCity.id);
            TextView tab = new TextView(this);
            tab.setId(View.generateViewId());
            tab.setText(city.autoLocated ? ("⌖ " + city.name) : city.name);
            setTvType(tab, TYPE_BODY);
            tab.setTypeface(isActive ? fontMedium : fontRegular);
            tab.setPadding(u(15), u(6), u(15), u(8));
            tab.setFocusable(true);
            tab.setFocusableInTouchMode(true);
            tab.setClickable(true);
            tab.setDefaultFocusHighlightEnabled(false);
            styleCityTab(tab, isActive, false);

            tab.setOnFocusChangeListener((v, focused) -> {
                boolean currentActive = activeCity != null && city.id.equals(activeCity.id);
                styleCityTab((TextView) v, currentActive, focused);
                animateFocusScale(v, focused, 1.06f);
                if (focused) {
                    centerHorizontalTab(v);
                    scheduleCitySwitchOnFocus(city);
                }
            });
            tab.setOnClickListener(v -> {
                cancelPendingCitySwitch();
                if (activeCity == null || !city.id.equals(activeCity.id)) {
                    WeatherStore.setActiveCity(this, city.id);
                    loadActiveCityAndRefresh(false, true);
                } else {
                    triggerManualRefresh();
                }
            });
            tab.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                    focusDownFromTopBar();
                    return true;
                }
                return false;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(u(12));
            cityTabsRow.addView(tab, lp);
            cityTabViews.put(city.id, tab);
            if (isActive) activeTabView = tab;
        }

        // Minimalist "+" global city manager trigger
        addCityButton = new TextView(this);
        addCityButton.setId(View.generateViewId());
        addCityButton.setText("+");
        setTvType(addCityButton, TYPE_TITLE);
        addCityButton.setTypeface(fontLight);
        addCityButton.setTextColor(0xA4FFFFFF);
        addCityButton.setPadding(u(14), u(2), u(14), u(5));
        addCityButton.setFocusable(true);
        addCityButton.setFocusableInTouchMode(true);
        addCityButton.setClickable(true);
        addCityButton.setDefaultFocusHighlightEnabled(false);
        addCityButton.setContentDescription(getString(R.string.manage_cities));
        addCityButton.setNextFocusRightId(unitButton.getId());
        unitButton.setNextFocusLeftId(addCityButton.getId());
        addCityButton.setOnFocusChangeListener((v, focused) -> {
            if (focused) cancelPendingCitySwitch();
            styleWhiteFocusPill(v, focused, 16);
            ((TextView) v).setTextColor(focused ? INK_DARK : 0xA4FFFFFF);
            animateFocusScale(v, focused, 1.08f);
        });
        addCityButton.setOnClickListener(v -> showCityDialog());
        addCityButton.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                focusDownFromTopBar();
                return true;
            }
            return false;
        });
        unitButton.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                focusDownFromTopBar();
                return true;
            }
            return false;
        });
        cityTabsRow.addView(addCityButton);

        if ((!initialFocusApplied || focusActiveAfterRebuild || getCurrentFocus() == null) && activeTabView != null) {
            initialFocusApplied = true;
            activeTabView.requestFocus();
        }
    }

    private void refreshCityTabVisuals() {
        for (Map.Entry<String, TextView> entry : cityTabViews.entrySet()) {
            boolean isActive = activeCity != null && entry.getKey().equals(activeCity.id);
            TextView tab = entry.getValue();
            tab.setTypeface(isActive ? fontMedium : fontRegular);
            styleCityTab(tab, isActive, tab.isFocused());
        }
    }

    private void centerHorizontalTab(View tab) {
        if (cityScroll == null) return;
        int targetX = tab.getLeft() - (cityScroll.getWidth() - tab.getWidth()) / 2;
        cityScroll.smoothScrollTo(Math.max(0, targetX), 0);
    }

    private void scheduleCitySwitchOnFocus(WeatherModels.City city) {
        cancelPendingCitySwitch();
        if (activeCity != null && city.id.equals(activeCity.id)) return;
        pendingCitySwitch = () -> {
            if (isFinishing() || isDestroyed()) return;
            if (activeCity != null && city.id.equals(activeCity.id)) return;
            WeatherStore.setActiveCity(this, city.id);
            loadActiveCityAndRefresh(false, true);
        };
        main.postDelayed(pendingCitySwitch, CITY_FOCUS_SWITCH_DELAY_MS);
    }

    private void cancelPendingCitySwitch() {
        if (pendingCitySwitch != null) {
            main.removeCallbacks(pendingCitySwitch);
            pendingCitySwitch = null;
        }
    }

    private void styleCityTab(TextView tab, boolean active, boolean focused) {
        if (focused) {
            styleWhiteFocusPill(tab, true, 18);
            tab.setTextColor(INK_DARK);
            return;
        }
        tab.setElevation(0f);
        if (active) {
            GradientDrawable underline = new GradientDrawable();
            underline.setColor(0xF0FFFFFF);
            LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{underline});
            layers.setLayerGravity(0, Gravity.BOTTOM);
            layers.setLayerHeight(0, Math.max(2, u(1.75f)));
            layers.setLayerInsetStart(0, u(15));
            layers.setLayerInsetEnd(0, u(15));
            tab.setBackground(layers);
            tab.setTextColor(0xFCFFFFFF);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0x94FFFFFF);
        }
    }

    private void styleWhiteFocusPill(View view, boolean focused, float radiusU) {
        if (focused) {
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(FOCUS_WHITE);
            pill.setCornerRadius(u(radiusU));
            view.setBackground(pill);
            view.setElevation(uF(8));
        } else {
            view.setBackground(null);
            view.setElevation(0f);
        }
    }

    private void styleFrostedFocusSurface(View view, boolean focused, float radiusU) {
        if (focused) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(0x2AFFFFFF);
            d.setCornerRadius(u(radiusU));
            d.setStroke(Math.max(1, u(1f)), 0x70FFFFFF);
            view.setBackground(d);
            view.setElevation(uF(4));
        } else {
            view.setBackground(null);
            view.setElevation(0f);
        }
    }

    private void updateUnitButtonLabel(boolean focused) {
        String text = "°C / °F";
        SpannableString span = new SpannableString(text);
        int bright = focused ? INK_DARK : 0xF8FFFFFF;
        int dim = focused ? 0x620E1420 : 0x72FFFFFF;
        int slash = focused ? 0x450E1420 : 0x52FFFFFF;
        span.setSpan(new ForegroundColorSpan(useFahrenheit ? dim : bright), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(slash), 2, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(useFahrenheit ? bright : dim), 5, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        unitButton.setText(span);
    }

    private void toggleUnit() {
        useFahrenheit = !useFahrenheit;
        WeatherStore.setUseFahrenheit(this, useFahrenheit);
        updateUnitButtonLabel(unitButton.isFocused());
        if (activeCity != null && activeData != null) {
            bindWeather(activeCity, activeData, false);
            heroTempText.setScaleX(0.94f);
            heroTempText.setScaleY(0.94f);
            heroTempText.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(EASE_OUT).start();
        }
    }

    private void triggerManualRefresh() {
        if (pendingRefreshLabelReset != null) main.removeCallbacks(pendingRefreshLabelReset);
        refreshBtn.setText(R.string.refreshing);
        refreshBtn.animate().alpha(0.55f).setDuration(180).start();
        loadActiveCityAndRefresh(true, false);
    }

    private void showRefreshCompletedFeedback() {
        if (refreshBtn == null) return;
        refreshBtn.animate().alpha(1f).setDuration(180).start();
        String nowClock = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        refreshBtn.setText(getString(R.string.updated_at, nowClock));
        if (pendingRefreshLabelReset != null) main.removeCallbacks(pendingRefreshLabelReset);
        pendingRefreshLabelReset = () -> {
            if (!isFinishing() && !isDestroyed() && refreshBtn != null) {
                refreshBtn.setText(R.string.refresh);
            }
        };
        main.postDelayed(pendingRefreshLabelReset, 2400L);
    }

    private void maybeAutoLocateFirstLaunch() {
        if (WeatherStore.hasAutoLocatedOnce(this)) return;
        WeatherStore.markAutoLocated(this);
        String lang = zh ? "zh" : "en";
        pool.execute(() -> {
            WeatherModels.City detected = WeatherApiClient.locateByIp(lang);
            if (detected == null) return;
            WeatherStore.addOrPromoteCity(getApplicationContext(), detected, true);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                loadActiveCityAndRefresh(true, true);
            });
        });
    }

    private void showLoadingPlaceholderForCity(WeatherModels.City city) {
        backdropView.resetForCityLoading(city.id);
        activeData = null;
        currentPoeticInsight = "";
        conditionKicker.setText("—");
        heroTempText.setText("—");
        heroCityText.setText(city.autoLocated ? ("⌖ " + city.name) : city.name);
        heroHighLowText.setText("");
        heroInsightText.setText("");
        localTimeText.setText("—");
        hourlyColumnsRow.removeAllViews();
        hourlyCurveView.setTemperatures(new double[0]);
        hourlyCurveView.setSelectedIndex(-1);
        aqiValueText.setText("—");
        aqiLabelText.setText("—");
        windValueText.setText("—");
        windSubText.setText("—");
        sunsetValueText.setText("—");
        sunsetSubText.setText("—");
        dailyRowsContainer.removeAllViews();
        footnoteText.setText("");
        statusHintText.setText(R.string.loading);
        statusHintText.setVisibility(View.VISIBLE);
    }

    private void loadActiveCityAndRefresh(boolean forceNetwork, boolean animateTransition) {
        activeCity = WeatherStore.activeCity(this);
        syncCityTabs(false);

        WeatherModels.WeatherData cached = WeatherStore.cachedWeather(this, activeCity.id);
        if (cached != null) {
            activeData = cached;
            bindWeather(activeCity, cached, animateTransition);
            statusHintText.setVisibility(View.INVISIBLE);
        } else {
            showLoadingPlaceholderForCity(activeCity);
        }

        boolean needsFetch = forceNetwork
                || cached == null
                || (System.currentTimeMillis() - cached.fetchedAtMs > STALE_MS);
        if (!needsFetch) {
            if (refreshBtn != null) refreshBtn.animate().alpha(1f).setDuration(120).start();
            return;
        }

        final int seq = ++requestSeq;
        final WeatherModels.City target = activeCity;
        final boolean hadCache = cached != null;

        pool.execute(() -> {
            try {
                WeatherModels.WeatherData fresh = WeatherApiClient.fetchWeather(target);
                WeatherStore.saveWeather(getApplicationContext(), fresh);
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || seq != requestSeq) return;
                    activeData = fresh;
                    bindWeather(target, fresh, !hadCache);
                    statusHintText.setVisibility(View.INVISIBLE);
                    if (forceNetwork) showRefreshCompletedFeedback();
                });
            } catch (Exception err) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || seq != requestSeq) return;
                    if (refreshBtn != null) {
                        refreshBtn.animate().alpha(1f).setDuration(150).start();
                        refreshBtn.setText(R.string.refresh);
                    }
                    statusHintText.setText(hadCache ? R.string.offline_cached : R.string.offline_error);
                    statusHintText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void bindWeather(WeatherModels.City city, WeatherModels.WeatherData d, boolean animateEntrance) {
        int displayWmo = debugPreviewWmo >= 0 ? debugPreviewWmo : d.weatherCode;
        boolean displayDay = debugPreviewWmo >= 0 ? debugPreviewDay : d.isDay;
        backdropView.setCityWeather(city.id, displayWmo, displayDay);

        if (animateEntrance && heroBlock != null) {
            heroBlock.animate().cancel();
            heroBlock.setAlpha(0.35f);
            heroBlock.setTranslationY(uF(7));
            heroBlock.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(EASE_OUT).start();
        }

        String timeDisplay = d.localTime.isEmpty() ? "—" : d.localTime;
        if (!d.tzAbbr.isEmpty()) timeDisplay = timeDisplay + " " + d.tzAbbr;
        localTimeText.setText(timeDisplay);

        conditionKicker.setText(WeatherModels.conditionLabel(displayWmo, displayDay, zh));
        heroTempText.setText(WeatherModels.formatTemp(d.tempC, useFahrenheit));
        heroCityText.setText(city.autoLocated ? ("⌖ " + city.name) : city.name);
        heroHighLowText.setText(getString(R.string.high_low,
                WeatherModels.formatTemp(d.highC, useFahrenheit),
                WeatherModels.formatTemp(d.lowC, useFahrenheit)));
        currentPoeticInsight = WeatherModels.poeticInsight(d, zh);
        heroInsightText.setText(currentPoeticInsight);
        heroInsightText.setTextColor(0xB4FFFFFF);

        // Preserve hourly focus index across background refreshes
        int prevHourlyFocus = -1;
        for (int i = 0; i < hourlyColumnsRow.getChildCount(); i++) {
            if (hourlyColumnsRow.getChildAt(i).isFocused()) {
                prevHourlyFocus = i;
                break;
            }
        }

        // Interactive hourly strip & curve
        hourlyColumnsRow.removeAllViews();
        hourlyCurveView.setSelectedIndex(-1);
        int hCount = Math.min(5, d.hourly.size());
        double[] curveTemps = new double[hCount];
        View[] slotViews = new View[hCount];
        for (int i = 0; i < hCount; i++) {
            final int slotIndex = i;
            final WeatherModels.HourlyPoint pt = d.hourly.get(i);
            curveTemps[i] = pt.tempC;

            LinearLayout col = new LinearLayout(this);
            col.setId(View.generateViewId());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(u(6), u(7), u(6), u(7));
            col.setFocusable(true);
            col.setFocusableInTouchMode(true);
            col.setClickable(true);
            col.setDefaultFocusHighlightEnabled(false);

            final String displayTime = "NOW".equals(pt.timeLabel) ? getString(R.string.now) : pt.timeLabel;
            TextView tLabel = new TextView(this);
            tLabel.setText(displayTime);
            setTvType(tLabel, TYPE_CAPTION);
            tLabel.setTypeface(i == 0 ? fontMedium : fontRegular);
            tLabel.setTextColor(i == 0 ? 0xF8FFFFFF : 0x96FFFFFF);
            tLabel.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(tLabel, new LinearLayout.LayoutParams(-2, -2));

            ImageView icon = new ImageView(this);
            icon.setImageResource(WeatherModels.iconRes(pt.weatherCode, pt.isDay));
            icon.setAlpha(i == 0 ? 0.96f : 0.86f);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(u(22), u(22));
            icLp.topMargin = u(7);
            icLp.bottomMargin = u(24);
            col.addView(icon, icLp);

            TextView tempLabel = new TextView(this);
            tempLabel.setText(WeatherModels.formatTemp(pt.tempC, useFahrenheit));
            setTvType(tempLabel, TYPE_BODY);
            tempLabel.setTypeface(fontLight);
            tempLabel.setTextColor(0xF8FFFFFF);
            tempLabel.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(tempLabel, new LinearLayout.LayoutParams(-2, -2));

            col.setOnFocusChangeListener((v, focused) -> {
                styleFrostedFocusSurface(v, focused, 15);
                animateFocusScale(v, focused, 1.05f);
                tLabel.setTextColor(focused ? 0xFFFFFFFF : (slotIndex == 0 ? 0xF8FFFFFF : 0x96FFFFFF));
                icon.setAlpha(focused ? 1f : (slotIndex == 0 ? 0.96f : 0.86f));
                if (focused) {
                    hourlyCurveView.setSelectedIndex(slotIndex);
                    String cond = WeatherModels.conditionLabel(pt.weatherCode, pt.isDay, zh);
                    String tempStr = WeatherModels.formatTemp(pt.tempC, useFahrenheit);
                    heroInsightText.setText(getString(R.string.hourly_probe, displayTime, cond, tempStr));
                    heroInsightText.setTextColor(0xF5FFFFFF);
                } else {
                    main.post(() -> {
                        if (!hourlyColumnsRow.hasFocus()) {
                            hourlyCurveView.setSelectedIndex(-1);
                            heroInsightText.setText(currentPoeticInsight);
                            heroInsightText.setTextColor(0xB4FFFFFF);
                        }
                    });
                }
            });
            col.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusActiveCityTab();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    scrollToForecast();
                    return true;
                }
                return false;
            });

            hourlyColumnsRow.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            slotViews[i] = col;
        }
        for (int i = 0; i < hCount; i++) {
            if (i > 0) slotViews[i].setNextFocusLeftId(slotViews[i - 1].getId());
            if (i < hCount - 1) slotViews[i].setNextFocusRightId(slotViews[i + 1].getId());
            else if (refreshBtn != null) slotViews[i].setNextFocusRightId(refreshBtn.getId());
        }
        hourlyCurveView.setTemperatures(curveTemps);
        if (prevHourlyFocus >= 0 && prevHourlyFocus < hCount) {
            slotViews[prevHourlyFocus].requestFocus();
        }

        // Unboxed right-hand metrics
        if (d.aqi >= 0) {
            aqiValueText.setText(String.valueOf(d.aqi));
            aqiLabelText.setText(WeatherModels.aqiLabel(d.aqi, zh));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(WeatherModels.aqiDotColor(d.aqi));
            aqiDotView.setBackground(dot);
            aqiDotView.setVisibility(View.VISIBLE);
        } else {
            aqiValueText.setText(d.humidity + "%");
            aqiLabelText.setText(zh ? "湿度" : "Humidity");
            aqiDotView.setVisibility(View.GONE);
        }

        windValueText.setText(String.valueOf(Math.round(d.windKmh)));
        windSubText.setText(getString(R.string.wind_unit, WeatherModels.windDirection(d.windDeg, zh)));

        sunsetValueText.setText(d.sunset.isEmpty() ? "—" : d.sunset);
        sunsetSubText.setText(getString(R.string.uv_short,
                (int) Math.round(d.uvMax),
                WeatherModels.uvLabel(d.uvMax, zh)));

        // Below-the-fold 7-Day Forecast
        bindDailyForecast(d);
    }

    private void bindDailyForecast(WeatherModels.WeatherData d) {
        // Preserve focused 7-day row index across background refreshes (rows are at even child indices)
        int prevDailyRow = -1;
        for (int i = 0; i < dailyRowsContainer.getChildCount(); i++) {
            if (dailyRowsContainer.getChildAt(i).isFocused()) {
                prevDailyRow = i / 2;
                break;
            }
        }
        dailyRowsContainer.removeAllViews();
        if (d.daily.isEmpty()) return;

        double weekMin = d.daily.get(0).lowC;
        double weekMax = d.daily.get(0).highC;
        for (WeatherModels.DailyPoint p : d.daily) {
            if (p.lowC < weekMin) weekMin = p.lowC;
            if (p.highC > weekMax) weekMax = p.highC;
        }

        for (int i = 0; i < d.daily.size(); i++) {
            final int rowIdx = i;
            WeatherModels.DailyPoint p = d.daily.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(u(16), u(14), u(16), u(14));
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setDefaultFocusHighlightEnabled(false);

            TextView dayTv = new TextView(this);
            dayTv.setText(i == 0 ? getString(R.string.today) : formatWeekday(p.dateIso));
            setTvType(dayTv, TYPE_BODY);
            dayTv.setTypeface(i == 0 ? fontMedium : fontRegular);
            dayTv.setTextColor(0xF2FFFFFF);
            row.addView(dayTv, new LinearLayout.LayoutParams(u(144), -2));

            ImageView icon = new ImageView(this);
            icon.setImageResource(WeatherModels.iconRes(p.weatherCode, true));
            icon.setAlpha(0.90f);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(u(24), u(24));
            icLp.setMarginEnd(u(18));
            row.addView(icon, icLp);

            TextView condTv = new TextView(this);
            condTv.setText(WeatherModels.conditionLabel(p.weatherCode, true, zh));
            setTvType(condTv, TYPE_SECONDARY);
            condTv.setTypeface(fontLight);
            condTv.setTextColor(0xA8FFFFFF);
            condTv.setMaxLines(1);
            condTv.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(condTv, new LinearLayout.LayoutParams(u(164), -2));

            TextView lowTv = new TextView(this);
            lowTv.setText(WeatherModels.formatTemp(p.lowC, useFahrenheit));
            setTvType(lowTv, TYPE_BODY);
            lowTv.setTypeface(fontLight);
            lowTv.setTextColor(0x98FFFFFF);
            lowTv.setGravity(Gravity.END);
            row.addView(lowTv, new LinearLayout.LayoutParams(u(54), -2));

            WeatherCanvasViews.TempRangeBarView bar = new WeatherCanvasViews.TempRangeBarView(this);
            bar.setRange(weekMin, weekMax, p.lowC, p.highC);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, u(6.5f), 1f);
            barLp.setMarginStart(u(20));
            barLp.setMarginEnd(u(20));
            row.addView(bar, barLp);

            TextView highTv = new TextView(this);
            highTv.setText(WeatherModels.formatTemp(p.highC, useFahrenheit));
            setTvType(highTv, TYPE_BODY);
            highTv.setTypeface(fontRegular);
            highTv.setTextColor(0xF8FFFFFF);
            row.addView(highTv, new LinearLayout.LayoutParams(u(54), -2));

            row.setOnFocusChangeListener((v, focused) -> {
                styleFrostedFocusSurface(v, focused, 16);
                animateFocusScale(v, focused, 1.016f);
                bar.setHighlighted(focused);
                dayTv.setTextColor(focused ? 0xFFFFFFFF : 0xF2FFFFFF);
                condTv.setTextColor(focused ? 0xECFFFFFF : 0xA8FFFFFF);
                lowTv.setTextColor(focused ? 0xDCFFFFFF : 0x98FFFFFF);
                highTv.setTextColor(focused ? 0xFFFFFFFF : 0xF8FFFFFF);
                icon.setAlpha(focused ? 1f : 0.90f);
            });
            if (rowIdx == 0) {
                row.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
                        returnToHeroTop(false);
                        return true;
                    }
                    return false;
                });
            }

            dailyRowsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));

            if (i < d.daily.size() - 1) {
                View sep = new View(this);
                sep.setBackgroundColor(0x18FFFFFF);
                dailyRowsContainer.addView(sep, new LinearLayout.LayoutParams(-1, Math.max(1, u(0.5f))));
            }
        }

        StringBuilder foot = new StringBuilder();
        foot.append(getString(R.string.humidity_label, d.humidity));
        foot.append("   ·   ").append(getString(R.string.feels_like, WeatherModels.formatTemp(d.feelsLikeC, useFahrenheit)));
        if (!d.sunrise.isEmpty()) {
            foot.append("   ·   ").append(getString(R.string.sunrise_label, d.sunrise));
        }
        foot.append("   ·   ").append(getString(R.string.data_source));
        footnoteText.setText(foot.toString());
        if (prevDailyRow >= 0 && prevDailyRow * 2 < dailyRowsContainer.getChildCount()) {
            dailyRowsContainer.getChildAt(prevDailyRow * 2).requestFocus();
        }
    }

    private String formatWeekday(String isoDate) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = parser.parse(isoDate);
            if (date == null) return isoDate;
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            SimpleDateFormat out = new SimpleDateFormat(zh ? "EEE  MM/dd" : "EEE  MMM d", locale);
            return out.format(date);
        } catch (Exception ignored) {
            return isoDate;
        }
    }

    // ============================================================
    // Apple Smoked-Glass Global City Search & IP Geolocation Modal
    // ============================================================
    private void showCityDialog() {
        cancelPendingCitySwitch();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        sheet.setPadding(u(28), u(24), u(28), u(24));
        GradientDrawable sheetBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xEC111722, 0xF20B0F18});
        sheetBg.setCornerRadius(u(24));
        sheetBg.setStroke(Math.max(1, u(0.75f)), 0x28FFFFFF);
        sheet.setBackground(sheetBg);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        TextView title = new TextView(this);
        title.setText(R.string.manage_cities);
        setTvType(title, TYPE_TITLE);
        title.setTypeface(fontMedium);
        title.setTextColor(0xF8FFFFFF);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView closeBtn = buildDialogButton(getString(R.string.close), false);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        sheet.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Search & IP auto-locate controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setClipChildren(false);
        LinearLayout.LayoutParams ctrlLp = new LinearLayout.LayoutParams(-1, -2);
        ctrlLp.topMargin = u(16);
        ctrlLp.bottomMargin = u(16);

        TextView locateBtn = buildDialogButton(getString(R.string.auto_locate), true);
        LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(-2, -2);
        locLp.setMarginEnd(u(12));
        controls.addView(locateBtn, locLp);

        EditText searchInput = new EditText(this);
        searchInput.setHint(R.string.search_hint);
        searchInput.setHintTextColor(0x70FFFFFF);
        searchInput.setTextColor(0xF8FFFFFF);
        setTvType(searchInput, TYPE_SECONDARY);
        searchInput.setTypeface(fontRegular);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setPadding(u(14), u(10), u(14), u(10));
        searchInput.setDefaultFocusHighlightEnabled(false);
        styleSearchInput(searchInput, false);
        searchInput.setOnFocusChangeListener((v, f) -> styleSearchInput(searchInput, f));
        controls.addView(searchInput, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView searchBtn = buildDialogButton(getString(R.string.search_button), false);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-2, -2);
        sbLp.setMarginStart(u(10));
        controls.addView(searchBtn, sbLp);
        sheet.addView(controls, ctrlLp);

        // Scrollable lists inside modal
        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setVerticalScrollBarEnabled(false);
        bodyScroll.setClipChildren(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);

        LinearLayout searchResultsSection = new LinearLayout(this);
        searchResultsSection.setOrientation(LinearLayout.VERTICAL);
        searchResultsSection.setVisibility(View.GONE);
        body.addView(searchResultsSection);

        LinearLayout savedSection = new LinearLayout(this);
        savedSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(savedSection);

        LinearLayout popularSection = new LinearLayout(this);
        popularSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams popLp = new LinearLayout.LayoutParams(-1, -2);
        popLp.topMargin = u(18);
        body.addView(popularSection, popLp);

        bodyScroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(bodyScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        populateSavedCities(savedSection, popularSection, dialog, locateBtn);
        populatePopularCities(popularSection, dialog);

        Runnable doSearch = () -> {
            String query = searchInput.getText().toString().trim();
            if (query.isEmpty()) return;
            searchResultsSection.setVisibility(View.VISIBLE);
            searchResultsSection.removeAllViews();
            searchResultsSection.addView(sectionLabel(getString(R.string.search_results)));
            TextView loading = new TextView(this);
            loading.setText(R.string.loading);
            setTvType(loading, TYPE_SECONDARY);
            loading.setTextColor(0x94FFFFFF);
            loading.setPadding(0, u(6), 0, u(10));
            searchResultsSection.addView(loading);

            String lang = zh ? "zh" : "en";
            pool.execute(() -> {
                List<WeatherModels.City> results = new ArrayList<>();
                try {
                    results = WeatherApiClient.searchCities(query, lang);
                } catch (Exception ignored) {
                }
                List<WeatherModels.City> finalResults = results;
                main.post(() -> {
                    if (!dialog.isShowing()) return;
                    searchResultsSection.removeAllViews();
                    searchResultsSection.addView(sectionLabel(getString(R.string.search_results)));
                    if (finalResults.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText(R.string.no_results);
                        setTvType(empty, TYPE_SECONDARY);
                        empty.setTextColor(0x84FFFFFF);
                        empty.setPadding(0, u(6), 0, u(10));
                        searchResultsSection.addView(empty);
                        return;
                    }
                    View firstResult = null;
                    for (WeatherModels.City c : finalResults) {
                        View row = buildCityRow(c, false, false, () -> {
                            WeatherStore.addOrPromoteCity(this, c, false);
                            dialog.dismiss();
                            loadActiveCityAndRefresh(true, true);
                            syncCityTabs(true);
                        }, null);
                        if (firstResult == null) firstResult = row;
                        searchResultsSection.addView(row);
                    }
                    if (firstResult != null) firstResult.requestFocus();
                });
            });
        };

        searchBtn.setOnClickListener(v -> doSearch.run());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                doSearch.run();
                return true;
            }
            return false;
        });

        locateBtn.setOnClickListener(v -> {
            locateBtn.setText(R.string.locating);
            locateBtn.setEnabled(false);
            String lang = zh ? "zh" : "en";
            pool.execute(() -> {
                WeatherModels.City detected = WeatherApiClient.locateByIp(lang);
                main.post(() -> {
                    locateBtn.setEnabled(true);
                    locateBtn.setText(R.string.auto_locate);
                    if (detected == null) {
                        Toast.makeText(this, R.string.locate_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    WeatherStore.addOrPromoteCity(this, detected, true);
                    Toast.makeText(this, getString(R.string.located_toast, detected.name), Toast.LENGTH_SHORT).show();
                    if (dialog.isShowing()) dialog.dismiss();
                    loadActiveCityAndRefresh(true, true);
                    syncCityTabs(true);
                });
            });
        });

        dialog.setOnDismissListener(d -> {
            if (addCityButton != null && getCurrentFocus() == null) {
                focusActiveCityTab();
            }
        });

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = Math.round(viewportW * 0.76f);
            lp.height = Math.round(viewportH * 0.78f);
            lp.dimAmount = 0.58f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        sheet.setAlpha(0f);
        sheet.setScaleX(0.96f);
        sheet.setScaleY(0.96f);
        sheet.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(EASE_OUT).start();
        locateBtn.requestFocus();
    }

    private void styleSearchInput(EditText input, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(focused ? 0x26FFFFFF : 0x16FFFFFF);
        bg.setCornerRadius(u(14));
        bg.setStroke(Math.max(1, u(focused ? 1.5f : 0.75f)), focused ? 0xE6FFFFFF : 0x26FFFFFF);
        input.setBackground(bg);
    }

    private void populateSavedCities(LinearLayout savedContainer, LinearLayout popularContainer,
                                     Dialog dialog, View fallbackFocus) {
        savedContainer.removeAllViews();
        savedContainer.addView(sectionLabel(getString(R.string.saved_cities)));
        List<WeatherModels.City> saved = WeatherStore.cities(this);
        boolean canRemove = saved.size() > 1;
        for (int i = 0; i < saved.size(); i++) {
            final int idx = i;
            WeatherModels.City c = saved.get(i);
            boolean isCurrent = activeCity != null && c.id.equals(activeCity.id);
            savedContainer.addView(buildCityRow(c, isCurrent, canRemove, () -> {
                WeatherStore.setActiveCity(this, c.id);
                dialog.dismiss();
                loadActiveCityAndRefresh(false, true);
                syncCityTabs(true);
            }, canRemove ? () -> {
                WeatherStore.removeCity(this, c.id);
                populateSavedCities(savedContainer, popularContainer, dialog, fallbackFocus);
                populatePopularCities(popularContainer, dialog);
                loadActiveCityAndRefresh(false, false);
                int nextRowIndex = Math.min(idx + 1, savedContainer.getChildCount() - 1);
                if (nextRowIndex >= 1) {
                    savedContainer.getChildAt(nextRowIndex).requestFocus();
                } else if (fallbackFocus != null) {
                    fallbackFocus.requestFocus();
                }
            } : null));
        }
    }

    private void populatePopularCities(LinearLayout container, Dialog dialog) {
        container.removeAllViews();
        container.addView(sectionLabel(getString(R.string.popular_cities)));

        Set<String> savedIds = new HashSet<>();
        for (WeatherModels.City c : WeatherStore.cities(this)) {
            savedIds.add(c.id);
            savedIds.add(c.name);
        }

        List<WeatherModels.City> presets = WeatherModels.globalPresets(zh);
        int cols = 4;
        for (int i = 0; i < presets.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
            rLp.topMargin = u(8);
            for (int c = 0; c < cols; c++) {
                int idx = i + c;
                if (idx >= presets.size()) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                WeatherModels.City city = presets.get(idx);
                boolean alreadySaved = savedIds.contains(city.id) || savedIds.contains(city.name);
                TextView chip = new TextView(this);
                chip.setText(alreadySaved ? (city.name + " ✓") : city.name);
                setTvType(chip, TYPE_SECONDARY);
                chip.setTypeface(alreadySaved ? fontMedium : fontRegular);
                chip.setTextColor(alreadySaved ? 0xFFFFFFFF : 0xDEFFFFFF);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(u(10), u(11), u(10), u(11));
                chip.setFocusable(true);
                chip.setFocusableInTouchMode(true);
                chip.setClickable(true);
                chip.setDefaultFocusHighlightEnabled(false);
                styleModalCard(chip, false);
                chip.setOnFocusChangeListener((v, f) -> {
                    styleModalCard(v, f);
                    ((TextView) v).setTextColor(f ? INK_DARK : (alreadySaved ? 0xFFFFFFFF : 0xDEFFFFFF));
                    animateFocusScale(v, f, 1.04f);
                });
                chip.setOnClickListener(v -> {
                    WeatherStore.addOrPromoteCity(this, city, false);
                    dialog.dismiss();
                    loadActiveCityAndRefresh(true, true);
                    syncCityTabs(true);
                });
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, -2, 1f);
                if (c < cols - 1) cLp.setMarginEnd(u(8));
                row.addView(chip, cLp);
            }
            container.addView(row, rLp);
        }
    }

    private View buildCityRow(WeatherModels.City city, boolean isCurrent, boolean showRemove,
                              Runnable onSelect, Runnable onRemove) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
        rLp.topMargin = u(7);
        row.setLayoutParams(rLp);

        LinearLayout mainBtn = new LinearLayout(this);
        mainBtn.setOrientation(LinearLayout.HORIZONTAL);
        mainBtn.setGravity(Gravity.CENTER_VERTICAL);
        mainBtn.setPadding(u(16), u(11), u(16), u(11));
        mainBtn.setFocusable(true);
        mainBtn.setFocusableInTouchMode(true);
        mainBtn.setClickable(true);
        mainBtn.setDefaultFocusHighlightEnabled(false);
        styleModalCard(mainBtn, false);

        if (isCurrent) {
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(0xFF5EE39B);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(u(7), u(7));
            dLp.setMarginEnd(u(10));
            mainBtn.addView(dot, dLp);
        }

        TextView nameTv = new TextView(this);
        nameTv.setText(city.autoLocated ? ("⌖ " + city.name) : city.name);
        setTvType(nameTv, TYPE_BODY);
        nameTv.setTypeface(fontMedium);
        nameTv.setTextColor(0xF8FFFFFF);
        mainBtn.addView(nameTv);

        TextView subTv = new TextView(this);
        subTv.setText(city.subtitle());
        setTvType(subTv, TYPE_SECONDARY);
        subTv.setTypeface(fontRegular);
        subTv.setTextColor(0x94FFFFFF);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sLp.setMarginStart(u(14));
        mainBtn.addView(subTv, sLp);

        mainBtn.setOnFocusChangeListener((v, f) -> {
            styleModalCard(v, f);
            nameTv.setTextColor(f ? INK_DARK : 0xF8FFFFFF);
            subTv.setTextColor(f ? 0xB80E1420 : 0x94FFFFFF);
            animateFocusScale(v, f, 1.015f);
        });
        mainBtn.setOnClickListener(v -> onSelect.run());
        row.addView(mainBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        if (showRemove && onRemove != null) {
            TextView remBtn = buildDialogButton(getString(R.string.remove_city), false);
            LinearLayout.LayoutParams remLp = new LinearLayout.LayoutParams(-2, -2);
            remLp.setMarginStart(u(10));
            remBtn.setOnClickListener(v -> onRemove.run());
            row.addView(remBtn, remLp);
        }
        return row;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        setTvType(tv, TYPE_CAPTION);
        tv.setTypeface(fontMedium);
        tv.setTextColor(0x8AFFFFFF);
        tv.setLetterSpacing(0.16f);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = u(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView buildDialogButton(String label, boolean accent) {
        TextView btn = new TextView(this);
        btn.setText(label);
        setTvType(btn, TYPE_SECONDARY);
        btn.setTypeface(fontMedium);
        btn.setTextColor(accent ? 0xF8FFFFFF : 0xDCFFFFFF);
        btn.setPadding(u(16), u(10), u(16), u(10));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        btn.setClickable(true);
        btn.setDefaultFocusHighlightEnabled(false);
        styleModalCard(btn, false);
        btn.setOnFocusChangeListener((v, f) -> {
            styleModalCard(v, f);
            ((TextView) v).setTextColor(f ? INK_DARK : (accent ? 0xF8FFFFFF : 0xDCFFFFFF));
            animateFocusScale(v, f, 1.05f);
        });
        return btn;
    }

    private void styleModalCard(View view, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(u(14));
        if (focused) {
            d.setColor(FOCUS_WHITE);
            view.setElevation(uF(6));
        } else {
            d.setColor(0x16FFFFFF);
            d.setStroke(Math.max(1, u(0.75f)), 0x1EFFFFFF);
            view.setElevation(0f);
        }
        view.setBackground(d);
    }
}
