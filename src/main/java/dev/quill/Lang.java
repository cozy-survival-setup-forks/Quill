package dev.quill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/** messages.yml. A message left empty is not sent, and one missing from an older file falls back to the default. */
public final class Lang {

    private final JavaPlugin plugin;
    private volatile FileConfiguration file = new YamlConfiguration();

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return false if messages.yml is broken. On a reload the messages already loaded are kept; on the first
     * load there is nothing to keep, so the bundled texts are used.
     */
    public boolean load() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) plugin.saveResource("messages.yml", false);

        boolean first = file.getDefaults() == null;
        YamlConfiguration loaded = new YamlConfiguration();
        boolean ok = true;
        try {
            loaded.load(target);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "messages.yml is broken, " + (first ? "using the bundled texts" : "keeping the messages already loaded"), e);
            if (!first) return false;
            loaded = new YamlConfiguration();
            ok = false;
        }
        var defaults = plugin.getResource("messages.yml");
        if (defaults != null) loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        file = loaded;
        return ok;
    }

    /**
     * {@code getString(key, "")} never falls back to the bundled default, even after setDefaults: the
     * two-argument form only returns its own default when the key is unset and never consults the defaults.
     * The one-argument form does, and a key someone set to "" still reads as "".
     */
    private String str(String key) {
        String value = file.getString(key);
        return value == null ? "" : value;
    }

    public Component get(String key, TagResolver... resolvers) {
        return Text.parse(str("prefix") + str(key), resolvers);
    }

    /** The message without the prefix, for actionbars and titles. */
    public Component bare(String key, TagResolver... resolvers) {
        return Text.parse(str(key), resolvers);
    }

    public boolean has(String key) {
        return file.isList(key) ? !file.getStringList(key).isEmpty() : !str(key).isEmpty();
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        if (file.isList(key)) {
            for (String line : file.getStringList(key)) to.sendMessage(Text.parse(line.replace("{prefix}", str("prefix")), resolvers));
            return;
        }
        if (!has(key)) return;
        to.sendMessage(get(key, resolvers));
    }

    /** To everybody, unless the message was left empty to turn it off. */
    public void broadcast(String key, TagResolver... resolvers) {
        if (has(key)) Bukkit.broadcast(get(key, resolvers));
    }
}
