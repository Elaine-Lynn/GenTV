package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Folder sheets in the Control Center's glass: pick a folder, name one, open one, manage one. Rows and tiles
 * rest as quiet glass and turn solid white under focus, as Control Center tiles do.
 */
final class FolderSheet {
    static final class Row {
        final Drawable icon;
        /** Tint the icon like text (a glyph) instead of showing it as artwork (an app icon). */
        final boolean glyph;
        final String title;
        final String detail;
        final boolean destructive;
        boolean checked;

        Row(Drawable icon, boolean glyph, String title, String detail, boolean destructive) {
            this.icon = icon;
            this.glyph = glyph;
            this.title = title;
            this.detail = detail;
            this.destructive = destructive;
        }
    }

    interface Pick { void pick(int index); }

    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final int[] IDLE = {};
    private static final int AMBER = 0xFFF5B85A;

    private final Activity activity;
    private final float unit;
    private final boolean motion;
    private final Typeface medium;
    private final Typeface regular;
    private final Dialog dialog;
    private final LinearLayout sheet;
    private final Runnable style;

    private FolderSheet(Activity activity, int widthDp) {
        this.activity = activity;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        float scale = Math.max(0.5f, Math.min(1f, (metrics.widthPixels / metrics.density - 48) / widthDp));
        unit = metrics.density * scale;
        Config config = Config.load(activity);
        motion = config.animations && !config.reducedMotion;
        medium = UiFont.get(activity, R.string.font_medium);
        regular = UiFont.get(activity, R.string.font_regular);
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        // Clip to the sheet: a long list scrolls inside it instead of drawing over the actions below.
        sheet.setClipChildren(true);
        sheet.setClipToPadding(false);
        sheet.setPadding(px(28), px(26), px(28), px(24));
        sheet.setBackground(QuickSettingsGlass.dialogSurface(px(28), Math.max(1, px(1))));
        dialog.setContentView(sheet);
        style = () -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(.55f);
                int available = metrics.widthPixels - px(48);
                window.setLayout(Math.min(available, px(widthDp)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            if (motion) {
                sheet.setAlpha(0f);
                sheet.setTranslationY(px(10));
                sheet.setScaleX(.97f);
                sheet.setScaleY(.97f);
                sheet.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(CompatApi.easeOut()).start();
            }
        };
    }

    /** Shows the sheet: window styling first, then focus on its first control. */
    private void show(View focus) {
        dialog.setOnShowListener(shown -> {
            style.run();
            if (focus != null) focus.requestFocus();
        });
        dialog.show();
    }

    // ---------------------------------------------------------------- sheets

    /** A list of rows; picking one closes the sheet. */
    static Dialog list(Activity activity, String title, String subtitle, List<Row> rows, Pick pick) {
        FolderSheet s = new FolderSheet(activity, 460);
        s.header(title, subtitle);
        LinearLayout list = s.scrollingColumn(s.px(14), 420);
        View first = null;
        for (int index = 0; index < rows.size(); index++) {
            int at = index;
            View row = s.row(rows.get(index), () -> {
                s.dialog.dismiss();
                pick.pick(at);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.px(56));
            if (index > 0) params.topMargin = s.px(8);
            list.addView(row, params);
            if (first == null) first = row;
        }
        s.show(first);
        return s.dialog;
    }

    /** Rows with check marks; Done hands back the rows that are checked. */
    static Dialog choose(Activity activity, String title, String subtitle, List<Row> rows, Runnable done) {
        FolderSheet s = new FolderSheet(activity, 520);
        s.header(title, subtitle);
        LinearLayout list = s.scrollingColumn(s.px(14), 360);
        View first = null;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            View[] view = new View[1];
            view[0] = s.row(row, () -> {
                row.checked = !row.checked;
                s.mark(view[0], row.checked);
            });
            s.mark(view[0], row.checked);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.px(54));
            if (index > 0) params.topMargin = s.px(6);
            list.addView(view[0], params);
            if (first == null) first = view[0];
        }
        Button[] buttons = s.actions(R.string.folder_cancel, R.string.folder_done, s.dialog::dismiss, () -> {
            s.dialog.dismiss();
            done.run();
        });
        View focus = first == null ? buttons[1] : first;
        s.show(focus);
        return s.dialog;
    }

