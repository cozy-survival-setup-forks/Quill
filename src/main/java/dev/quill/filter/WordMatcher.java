package dev.quill.filter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds listed words and phrases. A word counts only as a whole word after undoing the tricks (l33t, look-alike
 * letters, stretched letters, dots between letters), so a word that merely contains a listed one is left alone.
 */
public final class WordMatcher {

    private static final String KEEP_AT_EDGES = "@$*#%";
    private static final String SEPARATORS = "._-|/\\,~^:;";
    private static final String WILDCARDS = "*#%?";

    private final Terms terms;

    public WordMatcher(Terms terms) {
        this.terms = terms;
    }

    /**
     * @param cleaned  the message after {@link Normalizer#clean}
     * @param names    lowercase names of the players who are online: a name is never a match
     * @param skip     categories this player may say
     */
    public Verdict match(String cleaned, Set<String> names, Set<String> skip) {
        String text = terms.withoutSafePhrases(cleaned);
        String[] raw = text.trim().split("\\s+");
        List<String> tokens = new ArrayList<>(raw.length);
        for (String r : raw) {
            String t = strip(r);
            if (!t.isEmpty()) tokens.add(t);
        }

        // whole words
        for (String token : tokens) {
            if (names.contains(token) || token.startsWith("@") && names.contains(token.substring(1))) continue;
            Verdict v = matchToken(token, skip);
            if (v != null) return v;
        }

        // letters typed one at a time: n i g g e r
        int i = 0;
        while (i < tokens.size()) {
            int j = i;
            StringBuilder joined = new StringBuilder();
            while (j < tokens.size() && tokens.get(j).length() == 1 && Character.isLetterOrDigit(tokens.get(j).charAt(0))) {
                joined.append(tokens.get(j));
                j++;
            }
            if (j - i >= 3) {
                Verdict v = matchToken(joined.toString(), skip);
                if (v != null) return v;
            }
            i = Math.max(j, i + 1);
        }

        // phrases and patterns, on the text with l33t undone
        String phrases = phraseText(tokens);
        for (var entry : terms.patterns().entrySet()) {
            if (skip.contains(entry.getKey())) continue;
            for (Pattern p : entry.getValue()) {
                Matcher m = p.matcher(phrases);
                if (m.find()) return new Verdict(entry.getKey(), "phrase", m.group().trim());
            }
        }
        return null;
    }

    private static String strip(String token) {
        int a = 0, b = token.length();
        while (a < b && !keep(token.charAt(a))) a++;
        while (b > a && !keep(token.charAt(b - 1))) b--;
        return token.substring(a, b);
    }

    private static boolean keep(char c) {
        return Character.isLetterOrDigit(c) || KEEP_AT_EDGES.indexOf(c) >= 0;
    }

    private String phraseText(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            String f = Normalizer.fold(t);
            List<String> l = Normalizer.leet(f);
            if (sb.length() > 0) sb.append(' ');
            sb.append(l.isEmpty() ? f : l.get(0));
        }
        return sb.toString();
    }

    private Verdict matchToken(String token, Set<String> skip) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(token);
        // *word* is emphasis
        String bare = token;
        while (bare.length() > 2 && bare.startsWith("*") && bare.endsWith("*")) bare = bare.substring(1, bare.length() - 1);
        candidates.add(bare);
        // n.i.g.g.e.r, ni-gger, and every part of a word joined with dashes
        for (String c : List.copyOf(candidates)) addSeparated(c, candidates);

        for (String c : candidates) {
            if (c.isEmpty() || c.length() > 40) continue;
            String folded = Normalizer.fold(c);
            Set<String> forms = new LinkedHashSet<>();
            forms.add(folded);
            forms.addAll(Normalizer.leet(folded));
            Set<String> all = new LinkedHashSet<>();
            for (String f : forms) all.addAll(Normalizer.squeeze(f));

            for (String form : all) {
                Terms.Ref ref = terms.exact(form);
                if (ref != null && !skip.contains(ref.category())) return new Verdict(ref.category(), "word: " + ref.word(), token);
            }
            for (String form : forms) {
                Verdict v = wildcard(form, token, skip);
                if (v != null) return v;
                v = inside(form, token, skip);
                if (v != null) return v;
            }
        }
        return null;
    }

    private void addSeparated(String token, Set<String> out) {
        boolean has = false;
        for (int i = 1; i < token.length() - 1; i++) {
            if (SEPARATORS.indexOf(token.charAt(i)) >= 0) {
                has = true;
                break;
            }
        }
        if (!has) return;
        StringBuilder joined = new StringBuilder();
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (SEPARATORS.indexOf(c) >= 0) {
                if (part.length() > 0) out.add(part.toString());
                part.setLength(0);
            } else {
                part.append(c);
                joined.append(c);
            }
        }
        if (part.length() > 0) out.add(part.toString());
        out.add(joined.toString());
    }

    /** f*ck and sh#t: one or two masked letters in a word of four or more that is on the list. */
    private Verdict wildcard(String form, String token, Set<String> skip) {
        int wild = 0, letters = 0;
        for (int i = 0; i < form.length(); i++) {
            char c = form.charAt(i);
            if (WILDCARDS.indexOf(c) >= 0) wild++;
            else if (Character.isLetter(c)) letters++;
        }
        if (wild == 0 || wild > 2 || form.length() < 4 || letters < 2 || form.startsWith("*") && form.endsWith("*")) return null;
        for (Terms.Ref ref : terms.ofLength(form.length())) {
            if (skip.contains(ref.category())) continue;
            String w = ref.word();
            boolean ok = true;
            for (int i = 0; i < w.length() && ok; i++) {
                char c = form.charAt(i);
                if (WILDCARDS.indexOf(c) < 0 && c != w.charAt(i)) ok = false;
            }
            if (ok) return new Verdict(ref.category(), "masked: " + w, token);
        }
        return null;
    }

    /** The few long words that are matched inside a longer one, unless that longer one is a real word ("snigger"). */
    private Verdict inside(String form, String token, Set<String> skip) {
        if (form.length() < 5) return null;
        for (Terms.Ref ref : terms.inside()) {
            if (skip.contains(ref.category()) || !form.contains(ref.word())) continue;
            if (terms.isSafe(form)) continue;
            return new Verdict(ref.category(), "inside a word: " + ref.word(), token);
        }
        return null;
    }

    /** For tests and /quill filter test: the tokens as the matcher sees them. */
    static List<String> tokensOf(String cleaned) {
        List<String> out = new ArrayList<>();
        for (String r : cleaned.trim().split("\\s+")) {
            String t = strip(r);
            if (!t.isEmpty()) out.add(t.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
