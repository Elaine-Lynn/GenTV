package com.gentv.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** A deterministic permutation; persisting seed and cursor is enough to resume a deck. */
final class ChildQuestionDeck {
    private ChildQuestionDeck() {}

    static List<Integer> order(int count, long seed) {
        List<Integer> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(i);
        Collections.shuffle(result, new Random(seed));
        return result;
    }
}
