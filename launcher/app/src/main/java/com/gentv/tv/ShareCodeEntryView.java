package com.gentv.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

/** Remote-friendly share-code keypad. The code cells display input without an Android editor. */
final class ShareCodeEntryView extends LinearLayout {
    private static final int CODE_LENGTH = ShareCodeLinks.CODE_LENGTH;
    private static final int TEXT_COLOR = 0xFFF5F7FA;
    private static final int MUTED_TEXT = 0xFFB4B6BE;
    private static final int BUTTON_BACKGROUND = 0xFF25262D;
    private static final int FOCUS_TEXT = 0xFF121418;
    private static final int FOCUS_BACKGROUND = 0xFFF5F7FA;
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] DEFAULT = {};

    private final StringBuilder code = new StringBuilder(CODE_LENGTH);
    private final TextView[] cells = new TextView[CODE_LENGTH];
    private final LinearLayout slots;
    private final Typeface font;
    private final float scale;
    private final float density;
    private final Button firstKey;
    private final Button openButton;
    private final Button addButton;
    private final LinearLayout savedCards;
    private final Button clearButton;
    private final Consumer<ShareCodeLinks.Link> openSaved;
    private final Consumer<ShareCodeLinks.Link> deleteSaved;

    ShareCodeEntryView(Context context, String initialCode, Runnable open, Runnable add,
                       Consumer<ShareCodeLinks.Link> openSaved, Consumer<ShareCodeLinks.Link> deleteSaved) {
        super(context);
        this.openSaved = openSaved;
        this.deleteSaved = deleteSaved;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.density;
        scale = scale(metrics);
        font = UiFont.get(context, R.string.font_medium);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        TextView hint = label(12, MUTED_TEXT);
        hint.setText(R.string.share_code_input_hint);
        addView(hint, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 5));

        slots = row();
        slots.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        addView(slots, centered(ViewGroup.LayoutParams.WRAP_CONTENT, size(53), 18));
        for (int index = 0; index < CODE_LENGTH; index++) {
            if (index == 3) {
                TextView divider = label(22, 0xFF777981);
                divider.setText("–");
                divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                slots.addView(divider, new LayoutParams(size(24), ViewGroup.LayoutParams.MATCH_PARENT));
            }
            TextView cell = label(27, TEXT_COLOR);
            cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams params = new LayoutParams(size(43), ViewGroup.LayoutParams.MATCH_PARENT);
            params.leftMargin = index == 0 || index == 3 ? 0 : size(7);
            slots.addView(cell, params);
            cells[index] = cell;
        }

        firstKey = addKeys("ABCDEFGHIJKLM", 17, false);
        addKeys("NOPQRSTUVWXYZ", 5, false);
        addKeys("0123456789", 5, true);

        LinearLayout actions = row();
        addView(actions, centered(ViewGroup.LayoutParams.WRAP_CONTENT, size(32), 15));
        clearButton = button(context.getString(R.string.share_code_clear), 13, 8, () -> {
            code.setLength(0);
            updateCode();
        });
        actions.addView(clearButton, new LayoutParams(size(88), ViewGroup.LayoutParams.MATCH_PARENT));
        openButton = button(context.getString(R.string.share_code_open), 13, 8, open);
        LayoutParams openParams = new LayoutParams(size(88), ViewGroup.LayoutParams.MATCH_PARENT);
        openParams.leftMargin = size(10);
        actions.addView(openButton, openParams);

        TextView heading = label(13, MUTED_TEXT);
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setText(R.string.share_code_my_codes);
        addView(heading, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 20));
        savedCards = row();
        savedCards.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        savedCards.setWeightSum(ShareCodeLinks.MAX_LINKS);
        addView(savedCards, centered(ViewGroup.LayoutParams.MATCH_PARENT, size(58), 8));
        addButton = button(context.getString(R.string.share_code_add), 14, 12, add);
        addButton.setMaxLines(2);

        if (initialCode.length() <= CODE_LENGTH && initialCode.matches("[A-Z0-9]*")) {
            code.append(initialCode);
        }
        updateCode();
    }

    private Button addKeys(String keys, int topMargin, boolean deleteKey) {
        LinearLayout keysRow = row();
        keysRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        // Each row has thirteen columns. Backspace takes the last three on the number row.
        int width = 13 * size(36) + 12 * size(5);
        addView(keysRow, centered(width, size(31), topMargin));
        Button first = null;
        for (int index = 0; index < keys.length(); index++) {
            char character = keys.charAt(index);
            Button key = button(String.valueOf(character), 14, 7, () -> append(character));
            LayoutParams params = new LayoutParams(size(36), ViewGroup.LayoutParams.MATCH_PARENT);
            params.leftMargin = index == 0 ? 0 : size(5);
            keysRow.addView(key, params);
            if (index == 0) first = key;
        }
        if (deleteKey) {
            Button delete = button("⌫", 16, 7, this::deleteLast);
            delete.setContentDescription(getContext().getString(R.string.share_code_delete));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.leftMargin = size(5);
            keysRow.addView(delete, params);
        }
        return first;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LayoutParams centered(int width, int height, int topMargin) {
        LayoutParams params = new LayoutParams(width, height);
        params.topMargin = size(topMargin);
        return params;
    }

    private TextView label(int size, int color) {
        TextView label = new TextView(getContext());
        label.setTypeface(font);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(color);
        label.setTextSize(textSize(size));
        return label;
    }

    private Button button(String text, int textSize, int radius, Runnable action) {
        return button(getContext(), font, text, textSize(textSize), size(radius), action);
    }

    static Button button(Context context, Typeface font, String text, float textSize, int radius, Runnable action) {
        Button button = new Button(context);
        button.setId(View.generateViewId());
        button.setTypeface(font);
        button.setAllCaps(false);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setGravity(Gravity.CENTER);
        button.setText(text);
        button.setTextSize(textSize);
        button.setTextColor(new ColorStateList(new int[][]{DISABLED, FOCUSED, DEFAULT},
                new int[]{0xFF73757D, FOCUS_TEXT, TEXT_COLOR}));
        StateListDrawable states = new StateListDrawable();
        states.addState(DISABLED, surface(radius, 0xFF1B1C22, 0, 0));
        states.addState(FOCUSED, surface(radius, FOCUS_BACKGROUND, 0, 0));
        states.addState(DEFAULT, surface(radius, BUTTON_BACKGROUND, 0, 0));
        button.setBackground(states);
        CompatApi.disableDefaultFocusHighlight(button);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    static GradientDrawable surface(int radius, int color, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    int size(int value) {
        return Math.round(value * density * scale);
    }

    float textSize(int value) {
        return value * scale;
    }

    static float scale(DisplayMetrics metrics) {
        return Math.max(0.25f, Math.min(1f, Math.min(metrics.widthPixels / metrics.density / 720f,
                (metrics.heightPixels / metrics.density - 32) / 420f)));
    }

    void focusCodeAction() {
        (openButton.isEnabled() ? openButton : firstKey).requestFocusFromTouch();
    }

    void showSavedLinks(List<ShareCodeLinks.Link> links, boolean focusSavedRow) {
        savedCards.removeAllViews();
        if (links.size() < ShareCodeLinks.MAX_LINKS) addSavedCard(addButton);
        // Keep earlier saves accessible if an older build allowed more than four.
        for (int index = 0; index < Math.min(links.size(), ShareCodeLinks.MAX_LINKS); index++) {
            ShareCodeLinks.Link link = links.get(index);
            Button card = button(ShareCodeLinks.formatted(link.code) + "\n" + link.name, 13, 12,
                    () -> openSaved.accept(link));
            card.setMaxLines(2);
            card.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.setPadding(size(8), 0, size(8), 0);
            card.setOnLongClickListener(view -> {
                // A pending hold may run while a deleted card is being removed from the row.
                if (view.getParent() == savedCards) deleteSaved.accept(link);
                return true;
            });
            addSavedCard(card);
        }
        View firstCard = savedCards.getChildAt(0);
        clearButton.setNextFocusDownId(firstCard.getId());
        openButton.setNextFocusDownId(firstCard.getId());
        if (focusSavedRow) firstCard.requestFocusFromTouch();
    }

    private void addSavedCard(Button card) {
        card.setNextFocusUpId(clearButton.getId());
        LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (savedCards.getChildCount() != 0) params.setMarginStart(size(10));
        savedCards.addView(card, params);
    }

    boolean performSavedCardLongClick(View focused) {
        if (focused == null || focused.getParent() != savedCards || !focused.isLongClickable()) return false;
        focused.setPressed(false);
        return focused.performLongClick();
    }

    String code() {
        return code.toString();
    }

    boolean handleKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            deleteLast();
            return true;
        }
        int character = Character.toUpperCase(event.getUnicodeChar());
        if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
            if (event.getRepeatCount() == 0) append((char) character);
            return true;
        }
        return false;
    }

    private void append(char character) {
        if (code.length() == CODE_LENGTH) return;
        code.append(character);
        updateCode();
        if (code.length() == CODE_LENGTH) focusCodeAction();
    }

    private void deleteLast() {
        if (code.length() == 0) return;
        code.setLength(code.length() - 1);
        updateCode();
    }

    private void updateCode() {
        for (int index = 0; index < CODE_LENGTH; index++) {
            boolean filled = index < code.length();
            boolean active = index == code.length();
            cells[index].setText(filled ? String.valueOf(code.charAt(index)) : active ? "│" : "");
            cells[index].setBackground(surface(size(10), filled || active ? 0xFF303138 : 0xFF202127,
                    size(1), active ? FOCUS_BACKGROUND : 0xFF3A3B43));
        }
        slots.setContentDescription(code.length() == 0 ? null : code());
        if (openButton.hasFocus() && code.length() != CODE_LENGTH) firstKey.requestFocus();
        openButton.setEnabled(code.length() == CODE_LENGTH);
    }
}