    /** A name field, one-press suggestions, and Cancel / Done. Done is offered only for a usable name. */
    static Dialog name(Activity activity, String title, String initial, Consumer<String> done) {
        FolderSheet s = new FolderSheet(activity, 500);
        s.header(title, activity.getString(R.string.folder_name_subtitle));

        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        field.setText(initial);
        field.setSelection(field.getText().length());
        field.setHint(R.string.folder_name_hint);
        field.setTextColor(ShareCodeEntryView.INK_LIGHT);
        field.setHintTextColor(ShareCodeEntryView.INK_CAPTION);
        field.setTextSize(s.sp(18));
        field.setTypeface(s.medium);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setPadding(s.px(18), 0, s.px(18), 0);
        field.setGravity(Gravity.CENTER_VERTICAL);
        StateListDrawable fieldStates = new StateListDrawable();
        fieldStates.addState(FOCUSED, ShareCodeEntryView.surface(s.px(16), 0x1FFFFFFF, Math.max(1, s.px(2)), 0xE6FFFFFF));
        fieldStates.addState(IDLE, ShareCodeEntryView.surface(s.px(16), 0x14FFFFFF, Math.max(1, s.px(1)), 0x26FFFFFF));
        field.setBackground(fieldStates);
        CompatApi.disableDefaultFocusHighlight(field);
        s.sheet.addView(field, s.margins(ViewGroup.LayoutParams.MATCH_PARENT, s.px(56), 18));

        // Suggestions: a remote can name a folder without the on-screen keyboard.
        String[] names = activity.getResources().getStringArray(R.array.folder_name_suggestions);
        LinearLayout chips = new LinearLayout(activity);
        chips.setClipChildren(false);
        s.sheet.addView(chips, s.margins(ViewGroup.LayoutParams.MATCH_PARENT, s.px(38), 14));
        View firstChip = null;
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            Button chip = s.pill(name, false, () -> {
                field.setText(name);
                field.setSelection(name.length());
            });
            chip.setTextSize(s.sp(13));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (index > 0) params.setMarginStart(s.px(8));
            chips.addView(chip, params);
            if (firstChip == null) firstChip = chip;
        }

