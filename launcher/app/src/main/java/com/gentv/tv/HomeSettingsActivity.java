package com.gentv.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Requests the Home role or opens the system picker; never changes another launcher directly. */
public class HomeSettingsActivity extends Activity {
    private static final int REQUEST_HOME_ROLE = 1;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 29 && requestHomeRole()) return;
        openHomeSettings();
    }

    @android.annotation.TargetApi(29)
    private boolean requestHomeRole() {
        android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return false;
        if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
            Toast.makeText(this, R.string.home_already_default, Toast.LENGTH_SHORT).show();
            finish();
            return true;
        }
        try {
            startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQUEST_HOME_ROLE);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_HOME_ROLE) return;
        if (resultCode == RESULT_OK) Toast.makeText(this, R.string.home_set_success, Toast.LENGTH_SHORT).show();
        finish();
    }
}
