package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
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

    /** Shared startup-app picker for Launcher settings and the quick panel. TV and phone launchable apps are listed;
     *  only confirmation saves, and the pick takes effect from the next boot. */
    static void showPicker(Activity activity, Runnable onSaved) {
        PackageManager pm = activity.getPackageManager();
        Map<String, String> packages = new TreeMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo entry : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = entry.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName()) || packages.containsKey(pkg)) continue;
                if (!entry.activityInfo.enabled || !entry.activityInfo.applicationInfo.enabled) continue;
                packages.put(pkg, entry.loadLabel(pm).toString());
            }
        }
        final List<String> apps = new ArrayList<>(packages.keySet());
        final Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(packages.get(a), packages.get(b)));
        String current = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, "");
        String[] labels = new String[apps.size() + 1];
        labels[0] = activity.getString(R.string.off);
        int selected = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = packages.get(apps.get(i));
            if (apps.get(i).equals(current)) selected = i + 1;
        }
        final int[] pending = {selected};
        new AlertDialog.Builder(activity)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> pending[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int pick = pending[0];
                    if (!save(activity, pick == 0 ? "" : apps.get(pick - 1))) {
                        Toast.makeText(activity, R.string.open_on_start_save_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(activity, pick == 0 ? activity.getString(R.string.open_on_start_removed_generic)
                            : activity.getString(R.string.open_on_start_saved, labels[pick]), Toast.LENGTH_SHORT).show();
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
