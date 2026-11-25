package com.gentv.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Opens an XGH-A01 settings category after the vendor's singleTask panel has initialized. */
public final class XghSettingsRedirectActivity extends Activity {
    static final String ACTION_INPUT_SOURCE = "com.gentv.tv.action.XGH_INPUT_SOURCE";
    private static final String EXTRA_TAG = "tag";
    private static final ComponentName SETTINGS = new ComponentName("com.dangbei.haqusetting",
            "com.dangbei.haqusetting.ui.HomeActivity");

    static Intent intent(Context context, String tag) {
        return new Intent(context, XghSettingsRedirectActivity.class).putExtra(EXTRA_TAG, tag);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String tag = getIntent().getStringExtra(EXTRA_TAG);
        if (tag == null) {
            finish();
            return;
        }
        try {
            // This firmware reads the category only in HomeActivity.onNewIntent(), not onCreate().
            startActivity(new Intent().setComponent(SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("haqu://home?tag=" + tag))
                        .setComponent(SETTINGS));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The settings panel is already open; its default page remains usable.
            }
            finish();
        }, 1200);
    }
}
