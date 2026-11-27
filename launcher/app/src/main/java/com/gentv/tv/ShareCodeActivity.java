package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.zxing.WriterException;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** TV share-code entry, app catalog and system installer handoff. */
public final class ShareCodeActivity extends Activity {
    private static final int TEXT_COLOR = 0xFFF5F7FA;
    private static final int FOCUS_TEXT_COLOR = 0xFF121418;
    private static final int FOCUS_BACKGROUND = 0xF7FFFFFF;
    private static final String STATE_APPS_URL = "appsUrl";
    private static final String STATE_CODE = "code";
    private static final String STATE_ADDING = "adding";
    private static final String STATE_SAVED_CODE = "savedCode";
    private static final String STATE_PENDING_APK = "pendingApk";
    private static final int REQUEST_INSTALL = 1;
    private static final int MAX_ICON_PREVIEWS = 32;
    private static final String TAG = "ShareCodeActivity";

    // Serialize APK writes and cleanup across activity recreation.
    private static final ExecutorService FILE_WORKER = Executors.newSingleThreadExecutor();

    private final ExecutorService catalogWorker = Executors.newSingleThreadExecutor();
    private final Map<String, String> packageNames = new HashMap<>();
    private final Map<String, ApplicationInfo> installedApps = new HashMap<>();
    private final Set<String> installableApks = new HashSet<>();
    private LruCache<String, Bitmap> downloadedIcons = new LruCache<>(MAX_ICON_PREVIEWS);
    private LinearLayout panel;
    private TextView title;
    private ShareCodeEntryView entry;
    private ShareCodeLinks links;
    private ShareCodeAddView addPage;
    private LanShareCodeReceiver receiver;
    private ShareCodeLinks.Link savedLink;
    private FrameLayout results;
    private Button catalogBack;
    private String appsUrl;
    private View previousPageFocus;
    private ArrayAdapter<ShareCodeApkRepository.Apk> appsAdapter;
    private Dialog deleteDialog;
    private Dialog downloadDialog;
    private Future<?> catalogTask;
    private Future<?> downloadTask;
    private Future<?> cacheCleanup;
    private File pendingApk;
    // Compare down times so a held key and its release are treated as the same press.
    private long ignoreConfirmDownTimeThrough = -1;
    private long entryConfirmDownTime;
    private View entryConfirmFocus;

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
        panel.setClickable(true);
        scrim.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        title = new TextView(this);
        title.setTextColor(TEXT_COLOR);
        title.setTypeface(UiFont.get(this, R.string.font_medium));
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        setContentView(scrim);
        links = new ShareCodeLinks(this);
        // The installer may still hold a content URI when Android recreates this activity.
        File cache = getCacheDir();
        String installerFile = savedInstanceState == null
                ? null : savedInstanceState.getString(STATE_PENDING_APK);
        if (ShareCodeApkRepository.isCachedApkName(installerFile)) {
            pendingApk = new File(new File(cache, ShareCodeApkRepository.CACHE_DIRECTORY), installerFile);
        }
        if (savedInstanceState == null) clearApkCache();
        entry = new ShareCodeEntryView(this,
                savedInstanceState == null ? "" : savedInstanceState.getString(STATE_CODE, ""),
                this::openCode, this::addShareCode, this::openSavedApps, this::confirmDeleteLink);
        panel.addView(entry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layoutEntryPanel();
        entry.showSavedLinks(links.list(), false);
        entry.focusCodeAction();
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_ADDING)) {
            createAddPage();
            ShareCodeLinks.Link restored = links.find(savedInstanceState.getString(STATE_SAVED_CODE));
            if (restored != null) showSavedCode(restored);
        }
        String restoredUrl = savedInstanceState == null ? null : savedInstanceState.getString(STATE_APPS_URL);
        if (restoredUrl != null) showApps(restoredUrl);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (appsUrl != null) state.putString(STATE_APPS_URL, appsUrl);
        state.putString(STATE_CODE, entry.code());
        state.putBoolean(STATE_ADDING, addPage != null);
        if (savedLink != null) state.putString(STATE_SAVED_CODE, savedLink.code);
        if (pendingApk != null) state.putString(STATE_PENDING_APK, pendingApk.getName());
    }

    @Override
    protected void onDestroy() {
        stopReceiver();
        catalogWorker.shutdownNow();
        cancelDownload();
        if (deleteDialog != null) deleteDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        if (isFinishing() && pendingApk == null) clearApkCache();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (addPage != null && savedLink == null) startReceiver();
    }

    @Override
    protected void onStop() {
        ShareCodeLinks.Link received = stopReceiver();
        // The receiver may finish saving before its main-thread callback is delivered.
        if (received != null && addPage != null && !isFinishing()) showSavedCode(received);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInstalledApps();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isConfirmKey(event.getKeyCode())) {
            if (deleteDialog != null || event.getDownTime() <= ignoreConfirmDownTimeThrough) return true;
            if (addPage == null && results == null && handleEntryConfirm(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleEntryConfirm(KeyEvent event) {
        View focused = getCurrentFocus();
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !event.isLongPress()) {
            entryConfirmFocus = focused;
            entryConfirmDownTime = event.getDownTime();
            return false;
        }
        // A dialog can return focus while the remote is still repeating its confirmation press.
        boolean startedHere = focused != null && focused == entryConfirmFocus
                && event.getDownTime() == entryConfirmDownTime;
        if (action == KeyEvent.ACTION_UP) {
            entryConfirmFocus = null;
            return !startedHere;
        }
        if (action != KeyEvent.ACTION_DOWN) return false;
        if (!startedHere) return true;
        // Some remotes flag a long press before the View's own hold timer fires.
        if (event.isLongPress() && entry.performSavedCardLongClick(focused)) {
            ignoreConfirmDownTimeThrough = event.getDownTime();
            return true;
        }
        return false;
    }

    private static boolean isConfirmKey(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (addPage == null && results == null && entry.handleKey(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (results != null) closeApps();
        else if (addPage != null) closeAddPage();
        else super.onBackPressed();
    }

    private void openCode() {
        ShareCodeLinks.Link link = links.find(entry.code());
        if (link != null) openSavedApps(link);
        else showApps(ShareCodeApkRepository.DEFAULT_REPOSITORY);
    }

    private void addShareCode() {
        createAddPage();
        startReceiver();
    }

    private void createAddPage() {
        entry.setVisibility(View.GONE);
        title.setVisibility(View.GONE);
        addPage = new ShareCodeAddView(this);
        panel.addView(addPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startReceiver() {
        stopReceiver();
        if (!links.hasRoom()) {
            closeAddPage();
            return;
        }
        savedLink = null;
        String address = TvLan.localAddress();
        if (address != null) {
            try {
                receiver = new LanShareCodeReceiver(address, readShareForm(), links, (source, link) ->
                        runOnUiThread(() -> {
                            if (receiver != source || isFinishing() || isDestroyed()) return;
                            receiver = null;
                            showSavedCode(link);
                        }));
                String url = receiver.url();
                addPage.showReceiver(url, TvLan.qrCode(url), this::closeAddPage);
                receiver.start();
                return;
            } catch (IOException | WriterException error) {
                Log.w(TAG, "Could not open share-code receiver", error);
                stopReceiver();
            }
        }
        addPage.showUnavailable(this::closeAddPage, this::startReceiver);
    }

    private String readShareForm() throws IOException {
        try (InputStream input = getAssets().open("share_code_form.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private void showSavedCode(ShareCodeLinks.Link link) {
        savedLink = link;
        addPage.showSaved(link, () -> openSavedApps(link),
                links.hasRoom() ? this::startReceiver : null, this::finish);
    }

    private ShareCodeLinks.Link stopReceiver() {
        LanShareCodeReceiver active = receiver;
        receiver = null;
        if (active == null) return null;
        active.close();
        return active.savedLink();
    }

    private void closeAddPage() {
        removeAddPage();
        title.setVisibility(View.VISIBLE);
        entry.setVisibility(View.VISIBLE);
        entry.showSavedLinks(links.list(), true);
    }

    private void removeAddPage() {
        stopReceiver();
        panel.removeView(addPage);
        addPage = null;
        savedLink = null;
    }

    private void confirmDeleteLink(ShareCodeLinks.Link link) {
        if (deleteDialog != null || isFinishing() || isDestroyed()) return;
        entryConfirmFocus = null;
        Dialog dialog = new Dialog(this);
        deleteDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(window -> {
            ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
            deleteDialog = null;
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(entry.size(24), entry.size(20), entry.size(24), entry.size(20));
        content.setBackground(QuickSettingsGlass.dialogSurface(dp(20), dp(1)));
        TextView prompt = new TextView(this);
        prompt.setText(R.string.share_code_delete_prompt);
        prompt.setTextColor(TEXT_COLOR);
        prompt.setTextSize(entry.textSize(18));
        prompt.setTypeface(UiFont.get(this, R.string.font_medium));
        content.addView(prompt);
        TextView name = new TextView(this);
        name.setText(getString(R.string.share_code_delete_detail, link.name, ShareCodeLinks.formatted(link.code)));
        name.setTextColor(0xFFB4B6BE);
        name.setTextSize(entry.textSize(14));
        LinearLayout.LayoutParams detail = new LinearLayout.LayoutParams(-1, -2);
        detail.topMargin = entry.size(8);
        content.addView(name, detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionRow = new LinearLayout.LayoutParams(-1, entry.size(36));
        actionRow.topMargin = entry.size(20);
        content.addView(actions, actionRow);
        Button cancel = ShareCodeEntryView.button(this, prompt.getTypeface(), getString(android.R.string.cancel),
                entry.textSize(14), entry.size(8), dialog::dismiss);
        actions.addView(cancel, new LinearLayout.LayoutParams(entry.size(84), -1));
        Button delete = ShareCodeEntryView.button(this, prompt.getTypeface(), getString(R.string.share_code_remove),
                entry.textSize(14), entry.size(8), () -> deleteLink(dialog, link));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(entry.size(84), -1);
        deleteParams.setMarginStart(entry.size(8));
        actions.addView(delete, deleteParams);

        // Each dialog action takes a fresh press and runs when the key is released.
        long openedAt = SystemClock.uptimeMillis();
        dialog.setOnKeyListener((window, key, event) ->
                isConfirmKey(key) && (event.getDownTime() <= openedAt
                        || event.getRepeatCount() > 0 || event.isLongPress()));
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.42f);
            int available = getResources().getDisplayMetrics().widthPixels - dp(48);
            window.setLayout(Math.min(available, entry.size(320)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        cancel.requestFocusFromTouch();
    }

    private void deleteLink(Dialog dialog, ShareCodeLinks.Link link) {
        try {
            links.delete(link.code);
            dialog.dismiss();
            entry.showSavedLinks(links.list(), true);
        } catch (IOException error) {
            Log.w(TAG, "Could not delete shared link", error);
            Toast.makeText(this, R.string.share_code_remove_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openSavedApps(ShareCodeLinks.Link link) {
        showApps(link.url);
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

    private void layoutEntryPanel() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) panel.getLayoutParams();
        size.width = Math.min(dp(800), metrics.widthPixels - dp(32));
        size.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        panel.setLayoutParams(size);
        panel.setPadding(entry.size(42), entry.size(26), entry.size(42), entry.size(26));
        panel.setBackground(ShareCodeEntryView.surface(dp(16), 0xFF101116, dp(1), 0x40FFFFFF));
        title.setText(R.string.share_code_input_title);
        title.setTextSize(entry.textSize(23));
    }

    private void showApps(String url) {
        if (results != null) return;
        String repository = ShareCodeApkRepository.repositoryPath(url);
        if (repository == null) {
            showLiveStreamToast();
            return;
        }
        appsUrl = url;
        // Keep the originating page available until the repository yields an APK list.
        View previousPage = addPage == null ? entry : addPage;
        previousPageFocus = previousPage.findFocus();
        previousPage.setVisibility(View.GONE);
        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackgroundResource(R.drawable.quick_settings_panel);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) panel.getLayoutParams();
        size.width = Math.min(dp(600), metrics.widthPixels - dp(32));
        size.height = Math.min(dp(480), metrics.heightPixels - dp(64));
        panel.setLayoutParams(size);
        title.setText(R.string.share_code_apps);
        title.setTextSize(18);
        title.setVisibility(View.VISIBLE);
        results = new FrameLayout(this);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        layout.topMargin = dp(12);
        panel.addView(results, layout);

        catalogBack = catalogButton(R.string.share_code_back, this::closeApps);
        catalogBack.setId(View.generateViewId());
        LinearLayout.LayoutParams backLayout = new LinearLayout.LayoutParams(dp(100), dp(36));
        backLayout.topMargin = dp(12);
        panel.addView(catalogBack, backLayout);
        loadApps(repository);
    }

    private void closeApps() {
        if (catalogTask != null) {
            catalogTask.cancel(true);
            catalogTask = null;
        }
        if (pendingApk == null) clearApkCache();
        appsAdapter = null;
        packageNames.clear();
        installedApps.clear();
        installableApks.clear();
        downloadedIcons.evictAll();
        panel.removeView(results);
        panel.removeView(catalogBack);
        results = null;
        catalogBack = null;
        appsUrl = null;
        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
        layoutEntryPanel();
        View previousPage = addPage == null ? entry : addPage;
        title.setVisibility(addPage == null ? View.VISIBLE : View.GONE);
        previousPage.setVisibility(View.VISIBLE);
        View focus = previousPageFocus;
        previousPageFocus = null;
        if (focus == null || !focus.requestFocusFromTouch()) {
            if (addPage == null) entry.focusCodeAction();
            else addPage.focusAction();
        }
    }

    private void showLiveStreamToast() {
        Toast.makeText(this, R.string.share_code_live_stream, Toast.LENGTH_SHORT).show();
    }

    private void clearApkCache() {
        File cache = getCacheDir();
        cacheCleanup = FILE_WORKER.submit(() -> ShareCodeApkRepository.clearCache(cache));
    }

    private void loadApps(String repository) {
        // Consume every confirmation while the list is loading.
        ignoreConfirmDownTimeThrough = Long.MAX_VALUE;
        showStatus(R.string.share_code_loading, true, null);
        FrameLayout loadingPage = results;
        Future<?> cleanup = cacheCleanup;
        // Fetch the catalog while the worker clears downloads left by a previous session.
        catalogTask = catalogWorker.submit(() -> {
            try {
                List<ShareCodeApkRepository.Apk> apks = ShareCodeApkRepository.list(repository);
                if (cleanup != null) cleanup.get();
                Map<String, String> knownPackages =
                        ShareCodeApkRepository.resolvePackageNames(this, apks);
                Set<String> available = new HashSet<>();
                LruCache<String, Bitmap> previews = new LruCache<>(MAX_ICON_PREVIEWS);
                for (ShareCodeApkRepository.Apk apk : apks) {
                    if (Thread.currentThread().isInterrupted()) return;
                    File cached = completedApk(apk);
                    if (cached == null || available.contains(apk.cacheKey)) continue;
                    ApplicationInfo archive = ShareCodeApkRepository.inspectDownload(this, apk, cached);
                    if (archive == null) continue;
                    available.add(apk.cacheKey);
                    knownPackages.put(apk.cacheKey, archive.packageName);
                    Bitmap icon = ShareCodeApkRepository.readArchiveIcon(this, archive);
                    if (icon != null) previews.put(apk.cacheKey, icon);
                }
                runOnUiThread(() -> {
                    if (results != loadingPage || isFinishing() || isDestroyed()) return;
                    catalogTask = null;
                    ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
                    packageNames.clear();
                    packageNames.putAll(knownPackages);
                    installableApks.addAll(available);
                    downloadedIcons = previews;
                    if (apks.isEmpty()) {
                        closeApps();
                        showLiveStreamToast();
                    } else {
                        showList(apks);
                        refreshInstalledApps();
                    }
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (IOException | JSONException | ExecutionException error) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w(TAG, "Could not load the shared APK list", error);
                runOnUiThread(() -> {
                    if (results == loadingPage && !isFinishing() && !isDestroyed()) {
                        catalogTask = null;
                        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
                        showStatus(R.string.share_code_load_failed, false, () -> loadApps(repository));
                    }
                });
            }
        });
    }

    private Button catalogButton(int label, Runnable action) {
        return ShareCodeEntryView.button(this, title.getTypeface(), getString(label), 14, dp(8), action);
    }

    private void showStatus(int message, boolean loading, Runnable retry) {
        results.removeAllViews();
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER);
        results.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (loading) {
            status.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        TextView feedback = new TextView(this);
        feedback.setGravity(Gravity.CENTER);
        feedback.setTextColor(TEXT_COLOR);
        feedback.setTextSize(15);
        feedback.setText(message);
        if (loading) feedback.setPadding(0, dp(12), 0, 0);
        status.addView(feedback);
        if (retry != null) {
            Button button = catalogButton(R.string.share_code_retry, retry);
            LinearLayout.LayoutParams retryLayout = new LinearLayout.LayoutParams(dp(100), dp(36));
            retryLayout.topMargin = dp(12);
            status.addView(button, retryLayout);
            button.requestFocusFromTouch();
        } else {
            catalogBack.requestFocusFromTouch();
        }
    }

    private void showList(List<ShareCodeApkRepository.Apk> apks) {
        if (addPage != null) {
            removeAddPage();
            previousPageFocus = null;
            entry.showSavedLinks(links.list(), false);
        }
        results.removeAllViews();

        GridView grid = new GridView(this);
        grid.setId(View.generateViewId());
        grid.setNextFocusDownId(catalogBack.getId());
        catalogBack.setNextFocusUpId(grid.getId());
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
        grid.requestFocusFromTouch();
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
            ApplicationInfo installed = installedApps.get(packageNames.get(apk.cacheKey));
            boolean downloaded = installableApk(apk) != null;
            Bitmap icon = downloaded ? downloadedIcons.get(apk.cacheKey) : null;
            if (installed != null) {
                appIcon.setImageDrawable(installed.loadIcon(getPackageManager()));
            } else if (icon != null) {
                appIcon.setImageBitmap(icon);
            } else {
                appIcon.setImageResource(placeholderIcon(apk.fileName));
            }
            if (installed != null) {
                statusIcon.setImageResource(R.drawable.ic_share_code_open);
                statusIcon.setContentDescription(getString(R.string.share_code_installed));
            } else if (downloaded) {
                statusIcon.setImageResource(R.drawable.ic_share_code_downloaded);
                statusIcon.setContentDescription(getString(R.string.share_code_ready_to_install));
            } else {
                statusIcon.setImageResource(R.drawable.ic_qs_update);
                statusIcon.setContentDescription(getString(R.string.share_code_not_downloaded));
            }
        }
    }

    private static int placeholderIcon(String name) {
        // Catalog filenames include version suffixes. Match the app name so updates keep their artwork.
        if (name.startsWith("蓝莓音乐") || name.startsWith("融乐Music")) {
            return R.drawable.ic_share_placeholder_music;
        }
        if (name.startsWith("云漫") || name.startsWith("喵趣漫画")) {
            return R.drawable.ic_share_placeholder_comic;
        }
        if (name.contains("小说") || name.startsWith("小书亭") || name.startsWith("笔趣阁")
                || name.startsWith("省心英语")) {
            return R.drawable.ic_share_placeholder_book;
        }
        if (name.startsWith("扫描全能王")) return R.drawable.ic_share_placeholder_scan;
        if (name.startsWith("小猪电玩")) return R.drawable.ic_share_placeholder_game;
        if (name.startsWith("特效变声器")) return R.drawable.ic_share_placeholder_voice;
        if (name.contains("资源") || name.startsWith("搜盘器") || name.startsWith("磁宝库")) {
            return R.drawable.ic_share_placeholder_search;
        }
        if (name.startsWith("香蕉视频")) return R.drawable.ic_share_placeholder_banana;
        if (name.contains("影视") || name.contains("视频") || name.startsWith("剧荒")
                || name.startsWith("电影天堂") || name.startsWith("红枫影评")
                || name.startsWith("网飞猫") || name.startsWith("黑白弹幕")) {
            return R.drawable.ic_share_placeholder_video;
        }
        return R.drawable.ic_share_placeholder_app;
    }

    private void selectApk(ShareCodeApkRepository.Apk apk) {
        if (downloadDialog != null || pendingApk != null) return;
        if (openInstalledApp(packageNames.get(apk.cacheKey))) return;
        refreshInstalledApps();
        File cached = installableApk(apk);
        if (cached != null) openInstaller(cached);
        else downloadApk(apk);
    }

    private File completedApk(ShareCodeApkRepository.Apk apk) {
        // A completed transfer can still fail Android's archive parser.
        File file = ShareCodeApkRepository.completedFile(getCacheDir(), apk.cacheKey);
        return file.isFile() && file.length() == apk.size ? file : null;
    }

    private File installableApk(ShareCodeApkRepository.Apk apk) {
        return installableApks.contains(apk.cacheKey) ? completedApk(apk) : null;
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

        File file = ShareCodeApkRepository.completedFile(getCacheDir(), apk.cacheKey);
        FrameLayout catalog = results;
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
                ApplicationInfo archive = ShareCodeApkRepository.inspectDownload(this, apk, file);
                if (archive == null) {
                    ShareCodeApkRepository.deleteCachedApk(file);
                    throw new IOException("Downloaded file is not an Android APK");
                }
                Bitmap icon = ShareCodeApkRepository.readArchiveIcon(this, archive);
                runOnUiThread(() -> {
                    if (results != catalog || isFinishing() || isDestroyed()) return;
                    // A cancellation can arrive after validation. Keep the APK available in the
                    // current catalog and require another click before opening the installer.
                    installableApks.add(apk.cacheKey);
                    packageNames.put(apk.cacheKey, archive.packageName);
                    if (icon != null) downloadedIcons.put(apk.cacheKey, icon);
                    refreshInstalledApps();
                    if (downloadDialog != dialog || !dialog.isShowing()) return;
                    dismissFinishedDownload(dialog);
                    if (!openInstalledApp(archive.packageName)) openInstaller(file);
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

    @SuppressWarnings("deprecation")
    private void openInstaller(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            pendingApk = apk;
            startActivityForResult(install, REQUEST_INSTALL);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not open the APK installer", error);
            pendingApk = null;
            refreshInstalledApps();
            Toast.makeText(this, R.string.share_code_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL) return;
        // Keep the verified APK for the next click until the shared app list closes.
        pendingApk = null;
        if (results == null) clearApkCache();
        refreshInstalledApps();
        if (resultCode == RESULT_FIRST_USER) {
            Toast.makeText(this, R.string.share_code_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
