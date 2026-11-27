package com.gentv.game.api;

/** Contract owned by the foundation plugin; individual games compile against, but do not bundle it. */
public interface GameContentEntry {
    int API_VERSION = 1;
    String id();
    String entryUrl();
    String remoteAsset();
}
