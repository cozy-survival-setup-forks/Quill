package dev.quill;

import dev.quill.chat.ChatListener;
import dev.quill.chat.ChatState;
import dev.quill.chat.Formats;
import dev.quill.chat.Snapshots;
import dev.quill.command.ChatToggleCommand;
import dev.quill.command.LocalChatCommand;
import dev.quill.command.QuillCommand;
import dev.quill.command.StaffChatCommand;
import dev.quill.filter.FilterEngine;
import dev.quill.filter.Spam;
import dev.quill.filter.Warnings;
import dev.quill.hook.Hooks;
import dev.quill.hook.QuillExpansion;
import dev.quill.safe.ConfigMigrator;
import dev.quill.safe.Doctor;
import dev.quill.safe.FileBackups;
import dev.quill.safe.Guard;
import dev.quill.safe.Health;
import dev.quill.safe.Prep;
import dev.quill.safe.ServerId;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Quill: chat for a survival server. A local range, formats, the usual extras, and a filter for advertising and hate
 * that is built to stop the real thing and nothing else.
 */
public final class QuillPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static final int LANG_VERSION = 1;

    private final List<Prep.Spec> files = List.of(
            new Prep.Spec("config.yml", "config-version", CONFIG_VERSION, Prep.configMigrator(CONFIG_VERSION), null),
            new Prep.Spec("messages.yml", "lang-version", LANG_VERSION, new ConfigMigrator("lang-version", LANG_VERSION), null));

    private final Hooks hooks = new Hooks();
    private final ChatState state = new ChatState();
    private final Formats formats = new Formats();
    private final Snapshots snapshots = new Snapshots();
    private final Spam spam = new Spam();
    private Warnings warnings;
    private Lang lang;
    private Announcer announcer;
    private volatile Settings settings;
    private volatile FilterEngine engine;
    private QuillExpansion expansion;

    @Override
    public void onEnable() {
        try {
            enableInner();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Quill could not start, check config.yml, filter.yml, terms.yml and messages.yml for mistakes", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void enableInner() {
        saveDefaultConfig();
        for (String name : new String[]{"filter.yml", "terms.yml"}) {
            if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
        }
        Health.storage("YAML files in the plugin folder (config.yml, messages.yml, filter.yml, terms.yml, warnings.yml)");
        Prep.startup(this, files);
        lang = new Lang(this);
        warnings = new Warnings(new File(getDataFolder(), "warnings.yml"), getLogger());
        warnings.load();
        announcer = new Announcer(this);
        reloadAll();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        bind("quill", new QuillCommand(this));
        bind("staffchat", new StaffChatCommand(this));
        bind("chattoggle", new ChatToggleCommand(this));
        bind("localchat", new LocalChatCommand(this));

        for (var p : Bukkit.getOnlinePlayers()) state.join(p);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, warnings::save, 600L, 600L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, snapshots::sweep, 1200L, 1200L);
        if (hooks.papi()) {
            expansion = new QuillExpansion(this);
            expansion.register();
        }
        getLogger().info("Quill ready: " + engine.terms().wordCount() + " filtered words, local radius " + (int) settings.radius + ".");
        boolean beacon = getConfig().getBoolean("metrics.enabled", true);
        Metrics.start(this, ServerId.resolve(getDataFolder().toPath(), new ServerId.Slot() {
            @Override
            public String read() {
                return warnings.serverId();
            }

            @Override
            public void write(String id) {
                warnings.serverId(id);
            }
        }, beacon, getLogger()));
        Banner.print(this, "Thanks for keeping every conversation clean and cozy.");
    }

    @Override
    public void onDisable() {
        if (announcer != null) announcer.stop();
        if (expansion != null) expansion.unregister();
        if (warnings != null) warnings.save();
    }

    private void bind(String name, Object handler) {
        PluginCommand command = getCommand(name);
        if (command == null) return;
        command.setExecutor((org.bukkit.command.CommandExecutor) handler);
        if (handler instanceof org.bukkit.command.TabCompleter t) command.setTabCompleter(t);
    }

    /**
     * Reads every file again.
     *
     * @return false if a file has a mistake in it. On a reload the broken file is left as it was (a parse error
     * would otherwise turn into an empty file, and an empty terms.yml means no filter at all); on the very first
     * load there is nothing to keep, so the bundled copy is used.
     */
    public boolean reloadAll() {
        boolean first = engine == null;
        if (!first) {
            List<Guard.Problem> problems = Prep.validate(this, files);
            if (!problems.isEmpty()) {
                Prep.logRejected(this, problems);
                lang.load();
                return false;
            }
        }
        YamlConfiguration config = parse("config.yml");
        YamlConfiguration filter = parse("filter.yml");
        YamlConfiguration terms = parse("terms.yml");
        boolean ok = config != null && filter != null && terms != null;
        if (!ok) {
            if (!first) {
                lang.load();
                return false;
            }
            if (config == null) config = bundled("config.yml");
            if (filter == null) filter = bundled("filter.yml");
            if (terms == null) terms = bundled("terms.yml");
        }

        settings = new Settings(config, filter);
        FilterEngine fresh = new FilterEngine(filter, terms);
        for (String problem : fresh.problems()) getLogger().warning("Filter: " + problem);
        engine = fresh;
        spam.load(filter.getConfigurationSection("spam"));
        warnings.decayMinutes(settings.decayMinutes);
        formats.load(config.getConfigurationSection("formats"));
        snapshots.keepSeconds(settings.cacheSeconds);
        if (!lang.load()) ok = false;
        hooks.refresh();
        announcer.restart();
        return ok;
    }

    /** A yml from the plugin folder with the bundled one behind it for anything an older file lacks, or null if it does not parse. */
    private YamlConfiguration parse(String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(getDataFolder(), name));
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, name + " is broken", e);
            return null;
        }
        var bundled = getResource(name);
        if (bundled != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        return yaml;
    }

    private YamlConfiguration bundled(String name) {
        getLogger().severe("Using the bundled " + name + " until the one in the plugin folder is fixed and /quill reload is run.");
        var stream = getResource(name);
        return stream == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /** The text of /quill doctor. */
    public List<String> doctor() {
        List<String> extra = new ArrayList<>(Prep.versionLines(this, files));
        extra.add("Pending writes: warnings are written about every 30 seconds and when the server stops");
        return Doctor.report(getName(), getPluginMeta().getVersion(), extra);
    }

    /** /quill backup now: a verified copy of the settings and data files. */
    public boolean backupNow() {
        warnings.save();
        List<String> names = new ArrayList<>(Prep.fileNames(files));
        names.add("filter.yml");
        names.add("terms.yml");
        names.add("warnings.yml");
        return FileBackups.snapshot(getDataFolder().toPath(), names, 5, getLogger());
    }

    public Settings settings() {
        return settings;
    }

    public FilterEngine engine() {
        return engine;
    }

    public Spam spam() {
        return spam;
    }

    public Warnings warnings() {
        return warnings;
    }

    public ChatState state() {
        return state;
    }

    public Lang lang() {
        return lang;
    }

    public Formats formats() {
        return formats;
    }

    public Hooks hooks() {
        return hooks;
    }

    public Snapshots snapshots() {
        return snapshots;
    }
}
