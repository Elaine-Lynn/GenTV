package com.gentv.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class MetricsClockTest {
    @Test public void monotonicDeltasDoNotDoubleCountOrIncludeBackground() {
        MetricsClock c=new MetricsClock();assertEquals(0,c.sample(900));c.start(1000);
        assertEquals(0,c.sample(1400));assertEquals(1,c.sample(2200));assertEquals(0,c.sample(2200));
        c.stop();assertEquals(0,c.sample(100000));c.start(100000);assertEquals(60,c.sample(160000));
    }
    @Test public void delayedOrInvalidTicksAreBounded() {
        MetricsClock c=new MetricsClock();c.start(1000);assertEquals(0,c.sample(500));assertEquals(120,c.sample(1000000));
    }
}
