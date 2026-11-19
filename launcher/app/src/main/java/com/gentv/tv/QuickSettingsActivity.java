package com.gentv.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    private final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final int icon;
        final int title;
        final int status;
        final Runnable action;

        Entry(int icon, int title, int status, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.status = status;
            this.action = action;
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        WallpaperBackdrop.applyOverlay(this, findViewById(android.R.id.content));
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        View scrim = findViewById(R.id.scrim);
        scrim.setOnClickListener(v -> finish());
        scrim.setFocusable(false);
        addSection(R.string.quick_group_device);
        addDeviceRows();
        addSection(R.string.quick_group_desktop);
        addDesktopRows();
        addSection(R.string.quick_group_services);
        addServiceRows();
        VerticalGridView grid = findViewById(R.id.rows);
        grid.setNumColumns(1);
        grid.setAdapter(new ToolboxAdapter());
        grid.setSelectedPosition(1);
        grid.requestFocus();
    }

    private void addSection(int label) {
        entries.add(new Entry(0, label, 0, null));
    }

    private void addDeviceRows() {
        addRow(R.drawable.ic_input, R.string.inputs, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS));
        addRow(R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addRow(R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        android.media.AudioManager audio = (android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        if (audio != null && (android.os.Build.VERSION.SDK_INT < 21 || !audio.isVolumeFixed())
                && CompatApi.permissionGranted(this, android.Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            addVolume(R.drawable.ic_volume_down, R.string.desktop_volume_down, audio,
                    android.media.AudioManager.ADJUST_LOWER);
            addVolume(R.drawable.ic_volume_up, R.string.desktop_volume_up, audio,
                    android.media.AudioManager.ADJUST_RAISE);
        }
    }

    private void addDesktopRows() {
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(R.drawable.ic_controls, DesktopMode.settingsTitle(this), 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        addRow(R.drawable.ic_home_settings, R.string.set_default_home, R.string.set_default_home_hint,
                new Intent(this, HomeSettingsActivity.class));
        addRow(R.drawable.ic_personalize, R.string.quick_personalize, 0,
                new Intent(this, PersonalizationActivity.class));
        addRow(R.drawable.ic_theme, R.string.desktop_mode_title, 0, this::showDesktopModes);
    }

    private void addServiceRows() {
        addRow(R.drawable.ic_cloud, R.string.cloud_settings, 0, new Intent(this, CloudSettingsActivity.class));
        addRow(R.drawable.ic_weather, R.string.weather_settings_title, 0,
                new Intent(this, WeatherSettingsActivity.class));
        addRow(R.drawable.ic_lock, R.string.child_lock_settings, 0,
                new Intent(this, ChildSettingsActivity.class));
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addVolume(int icon, int label,
                           android.media.AudioManager audio, int direction) {
        addRow(icon, label, 0, () -> {
            try { audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI); }
            catch (SecurityException e) { Toast.makeText(this,R.string.not_available,Toast.LENGTH_SHORT).show(); }
        });
    }

    private void showDesktopModes() {
        DesktopMode current = DesktopMode.current(this);
        View previousFocus = getCurrentFocus();
        View choices = getLayoutInflater().inflate(R.layout.dialog_desktop_mode, null);
        View simple = choices.findViewById(R.id.desktopModeSimple);
        View pond = choices.findViewById(R.id.desktopModePond);
        choices.findViewById(R.id.simpleModeCheck).setVisibility(current == DesktopMode.SIMPLE ? View.VISIBLE : View.INVISIBLE);
        choices.findViewById(R.id.pondModeCheck).setVisibility(current == DesktopMode.POND ? View.VISIBLE : View.INVISIBLE);
        ((ImageView) choices.findViewById(R.id.modeGlassBackdrop)).setImageResource(
                current == DesktopMode.POND ? R.drawable.theme_preview_pond_blur : R.drawable.theme_preview_simple_blur);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(choices);
        simple.setOnClickListener(v -> selectDesktopMode(dialog, current, DesktopMode.SIMPLE));
        pond.setOnClickListener(v -> selectDesktopMode(dialog, current, DesktopMode.POND));
        dialog.setOnDismissListener(d -> {
            if (previousFocus != null) previousFocus.requestFocus();
        });
        dialog.show();
        android.view.Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0.38f);
        float density = getResources().getDisplayMetrics().density;
        int available = getResources().getDisplayMetrics().widthPixels - Math.round(48 * density);
        window.setLayout(Math.min(available, Math.round(680 * density)),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        (current == DesktopMode.POND ? pond : simple).requestFocus();
    }

    private void selectDesktopMode(android.app.Dialog dialog, DesktopMode current, DesktopMode selected) {
        dialog.dismiss();
        if (selected == current) return;
        selected.select(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void addRow(int icon, int title, int status, Intent target) {
        addRow(icon, title, status, () -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    private void addRow(int icon, int title, int status, Runnable action) {
        entries.add(new Entry(icon, title, status, action));
    }

    private final class ToolboxAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) {
            return entries.get(position).action == null ? 0 : 1;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int type) {
            View view = getLayoutInflater().inflate(type == 0 ? R.layout.item_quick_section : R.layout.item_input,
                    parent, false);
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Entry entry = entries.get(position);
            View view = holder.itemView;
            if (entry.action == null) {
                ((TextView) view).setText(entry.title);
                return;
            }
            ((ImageView) view.findViewById(R.id.icon)).setImageResource(entry.icon);
            ((TextView) view.findViewById(R.id.title)).setText(entry.title);
            view.findViewById(R.id.dot).setVisibility(View.GONE);
            TextView hint = view.findViewById(R.id.status);
            if (entry.status != 0) {
                hint.setText(entry.status);
                hint.setVisibility(View.VISIBLE);
            } else hint.setVisibility(View.GONE);
            view.setOnClickListener(v -> entry.action.run());
        }

        @Override public int getItemCount() { return entries.size(); }
    }
}
