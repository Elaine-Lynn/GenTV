package com.gentv.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.Arrays;

/** Downloads one ABI-specific game plugin into private storage without installation. */
@android.annotation.TargetApi(23)
public final class GameModuleActivity extends Activity {
    private static final long MAX_PLUGIN_BYTES = 180L * 1024 * 1024;
    private TextView status;
    private Button action;
    private ProgressBar progress;
    private boolean busy;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT < 23) { finish(); return; }
        buildUi();
        if (Build.VERSION.SDK_INT < 28) {
            status.setText(R.string.game_runtime_unsupported_android);
            action.setVisibility(View.GONE);
            return;
        }
        action.setOnClickListener(v -> prepare());
        action.requestFocus();
        if (GameRuntimeStore.ready(this)) prepare();
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

    private void prepare() {
        if (busy) return;
        String selectedAbi = GameRuntimeStore.abi();
        if (selectedAbi == null) { showError(R.string.game_module_unsupported_abi); return; }
        boolean hadComponent = GameRuntimeStore.ready(this);
        busy = true;
        action.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText(hadComponent ? R.string.game_module_checking : R.string.game_module_downloading);
        TvCloud.IO.execute(() -> {
            try {
                File plugin = download(selectedAbi);
                if (!hadComponent || !sha256(plugin).equalsIgnoreCase(sha256(GameRuntimeStore.archive(this)))) {
                    verifyArchive(plugin);
                    runOnUiThread(() -> status.setText(R.string.game_module_installing));
                    GameRuntimeStore.stage(this, plugin);
                }
                runOnUiThread(this::launchGame);
            }
            catch (Exception error) { android.util.Log.e("GenTVGameModule", "Module loading failed", error);
                runOnUiThread(() -> {
                    if (hadComponent && GameRuntimeStore.ready(this)) launchGame();
                    else showError(R.string.game_module_failed);
                }); }
        });
    }

    private File download(String selectedAbi) throws Exception {
        String prefix = "https://github.com/Elaine-Lynn/GenTV/releases/download/game-runtime-v"
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
                || !getPackageName().equals(manifest.getString("hostPackageName"))
                || !selectedAbi.equals(manifest.getString("abi"))
                || !UpdatePackage.certificate(this).equalsIgnoreCase(manifest.getString("signingCertificateSha256")))
            throw new IllegalArgumentException("Wrong game module version, ABI, package or signer");
        JSONObject item = manifest.getJSONObject("plugin");
        String name = "game-" + selectedAbi.replace('-', '_') + ".apk";
        String url = item.getString("url");
        String hash = item.getString("sha256");
        long size = item.getLong("sizeBytes");
        if (!name.equals(item.getString("name")) || !(prefix + name).equals(url)
                || !hash.matches("[a-fA-F0-9]{64}") || size <= 0 || size > MAX_PLUGIN_BYTES)
            throw new IllegalArgumentException("Invalid game plugin metadata");
        File directory = new File(getCacheDir(), "game-runtime-download/" + BuildConfig.VERSION_CODE + "/" + selectedAbi);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create module cache");
        File file = new File(directory, name);
        if (!file.isFile() || file.length() != size || !hash.equalsIgnoreCase(sha256(file))) {
            File partial = new File(directory, name + ".part");
            HttpURLConnection connection = TvCloud.openGameModule(url);
            try {
                if (connection.getResponseCode() != 200 || connection.getContentLength() > MAX_PLUGIN_BYTES)
                    throw new java.io.IOException("Game plugin download refused");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long received = 0;
                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        received += count;
                        if (received > size) throw new java.io.IOException("Game plugin too large");
                        digest.update(buffer, 0, count); out.write(buffer, 0, count);
                        int percent = (int) (received * 100 / size);
                        runOnUiThread(() -> progress.setProgress(percent));
                    }
                }
                if (received != size || !hash.equalsIgnoreCase(UpdatePackage.hex(digest.digest())))
                    throw new java.io.IOException("Game plugin checksum mismatch");
                if (file.exists() && !file.delete()) throw new java.io.IOException("Cannot replace cached plugin");
                if (!partial.renameTo(file)) throw new java.io.IOException("Cannot cache game plugin");
            } finally { connection.disconnect(); partial.delete(); }
        }
        return file;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return UpdatePackage.hex(digest.digest());
    }

    @SuppressWarnings("deprecation")
    private void verifyArchive(File file) throws Exception {
        PackageManager manager = getPackageManager();
        PackageInfo host = manager.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null || !"com.gentv.game.plugin".equals(archive.packageName)
                || archive.versionCode != BuildConfig.VERSION_CODE
                || archive.signatures == null || host.signatures == null
                || !Arrays.equals(archive.signatures, host.signatures))
            throw new SecurityException("Game plugin identity or signature mismatch");
    }

    private void launchGame() {
        try {
            startActivity(new Intent(this, GameRuntimeHostActivity.class));
            finish();
        } catch (Exception error) { android.util.Log.e("GenTVGameModule", "Game runtime unavailable", error);
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
