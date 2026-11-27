package com.gentv.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Cohesive photographic iPhone Weather inspired dynamic atmospheric views for Gen Weather:
 * - {@link BackdropView}:
 *   - Unified soft-photographic atmospheric visual language across all 8 weather moods.
 *   - Clear Day: pure Rayleigh-scattering azure gradient + procedurally baked feathered solar
 *     radiance bloom (zero hard polygon beams or harsh rings) with glacial breathing rotation.
 *   - Clear Night: deep cosmic indigo gradient + delicate fBm atmospheric night-haze veil +
 *     photographic pinprick multi-temperature stars with soft micro-halos + softly integrated
 *     textured lunar crescent nestled inside silver-blue moonlight mist.
 *   - Clouds / Overcast / Fog / Rain / Storm / Snow: high-resolution 360x200 6-octave wispy fBm
 *     cloud sheets with serene drift, tapered glistening rain streaks, diffused cloud lightning,
 *     and soft photographic snowfall & bokeh.
 * - {@link HourlyCurveView}: hairline luminous temperature curve with animated glowing focus node.
 * - {@link TempRangeBarView}: glowing cyan-to-amber range bar with focus elevation highlight.
 * - {@link FadingRuleView}: dissolving 1px atmospheric hairline separator.
 */
final class WeatherCanvasViews {
    private WeatherCanvasViews() {}

    static final class BackdropView extends View {
        private static final int STAR_COUNT = 112;
        private static final int CLOUD_INSTANCES = 10;
        private static final int RAIN_COUNT = 135;
        private static final int SPLASH_COUNT = 16;
        private static final int SNOW_COUNT = 105;
        private static final float TRANSITION_MS = 700f;
        private static final float CITY_REVEAL_MS = 560f;

        private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint celestialGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint secondaryGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint horizonScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sunDiscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint splashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lightningGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrollDimPaint = new Paint();

        private final RectF dstRect = new RectF();

        // High-resolution photographic procedural sprites (baked once asynchronously)
        private volatile Bitmap[] softCloudSprites;
        private volatile Bitmap cirrusSprite;
        private volatile Bitmap solarRadianceBitmap;
        private volatile Bitmap realisticMoonBitmap;
        private final Bitmap bokehOrbBitmap;
        private final Bitmap raindropBitmap;

        private final float[] starX = new float[STAR_COUNT];
        private final float[] starY = new float[STAR_COUNT];
        private final float[] starRadius = new float[STAR_COUNT];
        private final float[] starPhase = new float[STAR_COUNT];
        private final int[] starColor = new int[STAR_COUNT];
        private final boolean[] starBright = new boolean[STAR_COUNT];

        // Multi-layer slow-drifting cloud instances
        private final float[] cloudX = new float[CLOUD_INSTANCES];
        private final float[] cloudY = new float[CLOUD_INSTANCES];
        private final float[] cloudWFrac = new float[CLOUD_INSTANCES];
        private final float[] cloudSpeed = new float[CLOUD_INSTANCES];
        private final float[] cloudDepth = new float[CLOUD_INSTANCES];
        private final float[] cloudBreathPhase = new float[CLOUD_INSTANCES];
        private final int[] cloudVariant = new int[CLOUD_INSTANCES];

        // 3-depth parallax rain
        private final float[] rainX = new float[RAIN_COUNT];
        private final float[] rainY = new float[RAIN_COUNT];
        private final float[] rainDepth = new float[RAIN_COUNT];
        private final float[] rainSpeed = new float[RAIN_COUNT];

        // Horizon rain splashes
        private final float[] splashX = new float[SPLASH_COUNT];
        private final float[] splashPhase = new float[SPLASH_COUNT];

        // 3-depth parallax snow
        private final float[] snowX = new float[SNOW_COUNT];
        private final float[] snowY = new float[SNOW_COUNT];
        private final float[] snowDepth = new float[SNOW_COUNT];
        private final float[] snowPhase = new float[SNOW_COUNT];

        private final float density;
        private long sceneOriginMs = SystemClock.uptimeMillis();
        private long revealStartMs = SystemClock.uptimeMillis();
        private float cityCloudPhaseOffset = 0f;
        private String currentCityId = "";
        private int weatherCode = -1;
        private boolean isDay = true;
        private boolean sceneReady = false;
        private float scrollDimFraction = 0f;
        private boolean animating;

        // Smooth atmospheric transition state
        private int[] fromSky = neutralSkyGradient();
        private int[] toSky = neutralSkyGradient();
        private final int[] curSky = new int[4];
        private int[] fromGlow = new int[]{0x00000000, 0x00000000, 0x00000000, 0x00000000};
        private int[] toGlow = new int[]{0x00000000, 0x00000000, 0x00000000, 0x00000000};
        private final int[] curGlow = new int[4];
        private float fromStarVis = 0f, toStarVis = 0f;
        private float fromSunVis = 0f, toSunVis = 0f;
        private float fromMoonVis = 0f, toMoonVis = 0f;
        private float fromCloudVis = 0f, toCloudVis = 0f;
        private int activeCloudTier = 0;
        private float fromRainVis = 0f, toRainVis = 0f;
        private float fromStormVis = 0f, toStormVis = 0f;
        private float fromSnowVis = 0f, toSnowVis = 0f;
        private float fromFogVis = 0f, toFogVis = 0f;
        private float fromDayBlend = 0.5f, toDayBlend = 0.5f;
        private long transitionStartMs = 0L;
        private boolean transitioning = false;

        private final Runnable frameTicker = new Runnable() {
            @Override
            public void run() {
                if (!animating) return;
                invalidate();
                postDelayed(this, 30L);
            }
        };

        BackdropView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            splashPaint.setStyle(Paint.Style.STROKE);
            splashPaint.setStrokeWidth(Math.max(1f, 0.9f * density));

            bokehOrbBitmap = bakeSoftOrbBitmap(64, 0.48f);
            raindropBitmap = bakeTaperedRaindropBitmap();
            System.arraycopy(toSky, 0, curSky, 0, 4);
            System.arraycopy(toGlow, 0, curGlow, 0, 4);
            seedParticles();
            bakeRealisticSpritesAsync();
        }

        private void bakeRealisticSpritesAsync() {
            Thread t = new Thread(() -> {
                Bitmap[] clouds = new Bitmap[]{
                        bakeIphoneCloudSheet(360, 200, 17),
                        bakeIphoneCloudSheet(360, 200, 53),
                        bakeIphoneCloudSheet(360, 200, 89)
                };
                Bitmap cirrus = bakeRealisticCirrus(360, 120, 131);
                Bitmap solar = bakeFeatheredSolarRadiance(280);
                Bitmap moon = bakeAtmosphericMoon(112);
                softCloudSprites = clouds;
                cirrusSprite = cirrus;
                solarRadianceBitmap = solar;
                realisticMoonBitmap = moon;
                postInvalidate();
            }, "gen-weather-textures");
            t.setDaemon(true);
            t.start();
        }

