package com.gentv.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class ShareCodeLinksTest {
    @Test public void freshCodesAreFourDigits() {
        Random random = new Random(7);
        Set<String> used = new HashSet<>();
        for (int index = 0; index < 200; index++) {
            String code = ShareCodeLinks.nextCode(used, random);
            assertTrue(code, code.matches("[0-9]{4}"));
            assertTrue(used.add(code));
        }
    }

    @Test public void oneLetterAppearsOnlyAfterEveryDigitCodeIsTaken() {
        Set<String> used = new HashSet<>();
        for (int value = 0; value < 10_000; value++) used.add(String.format(Locale.ROOT, "%04d", value));
        Random random = new Random(11);
        for (int index = 0; index < 50; index++) {
            String code = ShareCodeLinks.nextCode(used, random);
            assertEquals(code, 1, ShareCodeLinks.letterCount(code));
            assertTrue(ShareCodeLinks.validCode(code));
            assertTrue(used.add(code));
        }
    }

    @Test public void lastFreeDigitCodeIsFoundBeforeLetters() {
        Set<String> used = new HashSet<>();
        for (int value = 0; value < 10_000; value++) used.add(String.format(Locale.ROOT, "%04d", value));
        used.remove("4821");
        assertEquals("4821", ShareCodeLinks.nextCode(used, new Random(3)));
    }

    @Test public void validCodesAreFourKeypadCharactersWithoutIOrO() {
        assertTrue(ShareCodeLinks.validCode("0427"));
        assertTrue(ShareCodeLinks.validCode("12A9"));
        assertFalse(ShareCodeLinks.validCode("ABC123"));
        assertFalse(ShareCodeLinks.validCode("12I4"));
        assertFalse(ShareCodeLinks.validCode("O123"));
        assertFalse(ShareCodeLinks.validCode("12a4"));
    }
}
