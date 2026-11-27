package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Surface;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix;
import android.media.MediaPlayer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** One muted, packaged preview at a time. The desktop owns focus and media lifetime. */
final class HomeChannelPreview extends LinearLayout {
    private final List<HomeContentModel.Item> channels;
    private final List<TextView> buttons = new ArrayList<>();
    private final FrameLayout window;
    private final TextView caption;
    private final SharedPreferences preferences;
    private final String preferenceKey;
    private final BooleanSupplier active;
    private MediaPlayer player;
    private TextureView video;
    private int selected;

    HomeChannelPreview(Context context, HomeContentModel model, HomeContentRegistry.Extension extension,
                       BooleanSupplier active, Runnable returnToTile, Runnable topBar) {
        super(context);
        this.active = active;
        channels = model.items;
        preferences = HomeContentRegistry.prefs(context);
        preferenceKey = HomeContentRegistry.channelKey(extension);
        String recent = preferences.getString(preferenceKey, "");
        setOrientation(HORIZONTAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        LinearLayout stage = new LinearLayout(context);
        stage.setOrientation(VERTICAL);
        addView(stage, new LayoutParams(0, -1, 2.1f));
        window = new FrameLayout(context);
        window.setBackground(surface(0xff111921, false));
        CompatApi.clipToOutline(window);
        stage.addView(window, new LayoutParams(-1, 0, 1));
        caption = label(17, Color.WHITE);
        caption.setPadding(0, dp(7), 0, 0);
        stage.addView(caption);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        list.setBackground(surface(0xe9141c24, false));
        list.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams listParams = new LayoutParams(0, -1, 1f);
        listParams.leftMargin = dp(16);
        addView(list, listParams);

        TextView heading = label(16, 0xffe3ecf2);
        heading.setText(model.title);
        list.addView(heading);
        TextView detail = label(13, 0xffb9c7d1);
        detail.setText(model.subtitle);
        list.addView(detail);

        for (int i = 0; i < channels.size(); i++) {
            HomeContentModel.Item channel = channels.get(i);
            if (channel.id.equals(recent)) selected = i;
            int index = i;
            TextView button = label(18, Color.WHITE);
            button.setId(View.generateViewId());
            button.setTag(channel.id);
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setOnFocusChangeListener((view, focused) -> updateButton(index));
            button.setOnClickListener(view -> {
                if (!active.getAsBoolean() || index == selected) return;
                selected = index;
                preferences.edit().putString(preferenceKey, channel.id).apply();
                playSelected();
            });
            button.setOnKeyListener((view, code, event) -> {
                boolean exit = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                        || code == KeyEvent.KEYCODE_DPAD_DOWN && index == channels.size() - 1;
                boolean top = code == KeyEvent.KEYCODE_DPAD_UP && index == 0;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (exit) returnToTile.run();
                    else if (top) topBar.run();
                }
                return exit || top;
            });
            LayoutParams params = new LayoutParams(-1, 0, 1);
            params.topMargin = dp(5);
            list.addView(button, params);
            buttons.add(button);
        }
        for (int i = 0; i < buttons.size(); i++) {
            TextView button = buttons.get(i);
            button.setNextFocusLeftId(button.getId());
            button.setNextFocusRightId(button.getId());
            if (i > 0) button.setNextFocusUpId(buttons.get(i - 1).getId());
            if (i + 1 < buttons.size()) button.setNextFocusDownId(buttons.get(i + 1).getId());
        }
    }

    boolean canMoveDown() { return !buttons.get(buttons.size() - 1).hasFocus(); }

    TextView initialFocus() { return buttons.get(selected); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playSelected();
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    private void playSelected() {
        release();
        HomeContentModel.Item channel = channels.get(selected);
        caption.setText(channel.title + (channel.subtitle.isEmpty() ? "" : "  ·  " + channel.subtitle));
        for (int i = 0; i < buttons.size(); i++) updateButton(i);

        TextureView next = new TextureView(getContext());
        next.setFocusable(false);
        next.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        video = next;
        next.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                if (video != next || !active.getAsBoolean()) return;
                MediaPlayer media = new MediaPlayer();
                player = media;
                Surface surface = new Surface(texture);
                try {
                    media.setSurface(surface);
                    media.setDataSource(getContext(), Uri.parse(channel.video));
                    media.setVolume(0f, 0f);
                    media.setLooping(true);
                    media.setOnPreparedListener(ready -> {
                        if (player != ready || !active.getAsBoolean()) return;
                        fitVideo(next, ready);
                        ready.start();
                    });
                    media.setOnErrorListener((failed, what, extra) -> {
                        if (player == failed) release();
                        return true;
                    });
                    media.prepareAsync();
                } catch (java.io.IOException | RuntimeException failed) {
                    release();
                } finally {
                    surface.release();
                }
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                if (video == next && player != null) fitVideo(next, player);
            }

            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                if (video == next) releasePlayer();
                return true;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        window.addView(next, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }

    private void fitVideo(TextureView view, MediaPlayer media) {
        if (media.getVideoWidth() <= 0 || media.getVideoHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        float aspect = (float) media.getVideoWidth() / media.getVideoHeight();
        float displayAspect = (float) view.getWidth() / view.getHeight();
        Matrix transform = new Matrix();
        transform.setScale(Math.min(1f, aspect / displayAspect), Math.min(1f, displayAspect / aspect),
                view.getWidth() / 2f, view.getHeight() / 2f);
        view.setTransform(transform);
    }

    void release() {
        TextureView old = video;
        video = null;
        releasePlayer();
        if (old != null) window.removeView(old);
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null;
        if (old != null) old.release();
    }

    private void updateButton(int index) {
        TextView button = buttons.get(index);
        boolean current = selected == index;
        button.setSelected(current);
        button.setText((current ? "●  " : "") + channels.get(index).title);
        button.setBackground(surface(current ? 0xff2a414b : Color.TRANSPARENT, button.hasFocus()));
    }

    private TextView label(int size, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setTextColor(color);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    private GradientDrawable surface(int color, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        if (focused) background.setStroke(dp(2), Color.WHITE);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