        private static Bitmap bakeSoftOrbBitmap(int size, float coreStop) {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float r = size * 0.5f;
            p.setShader(new RadialGradient(
                    r, r, r,
                    new int[]{0xFFFFFFFF, 0xBBFFFFFF, 0x36FFFFFF, 0x00FFFFFF},
                    new float[]{0f, coreStop, 0.72f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(r, r, r, p);
            return bmp;
        }

        /**
         * Pre-bakes a photographic, feather-soft solar scattering radiance bloom with ultra-subtle
         * organic angular light diffusion (no hard triangular rays or artificial polygon edges).
         */
        private static Bitmap bakeFeatheredSolarRadiance(int size) {
            int[] pixels = new int[size * size];
            float half = (size - 1) * 0.5f;
            for (int y = 0; y < size; y++) {
                float ny = (y - half) / half;
                for (int x = 0; x < size; x++) {
                    float nx = (x - half) / half;
                    float r = (float) Math.sqrt(nx * nx + ny * ny);
                    if (r >= 1.0f) continue;

                    double angle = Math.atan2(ny, nx);
                    // Ultra-subtle organic atmospheric light shafts baked smoothly in polar space
                    float shaft = 0.86f
                            + 0.08f * (float) Math.sin(angle * 5.0 + 0.4)
                            + 0.05f * (float) Math.cos(angle * 9.0 - 1.1)
                            + 0.04f * (float) Math.sin(angle * 13.0 + 2.0);
                    float radial = (float) Math.pow(1f - r, 2.55);
                    float core = (float) Math.exp(-r * r * 14.0);
                    float val = Math.min(1f, radial * shaft * 0.78f + core * 0.55f);

                    // Warm sunlit cream core transitioning smoothly to pale sky-gold glow
                    int cr = 255;
                    int cg = Math.round(lerp(244f, 254f, core));
                    int cb = Math.round(lerp(212f, 246f, core));
                    int ca = Math.round(val * 220f);
                    pixels[y * size + x] = Color.argb(ca, cr, cg, cb);
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, size, 0, 0, size, size);
            return bmp;
        }

        private static Bitmap bakeTaperedRaindropBitmap() {
            int w = 12, h = 96;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(
                    w * 0.5f, 0, w * 0.5f, h,
                    new int[]{0x00E4F2FF, 0x3CE4F2FF, 0xB8EEF7FF, 0xFFFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.28f, 0.78f, 0.94f, 1f},
                    Shader.TileMode.CLAMP));
            Path drop = new Path();
            drop.moveTo(w * 0.5f, 0);
            drop.lineTo(w * 0.76f, h * 0.90f);
            drop.quadTo(w * 0.5f, h, w * 0.24f, h * 0.90f);
            drop.close();
            c.drawPath(drop, p);
            return bmp;
        }

        /**
         * Fine, silky, photographic iPhone-Weather-style cloud veils:
         * 6-octave wispy fBm with very soft Gaussian-like feathered edges.
         */
        private static Bitmap bakeIphoneCloudSheet(int w, int h, int seed) {
            int[] pixels = new int[w * h];
            float[] densityField = new float[w * h];

            for (int y = 0; y < h; y++) {
                float ny = (y / (float) (h - 1)) * 2f - 1f;
                for (int x = 0; x < w; x++) {
                    float nx = (x / (float) (w - 1)) * 2f - 1f;
                    float r2 = (nx * nx) * 0.95f + (ny * ny) * 1.15f;
                    if (r2 >= 1.0f) continue;
                    float radialSoft = (float) Math.pow(1f - r2, 1.55);

                    float wx = fbm(nx * 2.2f + seed * 0.7f, ny * 2.6f + 4.1f, 3, seed) - 0.5f;
                    float wy = fbm(nx * 2.2f + 13.7f, ny * 2.6f + seed * 0.5f, 3, seed + 11) - 0.5f;
                    float px = nx * 3.1f + wx * 1.15f;
                    float py = ny * 3.8f + wy * 0.85f;

                    float body = fbm(px + seed, py + seed * 0.3f, 6, seed + 29);
                    float wisps = fbm(px * 2.1f - 7.3f, py * 2.6f + 3.2f, 4, seed + 61);
                    float combined = body * 0.72f + wisps * 0.28f;

                    float raw = radialSoft * (0.28f + combined * 1.38f);
                    float d = smoothstep(0.14f, 0.96f, raw);
                    d = d * d * (1.28f - 0.28f * d);
                    densityField[y * w + x] = Math.min(1f, d);
                }
            }

            int sunDx = Math.max(3, w / 34);
            int sunDy = -Math.max(3, h / 22);
            for (int y = 0; y < h; y++) {
                float ny = y / (float) (h - 1);
                for (int x = 0; x < w; x++) {
                    float d = densityField[y * w + x];
                    if (d <= 0.004f) continue;
                    int sx = Math.min(w - 1, Math.max(0, x + sunDx));
                    int sy = Math.min(h - 1, Math.max(0, y + sunDy));
                    float dSun = densityField[sy * w + sx];

                    float selfShadow = Math.max(0f, dSun - d * 0.55f);
                    float lit = 0.95f - ny * 0.24f - selfShadow * 0.34f + (1f - d) * 0.08f;
                    lit = Math.max(0.62f, Math.min(1.0f, lit));

                    int v = Math.round(lit * 255f);
                    int a = Math.round(d * 212f);
                    pixels[y * w + x] = Color.argb(a, v, v, v);
                }
            }

            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        }

        private static Bitmap bakeRealisticCirrus(int w, int h, int seed) {
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                float ny = (y / (float) (h - 1)) * 2f - 1f;
                for (int x = 0; x < w; x++) {
                    float nx = (x / (float) (w - 1)) * 2f - 1f;
                    float r2 = nx * nx + ny * ny * 1.15f;
                    if (r2 >= 1.0f) continue;
                    float env = (float) Math.pow(1f - r2, 1.6);
                    float streak = fbm(nx * 2.0f + seed, ny * 7.5f + nx * 1.8f, 5, seed);
                    float fine = fbm(nx * 6.0f, ny * 16f, 4, seed + 23);
                    float d = smoothstep(0.32f, 0.88f, env * (0.58f + streak * 0.78f + fine * 0.28f));
                    int a = Math.round(d * 148f);
                    pixels[y * w + x] = Color.argb(a, 248, 252, 255);
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        }

        /**
         * Pre-bakes a softly atmospheric photographic crescent moon that melts organically into
         * the misty midnight indigo sky (soft feathered limb, delicate maria shading, zero hard sticker rim).
         */
        private static Bitmap bakeAtmosphericMoon(int size) {
            int[] pixels = new int[size * size];
            float half = (size - 1) * 0.5f;
            for (int y = 0; y < size; y++) {
                float ny = (y - half) / (half * 0.84f);
                for (int x = 0; x < size; x++) {
                    float nx = (x - half) / (half * 0.84f);
                    float r2 = nx * nx + ny * ny;
                    if (r2 > 1.0f) continue;
                    float r = (float) Math.sqrt(r2);
                    // Softer atmospheric limb feathering so moon dissolves naturally into night mist
                    float edgeAa = smoothstep(1.0f, 0.86f, r);
                    float nz = (float) Math.sqrt(Math.max(0f, 1f - r2));

                    float maria = fbm(nx * 2.5f + 4.2f, ny * 2.5f + 1.8f, 4, 51);
                    float micro = fbm(nx * 8.0f, ny * 8.0f, 3, 83);
                    float albedo = 0.80f + (maria - 0.5f) * 0.26f + (micro - 0.5f) * 0.08f;

                    // Soft natural crescent phase with silky terminator and subtle atmospheric earthshine
                    float ndotl = nx * 0.88f - ny * 0.20f + nz * 0.36f;
                    float lit = smoothstep(-0.14f, 0.34f, ndotl);
                    float shade = (lit * (0.76f + 0.24f * nz)) * albedo;

                    int cr = Math.min(255, Math.round(shade * 238f));
                    int cg = Math.min(255, Math.round(shade * 244f));
                    int cb = Math.min(255, Math.round(shade * 255f));
                    int ca = Math.round(edgeAa * smoothstep(0.02f, 0.42f, lit) * 230f);
                    pixels[y * size + x] = Color.argb(ca, cr, cg, cb);
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, size, 0, 0, size, size);
            return bmp;
        }

        private static float smoothstep(float e0, float e1, float x) {
            float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
            return t * t * (3f - 2f * t);
        }

        private static float fbm(float x, float y, int octaves, int seed) {
            float sum = 0f;
            float amp = 0.5f;
            float freq = 1f;
            float norm = 0f;
            for (int i = 0; i < octaves; i++) {
                sum += amp * valueNoise(x * freq, y * freq, seed + i * 31);
                norm += amp;
                freq *= 2.04f;
                amp *= 0.52f;
            }
            return sum / Math.max(0.001f, norm);
        }

        private static float valueNoise(float x, float y, int seed) {
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            float fx = x - ix;
            float fy = y - iy;
            float ux = fx * fx * (3f - 2f * fx);
            float uy = fy * fy * (3f - 2f * fy);

            float a = hash2(ix, iy, seed);
            float b = hash2(ix + 1, iy, seed);
            float c = hash2(ix, iy + 1, seed);
            float d = hash2(ix + 1, iy + 1, seed);
            return lerp(lerp(a, b, ux), lerp(c, d, ux), uy);
        }

        private static float hash2(int x, int y, int seed) {
            int n = x * 374761393 + y * 668265263 + seed * 1442695041;
            n = (n ^ (n >>> 13)) * 1274126177;
            return ((n ^ (n >>> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
        }

        private void seedParticles() {
            long seed = 20261125L;
            int[] starPalette = {0xF2F7FF, 0xDCEBFF, 0xFFF2DC, 0xE6F0FF};
            for (int i = 0; i < STAR_COUNT; i++) {
                seed = nextSeed(seed);
                starX[i] = frac(seed);
                seed = nextSeed(seed);
                starY[i] = frac(seed) * 0.68f;
                seed = nextSeed(seed);
                float f = frac(seed);
                starBright[i] = (i < 14) && starX[i] > 0.34f;
                starRadius[i] = (starBright[i] ? (0.85f + f * 0.45f) : (0.38f + f * 0.62f)) * density;
                seed = nextSeed(seed);
                starPhase[i] = frac(seed) * 6.28318f;
                starColor[i] = starPalette[i % starPalette.length];
            }
            for (int i = 0; i < CLOUD_INSTANCES; i++) {
                if (i < 3) {
                    cloudDepth[i] = 0f;
                    cloudX[i] = i * 0.50f - 0.10f;
                    seed = nextSeed(seed);
                    cloudY[i] = 0.10f + frac(seed) * 0.16f;
                    cloudWFrac[i] = 0.62f + frac(seed) * 0.24f;
                    cloudSpeed[i] = 0.0014f + frac(seed) * 0.0008f;
                } else if (i < 6) {
                    cloudDepth[i] = 0.5f;
                    cloudX[i] = (i - 3) * 0.46f - 0.10f;
                    seed = nextSeed(seed);
                    cloudY[i] = 0.18f + frac(seed) * 0.18f;
                    cloudWFrac[i] = 0.56f + frac(seed) * 0.20f;
                    cloudSpeed[i] = 0.0022f + frac(seed) * 0.0012f;
                } else {
                    cloudDepth[i] = 1f;
                    cloudX[i] = (i - 6) * 0.40f - 0.18f;
                    seed = nextSeed(seed);
                    cloudY[i] = 0.12f + frac(seed) * 0.30f;
                    cloudWFrac[i] = 0.68f + frac(seed) * 0.24f;
                    cloudSpeed[i] = 0.0032f + frac(seed) * 0.0014f;
                }
                seed = nextSeed(seed);
                cloudBreathPhase[i] = frac(seed) * 6.28318f;
                cloudVariant[i] = i % 3;
            }
            for (int i = 0; i < RAIN_COUNT; i++) {
                seed = nextSeed(seed);
                rainX[i] = frac(seed);
                seed = nextSeed(seed);
                rainY[i] = frac(seed);
                seed = nextSeed(seed);
                rainDepth[i] = frac(seed);
                rainSpeed[i] = 0.72f + rainDepth[i] * 1.00f;
            }
            for (int i = 0; i < SPLASH_COUNT; i++) {
                seed = nextSeed(seed);
                splashX[i] = 0.06f + frac(seed) * 0.88f;
                seed = nextSeed(seed);
                splashPhase[i] = frac(seed) * 4.5f;
            }
            for (int i = 0; i < SNOW_COUNT; i++) {
                seed = nextSeed(seed);
                snowX[i] = frac(seed);
                seed = nextSeed(seed);
                snowY[i] = frac(seed);
                seed = nextSeed(seed);
                snowDepth[i] = frac(seed);
                seed = nextSeed(seed);
                snowPhase[i] = frac(seed) * 6.28318f;
            }
        }

        private static long nextSeed(long s) {
            return (s * 6364136223846793005L + 1442695040888963407L);
        }

        private static float frac(long s) {
            return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000L;
        }

        void resetForCityLoading(String cityId) {
            currentCityId = cityId == null ? "" : cityId;
            weatherCode = -1;
            sceneReady = false;
            float t = currentTransitionT();
            fromSky = blendArray(fromSky, toSky, t);
            fromGlow = blendArray(fromGlow, toGlow, t);
            toSky = neutralSkyGradient();
            toGlow = new int[]{0x00000000, 0x00000000, 0x00000000, 0x00000000};
            fromStarVis = toStarVis = 0f;
            fromSunVis = toSunVis = 0f;
            fromMoonVis = toMoonVis = 0f;
            fromCloudVis = toCloudVis = 0f;
            activeCloudTier = 0;
            fromRainVis = toRainVis = 0f;
            fromStormVis = toStormVis = 0f;
            fromSnowVis = toSnowVis = 0f;
            fromFogVis = toFogVis = 0f;
            transitionStartMs = SystemClock.uptimeMillis();
            transitioning = true;
            rebuildShaders(getWidth(), getHeight(), 0f);
            invalidate();
        }

        void setCityWeather(String cityId, int wmoCode, boolean day) {
            String safeCity = cityId == null ? "" : cityId;
            boolean cityChanged = !safeCity.equals(currentCityId) || !sceneReady;
            if (!cityChanged && this.weatherCode == wmoCode && this.isDay == day) return;

            float t = currentTransitionT();
            fromSky = blendArray(fromSky, toSky, t);
            fromGlow = blendArray(fromGlow, toGlow, t);
            fromDayBlend = lerp(fromDayBlend, toDayBlend, t);

            if (cityChanged) {
                currentCityId = safeCity;
                sceneOriginMs = SystemClock.uptimeMillis();
                revealStartMs = sceneOriginMs;
                cityCloudPhaseOffset = (Math.abs(safeCity.hashCode()) % 100) / 100f;
                fromStarVis = 0f;
                fromSunVis = 0f;
                fromMoonVis = 0f;
                fromCloudVis = 0f;
                fromRainVis = 0f;
                fromStormVis = 0f;
                fromSnowVis = 0f;
                fromFogVis = 0f;
            } else {
                fromStarVis = lerp(fromStarVis, toStarVis, t);
                fromSunVis = lerp(fromSunVis, toSunVis, t);
                fromMoonVis = lerp(fromMoonVis, toMoonVis, t);
                fromCloudVis = lerp(fromCloudVis, toCloudVis, t);
                fromRainVis = lerp(fromRainVis, toRainVis, t);
                fromStormVis = lerp(fromStormVis, toStormVis, t);
                fromSnowVis = lerp(fromSnowVis, toSnowVis, t);
                fromFogVis = lerp(fromFogVis, toFogVis, t);
            }

            sceneReady = true;
            this.weatherCode = wmoCode;
            this.isDay = day;
            toSky = appleSkyGradient(wmoCode, day);
            toGlow = celestialGlowColors(wmoCode, day);
            toStarVis = starVisibilityFor(wmoCode, day);
            toSunVis = sunVisibilityFor(wmoCode, day);
            toMoonVis = moonVisibilityFor(wmoCode, day);
            toCloudVis = cloudIntensityFor(wmoCode, day);
            activeCloudTier = cloudTierFor(wmoCode);
            toRainVis = rainIntensityFor(wmoCode);
            toStormVis = wmoCode >= 95 ? 1f : 0f;
            toSnowVis = snowIntensityFor(wmoCode);
            toFogVis = (wmoCode == 45 || wmoCode == 48) ? 1f : 0f;
            toDayBlend = day ? 1f : 0f;
            transitionStartMs = SystemClock.uptimeMillis();
            transitioning = true;
            rebuildShaders(getWidth(), getHeight(), 0f);
            invalidate();
        }

        void setScrollDim(float fraction) {
            float clamped = Math.max(0f, Math.min(1f, fraction));
            if (Math.abs(clamped - scrollDimFraction) < 0.01f) return;
            scrollDimFraction = clamped;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startAnimationLoop();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnimationLoop();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) startAnimationLoop();
            else stopAnimationLoop();
        }

        private void startAnimationLoop() {
            if (animating) return;
            animating = true;
            removeCallbacks(frameTicker);
            post(frameTicker);
        }

        private void stopAnimationLoop() {
            animating = false;
            removeCallbacks(frameTicker);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rebuildShaders(w, h, currentTransitionT());
        }

        private float currentTransitionT() {
            if (!transitioning) return 1f;
            float raw = (SystemClock.uptimeMillis() - transitionStartMs) / TRANSITION_MS;
            if (raw >= 1f) {
                transitioning = false;
                return 1f;
            }
            float inv = 1f - Math.max(0f, raw);
            return 1f - inv * inv * inv;
        }

        private float cityRevealAlpha() {
            float raw = (SystemClock.uptimeMillis() - revealStartMs) / CITY_REVEAL_MS;
            if (raw >= 1f) return 1f;
            float c = Math.max(0f, raw);
            return c * c * (3f - 2f * c);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static int lerpColor(int a, int b, float t) {
            int ca = Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
            int cr = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
            int cg = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
            int cb = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
            return Color.argb(ca, cr, cg, cb);
        }

        private static int[] blendArray(int[] a, int[] b, float t) {
            int[] out = new int[4];
            for (int i = 0; i < 4; i++) out[i] = lerpColor(a[i], b[i], t);
            return out;
        }

        private static int[] neutralSkyGradient() {
            return new int[]{0xFF0E1D38, 0xFF172D52, 0xFF23416E, 0xFF15294A};
        }

        private void rebuildShaders(int w, int h, float t) {
            if (w <= 0 || h <= 0) return;

            for (int i = 0; i < 4; i++) {
                curSky[i] = lerpColor(fromSky[i], toSky[i], t);
                curGlow[i] = lerpColor(fromGlow[i], toGlow[i], t);
            }
            float dayBlend = lerp(fromDayBlend, toDayBlend, t);

            skyPaint.setShader(new LinearGradient(
                    0, 0, w * 0.14f, h,
                    curSky,
                    new float[]{0f, 0.44f, 0.80f, 1f},
                    Shader.TileMode.CLAMP));

            float glowX = w * 0.82f;
            float glowY = h * 0.17f;
            float glowR = Math.max(w, h) * 0.66f;
            celestialGlowPaint.setShader(new RadialGradient(
                    glowX, glowY, glowR,
                    curGlow,
                    new float[]{0f, 0.28f, 0.66f, 1f},
                    Shader.TileMode.CLAMP));

            float sunR = Math.min(w, h) * 0.18f;
            sunDiscPaint.setShader(new RadialGradient(
                    glowX, glowY, sunR,
                    new int[]{0xEEFFFDF5, 0x98FFF2C9, 0x2EA6DCFF, 0x00A6DCFF},
                    new float[]{0f, 0.22f, 0.58f, 1f},
                    Shader.TileMode.CLAMP));

            int secColor = lerpColor(0x22365CA8, 0x26A8DCFF, dayBlend);
            secondaryGlowPaint.setShader(new RadialGradient(
                    w * 0.84f, h * 0.64f, w * 0.56f,
                    new int[]{secColor, 0x00000000},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));

            int topEdge = lerpColor(0x32050C1B, 0x3C0A2240, dayBlend);
            topScrimPaint.setShader(new LinearGradient(
                    0, 0, 0, h * 0.18f,
                    new int[]{topEdge, 0x00000000},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));

            int bottomScrim = lerpColor(0x7C040A18, 0x5A081C36, dayBlend);
            int midScrim = lerpColor(0x28040A18, 0x1E081C36, dayBlend);
            horizonScrimPaint.setShader(new LinearGradient(
                    0, h * 0.56f, 0, h,
                    new int[]{0x00000000, midScrim, bottomScrim},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP));

            lightningGlowPaint.setShader(new RadialGradient(
                    w * 0.72f, h * 0.22f, w * 0.60f,
                    new int[]{0xB0DCEBFF, 0x4888B2E6, 0x00000000},
                    new float[]{0f, 0.44f, 1f},
                    Shader.TileMode.CLAMP));
        }

        private static int[] appleSkyGradient(int wmo, boolean day) {
            if (wmo >= 95) {
                return new int[]{0xFF0D1524, 0xFF18253B, 0xFF273954, 0xFF172336};
            }
            if ((wmo >= 51 && wmo <= 67) || (wmo >= 80 && wmo <= 82)) {
                return day
                        ? new int[]{0xFF213650, 0xFF344F6E, 0xFF4D6B8B, 0xFF2C435E}
                        : new int[]{0xFF0E182A, 0xFF192940, 0xFF273D5B, 0xFF142134};
            }
            if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) {
                return day
                        ? new int[]{0xFF395878, 0xFF587A9D, 0xFF83A4C3, 0xFF506E8E}
                        : new int[]{0xFF15243A, 0xFF233957, 0xFF355276, 0xFF1B2D45};
            }
            if (wmo == 45 || wmo == 48) {
                return day
                        ? new int[]{0xFF455B72, 0xFF627990, 0xFF879DB2, 0xFF556C83}
                        : new int[]{0xFF182333, 0xFF25354A, 0xFF374B62, 0xFF1E2C3E};
            }
            if (!day) {
                return wmo == 3
                        ? new int[]{0xFF121C2E, 0xFF1D2E49, 0xFF293F60, 0xFF142136}
                        : new int[]{0xFF050B1E, 0xFF0D1E42, 0xFF193366, 0xFF0E1F42};
            }
            if (wmo == 3) {
                return new int[]{0xFF39526D, 0xFF526D8A, 0xFF708BA6, 0xFF48627E};
            }
            if (wmo == 2) {
                return new int[]{0xFF175194, 0xFF2973BE, 0xFF5398D5, 0xFF2E6DAF};
            }
            // Clear Day: pure luminous iPhone Rayleigh-scattering azure sky
            return new int[]{0xFF0F4EA3, 0xFF2174CC, 0xFF59AAF0, 0xFF2B79C8};
        }

        private static int[] celestialGlowColors(int wmo, boolean day) {
            if (wmo >= 95 || (wmo >= 51 && wmo <= 67) || (wmo >= 80 && wmo <= 82) || wmo == 3 || wmo == 45 || wmo == 48) {
                return new int[]{0x18A8C6E6, 0x0C5D80A8, 0x043C5878, 0x00000000};
            }
            if (!day) {
                return new int[]{0x3CA8C8F8, 0x1C547EC8, 0x0824427D, 0x00000000};
            }
            if (wmo <= 1) {
                return new int[]{0x6CFFF6DC, 0x36CDE9FF, 0x1278BEF8, 0x00000000};
            }
            return new int[]{0x46FFF5DB, 0x20C4E2FF, 0x0A79B2E8, 0x00000000};
        }

        private static float sunVisibilityFor(int wmo, boolean day) {
            if (!day) return 0f;
            if (wmo == 0) return 1.0f;
            if (wmo == 1) return 0.85f;
            if (wmo == 2) return 0.45f;
            return 0f;
        }

        private static float moonVisibilityFor(int wmo, boolean day) {
            if (day) return 0f;
            if (wmo == 0) return 1.0f;
            if (wmo == 1) return 0.90f;
            if (wmo == 2) return 0.55f;
            return 0f;
        }

        private static float starVisibilityFor(int wmo, boolean day) {
            if (day) return 0f;
            if (wmo == 0) return 1.0f;
            if (wmo == 1) return 0.85f;
            if (wmo == 2) return 0.50f;
            return 0f;
        }

        private static float cloudIntensityFor(int wmo, boolean day) {
            if (wmo == 0) return 0f;
            if (wmo == 1) return day ? 0.34f : 0.24f;
            if (wmo == 2) return day ? 0.68f : 0.48f;
            if (wmo == 3) return day ? 0.92f : 0.82f;
            if (wmo == 45 || wmo == 48) return 0.42f;
            if ((wmo >= 51 && wmo <= 67) || (wmo >= 80 && wmo <= 82) || wmo >= 95) return 0.90f;
            if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return 0.72f;
            return 0.50f;
        }

        private static int cloudTierFor(int wmo) {
            if (wmo <= 0) return 0;
            if (wmo == 1) return 1;
            if (wmo == 2) return 2;
            return 3;
        }

        private static float rainIntensityFor(int wmo) {
            if (wmo >= 95) return 1.0f;
            if (wmo == 65 || wmo == 82) return 1.0f;
            if (wmo == 63 || wmo == 81) return 0.84f;
            if ((wmo >= 61 && wmo <= 67) || wmo == 80) return 0.70f;
            if (wmo >= 51 && wmo <= 57) return 0.52f;
            return 0f;
        }

        private static float snowIntensityFor(int wmo) {
            if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return 1.0f;
            return 0f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float t = currentTransitionT();
            if (transitioning) {
                rebuildShaders(w, h, t);
            }
            float elapsedSec = (SystemClock.uptimeMillis() - sceneOriginMs) / 1000f;
            float reveal = sceneReady ? cityRevealAlpha() : 0f;
            float dayBlend = lerp(fromDayBlend, toDayBlend, t);

            // 1. Multi-stop sky gradient & atmospheric scattering
            canvas.drawRect(0, 0, w, h, skyPaint);
            canvas.drawRect(0, 0, w, h, celestialGlowPaint);
            canvas.drawRect(0, 0, w, h, secondaryGlowPaint);

            if (reveal > 0.01f) {
                // 2. Photographic Feathered Daytime Sun Radiance (zero hard polygon beams)
                float sunVis = lerp(fromSunVis, toSunVis, t) * reveal;
                if (sunVis > 0.01f) {
                    drawPhotographicSun(canvas, w, h, elapsedSec, sunVis);
                }

                // 3. Atmospheric Clear Night: silky cosmic haze veil + photographic stars + misty moon
                float starVis = lerp(fromStarVis, toStarVis, t) * reveal;
                if (starVis > 0.01f) {
                    drawNightAtmosphereAndStars(canvas, w, h, elapsedSec, starVis);
                }
                float moonVis = lerp(fromMoonVis, toMoonVis, t) * reveal;
                if (moonVis > 0.01f) {
                    drawAtmosphericMoon(canvas, w, h, elapsedSec, moonVis);
                }

                // 4. Fine Silky iPhone-Style Cloud Layers (strictly matched to weather condition)
                float cloudVis = lerp(fromCloudVis, toCloudVis, t) * reveal;
                float rainVis = lerp(fromRainVis, toRainVis, t) * reveal;
                float stormVis = lerp(fromStormVis, toStormVis, t) * reveal;
                float snowVis = lerp(fromSnowVis, toSnowVis, t) * reveal;
                float fogVis = lerp(fromFogVis, toFogVis, t) * reveal;

                if (cloudVis > 0.02f && activeCloudTier > 0) {
                    drawIphoneClouds(canvas, w, h, elapsedSec, cloudVis, activeCloudTier, dayBlend, rainVis, stormVis);
                }

                // 5. Diffused Cloud-Core Thunderstorm Illumination
                if (stormVis > 0.05f) {
                    drawStormLightning(canvas, w, h, elapsedSec, stormVis);
                }

                // 6. Rolling Valley Fog / Mist Ribbons
                if (fogVis > 0.02f) {
                    drawFogRibbons(canvas, w, h, elapsedSec, fogVis, dayBlend);
                }

                // 7. 3-Depth Parallax Glistening Raindrops & Horizon Splash Ripples
                if (rainVis > 0.02f) {
                    drawParallaxRain(canvas, w, h, elapsedSec, rainVis, stormVis);
                }

                // 8. 3-Depth Photographic Snowfall & Soft Foreground Bokeh
                if (snowVis > 0.02f) {
                    drawParallaxSnow(canvas, w, h, elapsedSec, snowVis);
                }
            }

            // 9. Upper/lower atmospheric scrims & smooth scroll-to-7-day dim
            canvas.drawRect(0, 0, w, h, topScrimPaint);
            canvas.drawRect(0, 0, w, h, horizonScrimPaint);
            if (scrollDimFraction > 0.01f) {
                int alpha = Math.round(scrollDimFraction * 0x58);
                scrollDimPaint.setColor((alpha << 24) | 0x061020);
                canvas.drawRect(0, 0, w, h, scrollDimPaint);
            }
        }

        /**
         * Silky photographic solar atmospheric scattering (replaces rough triangle beams & hard rings).
         */
        private void drawPhotographicSun(Canvas canvas, int w, int h, float sec, float vis) {
            float sx = w * 0.82f;
            float sy = h * 0.17f;
            float pulse = 0.96f + 0.04f * (float) Math.sin(sec * 0.45f);

            // Broad feathered solar radiance slowly rotating (~1.4 deg/s) for living sunlit atmosphere
            Bitmap solar = solarRadianceBitmap;
            if (solar != null && !solar.isRecycled()) {
                float rad = Math.max(w, h) * 0.56f * pulse;
                spritePaint.setColorFilter(null);
                spritePaint.setAlpha(Math.round(vis * 185f));
                canvas.save();
                canvas.translate(sx, sy);
                canvas.rotate(sec * 1.4f);
                dstRect.set(-rad, -rad, rad, rad);
                canvas.drawBitmap(solar, null, dstRect, spritePaint);
                canvas.restore();
            }

            // Soft incandescent sun core dissolving seamlessly into the blue sky
            sunDiscPaint.setAlpha(Math.round(vis * 235f));
            canvas.drawCircle(sx, sy, Math.min(w, h) * 0.18f * pulse, sunDiscPaint);

            // Subtle warm atmospheric bloom wisps along the sunlight path (no hard strokes)
            float drift = (float) Math.sin(sec * 0.28f) * 0.014f;
            float fx1 = lerp(sx, w * 0.36f, 0.34f + drift);
            float fy1 = lerp(sy, h * 0.58f, 0.34f + drift);
            drawTintedSprite(canvas, bokehOrbBitmap, fx1, fy1, 46f * density, 46f * density,
                    0xFFFFF4DC, Math.round(vis * 20f));
            float fx2 = lerp(sx, w * 0.36f, 0.58f - drift);
            float fy2 = lerp(sy, h * 0.58f, 0.58f - drift);
            drawTintedSprite(canvas, bokehOrbBitmap, fx2, fy2, 72f * density, 72f * density,
                    0xFFD4ECFF, Math.round(vis * 16f));
        }

        /**
         * Matches our silky Overcast aesthetic for Clear Night: soft diagonal cosmic night-haze
         * + crisp photographic multi-temperature stars with micro-glow halos (zero cartoon crosses).
         */
        private void drawNightAtmosphereAndStars(Canvas canvas, int w, int h, float sec, float vis) {
            // Subtle atmospheric Milky Way / night-sky silk haze (same feathered fBm DNA as Overcast)
            Bitmap[] sheets = softCloudSprites;
            if (sheets != null && sheets[0] != null && !sheets[0].isRecycled()) {
                float drift = (float) Math.sin(sec * 0.08f) * w * 0.025f;
                drawTintedSprite(canvas, sheets[0], w * 0.68f + drift, h * 0.28f, w * 0.44f, h * 0.32f,
                        0xFF3E68B8, Math.round(vis * 42f));
                drawTintedSprite(canvas, sheets[1], w * 0.44f - drift, h * 0.48f, w * 0.40f, h * 0.28f,
                        0xFF2C4D94, Math.round(vis * 34f));
            }

            // Photographic pinprick stars with gentle natural scintillation & soft micro-halos
            for (int i = 0; i < STAR_COUNT; i++) {
                float sx = starX[i] * w;
                float sy = starY[i] * h;
                float leftFade = starX[i] < 0.34f ? (0.28f + 0.72f * (starX[i] / 0.34f)) : 1.0f;
                float twinkle = 0.46f + 0.54f * (float) Math.sin(sec * (0.90f + (i % 7) * 0.16f) + starPhase[i]);
                int alpha = Math.round(twinkle * vis * leftFade * (starBright[i] ? 215f : 145f));

                if (starBright[i]) {
                    // Photographic soft circular glow halo instead of geometric '+' cross
                    float haloR = starRadius[i] * (4.6f + twinkle * 1.4f);
                    drawTintedSprite(canvas, bokehOrbBitmap, sx, sy, haloR, haloR,
                            starColor[i], Math.round(alpha * 0.42f));
                }
                starPaint.setColor((alpha << 24) | starColor[i]);
                canvas.drawCircle(sx, sy, starRadius[i], starPaint);
            }

            // Quiet delicate shooting star gliding across the open upper-right sky every 14 seconds
            float cycle = sec % 14.0f;
            if (cycle > 4.0f && cycle < 4.85f) {
                float p = (cycle - 4.0f) / 0.85f;
                float fade = (float) Math.sin(p * Math.PI);
                float headX = w * (0.60f + p * 0.16f);
                float headY = h * (0.10f + p * 0.11f);
                float tailX = headX - 44f * density * fade;
                float tailY = headY - 22f * density * fade;
                starPaint.setStrokeWidth(Math.max(1f, 1.1f * density));
                starPaint.setColor((Math.round(vis * fade * 165f) << 24) | 0xF0F6FF);
                canvas.drawLine(tailX, tailY, headX, headY, starPaint);
            }
        }

        private void drawAtmosphericMoon(Canvas canvas, int w, int h, float sec, float vis) {
            float mx = w * 0.82f;
            float my = h * 0.21f;
            float mr = Math.min(w, h) * 0.038f;
            float haloPulse = 0.96f + 0.04f * (float) Math.sin(sec * 0.36f);

            // Multi-layered silky silver-indigo lunar mist halo (melts organically into night sky)
            drawTintedSprite(canvas, bokehOrbBitmap, mx, my, mr * 7.2f * haloPulse, mr * 7.2f * haloPulse,
                    0xFF8AB2EE, Math.round(vis * 48f));
            drawTintedSprite(canvas, bokehOrbBitmap, mx, my, mr * 2.8f * haloPulse, mr * 2.8f * haloPulse,
                    0xFFDCEBFF, Math.round(vis * 76f));

            Bitmap moonBmp = realisticMoonBitmap;
            if (moonBmp != null && !moonBmp.isRecycled()) {
                spritePaint.setColorFilter(null);
                spritePaint.setAlpha(Math.round(vis * 232f));
                dstRect.set(mx - mr, my - mr, mx + mr, my + mr);
                canvas.drawBitmap(moonBmp, null, dstRect, spritePaint);
            }
        }

        private void drawIphoneClouds(Canvas canvas, int w, int h, float sec, float vis, int tier,
                                      float dayBlend, float rainVis, float stormVis) {
            Bitmap[] sheets = softCloudSprites;
            Bitmap cirrus = cirrusSprite;
            if (sheets == null || cirrus == null) return;

            int mulColor = lerpColor(0xFF7B94B8, 0xFFF7FAFF, dayBlend);
            int addColor = lerpColor(0xFF0A1426, 0xFF1A2B42, dayBlend);
            if (rainVis > 0.05f || stormVis > 0.05f) {
                float gloom = Math.max(rainVis * 0.72f, stormVis * 0.88f);
                mulColor = lerpColor(mulColor, dayBlend > 0.5f ? 0xFF94ABC4 : 0xFF475E7E, gloom);
                addColor = lerpColor(addColor, 0xFF08101C, gloom);
            }
            LightingColorFilter cloudFilter = new LightingColorFilter(mulColor, addColor);

            int maxIdx = tier == 1 ? 3 : (tier == 2 ? 6 : CLOUD_INSTANCES);

            for (int i = 0; i < maxIdx; i++) {
                float depth = cloudDepth[i];
                Bitmap bmp = depth < 0.2f ? cirrus : sheets[cloudVariant[i]];
                if (bmp == null || bmp.isRecycled()) continue;

                float span = 1.76f;
                float rawX = (cloudX[i] + cityCloudPhaseOffset + sec * cloudSpeed[i]) % span;
                if (rawX > 1.34f) rawX -= span;

                float breath = 1f + 0.022f * (float) Math.sin(sec * 0.26f + cloudBreathPhase[i]);
                float floatY = (float) Math.sin(sec * 0.18f + cloudBreathPhase[i]) * h * 0.006f;

                float cw = w * cloudWFrac[i] * breath;
                float ch = cw * (bmp.getHeight() / (float) bmp.getWidth());
                float cx = rawX * w;
                float cy = cloudY[i] * h + floatY;

                float depthAlpha = depth < 0.2f ? 0.52f : (depth < 0.7f ? 0.68f : 0.78f);
                int alpha = Math.min(230, Math.round(vis * depthAlpha * 200f));
                if (alpha <= 2) continue;

                spritePaint.setColorFilter(cloudFilter);
                spritePaint.setAlpha(alpha);
                dstRect.set(cx - cw * 0.5f, cy - ch * 0.5f, cx + cw * 0.5f, cy + ch * 0.5f);
                canvas.drawBitmap(bmp, null, dstRect, spritePaint);
            }
            spritePaint.setColorFilter(null);
        }

        private void drawStormLightning(Canvas canvas, int w, int h, float sec, float stormVis) {
            float cycle = sec % 7.5f;
            float flash = 0f;
            if (cycle > 1.8f && cycle < 1.98f) {
                flash = (float) Math.sin(((cycle - 1.8f) / 0.18f) * Math.PI);
            } else if (cycle > 2.06f && cycle < 2.28f) {
                flash = 0.70f * (float) Math.sin(((cycle - 2.06f) / 0.22f) * Math.PI);
            }
            if (flash <= 0.02f) return;

            // Diffused photographic cloud-core sheet lightning (natural atmospheric illumination)
            lightningGlowPaint.setAlpha(Math.round(flash * stormVis * 215f));
            canvas.drawRect(0, 0, w, h, lightningGlowPaint);
        }

        private void drawFogRibbons(Canvas canvas, int w, int h, float sec, float fogVis, float dayBlend) {
            Bitmap[] sheets = softCloudSprites;
            if (sheets == null || sheets[0] == null) return;
            int fogColor = lerpColor(0xFF7B90AA, 0xFFE6EEF6, dayBlend);
            for (int i = 0; i < 4; i++) {
                float drift = (float) Math.sin(sec * (0.12f + i * 0.035f) + i * 1.7f) * w * 0.07f;
                float cx = w * (0.20f + i * 0.22f) + drift;
                float cy = h * (0.36f + i * 0.13f);
                drawTintedSprite(canvas, sheets[i % 3], cx, cy, w * 0.58f, h * 0.22f, fogColor, Math.round(fogVis * 116f));
            }
        }

        private void drawParallaxRain(Canvas canvas, int w, int h, float sec, float rainVis, float stormVis) {
            int activeCount = Math.round(RAIN_COUNT * (0.58f + 0.42f * rainVis));
            float gustDeg = 10f + stormVis * 9f + (float) Math.sin(sec * 0.75f) * 2.8f;
            float tanSlant = (float) Math.tan(Math.toRadians(gustDeg));

            spritePaint.setColorFilter(null);
            for (int i = 0; i < activeCount; i++) {
                float d = rainDepth[i];
                float progress = (rainY[i] + sec * rainSpeed[i] * (0.85f + stormVis * 0.35f)) % 1.16f;
                float x = (rainX[i] * (w + 180f * density)) - progress * h * tanSlant * (0.45f + d * 0.45f);
                if (x < -40f) x += w + 180f * density;
                float y = progress * h - 48f * density;

                float dropH = (26f + d * 48f) * density;
                float dropW = (1.6f + d * 2.4f) * density;
                int alpha = Math.round(rainVis * (48f + d * 155f));

                spritePaint.setAlpha(Math.min(255, alpha));
                canvas.save();
                canvas.translate(x, y);
                canvas.rotate(gustDeg);
                dstRect.set(-dropW * 0.5f, 0, dropW * 0.5f, dropH);
                canvas.drawBitmap(raindropBitmap, null, dstRect, spritePaint);
                canvas.restore();
            }

            float horizonY = h * 0.77f;
            for (int i = 0; i < SPLASH_COUNT; i++) {
                float p = (sec * 1.45f + splashPhase[i]) % 2.2f;
                if (p < 0.72f) {
                    float norm = p / 0.72f;
                    float rx = (4f + norm * 18f) * density;
                    float ry = rx * 0.25f;
                    int a = Math.round(rainVis * (1f - norm) * 82f);
                    splashPaint.setColor((a << 24) | 0xEAF4FF);
                    dstRect.set(splashX[i] * w - rx, horizonY - ry, splashX[i] * w + rx, horizonY + ry);
                    canvas.drawOval(dstRect, splashPaint);
                }
            }
        }

        private void drawParallaxSnow(Canvas canvas, int w, int h, float sec, float snowVis) {
            float windGust = (float) Math.sin(sec * 0.35f) * 24f * density;
            for (int i = 0; i < SNOW_COUNT; i++) {
                float d = snowDepth[i];
                float speed = 0.055f + d * 0.115f;
                float progress = (snowY[i] + sec * speed) % 1.10f;
                float sway = (float) Math.sin(sec * (0.75f + d * 0.6f) + snowPhase[i]) * (12f + d * 22f) * density;
                float x = (snowX[i] * w + sway + windGust * (0.4f + d * 0.6f)) % (w + 40f * density);
                if (x < -20f * density) x += w + 40f * density;
                float y = progress * h - 20f * density;

                if (d > 0.76f) {
                    float r = (7.0f + (d - 0.76f) * 30f) * density;
                    drawTintedSprite(canvas, bokehOrbBitmap, x, y, r, r, 0xFFFFFFFF, Math.round(snowVis * 92f));
                } else if (d > 0.38f) {
                    float r = (2.6f + d * 3.4f) * density;
                    drawTintedSprite(canvas, bokehOrbBitmap, x, y, r, r, 0xFFF2F8FF, Math.round(snowVis * 165f));
                } else {
                    float r = (1.0f + d * 1.4f) * density;
                    int a = Math.round(snowVis * (72f + d * 90f));
                    snowPaint.setColor((a << 24) | 0xF0F7FF);
                    canvas.drawCircle(x, y, r, snowPaint);
                }
            }
        }

        private void drawTintedSprite(Canvas canvas, Bitmap bmp, float cx, float cy,
                                      float rx, float ry, int colorRgb, int alpha) {
            if (alpha <= 1 || bmp == null || bmp.isRecycled()) return;
            ColorFilter filter = new PorterDuffColorFilter(colorRgb | 0xFF000000, PorterDuff.Mode.SRC_IN);
            spritePaint.setColorFilter(filter);
            spritePaint.setAlpha(Math.min(255, alpha));
            dstRect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawBitmap(bmp, null, dstRect, spritePaint);
            spritePaint.setColorFilter(null);
        }
    }

    static final class HourlyCurveView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float density;
        private double[] temps = new double[0];
        private int selectedIndex = -1;

        HourlyCurveView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Math.max(1.2f, 1.25f * density));
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setColor(0x48FFFFFF);
            dotCorePaint.setColor(0xFFFFFFFF);
            dotGlowPaint.setColor(0x48FFFFFF);
        }

        void setTemperatures(double[] values) {
            this.temps = values == null ? new double[0] : values.clone();
            invalidate();
        }

        void setSelectedIndex(int index) {
            if (this.selectedIndex == index) return;
            this.selectedIndex = index;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0) {
                linePaint.setShader(new LinearGradient(
                        0, 0, w, 0,
                        new int[]{0x18FFFFFF, 0x5EFFFFFF, 0x5EFFFFFF, 0x18FFFFFF},
                        new float[]{0f, 0.14f, 0.86f, 1f},
                        Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int n = temps.length;
            if (n < 2) return;
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            double min = temps[0];
            double max = temps[0];
            for (int i = 1; i < n; i++) {
                if (temps[i] < min) min = temps[i];
                if (temps[i] > max) max = temps[i];
            }
            double span = Math.max(4.0, max - min);
            double mid = (max + min) * 0.5;

            float padY = h * 0.24f;
            float usableH = Math.max(2f, h - padY * 2f);
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = w * (i + 0.5f) / n;
                double norm = (temps[i] - (mid - span * 0.5)) / span;
                ys[i] = (float) (padY + (1.0 - norm) * usableH);
            }

            path.reset();
            path.moveTo(xs[0], ys[0]);
            for (int i = 0; i < n - 1; i++) {
                float cx = (xs[i] + xs[i + 1]) * 0.5f;
                path.cubicTo(cx, ys[i], cx, ys[i + 1], xs[i + 1], ys[i + 1]);
            }
            canvas.drawPath(path, linePaint);

            if (selectedIndex >= 0 && selectedIndex < n) {
                float sx = xs[selectedIndex];
                float sy = ys[selectedIndex];
                canvas.drawCircle(sx, sy, 8.5f * density, dotGlowPaint);
                canvas.drawCircle(sx, sy, 3.2f * density, dotCorePaint);
            }
        }
    }

    static final class TempRangeBarView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float startFrac = 0.2f;
        private float endFrac = 0.8f;
        private boolean highlighted;

        TempRangeBarView(Context context) {
            super(context);
            trackPaint.setColor(0x28FFFFFF);
        }

        void setRange(double weekMin, double weekMax, double dayMin, double dayMax) {
            double span = Math.max(1.0, weekMax - weekMin);
            startFrac = (float) Math.max(0.0, Math.min(1.0, (dayMin - weekMin) / span));
            endFrac = (float) Math.max(startFrac, Math.min(1.0, (dayMax - weekMin) / span));
            invalidate();
        }

        void setHighlighted(boolean focused) {
            if (this.highlighted == focused) return;
            this.highlighted = focused;
            trackPaint.setColor(focused ? 0x42FFFFFF : 0x26FFFFFF);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0) {
                fillPaint.setShader(new LinearGradient(
                        0, 0, w, 0,
                        new int[]{0xFF63C5F8, 0xFF7EE0BE, 0xFFF7CB69},
                        new float[]{0f, 0.52f, 1f},
                        Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h * 0.5f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            float minSeg = Math.max(h * 2.5f, w * 0.08f);
            float left = startFrac * w;
            float right = Math.max(left + minSeg, endFrac * w);
            if (right > w) {
                right = w;
                left = Math.max(0, right - minSeg);
            }
            fillPaint.setAlpha(highlighted ? 255 : 228);
            rect.set(left, 0, right, h);
            canvas.drawRoundRect(rect, r, r, fillPaint);
        }
    }

    static final class FadingRuleView extends View {
        static final int HORIZONTAL_FADE_RIGHT = 0;
        static final int HORIZONTAL_FADE_EDGES = 1;
        static final int VERTICAL_FADE_EDGES = 2;

        private final Paint paint = new Paint();
        private final int mode;
        private final int peakColor;

        FadingRuleView(Context context, int mode, int peakColor) {
            super(context);
            this.mode = mode;
            this.peakColor = peakColor;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            int clear = peakColor & 0x00FFFFFF;
            if (mode == HORIZONTAL_FADE_RIGHT) {
                paint.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{peakColor, clear}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            } else if (mode == HORIZONTAL_FADE_EDGES) {
                paint.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{clear, peakColor, peakColor, clear},
                        new float[]{0f, 0.08f, 0.92f, 1f}, Shader.TileMode.CLAMP));
            } else {
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{clear, peakColor, peakColor, clear},
                        new float[]{0f, 0.16f, 0.84f, 1f}, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
