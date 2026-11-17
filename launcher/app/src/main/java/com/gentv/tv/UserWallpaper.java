package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** A file picked on the TV, copied into private storage so it survives provider permission changes. */
final class UserWallpaper {
    static final String IMAGE_ID = "user_image";
    static final String VIDEO_ID = "user_video";
    private static final long IMAGE_LIMIT = 20L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 100L * 1024 * 1024;

    private UserWallpaper() { }

    static File image(Context context) { return new File(context.getFilesDir(), "user_wallpaper.image"); }
    static File video(Context context) { return new File(context.getFilesDir(), "user_wallpaper.mp4"); }
    static File videoPreview(Context context) { return new File(context.getFilesDir(), "user_wallpaper_preview.jpg"); }
    static boolean isImage(String id) { return IMAGE_ID.equals(id); }
    static boolean isVideo(String id) { return VIDEO_ID.equals(id); }

    static String importFile(Context context, Uri uri) throws IOException {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null && uri.getPath() != null) {
            String path = uri.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")
                    || path.endsWith(".webp") || path.endsWith(".bmp")) mime = "image/*";
            else if (path.endsWith(".mp4") || path.endsWith(".m4v")) mime = "video/*";
        }
        boolean image = mime != null && mime.startsWith("image/");
        boolean video = mime != null && mime.startsWith("video/");
        if (!image && !video) throw new IOException("Unsupported wallpaper format");
        File target = image ? image(context) : video(context);
        File temporary = new File(context.getFilesDir(), "user_wallpaper.tmp");
        long limit = image ? IMAGE_LIMIT : VIDEO_LIMIT;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("Cannot open selected file");
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) throw new IOException("Wallpaper file is too large");
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
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                Bitmap frame;
                try {
                    retriever.setDataSource(temporary.getAbsolutePath());
                    frame = retriever.getFrameAtTime(1_000_000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } catch (RuntimeException failure) {
                    throw new IOException("Cannot decode selected video", failure);
                } finally { retriever.release(); }
                if (frame == null) throw new IOException("Cannot preview selected video");
                Bitmap small = Bitmap.createScaledBitmap(frame, 400, 225, true);
                try (FileOutputStream out = new FileOutputStream(videoPreview(context))) {
                    if (!small.compress(Bitmap.CompressFormat.JPEG, 82, out))
                        throw new IOException("Cannot save video preview");
                } finally {
                    if (small != frame) small.recycle();
                    frame.recycle();
                }
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return image ? IMAGE_ID : VIDEO_ID;
        } finally { if (temporary.exists()) temporary.delete(); }
    }
}
