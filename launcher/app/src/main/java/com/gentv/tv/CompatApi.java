package com.gentv.tv;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.net.URLConnection;
import java.util.List;
import java.util.Locale;

/** Small platform fallbacks shared by the full launcher on Android 4.2. */
final class CompatApi {
    private CompatApi() { }

    static <T> T service(Context context, Class<T> type) {
        String name;
        if (type == ActivityManager.class) name = Context.ACTIVITY_SERVICE;
        else if (type == AudioManager.class) name = Context.AUDIO_SERVICE;
        else if (type == ConnectivityManager.class) name = Context.CONNECTIVITY_SERVICE;
        else if (type == DevicePolicyManager.class) name = Context.DEVICE_POLICY_SERVICE;
        else if (type == InputMethodManager.class) name = Context.INPUT_METHOD_SERVICE;
        else if (type == PowerManager.class) name = Context.POWER_SERVICE;
        else if (type == UserManager.class) name = Context.USER_SERVICE;
        else return null;
        Object result = context.getSystemService(name);
        return type.isInstance(result) ? type.cast(result) : null;
    }

    @SuppressWarnings("deprecation")
    static Drawable drawable(Context context, int id) {
        return context.getResources().getDrawable(id);
    }

    static boolean animatorsEnabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    static long contentLength(URLConnection connection) {
        return Build.VERSION.SDK_INT >= 24 ? connection.getContentLengthLong() : connection.getContentLength();
    }

    @SuppressWarnings("deprecation")
    static boolean interactive(PowerManager power) {
        return Build.VERSION.SDK_INT >= 20 ? power.isInteractive() : power.isScreenOn();
    }

    static boolean userUnlocked(UserManager users) {
        return Build.VERSION.SDK_INT < 24 || users == null || users.isUserUnlocked();
    }

    @SuppressWarnings("deprecation")
    static Locale locale(Configuration configuration) {
        return Build.VERSION.SDK_INT >= 24 ? configuration.getLocales().get(0) : configuration.locale;
    }

    static boolean lowRam(ActivityManager memory) {
        return Build.VERSION.SDK_INT >= 19 ? memory.isLowRamDevice() : memory.getMemoryClass() <= 64;
    }

    static boolean constrainedHeap(ActivityManager memory) {
        return memory != null && HeapBudget.constrained(memory.getMemoryClass(), lowRam(memory));
    }

    @SuppressWarnings("deprecation")
    static ColorStateList colorStateList(Context context, int id) {
        return context.getResources().getColorStateList(id);
    }

    static void compoundTint(TextView view, ColorStateList colors) {
        if (Build.VERSION.SDK_INT >= 23) {
            view.setCompoundDrawableTintList(colors);
            return;
        }
        for (Drawable drawable : view.getCompoundDrawables()) {
            if (drawable == null) continue;
            if (colors == null) drawable.mutate().clearColorFilter();
            else drawable.mutate().setColorFilter(colors.getDefaultColor(), PorterDuff.Mode.SRC_IN);
        }
    }

    static void letterSpacing(TextView view, float spacing) {
        if (Build.VERSION.SDK_INT >= 21) view.setLetterSpacing(spacing);
    }

    static void disableDefaultFocusHighlight(View view) {
        if (Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
    }

    static void forceOverlappingRendering(View view, boolean overlapping) {
        if (Build.VERSION.SDK_INT >= 24) view.forceHasOverlappingRendering(overlapping);
    }

    static boolean laidOut(View view) {
        return Build.VERSION.SDK_INT >= 19 ? view.isLaidOut() : view.getWidth() > 0 && view.getHeight() > 0;
    }

    static ViewPropertyAnimator translationZ(ViewPropertyAnimator animator, float z) {
        if (Build.VERSION.SDK_INT >= 21) animator.translationZ(z);
        return animator;
    }

    static boolean permissionGranted(Context context, String permission) {
        int result = Build.VERSION.SDK_INT >= 23
                ? context.checkSelfPermission(permission) : context.checkCallingOrSelfPermission(permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    static boolean waitFor(Process process, long timeoutMs) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException running) {
                Thread.sleep(25);
            }
        }
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException running) {
            return false;
        }
    }

    static Intent leanbackLaunch(PackageManager manager, String packageName) {
        if (Build.VERSION.SDK_INT >= 21) return manager.getLeanbackLaunchIntentForPackage(packageName);
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER");
        query.setPackage(packageName);
        List<ResolveInfo> matches = manager.queryIntentActivities(query, 0);
        if (matches.isEmpty()) return manager.getLaunchIntentForPackage(packageName);
        ResolveInfo first = matches.get(0);
        if (first.activityInfo == null) return null;
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(first.activityInfo.packageName, first.activityInfo.name));
    }
}
