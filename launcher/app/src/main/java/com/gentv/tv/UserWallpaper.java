package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** User-picked wallpapers live in private storage; the two original filenames remain readable. */
final class UserWallpaper {
    static final String IMAGE_ID = "user_image";
    static final String VIDEO_ID = "user_video";
    private static final long IMAGE_LIMIT = 20L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 100L * 1024 * 1024;

    private UserWallpaper() { }

    static boolean isImage(String id) { return IMAGE_ID.equals(id) || valid(id, IMAGE_ID); }
    static boolean isVideo(String id) { return VIDEO_ID.equals(id) || valid(id, VIDEO_ID); }

    private static boolean valid(String id, String prefix) {
        return id != null && id.matches(prefix + "_[0-9a-f]{32}");
    }

    private static File directory(File filesDir) { return new File(filesDir, "user_wallpapers"); }
    static File image(Context context) { return image(context, IMAGE_ID); }
    static File video(Context context) { return video(context, VIDEO_ID); }
    static File videoPreview(Context context) { return videoPreview(context, VIDEO_ID); }

    static File image(Context context, String id) {
        if (IMAGE_ID.equals(id)) return new File(context.getFilesDir(), "user_wallpaper.image");
        if (!isImage(id)) throw new IllegalArgumentException("Invalid image wallpaper ID");
        return new File(directory(context.getFilesDir()), id + ".image");
    }

    static File video(Context context, String id) {
        if (VIDEO_ID.equals(id)) return new File(context.getFilesDir(), "user_wallpaper.mp4");
        if (!isVideo(id)) throw new IllegalArgumentException("Invalid video wallpaper ID");
        File folder = directory(context.getFilesDir());
        for (String extension : new String[]{".mp4", ".webm", ".mkv"}) {
            File candidate = new File(folder, id + extension);
            if (candidate.isFile()) return candidate;
        }
        return new File(folder, id + ".mp4");
    }

    static File videoPreview(Context context, String id) {
        if (VIDEO_ID.equals(id)) return new File(context.getFilesDir(), "user_wallpaper_preview.jpg");
        if (!isVideo(id)) throw new IllegalArgumentException("Invalid video wallpaper ID");
        return new File(directory(context.getFilesDir()), id + ".preview.jpg");
    }

    static List<String> ids(Context context) { return ids(context.getFilesDir()); }

    static List<String> ids(File filesDir) {
        List<File> files = new ArrayList<>();
        File[] stored = directory(filesDir).listFiles();
        if (stored != null) for (File file : stored) {
            String name = file.getName();
            String id = name.endsWith(".image") ? name.substring(0, name.length() - 6)
                    : name.endsWith(".mp4") || name.endsWith(".mkv")
                    ? name.substring(0, name.length() - 4)
                    : name.endsWith(".webm") ? name.substring(0, name.length() - 5) : "";
            if (file.isFile() && file.length() > 0 && (isImage(id) || isVideo(id))) files.add(file);
        }
        File oldImage = new File(filesDir, "user_wallpaper.image");
        File oldVideo = new File(filesDir, "user_wallpaper.mp4");
        if (oldImage.isFile() && oldImage.length() > 0) files.add(oldImage);
        if (oldVideo.isFile() && oldVideo.length() > 0) files.add(oldVideo);
        Collections.sort(files, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        List<String> ids = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            ids.add(name.equals("user_wallpaper.image") ? IMAGE_ID
                    : name.equals("user_wallpaper.mp4") ? VIDEO_ID
                    : name.substring(0, name.lastIndexOf('.')));
        }
        return ids;
    }

    static String importFile(Context context, Uri uri) throws IOException {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null && uri.getPath() != null) {
            String path = uri.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")
                    || path.endsWith(".webp") || path.endsWith(".bmp")) mime = "image/*";
            else if (path.endsWith(".mp4") || path.endsWith(".m4v")) mime = "video/mp4";
            else if (path.endsWith(".webm")) mime = "video/webm";
            else if (path.endsWith(".mkv")) mime = "video/x-matroska";
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return importStream(context, input, mime);
        }
    }

    static String importStaged(Context context, File file, String mime) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return importStream(context, input, mime);
        }
    }

    /** A downloaded wallpaper (the wallpaper gallery): an MP4 video or a still image. */
    static String importDownload(Context context, InputStream input, boolean video) throws IOException {
        try (InputStream source = input) {
            return importStream(context, source, video ? "video/mp4" : "image/*");
        }
    }

    private static String importStream(Context context, InputStream input, String mime) throws IOException {
        boolean image = mime != null && mime.startsWith("image/");
        boolean video = mime != null && mime.startsWith("video/");
        if (!image && !video) throw new IOException("Unsupported wallpaper format");
        File folder = directory(context.getFilesDir());
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create wallpaper folder");
        String id = (image ? IMAGE_ID : VIDEO_ID) + "_" + UUID.randomUUID().toString().replace("-", "");
        String extension = "video/webm".equals(mime) ? ".webm"
                : "video/x-matroska".equals(mime) ? ".mkv" : ".mp4";
        File target = image ? image(context, id) : new File(folder, id + extension);
        File preview = video ? videoPreview(context, id) : null;
        File temporary = File.createTempFile("wallpaper-", ".tmp", folder);
        boolean saved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("Cannot open selected file");
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > (image ? IMAGE_LIMIT : VIDEO_LIMIT)) throw new IOException("Wallpaper file is too large");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("Selected file is empty");
            }
            if (image) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                    throw new IOException("Cannot decode selected image");
            } else {
                saveVideoPreview(temporary, preview);
            }
            // Same private directory: rename is atomic and works on Android 4.2 (API 17).
            if (!temporary.renameTo(target)) throw new IOException("Cannot save wallpaper");
            saved = true;
            return id;
        } finally {
            if (!saved && preview != null) preview.delete();
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void saveVideoPreview(File source, File preview) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            retriever.setDataSource(source.getAbsolutePath());
            frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) throw new IOException("Cannot preview selected video");
            Bitmap small = Bitmap.createScaledBitmap(frame, 400, 225, true);
            try (FileOutputStream output = new FileOutputStream(preview)) {
                if (!small.compress(Bitmap.CompressFormat.JPEG, 82, output))
                    throw new IOException("Cannot save video preview");
            } finally { if (small != frame) small.recycle(); }
        } catch (RuntimeException | OutOfMemoryError failure) {
            throw new IOException("Cannot decode selected video", failure);
        } finally {
            if (frame != null) frame.recycle();
            retriever.release();
        }
    }
}
