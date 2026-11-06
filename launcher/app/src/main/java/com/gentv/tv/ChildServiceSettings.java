package com.gentv.tv;

/** Preserves other accessibility services and guards both writes and rollback against intervening edits. */
final class ChildServiceSettings {
    private ChildServiceSettings() {}

    static String add(String before, String pkg) {
        String target = pkg + "/com.gentv.tv.ChildWatchService";
        if (before == null || before.isEmpty() || before.equals("null")) return target;
        for (String component : before.split(":")) {
            if (component.equals(target) || component.equals(pkg + "/.ChildWatchService")) return before;
        }
        return before + ":" + target;
    }

    static String enable(String before, String global, String updated) {
        return "if [ \"$(settings get secure enabled_accessibility_services)\" = " + quote(before)
                + " ] && [ \"$(settings get secure accessibility_enabled)\" = " + quote(global)
                + " ]; then settings put secure enabled_accessibility_services " + quote(updated)
                + " && settings put secure accessibility_enabled 1 && echo GENTV_CHILD_ENABLED; fi";
    }

    static String rollback(String before, String global, String updated) {
        return "if [ \"$(settings get secure enabled_accessibility_services)\" = " + quote(updated)
                + " ] && [ \"$(settings get secure accessibility_enabled)\" = '1' ]; then "
                + restore("enabled_accessibility_services", before) + " && "
                + restore("accessibility_enabled", global) + " && echo GENTV_CHILD_RESTORED; fi";
    }

    private static String restore(String key, String value) {
        return value.equals("null") ? "settings delete secure " + key
                : "settings put secure " + key + " " + quote(value);
    }

    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
