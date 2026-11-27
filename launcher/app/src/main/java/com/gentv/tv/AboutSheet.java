package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * About GenTV, in the Control Center's glass: the brand and the one line it stands for, the three
 * promises it keeps, and where to find it. Nothing on the sheet is focusable except its two actions.
 */
final class AboutSheet {
    private static final int BRAND_BLUE = 0xFF69B9F0;
    private static final String WEBSITE = "https://www.gentv.me/";

    private final Activity activity;
    private final float unit;
    private final Typeface medium;
    private final Typeface regular;

    private AboutSheet(Activity activity) {
        this.activity = activity;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        // Designed at 640 × 520 dp; smaller screens scale the whole sheet down together.
        float scale = Math.max(0.5f, Math.min(1f, Math.min((metrics.widthPixels / metrics.density - 48) / 640f,
                (metrics.heightPixels / metrics.density - 40) / 520f)));
        unit = metrics.density * scale;
        medium = UiFont.get(activity, R.string.font_medium);
        regular = UiFont.get(activity, R.string.font_regular);
    }

    static Dialog show(Activity activity, Runnable checkUpdates) {
        return new AboutSheet(activity).build(checkUpdates);
    }

    private Dialog build(Runnable checkUpdates) {
        Config config = Config.load(activity);
        boolean motion = config.animations && !config.reducedMotion;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        sheet.setPadding(px(32), px(28), px(32), px(24));
        sheet.setBackground(QuickSettingsGlass.dialogSurface(px(30), Math.max(1, px(1))));

        sheet.addView(header());
        TextView intro = text(R.string.about_intro, 13, ShareCodeEntryView.INK_MUTED, regular);
        intro.setLineSpacing(px(4), 1f);
        sheet.addView(intro, margins(-1, -2, 16));
        sheet.addView(promises(), margins(-1, -2, 20));
        sheet.addView(hairline(), margins(-1, Math.max(1, Math.round(unit * 0.75f)), 20));
        sheet.addView(footer(), margins(-1, -2, 16));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        sheet.addView(actions, margins(-1, px(42), 20));
        Button update = action(R.string.about_check_updates, () -> {
            dialog.dismiss();
            checkUpdates.run();
        }, motion);
        actions.addView(update, new LinearLayout.LayoutParams(px(148), ViewGroup.LayoutParams.MATCH_PARENT));
        Button done = action(R.string.about_done, dialog::dismiss, motion);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(px(148), ViewGroup.LayoutParams.MATCH_PARENT);
        doneParams.setMarginStart(px(12));
        actions.addView(done, doneParams);
        update.setNextFocusRightId(done.getId());
        done.setNextFocusLeftId(update.getId());

        dialog.setContentView(sheet);
        dialog.setOnShowListener(shown -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                // Darker than the usual sheet dim: the Control Center's text sits right behind this glass.
                window.setDimAmount(.66f);
                int available = activity.getResources().getDisplayMetrics().widthPixels - px(48);
                window.setLayout(Math.min(available, px(640)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            done.requestFocusFromTouch();
            if (motion) {
                sheet.setAlpha(0f);
                sheet.setTranslationY(px(10));
                sheet.setScaleX(.97f);
                sheet.setScaleY(.97f);
                sheet.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(CompatApi.easeOut()).start();
            }
        });
        dialog.show();
        return dialog;
    }

    /** App icon, the GenTV wordmark as Home draws it, the version, and the line it stands for. */
    private View header() {
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_gentv);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        CompatApi.elevation(icon, px(10));
        header.addView(icon, new LinearLayout.LayoutParams(px(76), px(76)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textsParams.setMarginStart(px(20));
        header.addView(texts, textsParams);

        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(brandRow);
        TextView wordmark = new TextView(activity);
        SpannableString brand = new SpannableString("GenTV");
        brand.setSpan(new ForegroundColorSpan(BRAND_BLUE), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        wordmark.setText(brand);
        wordmark.setTextColor(0xFFF5F5F7);
        wordmark.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        wordmark.setTextSize(size(30));
        wordmark.setIncludeFontPadding(false);
        CompatApi.letterSpacing(wordmark, -0.035f);
        brandRow.addView(wordmark);
        TextView version = text(0, 11, ShareCodeEntryView.INK_SOFT, medium);
        version.setText(activity.getString(R.string.about_version, BuildConfig.VERSION_NAME));
        version.setPadding(px(10), px(4), px(10), px(4));
        version.setBackground(ShareCodeEntryView.surface(px(11), 0x1AFFFFFF, 1, 0x1FFFFFFF));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.setMarginStart(px(12));
        brandRow.addView(version, versionParams);

        TextView tagline = text(R.string.about_tagline, 18, ShareCodeEntryView.INK_LIGHT, medium);
        CompatApi.letterSpacing(tagline, 0.01f);
        texts.addView(tagline, margins(-1, -2, 12));
        return header;
    }

    private View promises() {
        LinearLayout row = new LinearLayout(activity);
        row.setBaselineAligned(false);
        row.addView(promise(R.drawable.ic_about_no_ads, R.string.about_no_ads, R.string.about_no_ads_detail),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        middle.setMarginStart(px(12));
        middle.setMarginEnd(px(12));
        row.addView(promise(R.drawable.ic_about_open_source, R.string.about_open_source,
                R.string.about_open_source_detail), middle);
        row.addView(promise(R.drawable.ic_about_reversible, R.string.about_reversible,
                R.string.about_reversible_detail), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return row;
    }

    private View promise(int iconRes, int titleRes, int detailRes) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(16), px(15), px(16), px(16));
        card.setBackground(ShareCodeEntryView.surface(px(18), 0x10FFFFFF, 1, 0x17FFFFFF));

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading);
        FrameLayout badge = new FrameLayout(activity);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x2669B9F0);
        badge.setBackground(circle);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        CompatApi.imageTint(icon, ColorStateList.valueOf(BRAND_BLUE));
        badge.addView(icon, new FrameLayout.LayoutParams(px(17), px(17), Gravity.CENTER));
        heading.addView(badge, new LinearLayout.LayoutParams(px(32), px(32)));
        TextView title = text(titleRes, 14, ShareCodeEntryView.INK_LIGHT, medium);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(px(11));
        heading.addView(title, titleParams);

        TextView detail = text(detailRes, 11, ShareCodeEntryView.INK_MUTED, regular);
        detail.setLineSpacing(px(3), 1f);
        card.addView(detail, margins(-1, -2, 12));
        return card;
    }

    /** Where to find GenTV, with a code the viewer's phone can scan. */
    private View footer() {
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBaselineAligned(false);

        LinearLayout facts = new LinearLayout(activity);
        facts.setOrientation(LinearLayout.VERTICAL);
        footer.addView(facts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        facts.addView(fact(R.string.about_website, "www.gentv.me"));
        facts.addView(fact(R.string.about_source, "github.com/Elaine-Lynn/GenTV"), margins(-1, -2, 9));
        facts.addView(fact(R.string.about_contact, "support@gentv.me"), margins(-1, -2, 9));
        facts.addView(text(R.string.about_license, 10, ShareCodeEntryView.INK_CAPTION, regular), margins(-1, -2, 14));

        Bitmap qr;
        try {
            qr = TvLan.qrCode(WEBSITE);
        } catch (Exception error) {
            return footer;
        }
        LinearLayout scan = new LinearLayout(activity);
        scan.setOrientation(LinearLayout.VERTICAL);
        scan.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scanParams.setMarginStart(px(20));
        footer.addView(scan, scanParams);
        ImageView code = new ImageView(activity);
        code.setImageBitmap(qr);
        code.setScaleType(ImageView.ScaleType.FIT_CENTER);
        code.setPadding(px(4), px(4), px(4), px(4));
        code.setBackground(ShareCodeEntryView.surface(px(12), 0xFFFFFFFF, 0, 0));
        code.setContentDescription(activity.getString(R.string.about_scan));
        CompatApi.roundClip(code, px(12));
        scan.addView(code, new LinearLayout.LayoutParams(px(82), px(82)));
        TextView caption = text(R.string.about_scan, 10, ShareCodeEntryView.INK_CAPTION, regular);
        caption.setGravity(Gravity.CENTER);
        scan.addView(caption, margins(px(110), -2, 8));
        return footer;
    }

    private View fact(int labelRes, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(labelRes, 11, ShareCodeEntryView.INK_CAPTION, regular);
        row.addView(label, new LinearLayout.LayoutParams(px(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView text = text(0, 13, ShareCodeEntryView.INK_SOFT, medium);
        text.setText(value);
        text.setSingleLine(true);
        row.addView(text);
        return row;
    }

    private View hairline() {
        View line = new View(activity);
        line.setBackgroundColor(0x14FFFFFF);
        return line;
    }

    private Button action(int label, Runnable run, boolean motion) {
        Button button = ShareCodeEntryView.button(activity, medium, activity.getString(label), size(14),
                px(21), run);
        button.setOnFocusChangeListener((view, focused) -> {
            CompatApi.elevation(view, focused ? px(8) : 0f);
            float scale = focused ? 1.04f : 1f;
            if (motion) view.animate().scaleX(scale).scaleY(scale).setDuration(150)
                    .setInterpolator(CompatApi.easeOut()).start();
            else { view.setScaleX(scale); view.setScaleY(scale); }
        });
        return button;
    }

    private TextView text(int res, float sp, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        if (res != 0) view.setText(res);
        view.setTextColor(color);
        view.setTextSize(size(sp));
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width == -1 ? ViewGroup.LayoutParams.MATCH_PARENT : width == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : width,
                height == -1 ? ViewGroup.LayoutParams.MATCH_PARENT : height == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : height);
        params.topMargin = px(topDp);
        return params;
    }

    private float size(float sp) {
        return sp * unit / activity.getResources().getDisplayMetrics().density;
    }

    private int px(float dp) {
        return Math.round(dp * unit);
    }
}
