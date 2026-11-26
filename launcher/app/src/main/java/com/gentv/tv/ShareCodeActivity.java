package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.text.method.DigitsKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** TV share-code entry, app catalog and system installer handoff. */
public final class ShareCodeActivity extends Activity {
    private static final int CODE_LENGTH = 4;
    private static final int TEXT_COLOR = 0xFFF5F7FA;
    private static final int FOCUS_TEXT_COLOR = 0xFF121418;
    private static final int FOCUS_BACKGROUND = 0xF7FFFFFF;
    private static final String STATE_SHOWING_APPS = "showingApps";
    private static final String STATE_PENDING_APK = "pendingApk";
    private static final int REQUEST_INSTALL = 1;
    private static final String TAG = "ShareCodeActivity";

    // Serialize APK writes and cleanup across activity recreation.
    private static final ExecutorService FILE_WORKER = Executors.newSingleThreadExecutor();

    private final ExecutorService catalogWorker = Executors.newSingleThreadExecutor();
    private final Map<String, String> packageNames = new HashMap<>();
    private final Map<String, ApplicationInfo> installedApps = new HashMap<>();
    private LinearLayout panel;
    private TextView title;
    private FrameLayout results;
    private ArrayAdapter<ShareCodeApkRepository.Apk> appsAdapter;
    private Dialog downloadDialog;
    private Future<?> downloadTask;
    private File pendingApk;
    // Compare down times so a held key and its release are treated as the same press.
    private long ignoreConfirmDownTimeThrough = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x5405070C);
        scrim.setOnClickListener(view -> finish());
        // The backdrop handles outside taps without taking remote focus from the panel.
        scrim.setFocusable(false);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackgroundResource(R.drawable.quick_settings_panel);
        panel.setClickable(true);
        int width = Math.min(dp(328), getResources().getDisplayMetrics().widthPixels - dp(32));
        scrim.addView(panel, new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        title = new TextView(this);
        title.setText(R.string.quick_share_code);
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(18);
        title.setTypeface(UiFont.get(this, R.string.font_medium));
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        setContentView(scrim);
        // The installer may still hold a content URI when Android recreates this activity.
        File cache = getCacheDir();
        String installerFile = savedInstanceState == null
                ? null : savedInstanceState.getString(STATE_PENDING_APK);
        if (installerFile != null && installerFile.matches("[a-f0-9]{40}\\.apk")) {
            pendingApk = new File(new File(cache, ShareCodeApkRepository.CACHE_DIRECTORY), installerFile);
        }
        if (savedInstanceState == null || pendingApk != null) {
            File installerApk = pendingApk;
            FILE_WORKER.execute(() -> ShareCodeApkRepository.clearCache(cache, installerApk));
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWING_APPS)) {
            showApps();
            return;
        }

        CodeInput input = new CodeInput(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.topMargin = dp(16);
        panel.addView(input, inputParams);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                if (text.length() != CODE_LENGTH) return;
                // Replace the editor after the current text change finishes.
                input.post(() -> {
                    if (isFinishing() || isDestroyed() || results != null || input.length() != CODE_LENGTH) {
                        return;
                    }
                    InputMethodManager keyboard =
                            CompatApi.service(ShareCodeActivity.this, InputMethodManager.class);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    panel.removeView(input);
                    showApps();
                });
            }
        });

        input.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_SHOWING_APPS, results != null);
        if (pendingApk != null) state.putString(STATE_PENDING_APK, pendingApk.getName());
    }

    @Override
    protected void onDestroy() {
        catalogWorker.shutdownNow();
        cancelDownload();
        if (downloadDialog != null) downloadDialog.dismiss();
        if (isFinishing()) {
            File cache = getCacheDir();
            FILE_WORKER.execute(() -> ShareCodeApkRepository.clearCache(cache, null));
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInstalledApps();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isConfirmKey(event.getKeyCode())
                && event.getDownTime() <= ignoreConfirmDownTimeThrough) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isConfirmKey(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void refreshInstalledApps() {
        if (appsAdapter == null) return;
        installedApps.clear();
        if (!packageNames.isEmpty()) {
            for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
                installedApps.put(app.packageName, app);
            }
        }
        appsAdapter.notifyDataSetChanged();
    }

    private void showApps() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) panel.getLayoutParams();
        size.width = Math.min(dp(600), metrics.widthPixels - dp(32));
        size.height = Math.min(dp(480), metrics.heightPixels - dp(64));
        panel.setLayoutParams(size);
        title.setText(R.string.share_code_apps);
        results = new FrameLayout(this);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        layout.topMargin = dp(12);
        panel.addView(results, layout);
        loadApps();
    }

    private void loadApps() {
        // Consume every confirmation while the list is loading.
        ignoreConfirmDownTimeThrough = Long.MAX_VALUE;
        showStatus(R.string.share_code_loading, true, false);
        // The catalog needs no cached APKs and can load while a canceled download winds down.
        catalogWorker.execute(() -> {
            try {
                List<ShareCodeApkRepository.Apk> apks = ShareCodeApkRepository.list();
                Map<String, String> knownPackages =
                        ShareCodeApkRepository.resolvePackageNames(this, apks);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
                    packageNames.clear();
                    packageNames.putAll(knownPackages);
                    if (apks.isEmpty()) {
                        showStatus(R.string.share_code_empty, false, false);
                    } else {
                        showList(apks);
                        refreshInstalledApps();
                    }
                });
            } catch (IOException | JSONException error) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w(TAG, "Could not load the shared APK list", error);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
                        showStatus(R.string.share_code_load_failed, false, true);
                    }
                });
            }
        });
    }

    private void showStatus(int message, boolean loading, boolean retry) {
        results.removeAllViews();
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER);
        results.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (loading) status.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView feedback = new TextView(this);
        feedback.setGravity(Gravity.CENTER);
        feedback.setTextColor(TEXT_COLOR);
        feedback.setTextSize(15);
        feedback.setText(message);
        if (loading) feedback.setPadding(0, dp(12), 0, 0);
        status.addView(feedback);
        if (retry) {
            Button button = new Button(this);
            button.setText(R.string.share_code_retry);
            button.setOnClickListener(view -> loadApps());
            status.addView(button);
            button.requestFocus();
        }
    }

    private void showList(List<ShareCodeApkRepository.Apk> apks) {
        results.removeAllViews();

        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(8));
        grid.setCacheColorHint(0);
        grid.setSelector(new ColorDrawable());
        CompatApi.disableDefaultFocusHighlight(grid);
        // GridView keeps focus itself and reports the active tile through selection.
        int[] selected = {android.R.attr.state_selected};
        int[] idle = {};
        ColorStateList nameColors = new ColorStateList(
                new int[][]{selected, idle}, new int[]{FOCUS_TEXT_COLOR, TEXT_COLOR});

        appsAdapter = new ArrayAdapter<ShareCodeApkRepository.Apk>(
                this, android.R.layout.simple_list_item_1, apks) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ApkTile tile = convertView == null
                        ? new ApkTile(nameColors, selected, idle) : (ApkTile) convertView;
                tile.bind(apks.get(position));
                return tile;
            }
        };
        grid.setAdapter(appsAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> selectApk(apks.get(position)));
        results.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grid.requestFocus();
    }

    private final class ApkTile extends LinearLayout {
        private final ImageView appIcon;
        private final TextView name;
        private final ImageView statusIcon;

        ApkTile(ColorStateList colors, int[] selected, int[] idle) {
            super(ShareCodeActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(48));
            setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable selectedTile = new GradientDrawable();
            selectedTile.setColor(FOCUS_BACKGROUND);
            selectedTile.setCornerRadius(dp(12));
            GradientDrawable idleTile = new GradientDrawable();
            idleTile.setColor(0x12FFFFFF);
            idleTile.setCornerRadius(dp(12));
            StateListDrawable tiles = new StateListDrawable();
            tiles.addState(selected, selectedTile);
            tiles.addState(idle, idleTile);
            setBackground(tiles);

            appIcon = new ImageView(ShareCodeActivity.this);
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            appIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams appIconSize = new LayoutParams(dp(24), dp(24));
            appIconSize.setMarginEnd(dp(8));
            addView(appIcon, appIconSize);

            name = new TextView(ShareCodeActivity.this);
            name.setTextColor(colors);
            name.setTextSize(16);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            name.setMarqueeRepeatLimit(-1);
            addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

            statusIcon = new ImageView(ShareCodeActivity.this);
            statusIcon.setDuplicateParentStateEnabled(true);
            CompatApi.imageTint(statusIcon, colors);
            LayoutParams iconSize = new LayoutParams(dp(20), dp(20));
            iconSize.setMarginStart(dp(8));
            addView(statusIcon, iconSize);
        }

        void bind(ShareCodeApkRepository.Apk apk) {
            name.setText(apk.fileName);
            ApplicationInfo installed = installedApps.get(packageNames.get(apk.sha));
            appIcon.setImageDrawable(installed == null ? null : installed.loadIcon(getPackageManager()));
            appIcon.setVisibility(installed == null ? GONE : VISIBLE);
            if (installed != null) {
                statusIcon.setImageResource(R.drawable.ic_share_code_open);
                statusIcon.setContentDescription(getString(R.string.share_code_installed));
            } else if (pendingApk != null && pendingApk.getName().equals(apk.sha + ".apk")) {
                statusIcon.setImageResource(R.drawable.ic_menu_check);
                statusIcon.setContentDescription(getString(R.string.share_code_downloaded));
            } else {
                statusIcon.setImageResource(R.drawable.ic_qs_update);
                statusIcon.setContentDescription(getString(R.string.share_code_not_downloaded));
            }
        }
    }

    private void selectApk(ShareCodeApkRepository.Apk apk) {
        if (downloadDialog != null || pendingApk != null) return;
        if (openInstalledApp(packageNames.get(apk.sha))) return;
        refreshInstalledApps();
        downloadApk(apk);
    }

    private boolean openInstalledApp(String packageName) {
        if (packageName == null) return false;
        PackageManager packages = getPackageManager();
        try {
            packages.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
        if (ChildLock.locked(this)) {
            finish();
            return true;
        }
        Intent launch = CompatApi.leanbackLaunch(packages, packageName);
        if (launch == null) launch = packages.getLaunchIntentForPackage(packageName);
        try {
            if (launch != null) {
                startActivity(launch);
                return true;
            }
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w(TAG, "Could not launch the installed shared app", error);
        }
        Toast.makeText(this, R.string.share_code_open_failed, Toast.LENGTH_LONG).show();
        return true;
    }

    private void downloadApk(ShareCodeApkRepository.Apk apk) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = getLayoutInflater().inflate(R.layout.share_code_download_dialog, null);
        content.setBackground(QuickSettingsGlass.dialogSurface(dp(26), dp(1)));
        dialog.setContentView(content);

        TextView fileName = content.findViewById(R.id.share_download_name);
        fileName.setTypeface(UiFont.get(this, R.string.font_medium));
        fileName.setText(apk.fileName);
        ProgressBar progress = content.findViewById(R.id.share_download_progress);
        TextView status = content.findViewById(R.id.share_download_status);
        TextView percentage = content.findViewById(R.id.share_download_percentage);
        percentage.setTypeface(UiFont.get(this, R.string.font_medium));
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentage.setText(percentFormat.format(0));
        String totalSize = Formatter.formatShortFileSize(this, apk.size);

        TextView cancel = content.findViewById(R.id.share_download_cancel);
        cancel.setTypeface(UiFont.get(this, R.string.font_medium));
        CompatApi.disableDefaultFocusHighlight(cancel);
        cancel.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{FOCUS_TEXT_COLOR, TEXT_COLOR}));
        cancel.setOnClickListener(view -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(window -> {
            if (downloadDialog != dialog) return;
            downloadDialog = null;
            cancelDownload();
        });
        // A held confirmation that opened the dialog must not also activate Cancel.
        long openedAt = SystemClock.uptimeMillis();
        dialog.setOnKeyListener((window, key, event) ->
                isConfirmKey(key) && event.getDownTime() <= openedAt);
        downloadDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.42f);
            int available = getResources().getDisplayMetrics().widthPixels - dp(48);
            window.setLayout(Math.min(available, dp(380)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        cancel.requestFocus();

        File file = ShareCodeApkRepository.completedFile(getCacheDir(), apk.sha);
        downloadTask = FILE_WORKER.submit(() -> {
            try {
                ShareCodeApkRepository.download(apk, file, new ShareCodeApkRepository.Progress() {
                    @Override
                    public void update(int percent, long received) {
                        runOnUiThread(() -> {
                            if (downloadDialog != dialog || !dialog.isShowing()) return;
                            progress.setProgress(percent);
                            percentage.setText(percentFormat.format(percent / 100.0));
                            status.setText(getString(R.string.share_code_download_progress,
                                    Formatter.formatShortFileSize(ShareCodeActivity.this, received), totalSize));
                        });
                    }

                    @Override
                    public void reconnecting(int attempt, int maximum) {
                        runOnUiThread(() -> {
                            if (downloadDialog != dialog || !dialog.isShowing()) return;
                            status.setText(getString(R.string.share_code_download_reconnecting, attempt, maximum));
                        });
                    }
                });
                String packageName = ShareCodeApkRepository.rememberPackageName(this, apk, file);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || downloadDialog != dialog || !dialog.isShowing()) {
                        return;
                    }
                    dismissFinishedDownload(dialog);
                    pendingApk = file;
                    if (packageName != null) packageNames.put(apk.sha, packageName);
                    if (openInstalledApp(packageName)) {
                        clearInstallerApk();
                        refreshInstalledApps();
                        return;
                    }
                    refreshInstalledApps();
                    openInstaller();
                });
            } catch (IOException | RuntimeException error) {
                ShareCodeApkRepository.deleteCachedApk(file);
                if (Thread.currentThread().isInterrupted()) return;
                Log.w(TAG, "Could not download shared APK", error);
                runOnUiThread(() -> {
                    if (downloadDialog != dialog || !dialog.isShowing()) return;
                    dismissFinishedDownload(dialog);
                    Toast.makeText(this, R.string.share_code_download_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void dismissFinishedDownload(Dialog dialog) {
        // Clear the active dialog first so its dismiss listener skips cancellation.
        downloadTask = null;
        downloadDialog = null;
        dialog.dismiss();
    }

    private void cancelDownload() {
        if (downloadTask == null) return;
        // The file worker closes the transfer and leaves the saved bytes available for the next click.
        downloadTask.cancel(true);
        downloadTask = null;
    }

    private void clearInstallerApk() {
        if (pendingApk == null) return;
        File file = pendingApk;
        pendingApk = null;
        FILE_WORKER.execute(() -> ShareCodeApkRepository.deleteCachedApk(file));
    }

    @SuppressWarnings("deprecation")
    private void openInstaller() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", pendingApk);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(install, REQUEST_INSTALL);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not open the APK installer", error);
            clearInstallerApk();
            refreshInstalledApps();
            Toast.makeText(this, R.string.share_code_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL) return;
        // Success, failure and cancellation all release the APK once the installer returns.
        clearInstallerApk();
        refreshInstalledApps();
        if (resultCode == RESULT_FIRST_USER) {
            Toast.makeText(this, R.string.share_code_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** One editor keeps the IME connected while the code is drawn as four cells. */
    private static final class CodeInput extends EditText {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cellBounds = new RectF();
        private final float density;

        CodeInput(ShareCodeActivity activity) {
            super(activity);
            density = getResources().getDisplayMetrics().density;
            setSingleLine(true);
            setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            setRawInputType(InputType.TYPE_CLASS_NUMBER);
            setFilters(new InputFilter[]{new InputFilter.LengthFilter(CODE_LENGTH)});
            setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            setHint(R.string.share_code_input_hint);
            setBackground(null);
            setPadding(0, 0, 0, 0);
            setCursorVisible(false);
            setTextSize(24);
            setTypeface(UiFont.get(activity, R.string.font_medium));
            CompatApi.disableDefaultFocusHighlight(this);

            setOnKeyListener((view, key, event) -> {
                if (!isConfirmKey(key)) return false;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    InputMethodManager keyboard = CompatApi.service(activity, InputMethodManager.class);
                    if (keyboard != null) keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
                }
                return true;
            });
        }

        @Override
        protected void onSelectionChanged(int start, int end) {
            super.onSelectionChanged(start, end);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Editable digits = getText();
            int cursor = Math.max(0, Math.min(digits.length(), getSelectionStart()));
            float gap = 8 * density;
            float cellWidth = (getWidth() - gap * (CODE_LENGTH - 1)) / CODE_LENGTH;
            float centerY = getHeight() / 2f;
            float stroke = density;
            float radius = 12 * density;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(getTextSize());
            paint.setTypeface(getTypeface());
            float baseline = centerY - (paint.ascent() + paint.descent()) / 2f;

            for (int index = 0; index < CODE_LENGTH; index++) {
                boolean active = hasFocus() && index == cursor;
                float left = index * (cellWidth + gap);
                float right = left + cellWidth;
                float centerX = (left + right) / 2f;
                cellBounds.set(left + stroke, stroke, right - stroke, getHeight() - stroke);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(active ? 0x26FFFFFF : 0x12FFFFFF);
                canvas.drawRoundRect(cellBounds, radius, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(active ? 2 * stroke : stroke);
                paint.setColor(active ? 0xFFF5F7FA : 0x40FFFFFF);
                canvas.drawRoundRect(cellBounds, radius, radius, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFF5F7FA);
                if (index < digits.length()) {
                    canvas.drawText(digits, index, index + 1, centerX, baseline, paint);
                }
                if (active) {
                    float caretX = index < digits.length() ? centerX - 12 * density : centerX;
                    canvas.drawRect(caretX, centerY - 11 * density, caretX + 2 * density,
                            centerY + 11 * density, paint);
                }
            }
        }
    }
}
