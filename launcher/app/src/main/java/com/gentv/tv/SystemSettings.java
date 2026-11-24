package com.gentv.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 *  from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS). */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        Intent glowBox = glowBoxIntent(context, action);
        if (glowBox != null) return glowBox;
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
        // treat it as missing so the vendor fallback runs.
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;
        Intent vendor = vendorIntent(context);
        return vendor != null ? vendor : standard;
    }

    /** The first vendor settings entry from settings_fallback_components that is installed, or null
     *  when none is. China-market firmware keeps pages the AOSP TV settings has no row for -
     *  Xiaomi's Account & Security, where the ADB switch lives - in the vendor's own settings app;
     *  MainActivity's Settings menu offers it as "Native settings" while one is installed. */
    static Intent vendorIntent(Context context) {
        Intent glowBox = glowBoxIntent(context, Settings.ACTION_SETTINGS);
        if (glowBox != null) return glowBox;
        PackageManager pm = context.getPackageManager();
        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return null;
    }

    /** XGH-A01 exposes its own settings panel instead of the standard Android settings actions. */
    private static Intent glowBoxIntent(Context context, String action) {
        if (!"GlowBox".equalsIgnoreCase(Build.BRAND) || !"XGH-A01".equalsIgnoreCase(Build.MODEL)) return null;
        ComponentName component = new ComponentName("com.dangbei.haqusetting",
                "com.dangbei.haqusetting.ui.HomeActivity");
        try {
            context.getPackageManager().getActivityInfo(component, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        if (Settings.ACTION_SETTINGS.equals(action)) return new Intent().setComponent(component);
        if (Settings.ACTION_WIFI_SETTINGS.equals(action))
            return XghSettingsRedirectActivity.intent(context, "side_function_tag_pre_wifi");
        if (Settings.ACTION_DISPLAY_SETTINGS.equals(action))
            return XghSettingsRedirectActivity.intent(context, "side_function_tag_pre_image");
        if (XghSettingsRedirectActivity.ACTION_INPUT_SOURCE.equals(action))
            return XghSettingsRedirectActivity.intent(context, "side_function_tag_pre_hdmi");
        return null;
    }

    static Intent inputSourceIntent(Context context) {
        return glowBoxIntent(context, XghSettingsRedirectActivity.ACTION_INPUT_SOURCE);
    }

    static Intent glowBoxNetworkIntent(Context context) {
        return glowBoxIntent(context, Settings.ACTION_WIFI_SETTINGS);
    }

    /** The TV's App info page for pkg, or null where the firmware has none (CVTE's settings app, #1202). */
    static Intent appInfo(Context context, String pkg) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        return context.getPackageManager().resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) != null ? info : null;
    }
}
