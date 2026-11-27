package com.gentv.game;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Log;
import com.gentv.tv.GameRuntimeEntry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The downloaded foundation owns the catalog, shared browser and all child plugin lifecycle. */
public final class GameCenterScreen implements GameRuntimeEntry {
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private Activity host;
    private FrameLayout root;
    private TextView status;
    private GridLayout cards;
    private GameContentRepository repository;
    private BrowserGameScreen browser;
    private boolean visible, destroyed, busy;
    private boolean consumeGameBackUp;
    private String selected;
    private boolean chinese;

    @Override public void create(Activity activity) {
        host = activity;
        chinese = "zh".equals(Locale.getDefault().getLanguage());
        buildUi();
        worker.execute(() -> {
            try {
                GameNetwork network = new GameNetwork(host.getIntent().getStringExtra(API_BASE_EXTRA),
                        host.getIntent().getStringExtra(CONTENT_BASE_EXTRA));
                repository = new GameContentRepository(host, network, host.getIntent().getLongExtra(BASE_VERSION_EXTRA, 0));
                List<GameListing> cached = GameListing.parse(repository.cachedCatalog());
                post(() -> render(cached));
                try {
                    List<GameListing> fresh = GameListing.parse(repository.refreshCatalog());
                    post(() -> { if (!busy && browser == null) render(fresh); });
                } catch (Exception offline) {
                    Log.i("GenTVGame", "Using saved game catalog", offline);
                    post(() -> { if (!busy && browser == null) status.setText(text("已显示本地游戏列表 · 选择游戏检查更新", "Saved catalog · Select a game to check for updates")); });
                }
            } catch (Exception failed) { post(() -> status.setText(text("游戏列表加载失败，请返回重试", "Unable to load games. Please try again."))); }
        });
    }

