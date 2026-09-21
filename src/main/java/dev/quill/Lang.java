package dev.quill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** messages.yml. A message left empty is not sent, and one missing from an older file falls back to the default. */
public final class Lang {

    private final JavaPlugin plugin;
    private FileConfiguration file = new YamlConfiguration();

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) plugin.saveResource("messages.yml", false);
        file = YamlConfiguration.loadConfiguration(target);
        var defaults = plugin.getResource("messages.yml");
        if (defaults != null) file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
    }

    public String raw(String key) {
        return file.getString(key, "");
    }

    public List<String> lines(String key) {
        return file.getStringList(key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return Text.parse(file.getString("prefix", "") + file.getString(key, ""), resolvers);
    }

    /** The message without the prefix, for actionbars and titles. */
    public Component bare(String key, TagResolver... resolvers) {
        return Text.parse(file.getString(key, ""), resolvers);
    }

    public boolean has(String key) {
        return file.isList(key) ? !file.getStringList(key).isEmpty() : !file.getString(key, "").isEmpty();
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        if (file.isList(key)) {
            for (String line : file.getStringList(key)) to.sendMessage(Text.parse(line.replace("{prefix}", file.getString("prefix", "")), resolvers));
            return;
        }
        if (!has(key)) return;
        to.sendMessage(get(key, resolvers));
    }
}
