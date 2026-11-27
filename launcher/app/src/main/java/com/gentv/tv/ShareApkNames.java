package com.gentv.tv;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns shared APK file names such as "七猫免费小说v7.14.apk" into a display name and version. */
final class ShareApkNames {
    private static final Pattern VERSIONED =
            Pattern.compile("^(.+?)[\\s_-]*[vV]?(\\d+(?:\\.\\d+)+)[\\s_-]*(.*)$");

    final String title;
    /** "v7.14", "v4.8.6.1 VIP版", or empty when the file name carries no version. */
    final String version;

    private ShareApkNames(String title, String version) {
        this.title = title;
        this.version = version;
    }

    static ShareApkNames parse(String fileName) {
        String base = fileName.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".apk")) base = base.substring(0, base.length() - 4).trim();
        if (base.isEmpty()) return new ShareApkNames(fileName, "");
        Matcher match = VERSIONED.matcher(base);
        if (!match.matches()) return new ShareApkNames(base, "");
        String title = match.group(1).replaceAll("[\\s._-]+$", "");
        if (title.isEmpty()) return new ShareApkNames(base, "");
        String extra = match.group(3).replace('_', ' ').trim();
        String version = "v" + match.group(2) + (extra.isEmpty() ? "" : " " + extra);
        return new ShareApkNames(title, version);
    }
}
