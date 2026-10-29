package com.gentv.tv;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class AerialCaptionsTest {
    @Test public void usesPlaybackCueAtItsExactBoundary() {
        AerialCaptions captions = new AerialCaptions("New Zealand", Arrays.asList(
                new AerialCaptions.Cue(0, "日出", "Sunrise"),
                new AerialCaptions.Cue(170, "离开新西兰，越过太平洋前往东北方向", "Leaving New Zealand")));
        assertEquals("日出", captions.at(169999, true));
        assertEquals("离开新西兰，越过太平洋前往东北方向", captions.at(170000, true));
        assertEquals("Leaving New Zealand", captions.at(170001, false));
    }
    @Test public void labelIsUsedBeforeFirstCueOrWithNoCaptions() {
        assertEquals("Place", new AerialCaptions("Place", Collections.emptyList()).at(0, true));
        AerialCaptions delayed = new AerialCaptions("Place", Collections.singletonList(new AerialCaptions.Cue(20,"中文","English")));
        assertEquals("Place", delayed.at(19999, true));
        assertEquals("Place", delayed.at(-100, true));
    }
    @Test public void missingTranslationFallsBackToAvailableText() {
        AerialCaptions captions = new AerialCaptions("Place", Collections.singletonList(new AerialCaptions.Cue(0,"中文","")));
        assertEquals("中文", captions.at(0, false));
    }
    @Test public void outOfOrderCuesStillFollowPlaybackPosition() {
        AerialCaptions captions = new AerialCaptions("Place", Arrays.asList(
                new AerialCaptions.Cue(30,"后","Later"),new AerialCaptions.Cue(0,"前","Earlier")));
        assertEquals("Earlier",captions.at(0,false));
        assertEquals("Later",captions.at(30000,false));
    }
}
