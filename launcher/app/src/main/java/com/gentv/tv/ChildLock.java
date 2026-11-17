package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.TextView;

/** Restores a saved child challenge whenever GenTV enters Home. */
final class ChildLock {
    static final String PREFS = "child_lock";
    static final String STAGE = "stage";
    static final String WRONG = "wrong";
    static final String UNTIL = "until";
    static final String SEED = "question_seed";
    static final String WORDS = "question_words";
    static final String LEVEL = "question_level";
    static final String ROUND = "question_round";
    private static final String SNAPSHOT = "question_snapshot";
    static final int QUESTION = 1;
    static final int REST = 2;
    private static final long REST_MS = 300_000L;
    private static final long CLOCK_VALID_AFTER = 1_735_689_600_000L; // 2025-01-01 UTC
    private static final long CHECKPOINT_MS = 10_000L;
    private static final String REST_BOOT = "rest_boot";
    private static final String REST_ELAPSED_END = "rest_elapsed_end";
    private static final String REST_REMAINING = "rest_remaining";

    private final Context home;
    private final Runnable overlayFallback;
    private final SharedPreferences state;
    private final ChildLockPolicy policy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::render;
    private Dialog dialog;
    private TextView countdown;
    private long unknownBootEnd = -1;

    ChildLock(Activity home) {
        this.home = home;
        state = state(home);
        policy = new ChildLockPolicy(home, state);
        overlayFallback = null;
    }

    ChildLock(ChildWatchService service, Runnable fallback) {
        home = service;
        state = state(service);
        policy = null;
        overlayFallback = fallback;
    }

