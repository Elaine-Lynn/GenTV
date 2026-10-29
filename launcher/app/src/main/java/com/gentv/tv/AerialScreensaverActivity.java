package com.gentv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Home's aerial screensaver. The wake gesture is consumed, never forwarded to the desktop. */
public final class AerialScreensaverActivity extends Activity {
    private AerialPlayer player;
    private ScreensaverInfo info;
    private android.animation.ValueAnimator exitFade;
    private boolean paused;
    private static android.graphics.Bitmap entryFrame;

    static void setEntryFrame(android.graphics.Bitmap frame) { entryFrame = frame; }

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        ImageView fallback = new ImageView(this);
        fallback.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Hold the actual desktop frame until the first aerial frame can fade over it.
        fallback.setImageBitmap(entryFrame);
        entryFrame = null;
        fallback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        player = new AerialPlayer(this, root, fallback);
        info = new ScreensaverInfo(this);
        root.addView(info, new FrameLayout.LayoutParams(-1, -1));
        player.setListener(info);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); paused = false; player.start(); }
    @Override protected void onPause() {
        paused = true;
        info.stop();
        player.pause();
        super.onPause();
        finish();
    }
    // onStop can precede the window animation's final frame. Keep the paused video
    // surface intact until destruction instead of replacing it with the entry image.
    @Override protected void onDestroy() { player.close(); super.onDestroy(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Consume both halves, including repeats, so Back cannot immediately reopen the saver.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) finish();
        return true;
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { finish(); return true; }
    @Override public void onBackPressed() { finish(); }

    static void enterTransition(Activity activity) {
        // The entry snapshot already matches Home. Fading both windows would dim it twice.
        activity.overridePendingTransition(0, 0);
    }

    @Override public void finish() {
        if (isFinishing()) return;
        if (paused || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            finishWithoutAnimation();
            return;
        }
        if (exitFade != null) return;
        player.pause();
        exitFade = android.animation.ValueAnimator.ofFloat(1f, 0f);
        exitFade.setDuration(1000);
        exitFade.setInterpolator(new android.view.animation.LinearInterpolator());
        exitFade.addUpdateListener(animation -> {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.alpha = (float) animation.getAnimatedValue();
            getWindow().setAttributes(attributes);
        });
        exitFade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                finishWithoutAnimation();
            }
        });
        exitFade.start();
    }

    private void finishWithoutAnimation() {
        if (exitFade != null) {
            exitFade.removeAllListeners();
            exitFade.cancel();
            exitFade = null;
        }
        super.finish();
        overridePendingTransition(0, 0);
    }
}
