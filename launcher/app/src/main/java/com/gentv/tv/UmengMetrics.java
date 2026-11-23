package com.gentv.tv;

import android.app.Activity;
import android.content.Context;

import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

/** Optional U-App metrics, with consent separate from the GenTV-hosted metrics endpoint. */
final class UmengMetrics {
    private static final String CHANNEL = "GenTV_TV";
    private static final String CONSENT_KEY = "umeng_metrics_v1";
    private static boolean initialized;
    private static Activity reportedActivity;

    static boolean available() {
        return !BuildConfig.DEBUG && !BuildConfig.UMENG_APP_KEY.isEmpty();
    }

    static boolean consented(Context context) {
        return available() && TvCloud.prefs(context).getBoolean(CONSENT_KEY, false);
    }

    static void prepare(Context context) {
        if (!available()) return;
        UMConfigure.preInit(context, BuildConfig.UMENG_APP_KEY, CHANNEL);
        if (consented(context)) initialize(context);
    }

    private static void initialize(Context context) {
        if (initialized || !available()) return;
        UMConfigure.init(context, BuildConfig.UMENG_APP_KEY, CHANNEL, UMConfigure.DEVICE_TYPE_BOX, null);
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
        initialized = true;
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
        if (reportedActivity == activity) return;
        MobclickAgent.onResume(activity);
        reportedActivity = activity;
        MobclickAgent.onEvent(activity, "gentv_screen", screen);
    }

    static void onActivityPaused(Activity activity) {
        if (activity == null || reportedActivity != activity) return;
        MobclickAgent.onPause(activity);
        reportedActivity = null;
    }

    static void action(Context context, String action) {
        if (consented(context) && initialized) {
            MobclickAgent.onEvent(context, "gentv_action", action);
        }
    }
}
