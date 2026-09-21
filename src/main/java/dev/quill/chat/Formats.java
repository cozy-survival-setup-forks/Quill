package dev.quill.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The chat formats of config.yml: the highest weight the player has the permission for is used. */
public final class Formats {

    public record Format(String id, String permission, int weight, String local, String global) {
    }

    private static final String FALLBACK = "<name><dark_gray> » </dark_gray><message>";

    private final List<Format> formats = new ArrayList<>();

    public void load(ConfigurationSection section) {
        formats.clear();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                String global = s.getString("global", FALLBACK);
                formats.add(new Format(id, s.getString("permission", ""), s.getInt("weight", 0), s.getString("local", global), global));
            }
        }
        if (formats.isEmpty()) formats.add(new Format("default", "", 0, FALLBACK, FALLBACK));
        formats.sort(Comparator.comparingInt(Format::weight).reversed());
    }

    public Format pick(Player p) {
        for (Format f : formats) {
            if (f.permission.isEmpty() || p.hasPermission(f.permission)) return f;
        }
        return formats.get(formats.size() - 1);
    }
}