    private void buildUi() {
        root = new FrameLayout(host);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff06101c, 0xff183646, 0xff0b1426}));
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(32), dp(48), dp(28));
        TextView brand = label("GenTV  /  " + text("游戏", "Games"), 16, false);
        brand.setTextColor(0xff9bb7c9); content.addView(brand);
        TextView title = label(text("游戏中心", "Games"), 34, true);
        LinearLayout.LayoutParams heading = new LinearLayout.LayoutParams(-1, -2);
        heading.topMargin = dp(12); content.addView(title, heading);
        status = label(text("选择游戏后下载 · 所有游戏共用浏览器内核", "Download games on demand · One shared browser engine"), 16, false);
        status.setTextColor(0xffb6c6d6);
        status.setPadding(0, dp(8), 0, dp(4)); content.addView(status);
        ScrollView scroll = new ScrollView(host);
        scroll.setClipToPadding(false); scroll.setClipChildren(false);
        scroll.setPadding(dp(12), dp(24), dp(12), dp(24));
        cards = new GridLayout(host); cards.setColumnCount(3);
        cards.setClipChildren(false); cards.setClipToPadding(false);
        scroll.addView(cards); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(label(text("确定：打开游戏    返回：回到桌面", "OK: Open game    Back: Return to launcher"), 14, false));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1)); host.setContentView(root);
    }

    private void render(List<GameListing> games) {
        cards.removeAllViews();
        int width = Math.max(dp(160), (host.getResources().getDisplayMetrics().widthPixels - dp(168)) / 3);
        for (GameListing game : games) {
            LinearLayout card = new LinearLayout(host);
            card.setTag(game.id);
            card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(24), dp(20), dp(24), dp(20));
            TextView emblem = label("▶", 34, true); emblem.setTextColor(0xff9bd6ed); card.addView(emblem);
            TextView name = label(chinese ? game.title : game.titleEn, 24, true); card.addView(name);
            TextView description = label(chinese ? game.description : game.descriptionEn, 14, false);
            description.setPadding(0, dp(8), 0, 0); card.addView(description);
            card.setBackground(background(false)); card.setFocusable(true); card.setClickable(true);
            card.setOnFocusChangeListener((view, focus) -> {
                card.setBackground(background(focus));
                int color = focus ? 0xff101b27 : Color.WHITE;
                name.setTextColor(color); description.setTextColor(focus ? 0xff465263 : 0xffadbfce);
                emblem.setTextColor(focus ? 0xff246b88 : 0xff9bd6ed);
                card.animate().scaleX(focus ? 1.04f : 1f).scaleY(focus ? 1.04f : 1f).setDuration(150).start();
                if (focus) selected = game.id;
            });
            card.setOnClickListener(view -> open(game));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width; params.height = dp(190); params.setMargins(dp(6), dp(8), dp(12), dp(20));
            cards.addView(card, params);
            if (selected == null || selected.equals(game.id)) card.requestFocus();
        }
        if (games.isEmpty()) status.setText(text("暂时没有上架的游戏", "No games available yet"));
    }

    private void open(GameListing game) {
        if (busy || repository == null) return;
        busy = true;
        status.setText(text("正在检查游戏组件…", "Checking game plugin…"));
        worker.execute(() -> {
            try {
                GameContentRepository.Loaded loaded = repository.open(game, (count, total) -> post(() ->
                        status.setText(text("正在下载游戏组件  ", "Downloading game plugin  ") + count * 100 / total + "%")));
                post(() -> {
                    busy = false;
                    try {
                        browser = new BrowserGameScreen(loaded, this::returnToCenter);
                        browser.create(host);
                        if (visible) browser.start();
                    } catch (Throwable failed) {
                        Log.e("GenTVGame", "Unable to start game", failed);
                        returnToCenter(); status.setText(text("游戏启动失败，请重试", "Unable to start game. Please retry."));
                    }
                });
            } catch (Exception failed) {
                Log.w("GenTVGame", "Game download or validation failed", failed);
                post(() -> { busy = false; status.setText(text("游戏组件暂不可用，按确定重试", "Game plugin unavailable. Press OK to retry.")); });
            }
        });
    }

    private void returnToCenter() {
        if (browser != null) { if (visible) browser.stop(); browser.destroy(); browser = null; }
        host.setContentView(root);
        host.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        for (int i = 0; i < cards.getChildCount(); i++) {
            View card = cards.getChildAt(i);
            if (selected == null || selected.equals(card.getTag())) {
                card.requestFocus();
                root.post(() -> { if (!destroyed && browser == null) card.requestFocus(); });
                break;
            }
        }
        status.setText(text("选择游戏后检查更新并打开", "Select a game to check for updates and play"));
    }
    @Override public void start() { visible = true; if (browser != null) browser.start(); }
    @Override public void stop() { visible = false; if (browser != null) browser.stop(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // A game may return to the catalog on BACK down. Its matching up must not exit the catalog.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && consumeGameBackUp) {
                consumeGameBackUp = false; return true;
            }
            if (browser != null && event.getAction() == KeyEvent.ACTION_DOWN) consumeGameBackUp = true;
        }
        if (browser != null) return browser.dispatchKeyEvent(event);
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) host.finish();
            return true;
        }
        return false;
    }
    @Override public void destroy() {
        destroyed = true; worker.shutdownNow();
        if (browser != null) { browser.destroy(); browser = null; }
    }
    private void post(Runnable task) { host.runOnUiThread(() -> { if (!destroyed && !host.isFinishing()) task.run(); }); }
    private String text(String zh, String en) { return chinese ? zh : en; }
    private int dp(int value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(host); view.setText(text); view.setTextSize(size); view.setTextColor(Color.WHITE);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private GradientDrawable background(boolean focus) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                focus ? new int[]{0xfff9fcff, 0xffd8e5f0} : new int[]{0xff294b60, 0xff142c41});
        background.setCornerRadius(dp(22)); background.setStroke(dp(1), focus ? Color.WHITE : 0x657fa5bf);
        return background;
    }
}
