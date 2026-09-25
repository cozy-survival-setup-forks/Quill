package dev.quill.chat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The switches: chat muted for everyone, a player hiding public chat, staff listening in, staff chat mode, local chat mode. */
public final class ChatState {

    private static final NamespacedKey HIDDEN = new NamespacedKey("quill", "chat_hidden");

    public volatile boolean muted;
    public volatile boolean localOn = true;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> localMode = ConcurrentHashMap.newKeySet();

    public void join(Player p) {
        if (p.getPersistentDataContainer().has(HIDDEN, PersistentDataType.BYTE)) hidden.add(p.getUniqueId());
    }

    public void quit(Player p) {
        UUID id = p.getUniqueId();
        hidden.remove(id);
        spies.remove(id);
        staffMode.remove(id);
        localMode.remove(id);
    }

    public boolean hidden(UUID id) {
        return hidden.contains(id);
    }

    /** Flips whether a player sees public chat. Returns true when it is now hidden. */
    public boolean toggleHidden(Player p) {
        UUID id = p.getUniqueId();
        if (hidden.remove(id)) {
            p.getPersistentDataContainer().remove(HIDDEN);
            return false;
        }
        hidden.add(id);
        p.getPersistentDataContainer().set(HIDDEN, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public boolean spying(UUID id) {
        return spies.contains(id);
    }

    public boolean toggleSpy(UUID id) {
        return !spies.remove(id) && spies.add(id);
    }

    public Set<UUID> spies() {
        return spies;
    }

    public boolean staffMode(UUID id) {
        return staffMode.contains(id);
    }

    public boolean toggleStaffMode(UUID id) {
        return !staffMode.remove(id) && staffMode.add(id);
    }

    /** Whether every message of the player is local, switched with /localchat. */
    public boolean localMode(UUID id) {
        return localMode.contains(id);
    }

    public boolean toggleLocalMode(UUID id) {
        return !localMode.remove(id) && localMode.add(id);
    }
}