    static SharedPreferences state(Context context) {
        SharedPreferences preferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!BuildConfig.CHILD_TIMER_TEST_MODE && preferences.contains(ChildSettingsActivity.TEST_INTERVAL)) {
            SharedPreferences.Editor migration = preferences.edit().remove(ChildSettingsActivity.TEST_INTERVAL);
            if (preferences.getBoolean(ChildSettingsActivity.TEST_INTERVAL, false)) {
                migration.putInt(ChildSettingsActivity.MINUTES, ChildWatchInterval.minutes(
                        preferences.getInt(ChildSettingsActivity.MINUTES, 10), true));
                migration.putLong(ChildWatchService.WATCHED, 0);
            }
            migration.apply();
        }
        return preferences;
    }

    static boolean locked(Context context) {
        return state(context).getInt(STAGE, 0) != 0;
    }

    boolean showIfLocked() {
        boolean locked = state.getInt(STAGE, 0) != 0;
        if (policy != null) policy.apply(locked);
        if (!locked) return false;
        if (dialog == null) {
            dialog = new ChildQuizDialog(home);
            if (overlayFallback != null) dialog.getWindow().setType(
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.setCancelable(false);
            dialog.show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(home.getResources().getDisplayMetrics().widthPixels * 62 / 100,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setDimAmount(0.35f);
            render();
        }
        return true;
    }

    void dismiss() {
        handler.removeCallbacks(refresh);
        if (dialog != null) dialog.dismiss();
        dialog = null;
        countdown = null;
    }

    private void render() {
        if (overlayFallback != null && state.getInt(STAGE, 0) == REST) {
            dismiss();
            overlayFallback.run();
            return;
        }
        if (dialog == null) return;
        int stage = state.getInt(STAGE, 0);
        if (stage == 0) {
            dismiss();
            return;
        }
        long remaining = stage == REST ? remainingRest() : 0;
        if (stage == REST && remaining > 0) {
            if (countdown == null) {
                dialog.getWindow().setTitle("GenTV Child Lock: rest");
                ChildQuizView page = new ChildQuizView(home);
                countdown = page.rest(parentAction());
                dialog.setContentView(page);
            }
            countdown.setText(home.getString(R.string.child_lock_rest, (remaining + 999) / 1000));
            handler.postDelayed(refresh, Math.min(remaining, 1000));
            return;
        }
        if (stage == REST && !state.edit().putInt(STAGE, QUESTION).commit()) {
            handler.postDelayed(refresh, 1000);
            return;
        }
        countdown = null;
        dialog.getWindow().setTitle("GenTV Child Lock: question");
        long seed = state.getLong(SEED, 0);
        if (!state.contains(SEED)) {
            seed = new java.util.Random().nextLong();
            if (!state.edit().putLong(SEED, seed).commit()) {
                handler.postDelayed(refresh, 1000);
                return;
            }
        }
        boolean words = state.getBoolean(WORDS, false);
        int level = state.getInt(LEVEL, 1);
        ChildQuestions.Question question = storedQuestion(words, level, seed);
        if (question == null) {
            handler.postDelayed(refresh, 1000);
            return;
        }
        ChildQuizView page = new ChildQuizView(home);
        dialog.setContentView(page);
        page.context(words, level, state.getInt(WRONG, 0));
        page.question(question, index -> answer(index == question.correct()), parentAction());
    }

    private ChildQuestions.Question storedQuestion(boolean words, int level, long seed) {
        String snapshot = state.getString(SNAPSHOT, "");
        if (!snapshot.isEmpty()) {
            try {
                org.json.JSONArray saved = new org.json.JSONArray(snapshot);
                java.util.List<String> options = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) options.add(saved.getString(i + 2));
                int correct = saved.getInt(1);
                if (correct >= 0 && correct < options.size()) {
                    return new ChildQuestions.Question(saved.getString(0), options, correct);
                }
            } catch (org.json.JSONException ignored) {
                // A corrupt snapshot can be regenerated from the round's saved parameters.
            }
        }
        ChildQuestionBank.Selection selection = ChildQuestionBank.select(home, state, words, level,
                seed, state.getInt(WRONG, 0), false);
        ChildQuestions.Question question = selection == null
                ? ChildQuestions.create(words, level, seed, state.getInt(WRONG, 0)) : selection.question();
        org.json.JSONArray saved = new org.json.JSONArray().put(question.prompt()).put(question.correct());
        for (String option : question.options()) saved.put(option);
        SharedPreferences.Editor editor = state.edit().putString(SNAPSHOT, saved.toString());
        if (selection != null) selection.save(editor);
        else editor.remove("question_id").remove("question_bank_version");
        return editor.commit() ? question : null;
    }

    private long remainingRest() {
        long elapsed = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        int boot = OpenOnStart.boot(home);
        long saved = state.getLong(REST_REMAINING, REST_MS);
        long end = state.getLong(REST_ELAPSED_END, -1);
        if (boot < 0 && unknownBootEnd >= 0) end = unknownBootEnd;
        else if (boot < 0 || boot != state.getInt(REST_BOOT, -1) || end < 0) {
            long deadline = state.getLong(UNTIL, 0);
            if (deadline >= CLOCK_VALID_AFTER && wall >= CLOCK_VALID_AFTER) {
                saved = Math.min(saved, Math.max(0, deadline - wall));
            }
            end = elapsed + saved;
            if (boot < 0) unknownBootEnd = end;
            state.edit().putInt(REST_BOOT, boot).putLong(REST_ELAPSED_END, end)
                    .putLong(REST_REMAINING, saved).commit();
        }
        long remaining = Math.max(0, end - elapsed);
        if (saved - remaining >= CHECKPOINT_MS) {
            state.edit().putLong(REST_REMAINING, remaining).commit();
        }
        return remaining;
    }

    private Runnable parentAction() {
        if (!ChildPin.configured(state)) return null;
        if (overlayFallback != null) return () -> {
            dismiss();
            overlayFallback.run();
        };
        return () -> ChildPin.ask((Activity) home, state, false, () -> unlock(false), () -> {});
    }

    private void unlock(boolean resumePlayer) {
        String pkg = state.getString(ChildWatchService.PLAYBACK, "");
        if (!state.edit().remove(STAGE).remove(WRONG).remove(UNTIL).remove(SEED).remove(WORDS).remove(LEVEL)
                .remove(SNAPSHOT).remove("question_id").remove("question_bank_version").remove(ChildWatchService.PLAYBACK).remove(ChildWatchService.WATCHED)
                .putInt(ROUND, state.getInt(ROUND, 0) + 1)
                .remove(REST_BOOT).remove(REST_ELAPSED_END).remove(REST_REMAINING).commit()) return;
        if (policy != null) policy.apply(false);
        dismiss();
        if (overlayFallback != null) {
            ((ChildWatchService) home).overlayFinished(pkg);
            return;
        }
        if (home instanceof ChildChallengeActivity challenge) {
            if (resumePlayer) challenge.resumePlayerAfterFinish(pkg);
            if (!resumePlayer) {
                home.startActivity(new Intent(home, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            challenge.finish();
            return;
        }
        if (!resumePlayer || pkg.isEmpty()) return;
        Intent launch = OpenOnStart.intent(home.getPackageManager(), pkg);
        if (launch != null) {
            try {
                home.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The player may have been disabled or removed while the challenge was pending.
            }
        }
    }

    private void answer(boolean correct) {
        if (state.getInt(STAGE, 0) != QUESTION) return;
        if (correct) {
            unlock(true);
            return;
        }
        int wrong = state.getInt(WRONG, 0) + 1;
        SharedPreferences.Editor edit = state.edit().putInt(WRONG, wrong).remove(SNAPSHOT);
        if (wrong >= 3) {
            long duration = REST_MS * Math.min(4, wrong - 2L);
            long wall = System.currentTimeMillis();
            long end = SystemClock.elapsedRealtime() + duration;
            unknownBootEnd = end;
            edit.putInt(STAGE, REST).putLong(UNTIL, wall >= CLOCK_VALID_AFTER ? wall + duration : 0)
                    .putInt(REST_BOOT, OpenOnStart.boot(home)).putLong(REST_ELAPSED_END, end)
                    .putLong(REST_REMAINING, duration);
        }
        if (edit.commit()) render();
    }
}
