package com.gentv.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Full-screen line-art rocket that rises from the bottom of the screen on the panel's side, lifts off out of the
 * top once the cleanup finishes and leaves its result as a small notice in the bottom corner. Owns and cancels all
 * animation callbacks.
 */
final class MemoryCleanupView extends View {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private final Typeface fontRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resultGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rocket = new Path();
    private final Path outerFlame = new Path();
    private final Path innerFlame = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismiss;
    private final boolean motion;
    private ValueAnimator animator;
    private ValueAnimator departureAnimator;
    private ValueAnimator resultAnimator;
    private float resultVisibility;
    private boolean hiding;
    private MemoryCleanup.Task cleanup;
    private float departure;
    private long startedAt;
    private boolean finished, disposed, operationDone, departing, success;
    private int exitedProcesses;
    private final int port;
    /** The quick panel sits on the left edge (rtl_menu_bar): the rocket and notice mirror to that side. */
    private final boolean leftEdge;
    /** The quick settings panel: the rocket flies along its horizontal centre. */
    private final View anchor;
    private final float density;
    private final android.graphics.RectF card = new android.graphics.RectF();
    private long reclaimedMb;
    private final Runnable autoClose = this::close;
    private final Runnable beginDeparture = this::finishCleanup;
    private float cachedGlowRadius;
    private float cachedResultGlowR;
    private boolean cachedResultGlowSuccess;

