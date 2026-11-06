package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, checksummed offline packs. Unreviewed vocabulary is preview-only. */
final class ChildQuestionBank {
    private static final String ROOT = "child-questions/";
    private record Entry(String id, String status, String promptEn, String promptZh,
                         List<String> optionsEn, List<String> optionsZh, int correct,
                         String explanationEn, String explanationZh) {}
    private record Pack(String version, String hash, List<Entry> entries) {}
    private static final Map<String, Pack> CACHE = new LinkedHashMap<String, Pack>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Pack> entry) { return size() > 4; }
    };

    record Selection(ChildQuestions.Question question, String id, String version, String explanation,
                     String deckKey, long seed, int nextCursor) {
        void save(SharedPreferences.Editor editor) {
            editor.putString("question_id", id).putString("question_bank_version", version);
            if (!deckKey.isEmpty()) editor.putLong(deckKey + ".seed", seed)
                    .putInt(deckKey + ".cursor", nextCursor).putString(deckKey + ".last", id);
        }
    }

    private ChildQuestionBank() {}

    static Selection select(Context context, SharedPreferences state, boolean words, int grade,
                            long seed, int attempt, boolean preview) {
        try {
            Pack pack = load(context, words, Math.max(1, Math.min(6, grade)));
            List<Entry> eligible = new ArrayList<>();
            for (Entry entry : pack.entries()) {
                if (preview || entry.status().equals("reviewed")
                        || !words && entry.status().equals("automated_verified")) eligible.add(entry);
            }
            boolean legacy = words && !preview && eligible.size() < 3;
            if (legacy) {
                eligible.clear();
                for (int i = 0; i < 3; i++) {
                    ChildQuestions.Question question = ChildQuestions.create(true, grade, 0, i);
                    eligible.add(new Entry("legacy-en-g" + grade + "-" + i, "legacy", question.prompt(),
                            question.prompt(), question.options(), question.options(), question.correct(), "", ""));
                }
            }
            if (eligible.isEmpty()) return null;
            String key = "question_deck." + (words ? "en" : "math") + "." + grade + "." + pack.hash().substring(0, 12) + (legacy ? ".legacy" : ".pack");
            long deckSeed = preview ? seed : state.getLong(key + ".seed", seed);
            int cursor = preview ? Math.floorMod(attempt, eligible.size()) : state.getInt(key + ".cursor", 0);
            List<Integer> order;
            if (cursor < 0 || cursor >= eligible.size()) {
                cursor = 0;
                deckSeed = seed;
                String previous = state.getString(key + ".last", "");
                do {
                    order = ChildQuestionDeck.order(eligible.size(), deckSeed++);
                } while (eligible.size() > 1 && eligible.get(order.get(0)).id().equals(previous));
                deckSeed--;
            } else order = ChildQuestionDeck.order(eligible.size(), deckSeed);
            if (preview && words && grade <= 2) {
                List<Integer> pictures = new ArrayList<>();
                List<Integer> text = new ArrayList<>();
                for (int index : order) {
                    (eligible.get(index).promptEn().startsWith("picture:") ? pictures : text).add(index);
                }
                order = new ArrayList<>();
                for (int i = 0; i < Math.max(pictures.size(), text.size()); i++) {
                    if (i < pictures.size()) order.add(pictures.get(i));
                    if (i < text.size()) order.add(text.get(i));
                }
            }
            Entry entry = eligible.get(order.get(cursor));
            boolean chinese = context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
            List<String> options = new ArrayList<>(chinese ? entry.optionsZh() : entry.optionsEn());
            // Order is stable for the saved round and changes between newly generated rounds.
            String answer = options.get(entry.correct());
            Collections.shuffle(options, new java.util.Random(seed + attempt));
            return new Selection(new ChildQuestions.Question(chinese ? entry.promptZh() : entry.promptEn(),
                    Collections.unmodifiableList(options), options.indexOf(answer)), entry.id(), legacy ? "legacy-18" : pack.version(),
                    chinese ? entry.explanationZh() : entry.explanationEn(), preview ? "" : key, deckSeed, cursor + 1);
        } catch (Exception failure) {
            Log.w("GenTVChildQuestions", "Question pack unavailable; using bundled fallback", failure);
            return null;
        }
    }

    private static synchronized Pack load(Context context, boolean words, int grade) throws Exception {
        String name = (words ? "english" : "math") + "-g" + grade + ".jsonl";
        Pack cached = CACHE.get(name);
        if (cached != null) return cached;
        JSONObject manifest;
        try (InputStream input = context.getAssets().open(ROOT + "manifest.json")) {
            manifest = new JSONObject(new String(readBytes(input), StandardCharsets.UTF_8));
        }
        JSONObject metadata = manifest.getJSONObject("files").getJSONObject(name);
        byte[] data;
        try (InputStream input = context.getAssets().open(ROOT + name)) { data = readBytes(input); }
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(data)) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!hex.toString().equals(metadata.getString("sha256")) || data.length != metadata.getInt("bytes")) {
            throw new IllegalArgumentException("Question pack checksum mismatch");
        }
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            java.util.Set<String> ids = new java.util.HashSet<>();
            while ((line = reader.readLine()) != null) {
                JSONObject row = new JSONObject(line);
                if (row.getInt("grade") != grade || !row.getString("subject").equals(words ? "english" : "math")
                        || !row.getString("version").equals(manifest.getString("version"))
                        || !ids.add(row.getString("id"))) throw new IllegalArgumentException("Invalid question identity");
                int correct = row.getInt("correct");
                if (correct < 0 || correct >= 4) throw new IllegalArgumentException("Invalid answer index");
                entries.add(new Entry(row.getString("id"), row.getString("review_status"),
                        row.getString("prompt_en"), row.getString("prompt_zh"), strings(row.getJSONArray("options_en")),
                        strings(row.getJSONArray("options_zh")), correct, row.getString("explanation_en"), row.getString("explanation_zh")));
            }
        }
        if (entries.size() != metadata.getInt("count")) throw new IllegalArgumentException("Question count mismatch");
        Pack pack = new Pack(manifest.getString("version"), hex.toString(), Collections.unmodifiableList(entries));
        CACHE.put(name, pack);
        return pack;
    }

    private static byte[] readBytes(InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static List<String> strings(JSONArray array) throws Exception {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
        if (values.size() != 4 || new java.util.HashSet<>(values).size() != 4) throw new IllegalArgumentException("Invalid options");
        return Collections.unmodifiableList(values);
    }
}
