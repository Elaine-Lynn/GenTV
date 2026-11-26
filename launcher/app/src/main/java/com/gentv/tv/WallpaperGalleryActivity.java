package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV catalog with independently scrollable, remote-focusable shelves. */
public final class WallpaperGalleryActivity extends Activity {
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private LinearLayout shelves;
    private TextView status;
    private boolean applying;
    private final List<JSONObject> motion = new ArrayList<>();

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F5F7);
        scroll.setClipToPadding(false);
        shelves = new LinearLayout(this);
        shelves.setOrientation(LinearLayout.VERTICAL);
        shelves.setPadding(dp(34), dp(22), dp(34), dp(32));
        scroll.addView(shelves);
        setContentView(scroll);
        shelves.addView(text(getString(R.string.quick_wallpaper), 28, true));
        status = text(copy("正在加载壁纸…", "Loading wallpapers…"), 15, false);
        shelves.addView(status);
        load();
    }

    private void load() {
        worker.execute(() -> {
            try {
                JSONArray groups = TvCloud.json("wallpaper?view=catalog").getJSONArray("categories");
                runOnUiThread(() -> { if (!isDestroyed()) render(groups); });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    status.setText(copy("无法加载在线壁纸", "Online wallpapers unavailable"));
                    LinearLayout row = shelf(copy("图片", "Pictures"));
                    tile(row, new JSONObject(), copy("默认壁纸", "Default wallpaper"), "image", true);
                    localEntry();
                });
            }
        });
    }

    private void render(JSONArray groups) {
        status.setVisibility(View.GONE);
        View first = null;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null || group.optJSONArray("items") == null) continue;
            LinearLayout row = shelf(title(group));
            JSONArray items = group.optJSONArray("items");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                String kind = item.optString("kind");
                if ("video".equals(kind)) motion.add(item);
                View card = tile(row, item, title(item), kind, "default".equals(item.optString("id")));
                if (first == null && card.isFocusable()) first = card;
            }
        }
        localEntry();
        if (first != null) first.requestFocus();
    }

    private LinearLayout shelf(String title) {
        TextView heading = text(title, 22, true);
        heading.setPadding(0, dp(22), 0, dp(10));
        shelves.addView(heading);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFocusable(false);
        LinearLayout row = new LinearLayout(this);
        scroller.addView(row);
        shelves.addView(scroller);
        return row;
    }

    private View tile(LinearLayout row, JSONObject item, String name, String kind, boolean fallback) {
        boolean placeholder = "placeholder".equals(kind);
        boolean shuffle = "shuffle".equals(kind);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(208), -2);
        params.rightMargin = dp(12);
        row.addView(card, params);
        FrameLayout cover = new FrameLayout(this);
        cover.setBackground(surface(placeholder ? 0xFFE6E8ED : 0xFFDCE4EE, Color.TRANSPARENT));
        CompatApi.clipToOutline(cover);
        card.addView(cover, new LinearLayout.LayoutParams(-1, dp(112)));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (fallback) image.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.raw.wallpaper_default_preview));
        if (shuffle || placeholder || "video".equals(kind)) {
            TextView badge = text(shuffle ? "↻" : placeholder ? copy("待上线", "Coming soon") : "▶", shuffle ? 44 : 12, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(surface(0xEEF5F5F7, Color.TRANSPARENT));
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2,
                    shuffle || placeholder ? Gravity.CENTER : Gravity.BOTTOM | Gravity.START);
            position.setMargins(dp(8), 0, 0, dp(8));
            cover.addView(badge, position);
        }
        TextView caption = text(name, 15, false);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        caption.setPadding(0, dp(6), 0, 0);
        card.addView(caption);
        card.setFocusable(!placeholder);
        if (!placeholder) {
            card.setOnFocusChangeListener((v, focused) ->
                    v.setBackground(surface(focused ? Color.WHITE : Color.TRANSPARENT,
                            focused ? 0xFF007AFF : Color.TRANSPARENT)));
            card.setOnClickListener(v -> apply(item, kind, fallback));
        }
        String preview = item.optString("preview");
        if (!preview.isEmpty() && !fallback) worker.execute(() -> {
            try {
                HttpURLConnection connection = TvCloud.open(assetUrl(preview));
                try {
                    byte[] data = TvCloud.read(connection.getInputStream(), 8 * 1024 * 1024);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > 640
                            || options.outHeight / options.inSampleSize > 640) options.inSampleSize *= 2;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    runOnUiThread(() -> { if (!isDestroyed()) image.setImageBitmap(bitmap); });
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { /* Keep the neutral thumbnail surface. */ }
        });
        return card;
    }

    private void apply(JSONObject item, String kind, boolean fallback) {
        if (applying) return;
        if (fallback && item.optString("path").isEmpty()) {
            BundledWallpapers.select(this, "classic");
            finish();
            return;
        }
        boolean shuffle = "shuffle".equals(kind);
        if (shuffle && motion.isEmpty()) return;
        JSONObject chosen = shuffle ? motion.get(new java.util.Random().nextInt(motion.size())) : item;
        applying = true;
        status.setVisibility(View.VISIBLE);
        status.setText(copy("正在下载…", "Downloading…"));
        worker.execute(() -> {
            String selection = null;
            try {
                HttpURLConnection connection = TvCloud.open(assetUrl(chosen.getString("path")));
                try {
                    selection = UserWallpaper.importDownload(getApplicationContext(),
                            connection.getInputStream(), shuffle || "video".equals(kind));
                } finally { connection.disconnect(); }
            } catch (Exception error) {
                if (fallback) selection = "classic";
            }
            String applied = selection;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                applying = false;
                if (applied == null) status.setText(copy("无法下载，请重试", "Download failed. Try again."));
                else {
                    BundledWallpapers.select(this, applied);
                    finish();
                }
            });
        });
    }

    private static String assetUrl(String path) throws IOException {
        if (!path.matches("wallpapers/[A-Za-z0-9_%.-]+")
                || android.net.Uri.decode(path.substring(11)).matches(".*[/\\\\].*"))
            throw new IOException("Invalid wallpaper path");
        return TvCloud.BASE + path;
    }

    private void localEntry() {
        TextView local = text(copy("本地壁纸", "Local wallpapers"), 17, true);
        local.setPadding(dp(12), dp(12), dp(12), dp(12));
        local.setFocusable(true);
        local.setOnFocusChangeListener((v, focused) -> v.setBackground(
                surface(Color.WHITE, focused ? 0xFF007AFF : Color.TRANSPARENT)));
        local.setOnClickListener(v -> startActivity(new Intent(this, PersonalizationActivity.class)));
        shelves.addView(local);
    }

    private String title(JSONObject item) { return copy(item.optString("title"), item.optString("titleEn")); }
    private String copy(String zh, String en) {
        return "zh".equals(CompatApi.locale(getResources().getConfiguration()).getLanguage()) ? zh : en;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF202124);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private GradientDrawable surface(int color, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(2), border);
        return shape;
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
