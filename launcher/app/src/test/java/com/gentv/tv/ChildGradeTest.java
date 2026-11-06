package com.gentv.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChildGradeTest {
    @Test public void sharedGradeTakesPrecedenceOverPreviousSubjectGrades() {
        assertEquals(4, ChildGrade.resolve(4, 3, 1));
    }

    @Test public void previousMathChoiceMigratesWhenNoSharedGradeExists() {
        assertEquals(3, ChildGrade.resolve(-1, 3, 1));
        assertEquals(2, ChildGrade.resolve(-1, -1, 2));
        assertEquals(3, ChildGrade.resolve(-1, -1, -1));
    }

    @Test public void gradeStaysInsideTheSixSupportedLevels() {
        assertEquals(1, ChildGrade.resolve(0, 3, 1));
        assertEquals(6, ChildGrade.resolve(7, 3, 1));
    }
}
