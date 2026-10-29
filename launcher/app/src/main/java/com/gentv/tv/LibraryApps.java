package com.gentv.tv;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/** Home shortcut choices made from the library, separate from theme defaults and tile ordering. */
final class LibraryApps {
    static final int MAX_ADDED = 128;
    private static final long RECENT_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_BYTES = MAX_ADDED * 256;

    private LibraryApps() { }

    private static AtomicFile store(Context context, boolean removed) {
        return new AtomicFile(new File(context.getFilesDir(), removed ? "library_home_removed.txt" : "library_apps.txt"));
    }

    static Set<String> load(Context context) {
        return read(store(context, false));
    }

    static Set<String> loadRemoved(Context context) {
        Set<String> result = new HashSet<>(read(store(context, true)));
        // Compatibility with the unreleased first prototype; normal states derive this at save time.
        if (DesktopStore.libraryVisibilityReady(context) || DesktopStore.hiddenKeys(context).isEmpty()) return result;
        java.util.Map<String, Boolean> allHidden = new java.util.HashMap<>();
        for (String key : DesktopStore.catalog(context).keySet()) {
            String pkg = android.content.ComponentName.unflattenFromString(key).getPackageName();
            allHidden.put(pkg, allHidden.getOrDefault(pkg, true) && DesktopStore.hidden(context, key));
        }
        for (java.util.Map.Entry<String, Boolean> entry : allHidden.entrySet()) if (entry.getValue()) result.add(entry.getKey());
        return result;
    }

    private static Set<String> read(AtomicFile file) {
        try { return readStrict(file); }
        catch (IOException | RuntimeException e) {
            Log.w("GenTV.Library", "Could not read shortcuts", e);
            return Collections.emptySet();
        }
    }

    private static Set<String> readStrict(AtomicFile file) throws IOException {
        try { return decode(DesktopStore.text(file.getBaseFile())); }
        catch (Exception e) { throw new IOException("Cannot read owner shortcuts", e); }
    }

    static boolean save(Context context, String pkg, boolean onHome) {
        return DesktopStore.library(context, pkg, onHome);
    }

    static boolean visible(boolean tvApp, boolean themeHidden, boolean explicitlyAdded) {
        return visible(tvApp, themeHidden, explicitlyAdded, false);
    }

    static boolean visible(boolean tvApp, boolean themeHidden, boolean explicitlyAdded, boolean removedFromHome) {
        return !removedFromHome && (explicitlyAdded || (tvApp && !themeHidden));
    }

    /** The pill after the last Home row: show_library_pill false hides it only while the top-bar button is a way in. */
    static boolean showPill(boolean library, boolean button, boolean pill) {
        return library && (pill || !button);
    }

    /** OK on a library card opens the app (true) or changes its Home shortcut. app_library_selection's OK always
     *  changes it; otherwise an app on Home opens, and with library_ok_opens every app does. */
    static boolean okOpens(boolean selection, boolean openAll, boolean onHome) {
        return !selection && (openAll || onHome);
    }

    static boolean recent(long installedAt, long now) {
        return installedAt > 0 && installedAt <= now && now - installedAt <= RECENT_MS;
    }

    /** Missing shortcuts first; recent installs lead that group. The caller sorts ties by label. */
    static int comparePriority(boolean homeA, long installedA, boolean homeB, long installedB, long now) {
        if (homeA != homeB) return homeA ? 1 : -1;
        if (homeA) return 0;
        boolean recentA = recent(installedA, now), recentB = recent(installedB, now);
        if (recentA != recentB) return recentA ? -1 : 1;
        return recentA ? Long.compare(installedB, installedA) : 0;
    }

    /** After adding a shortcut, prefer another missing app; otherwise stay by a neighbour. */
    static int nextFocus(int current, int count, IntPredicate missing) {
        if (count <= 0) return -1;
        current = Math.max(0, Math.min(current, count - 1));
        for (int step = 1; step < count; step++) {
            int candidate = (current + step) % count;
            if (missing.test(candidate)) return candidate;
        }
        return current + 1 < count ? current + 1 : Math.max(0, current - 1);
    }

    /** Checked Home apps first, then apps hidden from Home; the caller sorts each group by label. */
    static int compareHomeGroup(boolean homeA, boolean homeB) {
        return Boolean.compare(homeB, homeA);
    }

    /** Keep the next neighbour when a shortcut moves away, or the previous one at the end. */
    static int nextAfterRemoval(int current, int count) {
        if (count <= 1) return -1;
        current = Math.max(0, Math.min(current, count - 1));
        return current + 1 < count ? current + 1 : current - 1;
    }

    static int compareCatalog(boolean homeA, String labelA, String packageA,
                              boolean homeB, String labelB, String packageB, Collator collator) {
        int group = compareHomeGroup(homeA, homeB);
        if (group != 0) return group;
        int label = collator.compare(labelA, labelB);
        return label != 0 ? label : packageA.compareTo(packageB);
    }

    static Set<String> change(Set<String> saved, String pkg, boolean added) {
        if (!validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
        Set<String> next = new HashSet<>(saved);
        if (added) next.add(pkg); else next.remove(pkg);
        if (next.size() > MAX_ADDED) throw new IllegalArgumentException("Too many shortcuts");
        return next;
    }

    static String encode(Set<String> added) {
        if (added.size() > MAX_ADDED) throw new IllegalArgumentException("Too many shortcuts");
        StringBuilder text = new StringBuilder();
        for (String pkg : new TreeSet<>(added)) {
            if (!validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
            text.append(pkg).append('\n');
        }
        return text.toString();
    }

    static Set<String> decode(String text) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Shortcut file too large");
        Set<String> result = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') continue;
            if (i == start && i == text.length()) break;
            String pkg = text.substring(start, i);
            if (!validPackage(pkg) || !result.add(pkg) || result.size() > MAX_ADDED)
                throw new IllegalArgumentException("Invalid shortcut file");
            start = i + 1;
        }
        return result;
    }

    static boolean validPackage(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 255) return false;
        boolean first = true;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (c == '.') {
                if (first) return false;
                first = true;
            } else {
                boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
                if (!letter && (first || c < '0' || c > '9')) return false;
                first = false;
            }
        }
        return !first;
    }
}
