package com.gentv.tv;

import android.app.Activity;
import android.view.*;
import android.graphics.drawable.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.*;
import android.content.*;
import android.content.pm.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import org.json.JSONObject;

/**
 * Software update, in the Control Center's glass: one status mark, a headline and a line of detail,
 * and the one or two actions that state allows. Opening it checks for an update straight away.
 */
public final class CloudSettingsActivity extends Activity {
    private static final int BLUE = 0xFF69B9F0;
    private static final int GREEN = 0xFF4CD98B;
    private static final int AMBER = 0xFFF5B85A;
    /** Checks and downloads the person is waiting on; never queued behind Home's background work on TvCloud.IO. */
    private static final java.util.concurrent.ExecutorService WORK = java.util.concurrent.Executors.newSingleThreadExecutor();
    private UpdateBadge badge;
    private TextView headline;
    private TextView detail;
    private TextView newVersion;
    private Button primary;
    private Button later;
    private boolean mandatory;
    private boolean motion;
    private File verified;

    private void mandatory(boolean required) {
        mandatory = required;
        later.setVisibility(required ? View.GONE : View.VISIBLE);
    }

    @Override public void onBackPressed() {
        if (mandatory && !TvCloud.required(this)) mandatory(false);
        if (mandatory) Toast.makeText(this, R.string.cloud_required, Toast.LENGTH_SHORT).show();
        else super.onBackPressed();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(float sp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTypeface(UiFont.get(this, medium ? R.string.font_medium : R.string.font_regular));
        return view;
    }

    private TextView pill(int fill, int rim, int ink) {
        TextView view = text(12, ink, true);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(ShareCodeEntryView.surface(dp(13), fill, 1, rim));
        return view;
    }

    private Button action(boolean emphasized) {
        Button button = ShareCodeEntryView.button(this, UiFont.get(this, R.string.font_medium), "", 14.5f, dp(22), () -> { });
        if (emphasized) {
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, ShareCodeEntryView.surface(dp(22), ShareCodeEntryView.FOCUS_WHITE, 0, 0));
            states.addState(new int[]{android.R.attr.state_pressed}, ShareCodeEntryView.surface(dp(22), 0xDDFFFFFF, 0, 0));
            states.addState(new int[]{}, ShareCodeEntryView.surface(dp(22), 0x3369B9F0, 1, 0x5569B9F0));
            button.setBackground(states);
        }
        button.setOnFocusChangeListener((view, focused) -> {
            CompatApi.elevation(view, focused ? dp(8) : 0f);
            float scale = focused ? 1.04f : 1f;
            if (motion) view.animate().scaleX(scale).scaleY(scale).setDuration(150).setInterpolator(CompatApi.easeOut()).start();
            else { view.setScaleX(scale); view.setScaleY(scale); }
        });
        return button;
    }

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(LauncherLocale.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Config appearance = Config.load(this);
        motion = appearance.animations && !appearance.reducedMotion;
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(0x7005070C);
        backdrop.setOnClickListener(v -> { if (!mandatory) finish(); });
        backdrop.setFocusable(false);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.setPadding(dp(40), dp(36), dp(40), dp(30));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        sheet.setBackground(QuickSettingsGlass.dialogSurface(dp(30), Math.max(1, dp(1))));
        CompatApi.elevation(sheet, dp(18));
        sheet.setOnClickListener(v -> { });
        sheet.setFocusable(false);
        int width = Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(48));
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setClipChildren(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.addView(sheet);
        backdrop.addView(scroll, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        badge = new UpdateBadge(this);
        sheet.addView(badge, new LinearLayout.LayoutParams(dp(92), dp(92)));
        headline = text(22, ShareCodeEntryView.INK_LIGHT, true);
        CompatApi.letterSpacing(headline, -0.01f);
        sheet.addView(headline, spaced(-1, 22));
        detail = text(13.5f, ShareCodeEntryView.INK_MUTED, false);
        detail.setLineSpacing(dp(4), 1f);
        sheet.addView(detail, spaced(-1, 10));

        LinearLayout versions = new LinearLayout(this);
        versions.setGravity(Gravity.CENTER);
        sheet.addView(versions, spaced(-2, 20));
        TextView current = pill(0x14FFFFFF, 0x1FFFFFFF, ShareCodeEntryView.INK_SOFT);
        current.setText(getString(R.string.cloud_current_version, BuildConfig.VERSION_NAME));
        versions.addView(current);
        newVersion = pill(0x2669B9F0, 0x4069B9F0, BLUE);
        LinearLayout.LayoutParams newParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        newParams.setMarginStart(dp(8));
        versions.addView(newVersion, newParams);
        newVersion.setVisibility(View.GONE);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        sheet.addView(actions, spaced(-1, 28));
        later = action(false);
        later.setText(R.string.cloud_later);
        later.setOnClickListener(v -> finish());
        actions.addView(later, new LinearLayout.LayoutParams(dp(136), dp(44)));
        primary = action(true);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(dp(176), dp(44));
        primaryParams.setMarginStart(dp(12));
        actions.addView(primary, primaryParams);
        later.setNextFocusRightId(primary.getId());
        primary.setNextFocusLeftId(later.getId());

        setContentView(backdrop);
        WallpaperBackdrop.applyOverlay(this, findViewById(android.R.id.content));
        mandatory(TvCloud.required(this));
        if (motion && state == null) {
            sheet.setAlpha(0f);
            sheet.setTranslationY(dp(10));
            sheet.setScaleX(.97f);
            sheet.setScaleY(.97f);
            sheet.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(240)
                    .setInterpolator(CompatApi.easeOut()).start();
        }
        if (!TvCloud.enabled()) {
            settle(R.drawable.ic_qs_info, ShareCodeEntryView.INK_SOFT, getString(R.string.update_unconfigured_title),
                    getString(R.string.cloud_unconfigured));
            offer(R.string.update_done, this::finish);
        } else if (TvCloud.prefs(this).getLong("pendingVersion", 0) > BuildConfig.VERSION_CODE) {
            settle(R.drawable.ic_qs_update, BLUE, getString(R.string.update_ready_title), getString(R.string.update_ready_detail));
            offer(R.string.update_install_now, this::install);
        } else {
            // Opening the page is the request: check straight away.
            check();
        }
    }

    private LinearLayout.LayoutParams spaced(int width, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width == -1 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    private void settle(int glyph, int accent, String title, String body) {
        if (isFinishing() || isDestroyed()) return;
        badge.show(glyph, accent);
        headline.setText(title);
        detail.setText(body);
    }

    private void working(String title, String body) {
        badge.busy(R.drawable.ic_qs_update, BLUE);
        headline.setText(title);
        detail.setText(body);
        primary.setVisibility(View.GONE);
        if (later.getVisibility() == View.VISIBLE) later.requestFocus();
    }

    /** The state's main action; "later" stays beside it unless the update is required. */
    private void offer(int label, Runnable run) {
        if (isFinishing() || isDestroyed()) return;
        primary.setText(label);
        primary.setOnClickListener(v -> run.run());
        primary.setVisibility(View.VISIBLE);
        primary.requestFocus();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused || primary == null || primary.hasFocus() || later.hasFocus()) return;
        if (primary.getVisibility() == View.VISIBLE) primary.requestFocus();
        else if (later.getVisibility() == View.VISIBLE) later.requestFocus();
    }

    private void failed(boolean downloading, Runnable retry) {
        if (isFinishing() || isDestroyed()) return;
        settle(R.drawable.ic_update_alert, AMBER,
                getString(downloading ? R.string.update_download_failed_title : R.string.update_check_failed_title),
                getString(R.string.cloud_failed));
        offer(R.string.update_retry, retry);
    }

    private void check() {
        newVersion.setVisibility(View.GONE);
        working(getString(R.string.update_checking_title), getString(R.string.update_checking_detail));
        WORK.execute(() -> {
            try {
                JSONObject update = TvCloud.updateManifest(this);
                TvCloud.remember(this,update);
                long version = update.getLong("versionCode");
                boolean required=UpdatePolicy.required(BuildConfig.VERSION_CODE,version,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));
                if(version <= BuildConfig.VERSION_CODE) {
                    TvCloud.prefs(this).edit().putLong("requiredVersion",0).apply();
                    runOnUiThread(()->{
                        if(isFinishing() || isDestroyed()) return;
                        mandatory(false);
                        settle(R.drawable.ic_update_latest, GREEN, getString(R.string.update_latest_title),
                                getString(R.string.update_latest_detail, BuildConfig.VERSION_NAME));
                        later.setVisibility(View.GONE);
                        offer(R.string.update_done, this::finish);
                    });
                    return;
                }
                String url = update.getString("url"), hash = update.getString("sha256");
                if(!TvCloud.allowedUpdateUrl(url) || !hash.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid update");
                String name = update.optString("versionName", Long.toString(version));
                runOnUiThread(()->{
                    if(isFinishing() || isDestroyed()) return;
                    TvCloud.prefs(this).edit().putLong("requiredVersion",required?version:0).apply();
                    mandatory(required);
                    settle(R.drawable.ic_qs_update, BLUE, getString(R.string.update_available_title, name),
                            (required ? getString(R.string.cloud_required) + "\n" : "") + getString(R.string.update_available_detail));
                    newVersion.setText(getString(R.string.update_new_version, name));
                    newVersion.setVisibility(View.VISIBLE);
                    offer(R.string.cloud_upgrade_now, () -> download(url, hash, version, name));
                });
            } catch(Exception e) {
                android.util.Log.w("GenTV.Update", "Update check failed", e);
                runOnUiThread(()->{
                    if(isFinishing() || isDestroyed()) return;
                    mandatory(TvCloud.required(this));
                    failed(false, this::check);
                });
            }
        });
    }
    private void download(String url,String hash,long version,String name) { download(url, hash, version, name, true); }
    private void download(String url,String hash,long version,String name,boolean tryBackup) {
        working(getString(R.string.update_downloading_title, name), getString(R.string.update_downloading_detail, 0));
        WORK.execute(()->{
            File file = new File(getCacheDir(),"updates/gentv.apk.part");
            HttpURLConnection c = null;
            try {
                file.getParentFile().mkdirs(); c = TvCloud.openUpdate(url);
                if(c.getResponseCode()!=200) throw new IOException("Download refused");
                long length=c.getContentLength();
                if(length>UpdatePackage.MAX_APK_BYTES) throw new IOException("APK too large");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(file)) {
                    byte[] buf=new byte[8192]; int n; long total=0;
                    while((n=in.read(buf))!=-1) { total+=n; if(total>UpdatePackage.MAX_APK_BYTES) throw new IOException("APK too large"); out.write(buf,0,n); digest.update(buf,0,n);
                        if(length>0) { final int percent=(int)(total*100/length); runOnUiThread(()->showProgress(percent)); } }
                }
                runOnUiThread(()->{ if(!isFinishing()&&!isDestroyed()) working(getString(R.string.update_downloading_title, name), getString(R.string.update_verifying)); });
                StringBuilder actual=new StringBuilder(); for(byte b:digest.digest()) actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!hash.equalsIgnoreCase(actual.toString())) throw new IOException("Checksum mismatch");
                PackageManager pm=getPackageManager();
                PackageInfo apk=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES);
                PackageInfo own=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES);
                if(apk==null || !getPackageName().equals(apk.packageName) || apk.versionCode!=version || apk.versionCode<=BuildConfig.VERSION_CODE
                        || apk.signatures==null || !java.util.Arrays.equals(own.signatures,apk.signatures)) throw new IOException("Package verification failed");
                File ready=new File(getCacheDir(),"updates/gentv.apk");
                if(!file.renameTo(ready)) throw new IOException("Cannot commit update");
                TvCloud.prefs(this).edit().putString("pendingHash",hash).putLong("pendingVersion",version).commit();
                verified=UpdatePackage.verified(this); runOnUiThread(()->{ if(!isFinishing()&&!isDestroyed()) install(); });
            } catch(Exception e) {
                android.util.Log.w("GenTV.Update", "Update download failed", e); file.delete();
                if (tryBackup && url.startsWith(TvCloud.BASE + "releases/")) {
                    try {
                        JSONObject backup = TvCloud.backupUpdateManifest(this);
                        if (backup.getLong("versionCode") != version || !hash.equalsIgnoreCase(backup.getString("sha256")))
                            throw new IOException("Backup does not match selected release");
                        String alternate = backup.getString("url");
                        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) download(alternate, hash, version, name, false); });
                        return;
                    } catch (Exception backupError) { android.util.Log.w("GenTV.Update", "Backup download unavailable", backupError); }
                }
                runOnUiThread(()->failed(true, () -> download(url, hash, version, name, true))); }
            finally { if(c!=null)c.disconnect(); }
        });
    }
    private void showProgress(int percent) {
        if (isFinishing() || isDestroyed()) return;
        badge.progress(R.drawable.ic_qs_update, BLUE, percent / 100f);
        detail.setText(getString(R.string.update_downloading_detail, percent));
    }

    private void install() {
        try {
            verified=UpdatePackage.verified(this);
            if(android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                settle(R.drawable.ic_qs_update, BLUE, getString(R.string.update_permission_title), getString(R.string.cloud_install_permission));
                offer(R.string.update_install_now, this::install);
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName())));
                return;
            }
            android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",verified);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            settle(R.drawable.ic_qs_update, BLUE, getString(R.string.update_ready_title), getString(R.string.cloud_install_confirm));
            offer(R.string.update_install_now, this::install);
        } catch(Exception e) {
            TvCloud.prefs(this).edit().remove("pendingHash").remove("pendingVersion").apply();
            failed(true, this::check);
        }
    }
}
