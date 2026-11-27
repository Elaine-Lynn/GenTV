package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
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
import androidx.core.graphics.drawable.DrawableCompat;

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
    private static final String STATE_APPS_URL = "appsUrl";
    private static final String STATE_APPS_NAME = "appsName";
    private static final int TILE_HEIGHT = 76;
    private static final int TILE_ICON = 46;
    private static final float TILE_LIFT = 1.025f;
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
    // Share-code lookups, deletes and syncs with the GenTV API, kept apart from catalog downloads.
    private final ExecutorService codeWorker = Executors.newSingleThreadExecutor();
    private boolean deleting;
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
    private String appsName;
    private View catalogHeader;
    private TextView catalogSubtitle;
    private ValueAnimator loadingPulse;
    private boolean motion;
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
        scrim.setBackgroundColor(0x6605070C);
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
        Config appearance = Config.load(this);
        motion = appearance.animations && !appearance.reducedMotion;
        if (savedInstanceState == null) animateEntrance(scrim);
        codeWorker.execute(() -> {
            if (links.syncLocalLinks()) runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && addPage == null && results == null) {
                    entry.showSavedLinks(links.list(), false);
                }
            });
        });
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_ADDING)) {
            createAddPage();
            ShareCodeLinks.Link restored = links.find(savedInstanceState.getString(STATE_SAVED_CODE));
            if (restored != null) showSavedCode(restored);
        }
        String restoredUrl = savedInstanceState == null ? null : savedInstanceState.getString(STATE_APPS_URL);
        if (restoredUrl != null) showApps(restoredUrl, savedInstanceState.getString(STATE_APPS_NAME));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (appsUrl != null) state.putString(STATE_APPS_URL, appsUrl);
        if (appsName != null) state.putString(STATE_APPS_NAME, appsName);
        state.putString(STATE_CODE, entry.code());
        state.putBoolean(STATE_ADDING, addPage != null);
        if (savedLink != null) state.putString(STATE_SAVED_CODE, savedLink.code);
        if (pendingApk != null) state.putString(STATE_PENDING_APK, pendingApk.getName());
    }

    @Override
    protected void onDestroy() {
        stopReceiver();
        catalogWorker.shutdownNow();
        codeWorker.shutdownNow();
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
        // Home blurs behind the sheet, as it does behind the Control Center.
        ToolboxBackdrop.visible(true);
        refreshInstalledApps();
    }

    @Override
    protected void onPause() {
        if (isFinishing()) ToolboxBackdrop.visible(false);
        super.onPause();
    }

    private void animateEntrance(View scrim) {
        if (!motion) return;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).setInterpolator(CompatApi.easeOut()).start();
        panel.setAlpha(0f);
        panel.setTranslationY(dp(10));
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(CompatApi.easeOut()).start();
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
        String code = entry.code();
        ShareCodeLinks.Link link = links.find(code);
        if (link != null) {
            openSavedApps(link);
            return;
        }
        if (!TvCloud.enabled()) {
            showApps(ShareCodeApkRepository.DEFAULT_REPOSITORY, null);
            return;
        }
        // Codes from other TVs live on the GenTV server.
        codeWorker.execute(() -> {
            ShareCodeLinks.Link remote = null;
            int failure = 0;
            try {
                remote = ShareCodeLinks.lookup(code);
                if (remote == null) failure = R.string.share_code_not_found;
            } catch (IOException error) {
                Log.w(TAG, "Share code lookup failed: " + error.getClass().getSimpleName());
                failure = R.string.share_code_server_failed;
            }
            ShareCodeLinks.Link found = remote;
            int message = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || addPage != null || results != null) return;
                if (found != null) openSavedApps(found);
                else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
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
        if (deleting) return;
        deleting = true;
        codeWorker.execute(() -> {
            boolean removed;
            try {
                links.delete(link.code);
                removed = true;
            } catch (IOException error) {
                Log.w(TAG, "Could not delete shared link: " + error.getClass().getSimpleName());
                removed = false;
            }
            boolean done = removed;
            runOnUiThread(() -> {
                deleting = false;
                if (isFinishing() || isDestroyed()) return;
                if (done) {
                    dialog.dismiss();
                    entry.showSavedLinks(links.list(), true);
                } else {
                    Toast.makeText(this, R.string.share_code_remove_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void openSavedApps(ShareCodeLinks.Link link) {
        showApps(link.url, link.name);
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
        size.width = Math.min(entry.size(640), metrics.widthPixels - dp(32));
        size.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        panel.setLayoutParams(size);
        panel.setPadding(entry.size(28), entry.size(26), entry.size(28), entry.size(26));
        // The Control Center's denser smoked glass for centred sheets; the pages carry their own headings.
        float radius = entry.size(30);
        panel.setBackground(QuickSettingsGlass.dialogSurface(radius, Math.max(1, dp(1))));
        CompatApi.roundClip(panel, radius);
        panel.setClipChildren(false);
        CompatApi.elevation(panel, dp(18));
        title.setVisibility(View.GONE);
    }

    private void showApps(String url, String name) {
        if (results != null) return;
        String repository = ShareCodeApkRepository.repositoryPath(url);
        if (repository == null) {
            showLiveStreamToast();
            return;
        }
        appsUrl = url;
        appsName = name;
        // Keep the originating page available until the repository yields an APK list.
        View previousPage = addPage == null ? entry : addPage;
        previousPageFocus = previousPage.findFocus();
        previousPage.setVisibility(View.GONE);
        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
        // The same glass sheet, opened wider for a two-column list.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) panel.getLayoutParams();
        size.width = Math.min(entry.size(760), metrics.widthPixels - dp(32));
        size.height = Math.min(entry.size(540), metrics.heightPixels - dp(48));
        panel.setLayoutParams(size);
        panel.setPadding(entry.size(24), entry.size(22), entry.size(24), entry.size(18));
        catalogHeader = catalogHeader(name, repository);
        panel.addView(catalogHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        results = new FrameLayout(this);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        layout.topMargin = entry.size(16);
        panel.addView(results, layout);
        loadApps(repository);
    }

    /** Back on the left; the shared link's name and its source centred, as on the add page. */
    private View catalogHeader(String name, String repository) {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);
        catalogBack = catalogButton(R.string.share_code_back, this::closeApps);
        Drawable icon = DrawableCompat.wrap(CompatApi.drawable(this, R.drawable.ic_ext_back)).mutate();
        icon.setBounds(0, 0, entry.size(20), entry.size(20));
        DrawableCompat.setTintList(icon, catalogBack.getTextColors());
        catalogBack.setCompoundDrawablesRelative(icon, null, null, null);
        catalogBack.setCompoundDrawablePadding(entry.size(2));
        catalogBack.setPaddingRelative(entry.size(8), 0, entry.size(15), 0);
        header.addView(catalogBack, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                entry.size(34), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView heading = catalogLabel(19, ShareCodeEntryView.INK_LIGHT, true);
        heading.setText(name == null || name.isEmpty() ? getString(R.string.share_code_apps) : name);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        CompatApi.letterSpacing(heading, -0.01f);
        titles.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        catalogSubtitle = catalogLabel(12, ShareCodeEntryView.INK_MUTED, false);
        catalogSubtitle.setText(sourceLabel(repository, -1));
        catalogSubtitle.setSingleLine(true);
        catalogSubtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = entry.size(6);
        titles.addView(catalogSubtitle, subtitleParams);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        titleParams.leftMargin = titleParams.rightMargin = entry.size(118);
        header.addView(titles, titleParams);
        return header;
    }

    private String sourceLabel(String repository, int count) {
        String source = "github.com/" + repository;
        return count < 0 ? source : source + "  ·  "
                + getResources().getQuantityString(R.plurals.share_code_app_count, count, count);
    }

    private TextView catalogLabel(int sp, int color, boolean medium) {
        TextView label = new TextView(this);
        label.setTypeface(UiFont.get(this, medium ? R.string.font_medium : R.string.font_regular));
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(color);
        label.setTextSize(entry.textSize(sp));
        return label;
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
        stopLoadingPulse();
        panel.removeView(results);
        panel.removeView(catalogHeader);
        results = null;
        catalogHeader = null;
        catalogSubtitle = null;
        catalogBack = null;
        appsUrl = null;
        appsName = null;
        ignoreConfirmDownTimeThrough = SystemClock.uptimeMillis();
        layoutEntryPanel();
        View previousPage = addPage == null ? entry : addPage;
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
        showLoading();
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
                        showStatus(R.string.share_code_load_failed, () -> loadApps(repository));
                    }
                });
            }
        });
    }

    private Button catalogButton(int label, Runnable action) {
        return ShareCodeEntryView.button(this, title.getTypeface(), getString(label), entry.textSize(13),
                entry.size(17), action);
    }

    private void clearResults() {
        stopLoadingPulse();
        results.removeAllViews();
    }

    private void stopLoadingPulse() {
        if (loadingPulse != null) loadingPulse.cancel();
        loadingPulse = null;
    }

    /** Skeleton tiles in the list's own shape breathe while GitHub answers. */
    private void showLoading() {
        clearResults();
        LinearLayout skeleton = new LinearLayout(this);
        skeleton.setOrientation(LinearLayout.VERTICAL);
        int inset = entry.size(6);
        skeleton.setPadding(inset, inset, inset, inset);
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, entry.size(TILE_HEIGHT));
            if (row > 0) lineParams.topMargin = entry.size(12);
            skeleton.addView(line, lineParams);
            for (int column = 0; column < 2; column++) {
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                if (column > 0) tileParams.setMarginStart(entry.size(14));
                line.addView(skeletonTile(row * 2 + column), tileParams);
            }
        }
        skeleton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        results.addView(skeleton, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        TextView note = catalogLabel(12, ShareCodeEntryView.INK_MUTED, false);
        note.setText(R.string.share_code_loading);
        results.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (motion) {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(skeleton, View.ALPHA, 1f, 0.42f);
            pulse.setDuration(900);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(CompatApi.easeOut());
            pulse.start();
            loadingPulse = pulse;
        }
        catalogBack.requestFocusFromTouch();
    }

    private View skeletonTile(int index) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(entry.size(14), 0, entry.size(14), 0);
        tile.setBackground(ShareCodeEntryView.surface(entry.size(18), 0x0DFFFFFF, 1, 0x14FFFFFF));
        View icon = new View(this);
        icon.setBackground(ShareCodeEntryView.surface(entry.size(12), 0x17FFFFFF, 0, 0));
        tile.addView(icon, new LinearLayout.LayoutParams(entry.size(TILE_ICON), entry.size(TILE_ICON)));
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams barsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barsParams.setMarginStart(entry.size(14));
        tile.addView(bars, barsParams);
        View name = new View(this);
        name.setBackground(ShareCodeEntryView.surface(entry.size(6), 0x17FFFFFF, 0, 0));
        // Varied widths read as names rather than a pattern.
        bars.addView(name, new LinearLayout.LayoutParams(entry.size(new int[]{132, 104, 150, 92}[index % 4]),
                entry.size(12)));
        View detail = new View(this);
        detail.setBackground(ShareCodeEntryView.surface(entry.size(5), 0x0FFFFFFF, 0, 0));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(entry.size(76), entry.size(9));
        detailParams.topMargin = entry.size(9);
        bars.addView(detail, detailParams);
        return tile;
    }

    private void showStatus(int message, Runnable retry) {
        clearResults();
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER);
        results.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x14FFFFFF);
        circle.setStroke(1, 0x1FFFFFFF);
        badge.setBackground(circle);
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_share_code_offline);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(ShareCodeEntryView.INK_SOFT));
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.addView(glyph, new FrameLayout.LayoutParams(entry.size(28), entry.size(28), Gravity.CENTER));
        status.addView(badge, new LinearLayout.LayoutParams(entry.size(64), entry.size(64)));
        TextView feedback = catalogLabel(16, ShareCodeEntryView.INK_LIGHT, true);
        feedback.setText(message);
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        feedbackParams.topMargin = entry.size(18);
        status.addView(feedback, feedbackParams);
        TextView hint = catalogLabel(12, ShareCodeEntryView.INK_MUTED, false);
        hint.setText(R.string.share_code_load_failed_hint);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = entry.size(7);
        status.addView(hint, hintParams);
        Button button = catalogButton(R.string.share_code_retry, retry);
        LinearLayout.LayoutParams retryLayout = new LinearLayout.LayoutParams(entry.size(120), entry.size(38));
        retryLayout.topMargin = entry.size(22);
        status.addView(button, retryLayout);
        button.requestFocusFromTouch();
    }

    private void showList(List<ShareCodeApkRepository.Apk> apks) {
        if (addPage != null) {
            removeAddPage();
            previousPageFocus = null;
            entry.showSavedLinks(links.list(), false);
        }
        clearResults();
        catalogSubtitle.setText(sourceLabel(ShareCodeApkRepository.repositoryPath(appsUrl), apks.size()));

        GridView grid = new GridView(this);
        grid.setId(View.generateViewId());
        grid.setNextFocusUpId(catalogBack.getId());
        catalogBack.setNextFocusDownId(grid.getId());
        grid.setNumColumns(2);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(entry.size(14));
        grid.setVerticalSpacing(entry.size(12));
        int inset = entry.size(8);
        // The padding holds the selected tile's lift and shadow. The list's frame (results) clips the grid to
        // its own bounds, so rows scrolled past the edge fade out there instead of drawing over the header.
        grid.setPadding(inset, inset, inset, inset);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        results.setClipChildren(true);
        grid.setVerticalScrollBarEnabled(false);
        grid.setVerticalFadingEdgeEnabled(true);
        grid.setFadingEdgeLength(entry.size(28));
        grid.setCacheColorHint(0);
        grid.setSelector(new ColorDrawable());
        CompatApi.disableDefaultFocusHighlight(grid);

        appsAdapter = new ArrayAdapter<ShareCodeApkRepository.Apk>(
                this, android.R.layout.simple_list_item_1, apks) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ApkTile tile = convertView == null ? new ApkTile() : (ApkTile) convertView;
                tile.bind(apks.get(position));
                // A recycled tile may still carry another position's lift or its running animation.
                boolean lifted = position == grid.getSelectedItemPosition() && grid.hasFocus();
                tile.animate().cancel();
                tile.setTag(R.id.share_code_spring_x, null);
                tile.setScaleX(lifted ? TILE_LIFT : 1f);
                tile.setScaleY(lifted ? TILE_LIFT : 1f);
                CompatApi.elevation(tile, lifted ? entry.size(10) : 0f);
                return tile;
            }
        };
        grid.setAdapter(appsAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> selectApk(apks.get(position)));
        // GridView keeps focus itself. Every visible tile is set from the current selection, so a tile the
        // selection left (or one recycled while lifted) always settles back into the grid.
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                liftSelected(grid);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                liftSelected(grid);
            }
        });
        grid.setOnFocusChangeListener((view, focused) -> liftSelected(grid));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_IDLE) liftSelected(grid);
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) { }
        });
        results.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grid.requestFocusFromTouch();
    }

    private void liftSelected(GridView grid) {
        View selected = grid.hasFocus() ? grid.getSelectedView() : null;
        for (int index = 0; index < grid.getChildCount(); index++) {
            View tile = grid.getChildAt(index);
            liftTile(tile, tile == selected);
        }
    }

    private void liftTile(View tile, boolean lifted) {
        float scale = lifted ? TILE_LIFT : 1f;
        // Already at (or heading to) this scale: leave a running animation alone.
        Object target = tile.getTag(R.id.share_code_spring_x);
        if (target instanceof Float && (Float) target == scale) return;
        tile.setTag(R.id.share_code_spring_x, scale);
        CompatApi.elevation(tile, lifted ? entry.size(10) : 0f);
        tile.animate().cancel();
        if (!motion) {
            tile.setScaleX(scale);
            tile.setScaleY(scale);
            return;
        }
        tile.animate().scaleX(scale).scaleY(scale).setDuration(150).setInterpolator(CompatApi.easeOut()).start();
    }

    /** A Control Center tile: app icon, name and version, and the next step as a pill. */
    private final class ApkTile extends LinearLayout {
        private final ImageView appIcon;
        private final TextView name;
        private final TextView detail;
        private final ImageView actionIcon;
        private final TextView actionText;

        ApkTile() {
            super(ShareCodeActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, entry.size(TILE_HEIGHT)));
            setPadding(entry.size(14), 0, entry.size(12), 0);
            int[] selected = {android.R.attr.state_selected};
            int[] idle = {};
            int radius = entry.size(18);
            StateListDrawable tiles = new StateListDrawable();
            tiles.addState(selected, ShareCodeEntryView.surface(radius, ShareCodeEntryView.FOCUS_WHITE, 0, 0));
            tiles.addState(idle, ShareCodeEntryView.surface(radius, 0x12FFFFFF, 1, 0x1AFFFFFF));
            setBackground(tiles);

            appIcon = new ImageView(ShareCodeActivity.this);
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            appIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(appIcon, new LayoutParams(entry.size(TILE_ICON), entry.size(TILE_ICON)));

            LinearLayout texts = new LinearLayout(ShareCodeActivity.this);
            texts.setOrientation(VERTICAL);
            LayoutParams textsParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            textsParams.setMarginStart(entry.size(14));
            textsParams.setMarginEnd(entry.size(10));
            addView(texts, textsParams);
            name = catalogLabel(15, ShareCodeEntryView.INK_LIGHT, true);
            name.setGravity(Gravity.START);
            name.setTextColor(new ColorStateList(new int[][]{selected, idle},
                    new int[]{ShareCodeEntryView.INK_DARK, ShareCodeEntryView.INK_LIGHT}));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            name.setMarqueeRepeatLimit(-1);
            texts.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            detail = catalogLabel(12, ShareCodeEntryView.INK_MUTED, false);
            detail.setGravity(Gravity.START);
            detail.setTextColor(new ColorStateList(new int[][]{selected, idle},
                    new int[]{0x8C121418, ShareCodeEntryView.INK_MUTED}));
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams detailParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = entry.size(6);
            texts.addView(detail, detailParams);

            ColorStateList actionColors = new ColorStateList(new int[][]{selected, idle},
                    new int[]{0xFFFFFFFF, ShareCodeEntryView.INK_LIGHT});
            LinearLayout action = new LinearLayout(ShareCodeActivity.this);
            action.setGravity(Gravity.CENTER);
            action.setPadding(entry.size(10), 0, entry.size(13), 0);
            StateListDrawable pills = new StateListDrawable();
            pills.addState(selected, ShareCodeEntryView.surface(entry.size(15), ShareCodeEntryView.INK_DARK, 0, 0));
            pills.addState(idle, ShareCodeEntryView.surface(entry.size(15), 0x1AFFFFFF, 0, 0));
            action.setBackground(pills);
            addView(action, new LayoutParams(LayoutParams.WRAP_CONTENT, entry.size(30)));
            actionIcon = new ImageView(ShareCodeActivity.this);
            CompatApi.imageTint(actionIcon, actionColors);
            actionIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            action.addView(actionIcon, new LayoutParams(entry.size(15), entry.size(15)));
            actionText = catalogLabel(12, ShareCodeEntryView.INK_LIGHT, true);
            actionText.setTextColor(actionColors);
            LayoutParams actionTextParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            actionTextParams.setMarginStart(entry.size(5));
            action.addView(actionText, actionTextParams);
        }

        void bind(ShareCodeApkRepository.Apk apk) {
            ShareApkNames label = ShareApkNames.parse(apk.fileName);
            name.setText(label.title);
            String size = Formatter.formatShortFileSize(ShareCodeActivity.this, apk.size);
            detail.setText(label.version.isEmpty() ? size : label.version + "  ·  " + size);
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
            int state;
            if (installed != null) {
                actionIcon.setImageResource(R.drawable.ic_share_code_open);
                actionText.setText(R.string.share_code_open);
                state = R.string.share_code_installed;
            } else if (downloaded) {
                actionIcon.setImageResource(R.drawable.ic_share_code_downloaded);
                actionText.setText(R.string.share_code_action_install);
                state = R.string.share_code_ready_to_install;
            } else {
                actionIcon.setImageResource(R.drawable.ic_qs_update);
                actionText.setText(R.string.share_code_action_get);
                state = R.string.share_code_not_downloaded;
            }
            setContentDescription(label.title + " " + label.version + ", " + getString(state));
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
        ShareApkNames label = ShareApkNames.parse(apk.fileName);
        fileName.setText(label.title);
        ImageView appIcon = content.findViewById(R.id.share_download_icon);
        appIcon.setImageResource(placeholderIcon(apk.fileName));
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
