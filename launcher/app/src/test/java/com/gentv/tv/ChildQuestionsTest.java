package com.gentv.tv;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

public class ChildQuestionsTest {
    @Test public void everyGradeIsRepeatableAndHasFourUniqueOptions() {
        for (boolean words : new boolean[]{false, true}) {
            for (int grade = 1; grade <= 6; grade++) {
                for (long seed = 0; seed < 200; seed++) {
                    ChildQuestions.Question question = ChildQuestions.create(words, grade, seed, 1);
                    assertEquals(question, ChildQuestions.create(words, grade, seed, 1));
                    assertEquals(4, question.options().size());
                    assertEquals(4, new HashSet<>(question.options()).size());
                    assertTrue(question.correct() >= 0 && question.correct() < 4);
                }
            }
        }
    }

    @Test public void vocabularyRetriesUseDifferentPrompts() {
        for (int grade = 1; grade <= 6; grade++) {
            HashSet<String> prompts = new HashSet<>();
            for (int attempt = 0; attempt < 3; attempt++) {
                prompts.add(ChildQuestions.create(true, grade, 33, attempt).prompt());
            }
            assertEquals(3, prompts.size());
        }
    }
}
