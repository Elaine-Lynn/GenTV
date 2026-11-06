package com.gentv.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native styling for the child-mode settings and parent password page. */
final class ChildStyle {
    static final int PAPER = 0xfffffdf7;
    static final int INK = 0xff252342;
    static final int MUTED = 0xff65617b;
    static final int LINE = 0xffe5e1d7;
    static final int GREEN = 0xffcefa57;
    static final int FOCUS = 0xff6443dc;

    private ChildStyle() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, CharSequence label, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setFontFeatureSettings("kern");
        if (bold) text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return text;
    }

    static GradientDrawable shape(Context context, int fill, int edge, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radius));
        if (stroke != 0) shape.setStroke(dp(context, stroke), edge);
        return shape;
    }

    static LayerDrawable raised(Context context, int fill, int edge, int radius) {
        LayerDrawable drawable = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                shape(context, edge, edge, radius, 0),
                shape(context, fill, edge, radius, 1)});
        drawable.setLayerInset(1, 0, 0, 0, dp(context, 3));
        return drawable;
    }

    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setTextColor(primary ? 0xfffffff7 : INK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 8), 0, dp(context, 8), dp(context, 2));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                raised(context, primary ? 0xff4a396a : 0xfff0eaff, FOCUS, 10));
        states.addState(new int[]{android.R.attr.state_selected},
                raised(context, 0xffebffad, 0xffa8cc56, 10));
        states.addState(new int[]{android.R.attr.state_pressed},
                raised(context, primary ? 0xff403454 : 0xffedf7d9, primary ? INK : LINE, 10));
        states.addState(new int[0],
                raised(context, primary ? INK : 0xffffffff, primary ? 0xff111022 : LINE, 10));
        button.setBackground(states);
        button.setStateListAnimator(null);
        return button;
    }

    static LinearLayout page(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(PAPER);
        page.setPadding(dp(context, 36), dp(context, 20), dp(context, 36), dp(context, 14));
        return page;
    }

    static void header(LinearLayout page) {
        Context context = page.getContext();
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text(context, "▶", 16, GREEN, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(context, INK, INK, 8, 0));
        header.addView(logo, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 26)));
        TextView title = text(context, context.getString(R.string.child_lock_brand), 17, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(context, 32), 1);
        titleParams.setMarginStart(dp(context, 10));
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, titleParams);
        header.addView(text(context, context.getString(R.string.child_lock_parent_settings), 11, MUTED, true));
        page.addView(header);
        android.view.View rule = new android.view.View(context);
        rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(context, 1));
        params.topMargin = dp(context, 13);
        page.addView(rule, params);
    }
}
