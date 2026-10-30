package com.gentv.tv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Same exact-model policy as the phone's ModelMatcher; a brand alone never selects an input route. */
final class InputProfileMatch {
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
            "tv", "smarttv", "smarttvpro", "androidtv", "googletv", "ledtv", "uhdtv", "hdtv", "4ktv", "atv", "tvbox", "box"));

    static boolean matches(String id, String title, String model, String device, String product, String brand, String manufacturer) {
        String text = (title + " " + id.replace('-', ' ')).toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>(Arrays.asList(text.split("[^a-z0-9_]+")));
        boolean brandHit = false;
        for (String name : new String[]{brand, manufacturer}) {
            String value = name.toLowerCase(Locale.ROOT);
            if (value.length() >= 2 && (tokens.contains(value) || id.startsWith(value + "-"))) brandHit = true;
        }
        if (!brandHit) return false;
        for (String code : new String[]{device, product}) {
            String value = code.toLowerCase(Locale.ROOT);
            if (value.length() >= 3 && tokens.contains(value)) return true;
        }
        String specific = compact(model);
        return specific.length() >= 4 && !GENERIC.contains(specific)
                && (brandHit || specific.matches(".*[0-9].*")) && compact(text).contains(specific);
    }

    private static String compact(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
