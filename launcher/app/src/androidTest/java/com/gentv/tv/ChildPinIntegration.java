package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Exercises the actual settings entry and password dialogs in an isolated test installation. */
public final class ChildPinIntegration extends Instrumentation {
    private int checks;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity current = null;
        int status = Activity.RESULT_OK;
        try {
            check(getTargetContext().getPackageName().endsWith(".childpinqa"), "isolated package required");
            ChildLock.state(getTargetContext()).edit().clear().commit();
            current = open();
            Activity setup = current;
            runOnMainSync(() -> check((password(setup).getWindow().getAttributes().flags
                    & android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) == 0,
                    "password window allows the input method"));
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                check(waitForKeyboard(setup, true), "keyboard opens on entering password page");
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
                check(waitForKeyboard(setup, false), "keyboard can be dismissed");
                check(waitForPasswordFocus(setup), "password regains focus after keyboard dismissal");
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
                boolean reopened = waitForKeyboard(setup, true);
                capture("child-keyboard-after-ok.png");
                check(reopened, "remote OK reopens the keyboard");
            }
            capture("child-parent-native.png");
            runOnMainSync(() -> {
                AlertDialog dialog = password(setup);
                check(dialog.isShowing(), "first entry requires password creation");
                check(!authorized(setup), "settings initially protected");
                List<EditText> fields = inputs(dialog);
                check(fields.size() == 2, "creation requires confirmation");
                fields.get(0).setText("12");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(dialog.isShowing() && !authorized(setup), "short password rejected");
                fields.get(0).setText("483726");
                fields.get(1).setText("483725");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(dialog.isShowing() && !authorized(setup), "mismatched confirmation rejected");
                fields.get(1).setText("483726");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(authorized(setup), "matching password opens settings");
            });
            waitForIdleSync();
            capture("child-settings-native.png");
            runOnMainSync(() -> ((android.widget.Button) field(setup, "playerButton")).performClick());
            waitForIdleSync();
            capture("child-apps-native.png");
            runOnMainSync(() -> {
                AlertDialog picker = (AlertDialog) field(setup, "playerDialog");
                android.widget.ListView list = picker.getListView();
                check(list.getAdapter().getCount() >= 2, "multiple app choices available");
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
                list.performItemClick(list.getChildAt(1), 1, list.getAdapter().getItemId(1));
                picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(ChildApps.read(ChildLock.state(getTargetContext())).size() == 2, "multiple apps saved");
                ((android.widget.Button) field(setup, "playerButton")).performClick();
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                AlertDialog again = (AlertDialog) field(setup, "playerDialog");
                check(again.getListView().getCheckedItemCount() == 2, "multiple choices restored");
                again.getListView().performItemClick(again.getListView().getChildAt(0), 0,
                        again.getListView().getAdapter().getItemId(0));
                again.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                check(ChildApps.read(ChildLock.state(getTargetContext())).size() == 2, "cancel leaves choices intact");
            });
            runOnMainSync(() -> {
                check(!ChildWatchService.isConnected(), "test has no child accessibility service");
                android.widget.Button enable = (android.widget.Button) field(setup, "enableButton");
                enable.performClick();
                check(ChildLock.state(getTargetContext()).getBoolean(ChildSettingsActivity.ENABLED, false),
                        "child mode can be enabled without accessibility settings/service");
                enable.performClick();
                check(!ChildLock.state(getTargetContext()).getBoolean(ChildSettingsActivity.ENABLED, false),
                        "child mode can still be disabled");
            });
            verifyPicturePreview(setup);
            verifyLongPreview(setup);
            runOnMainSync(() -> {
                ChildQuizView quiz = new ChildQuizView(setup);
                quiz.context(false, 3, 0);
                quiz.question(ChildQuestions.create(false, 3, 42, 0), index -> {}, null);
                ChildQuizDialog preview = new ChildQuizDialog(setup);
                preview.setContentView(quiz);
                preview.show();
            });
            capture("child-quiz-native.png");
            runOnMainSync(setup::finish);
            waitForIdleSync();
            check(ChildPin.configured(ChildLock.state(getTargetContext())), "password survives new entry");
            current = open();
            Activity protectedPage = current;
            runOnMainSync(() -> {
                AlertDialog dialog = password(protectedPage);
                check(!authorized(protectedPage), "new entry requires verification");
                List<EditText> fields = inputs(dialog);
                check(fields.size() == 1, "verification requests one password");
                fields.get(0).setText("000000");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(dialog.isShowing() && !authorized(protectedPage), "incorrect password keeps settings protected");
                fields.get(0).setText("483726");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(authorized(protectedPage), "correct password opens settings");
                protectedPage.finish();
            });
            waitForIdleSync();
            current = open();
            Activity cancelled = current;
            runOnMainSync(() -> password(cancelled).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            waitForIdleSync();
            check(cancelled.isFinishing() || cancelled.isDestroyed(), "cancel exits settings");
            verifyQuestionBank();
            result.putInt("checks", checks);
            result.putString("result", "PASS");
        } catch (Throwable failure) {
            status = Activity.RESULT_CANCELED;
            result.putString("error", failure.toString());
        } finally {
            Activity close = current;
            if (close != null && !close.isDestroyed()) runOnMainSync(close::finish);
            if (getTargetContext().getPackageName().endsWith(".childpinqa")) {
                ChildLock.state(getTargetContext()).edit().clear().commit();
            }
        }
        finish(status, result);
    }

    private void verifyPicturePreview(Activity setup) throws Exception {
        android.content.SharedPreferences prefs = ChildLock.state(getTargetContext());
        for (int grade = 1; grade <= 6; grade++) {
            final int level = grade;
            runOnMainSync(() -> {
                prefs.edit().putInt(ChildGrade.KEY, level).commit();
                try {
                    java.lang.reflect.Method render = ChildSettingsActivity.class.getDeclaredMethod("render");
                    render.setAccessible(true);
                    render.invoke(setup);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                String details = ((android.widget.TextView) field(setup, "difficulty")).getText().toString();
                check(details.contains(setup.getResources().getStringArray(R.array.child_lock_math_scope)[level - 1]),
                        "settings show selected math scope");
                check(details.contains(setup.getResources().getStringArray(R.array.child_lock_words_scope)[level - 1]),
                        "settings show selected English scope");
            });
        }
        for (int grade = 1; grade <= 2; grade++) {
            final int level = grade;
            long candidateSeed = 0;
            String desired = grade == 1 ? "picture:book" : "picture:bread";
            while (!ChildQuestionBank.select(getTargetContext(), prefs, true, grade,
                    candidateSeed, 0, true).question().prompt().equals(desired) && candidateSeed < 200) candidateSeed++;
            final long pictureSeed = candidateSeed;
            runOnMainSync(() -> {
                prefs.edit().putInt(ChildGrade.KEY, level).putInt(ChildSettingsActivity.MODE, 1).commit();
                try {
                    java.lang.reflect.Method render = ChildSettingsActivity.class.getDeclaredMethod("render");
                    render.setAccessible(true);
                    render.invoke(setup);
                    java.lang.reflect.Method preview = ChildSettingsActivity.class.getDeclaredMethod(
                            "preview", int.class, int.class, long.class);
                    preview.setAccessible(true);
                    preview.invoke(setup, 0, 0, pictureSeed);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            waitForIdleSync();
            capture("child-picture-grade-" + grade + ".png");
            java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
            runOnMainSync(() -> {
                android.app.Dialog dialog = (android.app.Dialog) field(setup, "previewDialog");
                check(dialog != null && dialog.isShowing(), "parent picture preview opens");
                android.widget.TextView note = findText(dialog.getWindow().getDecorView(),
                        setup.getString(R.string.child_lock_preview_review));
                check(note != null && note.isShown(), "review scope stays visible in preview");
                check(note.getText().toString().contains(setup.getString(R.string.child_lock_grade_reference)),
                        "preview explains grade reference");
                android.widget.Button answer = findButton(dialog.getWindow().getDecorView());
                check(answer != null && answer.hasFocus(), "picture answer receives remote focus");
                answer.performClick();
                android.app.Dialog next = (android.app.Dialog) field(setup, "previewDialog");
                check(next instanceof AlertDialog && next.isShowing(), "one press submits and opens answer feedback");
                check(findText(next.getWindow().getDecorView(), setup.getString(R.string.child_lock_preview_next)) != null,
                        "answer feedback offers the next question");
                ((AlertDialog) next).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                android.app.Dialog nextQuestion = (android.app.Dialog) field(setup, "previewDialog");
                check(nextQuestion instanceof ChildQuizDialog && nextQuestion.isShowing(),
                        "next action advances after readable feedback");
                nextQuestion.dismiss();
            });
            check(before.equals(prefs.getAll()), "answering preview leaves lock state and decks unchanged");
        }
    }

    private void verifyLongPreview(Activity setup) throws Exception {
        final android.app.Dialog[] shown = new android.app.Dialog[1];
        final android.widget.Button[] last = new android.widget.Button[1];
        ChildQuestions.Question longest = null;
        int length = -1;
        for (int i = 0; i < 200; i++) {
            ChildQuestions.Question q = ChildQuestionBank.select(getTargetContext(),
                    ChildLock.state(getTargetContext()), true, 6, 713, i, true).question();
            int size = q.prompt().length() + q.options().stream().mapToInt(String::length).sum();
            if (size > length) { length = size; longest = q; }
        }
        final ChildQuestions.Question question = longest;
        runOnMainSync(() -> {
            ChildQuizView quiz = new ChildQuizView(setup);
            quiz.context(true, 6, 0);
            quiz.previewDetails(setup.getString(R.string.child_lock_grade_scope,
                    setup.getResources().getStringArray(R.array.child_lock_math_scope)[5],
                    setup.getResources().getStringArray(R.array.child_lock_words_scope)[5]) + "\n"
                    + setup.getString(R.string.child_lock_grade_reference) + "\n"
                    + setup.getString(R.string.child_lock_preview_review));
            quiz.question(question, answer -> {}, null);
            ChildQuizDialog dialog = new ChildQuizDialog(setup);
            dialog.setContentView(quiz);
            dialog.show();
            shown[0] = dialog;
            List<android.widget.Button> buttons = new ArrayList<>();
            collectButtons(quiz, buttons);
            check(buttons.size() == 4, "long preview retains four answers");
            last[0] = buttons.get(3);
            last[0].requestFocus();
        });
        waitForIdleSync();
        capture("child-preview-long.png");
        runOnMainSync(() -> {
            android.graphics.Rect visible = new android.graphics.Rect();
            check(last[0].hasFocus(), "long preview last answer receives remote focus");
            check(last[0].getGlobalVisibleRect(visible) && visible.height() == last[0].getHeight(),
                    "long preview scrolls focused answer fully into view");
            shown[0].dismiss();
        });
    }

    private static void collectButtons(View view, List<android.widget.Button> result) {
        if (view instanceof android.widget.Button button) result.add(button);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            collectButtons(group.getChildAt(i), result);
        }
    }

    private static android.widget.TextView findText(View view, String value) {
        if (view instanceof android.widget.TextView text && text.getText().toString().contains(value)) return text;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            android.widget.TextView found = findText(group.getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }

    private static android.widget.Button findButton(View view) {
        if (view instanceof android.widget.Button button) return button;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            android.widget.Button found = findButton(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void verifyQuestionBank() {
        android.content.SharedPreferences prefs = ChildLock.state(getTargetContext());
        for (int grade = 1; grade <= 6; grade++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < 12; i++) {
                ChildQuestionBank.Selection selection = ChildQuestionBank.select(getTargetContext(), prefs,
                        false, grade, 9000 + i, 0, false);
                check(selection != null && selection.id().startsWith("math-g" + grade + "-"), "math pack loads");
                check(seen.add(selection.id()), "formal deck avoids cross-round repeats");
                android.content.SharedPreferences.Editor editor = prefs.edit();
                selection.save(editor);
                check(editor.commit(), "deck progress committed");
            }
            java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
            ChildQuestionBank.Selection preview = ChildQuestionBank.select(getTargetContext(), prefs,
                    true, grade, 713, 0, true);
            check(preview != null && preview.id().startsWith("english-g" + grade + "-"), "draft available in preview");
            check(before.equals(prefs.getAll()), "preview does not change formal progress");
            if (grade <= 2) for (int attempt = 0; attempt < 3; attempt++) {
                ChildQuestionBank.Selection picture = ChildQuestionBank.select(getTargetContext(), prefs,
                        true, grade, 713, attempt, true);
                check(picture != null && picture.question().prompt().startsWith("picture:") == (attempt % 2 == 0),
                        "early grade preview alternates pictures and text");
                if (attempt % 2 == 0) check(picture.question().options().stream().allMatch(option -> option.length() <= 10),
                        "picture answers have low reading load");
            }
            java.util.Set<String> legacy = new java.util.HashSet<>();
            for (int i = 0; i < 3; i++) {
                ChildQuestionBank.Selection formal = ChildQuestionBank.select(getTargetContext(), prefs,
                        true, grade, 18 + i, 0, false);
                check(formal != null && formal.id().startsWith("legacy-en-"), "draft excluded from enforced question");
                check(legacy.add(formal.id()), "legacy English also avoids repeats");
                android.content.SharedPreferences.Editor editor = prefs.edit();
                formal.save(editor);
                check(editor.commit(), "English deck progress committed");
            }
        }
    }

    private boolean waitForPasswordFocus(Activity activity) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        boolean[] focused = new boolean[1];
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> {
                EditText field = inputs(password(activity)).get(0);
                focused[0] = field.hasWindowFocus() && field.hasFocus();
            });
            if (focused[0]) return true;
            android.os.SystemClock.sleep(100);
        }
        return false;
    }

    @android.annotation.TargetApi(30)
    private boolean waitForKeyboard(Activity activity, boolean visible) {
        long deadline = android.os.SystemClock.uptimeMillis() + 7000;
        boolean[] result = new boolean[1];
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> {
                android.view.WindowInsets insets = password(activity).getWindow().getDecorView().getRootWindowInsets();
                result[0] = insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
            });
            if (result[0] == visible) return true;
            android.os.SystemClock.sleep(100);
        }
        return false;
    }

    private void capture(String name) throws java.io.IOException {
        waitForIdleSync();
        // Let the compositor present the frame after a full-screen password dialog closes.
        android.os.SystemClock.sleep(350);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new java.io.IOException("Screenshot unavailable");
        java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), name);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            bitmap.recycle();
        }
    }

    private Activity open() {
        return startActivitySync(new Intent(getTargetContext(), ChildSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static Object field(Activity activity, String name) {
        try {
            Field field = ChildSettingsActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static AlertDialog password(Activity activity) {
        return (AlertDialog) field(activity, "passwordDialog");
    }

    private static boolean authorized(Activity activity) {
        return (boolean) field(activity, "authorized");
    }

    private static List<EditText> inputs(AlertDialog dialog) {
        List<EditText> result = new ArrayList<>();
        collect(dialog.getWindow().getDecorView(), result);
        return result;
    }

    private static void collect(View view, List<EditText> result) {
        if (view instanceof EditText) result.add((EditText) view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
        }
    }
}
