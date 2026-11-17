package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

public final class ChildSettingsActivity extends Activity {
    static final String ENABLED = "enabled";
    static final String MODE = "mode";
    static final String MINUTES = "minutes";
    static final String TEST_INTERVAL = "test_interval_30_seconds";

    private SharedPreferences state;
    private LinearLayout page;
    private TextView summary;
    private TextView difficulty;
    private TextView protectionStatus;
    private AlertDialog serviceDialog;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshProtection = new Runnable() {
        @Override public void run() {
            if (!resumed || !authorized) return;
            updateProtectionStatus();
            statusHandler.postDelayed(this, 1000);
        }
    };
    private Button enableButton;
    private Button firstChoice;
    private Button playerButton;
    private Button permissionButton;
    private boolean readingAdbPort;
    private boolean authorized;
    private boolean resumed;
    private AlertDialog passwordDialog;
    private AlertDialog playerDialog;
    private android.app.Dialog previewDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = ChildLock.state(this);
    }

    private void authorized() {
        if (!resumed || isFinishing()) return;
        authorized = true;
        render();
        statusHandler.removeCallbacks(refreshProtection);
        statusHandler.post(refreshProtection);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (authorized) {
            render();
        } else if (passwordDialog == null) {
            passwordDialog = ChildPin.ask(this, state, !ChildPin.configured(state),
                    this::authorized, this::finish);
            passwordDialog.setOnDismissListener(dialog -> passwordDialog = null);
        }
    }

    @Override protected void onStop() {
        resumed = false;
        authorized = false;
        statusHandler.removeCallbacks(refreshProtection);
        if (serviceDialog != null) { serviceDialog.dismiss(); serviceDialog = null; }
        if (previewDialog != null) {
            previewDialog.dismiss();
            previewDialog = null;
        }
        if (playerDialog != null) {
            playerDialog.dismiss();
            playerDialog = null;
        }
        if (passwordDialog != null) {
            passwordDialog.dismiss();
            passwordDialog = null;
        }
        // Remove settings from the existing activity when Home or a system screen covers it.
        setContentView(new android.widget.FrameLayout(this));
        super.onStop();
    }

    private void render() {
        firstChoice = null;
        LinearLayout shell = ChildStyle.page(this);
        ChildStyle.header(shell);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.topMargin = dp(18);
        bodyParams.bottomMargin = dp(16);
        shell.addView(body, bodyParams);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -1, 1.42f);
        leftParams.setMarginEnd(dp(24));
        body.addView(scroll, leftParams);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);
        TextView heading = ChildStyle.text(this, getString(R.string.child_lock_settings_heading),
                32, ChildStyle.INK, true);
        page.addView(heading);
        TextView intro = ChildStyle.text(this, getString(R.string.child_lock_settings_intro),
                12, ChildStyle.MUTED, false);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.bottomMargin = dp(14);
        page.addView(intro, introParams);
        int mode = state.getInt(MODE, 2);
        row(R.string.child_lock_mode, new String[]{getString(R.string.child_lock_math),
                getString(R.string.child_lock_words), getString(R.string.child_lock_mixed)}, mode,
                index -> state.edit().putInt(MODE, index).apply());
        String[] levels = new String[6];
        for (int i = 0; i < levels.length; i++) levels[i] = getString(R.string.child_lock_grade_short, i + 1);
        row(R.string.child_lock_difficulty, levels, ChildGrade.read(state) - 1,
                index -> state.edit().putInt(ChildGrade.KEY, index + 1).apply());
        int[] minutes = {5, 10, 15, 20};
        String[] labels = new String[minutes.length];
        int selected = 1;
        for (int i = 0; i < minutes.length; i++) {
            labels[i] = getString(R.string.child_lock_minutes, minutes[i]);
            if (minutes[i] == ChildWatchInterval.minutes(state.getInt(MINUTES, 10), false)) selected = i;
        }
        row(R.string.child_lock_interval, labels, selected, index ->
                state.edit().remove(TEST_INTERVAL).putInt(MINUTES, minutes[index]).apply());
        LinearLayout utilities = new LinearLayout(this);
        utilities.setBaselineAligned(false);
        page.addView(utilities);
        playerButton = ChildStyle.button(this, getString(R.string.child_lock_player, playerLabel()), false);
        playerButton.setTextSize(11);
        playerButton.setMaxLines(2);
        playerButton.setOnClickListener(view -> pickPlayer());
        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(0, dp(43), 1.2f);
        utilityParams.setMarginEnd(dp(7));
        utilities.addView(playerButton, utilityParams);
        permissionButton = ChildStyle.button(this, "", false);
        permissionButton.setTextSize(11);
        permissionButton.setMaxLines(2);
        permissionButton.setOnClickListener(view -> enableTimerService());
        utilities.addView(permissionButton, new LinearLayout.LayoutParams(0, dp(43), 1));
        TextView serviceHint = ChildStyle.text(this, getString(R.string.child_adb_inline_help),
                10, ChildStyle.MUTED, false);
        serviceHint.setPadding(0, dp(7), 0, 0);
        page.addView(serviceHint);
        // At 720p or enlarged display density, the card can exceed the viewport.
        // Keep every action reachable by remote focus instead of clipping the bottom controls.
        ScrollView posterScroll = new ScrollView(this);
        posterScroll.setFillViewport(true);
        posterScroll.setClipToPadding(false);
        posterScroll.setVerticalScrollBarEnabled(false);
        posterScroll.addView(poster(), new ScrollView.LayoutParams(-1, -2));
        body.addView(posterScroll, new LinearLayout.LayoutParams(0, -1, 1));
        TextView footer = ChildStyle.text(this, getString(R.string.child_lock_remote_hint), 11, ChildStyle.MUTED, false);
        footer.setGravity(Gravity.END);
        footer.setPadding(0, dp(8), 0, 0);
        shell.addView(footer, new LinearLayout.LayoutParams(-1, dp(26)));
        // Older TV frameworks can defer ScrollView's focus scroll during layout.
        // Reveal the final focused rectangle after layout, including wrapped button labels.
        shell.getViewTreeObserver().addOnGlobalFocusChangeListener((previous, focused) -> {
            if (focused != null) focused.post(() -> {
                if (focused.isAttachedToWindow() && focused.hasFocus()) {
                    focused.requestRectangleOnScreen(new android.graphics.Rect(
                            0, 0, focused.getWidth(), focused.getHeight()), true);
                }
            });
        });
        setContentView(shell);
        updateSummary();
        if (firstChoice != null) firstChoice.requestFocus();
    }

    private LinearLayout poster() {
        LinearLayout poster = new LinearLayout(this);
        poster.setOrientation(LinearLayout.VERTICAL);
        poster.setPadding(dp(20), dp(20), dp(20), dp(18));
        poster.setBackground(ChildStyle.raised(this, ChildStyle.GREEN, 0xffa1c83c, 22));
        TextView eyebrow = ChildStyle.text(this, getString(R.string.child_lock_preview), 12, 0xff456411, true);
        poster.addView(eyebrow);
        TextView title = ChildStyle.text(this, getString(R.string.child_lock_preview_heading), 28, ChildStyle.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        titleParams.bottomMargin = dp(9);
        poster.addView(title, titleParams);
        summary = ChildStyle.text(this, "", 12, 0xff3f541a, false);
        poster.addView(summary);
        protectionStatus = ChildStyle.text(this, "", 11, 0xff3f541a, true);
        protectionStatus.setPadding(0, dp(6), 0, 0);
        poster.addView(protectionStatus);
        difficulty = ChildStyle.text(this, "", 12, 0xff3f541a, false);
        difficulty.setPadding(0, dp(8), 0, 0);
        poster.addView(difficulty);
        ImageView cards = new ImageView(this);
        cards.setImageResource(R.drawable.child_settings_cards);
        cards.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        poster.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
        Button preview = ChildStyle.button(this, getString(R.string.child_lock_preview), true);
        preview.setOnClickListener(view -> preview(0, 0, new Random().nextLong()));
        poster.addView(preview, new LinearLayout.LayoutParams(-1, dp(43)));
        TextView note = ChildStyle.text(this, getString(R.string.child_lock_preview_note), 10, 0xff456411, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(7);
        noteParams.bottomMargin = dp(12);
        poster.addView(note, noteParams);
        enableButton = ChildStyle.button(this, "", false);
        enableButton.setOnClickListener(view -> toggle());
        poster.addView(enableButton, new LinearLayout.LayoutParams(-1, dp(40)));
        return poster;
    }

    private void updateSummary() {
        if (summary == null) return;
        int mode = state.getInt(MODE, 2);
        String type = getString(mode == 0 ? R.string.child_lock_math
                : mode == 1 ? R.string.child_lock_words : R.string.child_lock_mixed);
        summary.setText(getString(R.string.child_lock_settings_summary, type, ChildGrade.read(state),
                getString(R.string.child_lock_minutes,
                        ChildWatchInterval.minutes(state.getInt(MINUTES, 10), false))));
        difficulty.setText(gradeScope(ChildGrade.read(state)) + "\n" + getString(R.string.child_lock_grade_reference));
        enableButton.setText(state.getBoolean(ENABLED, false) ? R.string.child_lock_disable : R.string.child_lock_enable);
        updateProtectionStatus();
    }

    private void updateProtectionStatus() {
        if (protectionStatus == null) return;
        boolean working = ChildLocalAdb.busy() || readingAdbPort;
        boolean connected = ChildWatchService.isConnected();
        if (permissionButton != null) {
            int label = connected ? R.string.child_adb_button_enabled
                    : working ? R.string.child_adb_button_working : R.string.child_adb_button_enable;
            if (!getString(label).contentEquals(permissionButton.getText())) permissionButton.setText(label);
            permissionButton.setEnabled(!connected && !working);
        }
        int status = working ? R.string.child_adb_waiting
                : !connected ? R.string.child_service_disconnected
                : state.getBoolean(ENABLED, false) ? R.string.child_service_active : R.string.child_service_ready;
        if (!getString(status).contentEquals(protectionStatus.getText())) protectionStatus.setText(status);
    }

    private void enableTimerService() {
        if (readingAdbPort || ChildLocalAdb.busy() || ChildWatchService.isConnected()) return;
        readingAdbPort = true;
        updateProtectionStatus();
        new Thread(() -> {
            int port = ChildLocalAdb.reportedPort();
            runOnUiThread(() -> {
                readingAdbPort = false;
                if (isDestroyed() || isFinishing() || !authorized || !resumed) return;
                if (port > 0) startLocalAdb(port);
                else localAdbConsent();
                updateProtectionStatus();
            });
        }, "GenTVLocalAdbPort").start();
    }

    private void startLocalAdb(int port) {
        ChildLocalAdb.start(this, port, (connected, restored) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (authorized && resumed) {
                updateProtectionStatus();
                if (!connected) {
                    serviceDialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                            .setTitle(R.string.child_adb_title)
                            .setMessage(restored ? R.string.child_adb_restored : R.string.child_adb_failed)
                            .setPositiveButton(R.string.child_adb_retry, (dialog, which) -> enableTimerService())
                            .setNeutralButton(R.string.child_adb_port_edit, (dialog, which) -> localAdbConsent())
                            .setNegativeButton(R.string.child_service_system, (dialog, which) -> openAccessibilitySettings())
                            .create();
                    serviceDialog.show();
                }
            }
            android.widget.Toast.makeText(this, connected ? R.string.child_adb_success
                    : restored ? R.string.child_adb_restored : R.string.child_adb_failed,
                    android.widget.Toast.LENGTH_LONG).show();
        }));
        updateProtectionStatus();
    }

    private void localAdbConsent() {
        if (ChildLocalAdb.busy()) {
            android.widget.Toast.makeText(this, R.string.child_adb_waiting, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), 0);
        TextView help = ChildStyle.text(this, getString(R.string.child_adb_help), 15, ChildStyle.INK, false);
        content.addView(help);
        android.widget.EditText port = new android.widget.EditText(this);
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        port.setSingleLine(true);
        port.setHint(R.string.child_adb_port);
        content.addView(port, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(R.string.child_adb_title).setView(content)
                .setPositiveButton(R.string.child_adb_connect, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        serviceDialog = dialog;
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!authorized || !resumed) return;
            int number;
            try { number = Integer.parseInt(port.getText().toString().trim()); }
            catch (NumberFormatException invalid) { port.setError(getString(R.string.child_adb_port)); return; }
            if (number < 1 || number > 65535) { port.setError(getString(R.string.child_adb_port)); return; }
            dialog.dismiss();
            startLocalAdb(number);
        }));
        dialog.show();
        new Thread(() -> {
            int reported = ChildLocalAdb.reportedPort();
            runOnUiThread(() -> {
                if (dialog.isShowing() && port.getText().length() == 0 && reported > 0) {
                    port.setText(Integer.toString(reported));
                }
            });
        }, "GenTVLocalAdbPort").start();
    }

    private void toggle() {
        if (state.getBoolean(ENABLED, false)) {
            state.edit().putBoolean(ENABLED, false).remove(ChildWatchService.WATCHED).apply();
            updateSummary();
            return;
        }
        if (ChildApps.read(state).isEmpty()) {
            pickPlayer();
            return;
        }
        // Some TV firmware omits a running service from AccessibilityManager's list.
        // Permission guidance must not prevent parents from enabling the saved mode.
        state.edit().putBoolean(ENABLED, true).apply();
        updateSummary();
        if (!ChildWatchService.isConnected()) {
            android.widget.Toast.makeText(this, R.string.child_lock_service_required,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        // Try launching directly: resolveActivity can hide OEM handlers on TV firmware.
        for (String action : new String[]{Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                android.util.Log.w("GenTVChildLock", "Settings unavailable: " + action);
            }
        }
        android.widget.Toast.makeText(this, R.string.child_lock_settings_unavailable,
                android.widget.Toast.LENGTH_LONG).show();
    }

    private String playerLabel() {
        Set<String> packages = ChildApps.read(state);
        if (packages.isEmpty()) return getString(R.string.child_lock_player_none);
        if (packages.size() > 1) return getString(R.string.child_lock_app_count, packages.size());
        return appLabel(packages.iterator().next());
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return getString(R.string.child_lock_app_uninstalled, pkg);
        }
    }

    private void pickPlayer() {
        Set<String> chosen = ChildApps.read(state);
        Map<String, String> available = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo app : getPackageManager().queryIntentActivities(query, 0)) {
                available.put(app.activityInfo.packageName, app.loadLabel(getPackageManager()).toString());
            }
        }
        // Keep saved packages visible even while uninstalled, until the parent explicitly deselects them.
        for (String pkg : chosen) available.putIfAbsent(pkg, appLabel(pkg));
        Set<String> excluded = new HashSet<>();
        excluded.add(getPackageName());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo app : getPackageManager().queryIntentActivities(home, 0)) {
            excluded.add(app.activityInfo.packageName);
        }
        for (String action : new String[]{Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_ACCESSIBILITY_SETTINGS}) {
            for (ResolveInfo app : getPackageManager().queryIntentActivities(new Intent(action), 0)) {
                excluded.add(app.activityInfo.packageName);
            }
        }
        available.keySet().removeAll(excluded);
        chosen.removeAll(excluded);
        List<Map.Entry<String, String>> entries = new ArrayList<>(available.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int position) { return entries.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public android.view.View getView(int position, android.view.View recycled,
                    ViewGroup parent) {
                Map.Entry<String, String> app = entries.get(position);
                android.widget.CheckedTextView row = new android.widget.CheckedTextView(ChildSettingsActivity.this);
                row.setText(app.getValue());
                row.setTextSize(18);
                row.setTextColor(ChildStyle.INK);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(10), dp(18), dp(10));
                row.setMinHeight(dp(66));
                row.setCompoundDrawablePadding(dp(18));
                android.graphics.drawable.Drawable icon;
                try { icon = getPackageManager().getApplicationIcon(app.getKey()); }
                catch (android.content.pm.PackageManager.NameNotFoundException missing) {
                    icon = getDrawable(android.R.drawable.sym_def_app_icon);
                }
                icon.setBounds(0, 0, dp(42), dp(42));
                row.setCompoundDrawablesRelative(icon, null, null, null);
                android.graphics.drawable.StateListDrawable check = new android.graphics.drawable.StateListDrawable();
                check.addState(new int[]{android.R.attr.state_checked},
                        getDrawable(R.drawable.child_app_checked));
                check.addState(new int[0], getDrawable(R.drawable.child_app_unchecked));
                row.setCheckMarkDrawable(check);
                row.setChecked(chosen.contains(app.getKey()));
                android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
                background.addState(new int[]{android.R.attr.state_focused},
                        ChildStyle.shape(ChildSettingsActivity.this, 0xfff0eaff, ChildStyle.FOCUS, 12, 2));
                background.addState(new int[]{android.R.attr.state_checked},
                        ChildStyle.shape(ChildSettingsActivity.this, 0xffebffad, 0xffa8cc56, 12, 1));
                background.addState(new int[0],
                        ChildStyle.shape(ChildSettingsActivity.this, 0xffffffff, ChildStyle.LINE, 12, 1));
                row.setBackground(background);
                return row;
            }
        };
        TextView pickerTitle = ChildStyle.text(this, getString(R.string.child_lock_pick_player),
                21, ChildStyle.INK, true);
        pickerTitle.setPadding(dp(24), dp(20), dp(24), dp(12));
        AlertDialog picker = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setCustomTitle(pickerTitle)
                .setAdapter(adapter, null)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        playerDialog = picker;
        picker.setOnDismissListener(dialog -> {
            if (playerDialog == picker) playerDialog = null;
        });
        picker.setOnShowListener(dialog -> {
            android.widget.ListView list = picker.getListView();
            list.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            list.setDividerHeight(dp(8));
            list.setPadding(dp(22), dp(8), dp(22), dp(8));
            for (int i = 0; i < entries.size(); i++) {
                list.setItemChecked(i, chosen.contains(entries.get(i).getKey()));
            }
            list.setOnItemClickListener((parent, row, index, id) -> {
                String pkg = entries.get(index).getKey();
                if (list.isItemChecked(index)) chosen.add(pkg);
                else chosen.remove(pkg);
            });
            picker.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels * 65 / 100,
                    getResources().getDisplayMetrics().heightPixels * 82 / 100);
            picker.getWindow().setBackgroundDrawable(
                    ChildStyle.shape(this, ChildStyle.PAPER, ChildStyle.LINE, 18, 1));
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ChildStyle.FOCUS);
            picker.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ChildStyle.MUTED);
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (!authorized || !resumed) return;
                if (chosen.isEmpty()) {
                    android.widget.Toast.makeText(this, R.string.child_lock_select_app,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!ChildApps.save(state, chosen)) return;
                picker.dismiss();
                render();
                playerButton.requestFocus();
            });
        });
        picker.show();
    }

    private void preview(int round, int attempt, long seed) {
        int mode = state.getInt(MODE, 2);
        boolean words = mode == 1 || mode == 2 && round % 2 == 1;
        int level = ChildGrade.read(state);
        ChildQuestionBank.Selection selection = ChildQuestionBank.select(this, state, words, level, seed, attempt, true);
        ChildQuestions.Question question = selection == null
                ? ChildQuestions.create(words, level, seed, attempt) : selection.question();
        ChildQuizView view = new ChildQuizView(this);
        android.app.Dialog dialog = new ChildQuizDialog(this);
        dialog.setContentView(view);
        previewDialog = dialog;
        dialog.setOnDismissListener(closed -> {
            if (previewDialog == dialog) previewDialog = null;
        });
        view.context(words, level, 0);
        view.previewDetails(gradeScope(level) + "\n" + getString(R.string.child_lock_grade_reference)
                + (words ? "\n" + getString(R.string.child_lock_preview_review) : ""));
        view.question(question, index -> {
            dialog.dismiss();
            int limit = mode == 2 ? 6 : 3;
            boolean more = round + 1 < limit;
            AlertDialog feedback = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle(getString(R.string.child_lock_preview_answer,
                            question.options().get(question.correct())))
                    .setMessage(selection == null ? "" : selection.explanation())
                    .setPositiveButton(more ? R.string.child_lock_preview_next : android.R.string.ok,
                            (closed, which) -> {
                                if (more && authorized && resumed && !isFinishing()) {
                                    preview(round + 1, mode == 2 ? (round + 1) / 2 : round + 1, seed);
                                }
                            })
                    .setNegativeButton(R.string.child_lock_preview_close, null).create();
            previewDialog = feedback;
            feedback.setOnDismissListener(closed -> {
                if (previewDialog == feedback) previewDialog = null;
            });
            feedback.show();
        }, null);
        dialog.show();
    }

    private String gradeScope(int grade) {
        return getString(R.string.child_lock_grade_scope,
                getResources().getStringArray(R.array.child_lock_math_scope)[grade - 1],
                getResources().getStringArray(R.array.child_lock_words_scope)[grade - 1]);
    }

    private void row(int title, String[] labels, int selected, java.util.function.IntConsumer change) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(10));
        panel.setBackground(ChildStyle.raised(this, 0xffffffff, ChildStyle.LINE, 15));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.bottomMargin = dp(9);
        page.addView(panel, panelParams);
        TextView label = ChildStyle.text(this, getString(title), 14, ChildStyle.INK, true);
        panel.addView(label);
        if (title == R.string.child_lock_difficulty) {
            panel.addView(ChildStyle.text(this, getString(R.string.child_lock_shared_grade_hint),
                    10, ChildStyle.MUTED, false));
        }
        LinearLayout group = new LinearLayout(this);
        group.setBaselineAligned(false);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        groupParams.topMargin = dp(6);
        panel.addView(group, groupParams);
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            Button button = ChildStyle.button(this, labels[i], false);
            button.setTextSize(labels.length >= 5 ? 11 : 13);
            button.setSelected(i == selected);
            if (firstChoice == null && title == R.string.child_lock_mode && i == Math.max(0, selected)) {
                firstChoice = button;
            }
            button.setOnClickListener(view -> {
                for (int j = 0; j < group.getChildCount(); j++) {
                    group.getChildAt(j).setSelected(group.getChildAt(j) == button);
                }
                change.accept(index);
                updateSummary();
            });
            boolean compact = getResources().getConfiguration().screenHeightDp < 480
                    || getResources().getConfiguration().screenWidthDp < 800;
            button.setMaxLines(2);
            button.setMinHeight(dp(compact ? 44 : 33));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i != labels.length - 1) params.setMarginEnd(dp(5));
            group.addView(button, params);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
