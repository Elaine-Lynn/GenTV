package com.gentv.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.Random;

/** Bundled questions. A saved seed and attempt recreate the same prompt and option order. */
final class ChildQuestions {
    record Question(String prompt, List<String> options, int correct) {}

    private static final String[][] WORDS = {
            {"picture:apple|apple|shoe|dog|book",
             "picture:dog|dog|duck|fish|bird",
             "picture:chair|chair|bag|cat|cup"},
            {"picture:blue|blue|yellow|green|red",
             "picture:fish|fish|mouse|bear|duck",
             "picture:umbrella|umbrella|basket|window|spoon"},
            {"I hear with my ___.|ears|eyes|hands|feet",
             "I write with a ___.|pen|cup|shoe|ball",
             "I see with my ___.|eyes|ears|hands|feet"},
            {"A week has seven ___.|days|hours|months|minutes",
             "We borrow books at the ___.|library|station|hospital|park",
             "We wash our hands with soap and ___.|water|salt|sand|rice"},
            {"The train leaves at eight. We got there at seven. We were ___.|early|late|slow|loud",
             "Which word means having nothing inside?|empty|full|open|heavy",
             "She checked every answer. She was ___.|careful|careless|noisy|hurried"},
            {"The bag is light. It is easy to carry. What does light mean?|not heavy|bright|dark|warm",
             "The music was too loud. Turn it ___.|down|on|up|around",
             "We missed the bus. What does missed mean?|did not catch|remembered|washed|repaired"}
    };

    private ChildQuestions() {}

    static Question create(boolean words, int level, long seed, int attempt) {
        Random random = new Random(seed + attempt);
        int grade = Math.max(1, Math.min(6, level));
        if (words) {
            int first = new Random(seed).nextInt(WORDS[grade - 1].length);
            String[] entry = WORDS[grade - 1][Math.floorMod(first + attempt, WORDS[grade - 1].length)].split("\\|");
            return shuffle(entry[0], new ArrayList<>(Arrays.asList(entry).subList(1, 5)), random);
        }

        // Rotate templates within a round before reusing one.
        int variant = Math.floorMod(new Random(seed).nextInt(3) + attempt, 3);
        int a = 2 + random.nextInt(8);
        int b = 2 + random.nextInt(8);
        int c = 2 + random.nextInt(5);
        return switch (grade) {
            case 1 -> switch (variant) {
                case 0 -> numeric(a + " + " + b + " = ?", a + b, random);
                case 1 -> numeric((a + b) + " − " + a + " = ?", b, random);
                default -> numeric("? + " + a + " = " + (a + b), b, random);
            };
            case 2 -> switch (variant) {
                case 0 -> numeric((a * 5 + 3) + " + " + (b * 4 + 1) + " = ?", a * 5 + b * 4 + 4, random);
                case 1 -> numeric(a + " × " + b + " = ?", a * b, random);
                default -> numeric((a * b) + " ÷ " + a + " = ?", b, random);
            };
            case 3 -> switch (variant) {
                case 0 -> numeric((200 + a * 13) + " − " + (100 + b * 7) + " = ?", 100 + a * 13 - b * 7, random);
                case 1 -> numeric((a * 3 + 12) + " × " + b + " = ?", (a * 3 + 12) * b, random);
                default -> numeric("(" + (a * b) + " + " + (a * c) + ") ÷ " + a + " = ?", b + c, random);
            };
            case 4 -> switch (variant) {
                case 0 -> numeric((a + 12) + " × " + (b + 12) + " = ?", (a + 12) * (b + 12), random);
                case 1 -> numeric(((a + 10) * (b + 10)) + " ÷ " + (a + 10) + " = ?", b + 10, random);
                default -> numeric(decimal(a * 12) + " + " + decimal(b * 5) + " = ?", a * 12 + b * 5,
                        random, ChildQuestions::decimal);
            };
            case 5 -> switch (variant) {
                case 0 -> numeric(decimal(a * 10) + " × " + decimal(b * 10) + " = ?", a * b,
                        random, ChildQuestions::decimal);
                case 1 -> numeric(a + "/12 + " + b + "/6 = ?", a + 2 * b, random, value -> value + "/12");
                default -> numeric("(" + decimal(a * 10) + " + " + decimal(b * 10) + ") ÷ 0.1 = ?", a + b, random);
            };
            default -> switch (variant) {
                case 0 -> numeric(a + "/12 × " + b + "/5 = ?", a * b, random, value -> value + "/60");
                case 1 -> numeric((a * 20) + " × " + (b * 5) + "% = ?", a * b, random);
                default -> numeric(a + "/12 ÷ " + b + "/6 = ?", a, random, value -> value + "/" + (b * 2));
            };
        };
    }

    private static String decimal(int hundredths) {
        return java.math.BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString();
    }

    private static Question numeric(String prompt, int answer, Random random) {
        return numeric(prompt, answer, random, Integer::toString);
    }

    private static Question numeric(String prompt, int answer, Random random, IntFunction<String> format) {
        List<Integer> distractors = new ArrayList<>();
        for (int candidate = Math.max(0, answer - 6); candidate <= answer + 6; candidate++) {
            if (candidate != answer) distractors.add(candidate);
        }
        Collections.shuffle(distractors, random);
        List<String> options = new ArrayList<>();
        options.add(format.apply(answer));
        for (int i = 0; i < 3; i++) options.add(format.apply(distractors.get(i)));
        return shuffle(prompt, options, random);
    }

    private static Question shuffle(String prompt, List<String> options, Random random) {
        String solution = options.get(0);
        Collections.shuffle(options, random);
        return new Question(prompt, Collections.unmodifiableList(options), options.indexOf(solution));
    }
}
