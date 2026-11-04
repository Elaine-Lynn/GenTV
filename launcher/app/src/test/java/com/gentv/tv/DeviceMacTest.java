package com.gentv.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceMacTest {
    @Test public void normalizesFactoryAddresses() {
        assertEquals("00:1a:2b:3c:4d:5e",DeviceMac.normalize("00-1A-2B-3C-4D-5E"));
        assertEquals("00:1a:2b:3c:4d:5e",DeviceMac.normalize("001a2b3c4d5e"));
    }
    @Test public void rejectsUnavailableRandomAndSharedAddresses() {
        for (String value : new String[] {null,"","bad","00:00:00:00:00:00","02:00:00:00:00:00","ff:ff:ff:ff:ff:ff","da:11:22:33:44:55","01:11:22:33:44:55"})
            assertEquals("",DeviceMac.normalize(value));
    }
}
