package dev.quill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** config.yml and the warnings part of filter.yml, read once per reload so the chat thread never touches a file. */
public final class Settings {

    public record Escalation(int at, boolean repeat, List<String> commands) {
    }

    public final double radius;
    public final String localPrefix;
    public final boolean hintWhenAlone;
    public final Set<String> ignoreWorlds = new HashSet<>();

    public final String name, prefix, suffix;
    public final List<String> hover;
    public final String hoverClick;
    public final String spyFormat, staffFormat, staffPrefix;

    public final boolean mentions;
    public final String mentionFormat, mentionSound, mentionActionbar;

    public final boolean items;
    public final List<String> itemTokens, invTokens, enderTokens;
    public final String invFormat, enderFormat;
    public final int cacheSeconds;
    public final boolean clickableLinks;

    public final boolean announce, announceRandom;
    public final int announceSeconds;
    public final List<List<String>> announcements = new ArrayList<>();

    public final boolean filterEnabled, alertStaff, logFile;
    public final int maxWarnings, decayMinutes;
    public final List<Escalation> escalation = new ArrayList<>();

    public Settings(FileConfiguration c, ConfigurationSection filter) {
        radius = Math.max(1, c.getDouble("local.radius", 64));
        localPrefix = c.getString("local.prefix", "~");
        hintWhenAlone = c.getBoolean("local.hint-when-alone", true);
        for (String w : c.getStringList("local.ignore-worlds")) ignoreWorlds.add(w.toLowerCase(Locale.ROOT));

        name = c.getString("name", "%spectrum_name%");
        prefix = c.getString("prefix", "%luckperms_prefix%");
        suffix = c.getString("suffix", "%luckperms_suffix%");
        hover = c.getStringList("hover.lines");
        hoverClick = c.getString("hover.click", "");
        spyFormat = c.getString("spy-format", "<gray>[Spy] <name>: <message>");
        staffFormat = c.getString("staff.format", "<red>[Staff] <name>: <message>");
        staffPrefix = c.getString("staff.prefix", "#");

        mentions = c.getBoolean("mentions.enabled", true);
        mentionFormat = c.getString("mentions.format", "<yellow>@<player></yellow>");
        mentionSound = c.getString("mentions.sound", "block.note_block.pling");
        mentionActionbar = c.getString("mentions.actionbar", "<yellow><player> mentioned you");

        items = c.getBoolean("items.enabled", true);
        itemTokens = lower(c.getStringList("items.item-tokens"));
        invTokens = lower(c.getStringList("items.inventory-tokens"));
        enderTokens = lower(c.getStringList("items.ender-tokens"));
        invFormat = c.getString("items.inventory-format", "<aqua>[<player>'s Inventory]");
        enderFormat = c.getString("items.ender-format", "<light_purple>[<player>'s Ender Chest]");
        cacheSeconds = c.getInt("items.cache-seconds", 120);
        clickableLinks = c.getBoolean("links.clickable", true);

        announce = c.getBoolean("announcements.enabled", false);
        announceRandom = c.getBoolean("announcements.random", false);
        announceSeconds = Math.max(10, c.getInt("announcements.interval-seconds", 300));
        for (Object o : c.getList("announcements.list", List.of())) {
            if (o instanceof List<?> l) announcements.add(l.stream().map(String::valueOf).toList());
            else if (o != null) announcements.add(List.of(String.valueOf(o)));
        }

        filterEnabled = filter.getBoolean("enabled", true);
        alertStaff = filter.getBoolean("warnings.alert-staff", true);
        logFile = filter.getBoolean("warnings.log", true);
        maxWarnings = Math.max(1, filter.getInt("warnings.max", 3));
        decayMinutes = Math.max(1, filter.getInt("warnings.decay-minutes", 60));
        for (Map<?, ?> m : filter.getMapList("warnings.escalation")) {
            Object at = m.get("at");
            if (!(at instanceof Number n)) continue;
            List<String> commands = new ArrayList<>();
            if (m.get("commands") instanceof List<?> l) l.forEach(o -> commands.add(String.valueOf(o)));
            escalation.add(new Escalation(n.intValue(), Boolean.TRUE.equals(m.get("repeat")), commands));
        }
    }

    private static List<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }
}
