package dev.quill.filter;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The plain spam checks: chatting too fast, saying the same thing again, shouting. They stop the message and never count as warnings. */
public final class Spam {

    public enum Kind { COOLDOWN, REPEAT, CAPS }

    private record Last(String text, long at) {
    }

    private final Map<UUID, Last> last = new ConcurrentHashMap<>();
    private volatile boolean cooldown, repeat, caps;
    private volatile long cooldownMs, repeatMs;
    private volatile int minLetters, percent;

    public void load(ConfigurationSection spam) {
        if (spam == null) spam = new org.bukkit.configuration.file.YamlConfiguration();
        cooldown = spam.getBoolean("cooldown.enabled", true);
        cooldownMs = (long) (spam.getDouble("cooldown.seconds", 2) * 1000);
        repeat = spam.getBoolean("repeat.enabled", true);
        repeatMs = (long) (spam.getDouble("repeat.seconds", 15) * 1000);
        caps = spam.getBoolean("caps.enabled", true);
        minLetters = Math.max(1, spam.getInt("caps.min-letters", 12));
        percent = Math.min(100, Math.max(1, spam.getInt("caps.percent", 75)));
    }

    /** Returns the kind of spam, or null. A message that passes is remembered for the next check. */
    public Kind check(UUID id, String text, long now) {
        Last before = last.get(id);
        if (cooldown && before != null && now - before.at < cooldownMs) return Kind.COOLDOWN;
        String norm = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (repeat && before != null && before.text.equals(norm) && now - before.at < repeatMs) return Kind.REPEAT;
        if (caps && shouting(text)) return Kind.CAPS;
        last.put(id, new Last(norm, now));
        return null;
    }

    boolean shouting(String text) {
        int letters = 0, upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters >= minLetters && upper * 100 >= letters * percent;
    }

    public void forget(UUID id) {
        last.remove(id);
    }
}