    MemoryCleanupView(Context context, boolean motion, int port, boolean leftEdge, View anchor,
                      Runnable dismiss) {
        super(context);
        this.motion = motion;
        this.port = port;
        this.leftEdge = leftEdge;
        this.anchor = anchor;
        this.dismiss = dismiss;
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnClickListener(v -> { if (finished) close(); });
        setContentDescription(context.getString(R.string.memory_cleaning));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        // Normalised geometry retains the approved thin-outline silhouette at every resolution.
        rocket.moveTo(0, -110);
        rocket.cubicTo(-43, -65, -40, 12, -20, 62);
        rocket.lineTo(20, 62);
        rocket.cubicTo(40, 12, 43, -65, 0, -110);
        rocket.moveTo(-32, 12); rocket.lineTo(-53, 43); rocket.lineTo(-53, 76);
        rocket.lineTo(-20, 52);
        rocket.moveTo(32, 12); rocket.lineTo(53, 43); rocket.lineTo(53, 76);
        rocket.lineTo(20, 52);
        rocket.addCircle(0, -42, 15, Path.Direction.CW);
        rocket.moveTo(-15, 63); rocket.lineTo(-14, 73); rocket.lineTo(14, 73); rocket.lineTo(15, 63);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startedAt = SystemClock.uptimeMillis();
        cleanup = MemoryCleanup.start(getContext(), port, (ok, freed, exited) -> handler.post(() -> {
            if (disposed) return;
            operationDone = true;
            success = ok;
            reclaimedMb = freed;
            exitedProcesses = exited;
            finishCleanup();
        }));
        if (motion) {
            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(2200);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> invalidate());
            animator.start();
        }
    }

    private void finishCleanup() {
        if (disposed || finished || departing || !operationDone) return;
        long remaining = motion ? 650 - (SystemClock.uptimeMillis() - startedAt) : 0;
        if (remaining > 0) {
            handler.removeCallbacks(beginDeparture);
            handler.postDelayed(beginDeparture, remaining);
            return;
        }
        departing = true;
        if (animator != null) animator.cancel();
        if (!motion) {
            departure = 1f;
            showResult();
            handler.postDelayed(autoClose, 1400);
            return;
        }
        departureAnimator = ValueAnimator.ofFloat(0f, 1f);
        departureAnimator.setDuration(success ? 1100 : 320);
        departureAnimator.setInterpolator(new LinearInterpolator());
        departureAnimator.addUpdateListener(a -> {
            departure = (float) a.getAnimatedValue();
            if (departure >= .55f && !finished) showResult();
            invalidate();
        });
        departureAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (disposed || hiding) return;
                if (!finished) showResult();
                handler.postDelayed(autoClose, 1400);
            }
        });
        departureAnimator.start();
    }

    private void showResult() {
        finished = true;
        String result = resultText();
        setContentDescription(result);
        announceForAccessibility(result);
        animateResult(1f, 280, () -> { });
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float unit = 1.6f * density;
        // Centred under the quick settings panel; both share the scrim's coordinates.
        float cx = anchor != null && anchor.getWidth() > 0
                ? anchor.getLeft() + anchor.getWidth() * 0.5f
                : leftEdge ? 242f * density : w - 242f * density;
        // Resting on the bottom edge: the body sits just above it, the plume runs off the screen.
        float restY = h - 34f * density - 73f * unit;

        if (!finished || departing && departure < 1f) {
            float seconds = motion ? (SystemClock.uptimeMillis() - startedAt) / 1000f : 0f;
            float t = departing && success ? .30f + .70f * departure : .30f;
            // A successful launch stays opaque until it has left the screen; a failure fades where it stands.
            float rocketAlpha = success ? 1f - smoothStep(.90f, 1f, departure)
                    : 1f - smoothStep(.10f, 1f, departure);
            if (rocketAlpha < .99f) {
                CompatApi.saveLayerAlpha(canvas, w, h, Math.round(rocketAlpha * 255f));
            } else canvas.save();

            // Smooth C2 envelopes — zero abrupt slope kinks between ignition, spooling and liftoff.
            float ignite = smootherStep(0.00f, 0.22f, t);
            float spool = smootherStep(0.16f, 0.54f, t);
            float surge = smootherStep(0.42f, 0.88f, t);
            float depart = smootherStep(0.36f, 1.00f, t);
            float departEase = (float) Math.pow(depart, 2.25);

            // Rises in from below the screen, then a subtle thrust compression before climbing out of the top.
            float entry = motion ? 1f - smootherStep(0f, .55f, seconds) : 0f;
            float crouch = departing && success
                    ? 6f * unit * (float) Math.sin(Math.PI * smoothStep(0f, .28f, departure)) : 0f;
            float travel = restY + 340f * unit;
            float altitude = restY + entry * (h - restY + 120f * unit) + crouch
                    - (departing && success ? travel * departEase : 0f);

            // Slow breathing while work runs; no launch until the real cleanup callback.
            float shakeEnv = motion ? 1f - smoothStep(0f, .36f, departure) : 0f;
            float shake = motion
                    ? (float) Math.sin(seconds * TWO_PI * .42f) * .65f * shakeEnv
                    : 0f;

            float cy = altitude + (motion
                    ? (float) Math.sin(seconds * TWO_PI * .55f) * 3f * unit * shakeEnv : 0f);

            // Soft cyan glow around the plume.
            float ambientAlpha = (0.15f + 0.22f * ignite + 0.14f * spool) * (1f - 0.72f * smoothStep(0.70f, 1.00f, t));
            drawAmbientGlow(canvas, cx, cy + 75f * unit, 230f * unit, ambientAlpha);

            canvas.save();
            canvas.translate(cx + shake * unit, cy);
            canvas.scale(unit, unit);
            canvas.rotate(shake * 0.35f);

            // Rocket silhouette: soft outer bloom + crisp thin outline.
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeJoin(Paint.Join.ROUND);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeWidth(7.5f);
            ink.setColor(0x2E42DFF5);
            canvas.drawPath(rocket, ink);
            ink.setStrokeWidth(1.45f);
            ink.setColor(0xE8DDFEFF);
            canvas.drawPath(rocket, ink);

            // Organic nozzle-anchored thruster flame (dynamic Bezier, never distorting stroke width).
            drawOrganicFlame(canvas, seconds * .24f, ignite, spool, surge);

            // Subtle vertical slipstream lines sweeping down as the rocket climbs.
            float streakAlpha = departing && success ? .40f * surge : 0f;
            if (streakAlpha > 0.02f) {
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(0.85f);
                for (int i = 0; i < 10; i++) {
                    float side = (i < 5) ? -1f : 1f;
                    int lane = i % 5;
                    float x = side * (46f + lane * 20f);
                    float y = 76f + ((t * 760f + i * 61f) % 210f);
                    float len = 10f + spool * 14f + surge * 78f;
                    float laneFade = 1f - lane * 0.14f;
                    ink.setColor(android.graphics.Color.argb(
                            Math.round(255f * streakAlpha * laneFade), 118, 232, 248));
                    canvas.drawLine(x, y, x, y + len, ink);
                }
            }

            canvas.restore();

            // While cleaning runs, a quiet caption beside the rocket on the screen's inner side.
            float captionAlpha = (motion ? .85f * smoothStep(.25f, .55f, seconds) : .85f)
                    * (1f - smoothStep(0f, .25f, departure));
            if (captionAlpha > .01f) {
                ink.setStyle(Paint.Style.FILL);
                ink.setColor(android.graphics.Color.argb(Math.round(captionAlpha * 255f), 220, 236, 248));
                ink.setTextSize(30f * density);
                ink.setTypeface(fontRegular);
                ink.setTextAlign(leftEdge ? Paint.Align.LEFT : Paint.Align.RIGHT);
                float captionX = leftEdge ? cx + 70f * unit : cx - 70f * unit;
                canvas.drawText(getResources().getString(R.string.memory_cleaning), captionX,
                        h - 48f * density, ink);
            }
            canvas.restore();
        }
        if (finished) drawResult(canvas, w, h);
    }

    /**
     * Draws a nozzle-anchored dual-layer thruster plume whose length, belly width and tip sway
     * breathe through smooth multi-harmonic waves with subtle volume conservation (squash & stretch).
     */
    private void drawOrganicFlame(Canvas canvas, float t, float ignite, float spool, float surge) {
        if (ignite <= 0.01f) return;

        // Multi-harmonic waves well below 60Hz Nyquist so breathing is silky rather than jittery.
        float breathSlow = (float) Math.sin(t * TWO_PI * 2.8f);
        float breathMid = (float) Math.sin(t * TWO_PI * 5.9f + 1.15f);
        float breathTip = (float) Math.sin(t * TWO_PI * 9.7f + 2.35f);

        float lengthWave = 0.54f * breathSlow + 0.30f * breathMid + 0.16f * breathTip;
        // Phase-lagged inverse response: as the plume elongates, its belly tapers slightly; as it retracts, it blooms.
        float widthWave = -0.45f * (float) Math.sin(t * TWO_PI * 2.8f - 0.45f)
                + 0.35f * (float) Math.cos(t * TWO_PI * 5.9f + 0.8f);
        float tipSway = motion
                ? ((float) Math.sin(t * TWO_PI * 3.7f + 0.6f) * 2.4f
                + (float) Math.sin(t * TWO_PI * 7.3f) * 1.1f) * (0.45f + 0.55f * spool)
                : 0f;

        // Plume breathes visibly during ignition/spooling, then elongates gracefully on liftoff.
        float pulseAmp = motion ? (0.22f * ignite * (1f - 0.28f * surge)) : 0f;
        float baseLen = 52f * ignite + 62f * spool + 128f * surge;
        float outerLen = Math.max(8f, baseLen * (1f + pulseAmp * lengthWave));

        // Nozzle mouth stays locked inside the ±14 nozzle bell while belly blooms in a fluid teardrop.
        float rootHalfW = 7.4f + 3.4f * ignite + 1.6f * surge;
        float baseBellyW = 14.5f * ignite + 9.0f * spool + 9.5f * surge;
        float bellyHalfW = Math.max(rootHalfW * 1.15f, baseBellyW * (1f + 0.16f * ignite * widthWave));

        buildPlumePath(outerFlame, 75f, rootHalfW, bellyHalfW, outerLen, tipSway);

        // Inner plasma core breathes slightly out of phase for organic depth.
        float innerWave = 0.55f * (float) Math.sin(t * TWO_PI * 3.4f + 0.85f)
                + 0.45f * (float) Math.sin(t * TWO_PI * 7.1f + 2.0f);
        float innerLen = Math.max(6f, outerLen * (0.56f + 0.08f * surge) * (1f + 0.16f * innerWave));
        float innerRootW = rootHalfW * 0.56f;
        float innerBellyW = bellyHalfW * (0.50f + 0.08f * (1f - innerWave));
        buildPlumePath(innerFlame, 75f, innerRootW, innerBellyW, innerLen, tipSway * 0.45f);

        int alphaOuter = Math.round(255f * Math.min(1f, ignite * 1.15f));
        int alphaGlow = Math.round((46f + 32f * surge) * ignite);

        // 1. Soft cyan outer bloom halo
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeWidth(7.0f + 2.5f * surge);
        ink.setColor(android.graphics.Color.argb(alphaGlow, 66, 223, 245));
        canvas.drawPath(outerFlame, ink);

        // 2. Subtle translucent plasma body tint
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(android.graphics.Color.argb(Math.round((28f + 24f * surge) * ignite), 120, 238, 255));
        canvas.drawPath(outerFlame, ink);

        // 3. Crisp outer flame line-art contour (uniform stroke thickness)
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.35f + 0.35f * surge);
        ink.setColor(android.graphics.Color.argb(alphaOuter, 154, 247, 255));
        canvas.drawPath(outerFlame, ink);

        // 4. Luminous inner white-cyan core
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(android.graphics.Color.argb(Math.round((56f + 44f * surge) * ignite), 218, 252, 255));
        canvas.drawPath(innerFlame, ink);

        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.05f);
        ink.setColor(android.graphics.Color.argb(Math.round(215f * ignite), 232, 254, 255));
        canvas.drawPath(innerFlame, ink);
    }

    /** Constructs a teardrop exhaust plume anchored at y = nozzleY with natural waist & pointed tail. */
    private static void buildPlumePath(Path path, float nozzleY, float rootHalfW,
                                       float bellyHalfW, float length, float tipSway) {
        path.reset();
        float bellyY = nozzleY + length * 0.24f;
        float taperY = nozzleY + length * 0.66f;
        float tipY = nozzleY + length;
        path.moveTo(-rootHalfW, nozzleY);
        path.cubicTo(
                -bellyHalfW, bellyY,
                -bellyHalfW * 0.52f + tipSway * 0.45f, taperY,
                tipSway, tipY
        );
        path.cubicTo(
                bellyHalfW * 0.52f + tipSway * 0.45f, taperY,
                bellyHalfW, bellyY,
                rootHalfW, nozzleY
        );
        path.close();
    }

    private void drawAmbientGlow(Canvas canvas, float cx, float cy, float radius, float alpha) {
        if (alpha <= 0.01f || radius <= 1f) return;
        if (cachedGlowRadius != radius || glowPaint.getShader() == null) {
            cachedGlowRadius = radius;
            glowPaint.setShader(new RadialGradient(
                    0, 0, radius,
                    new int[]{0x5C48D8F5, 0x222690C8, 0x00000000},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP));
        }
        glowPaint.setAlpha(Math.round(Math.min(1f, alpha) * 255f));
        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0, 0, radius, glowPaint);
        canvas.restore();
    }

    /** Draws the completion notice as a small glass card in the bottom corner on the panel's side. */
    private void drawResult(Canvas canvas, float w, float h) {
        int alpha = Math.round(resultVisibility * 255f);
        if (alpha <= 1) return;
        // Twice the size of a regular notice so it reads from the sofa.
        float d = density * 2f;
        String headline = resultText();
        String detail = success
                ? getResources().getString(R.string.memory_cleanup_scope, exitedProcesses)
                : getResources().getString(R.string.memory_cleanup_retry);

        float badgeR = 15f * d;
        float padStart = 16f * d, gap = 12f * d, padEnd = 20f * d;
        float maxText = Math.min(w * 0.5f, 420f * d);
        float headlineSize = fitSize(headline, 17f * d, maxText, fontRegular);
        float detailSize = fitSize(detail, 12.5f * d, maxText, fontRegular);
        ink.setTypeface(fontRegular);
        ink.setTextSize(headlineSize);
        float textW = ink.measureText(headline);
        ink.setTextSize(detailSize);
        textW = Math.max(textW, ink.measureText(detail));
        float cardW = padStart + badgeR * 2f + gap + textW + padEnd;
        float cardH = 68f * d;
        float lift = (1f - resultVisibility) * 14f * d;
        float right = leftEdge ? 28f * density + cardW : w - 28f * density;
        float bottom = h - 26f * density + lift;
        card.set(right - cardW, bottom - cardH, right, bottom);

        CompatApi.saveLayerAlpha(canvas, w, h, alpha);

        float radius = 18f * d;
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(0xF0161A23);
        canvas.drawRoundRect(card, radius, radius, ink);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1f, 0.75f * d));
        ink.setColor(0x30FFFFFF);
        canvas.drawRoundRect(card, radius, radius, ink);

        // Medallion circle + crisp checkmark / notice glyph.
        float bx = card.left + padStart + badgeR;
        float by = card.centerY();
        float g = badgeR / 28f;
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(success ? 0x208BEFF2 : 0x20FFCE91);
        canvas.drawCircle(bx, by, badgeR, ink);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStrokeWidth(1.5f * d);
        ink.setColor(success ? 0xFF8BEFF2 : 0xFFFFCE91);
        canvas.drawCircle(bx, by, badgeR, ink);
        ink.setStrokeWidth(2.2f * d);
        if (success) {
            canvas.drawLine(bx - 10f * g, by + 1f * g, bx - 2.5f * g, by + 8.5f * g, ink);
            canvas.drawLine(bx - 2.5f * g, by + 8.5f * g, bx + 11f * g, by - 7.5f * g, ink);
        } else {
            canvas.drawLine(bx, by - 10f * g, bx, by + 2f * g, ink);
            canvas.drawPoint(bx, by + 9f * g, ink);
        }

        float tx = bx + badgeR + gap;
        drawStartLine(canvas, headline, tx, by - 2f * d, headlineSize, 0xFFF5F7FA);
        drawStartLine(canvas, detail, tx, by + 17f * d, detailSize, 0xBFD8E1EC);

        canvas.restore();
    }

    private float fitSize(String text, float size, float maxWidth, Typeface typeface) {
        ink.setTypeface(typeface);
        ink.setTextSize(size);
        float width = ink.measureText(text);
        return width > maxWidth && width > 0 ? size * maxWidth / width : size;
    }

    private void drawStartLine(Canvas canvas, String text, float x, float y, float size, int color) {
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(color);
        ink.setTextAlign(Paint.Align.LEFT);
        ink.setTypeface(fontRegular);
        ink.setTextSize(size);
        canvas.drawText(text, x, y, ink);
    }

    private void animateResult(float target, long duration, Runnable end) {
        if (resultAnimator != null) {
            resultAnimator.removeAllListeners();
            resultAnimator.cancel();
        }
        if (!motion) {
            resultVisibility = target;
            invalidate();
            end.run();
            return;
        }
        resultAnimator = ValueAnimator.ofFloat(resultVisibility, target);
        resultAnimator.setDuration(duration);
        resultAnimator.setInterpolator(CompatApi.easeOut());
        resultAnimator.addUpdateListener(value -> {
            resultVisibility = (float) value.getAnimatedValue();
            invalidate();
        });
        resultAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!disposed) end.run();
            }
        });
        resultAnimator.start();
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float u = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return u * u * (3f - 2f * u);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        float u = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return u * u * u * (u * (u * 6f - 15f) + 10f);
    }

    private String resultText() {
        return getResources().getString(success ? R.string.memory_cleaned : R.string.memory_cleanup_failed, reclaimedMb);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (finished) close();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    boolean succeeded() { return success; }

    void close() {
        if (disposed || hiding) return;
        if (finished) {
            hiding = true;
            handler.removeCallbacks(autoClose);
            departure = 1f;
            if (departureAnimator != null) {
                departureAnimator.removeAllListeners();
                departureAnimator.cancel();
            }
            animateResult(0f, 260, this::dismissNow);
        } else {
            dismissNow();
        }
    }

    private void dismissNow() {
        dispose();
        dismiss.run();
    }

    void dispose() {
        disposed = true;
        if (cleanup != null) cleanup.cancel();
        handler.removeCallbacksAndMessages(null);
        if (animator != null) { animator.cancel(); animator.removeAllUpdateListeners(); }
        if (departureAnimator != null) {
            departureAnimator.removeAllListeners();
            departureAnimator.cancel();
            departureAnimator.removeAllUpdateListeners();
        }
        if (resultAnimator != null) {
            resultAnimator.removeAllListeners();
            resultAnimator.cancel();
        }
    }

    @Override protected void onDetachedFromWindow() { dispose(); super.onDetachedFromWindow(); }
}
