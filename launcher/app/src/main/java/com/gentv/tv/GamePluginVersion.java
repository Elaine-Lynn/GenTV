package com.gentv.tv;

/** Plugin releases have their own version sequence and a stable host API contract. */
final class GamePluginVersion {
    static final int HOST_API = GameRuntimeEntry.API_VERSION;

    static boolean compatible(long version, int api, long minHost, long maxHost, long host) {
        return version > 0 && api == HOST_API && minHost > 0 && maxHost >= minHost
                && host >= minHost && host <= maxHost;
    }

    static boolean newer(long installed, long offered) { return offered > installed; }
}
