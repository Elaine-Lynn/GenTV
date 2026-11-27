package com.gentv.game.kart;

import com.gentv.game.api.GameContentEntry;

/** Independently downloaded kart content, reusing the foundation's browser and LAN transport. */
public final class KartGamePlugin implements GameContentEntry {
    @Override public String id() { return "kart"; }
    @Override public String entryUrl() { return "https://kart.igame86.com/play/"; }
    @Override public String remoteAsset() { return "game_kart_remote.html"; }
}
