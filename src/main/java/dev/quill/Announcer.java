package dev.quill;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Broadcasts the lines of config.yml on a timer, in order or at random. */
public final class Announcer {

    private final QuillPlugin plugin;
    private BukkitTask task;
    private int next;

    Announcer(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    void restart() {
        if (task != null) task.cancel();
        task = null;
        Settings s = plugin.settings();
        if (!s.announce || s.announcements.isEmpty()) return;
        long ticks = s.announceSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::send, ticks, ticks);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void send() {
        Settings s = plugin.settings();
        if (s.announcements.isEmpty()) return;
        int index = s.announceRandom ? ThreadLocalRandom.current().nextInt(s.announcements.size()) : next++ % s.announcements.size();
        List<String> lines = s.announcements.get(index);
        List<Component> parts = lines.stream().map(l -> Text.parse(plugin.hooks().apply(null, l))).toList();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("quill.announcements.bypass")) continue;
            for (Component c : parts) p.sendMessage(c);
        }
    }
}
