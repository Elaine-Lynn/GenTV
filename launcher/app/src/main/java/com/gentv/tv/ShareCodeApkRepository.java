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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Reads the remote APK catalog and prepares verified APKs for the system installer. */
final class ShareCodeApkRepository {
    static final String CACHE_DIRECTORY = "share-code";
    static final String DEFAULT_REPOSITORY = "https://github.com/Archmage83/Android_apk";

    private static final String TAG = "ShareCodeApkRepository";
    private static final String PACKAGE_PREFS = "share_code_packages";
    private static final String API_URL = "https://api.github.com/repos/";
    private static final String RAW_URL = "https://raw.githubusercontent.com/";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_RECONNECTS = 3;
    private static final int RELEASE_PAGE_SIZE = 30;
    private static final Pattern REPOSITORY_PATH =
            Pattern.compile("/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/?");
    private static final Pattern OBJECT_ID = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern RELEASE_DIGEST = Pattern.compile("sha256:([a-f0-9]{64})");
    private static final Pattern CACHE_KEY = Pattern.compile("[a-f0-9]{40}|[a-f0-9]{64}|release-[1-9][0-9]*");
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private ShareCodeApkRepository() {}

    static String repositoryPath(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null) {
                return null;
            }
            int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            if (uri.getPort() != -1 && uri.getPort() != defaultPort) return null;
            Matcher path = REPOSITORY_PATH.matcher(uri.getRawPath());
            if (!path.matches()) return null;
            String name = path.group(2);
            if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) return null;
            return path.group(1) + "/" + name;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    static List<Apk> list(String repository) throws IOException, JSONException {
        String api = API_URL + repository;
        List<Apk> apks = listTree(repository, api);
        if (apks.isEmpty()) apks = listReleases(api);
        if (apks.size() > 1) {
            Collections.sort(apks, Comparator.comparing((Apk apk) -> apk.fileName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(apk -> apk.path));
        }
        return apks;
    }

    private static List<Apk> listTree(String repository, String api) throws IOException, JSONException {
        // GitHub lists commits from the default branch when no branch is specified.
        String latest = readCatalogResponse(api + "/commits?per_page=1");
        if (latest == null) return Collections.emptyList();
        JSONArray commits = new JSONArray(latest);
        if (commits.length() == 0) return Collections.emptyList();
        JSONObject head = commits.getJSONObject(0);
        String revision = head.getString("sha");
        String treeId = head.getJSONObject("commit").getJSONObject("tree").getString("sha");
        if (!OBJECT_ID.matcher(revision).matches() || !OBJECT_ID.matcher(treeId).matches()) {
            throw new IOException("Invalid GitHub revision");
        }
        String listing = readCatalogResponse(api + "/git/trees/" + treeId + "?recursive=1");
        if (listing == null) return Collections.emptyList();
        JSONObject tree = new JSONObject(listing);
        if (tree.getBoolean("truncated")) throw new IOException("GitHub returned a partial APK listing");
        // Pin raw downloads to the same commit as the listing and blob IDs.
        String raw = RAW_URL + repository + "/" + revision + "/";
        JSONArray entries = tree.getJSONArray("tree");
        List<Apk> apks = new ArrayList<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            String path = entry.getString("path");
            if ("blob".equals(entry.getString("type")) && path.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                long size = entry.getLong("size");
                String sha = entry.getString("sha");
                if (size > 0 && OBJECT_ID.matcher(sha).matches()) {
                    apks.add(new Apk(path, size, sha, new URL(raw + Uri.encode(path, "/"))));
                }
            }
        }
        return apks;
    }

    private static List<Apk> listReleases(String api) throws IOException, JSONException {
        for (int page = 1; ; page++) {
            String json = readCatalogResponse(api + "/releases?per_page=" + RELEASE_PAGE_SIZE + "&page=" + page);
            if (json == null) return Collections.emptyList();
            JSONArray releases = new JSONArray(json);
            List<Apk> apks = releaseApks(releases);
            if (!apks.isEmpty() || releases.length() < RELEASE_PAGE_SIZE) return apks;
        }
    }

    // Show the first release with APKs so older versions do not duplicate the same apps.
    static List<Apk> releaseApks(JSONArray releases) throws JSONException, IOException {
        List<Apk> apks = new ArrayList<>();
        for (int index = 0; index < releases.length(); index++) {
            JSONObject release = releases.getJSONObject(index);
            if (release.optBoolean("draft")) continue;
            JSONArray assets = release.getJSONArray("assets");
            for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                JSONObject asset = assets.getJSONObject(assetIndex);
                String name = asset.optString("name");
                long id = asset.optLong("id");
                long size = asset.optLong("size");
                if (!name.toLowerCase(Locale.ROOT).endsWith(".apk") || id <= 0 || size <= 0
                        || !"uploaded".equals(asset.optString("state"))) continue;
                URL url = new URL(asset.getString("browser_download_url"));
                if (!"https".equals(url.getProtocol()) || !"github.com".equalsIgnoreCase(url.getHost())
                        || url.getUserInfo() != null || !url.getPath().contains("/releases/download/")) continue;
                Matcher digest = RELEASE_DIGEST.matcher(asset.optString("digest").toLowerCase(Locale.ROOT));
                if (digest.matches()) {
                    apks.add(new Apk(name, size, digest.group(1), url, Checksum.SHA256));
                } else {
                    apks.add(new Apk(name, size, "release-" + id, url, null));
                }
            }
            if (!apks.isEmpty()) break;
        }
        return apks;
    }

    private static String readCatalogResponse(String url) throws IOException {
        checkInterrupted();
        HttpURLConnection connection = connect(new URL(url));
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_CONFLICT) {
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("GitHub catalog HTTP " + status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    checkInterrupted();
                    json.append(buffer, 0, count);
                }
                return json.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    static void download(Apk apk, File apkFile, Progress progress) throws IOException {
        download(apk, apkFile, progress, apk.downloadUrl);
    }

    static void download(Apk apk, File apkFile, Progress progress, URL url) throws IOException {
        checkInterrupted();
        File directory = apkFile.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create the share-code download directory");
        }
        File partial = new File(directory, apk.cacheKey + ".part");
        File etagFile = new File(directory, partial.getName() + ".etag");
        if (apkFile.isFile()) {
            // Cancellation may happen after the APK has been verified and saved.
            if (apkFile.length() == apk.size && (apk.checksum == null ? hasManifest(apkFile)
                    : apk.cacheKey.equals(readHash(apkFile, apk.size, apk.checksum)))) {
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
        // Publish the .apk path only after the complete download has been verified.
        if (!partial.renameTo(apkFile)) throw new IOException("Cannot save the verified APK");
        deleteCachedApk(etagFile);
        progress.update(100, apk.size);
    }

    static File completedFile(File cache, String key) {
        return new File(new File(cache, CACHE_DIRECTORY), key + ".apk");
    }

    static boolean isCachedApkName(String name) {
        return name != null && name.endsWith(".apk")
                && CACHE_KEY.matcher(name.substring(0, name.length() - 4)).matches();
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
        Map<Checksum, Map<Long, Set<String>>> unknown = new EnumMap<>(Checksum.class);
        for (Apk apk : apks) {
            checkInterrupted();
            String name = known.getString(apk.cacheKey, null);
            if (name != null) {
                names.put(apk.cacheKey, name);
            } else if (apk.checksum != null) {
                unknown.computeIfAbsent(apk.checksum, checksum -> new HashMap<>())
                        .computeIfAbsent(apk.size, size -> new HashSet<>()).add(apk.cacheKey);
            }
        }
        if (unknown.isEmpty()) return names;

        // Compare a hash only when the installed file has the same size as a catalog entry.
        SharedPreferences.Editor changes = known.edit();
        for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(0)) {
            checkInterrupted();
            if (app.sourceDir == null) continue;
            File source = new File(app.sourceDir);
            long size = source.length();
            for (Map.Entry<Checksum, Map<Long, Set<String>>> group : unknown.entrySet()) {
                Set<String> candidates = group.getValue().get(size);
                if (candidates == null) continue;
                String hash = readHash(source, size, group.getKey());
                if (hash == null || !candidates.remove(hash)) continue;
                names.put(hash, app.packageName);
                changes.putString(hash, app.packageName);
                if (candidates.isEmpty()) group.getValue().remove(size);
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
                .edit().putString(apk.cacheKey, archive.packageName).apply();
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

    private static String readHash(File file, long size, Checksum checksum) throws InterruptedIOException {
        try {
            MessageDigest digest = checksum.start(size);
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
        MessageDigest digest = apk.checksum == null ? null : apk.checksum.start(apk.size);
        byte[] buffer = new byte[64 * 1024];
        long offset = file.length();
        if (offset > apk.size) throw new IOException("Cached APK exceeds the expected length");
        String validator = offset > 0 ? readEtag(etagFile) : null;
        // Older release assets may lack a hash. Resume those only with a strong HTTP validator.
        if (offset > 0 && digest == null && validator == null) {
            if (!file.delete() && file.exists()) throw new IOException("Cannot restart unverified APK download");
            offset = 0;
        }
        if (offset > 0) progress.update(downloadPercent(offset, apk.size), offset);
        // Rebuild the hash from the saved prefix before appending any new bytes.
        if (offset > 0 && digest != null && !hashFile(file, offset, digest, buffer)) {
            throw new IOException("Cached APK changed during download");
        }
        int reconnects = 0;
        while ((offset = file.length()) < apk.size) {
            checkInterrupted();
            HttpURLConnection connection = connect(url);
            connection.setRequestProperty("Accept-Encoding", "identity");
            boolean requestRange = offset > 0 && (digest != null || validator != null);
            if (requestRange) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
                if (validator != null) connection.setRequestProperty("If-Range", validator);
            }
            try {
                int status = connection.getResponseCode();
                long end;
                boolean append = status == HttpURLConnection.HTTP_PARTIAL && requestRange;
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
                            if (apk.checksum != null) digest = apk.checksum.start(apk.size);
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
        if (file.length() != apk.size || (digest == null ? !hasManifest(file)
                : !apk.cacheKey.equals(UpdatePackage.hex(digest.digest())))) {
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
            // The file hash can validate a resumed download when no HTTP validator is available.
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
            if (digest != null) digest.update(buffer, 0, count);
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

    private static boolean hasManifest(File file) {
        try (ZipFile archive = new ZipFile(file)) {
            return archive.getEntry("AndroidManifest.xml") != null;
        } catch (IOException error) {
            return false;
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

    private enum Checksum {
        GIT_BLOB, SHA256;

        MessageDigest start(long size) throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance(this == GIT_BLOB ? "SHA-1" : "SHA-256");
                if (this == GIT_BLOB) {
                    // Git blob IDs include a header with the type and length.
                    digest.update(("blob " + size + "\0").getBytes(UTF_8));
                }
                return digest;
            } catch (NoSuchAlgorithmException error) {
                throw new IOException(error);
            }
        }
    }

    static final class Apk {
        final String path;
        final String fileName;
        final long size;
        final String cacheKey;
        final URL downloadUrl;
        private final Checksum checksum;

        Apk(String path, long size, String sha) {
            this(path, size, sha, null);
        }

        Apk(String path, long size, String sha, URL downloadUrl) {
            this(path, size, sha, downloadUrl, Checksum.GIT_BLOB);
        }

        private Apk(String path, long size, String cacheKey, URL downloadUrl, Checksum checksum) {
            this.path = path;
            this.fileName = path.substring(path.lastIndexOf('/') + 1);
            this.size = size;
            this.cacheKey = cacheKey;
            this.downloadUrl = downloadUrl;
            this.checksum = checksum;
        }
    }
}
