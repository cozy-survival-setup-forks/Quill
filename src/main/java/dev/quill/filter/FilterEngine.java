package dev.quill.filter;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The whole filter: words and phrases from terms.yml, then advertising. Pure text in, a verdict or nothing out. */
public final class FilterEngine {

    private final Terms terms;
    private final WordMatcher words;
    private final AdMatcher ads;
    private final boolean adsEnabled;
    private final List<String> problems = new java.util.ArrayList<>();

    /**
     * @param filter the filter.yml root
     * @param termsYml the terms.yml root
     */
    public FilterEngine(ConfigurationSection filter, ConfigurationSection termsYml) {
        this.terms = Terms.load(termsYml);
        this.words = new WordMatcher(terms);
        ConfigurationSection a = filter.getConfigurationSection("advertising");
        if (a == null) a = filter.createSection("advertising");
        this.adsEnabled = a.getBoolean("enabled", true);
        Set<String> allowed = new HashSet<>();
        for (String d : a.getStringList("allowed-domains")) allowed.add(d.toLowerCase(Locale.ROOT).trim());
        Set<String> tlds = new HashSet<>();
        for (String t : a.getStringList("tlds")) tlds.add(t.toLowerCase(Locale.ROOT).trim());
        this.ads = new AdMatcher(allowed, tlds, a.getBoolean("block-ips", true), a.getStringList("phrases"));
        problems.addAll(terms.problems());
        problems.addAll(ads.problems());
    }

    public List<String> problems() {
        return problems;
    }

    public Terms terms() {
        return terms;
    }

    /**
     * @param names lowercase names of online players
     * @param skip  categories the player may say (bypass permissions), "advertising" included
     * @param allowLinks the player may post links
     * @return why the message must be stopped, or null
     */
    public Verdict check(String text, Set<String> names, Set<String> skip, boolean allowLinks) {
        if (text == null || text.isBlank()) return null;
        String cleaned = Normalizer.clean(text);
        Verdict v = words.match(cleaned, names, skip);
        if (v != null) return v;
        if (adsEnabled && !skip.contains("advertising")) return ads.match(cleaned, allowLinks);
        return null;
    }

    public String label(String category) {
        if (category.equals("advertising")) return "advertising";
        Terms.Category c = terms.categories().get(category);
        return c == null ? category : c.label();
    }
}
