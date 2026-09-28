package dev.quill.filter;

import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns what a player typed into plain lowercase letters, undoing the usual tricks: accents, full-width and
 * mathematical letters, look-alike letters from other alphabets, invisible characters and l33t. Each step only
 * offers candidates, the word lists decide what counts.
 */
public final class Normalizer {

    private static final Map<Character, Character> LOOKALIKE = new HashMap<>();
    private static final Map<Character, Character> LEET = new HashMap<>();

    static {
        String[] pairs = {"аa", "еe", "оo", "кk", "рp", "сc", "уy", "хx", "іi", "јj", "ѕs", "ԁd", "ɡg", "οo", "αa", "εe", "υu",
                "κk", "νv", "ρp", "τt", "χx", "ıi", "ӏl", "һh", "ԛq", "ԝw", "пn", "ιi",
                // small capitals, as text generators write them
                "ᴀa", "ʙb", "ᴄc", "ᴅd", "ᴇe", "ɢg", "ʜh", "ɪi", "ᴊj", "ᴋk", "ʟl", "ᴍm", "ɴn", "ᴏo", "ᴘp", "ʀr", "ᴛt", "ᴜu", "ᴠv", "ᴡw", "ʏy", "ᴢz"};
        for (String p : pairs) LOOKALIKE.put(p.charAt(0), p.charAt(1));
        String[] leet = {"0o", "3e", "4a", "5s", "7t", "8b", "@a", "$s", "!i", "+t", "¡i", "€e", "9g", "6g", "|i"};
        for (String p : leet) LEET.put(p.charAt(0), p.charAt(1));
    }

    private Normalizer() {
    }

    /** Accents and marks removed, compatibility forms unfolded, invisible characters dropped, lowercase. */
    public static String clean(String text) {
        String s = java.text.Normalizer.normalize(text, Form.NFKD);
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (!invisible(cp)) out.appendCodePoint(cp);
        });
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** Marks, format characters (zero-width, bidi, tags) and the blank "letters" that draw nothing. */
    private static boolean invisible(int cp) {
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.FORMAT) return true;
        return cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x2800 || cp == 0x180E
                || (cp >= 0xE0000 && cp <= 0xE007F);
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

    static boolean isLeet(char c) {
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
}
