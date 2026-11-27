package com.gentv.game;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.AtomicFile;
import com.gentv.game.api.GameContentEntry;
import dalvik.system.DexClassLoader;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipFile;

/** Private, immutable game versions. Only the foundation loads these archives; Android never installs them. */
final class GameContentRepository {
    interface Progress { void update(long downloaded, long total); }
    static final class Loaded {
        final GameContentEntry entry;
        final ClassLoader loader;
        Loaded(GameContentEntry entry, ClassLoader loader) { this.entry = entry; this.loader = loader; }
    }
    private final Context context;
    private final GameNetwork network;
    private final long baseVersion;
    private final String certificate;
    private final File root;

    @SuppressWarnings("deprecation")
    GameContentRepository(Context context, GameNetwork network, long baseVersion) throws Exception {
        this.context = context;
        this.network = network;
        this.baseVersion = baseVersion;
        root = new File(context.getFilesDir(), "game-content");
        mkdir(root);
        PackageInfo host = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        if (host.signatures == null || host.signatures.length != 1) throw new IOException("Missing host signer");
        certificate = hex(MessageDigest.getInstance("SHA-256").digest(host.signatures[0].toByteArray()));
    }

    JSONObject cachedCatalog() throws Exception {
        File file = new File(root, "catalog.json");
        if (file.exists()) {
            try {
                JSONObject catalog = readJson(new AtomicFile(file)); GameListing.parse(catalog); return catalog;
            } catch (Exception ignored) { /* Bundled catalog below. */ }
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("assets/game_base_catalog.json")) {
            if (input == null) throw new IOException("Missing bundled game catalog");
            JSONObject catalog = new JSONObject(new String(GameNetwork.read(input, 128 * 1024), StandardCharsets.UTF_8));
            GameListing.parse(catalog);
            return catalog;
        }
    }

    JSONObject refreshCatalog() throws Exception {
        JSONObject catalog = network.catalog();
        GameListing.parse(catalog);
        writeJson(new AtomicFile(new File(root, "catalog.json")), catalog);
        return catalog;
    }

    private File directory(GameListing game) throws IOException {
        if (!GameListing.validId(game.id)) throw new IOException("Invalid game ID");
        File directory = new File(root, game.id);
        mkdir(directory);
        return directory;
    }
    private AtomicFile active(GameListing game) throws IOException {
        return new AtomicFile(new File(directory(game), "active.json"));
    }
    private GameContentManifest validate(JSONObject json, GameListing game) throws Exception {
        return new GameContentManifest(json, game, baseVersion, certificate, network.api);
    }
    private File archive(GameListing game, GameContentManifest manifest) throws IOException {
        File version = new File(directory(game), "versions/" + manifest.version);
        mkdir(version);
        return new File(version, manifest.name);
    }

    /** An unavailable update service never removes a valid offline game. No downgrade is accepted. */
    Loaded open(GameListing game, Progress progress) throws Exception {
        GameContentManifest installed = null;
        JSONObject saved = null;
        try { saved = readJson(active(game)); installed = validate(saved, game); }
        catch (Exception ignored) { /* Missing or incompatible game requires a new compatible version. */ }
        GameContentManifest available;
        try { available = validate(network.manifest(game.id, value -> validate(value, game)), game); }
        catch (Exception unavailable) {
            if (installed != null) return load(game, installed, archive(game, installed));
            throw unavailable;
        }
        long previousVersion = saved == null ? 0 : saved.optLong("versionCode", 0);
        if (available.version < previousVersion) {
            if (installed != null) return load(game, installed, archive(game, installed));
            throw new IOException("Game update would downgrade an existing version");
        }
        if (installed != null && available.version == installed.version) {
            if (!available.hash.equalsIgnoreCase(installed.hash)) throw new IOException("Published game version changed");
            try { return load(game, installed, archive(game, installed)); }
            catch (Exception damaged) { /* Repair the same verified version below. */ }
        }
        File destination = archive(game, available);
        File pending = new File(destination.getParentFile(), "download.pending");
        try {
            download(available, pending, progress);
            verify(game, available, pending);
            if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace damaged game");
            if (!pending.setReadOnly() || !pending.renameTo(destination)) throw new IOException("Cannot activate game archive");
            Loaded loaded = load(game, available, destination);
            writeJson(active(game), available.json);
            return loaded;
        } catch (Exception failed) {
            if (installed != null && available.version > installed.version)
                return load(game, installed, archive(game, installed));
            throw failed;
        } finally { if (pending.exists()) pending.delete(); }
    }

    private void download(GameContentManifest manifest, File pending, Progress progress) throws Exception {
        HttpURLConnection connection = GameNetwork.download(manifest.url);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Game download HTTP " + connection.getResponseCode());
            long count = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(pending)) {
                byte[] buffer = new byte[32768]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Game download cancelled");
                    count += size;
                    if (count > manifest.size) throw new IOException("Game download exceeds declared size");
                    output.write(buffer, 0, size);
                    progress.update(count, manifest.size);
                }
                output.getFD().sync();
            }
            if (count != manifest.size) throw new IOException("Incomplete game download");
        } finally { connection.disconnect(); }
    }

    @SuppressWarnings("deprecation")
    private void verify(GameListing game, GameContentManifest manifest, File file) throws Exception {
        if (file.length() != manifest.size) throw new IOException("Wrong game size");
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
        }
        if (!manifest.hash.equalsIgnoreCase(hex(hash.digest()))) throw new IOException("Game hash mismatch");
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(file.getPath(), PackageManager.GET_SIGNATURES);
        if (info == null || !game.packageName.equals(info.packageName)
                || info.getLongVersionCode() != manifest.version || !manifest.versionName.equals(info.versionName)
                || info.signatures == null || info.signatures.length != 1
                || !certificate.equals(hex(MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray()))))
            throw new IOException("Game identity or signer mismatch");
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("lib/") || name.endsWith("omni.ja")) throw new IOException("Game must reuse the shared browser");
            }
        }
    }

    private Loaded load(GameListing game, GameContentManifest manifest, File file) throws Exception {
        verify(game, manifest, file);
        File optimized = new File(context.getCodeCacheDir(), "game-content/" + game.id + "/" + manifest.version);
        mkdir(optimized);
        DexClassLoader loader = new DexClassLoader(file.getPath(), optimized.getPath(), null,
                GameContentEntry.class.getClassLoader());
        GameContentEntry entry = (GameContentEntry) loader.loadClass(game.entryClass).getDeclaredConstructor().newInstance();
        URI url = URI.create(entry.entryUrl());
        String asset = entry.remoteAsset();
        if (!game.id.equals(entry.id()) || !"https".equals(url.getScheme()) || url.getHost() == null
                || url.getUserInfo() != null || asset == null || !asset.matches("game_" + game.id + "_[a-z0-9_]+\\.html"))
            throw new IOException("Invalid game entry configuration");
        try (InputStream page = loader.getResourceAsStream("assets/" + asset)) {
            if (page == null) throw new IOException("Missing game controller page");
        }
        return new Loaded(entry, loader);
    }

    private static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create game storage");
    }
    private static JSONObject readJson(AtomicFile file) throws Exception {
        return new JSONObject(new String(GameNetwork.read(file.openRead(), 128 * 1024), StandardCharsets.UTF_8));
    }
    private static void writeJson(AtomicFile file, JSONObject json) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(json.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output);
        } catch (IOException failed) { file.failWrite(output); throw failed; }
    }
    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
