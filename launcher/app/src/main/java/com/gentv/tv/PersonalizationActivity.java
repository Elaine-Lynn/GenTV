package com.gentv.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Large preview and two Leanback thumbnail rows; image decoding never blocks focus scrolling. */
public final class PersonalizationActivity extends Activity {
    private static final int PICK_WALLPAPER = 31;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Set<Integer> loading = new HashSet<>();
    private final Bitmap[][] thumbnails = {
            new Bitmap[BundledWallpapers.IDS.length],
            new Bitmap[BundledWallpapers.MOTION_IDS.length],
            new Bitmap[1]
    };
    private LruCache<Integer, Bitmap> previews;
    private ImageView preview;
    private ImageView previousPreview;
    private HorizontalGridView stillChoices;
    private HorizontalGridView motionChoices;
    private HorizontalGridView userChoices;
    private BundledVideoPreview videoPreview;
    private Bitmap shownBitmap;
    private String selected;
    private int requestedResource;
    private int selectedMotionResource;
    private int previewSample;
    private boolean ready;
    private boolean closed;
    private boolean freezeMotionPreview;
    private final int[] focusedIndexes = new int[3];
    private final boolean[] visitedRows = new boolean[3];
    private int rowNavigationKey;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_personalization);
        WallpaperBackdrop.apply(this, ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        selected = BundledWallpapers.selected(this);
        freezeMotionPreview = UserWallpaper.isVideo(selected)
                || BundledWallpapers.videoResource(this) != 0;
        ActivityManager memory = CompatApi.service(this, ActivityManager.class);
        boolean lowRam = memory != null && CompatApi.lowRam(memory);
        previewSample = lowRam ? 2 : 1;
        previews = new LruCache<Integer, Bitmap>(lowRam ? 8 * 1024 * 1024 : 28 * 1024 * 1024) {
            @Override protected int sizeOf(Integer key, Bitmap value) { return value.getByteCount(); }
        };
        preview = findViewById(R.id.wallpaperPreview);
        previousPreview = findViewById(R.id.wallpaperPreviousPreview);
        stillChoices = findViewById(R.id.wallpaperChoices);
        motionChoices = findViewById(R.id.wallpaperMotionChoices);
        userChoices = findViewById(R.id.wallpaperUserChoices);
        videoPreview = new BundledVideoPreview(this, findViewById(R.id.wallpaperVideoPreview));
        configure(stillChoices);
        configure(motionChoices);
        configure(userChoices);

        int group = 0, index = 0;
        for (int i = 0; i < BundledWallpapers.IDS.length; i++)
            if (BundledWallpapers.IDS[i].equals(selected)) index = i;
        for (int i = 0; i < BundledWallpapers.MOTION_IDS.length; i++)
            if (BundledWallpapers.MOTION_IDS[i].equals(selected)) { group = 1; index = i; }
        if (UserWallpaper.isImage(selected) || UserWallpaper.isVideo(selected)) { group = 2; index = 0; }
        final int initialGroup = group, initialIndex = index;
        requestedResource = group == 2 ? 0 : imageAt(group, index);
        selectedMotionResource = videoAt(group, index);
        decoder.execute(() -> {
            Bitmap first = initialGroup == 2 ? decodeUserPreview(previewSample)
                    : decode(requestedResource, previewSample);
            if (freezeMotionPreview && (initialGroup == 1 || UserWallpaper.isVideo(selected)))
                first = WallpaperBlur.fromFrame(first);
            Bitmap initialPreview = first;
            Bitmap[][] loaded = {
                    new Bitmap[BundledWallpapers.IDS.length],
                    new Bitmap[BundledWallpapers.MOTION_IDS.length],
                    new Bitmap[1]
            };
            for (int column = 0; column < 2; column++)
                for (int choice = 0; choice < loaded[column].length; choice++)
                    loaded[column][choice] = decode(thumbnailAt(column, choice), column == 0 ? 1 : 4);
            loaded[2][0] = decodeUserPreview(4);
            main.post(() -> {
                if (closed) return;
                for (int column = 0; column < loaded.length; column++)
                    System.arraycopy(loaded[column], 0, thumbnails[column], 0, loaded[column].length);
                if (initialPreview != null) {
                    if (initialGroup != 2) previews.put(requestedResource, initialPreview);
                    showBitmap(initialPreview);
                }
                stillChoices.setAdapter(new ChoiceAdapter(0));
                motionChoices.setAdapter(new ChoiceAdapter(1));
                userChoices.setAdapter(new ChoiceAdapter(2));
                ready = true;
                HorizontalGridView target = initialGroup == 0 ? stillChoices
                        : initialGroup == 1 ? motionChoices : userChoices;
                target.setSelectedPosition(initialIndex);
                focusedIndexes[initialGroup] = initialIndex;
                visitedRows[initialGroup] = true;
                target.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = target.findViewHolderForAdapterPosition(initialIndex);
                    if (holder != null) holder.itemView.requestFocusFromTouch();
                }, 60);
                prefetch(initialGroup == 2 ? 0 : initialGroup);
            });
        });
    }

    private void configure(HorizontalGridView row) {
        row.setNumRows(1);
        row.setHorizontalSpacing(dp(10));
        row.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        row.setWindowAlignmentOffsetPercent(50f);
        row.setItemAlignmentOffsetPercent(50f);
        row.setClipChildren(false);
        row.setClipToPadding(false);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && key == rowNavigationKey) {
            rowNavigationKey = 0;
            return true;
        }
        if (!ready || event.getAction() != KeyEvent.ACTION_DOWN
                || (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN))
            return super.dispatchKeyEvent(event);
        HorizontalGridView[] rows = {stillChoices, motionChoices, userChoices};
        View focused = getCurrentFocus();
        for (int group = 0; group < rows.length; group++) {
            RecyclerView.ViewHolder holder = focused == null ? null
                    : rows[group].findContainingViewHolder(focused);
            if (holder == null) continue;
            int destination = group + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (destination < 0 || destination >= rows.length) return true;
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) current = focusedIndexes[group];
            focusedIndexes[group] = current;
            visitedRows[group] = true;
            int count = rows[destination].getAdapter().getItemCount();
            int index = Math.min(visitedRows[destination] ? focusedIndexes[destination] : current, count - 1);
            rowNavigationKey = key;
            HorizontalGridView target = rows[destination];
            target.setSelectedPosition(index);
            target.post(() -> {
                RecyclerView.ViewHolder next = target.findViewHolderForAdapterPosition(index);
                if (next != null) next.itemView.requestFocus();
                else target.requestFocus();
            });
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private int imageAt(int group, int index) {
        return group == 0 ? BundledWallpapers.IMAGES[index]
                : group == 1 ? BundledWallpapers.MOTION_IMAGES[index] : 0;
    }

    private int thumbnailAt(int group, int index) {
        return group == 0 ? BundledWallpapers.IMAGE_PREVIEWS[index]
                : BundledWallpapers.MOTION_IMAGES[index];
    }

    private int videoAt(int group, int index) {
        return group == 1 ? BundledWallpapers.MOTION_VIDEOS[index] : 0;
    }

    private void requestPreview(int group, int index) {
        if (group == 2) {
            requestedResource = 0;
            selectedMotionResource = 0;
            if (!freezeMotionPreview && UserWallpaper.isVideo(selected))
                videoPreview.selectFile(UserWallpaper.video(this));
            else videoPreview.select(0);
            decoder.execute(() -> {
                Bitmap bitmap = decodeUserPreview(previewSample);
                if (freezeMotionPreview && UserWallpaper.isVideo(selected))
                    bitmap = WallpaperBlur.fromFrame(bitmap);
                Bitmap result = bitmap;
                main.post(() -> { if (!closed && requestedResource == 0 && result != null) showBitmap(result); });
            });
            return;
        }
        int resource = imageAt(group, index);
        requestedResource = resource;
        selectedMotionResource = videoAt(group, index);
        videoPreview.select(freezeMotionPreview ? 0 : selectedMotionResource);
        Bitmap cached = previews.get(resource);
        if (cached != null) showBitmap(cached);
        else queuePreview(resource);
    }

    private void queuePreview(int resource) {
        if (previews.get(resource) != null || !loading.add(resource)) return;
        decoder.execute(() -> {
            Bitmap bitmap = decode(resource, previewSample);
            if (freezeMotionPreview && isMotionImage(resource))
                bitmap = WallpaperBlur.fromFrame(bitmap);
            Bitmap result = bitmap;
            main.post(() -> {
                loading.remove(resource);
                if (closed || result == null) return;
                previews.put(resource, result);
                if (requestedResource == resource) showBitmap(result);
            });
        });
    }

    private boolean isMotionImage(int resource) {
        for (int motion : BundledWallpapers.MOTION_IMAGES)
            if (motion == resource) return true;
        return false;
    }

    private void prefetch(int firstGroup) {
        for (int pass = 0; pass < 2; pass++) {
            int group = pass == 0 ? firstGroup : 1 - firstGroup;
            int count = group == 0 ? BundledWallpapers.IDS.length : BundledWallpapers.MOTION_IDS.length;
            for (int i = 0; i < count; i++) queuePreview(imageAt(group, i));
        }
    }

    private void showBitmap(Bitmap bitmap) {
        if (shownBitmap == bitmap) return;
        preview.animate().cancel();
        boolean previous = preview.getDrawable() != null;
        if (previous) {
            previousPreview.setImageDrawable(preview.getDrawable());
            previousPreview.setVisibility(View.VISIBLE);
        }
        preview.setImageBitmap(bitmap);
        shownBitmap = bitmap;
        preview.setAlpha(previous ? 0f : 1f);
        if (previous) preview.animate().alpha(1f).setDuration(230)
                .withEndAction(() -> {
                    previousPreview.setImageDrawable(null);
                    previousPreview.setVisibility(View.GONE);
                }).start();
    }

    private Bitmap decode(int resource, int sample) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(getResources(), resource, options);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    private Bitmap decodeUserPreview(int sample) {
        File file = UserWallpaper.isVideo(selected) ? UserWallpaper.videoPreview(this)
                : UserWallpaper.image(this);
        if (!file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try { return BitmapFactory.decodeFile(file.getAbsolutePath(), options); }
        catch (RuntimeException | OutOfMemoryError ignored) { return null; }
    }

    private void pickUserWallpaper() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"image/jpeg", "image/png", "image/webp", "video/mp4"});
            try {
                startActivityForResult(picker, PICK_WALLPAPER);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Some TVs ship no document provider; use the remote-friendly local browser.
            }
        }
        startActivityForResult(new Intent(this, LocalWallpaperPickerActivity.class), PICK_WALLPAPER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        decoder.execute(() -> {
            String id;
            try { id = UserWallpaper.importFile(this, uri); }
            catch (Exception error) { id = null; }
            String imported = id;
            main.post(() -> {
                if (closed) return;
                if (imported == null) {
                    Toast.makeText(this, R.string.wallpaper_user_failed, Toast.LENGTH_SHORT).show();
                } else {
                    BundledWallpapers.select(this, imported);
                    finish();
                }
            });
        });
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onPause() {
        videoPreview.stop();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ready && !freezeMotionPreview && selectedMotionResource != 0)
            videoPreview.select(selectedMotionResource);
        else if (ready && !freezeMotionPreview && userChoices.hasFocus() && UserWallpaper.isVideo(selected))
            videoPreview.selectFile(UserWallpaper.video(this));
    }

    @Override protected void onDestroy() {
        closed = true;
        decoder.shutdownNow();
        videoPreview.close();
        previousPreview.setImageDrawable(null);
        super.onDestroy();
    }

    private final class ChoiceAdapter extends RecyclerView.Adapter<ChoiceHolder> {
        private final int group;

        ChoiceAdapter(int group) { this.group = group; setHasStableIds(true); }

        @NonNull @Override public ChoiceHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper_choice, parent, false);
            View card = item.findViewById(R.id.wallpaperCard);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
                    }
                });
                card.setClipToOutline(true);
            }
            item.setOnFocusChangeListener((view, focused) -> {
                card.setSelected(focused);
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                        .setDuration(focused ? 170 : 135)
                        .setInterpolator(new DecelerateInterpolator()).start();
                if (focused) {
                    HorizontalGridView row = group == 0 ? stillChoices
                            : group == 1 ? motionChoices : userChoices;
                    int position = row.getChildAdapterPosition(view);
                    if (position != RecyclerView.NO_POSITION) {
                        focusedIndexes[group] = position;
                        visitedRows[group] = true;
                        requestPreview(group, position);
                    }
                }
            });
            return new ChoiceHolder(item);
        }

        @Override public void onBindViewHolder(@NonNull ChoiceHolder holder, int position) {
            holder.image.setImageBitmap(thumbnails[group][position]);
            holder.add.setVisibility(group == 2 ? View.VISIBLE : View.GONE);
            holder.name.setText(group == 0 ? BundledWallpapers.TITLES[position]
                    : group == 1 ? BundledWallpapers.MOTION_TITLES[position]
                    : R.string.wallpaper_user_add);
            String id = group == 0 ? BundledWallpapers.IDS[position]
                    : group == 1 ? BundledWallpapers.MOTION_IDS[position] : selected;
            holder.selectedBadge.setVisibility(group == 2
                    ? UserWallpaper.isImage(selected) || UserWallpaper.isVideo(selected)
                        ? View.VISIBLE : View.GONE
                    : id.equals(selected) ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(view -> {
                if (group == 2) { pickUserWallpaper(); return; }
                BundledWallpapers.select(PersonalizationActivity.this, id);
                finish();
            });
        }

        @Override public long getItemId(int position) { return position; }
        @Override public int getItemCount() {
            return group == 0 ? BundledWallpapers.IDS.length
                    : group == 1 ? BundledWallpapers.MOTION_IDS.length : 1;
        }
    }

    private static final class ChoiceHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView add;
        final TextView name;
        final TextView selectedBadge;

        ChoiceHolder(View item) {
            super(item);
            image = item.findViewById(R.id.wallpaperImage);
            add = item.findViewById(R.id.wallpaperAdd);
            name = item.findViewById(R.id.wallpaperName);
            selectedBadge = item.findViewById(R.id.selectedBadge);
        }
    }
}
