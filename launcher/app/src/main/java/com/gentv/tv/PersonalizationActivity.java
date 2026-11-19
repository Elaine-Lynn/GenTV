package com.gentv.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Large preview and two Leanback thumbnail rows; image decoding never blocks focus scrolling. */
public final class PersonalizationActivity extends Activity {
    private static final int PICK_WALLPAPER = 31;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Set<Integer> loading = new HashSet<>();
    private Bitmap[][] thumbnails = {
            new Bitmap[BundledWallpapers.IDS.length],
            new Bitmap[BundledWallpapers.MOTION_IDS.length],
            new Bitmap[2]
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
    private List<String> userIds;
    private String focusedUserId;
    private int requestedResource;
    private int selectedMotionResource;
    private int previewSample;
    private boolean lowRam;
    private boolean ready;
    private boolean closed;
    private LanWallpaperReceiver lanReceiver;
    private AlertDialog lanDialog;
    private TextView lanStatus;
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
        userIds = UserWallpaper.ids(this);
        ActivityManager memory = CompatApi.service(this, ActivityManager.class);
        lowRam = CompatApi.constrainedHeap(memory);
        previewSample = lowRam ? 2 : 1;
        previews = new LruCache<Integer, Bitmap>(lowRam ? 4 * 1024 * 1024 : 28 * 1024 * 1024) {
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
        int userIndex = userIds.indexOf(selected);
        if (userIndex >= 0) { group = 2; index = userIndex + 2; focusedUserId = selected; }
        final int initialGroup = group, initialIndex = index;
        requestedResource = group == 2 ? 0 : imageAt(group, index);
        selectedMotionResource = videoAt(group, index);
        decoder.execute(() -> {
            Bitmap first = initialGroup == 2 ? decodeUserPreview(selected,
                    userPreviewWidth(), userPreviewHeight())
                    : decode(requestedResource, previewSample);
            Bitmap initialPreview = first;
            Bitmap[][] loaded = {
                    new Bitmap[BundledWallpapers.IDS.length],
                    new Bitmap[BundledWallpapers.MOTION_IDS.length],
                    new Bitmap[userIds.size() + 2]
            };
            for (int column = 0; column < 2; column++)
                for (int choice = 0; choice < loaded[column].length; choice++)
                    loaded[column][choice] = decode(thumbnailAt(column, choice), column == 0 ? 1 : 4);
            for (int choice = 0; choice < userIds.size(); choice++)
                loaded[2][choice + 2] = decodeUserPreview(userIds.get(choice), 220, 130);
            main.post(() -> {
                if (closed) {
                    if (initialPreview != null) initialPreview.recycle();
                    for (Bitmap[] groupBitmaps : loaded)
                        for (Bitmap bitmap : groupBitmaps) if (bitmap != null) bitmap.recycle();
                    return;
                }
                thumbnails = loaded;
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
                if (!lowRam) prefetch(initialGroup == 2 ? 0 : initialGroup);
            });
        });
    }

    private void configure(HorizontalGridView row) {
        row.setNumRows(1);
        row.setHorizontalSpacing(dp(28));
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
            focusedUserId = index < 2 ? null : userIds.get(index - 2);
            if (focusedUserId != null && UserWallpaper.isVideo(focusedUserId))
                videoPreview.selectFile(UserWallpaper.video(this, focusedUserId));
            else videoPreview.select(0);
            if (focusedUserId == null) return;
            String id = focusedUserId;
            decoder.execute(() -> {
                Bitmap bitmap = decodeUserPreview(id,
                        userPreviewWidth(), userPreviewHeight());
                Bitmap result = bitmap;
                main.post(() -> {
                    if (result == null) return;
                    if (!closed && id.equals(focusedUserId)) showBitmap(result);
                    else result.recycle();
                });
            });
            return;
        }
        int resource = imageAt(group, index);
        requestedResource = resource;
        selectedMotionResource = videoAt(group, index);
        videoPreview.select(selectedMotionResource);
        Bitmap cached = previews.get(resource);
        if (cached != null) showBitmap(cached);
        else queuePreview(resource);
    }

