package com.gentv.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.List;
import java.util.function.Consumer;

/**
 * Remote-friendly share-code keypad in the Control Center's smoked-glass language. The code cells
 * display input without an Android editor, and a complete code opens on its own.
 */
final class ShareCodeEntryView extends LinearLayout {
    private static final int CODE_LENGTH = ShareCodeLinks.CODE_LENGTH;
    // Control Center ink and glass tones.
    static final int INK_LIGHT = 0xFFF5F7FA;
    static final int INK_SOFT = 0xDBFFFFFF;
    static final int INK_MUTED = 0xA6FFFFFF;
    static final int INK_CAPTION = 0x70FFFFFF;
    static final int INK_DARK = 0xFF121418;
    static final int FOCUS_WHITE = 0xF7FFFFFF;
    private static final int PRESSED_WHITE = 0xDDFFFFFF;
    private static final int GLASS_FILL = 0x17FFFFFF;
    private static final int GLASS_RIM = 0x1EFFFFFF;
    private static final int KEY_FILL = 0x12FFFFFF;
    private static final int OPEN_DELAY_MS = 180;
    private static final int CARET_BLINK_MS = 530;
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final int[] DEFAULT = {};

    private final StringBuilder code = new StringBuilder(CODE_LENGTH);
    private final TextView[] cells = new TextView[CODE_LENGTH];
    private final LinearLayout slots;
    private final Typeface font;
    private final Typeface fontRegular;
    private final float scale;
    private final float density;
    private final boolean motion;
    private final Button firstKey;
    private final Button addButton;
    private final LinearLayout savedCards;
    private final TextView savedHint;
    private final Runnable open;
    private final Consumer<ShareCodeLinks.Link> openSaved;
    private final Consumer<ShareCodeLinks.Link> deleteSaved;
    private boolean opening;
    private boolean caretVisible = true;
    private boolean attached;

    private final Runnable openComplete = new Runnable() {
        @Override public void run() {
            if (code.length() != CODE_LENGTH) { opening = false; return; }
            open.run();
            // The code has been handed over; the keypad is empty when this page shows again.
            code.setLength(0);
            opening = false;
            updateCode();
        }
    };

    private final Runnable blink = new Runnable() {
        @Override public void run() {
            caretVisible = !caretVisible;
            int active = code.length();
            if (active < CODE_LENGTH) cells[active].setText(caretVisible ? "│" : "");
            postDelayed(this, CARET_BLINK_MS);
        }
    };

    ShareCodeEntryView(Context context, String initialCode, Runnable open, Runnable add,
                       Consumer<ShareCodeLinks.Link> openSaved, Consumer<ShareCodeLinks.Link> deleteSaved) {
        super(context);
        this.open = open;
        this.openSaved = openSaved;
        this.deleteSaved = deleteSaved;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.density;
        scale = scale(metrics);
        Config appearance = Config.load(context);
        motion = appearance.animations && !appearance.reducedMotion;
        font = UiFont.get(context, R.string.font_medium);
        fontRegular = UiFont.get(context, R.string.font_regular);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);

        TextView title = label(19, INK_LIGHT, font);
        title.setText(R.string.share_code_input_title);
        CompatApi.letterSpacing(title, -0.01f);
        addView(title, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        TextView hint = label(12, INK_MUTED, fontRegular);
        hint.setText(R.string.share_code_input_hint);
        addView(hint, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 7));

        slots = row();
        slots.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        slots.setClipChildren(false);
        addView(slots, centered(ViewGroup.LayoutParams.WRAP_CONTENT, size(70), 22));
        for (int index = 0; index < CODE_LENGTH; index++) {
            TextView cell = label(31, INK_LIGHT, font);
            cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams params = new LayoutParams(size(62), ViewGroup.LayoutParams.MATCH_PARENT);
            params.leftMargin = index == 0 ? 0 : size(14);
            slots.addView(cell, params);
            cells[index] = cell;
        }

