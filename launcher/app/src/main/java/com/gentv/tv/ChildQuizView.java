package com.gentv.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Native TV layout shared by the lock dialog and the parent preview. */
final class ChildQuizView extends LinearLayout {
    private final LinearLayout card;
    private final TextView line;

    ChildQuizView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LinearLayout host = new LinearLayout(context);
        host.setGravity(Gravity.CENTER_VERTICAL);
        ImageView teacher = new ImageView(context);
        teacher.setImageResource(R.drawable.child_lock_teacher);
        host.addView(teacher, new LayoutParams(dp(118), dp(116)));
        line = text(context.getString(R.string.child_lock_teacher_line), 23);
        host.addView(line, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(host, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(24), dp(16), dp(24), dp(20));
        card.setBackground(shape(0xcb19262b, 22, 0x70d8f0dd, 1));
        android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(dp(120), getResources().getDisplayMetrics().heightPixels * 9 / 10 - dp(116));
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                    available = Math.min(available, MeasureSpec.getSize(heightSpec));
                }
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
            }
        };
        scroll.setClipToPadding(false);
        scroll.addView(card, new android.widget.ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void context(boolean words, int level, int wrong) {
        Context context = getContext();
        if (wrong > 0) line.setText(R.string.child_lock_retry_line);
        TextView category = text(context.getString(R.string.child_lock_category,
                context.getString(words ? R.string.child_lock_words : R.string.child_lock_math),
                context.getString(R.string.child_lock_grade, level)), 14);
        category.setTextColor(0xffd8fa9a);
        card.addView(category);
    }

    void previewDetails(String details) {
        TextView note = text(details, 12);
        note.setTextColor(0xffdbe4dd);
        note.setPadding(0, dp(4), 0, dp(6));
        card.addView(note);
    }

    TextView rest(Runnable parent) {
        line.setText(R.string.child_lock_rest_line);
        TextView label = text("", 31);
        card.addView(label);
        parentButton(parent);
        return label;
    }

    void question(ChildQuestions.Question question, IntConsumer answer, Runnable parent) {
        int picture = switch (question.prompt()) {
            case "picture:apple" -> R.drawable.child_picture_apple;
            case "picture:dog" -> R.drawable.child_picture_dog;
            case "picture:chair" -> R.drawable.child_picture_chair;
            case "picture:blue" -> R.drawable.child_picture_blue;
            case "picture:fish" -> R.drawable.child_picture_fish;
            case "picture:umbrella" -> R.drawable.child_picture_umbrella;
            case "picture:book" -> R.drawable.child_picture_book;
            case "picture:ball" -> R.drawable.child_picture_ball;
            case "picture:sun" -> R.drawable.child_picture_sun;
            case "picture:moon" -> R.drawable.child_picture_moon;
            case "picture:tree" -> R.drawable.child_picture_tree;
            case "picture:cat" -> R.drawable.child_picture_cat;
            case "picture:egg" -> R.drawable.child_picture_egg;
            case "picture:bread" -> R.drawable.child_picture_bread;
            case "picture:red" -> R.drawable.child_picture_red;
            case "picture:green" -> R.drawable.child_picture_green;
            case "picture:yellow" -> R.drawable.child_picture_yellow;
            default -> 0;
        };
        TextView title = text(getContext().getString(
                picture == 0 ? R.string.child_lock_choose : R.string.child_lock_picture), 17);
        card.addView(title);
        if (picture == 0) {
            TextView prompt = text(question.prompt(), question.prompt().length() > 25 ? 23 : 39);
            prompt.setPadding(0, dp(4), 0, dp(12));
            card.addView(prompt);
        } else {
            ImageView image = new ImageView(getContext());
            image.setImageResource(picture);
            image.setContentDescription(getContext().getString(R.string.child_lock_picture));
            card.addView(image, new LayoutParams(dp(95), dp(72)));
        }
        boolean longOptions = false;
        for (String option : question.options()) if (option.length() > 42) longOptions = true;
        Button first = null;
        LinearLayout row = null;
        for (int index = 0; index < question.options().size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(getContext());
                card.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            final int choice = index;
            Button button = action(question.options().get(index));
            button.setTextSize(longOptions ? 14 : 19);
            button.setMaxLines(longOptions ? 3 : 2);
            button.setOnClickListener(view -> answer.accept(choice));
            LayoutParams params = new LayoutParams(0, dp(longOptions ? 72 : 51), 1);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(button, params);
            if (first == null) first = button;
        }
        parentButton(parent);
        if (first != null) first.requestFocus();
    }

    private void parentButton(Runnable parent) {
        if (parent == null) return;
        Button button = action(getContext().getString(R.string.child_lock_parent));
        button.setOnClickListener(view -> parent.run());
        card.addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(43)));
    }

    private Button action(String label) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(ColorStateList.valueOf(Color.WHITE));
        button.setTextSize(19);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), 0, dp(12), 0);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(0xff3c503d, 11, 0xffdbff9c, 3));
        background.addState(new int[0], shape(0xff2b383c, 11, 0xff647875, 1));
        button.setBackground(background);
        return button;
    }

    private TextView text(String label, int size) {
        TextView text = new TextView(getContext());
        text.setTextColor(0xfff7faf2);
        text.setTextSize(size);
        text.setText(label);
        return text;
    }

    private GradientDrawable shape(int color, int radius, int outline, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(width), outline);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