    private void queuePreview(int resource) {
        if (previews.get(resource) != null || !loading.add(resource)) return;
        decoder.execute(() -> {
            Bitmap bitmap = decode(resource, previewSample);
            Bitmap result = bitmap;
            main.post(() -> {
                loading.remove(resource);
                if (result == null) return;
                if (closed) { result.recycle(); return; }
                previews.put(resource, result);
                if (requestedResource == resource) showBitmap(result);
            });
        });
    }

    private void prefetch(int firstGroup) {
        for (int pass = 0; pass < 2; pass++) {
            int group = pass == 0 ? firstGroup : 1 - firstGroup;
            int count = group == 0 ? BundledWallpapers.IDS.length : BundledWallpapers.MOTION_IDS.length;
            for (int i = 0; i < count; i++) queuePreview(imageAt(group, i));
        }
    }

    private int userPreviewWidth() {
        int width = getResources().getDisplayMetrics().widthPixels / previewSample;
        return lowRam ? Math.min(640, width) : width;
    }

    private int userPreviewHeight() {
        int height = getResources().getDisplayMetrics().heightPixels / previewSample;
        return lowRam ? Math.min(360, height) : height;
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

    private Bitmap decodeUserPreview(String id, int maxWidth, int maxHeight) {
        File file = UserWallpaper.isVideo(id) ? UserWallpaper.videoPreview(this, id)
                : UserWallpaper.image(this, id);
        if (!file.isFile()) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (options.outWidth / (options.inSampleSize * 2) >= Math.max(1, maxWidth)
                    || options.outHeight / (options.inSampleSize * 2) >= Math.max(1, maxHeight))
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
        catch (RuntimeException | OutOfMemoryError ignored) { return null; }
    }

    private void pickUserWallpaper() {
        // Android 12 and below can browse shared images/videos in our remote-friendly
        // picker with READ_EXTERNAL_STORAGE. Keep SAF on 13+ until its granular media
        // permissions and partial-access behavior are handled by the local browser.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"image/jpeg", "image/png", "image/webp", "video/mp4",
                                    "video/webm", "video/x-matroska"});
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
            catch (Exception | OutOfMemoryError error) { id = null; }
            String imported = id;
            showImportedWallpaper(imported, true);
        });
    }

    private void showImportedWallpaper(String imported, boolean apply) {
        List<String> updated = imported == null ? null : UserWallpaper.ids(this);
        Bitmap[] updatedThumbs = updated == null ? null : new Bitmap[updated.size() + 2];
        if (updated != null) for (int i = 0; i < updated.size(); i++)
            updatedThumbs[i + 2] = decodeUserPreview(updated.get(i), 220, 130);
        main.post(() -> {
            if (closed) {
                if (updatedThumbs != null) for (Bitmap bitmap : updatedThumbs)
                    if (bitmap != null) bitmap.recycle();
                return;
            }
            if (imported == null) {
                Toast.makeText(this, R.string.wallpaper_user_failed, Toast.LENGTH_SHORT).show();
                if (!apply && lanStatus != null) lanStatus.setText(R.string.wallpaper_lan_import_failed);
                return;
            }
            if (apply) {
                BundledWallpapers.select(this, imported);
                selected = imported;
            } else {
                Toast.makeText(this, R.string.wallpaper_lan_added, Toast.LENGTH_SHORT).show();
                if (lanStatus != null) lanStatus.setText(R.string.wallpaper_lan_added);
            }
            userIds = updated;
            thumbnails[2] = updatedThumbs;
            userChoices.getAdapter().notifyDataSetChanged();
            int position = updated.indexOf(imported) + 2;
            userChoices.setSelectedPosition(position);
            focusedIndexes[2] = position;
            requestPreview(2, position);
            userChoices.post(() -> {
                RecyclerView.ViewHolder holder = userChoices.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.requestFocus();
            });
        });
    }

    private void openLanReceiver() {
        String address = localAddress();
        if (address == null) {
            Toast.makeText(this, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        String code = String.format(java.util.Locale.ROOT, "%04d", new SecureRandom().nextInt(10_000));
        try {
            lanReceiver = new LanWallpaperReceiver(address, code, getCacheDir(), (file, mime) ->
                    main.post(() -> onLanUploaded(file, mime)));
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(28), dp(26), dp(28), dp(24));
            GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(34, 53, 77), Color.rgb(16, 25, 39)});
            panel.setCornerRadius(dp(22));
            panel.setStroke(dp(1), Color.rgb(69, 95, 122));
            body.setBackground(panel);
            TextView title = lanText(R.string.wallpaper_lan_title, 26, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            body.addView(title);
            TextView subtitle = lanText(R.string.wallpaper_lan_subtitle, 15, Color.rgb(174, 194, 216));
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
            subtitleParams.topMargin = dp(8);
            body.addView(subtitle, subtitleParams);
            LinearLayout columns = new LinearLayout(this);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(-1, -2);
            columnsParams.topMargin = dp(24);
            body.addView(columns, columnsParams);
            LinearLayout qrFrame = new LinearLayout(this);
            qrFrame.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable qrBackground = new GradientDrawable();
            qrBackground.setColor(Color.WHITE);
            qrBackground.setCornerRadius(dp(16));
            qrFrame.setBackground(qrBackground);
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qrCode(lanReceiver.url() + "#code=" + code));
            qr.setContentDescription(getString(R.string.wallpaper_lan_scan));
            qrFrame.addView(qr, new LinearLayout.LayoutParams(dp(190), dp(190)));
            columns.addView(qrFrame);
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1f);
            detailsParams.leftMargin = dp(25);
            columns.addView(details, detailsParams);
            TextView scan = lanText(R.string.wallpaper_lan_scan, 19, Color.WHITE);
            scan.setTypeface(null, android.graphics.Typeface.BOLD);
            details.addView(scan);
            TextView browser = lanText(R.string.wallpaper_lan_browser, 13, Color.rgb(174, 194, 216));
            LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(-1, -2);
            browserParams.topMargin = dp(17);
            details.addView(browser, browserParams);
            TextView url = lanText(lanReceiver.url(), 17, Color.rgb(126, 219, 236));
            url.setTextIsSelectable(true);
            LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, -2);
            urlParams.topMargin = dp(5);
            details.addView(url, urlParams);
            TextView codeLabel = lanText(R.string.wallpaper_lan_code, 13, Color.rgb(174, 194, 216));
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
            codeParams.topMargin = dp(18);
            details.addView(codeLabel, codeParams);
            TextView codeView = lanText(code, 28, Color.WHITE);
            codeView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            if (android.os.Build.VERSION.SDK_INT >= 21) codeView.setLetterSpacing(0.12f);
            details.addView(codeView);
            TextView limits = lanText(R.string.wallpaper_lan_limits, 13, Color.rgb(148, 171, 196));
            LinearLayout.LayoutParams limitsParams = new LinearLayout.LayoutParams(-1, -2);
            limitsParams.topMargin = dp(20);
            body.addView(limits, limitsParams);
            TextView waiting = lanText(R.string.wallpaper_lan_waiting, 14, Color.rgb(126, 219, 236));
            lanStatus = waiting;
            LinearLayout.LayoutParams waitingParams = new LinearLayout.LayoutParams(-1, -2);
            waitingParams.topMargin = dp(12);
            body.addView(waiting, waitingParams);
            lanDialog = new AlertDialog.Builder(this).setView(body).create();
            lanDialog.setOnDismissListener(dialog -> stopLanReceiver());
            lanDialog.show();
            if (lanDialog.getWindow() != null) {
                lanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                lanDialog.getWindow().setLayout(
                        Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(48)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (IOException | WriterException | OutOfMemoryError error) {
            stopLanReceiver();
            Toast.makeText(this, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private TextView lanText(int resource, int sp, int color) {
        return lanText(getString(resource), sp, color);
    }

    private TextView lanText(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Bitmap qrCode(String value) throws WriterException {
        BitMatrix bits = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 320, 320);
        Bitmap bitmap = Bitmap.createBitmap(bits.getWidth(), bits.getHeight(), Bitmap.Config.RGB_565);
        for (int y = 0; y < bits.getHeight(); y++)
            for (int x = 0; x < bits.getWidth(); x++)
                bitmap.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE);
        return bitmap;
    }

    private static String localAddress() {
        try {
            String fallback = null;
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = nic.getName().toLowerCase(java.util.Locale.ROOT);
                if (!nic.isUp() || nic.isLoopback() || name.startsWith("tun") || name.startsWith("ppp")
                        || name.startsWith("rmnet")) continue;
                for (java.net.InetAddress address : Collections.list(nic.getInetAddresses()))
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en"))
                            return address.getHostAddress();
                        if (fallback == null) fallback = address.getHostAddress();
                    }
            }
            return fallback;
        } catch (Exception ignored) { }
        return null;
    }

    private void onLanUploaded(File file, String mime) {
        if (closed || lanDialog == null || !lanDialog.isShowing()) { file.delete(); return; }
        if (lanStatus != null) lanStatus.setText(R.string.wallpaper_lan_importing);
        decoder.execute(() -> {
            String id;
            try { id = UserWallpaper.importStaged(this, file, mime); }
            catch (Exception | OutOfMemoryError error) { id = null; }
            finally { file.delete(); }
            showImportedWallpaper(id, false);
        });
    }

    private void stopLanReceiver() {
        if (lanReceiver != null) lanReceiver.close();
        lanReceiver = null;
        lanDialog = null;
        lanStatus = null;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onPause() {
        if (lanDialog != null) lanDialog.dismiss();
        stopLanReceiver();
        videoPreview.stop();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ready && selectedMotionResource != 0)
            videoPreview.select(selectedMotionResource);
        else if (ready && userChoices.hasFocus() && UserWallpaper.isVideo(focusedUserId))
            videoPreview.selectFile(UserWallpaper.video(this, focusedUserId));
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
                if (android.os.Build.VERSION.SDK_INT >= 21)
                    view.setTranslationZ(focused ? dp(12) : 0);
                view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
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
            holder.add.setVisibility(group == 2 && position < 2 ? View.VISIBLE : View.GONE);
            if (group == 2 && position < 2) holder.add.setText(position == 0 ? "+" : "⇧");
            holder.name.setText(group == 0 ? BundledWallpapers.TITLES[position]
                    : group == 1 ? BundledWallpapers.MOTION_TITLES[position]
                    : position == 0 ? R.string.wallpaper_user_add
                    : position == 1 ? R.string.wallpaper_lan_send
                    : UserWallpaper.isVideo(userIds.get(position - 2))
                    ? R.string.wallpaper_user_video : R.string.wallpaper_user_image);
            String id = group == 0 ? BundledWallpapers.IDS[position]
                    : group == 1 ? BundledWallpapers.MOTION_IDS[position]
                    : position < 2 ? "" : userIds.get(position - 2);
            holder.selectedBadge.setVisibility(position < 2 && group == 2
                    ? View.GONE : id.equals(selected) ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(view -> {
                if (group == 2 && position == 0) { pickUserWallpaper(); return; }
                if (group == 2 && position == 1) { openLanReceiver(); return; }
                BundledWallpapers.select(PersonalizationActivity.this, id);
                finish();
            });
        }

        @Override public long getItemId(int position) {
            return group == 2 ? position == 0 ? Long.MIN_VALUE
                    : position == 1 ? Long.MIN_VALUE + 1 : userIds.get(position - 2).hashCode() : position;
        }
        @Override public int getItemCount() {
            return group == 0 ? BundledWallpapers.IDS.length
                    : group == 1 ? BundledWallpapers.MOTION_IDS.length : userIds.size() + 2;
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
