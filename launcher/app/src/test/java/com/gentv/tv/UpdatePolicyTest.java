package com.gentv.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePolicyTest {
    @Test public void optionalAndCurrentNeverBlock() { assertFalse(UpdatePolicy.required(15,16,0,false)); assertFalse(UpdatePolicy.required(16,16,16,true)); }
    @Test public void minimumAndForcedReleaseBlockOldVersions() { assertTrue(UpdatePolicy.required(15,16,16,false)); assertTrue(UpdatePolicy.required(15,16,0,true)); assertFalse(UpdatePolicy.required(15,16,15,false)); }
    @Test(expected=IllegalArgumentException.class) public void inconsistentMinimumIsRejected() { UpdatePolicy.required(15,16,17,true); }
    @Test public void oldReleaseWithHigherSdkIsStillCurrent() { assertTrue(UpdatePolicy.compatible(35,34,26,19)); }
    @Test public void newerReleaseMustSupportDeviceSdk() { assertFalse(UpdatePolicy.compatible(35,36,26,19)); assertTrue(UpdatePolicy.compatible(35,36,17,19)); }
}
