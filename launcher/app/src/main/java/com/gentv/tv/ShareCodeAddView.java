package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

/** Phone handoff and saved confirmation inside the share-code panel. */
final class ShareCodeAddView extends LinearLayout {
    private static final int TEXT = 0xFFF5F7FA;
    private static final int MUTED = 0xFFB4B6BE;
    private final Typeface font;
    private final float scale;
    private final float density;
    private Button primaryAction;

    ShareCodeAddView(Context context) {
        super(context);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = ShareCodeEntryView.scale(metrics);
        density = metrics.density;
        font = UiFont.get(context, R.string.font_medium);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
    }

    void showReceiver(String url, Bitmap qr, Runnable back) {
        showHeader(back);
        addView(label(R.string.share_code_send_subtitle, 12, MUTED), spacing(-1, -2, 5));

        LinearLayout methods = new LinearLayout(getContext());
        methods.setGravity(Gravity.CENTER_VERTICAL);
        addView(methods, spacing(-1, size(274), 24));
        LinearLayout scan = card(methods, 0xFF2A2B32, 0xFF494B54);
        scan.addView(label(R.string.share_code_scan, 17, TEXT));
        ImageView image = new ImageView(getContext());
        image.setContentDescription(getContext().getString(R.string.share_code_scan));
        image.setImageBitmap(qr);
        image.setBackground(ShareCodeEntryView.surface(size(12), 0xFFFFFFFF, 0, 0));
        image.setPadding(size(5), size(5), size(5), size(5));
        scan.addView(image, spacing(size(147), size(147), 16));
        scan.addView(label(R.string.share_code_scan_hint, 13, TEXT), spacing(-1, -2, 14));

        methods.addView(label(R.string.share_code_or, 12, MUTED), new LayoutParams(size(34), -1));
        LinearLayout browser = card(methods, 0xFF202127, 0xFF353640);
        browser.addView(label(R.string.share_code_browser, 17, TEXT));
        TextView address = label(url, 20, TEXT);
        address.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        address.setMaxLines(2);
        address.setBackground(ShareCodeEntryView.surface(size(10), 0xFF32333A, 0, 0));
        browser.addView(address, spacing(-1, size(60), 16));
        addStep(browser, "1", R.string.share_code_step_network, 16);
        addStep(browser, "2", R.string.share_code_step_browser, 7);
        addStep(browser, "3", R.string.share_code_step_send, 7);

        TextView waiting = label(R.string.share_code_waiting, 12, 0xFFB3CBFF);
        waiting.setPadding(size(15), 0, size(15), 0);
        waiting.setBackground(ShareCodeEntryView.surface(size(15), 0xFF232936, 0, 0));
        addView(waiting, spacing(-2, size(29), 18));
    }

    void showUnavailable(Runnable back, Runnable retry) {
        showHeader(back);
        addView(label(R.string.share_code_receiver_failed, 14, MUTED), spacing(-1, size(164), 14));
        Button button = button(R.string.share_code_retry, retry);
        addView(button, spacing(size(108), size(34), 0));
        primaryAction = button;
        focusAction();
    }

    void showSaved(ShareCodeLinks.Link link, Runnable open, Runnable more, Runnable home) {
        removeAllViews();
        TextView check = label("✓", 26, 0xFF57D283);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        check.setBackground(ShareCodeEntryView.surface(size(26), 0xFF193426, 0, 0));
        addView(check, spacing(size(52), size(52), 28));
        addView(label(R.string.share_code_added, 23, TEXT), spacing(-1, -2, 16));

        LinearLayout code = new LinearLayout(getContext());
        code.setGravity(Gravity.CENTER);
        code.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        code.setContentDescription(ShareCodeLinks.formatted(link.code));
        addView(code, spacing(-1, size(49), 25));
        for (int index = 0; index < link.code.length(); index++) {
            if (index == 3) {
                TextView separator = label("–", 20, MUTED);
                separator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                code.addView(separator, new LayoutParams(size(22), -1));
            }
            TextView slot = label(String.valueOf(link.code.charAt(index)), 25, TEXT);
            slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            slot.setBackground(ShareCodeEntryView.surface(size(9), 0xFF292A31, 0, 0));
            LayoutParams params = new LayoutParams(size(42), -1);
            params.leftMargin = index == 0 || index == 3 ? 0 : size(6);
            code.addView(slot, params);
        }

        TextView name = label(link.name, 16, TEXT);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        addView(name, spacing(-1, -2, 14));
        addView(label(R.string.share_code_saved_hint, 12, MUTED), spacing(-1, -2, 5));

        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER);
        addView(actions, spacing(-1, size(34), 24));
        Button openButton = button(R.string.share_code_open_now, open);
        actions.addView(openButton, new LayoutParams(size(110), -1));
        if (more != null) addAction(actions, R.string.share_code_add_more, more);
        addAction(actions, R.string.share_code_return_home, home);
        primaryAction = openButton;
        focusAction();
    }

    void focusAction() {
        if (primaryAction != null) primaryAction.requestFocusFromTouch();
    }

    private void showHeader(Runnable back) {
        removeAllViews();
        FrameLayout header = new FrameLayout(getContext());
        addView(header, new LayoutParams(-1, size(36)));
        header.addView(label(R.string.share_code_add_title, 23, TEXT), new FrameLayout.LayoutParams(-2, -1,
                Gravity.CENTER));
        Button button = button(R.string.share_code_back, back);
        Drawable icon = DrawableCompat.wrap(CompatApi.drawable(getContext(), R.drawable.ic_ext_back)).mutate();
        icon.setBounds(0, 0, size(21), size(21));
        DrawableCompat.setTintList(icon, button.getTextColors());
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setPaddingRelative(size(8), 0, size(13), 0);
        header.addView(button, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size(32),
                Gravity.START | Gravity.CENTER_VERTICAL));
        primaryAction = button;
        focusAction();
    }

    private LinearLayout card(LinearLayout row, int color, int strokeColor) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(size(20), size(14), size(20), size(14));
        card.setBackground(ShareCodeEntryView.surface(size(16), color, size(1), strokeColor));
        row.addView(card, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return card;
    }

    private void addStep(LinearLayout card, String number, int text, int topMargin) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, spacing(-1, -2, topMargin));
        TextView badge = label(number, 10, TEXT);
        badge.setBackground(ShareCodeEntryView.surface(size(10), 0xFF41424A, 0, 0));
        row.addView(badge, new LayoutParams(size(18), size(18)));
        TextView step = label(text, 12, TEXT);
        step.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LayoutParams params = new LayoutParams(0, -2, 1);
        params.setMarginStart(size(8));
        row.addView(step, params);
    }

    private void addAction(LinearLayout row, int text, Runnable click) {
        LayoutParams params = new LayoutParams(size(110), -1);
        params.setMarginStart(size(9));
        row.addView(button(text, click), params);
    }

    private Button button(int text, Runnable click) {
        return ShareCodeEntryView.button(getContext(), font, getContext().getString(text), 13 * scale, size(9), click);
    }

    private TextView label(int text, int sp, int color) {
        return label(getContext().getString(text), sp, color);
    }

    private TextView label(String text, int sp, int color) {
        TextView label = new TextView(getContext());
        label.setTypeface(font);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(color);
        label.setTextSize(sp * scale);
        label.setText(text);
        return label;
    }

    private LayoutParams spacing(int width, int height, int topMargin) {
        LayoutParams params = new LayoutParams(width, height);
        params.topMargin = size(topMargin);
        return params;
    }

    private int size(int value) {
        return Math.round(value * density * scale);
    }
}
