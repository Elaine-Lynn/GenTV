package com.gentv.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Place descriptions change with the film's playback position, not the wall clock. */
final class AerialCaptions {
    static final class Cue {
        final int seconds;
        final String chinese, english;
        Cue(int seconds, String chinese, String english) {
            this.seconds = seconds; this.chinese = chinese; this.english = english;
        }
    }
    private final String label;
    private final List<Cue> cues;

    AerialCaptions(String label, List<Cue> cues) {
        this.label = label;
        this.cues = new ArrayList<>(cues);
        this.cues.sort(Comparator.comparingInt(cue -> cue.seconds));
    }

    String at(int positionMs, boolean chinese) {
        String text = label;
        for (Cue cue : cues) {
            if (cue.seconds * 1000L > Math.max(0, positionMs)) break;
            String localized = chinese ? cue.chinese : cue.english;
            if (localized.isEmpty()) localized = chinese ? cue.english : cue.chinese;
            if (!localized.isEmpty()) text = localized;
        }
        return text;
    }
}
