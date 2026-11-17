package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputProfileMatchTest {
    @Test public void brandAndGenericModelCannotActivateVendorRouting() {
        assertFalse(InputProfileMatch.matches("tcl-55p638", "TCL 55P638", "Smart TV", "unknown", "unknown", "TCL", "TCL"));
        assertFalse(InputProfileMatch.matches("tcl-55p638", "TCL 55P638", "55P639", "unknown", "unknown", "TCL", "TCL"));
        assertFalse(InputProfileMatch.matches("tcl-55p638", "TCL 55P638 sharedboard", "55P638", "sharedboard", "unknown", "Other", "Other"));
    }
    @Test public void exactModelsAndCodenamesCanSelectAProfile() {
        assertTrue(InputProfileMatch.matches("skyworth-7s60-a5", "Skyworth 7S60_A5 sky8S34", "7S60_A5", "sky8S34", "unknown", "Skyworth", "Skyworth"));
        assertTrue(InputProfileMatch.matches("tcl-tc8000-ay30a2", "TCL tc8000_ay30a2", "Smart TV", "tc8000_ay30a2", "unknown", "TCL", "TCL"));
    }
}
