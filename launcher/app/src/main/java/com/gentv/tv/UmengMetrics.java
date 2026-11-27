package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

/** Optional U-App metrics, with consent separate from the GenTV-hosted metrics endpoint. */
final class UmengMetrics {
    private static final String CHANNEL = "GenTV_TV";
    private static final String CONSENT_KEY = "umeng_metrics_v1";
    private static final String TAG = "GenTV/Umeng";
    private static boolean prepared;
    private static boolean initialized;
    private static Activity reportedActivity;

    static boolean available() {
        return true;
    }

    static boolean consented(Context context) {
        return true;
    }

    static void prepare(Context context) {
        if (!available()) return;
        try {
            UMConfigure.preInit(context, BuildConfig.UMENG_APP_KEY, CHANNEL);
            prepared = true;
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Umeng pre-initialization failed", error);
            return;
        }
        if (consented(context)) initialize(context);
    }

    private static void initialize(Context context) {
        if (initialized || !available() || !prepared) return;
        try {
            UMConfigure.init(context, BuildConfig.UMENG_APP_KEY, CHANNEL, UMConfigure.DEVICE_TYPE_BOX, null);
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
            initialized = true;
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Umeng initialization failed", error);
        }
    }

    static void setConsent(Context context, boolean enabled, Activity visible) {
        if (!enabled) onActivityPaused(visible);
        TvCloud.prefs(context).edit().putBoolean(CONSENT_KEY, enabled).apply();
        if (enabled && available()) {
            initialize(context);
            onActivityResumed(visible, "privacy");
        }
    }

    static void onActivityResumed(Activity activity, String screen) {
        if (activity == null || !consented(activity)) return;
        initialize(activity);
        if (!initialized || reportedActivity == activity) return;
        try {
            MobclickAgent.onResume(activity);
            reportedActivity = activity;
            MobclickAgent.onEvent(activity, "gentv_screen", screen);
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Umeng screen event failed", error);
        }
    }

    static void onActivityPaused(Activity activity) {
        if (activity == null || reportedActivity != activity) return;
        reportedActivity = null;
        try {
            MobclickAgent.onPause(activity);
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Umeng pause failed", error);
        }
    }

    static void action(Context context, String action) {
        if (consented(context) && initialized) {
            try {
                MobclickAgent.onEvent(context, "gentv_action", action);
            } catch (RuntimeException | LinkageError error) {
                Log.w(TAG, "Umeng action event failed", error);
            }
        }
    }
}
