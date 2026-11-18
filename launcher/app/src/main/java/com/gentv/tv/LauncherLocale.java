package com.gentv.tv;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private LauncherLocale() {
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        // SDK_INT is 0 in the host JVM used by the plain unit tests.
        if (android.os.Build.VERSION.SDK_INT == 0 || android.os.Build.VERSION.SDK_INT >= 21)
            return Locale.forLanguageTag(tag.trim());
        String[] parts = tag.trim().split("-");
        return parts.length > 1 && parts[1].length() == 2
                ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
    }

    /** base with the configured language, or base itself when locale is off. */
    static Context wrap(Context base) {
        Locale locale = parse(Config.load(base).locale);
        if (locale == null) return base;
        // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
        Locale.setDefault(locale);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(locale);
        return base.createConfigurationContext(c);
    }
}
