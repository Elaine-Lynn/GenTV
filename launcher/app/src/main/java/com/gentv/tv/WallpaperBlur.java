package com.gentv.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;

/** Small software copy of the selected wallpaper, blurred before it is stretched behind the app grid. */
final class WallpaperBlur {
    private static final int WIDTH = 192;
    private static final int HEIGHT = 108;
    private static final int RADIUS = 7;

    private WallpaperBlur() { }

    static Bitmap fromFile(File file) {
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
        return blurDecoded(BitmapFactory.decodeFile(file.getPath(), options));
    }

    static Bitmap fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
        return blurDecoded(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options));
    }

    static Bitmap fromFrame(Bitmap frame) {
        return blurDecoded(frame);
    }

    /** A gentler blur for other app tiles while their long-press menu is open. */
    static Bitmap menuFrame(Bitmap frame) {
        return blurDecoded(frame, frame.getWidth(), frame.getHeight(), 2, 1.0);
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= WIDTH && height / (sample * 2) >= HEIGHT)
            sample *= 2;
        return sample;
    }

    private static Bitmap blurDecoded(Bitmap source) {
        return blurDecoded(source, WIDTH, HEIGHT, RADIUS, 0.78);
    }

    private static Bitmap blurDecoded(Bitmap source, int width, int height, int radius, double dim) {
        if (source == null) return null;
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        if (small != source) source.recycle();
        int[] input = new int[width * height];
        int[] temp = new int[input.length];
        int[] output = new int[input.length];
        small.getPixels(input, 0, width, 0, 0, width, height);
        double[] weights = new double[radius + 1];
        double sum = 0;
        for (int i = 0; i <= radius; i++) {
            weights[i] = Math.exp(-i * i / (radius == RADIUS ? 18.0 : 8.0));
            sum += weights[i] * (i == 0 ? 1 : 2);
        }
        for (int i = 0; i <= radius; i++) weights[i] /= sum;
        pass(input, temp, width, height, weights, true, dim);
        pass(temp, output, width, height, weights, false, dim);
        Bitmap result = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (result != small) small.recycle();
        result.setPixels(output, 0, width, 0, 0, width, height);
        return result;
    }

    private static void pass(int[] source, int[] target, int width, int height,
                             double[] weights, boolean horizontal, double dim) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double red = 0, green = 0, blue = 0;
                for (int step = 1 - weights.length; step < weights.length; step++) {
                    int xx = horizontal ? Math.max(0, Math.min(width - 1, x + step)) : x;
                    int yy = horizontal ? y : Math.max(0, Math.min(height - 1, y + step));
                    int color = source[yy * width + xx];
                    double weight = weights[Math.abs(step)];
                    red += Color.red(color) * weight;
                    green += Color.green(color) * weight;
                    blue += Color.blue(color) * weight;
                }
                // A light dim keeps bright wallpapers from competing with the focused app.
                double shade = horizontal ? 1.0 : dim;
                target[y * width + x] = Color.rgb((int) (red * shade),
                        (int) (green * shade), (int) (blue * shade));
            }
        }
    }
}
