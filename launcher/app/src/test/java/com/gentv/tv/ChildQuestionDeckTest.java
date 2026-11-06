package com.gentv.tv;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class ChildQuestionDeckTest {
    @Test public void everyQuestionAppearsOnceBeforeDeckExhaustion() {
        for (int size : new int[]{1, 3, 200, 2000}) {
            List<Integer> order = ChildQuestionDeck.order(size, 8342);
            assertEquals(size, new HashSet<>(order).size());
            for (int i = 0; i < size; i++) assertTrue(order.contains(i));
        }
    }

    @Test public void savedSeedAndCursorRecreateRemainingOrderAfterRestart() {
        List<Integer> original = ChildQuestionDeck.order(200, 8342);
        List<Integer> restored = ChildQuestionDeck.order(200, 8342);
        assertEquals(original.subList(73, 200), restored.subList(73, 200));
        assertNotEquals(original, ChildQuestionDeck.order(200, 8343));
    }
}
