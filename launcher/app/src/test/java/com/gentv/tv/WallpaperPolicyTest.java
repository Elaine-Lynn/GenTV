package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class WallpaperPolicyTest {
    private WallpaperPolicy fresh() throws Exception { return new WallpaperPolicy(null); }
    @Test public void emptyManifestUsesNormalSixHourIntervalAcrossRestart() throws Exception {
        WallpaperPolicy p=fresh(); assertTrue(p.manifestDue(1000,WallpaperPolicy.REFRESH));
        p.manifestSuccess(1000,WallpaperPolicy.REFRESH);
        p=new WallpaperPolicy(p.bytes());
        assertFalse(p.manifestDue(121000,WallpaperPolicy.REFRESH));
        assertFalse(p.manifestDue(1000+WallpaperPolicy.REFRESH-1,WallpaperPolicy.REFRESH));
        assertTrue(p.manifestDue(1000+WallpaperPolicy.REFRESH,WallpaperPolicy.REFRESH));
    }
    @Test public void failedManifestBacksOffAndSuccessful304ResetsDelay() throws Exception {
        WallpaperPolicy p=fresh(); long hour=3600000;
        p.manifestFailure(1000); assertFalse(p.manifestDue(1000+hour-1,WallpaperPolicy.REFRESH));
        assertTrue(p.manifestDue(1000+hour,WallpaperPolicy.REFRESH));
        p.manifestFailure(1000+hour);
        assertFalse(p.manifestDue(1000+2*hour,WallpaperPolicy.REFRESH));
        p.manifestSuccess(1000+3*hour,WallpaperPolicy.REFRESH);
        assertFalse(p.manifestDue(1000+4*hour,WallpaperPolicy.REFRESH));
    }
    @Test public void quotaSurvivesRestartAndIncludesFailedDownloads() throws Exception {
        WallpaperPolicy p=fresh(); assertTrue(p.reserve(1000)); p.finish(WallpaperPolicy.MAX_IMAGE);
        p=new WallpaperPolicy(p.bytes());
        assertTrue(p.reserve(2000)); p.finish(WallpaperPolicy.MAX_IMAGE);
        assertFalse(p.reserve(3000));
        assertTrue(p.reserve(1000+WallpaperPolicy.DAY));
    }
    @Test public void interruptedProcessKeepsWorstCaseReservation() throws Exception {
        WallpaperPolicy p=fresh(); assertTrue(p.reserve(1000));
        p=new WallpaperPolicy(p.bytes()); assertTrue(p.reserve(2000));
        p=new WallpaperPolicy(p.bytes()); assertFalse(p.reserve(3000));
    }
    @Test public void unusedReservationIsRefunded() throws Exception {
        WallpaperPolicy p=fresh();
        for(int i=0;i<20;i++) {assertTrue(p.reserve(1000+i));p.finish(100);}
    }
    @Test public void clockRollbackDoesNotGrantMoreQuotaOrReplacement() throws Exception {
        WallpaperPolicy p=fresh();
        assertTrue(p.reserve(10000));assertTrue(p.reserve(10001));
        assertFalse(p.reserve(1));p.replaced(10000);assertFalse(p.mayReplace(1));
    }
    @Test public void evictedKnownVersionIsNotDownloadedAgain() throws Exception {
        WallpaperPolicy p=fresh();p.downloaded("one?v=1",1000);
        p=new WallpaperPolicy(p.bytes());p.retain(Arrays.asList("one?v=1","two?v=1"));
        assertFalse(p.mayDownload("one?v=1",1000+30*WallpaperPolicy.DAY));
        assertTrue(p.mayDownload("one?v=2",1000));
    }
    @Test public void badImagesHavePersistentExponentialCooldown() throws Exception {
        WallpaperPolicy p=fresh();p.failed("bad",1000);p=new WallpaperPolicy(p.bytes());
        assertFalse(p.mayDownload("bad",121000));assertTrue(p.mayDownload("bad",3601000));
        p.failed("bad",3601000);assertFalse(p.mayDownload("bad",7201000));
        assertTrue(p.mayDownload("bad",10801000));
    }
    @Test public void replacementLimitedToOnePer24Hours() throws Exception {
        WallpaperPolicy p=fresh();assertTrue(p.mayReplace(1000));p.replaced(1000);
        p=new WallpaperPolicy(p.bytes());assertFalse(p.mayReplace(1000+WallpaperPolicy.DAY-1));
        assertTrue(p.mayReplace(1000+WallpaperPolicy.DAY));
    }
}
