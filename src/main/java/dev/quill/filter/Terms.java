package dev.quill.filter;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * terms.yml, compiled: what is looked for and what is never a match. Words are matched as whole words, which is what
 * keeps "class" and "Scunthorpe" clean, and a few long unmistakable ones are also matched inside a word unless the
 * word is on the safe list.
 */
public final class Terms {

    /** One list entry: the word and the category it belongs to. */
    public record Ref(String word, String category) {
    }

    public record Category(String id, String label, boolean enabled) {
    }

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Ref> exact = new HashMap<>();
    private final Map<Integer, List<Ref>> byLength = new HashMap<>();
    private final List<Ref> inside = new ArrayList<>();
    private final Map<String, List<Pattern>> patterns = new LinkedHashMap<>();
    private final Set<String> safeWords = new HashSet<>();
    private final List<String> safePrefixes = new ArrayList<>();
    private Pattern safePhrases;
    private final List<String> problems = new ArrayList<>();

    public static Terms load(ConfigurationSection root) {
        Terms t = new Terms();
        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection s = cats.getConfigurationSection(id);
                if (s == null) continue;
                boolean enabled = s.getBoolean("enabled", true);
                t.categories.put(id, new Category(id, s.getString("label", id), enabled));
                if (!enabled) continue;
                for (String w : s.getStringList("words")) t.addWord(id, w, false);
                for (String w : s.getStringList("contains")) t.addWord(id, w, true);
                List<Pattern> list = new ArrayList<>();
                for (String p : s.getStringList("phrases")) {
                    String words = Normalizer.clean(p).trim().replaceAll("\\s+", " ");
                    if (!words.isEmpty()) list.add(Pattern.compile("(?<![a-z0-9])" + Pattern.quote(words).replace(" ", "\\E\\s+\\Q") + "(?![a-z0-9])"));
                }
                for (String p : s.getStringList("patterns")) {
                    try {
                        list.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
                    } catch (PatternSyntaxException e) {
                        t.problems.add(id + ": bad pattern '" + p + "': " + e.getDescription());
                    }
                }
                if (!list.isEmpty()) t.patterns.put(id, list);
            }
        }
        for (String w : root.getStringList("safe-words")) {
            String c = Normalizer.clean(w).trim();
            if (c.endsWith("*")) t.safePrefixes.add(c.substring(0, c.length() - 1));
            else if (!c.isEmpty()) t.safeWords.add(c);
        }
        List<String> phrases = new ArrayList<>();
        for (String p : root.getStringList("safe-phrases")) {
            String c = Normalizer.clean(p).trim().replaceAll("\\s+", " ");
            if (!c.isEmpty()) phrases.add(Pattern.quote(c).replace(" ", "\\E\\s+\\Q"));
        }
        if (!phrases.isEmpty()) t.safePhrases = Pattern.compile("(?<![a-z0-9])(?:" + String.join("|", phrases) + ")(?![a-z0-9])");
        return t;
    }

    private void addWord(String category, String word, boolean within) {
        String w = Normalizer.clean(word).trim();
        if (w.isEmpty() || w.contains(" ")) {
            problems.add(category + ": '" + word + "' is not a single word, put it under phrases");
            return;
        }
        Ref ref = new Ref(w, category);
        exact.putIfAbsent(w, ref);
        byLength.computeIfAbsent(w.length(), k -> new ArrayList<>()).add(ref);
        if (within) {
            if (w.length() < 5) problems.add(category + ": '" + w + "' is too short to be matched inside words");
            else inside.add(ref);
        }
    }

    public Map<String, Category> categories() {
        return categories;
    }

    public List<String> problems() {
        return problems;
    }

    public Ref exact(String word) {
        return exact.get(word);
    }

    public List<Ref> ofLength(int length) {
        return byLength.getOrDefault(length, List.of());
    }

    public List<Ref> inside() {
        return inside;
    }

    public Map<String, List<Pattern>> patterns() {
        return patterns;
    }

    public boolean isSafe(String word) {
        if (safeWords.contains(word)) return true;
        for (String p : safePrefixes) if (word.startsWith(p)) return true;
        return false;
    }

    /** The text with the safe phrases (a "chink in the armor") blanked out. */
    public String withoutSafePhrases(String text) {
        return safePhrases == null ? text : safePhrases.matcher(text).replaceAll(" ");
    }

    public int wordCount() {
        return exact.size();
    }
}
