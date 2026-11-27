package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the remote APK catalog and prepares verified APKs for the system installer. */
final class ShareCodeApkRepository {
    static final String CACHE_DIRECTORY = "share-code";

    private static final String TAG = "ShareCodeApkRepository";
    private static final String PACKAGE_PREFS = "share_code_packages";
    private static final String TREE_URL =
            "https://api.github.com/repos/Archmage83/Android_apk/git/trees/main?recursive=1";
    private static final String RAW_URL = "https://raw.githubusercontent.com/Archmage83/Android_apk/main/";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_RECONNECTS = 3;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private ShareCodeApkRepository() {}

    static List<Apk> list() throws IOException, JSONException {
        HttpURLConnection connection = connect(new URL(TREE_URL));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), UTF_8))) {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                checkInterrupted();
                json.append(buffer, 0, count);
            }
            return parseTree(json.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static List<Apk> parseTree(String json) throws JSONException, IOException {
        JSONObject tree = new JSONObject(json);
        if (tree.getBoolean("truncated")) throw new IOException("GitHub returned a partial APK listing");
        JSONArray entries = tree.getJSONArray("tree");
        List<Apk> apks = new ArrayList<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            String path = entry.getString("path");
            if ("blob".equals(entry.getString("type")) && path.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                long size = entry.getLong("size");
                String sha = entry.getString("sha");
                if (size > 0 && sha.matches("[a-f0-9]{40}")) apks.add(new Apk(path, size, sha));
            }
        }
        Collections.sort(apks, Comparator.comparing((Apk apk) -> apk.fileName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(apk -> apk.path));
        return apks;
    }

    static void download(Apk apk, File apkFile, Progress progress) throws IOException {
        download(apk, apkFile, progress, new URL(RAW_URL + Uri.encode(apk.path, "/")));
    }

    static void download(Apk apk, File apkFile, Progress progress, URL url) throws IOException {
        checkInterrupted();
        File directory = apkFile.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create the share-code download directory");
        }
        File partial = new File(directory, apk.sha + ".part");
        File etagFile = new File(directory, partial.getName() + ".etag");
        if (apkFile.isFile()) {
            // Cancellation may happen after the APK has been verified and saved.
            if (apk.sha.equals(readBlobId(apkFile, apk.size))) {
                deleteCachedApk(partial);
                deleteCachedApk(etagFile);
                progress.update(100, apk.size);
                return;
            }
            deleteCachedApk(apkFile);
        }
        try {
            transfer(apk, partial, etagFile, progress, url);
        } catch (InterruptedIOException | SocketException | EOFException | ProtocolException error) {
            // Keep written bytes after cancellation or a connection failure so a new task can resume.
            if (partial.length() == 0) {
                deleteCachedApk(partial);
                deleteCachedApk(etagFile);
            }
            throw error;
        } catch (IOException error) {
            deleteCachedApk(partial);
            deleteCachedApk(etagFile);
            throw error;
        }
        checkInterrupted();
        // Publish the .apk path only after the full length and blob ID have been verified.
        if (!partial.renameTo(apkFile)) throw new IOException("Cannot save the verified APK");
        deleteCachedApk(etagFile);
        progress.update(100, apk.size);
    }

    static File completedFile(File cache, String sha) {
        return new File(new File(cache, CACHE_DIRECTORY), sha + ".apk");
    }

    /** Removes APKs and download fragments when the shared app list is closed. */
    static void clearCache(File cache) {
        File directory = new File(cache, CACHE_DIRECTORY);
        File[] files = directory.listFiles((parent, name) ->
                name.endsWith(".apk") || name.endsWith(".part") || name.endsWith(".part.etag"));
        if (files == null) return;
        for (File file : files) deleteCachedApk(file);
    }

    static void deleteCachedApk(File file) {
        if (file.isFile() && !file.delete() && file.isFile()) {
            Log.w(TAG, "Could not delete cached APK: " + file.getName());
        }
    }

    static Map<String, String> resolvePackageNames(Context context, List<Apk> apks)
            throws InterruptedIOException {
        SharedPreferences known = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE);
        Map<String, String> names = new HashMap<>();
        Map<Long, Set<String>> unknownBySize = new HashMap<>();
        for (Apk apk : apks) {
            checkInterrupted();
            String name = known.getString(apk.sha, null);
            if (name != null) {
                names.put(apk.sha, name);
            } else {
                unknownBySize.computeIfAbsent(apk.size, size -> new HashSet<>()).add(apk.sha);
            }
        }
        if (unknownBySize.isEmpty()) return names;

        // Recover missing mappings from installed APKs. Size narrows the search to a single hash per file.
        SharedPreferences.Editor changes = known.edit();
        for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(0)) {
            checkInterrupted();
            if (app.sourceDir == null) continue;
            File source = new File(app.sourceDir);
            long size = source.length();
            Set<String> candidates = unknownBySize.get(size);
            if (candidates == null) continue;
            String sha = readBlobId(source, size);
            if (sha == null || !candidates.remove(sha)) continue;

            names.put(sha, app.packageName);
            changes.putString(sha, app.packageName);
            if (candidates.isEmpty()) {
                unknownBySize.remove(size);
                if (unknownBySize.isEmpty()) break;
            }
        }
        changes.apply();
        return names;
    }

    static ApplicationInfo inspectDownload(Context context, Apk apk, File file) {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0);
        if (archive == null || archive.applicationInfo == null) return null;
        // Keep the package mapping after the temporary APK has been deleted.
        context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(apk.sha, archive.packageName).apply();
        ApplicationInfo app = archive.applicationInfo;
        app.sourceDir = file.getPath();
        app.publicSourceDir = file.getPath();
        return app;
    }

    @SuppressWarnings("deprecation")
    static Bitmap readArchiveIcon(Context context, ApplicationInfo app) {
        if (app == null || app.icon == 0) return null;
        try {
            Drawable icon = context.getPackageManager().getResourcesForApplication(app).getDrawable(app.icon);
            // Draw into a small bitmap so the icon survives deletion of the temporary APK.
            int size = 96;
            Bitmap preview = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(preview));
            return preview;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            Log.w(TAG, "Could not read the downloaded APK icon", error);
            return null;
        }
    }

    private static String readBlobId(File file, long size) throws InterruptedIOException {
        try {
            MessageDigest digest = blobDigest(size);
            return hashFile(file, size, digest, new byte[64 * 1024])
                    ? UpdatePackage.hex(digest.digest()) : null;
        } catch (InterruptedIOException error) {
            throw error;
        } catch (IOException error) {
            return null;
        }
    }

    private static boolean hashFile(File file, long size, MessageDigest digest, byte[] buffer)
            throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            long received = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkInterrupted();
                received += count;
                if (received > size) return false;
                digest.update(buffer, 0, count);
            }
            return received == size;
        }
    }

    private static void transfer(Apk apk, File file, File etagFile, Progress progress, URL url)
            throws IOException {
        MessageDigest digest = blobDigest(apk.size);
        byte[] buffer = new byte[64 * 1024];
        long offset = file.length();
        if (offset > apk.size) throw new IOException("Cached APK exceeds the expected length");
        if (offset > 0) progress.update(downloadPercent(offset, apk.size), offset);
        // Rebuild the Git blob hash from the saved prefix before appending any new bytes.
        if (offset > 0 && !hashFile(file, offset, digest, buffer)) {
            throw new IOException("Cached APK changed during download");
        }
        String validator = offset > 0 ? readEtag(etagFile) : null;
        int reconnects = 0;
        while ((offset = file.length()) < apk.size) {
            checkInterrupted();
            HttpURLConnection connection = connect(url);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
                if (validator != null) connection.setRequestProperty("If-Range", validator);
            }
            try {
                int status = connection.getResponseCode();
                long end;
                boolean append = status == HttpURLConnection.HTTP_PARTIAL && offset > 0;
                String etag = connection.getHeaderField("ETag");
                if (append) {
                    end = rangeEnd(connection.getHeaderField("Content-Range"), offset, apk.size);
                    if (validator != null && etag != null && !validator.equals(etag)) {
                        throw new IOException("APK changed during download");
                    }
                } else if (status == HttpURLConnection.HTTP_OK) {
                    end = apk.size;
                } else if (status == HttpURLConnection.HTTP_CLIENT_TIMEOUT
                        || status == HttpURLConnection.HTTP_GATEWAY_TIMEOUT) {
                    throw new SocketTimeoutException("APK download HTTP " + status);
                } else {
                    throw new IOException("APK download HTTP " + status);
                }

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(file, append)) {
                    if (!append || validator == null) {
                        validator = strongEtag(etag);
                        writeEtag(etagFile, validator);
                    }
                    if (!append) {
                        if (offset > 0) {
                            // A full response to a range request replaces both the partial file and its hash.
                            digest = blobDigest(apk.size);
                            progress.update(0, 0);
                            offset = 0;
                        }
                    }
                    copyResponse(input, output, digest, buffer, offset, end, apk.size, progress);
                }
                continue;
            } catch (SocketTimeoutException | SocketException | EOFException | ProtocolException error) {
                checkInterrupted();
                if (file.length() == apk.size) break;
                if (reconnects >= MAX_RECONNECTS) throw error;
            } finally {
                connection.disconnect();
            }
            progress.reconnecting(++reconnects, MAX_RECONNECTS);
            try {
                Thread.sleep(500L << (reconnects - 1));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
        checkInterrupted();
        if (file.length() != apk.size || !apk.sha.equals(UpdatePackage.hex(digest.digest()))) {
            throw new IOException("APK does not match the GitHub listing");
        }
    }

    /** Returns the exclusive end offset of a response that starts exactly where the local file ends. */
    private static long rangeEnd(String contentRange, long offset, long size) throws IOException {
        Matcher range = CONTENT_RANGE.matcher(String.valueOf(contentRange));
        if (range.matches()) {
            try {
                long end = Long.parseLong(range.group(2));
                if (Long.parseLong(range.group(1)) == offset && end >= offset && end < size
                        && Long.parseLong(range.group(3)) == size) {
                    return end + 1;
                }
            } catch (NumberFormatException ignored) {
                // Reject lengths outside the supported range.
            }
        }
        throw new IOException("Unexpected APK Content-Range");
    }

    private static String strongEtag(String value) {
        return value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value : null;
    }

    private static String readEtag(File file) {
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            return strongEtag(input.readUTF());
        } catch (IOException ignored) {
            // Length and Git blob ID still have to match when the server provides no validator.
            return null;
        }
    }

    private static void writeEtag(File file, String etag) throws IOException {
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeUTF(etag == null ? "" : etag);
        }
    }

    private static void copyResponse(InputStream input, FileOutputStream output, MessageDigest digest,
                                     byte[] buffer, long received, long end, long size, Progress progress)
            throws IOException {
        int lastPercent = -1;
        while (true) {
            checkInterrupted();
            int count = input.read(buffer);
            if (count == -1) {
                if (received != end) throw new EOFException("Incomplete APK response");
                return;
            }
            checkInterrupted();
            if (count > end - received) throw new IOException("APK response exceeds the expected length");
            output.write(buffer, 0, count);
            digest.update(buffer, 0, count);
            received += count;
            // Reserve 100% for the verified APK and post updates only when the percentage changes.
            int percent = downloadPercent(received, size);
            if (percent != lastPercent) {
                lastPercent = percent;
                progress.update(percent, received);
            }
        }
    }

    private static int downloadPercent(long received, long size) {
        return (int) Math.min(99, received * 100 / size);
    }

    private static MessageDigest blobDigest(long size) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            // Git blob IDs include a header with the type and length.
            digest.update(("blob " + size + "\0").getBytes(UTF_8));
            return digest;
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        return connection;
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
    }

    interface Progress {
        void update(int percent, long received);

        default void reconnecting(int attempt, int maximum) {}
    }

    static final class Apk {
        final String path;
        final String fileName;
        final long size;
        final String sha;

        Apk(String path, long size, String sha) {
            this.path = path;
            this.fileName = path.substring(path.lastIndexOf('/') + 1);
            this.size = size;
            this.sha = sha;
        }
    }
}