        // One frosted plate holds the keypad, like the Control Center's grouped utilities.
        LinearLayout plate = new LinearLayout(context);
        plate.setOrientation(VERTICAL);
        plate.setClipChildren(false);
        plate.setClipToPadding(false);
        plate.setBackground(surface(size(20), 0x0FFFFFFF, hairline(), 0x17FFFFFF));
        plate.setPadding(size(8), size(8), size(8), size(8));
        addView(plate, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 24));
        // Codes are usually all digits, so the number row comes first and takes the first focus.
        firstKey = addKeys(plate, "1234567890", 0, true);
        addKeys(plate, "ABCDEFGHIJKLM", 6, false);
        addKeys(plate, "NOPQRSTUVWXYZ", 6, false);

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        addView(heading, centered(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 22));
        TextView headingTitle = label(12, INK_MUTED, font);
        headingTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        headingTitle.setText(R.string.share_code_my_codes);
        CompatApi.letterSpacing(headingTitle, 0.02f);
        heading.addView(headingTitle, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        savedHint = label(11, INK_CAPTION, fontRegular);
        savedHint.setText(R.string.share_code_hold_to_delete);
        heading.addView(savedHint, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        savedCards = row();
        savedCards.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        savedCards.setClipChildren(false);
        savedCards.setWeightSum(ShareCodeLinks.MAX_LINKS);
        addView(savedCards, centered(ViewGroup.LayoutParams.MATCH_PARENT, size(60), 10));
        addButton = shareButton(add);

        if (initialCode.length() < CODE_LENGTH && initialCode.matches("[A-Z0-9]*")) {
            code.append(initialCode);
        }
        updateCode();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        removeCallbacks(blink);
        if (motion) postDelayed(blink, CARET_BLINK_MS);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(blink);
        removeCallbacks(openComplete);
        super.onDetachedFromWindow();
    }

    private Button addKeys(LinearLayout plate, String keys, int topMargin, boolean deleteKey) {
        LinearLayout keysRow = row();
        keysRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        keysRow.setClipChildren(false);
        // Each row has thirteen equal columns. Backspace takes the last three on the number row.
        keysRow.setWeightSum(13);
        LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size(40));
        rowParams.topMargin = size(topMargin);
        plate.addView(keysRow, rowParams);
        Button first = null;
        boolean digits = deleteKey;
        for (int index = 0; index < keys.length(); index++) {
            char character = keys.charAt(index);
            Button key = key(String.valueOf(character), digits ? 16 : 14, () -> append(character));
            if (!digits) key.setTextColor(keyText(INK_SOFT));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.leftMargin = index == 0 ? 0 : size(5);
            keysRow.addView(key, params);
            if (index == 0) first = key;
        }
        if (deleteKey) {
            Button delete = key("⌫", 17, this::deleteLast);
            delete.setContentDescription(getContext().getString(R.string.share_code_delete));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3);
            params.leftMargin = size(5);
            keysRow.addView(delete, params);
        }
        return first;
    }

    private Button key(String text, int textSize, Runnable action) {
        Button key = button(getContext(), font, text, textSize(textSize), size(11), action);
        key.setBackground(glassStates(size(11), KEY_FILL, 0));
        springOnFocus(key, 1.12f);
        return key;
    }

    private Button shareButton(Runnable add) {
        Button button = button(getContext(), font, getContext().getString(R.string.share_code_add),
                textSize(13), size(16), add);
        StateListDrawable states = new StateListDrawable();
        states.addState(FOCUSED, surface(size(16), FOCUS_WHITE, 0, 0));
        states.addState(PRESSED, surface(size(16), PRESSED_WHITE, 0, 0));
        GradientDrawable idle = surface(size(16), 0x0AFFFFFF, 0, 0);
        idle.setStroke(hairline() * 2, 0x47FFFFFF, size(5), size(4));
        states.addState(DEFAULT, idle);
        button.setBackground(states);
        button.setMaxLines(1);
        springOnFocus(button, 1.04f);
        return button;
    }

    private View savedCard(ShareCodeLinks.Link link) {
        LinearLayout card = new LinearLayout(getContext());
        card.setId(View.generateViewId());
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(size(10), 0, size(10), 0);
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);
        CompatApi.disableDefaultFocusHighlight(card);
        card.setBackground(glassStates(size(16), GLASS_FILL, GLASS_RIM));
        card.setContentDescription(ShareCodeLinks.formatted(link.code) + " " + link.name);

        TextView codeText = label(17, INK_LIGHT, font);
        codeText.setText(ShareCodeLinks.formatted(link.code));
        CompatApi.letterSpacing(codeText, 0.16f);
        codeText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(codeText, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = label(11, INK_MUTED, fontRegular);
        name.setText(link.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = size(4);
        card.addView(name, nameParams);

        card.setOnClickListener(view -> openSaved.accept(link));
        card.setOnLongClickListener(view -> {
            // A pending hold may run while a deleted card is being removed from the row.
            if (view.getParent() == savedCards) deleteSaved.accept(link);
            return true;
        });
        card.setOnFocusChangeListener((view, focused) -> {
            codeText.setTextColor(focused ? INK_DARK : INK_LIGHT);
            name.setTextColor(focused ? 0x99121418 : INK_MUTED);
            CompatApi.elevation(view, focused ? size(8) : 0f);
            animateScale(view, focused ? 1.04f : 1f);
        });
        return card;
    }

    private void springOnFocus(View view, float focusedScale) {
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((target, focused) -> {
            if (previous != null) previous.onFocusChange(target, focused);
            CompatApi.elevation(target, focused ? size(6) : 0f);
            animateScale(target, focused ? focusedScale : 1f);
        });
    }

    private void animateScale(View view, float target) {
        if (!motion) {
            view.setScaleX(target);
            view.setScaleY(target);
            return;
        }
        spring(view, DynamicAnimation.SCALE_X, R.id.share_code_spring_x, target);
        spring(view, DynamicAnimation.SCALE_Y, R.id.share_code_spring_y, target);
    }

    private static void spring(View view, DynamicAnimation.ViewProperty property, int tagKey, float target) {
        Object existing = view.getTag(tagKey);
        SpringAnimation animation;
        if (existing instanceof SpringAnimation) {
            animation = (SpringAnimation) existing;
        } else {
            animation = new SpringAnimation(view, property, target);
            animation.setSpring(new SpringForce(target).setDampingRatio(0.74f).setStiffness(480f));
            view.setTag(tagKey, animation);
        }
        animation.animateToFinalPosition(target);
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

    private TextView label(int size, int color, Typeface typeface) {
        TextView label = new TextView(getContext());
        label.setTypeface(typeface);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(color);
        label.setTextSize(textSize(size));
        return label;
    }

    private int hairline() {
        return Math.max(1, Math.round(0.75f * density));
    }

    private static ColorStateList keyText(int idle) {
        return new ColorStateList(new int[][]{DISABLED, FOCUSED, DEFAULT}, new int[]{0x52FFFFFF, INK_DARK, idle});
    }

    /** Translucent glass at rest, solid white under focus, as in the Control Center. */
    private static StateListDrawable glassStates(int radius, int fill, int rim) {
        StateListDrawable states = new StateListDrawable();
        states.addState(DISABLED, surface(radius, 0x0AFFFFFF, 0, 0));
        states.addState(FOCUSED, surface(radius, FOCUS_WHITE, 0, 0));
        states.addState(PRESSED, surface(radius, PRESSED_WHITE, 0, 0));
        states.addState(DEFAULT, surface(radius, fill, rim == 0 ? 0 : 1, rim));
        return states;
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
        button.setTextColor(keyText(INK_LIGHT));
        button.setBackground(glassStates(radius, GLASS_FILL, GLASS_RIM));
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

    /** Fits the 640 × 480 dp share-code sheet on small screens; larger screens keep it at 1× with room around it. */
    static float scale(DisplayMetrics metrics) {
        return Math.max(0.25f, Math.min(1f, Math.min((metrics.widthPixels / metrics.density - 32) / 640f,
                (metrics.heightPixels / metrics.density - 96) / 480f)));
    }

    void focusCodeAction() {
        firstKey.requestFocusFromTouch();
    }

    void showSavedLinks(List<ShareCodeLinks.Link> links, boolean focusSavedRow) {
        savedCards.removeAllViews();
        // "Share" always leads the row; this TV's codes follow it.
        if (links.size() < ShareCodeLinks.MAX_LINKS) addSavedCard(addButton);
        // Keep earlier saves accessible if an older build allowed more than four.
        for (int index = 0; index < Math.min(links.size(), ShareCodeLinks.MAX_LINKS); index++) {
            addSavedCard(savedCard(links.get(index)));
        }
        savedHint.setVisibility(links.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (focusSavedRow) savedCards.getChildAt(0).requestFocusFromTouch();
    }

    private void addSavedCard(View card) {
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
        if (opening || code.length() == CODE_LENGTH) return;
        code.append(character);
        updateCode();
        TextView cell = cells[code.length() - 1];
        if (motion) {
            cell.setScaleX(1.10f);
            cell.setScaleY(1.10f);
            cell.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(CompatApi.easeOut()).start();
        }
        if (code.length() == CODE_LENGTH) {
            // Let the last character land on screen before the code opens.
            opening = true;
            postDelayed(openComplete, OPEN_DELAY_MS);
        }
    }

    private void deleteLast() {
        if (opening || code.length() == 0) return;
        code.setLength(code.length() - 1);
        updateCode();
    }

    private void updateCode() {
        caretVisible = true;
        int stroke = hairline();
        for (int index = 0; index < CODE_LENGTH; index++) {
            boolean filled = index < code.length();
            boolean active = index == code.length();
            TextView cell = cells[index];
            cell.setText(filled ? String.valueOf(code.charAt(index)) : active ? "│" : "");
            cell.setTextColor(filled ? INK_LIGHT : 0xB3FFFFFF);
            cell.setBackground(filled ? surface(size(18), 0x24FFFFFF, stroke, 0x2EFFFFFF)
                    : active ? surface(size(18), 0x1AFFFFFF, stroke * 2, 0xD9FFFFFF)
                    : surface(size(18), 0x0DFFFFFF, stroke, 0x1AFFFFFF));
        }
        slots.setContentDescription(code.length() == 0 ? null : code());
        removeCallbacks(blink);
        if (motion && attached) postDelayed(blink, CARET_BLINK_MS);
    }
}
