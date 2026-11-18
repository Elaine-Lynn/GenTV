package com.gentv.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.annotation.RequiresApi;

/**
 * Starts this launcher's process after the firmware has killed it.
 *
 * A notification listener keeps its approval across a force stop, and the system binds it again
 * once the process is gone. That rebind is what brings Home back on TCL and Xiaomi. No notification
 * is read. Enable with {@code cmd notification allow_listener com.gentv.tv/.GuardRevival}.
 */
@RequiresApi(18)
public class GuardRevival extends NotificationListenerService {
    private BroadcastReceiver unlocked;

    @Override public void onNotificationPosted(StatusBarNotification notification) { }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { }

    @Override
    public void onListenerConnected() {
        UserManager users = CompatApi.service(this, UserManager.class);
        if (users != null && !CompatApi.userUnlocked(users)) {
            if (unlocked != null) return;
            unlocked = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    stopWaiting();
                    GuardKeep.ensureHomeKey(GuardRevival.this);
                }
            };
            registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if (CompatApi.userUnlocked(users)) {
                stopWaiting();
                GuardKeep.ensureHomeKey(this);
            }
            return;
        }
        GuardKeep.ensureHomeKey(this);
    }

    private void stopWaiting() {
        if (unlocked == null) return;
        try {
            unregisterReceiver(unlocked);
        } catch (RuntimeException ignored) {
        }
        unlocked = null;
    }

    @Override
    public void onDestroy() {
        stopWaiting();
        super.onDestroy();
    }
}
