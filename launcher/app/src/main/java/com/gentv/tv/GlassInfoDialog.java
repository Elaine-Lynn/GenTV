package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared glass sheet for About and launcher-owned information/confirmation prompts. */
final class GlassInfoDialog {
    private GlassInfoDialog() {}

    static Dialog create(Activity activity, int iconRes, int titleRes, CharSequence message,
                         int primaryRes, Runnable primaryAction, int secondaryRes, Runnable secondaryAction) {
        float density = activity.getResources().getDisplayMetrics().density;
        Config config = Config.load(activity);
        boolean motion = config.animations && !config.reducedMotion;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setPadding(dp(density, 24), dp(density, 24), dp(density, 24), dp(density, 20));
        container.setBackground(QuickSettingsGlass.dialogSurface(26 * density, density));

        FrameLayout badge = new FrameLayout(activity);
        badge.setBackground(shape(density, 21, 0x18FFFFFF, 0x22FFFFFF));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        CompatApi.imageTint(icon, ColorStateList.valueOf(0xFFF5F7FA));
        badge.addView(icon, new FrameLayout.LayoutParams(dp(density, 20), dp(density, 20), Gravity.CENTER));
        container.addView(badge, new LinearLayout.LayoutParams(dp(density, 42), dp(density, 42)));

        TextView title = new TextView(activity);
        title.setText(titleRes);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17.5f);
        title.setTypeface(UiFont.get(activity, R.string.font_medium));
        title.setGravity(Gravity.CENTER);
        add(container, title, density, 12);

        TextView body = new TextView(activity);
        body.setText(message);
        body.setTextColor(0xBFFFFFFF);
        body.setTextSize(13.5f);
        body.setTypeface(UiFont.get(activity, R.string.font_regular));
        body.setLineSpacing(3 * density, 1f);
        body.setGravity(Gravity.CENTER);
        add(container, body, density, 10);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        add(container, actions, density, 20);

        TextView secondary = null;
        if (secondaryRes != 0) {
            secondary = button(activity, secondaryRes, false, density, motion);
            secondary.setOnClickListener(v -> {
                dialog.dismiss();
                if (secondaryAction != null) secondaryAction.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(density, 42), 1f);
            params.setMarginEnd(dp(density, 10));
            actions.addView(secondary, params);
        }
        TextView primary = button(activity, primaryRes, true, density, motion);
        primary.setOnClickListener(v -> {
            dialog.dismiss();
            if (primaryAction != null) primaryAction.run();
        });
        actions.addView(primary, new LinearLayout.LayoutParams(0, dp(density, 42), 1f));
        if (secondary != null) {
            primary.setNextFocusLeftId(secondary.getId());
            secondary.setNextFocusRightId(primary.getId());
        }
        dialog.setContentView(container);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(.42f);
                int available = activity.getResources().getDisplayMetrics().widthPixels - dp(density, 48);
                window.setLayout(Math.min(available, dp(density, 380)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            primary.requestFocusFromTouch();
            if (motion) {
                container.setAlpha(0f);
                container.setTranslationY(8 * density);
                container.animate().alpha(1f).translationY(0f).setDuration(180)
                        .setInterpolator(CompatApi.easeOut()).start();
            }
        });
        return dialog;
    }

    private static void add(LinearLayout parent, View child, float density, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(density, top);
        parent.addView(child, params);
    }

    private static TextView button(Activity activity, int label, boolean emphasized, float density, boolean motion) {
        TextView view = new TextView(activity);
        view.setId(View.generateViewId());
        view.setText(label);
        view.setTextSize(14f);
        view.setTypeface(UiFont.get(activity, R.string.font_medium));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setFocusable(true);
        view.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(view);
        view.setTextColor(0xFFF5F7FA);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(density, 21, 0xF7FFFFFF, 0));
        background.addState(new int[]{android.R.attr.state_pressed}, shape(density, 21, 0xDDFFFFFF, 0));
        background.addState(new int[]{}, shape(density, 21, emphasized ? 0x22FFFFFF : 0x12FFFFFF, 0x1EFFFFFF));
        view.setBackground(background);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setTextColor(focused ? 0xFF121418 : 0xFFF5F7FA);
            CompatApi.elevation(view, focused ? 6 * density : 0);
            view.animate().cancel();
            float scale = focused ? 1.03f : 1f;
            if (motion) view.animate().scaleX(scale).scaleY(scale).setDuration(160)
                    .setInterpolator(CompatApi.easeOut()).start();
            else { view.setScaleX(scale); view.setScaleY(scale); }
        });
        return view;
    }

    private static GradientDrawable shape(float density, int radius, int fill, int border) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(radius * density);
        result.setStroke(Math.max(1, dp(density, 1)), border);
        return result;
    }

    private static int dp(float density, int value) { return Math.round(density * value); }
}
