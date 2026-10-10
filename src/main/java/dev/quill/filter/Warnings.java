package dev.quill.filter;

import dev.quill.safe.Health;
import dev.quill.safe.SafeIo;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** How many times each player was stopped lately. A warning fades after a while, and the count survives a restart. */
public final class Warnings {

    private record Record(int count, long last) {
    }

    private final File file;
    private final Logger log;
    private final Map<UUID, Record> records = new ConcurrentHashMap<>();
    private volatile long decayMs = 60 * 60_000L;
    private volatile boolean dirty;
    /** The id of the usage beacon lives in this file, next to the warnings. */
    private volatile String serverId;

    public Warnings(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void decayMinutes(long minutes) {
        this.decayMs = Math.max(1, minutes) * 60_000L;
    }

    public void load() {
        records.clear();
        if (!file.exists()) return;
        // warnings fade within the hour: if the file is lost it is better to start without them than to stop chat
        YamlConfiguration yaml = SafeIo.loadYaml(file.toPath(), SafeIo.Policy.SETTINGS, log).yaml;
        serverId = yaml.getString("server-id");
        for (String key : yaml.getKeys(false)) {
            try {
                records.put(UUID.fromString(key), new Record(yaml.getInt(key + ".count"), yaml.getLong(key + ".last")));
            } catch (IllegalArgumentException ignored) {
                // a line that is not a player: skip it
            }
        }
    }

    /** Adds a warning and returns the new count. */
    public int add(UUID id, long now) {
        int count = records.merge(id, new Record(1, now), (old, ignored) ->
                new Record(now - old.last > decayMs ? 1 : old.count + 1, now)).count;
        dirty = true;
        return count;
    }

    public int get(UUID id, long now) {
        Record r = records.get(id);
        return r == null || now - r.last > decayMs ? 0 : r.count;
    }

    public void clear(UUID id) {
        if (records.remove(id) != null) dirty = true;
    }

    /** Writes the file if anything changed. Called from an async task and on shutdown. */
    public synchronized void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        if (serverId != null) yaml.set("server-id", serverId);
        records.forEach((id, r) -> {
            if (now - r.last <= decayMs) {
                yaml.set(id + ".count", r.count);
                yaml.set(id + ".last", r.last);
            }
        });
        try {
            SafeIo.writeYaml(file.toPath(), yaml.saveToString());
        } catch (IOException e) {
            dirty = true; // try again at the next save
            log.warning("Could not save warnings.yml: " + e.getMessage());
            Health.failure("warnings.yml could not be saved: " + e.getMessage());
        }
    }

    public String serverId() {
        return serverId;
    }

    /** Stores the beacon id and writes the file at once. */
    public synchronized void serverId(String id) {
        this.serverId = id;
        dirty = true;
        save();
        if (dirty) throw new IllegalStateException("warnings.yml could not be written");
    }
}
