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

    /** Replaced whole, never edited in place: chat threads read this while a reload runs on the main thread. */
    private volatile List<Format> formats = List.of(new Format("default", "", 0, FALLBACK, FALLBACK));

    public void load(ConfigurationSection section) {
        List<Format> loaded = new ArrayList<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                String global = s.getString("global", FALLBACK);
                loaded.add(new Format(id, s.getString("permission", ""), s.getInt("weight", 0), s.getString("local", global), global));
            }
        }
        if (loaded.isEmpty()) loaded.add(new Format("default", "", 0, FALLBACK, FALLBACK));
        loaded.sort(Comparator.comparingInt(Format::weight).reversed());
        formats = List.copyOf(loaded);
    }

    public Format pick(Player p) {
        List<Format> current = formats;
        for (Format f : current) {
            if (f.permission.isEmpty() || p.hasPermission(f.permission)) return f;
        }
        return current.get(current.size() - 1);
    }
}
