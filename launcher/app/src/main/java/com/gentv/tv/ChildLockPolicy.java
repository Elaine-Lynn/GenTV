package com.gentv.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.List;

/** Applies the extra package and task restrictions when this installation owns the device. */
final class ChildLockPolicy {
    static final String TARGET = "managed_target";
    private static final String APPLIED_TARGET = "managed_suspension";
    private static final String TAG = "GenTVChildLock";
    private final Activity home;
    private final SharedPreferences state;
    private final DevicePolicyManager policies;

    ChildLockPolicy(Activity home, SharedPreferences state) {
        this.home = home;
        this.state = state;
        policies = home.getSystemService(DevicePolicyManager.class);
    }

    void apply(boolean locked) {
        ComponentName admin = ownerAdmin();
        if (admin == null) return;
        try {
            if (locked) enforce(admin);
            else release(admin);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            Log.w(TAG, "Managed policy could not be applied", e);
        }
    }

    private ComponentName ownerAdmin() {
        if (policies == null || !policies.isDeviceOwnerApp(home.getPackageName())) return null;
        List<ComponentName> admins = policies.getActiveAdmins();
        if (admins != null) {
            for (ComponentName admin : admins) {
                if (home.getPackageName().equals(admin.getPackageName())) return admin;
            }
        }
        return null;
    }

    private void enforce(ComponentName admin) {
        String target = state.getString(TARGET, "");
        if (target.isEmpty() || target.equals(home.getPackageName())) return;
        suspend(admin, target);
        policies.setLockTaskPackages(admin, new String[]{home.getPackageName()});
        if (Build.VERSION.SDK_INT >= 28) {
            policies.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        }
        if (!inLockTask()) home.startLockTask();
        Log.i(TAG, "managed locked=" + inLockTask() + " suspended=" + suspended(admin, target));
    }

    private void suspend(ComponentName admin, String target) {
        if (suspended(admin, target)) return;
        // Record ownership first so a process death between the two operations remains recoverable.
        if (!state.edit().putString(APPLIED_TARGET, target).commit()) {
            throw new IllegalStateException("Could not save managed suspension");
        }
        if (policies.setPackagesSuspended(admin, new String[]{target}, true).length != 0) {
            state.edit().remove(APPLIED_TARGET).commit();
            Log.w(TAG, "Managed target refused suspension");
        }
    }

    private void release(ComponentName admin) {
        String target = state.getString(APPLIED_TARGET, "");
        if (!target.isEmpty() &&
                policies.setPackagesSuspended(admin, new String[]{target}, false).length == 0) {
            state.edit().remove(APPLIED_TARGET).commit();
        }
        if (inLockTask()) home.stopLockTask();
    }

    private boolean suspended(ComponentName admin, String target) {
        try {
            return policies.isPackageSuspended(admin, target);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean inLockTask() {
        return home.getSystemService(ActivityManager.class).getLockTaskModeState()
                == ActivityManager.LOCK_TASK_MODE_LOCKED;
    }
}