        Button[] buttons = s.actions(R.string.folder_cancel, R.string.folder_done, s.dialog::dismiss, null);
        Button confirm = buttons[1];
        Runnable submit = () -> {
            String value = field.getText().toString().trim();
            try {
                DesktopExtras.name(value);
            } catch (IllegalArgumentException invalid) {
                return;
            }
            s.dialog.dismiss();
            done.accept(value);
        };
        confirm.setOnClickListener(v -> submit.run());
        Runnable refresh = () -> confirm.setEnabled(!field.getText().toString().trim().isEmpty());
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { refresh.run(); }
        });
        field.setOnEditorActionListener((view, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action == EditorInfo.IME_ACTION_DONE || enter) {
                submit.run();
                return true;
            }
            return false;
        });
        refresh.run();
        // A new folder starts on the suggestions (no keyboard pops up); a rename starts on Done.
        View focus = initial.isEmpty() && firstChip != null ? firstChip : confirm;
        s.show(focus);
        return s.dialog;
    }

    /** An open folder: its apps as tiles, and the way into managing it. */
    static Dialog grid(Activity activity, String title, String subtitle, List<Row> apps, Pick open, Runnable manage) {
        FolderSheet s = new FolderSheet(activity, 640);
        s.header(title, subtitle);
        View first = null;
        if (apps.isEmpty()) {
            TextView empty = s.text(activity.getString(R.string.desktop_folder_empty), 13, ShareCodeEntryView.INK_MUTED, s.regular);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, s.px(28), 0, s.px(28));
            s.sheet.addView(empty, s.margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        } else {
            LinearLayout rows = s.scrollingColumn(s.px(16), 330);
            int columns = 4;
            LinearLayout line = null;
            for (int index = 0; index < apps.size(); index++) {
                if (index % columns == 0) {
                    line = new LinearLayout(activity);
                    line.setClipChildren(false);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.px(118));
                    if (index > 0) params.topMargin = s.px(12);
                    rows.addView(line, params);
                }
                int at = index;
                View tile = s.tile(apps.get(index), () -> {
                    s.dialog.dismiss();
                    open.pick(at);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                if (index % columns > 0) params.setMarginStart(s.px(12));
                line.addView(tile, params);
                if (first == null) first = tile;
            }
            // Keep the last row's tiles the same width as the others.
            for (int pad = apps.size() % columns == 0 ? 0 : columns - apps.size() % columns; pad > 0; pad--) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                params.setMarginStart(s.px(12));
                line.addView(new View(activity), params);
            }
        }
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER);
        footer.setClipChildren(false);
        s.sheet.addView(footer, s.margins(ViewGroup.LayoutParams.MATCH_PARENT, s.px(42), 20));
        Button manageButton = s.pill(activity.getString(R.string.folder_manage), false, () -> {
            s.dialog.dismiss();
            manage.run();
        });
        manageButton.setPadding(s.px(22), 0, s.px(22), 0);
        footer.addView(manageButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View focus = first == null ? manageButton : first;
        s.show(focus);
        return s.dialog;
    }

    // ---------------------------------------------------------------- parts

    private void header(String title, String subtitle) {
        TextView heading = text(title, 20, ShareCodeEntryView.INK_LIGHT, medium);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        CompatApi.letterSpacing(heading, -0.01f);
        sheet.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12.5f, ShareCodeEntryView.INK_MUTED, regular);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sheet.addView(sub, margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6));
        }
    }

    /**
     * A column that scrolls once it passes maxHeightDp, or what the screen leaves beside the header and actions.
     * The padding gives focused rows room to lift inside the clip.
     */
    private LinearLayout scrollingColumn(int top, int maxHeightDp) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int maxHeight = Math.max(px(120), Math.min(px(maxHeightDp), metrics.heightPixels - px(250)));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        int inset = px(6);
        column.setPadding(inset, inset, inset, inset);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int width, int height) {
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(20));
        scroll.setClipToPadding(false);
        scroll.addView(column);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top - inset;
        params.leftMargin = params.rightMargin = -inset;
        sheet.addView(scroll, params);
        return column;
    }

    private View row(Row model, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), 0, px(16), 0);
        focusable(row, px(16), 0x10FFFFFF, 0x1AFFFFFF, action, 1.02f);
        int ink = model.destructive ? AMBER : ShareCodeEntryView.INK_LIGHT;
        ColorStateList titleInk = new ColorStateList(new int[][]{FOCUSED, IDLE}, new int[]{ShareCodeEntryView.INK_DARK, ink});
        if (model.icon != null) {
            FrameLayout well = new FrameLayout(activity);
            well.setDuplicateParentStateEnabled(true);
            ImageView icon = new ImageView(activity);
            icon.setDuplicateParentStateEnabled(true);
            icon.setImageDrawable(model.icon.mutate());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (model.glyph) {
                StateListDrawable disc = new StateListDrawable();
                disc.addState(FOCUSED, ShareCodeEntryView.surface(px(10), 0x14121418, 0, 0));
                disc.addState(IDLE, ShareCodeEntryView.surface(px(10), 0x14FFFFFF, 0, 0));
                well.setBackground(disc);
                CompatApi.imageTint(icon, titleInk);
                well.addView(icon, new FrameLayout.LayoutParams(px(18), px(18), Gravity.CENTER));
            } else {
                well.addView(icon, new FrameLayout.LayoutParams(px(32), px(32), Gravity.CENTER));
            }
            row.addView(well, new LinearLayout.LayoutParams(px(34), px(34)));
        }
        TextView title = text(model.title, 15, ink, medium);
        title.setDuplicateParentStateEnabled(true);
        title.setTextColor(titleInk);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (model.icon != null) titleParams.setMarginStart(px(14));
        row.addView(title, titleParams);
        if (model.detail != null && !model.detail.isEmpty()) {
            TextView detail = text(model.detail, 12, ShareCodeEntryView.INK_MUTED, regular);
            detail.setDuplicateParentStateEnabled(true);
            detail.setTextColor(new ColorStateList(new int[][]{FOCUSED, IDLE}, new int[]{0x99121418, ShareCodeEntryView.INK_MUTED}));
            detail.setSingleLine(true);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailParams.setMarginStart(px(12));
            row.addView(detail, detailParams);
        }
        row.setContentDescription(model.detail == null ? model.title : model.title + ", " + model.detail);
        return row;
    }

    /** A check disc at the end of a choose row. */
    private void mark(View row, boolean checked) {
        LinearLayout line = (LinearLayout) row;
        View last = line.getChildAt(line.getChildCount() - 1);
        TextView check;
        if (last instanceof TextView && "check".equals(last.getTag())) {
            check = (TextView) last;
        } else {
            check = text("✓", 13, 0xFFFFFFFF, medium);
            check.setTag("check");
            check.setGravity(Gravity.CENTER);
            check.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(24), px(24));
            params.setMarginStart(px(12));
            line.addView(check, params);
        }
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        if (checked) {
            disc.setColor(0xFF69B9F0);
        } else {
            disc.setColor(0x00000000);
            // Neutral grey reads on both the quiet row and the white focused one.
            disc.setStroke(Math.max(1, px(1.5f)), 0xFF8A919E);
        }
        check.setBackground(disc);
        check.setText(checked ? "✓" : "");
        row.setSelected(checked);
    }

    private View tile(Row model, Runnable action) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(px(8), px(12), px(8), px(10));
        focusable(tile, px(18), 0x10FFFFFF, 0x1AFFFFFF, action, 1.05f);
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(model.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LinearLayout.LayoutParams(px(54), px(54)));
        TextView label = text(model.title, 13, ShareCodeEntryView.INK_LIGHT, medium);
        label.setDuplicateParentStateEnabled(true);
        label.setTextColor(new ColorStateList(new int[][]{FOCUSED, IDLE},
                new int[]{ShareCodeEntryView.INK_DARK, ShareCodeEntryView.INK_LIGHT}));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label, margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        tile.setContentDescription(model.title);
        return tile;
    }

    /** Cancel and a primary action, right-aligned. */
    private Button[] actions(int secondaryRes, int primaryRes, Runnable secondary, Runnable primary) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false);
        sheet.addView(actions, margins(ViewGroup.LayoutParams.MATCH_PARENT, px(44), 22));
        Button cancel = pill(activity.getString(secondaryRes), false, secondary);
        actions.addView(cancel, new LinearLayout.LayoutParams(px(120), ViewGroup.LayoutParams.MATCH_PARENT));
        Button confirm = pill(activity.getString(primaryRes), true, primary == null ? () -> { } : primary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(140), ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMarginStart(px(12));
        actions.addView(confirm, params);
        cancel.setNextFocusRightId(confirm.getId());
        confirm.setNextFocusLeftId(cancel.getId());
        return new Button[]{cancel, confirm};
    }

    private Button pill(String label, boolean emphasized, Runnable action) {
        Button button = ShareCodeEntryView.button(activity, medium, label, sp(14), px(20), action);
        if (emphasized) {
            StateListDrawable states = new StateListDrawable();
            states.addState(DISABLED, ShareCodeEntryView.surface(px(20), 0x0AFFFFFF, 0, 0));
            states.addState(FOCUSED, ShareCodeEntryView.surface(px(20), ShareCodeEntryView.FOCUS_WHITE, 0, 0));
            states.addState(PRESSED, ShareCodeEntryView.surface(px(20), 0xDDFFFFFF, 0, 0));
            states.addState(IDLE, ShareCodeEntryView.surface(px(20), 0x3369B9F0, 1, 0x5569B9F0));
            button.setBackground(states);
        }
        button.setOnFocusChangeListener((view, focused) -> lift(view, focused, 1.04f));
        return button;
    }

    private void focusable(View view, int radius, int fill, int rim, Runnable action, float liftTo) {
        view.setFocusable(true);
        view.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(view);
        StateListDrawable states = new StateListDrawable();
        states.addState(FOCUSED, ShareCodeEntryView.surface(radius, ShareCodeEntryView.FOCUS_WHITE, 0, 0));
        states.addState(PRESSED, ShareCodeEntryView.surface(radius, 0xDDFFFFFF, 0, 0));
        states.addState(IDLE, ShareCodeEntryView.surface(radius, fill, 1, rim));
        view.setBackground(states);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> lift(v, focused, liftTo));
    }

    private void lift(View view, boolean focused, float to) {
        CompatApi.elevation(view, focused ? px(8) : 0f);
        float scale = focused ? to : 1f;
        if (motion) view.animate().scaleX(scale).scaleY(scale).setDuration(150).setInterpolator(CompatApi.easeOut()).start();
        else { view.setScaleX(scale); view.setScaleY(scale); }
    }

    private TextView text(String value, float sp, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp(sp));
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = px(topDp);
        return params;
    }

    private float sp(float value) {
        return value * unit / activity.getResources().getDisplayMetrics().density;
    }

    private int px(float dp) {
        return Math.round(dp * unit);
    }
}
