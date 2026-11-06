package com.gentv.tv;

import android.content.SharedPreferences;

/** One grade applies to both subjects. Pending rounds keep their own saved grade. */
final class ChildGrade {
    static final String KEY = "grade";

    private ChildGrade() {}

    static int read(SharedPreferences state) {
        int grade = resolve(state.getInt(KEY, -1), state.getInt("math_level", -1),
                state.getInt("word_level", -1));
        if (!state.contains(KEY)) state.edit().putInt(KEY, grade).apply();
        return grade;
    }

    static int resolve(int grade, int math, int words) {
        int selected = grade != -1 ? grade : math != -1 ? math : words != -1 ? words : 3;
        return Math.max(1, Math.min(6, selected));
    }
}
