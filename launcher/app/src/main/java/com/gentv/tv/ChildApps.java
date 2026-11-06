package com.gentv.tv;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Package names remain selected when the app is temporarily uninstalled. */
final class ChildApps {
    static final String KEY = "players";
    private static final String LEGACY = "player";

    private ChildApps() {}

    static Set<String> read(SharedPreferences state) {
        Set<String> saved = state.contains(KEY) ? state.getStringSet(KEY, java.util.Collections.emptySet()) : null;
        return resolve(saved, state.getString(LEGACY, ""));
    }

    static Set<String> resolve(Collection<String> saved, String legacy) {
        Set<String> result = new HashSet<>();
        if (saved != null) {
            for (String pkg : saved) if (pkg != null && !pkg.isEmpty()) result.add(pkg);
        } else if (legacy != null && !legacy.isEmpty()) {
            result.add(legacy);
        }
        return result;
    }

    static boolean save(SharedPreferences state, Set<String> packages) {
        return state.edit().putStringSet(KEY, new HashSet<>(packages)).remove(LEGACY).commit();
    }
}
