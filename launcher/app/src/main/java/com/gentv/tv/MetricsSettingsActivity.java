package com.gentv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;

/** A separate privacy page: software-update UI remains unchanged. */
public final class MetricsSettingsActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(LauncherLocale.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL); int pad=(int)(48*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad,pad,pad);panel.setBackgroundColor(0xFF142028);
        TextView title = new TextView(this);title.setText(R.string.metrics_title);title.setTextColor(-1);title.setTextSize(26);panel.addView(title);
        TextView hint = new TextView(this);hint.setText(R.string.metrics_hint);hint.setTextColor(0xFFCCD4DB);hint.setTextSize(17);hint.setPadding(0,20,0,24);panel.addView(hint);
        Switch consent = new Switch(this);consent.setText(R.string.metrics_share_statistics);consent.setTextColor(-1);consent.setTextSize(20);consent.setChecked(TvMetrics.enabled(this));consent.setEnabled(TvCloud.enabled());consent.setFocusable(true);consent.setOnCheckedChangeListener((v,on)->TvMetrics.consent(this,on));panel.addView(consent);
        Switch test = new Switch(this);test.setText(R.string.metrics_test);test.setTextColor(-1);test.setTextSize(18);test.setPadding(0,24,0,24);test.setChecked(BuildConfig.DEBUG || TvMetrics.emulator() || TvCloud.prefs(this).getBoolean("metrics_test",false));test.setEnabled(!BuildConfig.DEBUG && !TvMetrics.emulator());test.setFocusable(true);test.setOnCheckedChangeListener((v,on)->TvCloud.prefs(this).edit().putBoolean("metrics_test",on).apply());panel.addView(test);
        setContentView(panel);consent.requestFocus();
    }
}
