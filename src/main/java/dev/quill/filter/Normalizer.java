package dev.quill.filter;

import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns what a player typed into plain lowercase letters, undoing the usual tricks: accents, full-width and
 * mathematical letters, look-alike letters from other alphabets, zero-width characters, l33t and stretched letters.
 * Each step only offers candidates, the word lists decide what counts.
 */
public final class Normalizer {

    private static final Map<Character, Character> LOOKALIKE = new HashMap<>();
    private static final Map<Character, Character> LEET = new HashMap<>();

    static {
        String[] pairs = {"аa", "еe", "оo", "кk", "рp", "сc", "уy", "хx", "іi", "јj", "ѕs", "ԁd", "ɡg", "οo", "αa", "εe", "υu",
                "κk", "νv", "ρp", "τt", "χx", "ıi", "ӏl", "һh", "ԛq", "ԝw"};
        for (String p : pairs) LOOKALIKE.put(p.charAt(0), p.charAt(1));
        String[] leet = {"0o", "3e", "4a", "5s", "7t", "8b", "@a", "$s", "!i", "+t", "¡i", "€e"};
        for (String p : leet) LEET.put(p.charAt(0), p.charAt(1));
    }

    private Normalizer() {
    }

    /** Accents and marks removed, compatibility forms unfolded, zero-width characters dropped, lowercase. */
    public static String clean(String text) {
        String s = java.text.Normalizer.normalize(text, Form.NFKD);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK) continue;
            if ((c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E) || c == 0x2060 || c == 0xFEFF || c == 0x00AD) continue;
            out.append(c);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** Look-alike letters to their latin twin, but only inside a word that already has latin letters (or is nothing but look-alikes). */
    public static String fold(String token) {
        boolean latin = false;
        boolean allLookalike = !token.isEmpty();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z') latin = true;
            if (!LOOKALIKE.containsKey(c)) allLookalike = false;
        }
        if (!latin && !allLookalike) return token;
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            sb.append(LOOKALIKE.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public static boolean isLeet(char c) {
        return LEET.containsKey(c) || c == '1';
    }

    /** l33t undone, once with 1 read as an i and once as an l. Empty when there is nothing to undo, or no letter to anchor it. */
    public static List<String> leet(String token) {
        boolean letter = false, change = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z') letter = true;
            if (isLeet(c)) change = true;
        }
        if (!letter || !change) return List.of();
        List<String> out = new ArrayList<>(2);
        for (char one : new char[]{'i', 'l'}) {
            StringBuilder sb = new StringBuilder(token.length());
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                sb.append(c == '1' ? one : LEET.getOrDefault(c, c));
            }
            String s = sb.toString();
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    /**
     * Stretched letters: every run of a repeated letter can be one, two or its own length long, so "niiigger" and
     * "fuuuck" come back to a word. A word typed with fewer letters than the list has ("niger") is never made longer.
     */
    public static Set<String> squeeze(String token) {
        Set<String> out = new LinkedHashSet<>();
        out.add(token);
        if (token.length() > 24) return out;
        List<int[]> runs = new ArrayList<>();
        for (int i = 0; i < token.length(); ) {
            int j = i;
            while (j < token.length() && token.charAt(j) == token.charAt(i)) j++;
            if (j - i >= 2 && Character.isLetter(token.charAt(i))) runs.add(new int[]{i, j - i});
            i = j;
        }
        if (runs.isEmpty()) return out;
        if (runs.size() > 4) runs = runs.subList(0, 4);
        List<String> current = List.of(token);
        // rebuild from the last run to the first so earlier offsets stay valid
        for (int r = runs.size() - 1; r >= 0; r--) {
            int start = runs.get(r)[0], len = runs.get(r)[1];
            List<String> next = new ArrayList<>();
            for (String base : current) {
                next.add(base);
                for (int want = 1; want <= 2; want++) {
                    if (want == len) continue;
                    next.add(base.substring(0, start) + String.valueOf(base.charAt(start)).repeat(want) + base.substring(start + len));
                }
            }
            current = next;
        }
        out.addAll(current);
        return out;
    }
}
