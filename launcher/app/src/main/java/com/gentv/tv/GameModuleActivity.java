package com.gentv.tv;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Installs the signed, ABI-specific game splits into this existing package. */
@android.annotation.TargetApi(23)
public final class GameModuleActivity extends Activity {
    private static final String RESULT_ACTION = "com.gentv.tv.GAME_MODULE_INSTALL_RESULT";
    private static final long MAX_SPLIT_BYTES = 100L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 140L * 1024 * 1024;
    private TextView status;
    private Button action;
    private ProgressBar progress;
    private boolean busy;

    private static final class Split {
        final String name;
        final File file;
        final long size;
        Split(String name, File file, long size) { this.name = name; this.file = file; this.size = size; }
    }

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT < 23) { finish(); return; }
        buildUi();
        if (installed()) { launchGame(); return; }
        if (RESULT_ACTION.equals(getIntent().getAction())) { handleResult(getIntent()); return; }
        action.setOnClickListener(v -> prepare());
        action.requestFocus();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (RESULT_ACTION.equals(intent.getAction())) handleResult(intent);
    }

    private void buildUi() {
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(0xff09111f);
        setContentView(backdrop);
        WallpaperBackdrop.applyOverlay(this, backdrop);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(32), dp(28), dp(32), dp(30));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xe7475364, 0xeb1b2839});
        glass.setCornerRadius(dp(27));
        glass.setStroke(dp(1), 0x80ffffff);
        card.setBackground(glass);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(18));
        int width = Math.min(dp(650), getResources().getDisplayMetrics().widthPixels - dp(48));
        backdrop.addView(card, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER));
        TextView brand = new TextView(this);
        brand.setText("GenTV");
        brand.setTextColor(0xff95d9f4);
        brand.setTextSize(16);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(brand);
        TextView title = new TextView(this);
        title.setText(R.string.game_kart);
        title.setTextColor(Color.WHITE);
        title.setTextSize(31);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams heading = new LinearLayout.LayoutParams(-1, -2);
        heading.topMargin = dp(16);
        card.addView(title, heading);
        status = new TextView(this);
        status.setText(R.string.game_module_ready_to_download);
        status.setTextColor(0xffd8e0ed);
        status.setTextSize(17);
        status.setMinHeight(dp(60));
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(-1, -2);
        label.topMargin = dp(12);
        card.addView(status, label);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xff8bddf5));
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(7));
        bar.topMargin = dp(16);
        card.addView(progress, bar);
        action = new Button(this);
        action.setText(R.string.game_module_download);
        action.setAllCaps(false);
        action.setTextSize(18);
        action.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xff111923, Color.WHITE}));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x66445a72);
        normal.setCornerRadius(dp(16));
        normal.setStroke(dp(1), 0x88ffffff);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xfff2f6fa);
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(2), Color.WHITE);
        StateListDrawable buttonBg = new StateListDrawable();
        buttonBg.addState(new int[]{android.R.attr.state_focused}, focused);
        buttonBg.addState(new int[]{}, normal);
        action.setBackground(buttonBg);
        action.setOnFocusChangeListener((view, hasFocus) -> view.animate()
                .scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(140).start());
        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        LinearLayout.LayoutParams buttonRow = new LinearLayout.LayoutParams(-1, dp(58));
        buttonRow.topMargin = dp(25);
        card.addView(buttons, buttonRow);
        Button later = new Button(this);
        later.setText(R.string.game_module_back);
        later.setAllCaps(false);
        later.setTextSize(16);
        later.setTextColor(action.getTextColors());
        later.setBackground(buttonBg.getConstantState().newDrawable());
        later.setOnFocusChangeListener((view, hasFocus) -> view.animate()
                .scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(140).start());
        later.setOnClickListener(v -> finish());
        buttons.addView(later, new LinearLayout.LayoutParams(dp(150), -1));
        LinearLayout.LayoutParams primary = new LinearLayout.LayoutParams(0, -1, 1);
        primary.leftMargin = dp(12);
        buttons.addView(action, primary);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String abi() {
        for (String candidate : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(candidate) || "armeabi-v7a".equals(candidate)) return candidate;
        }
        return null;
    }

    private boolean installed() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.splitNames == null) return false;
            String selectedAbi = abi();
            boolean master = false, nativeCode = false;
            for (String name : info.splitNames) {
                if ("game".equals(name)) master = true;
                if (selectedAbi != null && ("game.config." + selectedAbi.replace('-', '_')).equals(name)) nativeCode = true;
            }
            return master && nativeCode;
        } catch (Exception ignored) { }
        return false;
    }

    private void prepare() {
        if (busy) return;
        String selectedAbi = abi();
        if (selectedAbi == null) { showError(R.string.game_module_unsupported_abi); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + getPackageName())));
            status.setText(R.string.game_module_permission);
            return;
        }
        busy = true;
        action.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.game_module_downloading);
        TvCloud.IO.execute(() -> {
            try { install(download(selectedAbi)); }
            catch (Exception error) { android.util.Log.e("GenTVGameModule", "Module install failed", error);
                runOnUiThread(() -> showError(R.string.game_module_failed)); }
        });
    }

    private List<Split> download(String selectedAbi) throws Exception {
        String prefix = "https://github.com/Elaine-Lynn/GenTV/releases/download/game-modules-v"
                + BuildConfig.VERSION_NAME + "/";
        String manifestUrl = BuildConfig.GAME_MANIFEST_BASE_URL + BuildConfig.VERSION_CODE + "/"
                + selectedAbi + "/manifest.json";
        HttpURLConnection manifestConnection = TvCloud.external(manifestUrl);
        JSONObject manifest;
        try {
            if (manifestConnection.getResponseCode() != 200)
                throw new java.io.IOException("Game module manifest unavailable");
            manifest = new JSONObject(new String(TvCloud.read(manifestConnection.getInputStream(), 65536),
                    java.nio.charset.StandardCharsets.UTF_8));
        } finally { manifestConnection.disconnect(); }
        if (manifest.getInt("versionCode") != BuildConfig.VERSION_CODE
                || !BuildConfig.VERSION_NAME.equals(manifest.getString("versionName"))
                || !getPackageName().equals(manifest.getString("packageName"))
                || !selectedAbi.equals(manifest.getString("abi"))
                || !UpdatePackage.certificate(this).equalsIgnoreCase(manifest.getString("signingCertificateSha256")))
            throw new IllegalArgumentException("Wrong game module version, ABI, package or signer");
        JSONArray files = manifest.getJSONArray("splits");
        if (files.length() != 2) throw new IllegalArgumentException("Expected two game splits");
        String[] names = {"game-master.apk", "game-" + selectedAbi.replace('-', '_') + ".apk"};
        List<Split> ready = new ArrayList<>();
        long expectedTotal = 0;
        for (int i = 0; i < 2; i++) {
            JSONObject item = files.getJSONObject(i);
            String name = item.getString("name");
            long size = item.getLong("sizeBytes");
            if (!names[i].equals(name) || !(prefix + name).equals(item.getString("url"))
                    || !item.getString("sha256").matches("[a-fA-F0-9]{64}")
                    || size <= 0 || size > MAX_SPLIT_BYTES || (expectedTotal += size) > MAX_TOTAL_BYTES)
                throw new IllegalArgumentException("Invalid game split metadata");
        }
        final long totalSize = expectedTotal;
        File directory = new File(getCacheDir(), "game-modules/" + BuildConfig.VERSION_CODE + "/" + selectedAbi);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create module cache");
        for (int i = 0; i < 2; i++) {
            JSONObject item = files.getJSONObject(i);
            String name = item.getString("name");
            String url = item.getString("url");
            String hash = item.getString("sha256");
            long size = item.getLong("sizeBytes");
            File file = new File(directory, name);
            if (!file.isFile() || file.length() != size || !hash.equalsIgnoreCase(sha256(file))) {
                File partial = new File(directory, name + ".part");
                HttpURLConnection connection = TvCloud.openGameModule(url);
                try {
                    if (connection.getResponseCode() != 200 || connection.getContentLength() > MAX_SPLIT_BYTES)
                        throw new java.io.IOException("Game split download refused");
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long received = 0;
                    try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(partial)) {
                        byte[] buffer = new byte[32768]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            received += count;
                            if (received > size) throw new java.io.IOException("Game split too large");
                            digest.update(buffer, 0, count); out.write(buffer, 0, count);
                            int percent = (int) ((readyBytes(ready) + received) * 100 / totalSize);
                            runOnUiThread(() -> progress.setProgress(percent));
                        }
                    }
                    if (received != size || !hash.equalsIgnoreCase(UpdatePackage.hex(digest.digest())))
                        throw new java.io.IOException("Game split checksum mismatch");
                    if (file.exists() && !file.delete()) throw new java.io.IOException("Cannot replace cached split");
                    if (!partial.renameTo(file)) throw new java.io.IOException("Cannot cache game split");
                } finally { connection.disconnect(); partial.delete(); }
            }
            ready.add(new Split(name, file, size));
        }
        return ready;
    }

    private static long readyBytes(List<Split> splits) {
        long total = 0; for (Split split : splits) total += split.size; return total;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return UpdatePackage.hex(digest.digest());
    }

    private void install(List<Split> splits) throws Exception {
        runOnUiThread(() -> status.setText(R.string.game_module_installing));
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING);
        params.setAppPackageName(getPackageName());
        params.setSize(readyBytes(splits));
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            for (Split split : splits) {
                try (InputStream in = new java.io.FileInputStream(split.file);
                     OutputStream out = session.openWrite(split.name, 0, split.size)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    session.fsync(out);
                }
            }
            Intent result = new Intent(this, GameModuleActivity.class).setAction(RESULT_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            session.commit(PendingIntent.getActivity(this, id, result, flags).getIntentSender());
        } catch (Exception error) { installer.abandonSession(id); throw error; }
    }

    private void handleResult(Intent intent) {
        int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (result == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
            else showError(R.string.game_module_failed);
        } else if (result == PackageInstaller.STATUS_SUCCESS || installed()) {
            launchGame();
        } else {
            android.util.Log.e("GenTVGameModule", "PackageInstaller status " + result + ": "
                    + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            showError(R.string.game_module_failed);
        }
    }

    private void launchGame() {
        try {
            startActivity(new Intent().setClassName(getPackageName(), "com.gentv.game.KartGameActivity"));
            finish();
        } catch (Exception error) { android.util.Log.e("GenTVGameModule", "Game split unavailable", error);
            showError(R.string.game_module_failed); }
    }

    private void showError(int text) {
        busy = false;
        status.setText(text);
        progress.setVisibility(View.GONE);
        action.setEnabled(true);
        action.setText(R.string.game_module_retry);
        action.setOnClickListener(v -> prepare());
        action.requestFocus();
    }
}
