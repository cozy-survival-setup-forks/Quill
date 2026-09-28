package dev.quill.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Finds advertising: links, domains, server addresses and the phrases people use to pull others away. A bare
 * "something.tld" only counts when the ending is one that ads really use and the name in front of it is at least
 * three letters, so a sentence typed without a space ("ok.no") is left alone.
 */
public final class AdMatcher {

    private static final Pattern SCHEME_URL = Pattern.compile("(?:https?://|ftp://|www\\.)[^\\s<>\"']+");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)((?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})(?::(\\d{1,5}))?(?![\\d]|\\.\\d)");

    private static final Pattern SPACED_DOT = Pattern.compile("(?<=[a-z0-9])\\s+\\.\\s+(?=[a-z0-9])");

    private final Set<String> allowed;
    private final boolean blockIps;
    private final Pattern domain;
    private final Pattern spacedDomain;
    private final List<Pattern> phrases = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public AdMatcher(Set<String> allowedDomains, Set<String> tlds, boolean blockIps, List<String> phrasePatterns) {
        this.allowed = allowedDomains;
        this.blockIps = blockIps;
        String tld = String.join("|", tlds.stream().map(t -> Pattern.quote(t.toLowerCase(Locale.ROOT))).toList());
        if (tld.isEmpty()) tld = "(?!)";
        this.domain = Pattern.compile("(?<![a-z0-9])((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:" + tld + "))(?![a-z0-9-])(?::\\d{1,5})?(?:/[^\\s]*)?");
        this.spacedDomain = Pattern.compile("(?<![a-z0-9])((?:[a-z0-9-]{2,}\\s*(?:\\(dot\\)|\\[dot\\]|\\{dot\\}|\\s+dot\\s+|\\sd0t\\s)\\s*)+)(?:" + tld + ")(?![a-z0-9-])");
        for (String p : phrasePatterns) {
            try {
                phrases.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                problems.add("advertising phrase '" + p + "': " + e.getDescription());
            }
        }
    }

    public List<String> problems() {
        return problems;
    }

    /**
     * @param cleaned    the message after {@link Normalizer#clean}
     * @param allowLinks the player may post links (they still cannot post an IP or use ad phrases)
     */
    public Verdict match(String cleaned, boolean allowLinks) {
        String text = readDots(foldWords(cleaned));

        if (!allowLinks) {
            Matcher m = SCHEME_URL.matcher(text);
            while (m.find()) {
                String host = hostOf(m.group());
                if (!isAllowed(host)) return new Verdict("advertising", "link", m.group());
            }
            m = domain.matcher(text);
            while (m.find()) {
                String host = m.group(1);
                if (isAllowed(host) || registrableLabel(host).length() < 3) continue;
                return new Verdict("advertising", "domain", m.group());
            }
            m = spacedDomain.matcher(text);
            if (m.find()) return new Verdict("advertising", "spelled-out domain", m.group().trim());
        }

        if (blockIps) {
            Matcher m = IPV4.matcher(text);
            while (m.find()) {
                if (isVersionOrLocal(m.group(1), m.group(2))) continue;
                return new Verdict("advertising", "server address", m.group());
            }
        }

        for (Pattern p : phrases) {
            Matcher m = p.matcher(text);
            if (m.find()) return new Verdict("advertising", "phrase", m.group().trim());
        }
        return null;
    }

    private boolean isAllowed(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        for (String a : allowed) {
            if (h.equals(a) || h.endsWith("." + a)) return true;
        }
        return false;
    }

    static String hostOf(String url) {
        String s = url.toLowerCase(Locale.ROOT).replace('\\', '/');
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        s = s.substring(0, end);
        // user:password@host: the host is what follows the last @
        s = s.substring(s.lastIndexOf('@') + 1);
        int port = s.indexOf(':');
        if (port >= 0) s = s.substring(0, port);
        while (s.endsWith(".") || s.endsWith(",") || s.endsWith(")")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String registrableLabel(String host) {
        String[] labels = host.split("\\.");
        return labels.length >= 2 ? labels[labels.length - 2] : host;
    }

    /** 127.x.x.x, 0.x.x.x and a game version like 1.21.4.1 (no port, every part small) are not server addresses. */
    private static boolean isVersionOrLocal(String ip, String port) {
        String[] o = ip.split("\\.");
        int a = Integer.parseInt(o[0]);
        if (a == 0 || a == 127) return true;
        if (a != 1 || port != null) return false;
        for (int i = 1; i < 4; i++) if (Integer.parseInt(o[i]) > 30) return false;
        return true;
    }

    /** evil[.]com, evil(.)com, an ideographic full stop, and "play . evil . com" read as plain dots. */
    private static String readDots(String text) {
        return SPACED_DOT.matcher(text.replace("[.]", ".").replace("(.)", ".").replace("{.}", ".")
                .replace('。', '.').replace('｡', '.')).replaceAll(".");
    }

    /** Look-alike letters folded word by word, so "exаmple.com" with a Cyrillic а is still seen. */
    private static String foldWords(String cleaned) {
        String[] parts = cleaned.split("(?<=\\s)|(?=\\s)");
        StringBuilder sb = new StringBuilder(cleaned.length());
        for (String p : parts) sb.append(Normalizer.fold(p));
        return sb.toString();
    }
}
