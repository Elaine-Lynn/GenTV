package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Uses system Home selection UI and verifies the actual resolver when it returns. */
public class HomeSettingsActivity extends Activity implements HomeSetupFlow.Platform {
    private static final int REQUEST_HOME = 1;
    private HomeSetupFlow flow;
    private boolean waiting;
    private boolean left;
    private Dialog fallback;
    private Toast chooseHint;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout backdrop = new FrameLayout(this);
        setContentView(backdrop);
        WallpaperBackdrop.apply(this, backdrop);
        flow = new HomeSetupFlow(this, state == null ? 0 : state.getInt("nextRoute"));
        waiting = state != null && state.getBoolean("waiting");
        left = state != null && state.getBoolean("left");
        if (isDefaultHome()) complete(R.string.home_already_default);
        else if (state == null) advance();
        else if (!waiting) showFallback();
    }

    @Override public boolean isDefaultHome() {
        try {
            ResolveInfo resolved = getPackageManager().resolveActivity(homeIntent(),
                    PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                    && getPackageName().equals(resolved.activityInfo.packageName);
        } catch (RuntimeException unavailable) { return false; }
    }

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addCategory(Intent.CATEGORY_DEFAULT);
    }

    @Override public boolean open(HomeSetupFlow.Route route) {
        try {
            Intent intent;
            switch (route) {
                case ROLE:
                    if (Build.VERSION.SDK_INT < 29) return false;
                    intent = homeRoleIntent();
                    if (intent == null) return false;
                    break;
                case HOME_SETTINGS:
                    intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                    break;
                case ANDROID_PICKER:
                    intent = picker("android", "com.android.internal.app.ResolverActivity");
                    if (intent == null) return false;
                    break;
                case HUAWEI_PICKER:
                    intent = picker("com.huawei.android.internal.app",
                            "com.huawei.android.internal.app.HwResolverActivity");
                    if (intent == null) return false;
                    break;
                default: return false;
            }
            waiting = true;
            left = false;
            startActivityForResult(intent, REQUEST_HOME);
            chooseHint = Toast.makeText(this, R.string.home_setup_choose, Toast.LENGTH_LONG);
            chooseHint.show();
            return true;
        } catch (RuntimeException unavailable) {
            waiting = false;
            return false;
        }
    }

    @android.annotation.TargetApi(29)
    private Intent homeRoleIntent() {
        android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
        return roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                ? roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME) : null;
    }

    private Intent picker(String pkg, String name) {
        ComponentName component = new ComponentName(pkg, name);
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(component, 0);
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported) return null;
            if (info.permission != null && checkCallingOrSelfPermission(info.permission)
                    != PackageManager.PERMISSION_GRANTED) return null;
            return homeIntent().setComponent(component);
        } catch (PackageManager.NameNotFoundException unavailable) { return null; }
    }

    private void advance() {
        switch (flow.advance()) {
            case DEFAULT: complete(R.string.home_already_default); break;
            case UNAVAILABLE: showFallback(); break;
            default: break;
        }
    }

    @Override protected void onPause() {
        if (waiting) left = true;
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        // Some TV settings return no useful result code; verify after either return path.
        if (waiting && left) verifyReturn();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_HOME && waiting) verifyReturn();
    }

    private void verifyReturn() {
        if (chooseHint != null) chooseHint.cancel();
        waiting = false;
        left = false;
        if (flow.verified()) complete(R.string.home_set_success);
        else showFallback();
    }

    private void showFallback() {
        if (isFinishing() || isDestroyed() || fallback != null) return;
        boolean more = flow.nextRoute() < HomeSetupFlow.Route.values().length;
        fallback = GlassInfoDialog.create(this, R.drawable.ic_qs_home, R.string.set_default_home,
                getString(more ? R.string.home_setup_not_changed : R.string.home_setup_unavailable),
                more ? R.string.home_setup_try_next : android.R.string.ok,
                () -> { fallback = null; if (more) advance(); else finish(); },
                more ? android.R.string.cancel : 0, this::finish);
        fallback.setOnCancelListener(dialog -> finish());
        fallback.show();
    }

    private void complete(int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("nextRoute", flow.nextRoute());
        state.putBoolean("waiting", waiting);
        state.putBoolean("left", left);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (chooseHint != null) chooseHint.cancel();
        if (fallback != null) fallback.dismiss();
        super.onDestroy();
    }
}
