package com.gentv.tv;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;

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

    static void imageTint(ImageView view, ColorStateList colors) {
        if (Build.VERSION.SDK_INT >= 21) {
            view.setImageTintList(colors);
            return;
        }
        if (colors == null) view.clearColorFilter();
        else view.setColorFilter(colors.getDefaultColor(), PorterDuff.Mode.SRC_IN);
    }

    static void elevation(View view, float elevation) {
        if (Build.VERSION.SDK_INT >= 21) view.setElevation(elevation);
    }

    /** Clips to the background's outline where the platform can (API 21+); older TVs draw unclipped. */
    static void clipToOutline(View view) {
        if (Build.VERSION.SDK_INT >= 21) view.setClipToOutline(true);
    }

    /** Clips to a rounded rectangle of radius px where the platform can (API 21+). */
    static void roundClip(View view, float radius) {
        if (Build.VERSION.SDK_INT < 21) return;
        view.setOutlineProvider(new RoundOutline(radius));
        view.setClipToOutline(true);
    }

    @android.annotation.TargetApi(21)
    private static final class RoundOutline extends ViewOutlineProvider {
        private final float radius;

        RoundOutline(float radius) { this.radius = radius; }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }

    /** The shared ease-out curve (0.22, 1, 0.36, 1); a plain deceleration before API 21. */
    static TimeInterpolator easeOut() {
        return Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.22f, 1f, 0.36f, 1f) : new DecelerateInterpolator(2f);
    }

    static String languageTag(Locale locale) {
        if (locale == null) return "";
        if (Build.VERSION.SDK_INT >= 21) return locale.toLanguageTag();
        String country = locale.getCountry();
        return country.isEmpty() ? locale.getLanguage() : locale.getLanguage() + "-" + country;
    }

    /** The sender's UID, or -1 where Android cannot report it (before API 21): callers then reject the message. */
    static int sendingUid(Message message) {
        return Build.VERSION.SDK_INT >= 21 ? message.sendingUid : -1;
    }

    static AssetFileDescriptor openAsset(Context context, Uri uri, CancellationSignal signal)
            throws FileNotFoundException {
        return Build.VERSION.SDK_INT >= 19
                ? context.getContentResolver().openAssetFileDescriptor(uri, "r", signal)
                : context.getContentResolver().openAssetFileDescriptor(uri, "r");
    }

    static int allocationBytes(Bitmap bitmap) {
        return Build.VERSION.SDK_INT >= 19 ? bitmap.getAllocationByteCount() : bitmap.getByteCount();
    }

    static File noBackupDir(Context context) {
        return Build.VERSION.SDK_INT >= 21 ? context.getNoBackupFilesDir() : context.getFilesDir();
    }

    @SuppressWarnings("deprecation")
    static int saveLayerAlpha(Canvas canvas, float width, float height, int alpha) {
        return Build.VERSION.SDK_INT >= 21 ? canvas.saveLayerAlpha(0, 0, width, height, alpha)
                : canvas.saveLayerAlpha(0, 0, width, height, alpha, Canvas.ALL_SAVE_FLAG);
    }
}
