package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The one app Home opens on its first appearance after each boot. */
final class OpenOnStart {
    /** The picked package in Config.SETTINGS_NAME; empty means Off, absent permits theme defaults. */
    static final String KEY = "open_on_start";
    /** The boot whose first Home has been handled, shared with boot_to_live_tv. */
    static final String BOOT_PREFS = "boot";
    static final String BOOT_KEY = "live_tv_boot";

    private OpenOnStart() {
    }

    /** Shows the startup-app sheet. TV and phone launchable apps are listed; OK on one saves it, and the pick takes
     *  effect from the next boot. */
    static void showPicker(Activity activity) {
        OpenOnStartDialog.show(activity);
    }

    /** Every enabled TV or phone launchable app but GenTV, package to label, sorted by label. */
    static Map<String, String> launchableApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> packages = new TreeMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo entry : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = entry.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || packages.containsKey(pkg)) continue;
                if (!entry.activityInfo.enabled || !entry.activityInfo.applicationInfo.enabled) continue;
                packages.put(pkg, entry.loadLabel(pm).toString());
            }
        }
        List<String> apps = new ArrayList<>(packages.keySet());
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(packages.get(a), packages.get(b)));
        Map<String, String> sorted = new LinkedHashMap<>();
        for (String pkg : apps) sorted.put(pkg, packages.get(pkg));
        return sorted;
    }

    /** Whether the first Home of this boot opens {@code pkg}: a known boot not handled yet, and an app that is not
     *  the launcher itself (which would only reopen Home). */
    static boolean due(int boot, int handledBoot, String pkg, String self) {
        return boot >= 0 && boot != handledBoot && pkg != null && !pkg.isEmpty() && !pkg.equals(self);
    }

    static int boot(Context ctx) {
        int count = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (count >= 0) return count;
        // BOOT_COUNT is absent on older Android versions. Linux's per-boot ID also changes after a reboot.
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String id = reader.readLine();
            return id == null || id.isEmpty() ? -1 : id.hashCode() & Integer.MAX_VALUE;
        } catch (IOException | SecurityException ignored) {
            return -1;
        }
    }

    /** A pick takes effect from the next boot: the Home that follows the pick stays Home. */
    static boolean save(Context ctx, String pkg) {
        if (pkg == null) pkg = "";
        int currentBoot = boot(ctx);
        if (currentBoot >= 0 && !ctx.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                .edit().putInt(BOOT_KEY, currentBoot).commit()) return false;
        SharedPreferences.Editor choice = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit();
        choice.putString(KEY, pkg);
        return choice.commit();
    }

    /** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
    static Intent intent(PackageManager pm, String pkg) {
        Intent i = CompatApi.leanbackLaunch(pm, pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
