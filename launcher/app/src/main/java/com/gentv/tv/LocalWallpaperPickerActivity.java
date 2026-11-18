package com.gentv.tv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Small remote-friendly browser for TVs without a system document picker. */
public final class LocalWallpaperPickerActivity extends Activity {
    private static final int STORAGE_PERMISSION = 12;
    private final List<File> entries = new ArrayList<>();
    private final LinkedHashMap<String, File> roots = new LinkedHashMap<>();
    private RecyclerView grid;
    private TextView path;
    private TextView empty;
    private File current;
    private File internal;
    private boolean browserReady;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable storageCheck = new Runnable() {
        @Override public void run() {
            reconcileStorage(false);
            handler.postDelayed(this, 2000);
        }
    };
    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Some TV firmware does not publish its USB mount under /storage.
            // The periodic check below also catches those devices.
            String action = intent.getAction();
            if (current != null && isRemovableFolder(current)
                    && (Intent.ACTION_MEDIA_EJECT.equals(action)
                    || Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                    || Intent.ACTION_MEDIA_REMOVED.equals(action)
                    || Intent.ACTION_MEDIA_BAD_REMOVAL.equals(action))) {
                showRoots();
                Toast.makeText(LocalWallpaperPickerActivity.this,
                        R.string.wallpaper_picker_disconnected, Toast.LENGTH_SHORT).show();
                return;
            }
            reconcileStorage(true);
        }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_local_wallpaper_picker);
        WallpaperBackdrop.applyImage(this, findViewById(R.id.wallpaper));
        path = findViewById(R.id.path);
        empty = findViewById(R.id.empty);
        grid = findViewById(R.id.files);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setItemAnimator(null);
        grid.setAdapter(new FilesAdapter());
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 32
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        } else showRoots();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION) showRoots();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addDataScheme("file");
        registerReceiver(storageReceiver, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        reconcileStorage(false);
        handler.postDelayed(storageCheck, 2000);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(storageCheck);
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterReceiver(storageReceiver);
        super.onStop();
    }

    private void showRoots() {
        current = null;
        discoverRoots();
        entries.clear();
        entries.addAll(roots.values());
        path.setText(R.string.wallpaper_picker_roots);
        browserReady = true;
        refresh();
    }

    private void discoverRoots() {
        roots.clear();
        internal = Environment.getExternalStorageDirectory();
        addRoot(internal);
        File[] appDirs = android.os.Build.VERSION.SDK_INT >= 19
                ? getExternalFilesDirs(null) : new File[]{getExternalFilesDir(null)};
        if (appDirs != null) for (File appDir : appDirs) {
            if (appDir == null) continue;
            String full = appDir.getAbsolutePath();
            int boundary = full.indexOf("/Android/data/");
            if (boundary > 0) addRoot(new File(full.substring(0, boundary)));
        }
        File[] storage = new File("/storage").listFiles();
        if (storage != null) for (File candidate : storage) {
            if (!candidate.getName().equals("emulated") && !candidate.getName().equals("self"))
                addRoot(candidate);
        }
        // Some Android 5 TV firmware mounts USB under /mnt/sda/sda1 only.
        File[] mounts = new File("/mnt").listFiles();
        if (mounts != null) for (File disk : mounts) {
            if (!disk.getName().matches("sd[a-z]")) continue;
            File[] partitions = disk.listFiles();
            if (partitions == null) continue;
            for (File partition : partitions) {
                if (partition.getName().matches("sd[a-z][0-9]+")) addRoot(partition);
            }
        }
    }

    private void reconcileStorage(boolean mediaEvent) {
        if (!browserReady) return;
        if (current != null) {
            if (!current.isDirectory() || !current.canRead() || current.listFiles() == null) {
                showRoots();
                Toast.makeText(this, R.string.wallpaper_picker_disconnected, Toast.LENGTH_SHORT).show();
            } else if (mediaEvent) {
                showFolder(current);
            }
            return;
        }
        List<String> previous = new ArrayList<>(roots.keySet());
        discoverRoots();
        if (!previous.equals(new ArrayList<>(roots.keySet()))) {
            boolean removed = !roots.keySet().containsAll(previous);
            entries.clear();
            entries.addAll(roots.values());
            refresh();
            if (removed) Toast.makeText(this, R.string.wallpaper_picker_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void addRoot(File file) {
        if (file != null && file.isDirectory() && file.canRead())
            roots.put(file.getAbsolutePath(), file);
    }

    private boolean isRemovableFolder(File folder) {
        String path = folder.getAbsolutePath();
        String internalPath = internal == null ? "" : internal.getAbsolutePath();
        return !path.equals(internalPath) && !path.startsWith(internalPath + "/");
    }

    private void showFolder(File folder) {
        File[] children = folder == null ? null : folder.listFiles();
        if (children == null || !folder.canRead()) {
            showRoots();
            Toast.makeText(this, R.string.wallpaper_picker_disconnected, Toast.LENGTH_SHORT).show();
            return;
        }
        current = folder;
        entries.clear();
        for (File file : children) {
            if (file.getName().startsWith(".")) continue;
            if (file.isDirectory() && file.canRead() || file.isFile() && supported(file))
                entries.add(file);
        }
        Collator collator = Collator.getInstance();
        entries.sort((left, right) -> {
            if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
            return collator.compare(left.getName(), right.getName());
        });
        path.setText(folder.getAbsolutePath());
        refresh();
    }

    private boolean supported(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return Arrays.asList(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".mp4", ".m4v")
                .stream().anyMatch(name::endsWith);
    }

    private void refresh() {
        grid.getAdapter().notifyDataSetChanged();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        if (!entries.isEmpty()) grid.post(() -> {
            RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.findViewById(R.id.fileCard).requestFocus();
        });
    }

    @Override public void onBackPressed() {
        if (current == null) { super.onBackPressed(); return; }
        if (roots.containsKey(current.getAbsolutePath())) showRoots();
        else showFolder(current.getParentFile());
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_local_wallpaper_file, parent, false);
            LinearLayout card = item.findViewById(R.id.fileCard);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                card.setForeground(CompatApi.drawable(LocalWallpaperPickerActivity.this, R.drawable.my_apps_focus));
            }
            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
                        .setDuration(focused ? 170 : 130)
                        .setInterpolator(new DecelerateInterpolator()).start();
            });
            return new Holder(item);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            File file = entries.get(position);
            boolean directory = file.isDirectory();
            String name = file.getName().toLowerCase(Locale.ROOT);
            boolean video = name.endsWith(".mp4") || name.endsWith(".m4v");
            holder.symbol.setImageResource(directory ? R.drawable.ic_desktop_folder
                    : video ? R.drawable.ic_wallpaper_video : R.drawable.ic_wallpaper_image);
            holder.name.setText(current == null && file.equals(internal)
                    ? getString(R.string.wallpaper_picker_internal) : file.getName());
            int first = directory ? 0xFF315578 : video ? 0xFF51447B : 0xFF286E75;
            int second = directory ? 0xFF172B42 : video ? 0xFF24233F : 0xFF17343C;
            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{first, second});
            background.setCornerRadius(dp(20));
            holder.card.setBackground(background);
            holder.card.setOnClickListener(view -> {
                if (directory) showFolder(file);
                else {
                    if (!file.isFile() || !file.canRead()) {
                        reconcileStorage(true);
                        if (current != null) Toast.makeText(LocalWallpaperPickerActivity.this,
                                R.string.wallpaper_picker_file_unavailable, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setResult(RESULT_OK, new Intent().setData(Uri.fromFile(file)));
                    finish();
                }
            });
        }

        @Override public int getItemCount() { return entries.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final ImageView symbol;
            final TextView name;
            Holder(View item) {
                super(item);
                card = item.findViewById(R.id.fileCard);
                symbol = item.findViewById(R.id.fileSymbol);
                name = item.findViewById(R.id.fileName);
            }
        }
    }
}
