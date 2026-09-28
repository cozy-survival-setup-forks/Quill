package dev.quill.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What [inv] and [ender] show: a copy of the contents taken when the message was sent, read-only, kept for a while. */
public final class Snapshots {

    /** Marks the windows Quill opens, so they can be locked. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            // never null: other plugins that look at the holder of a clicked window would otherwise throw
            return inventory;
        }
    }

    private record Snap(String owner, boolean ender, ItemStack[] items, ItemStack[] armor, ItemStack offhand, long expires) {
    }

    private final Map<String, Snap> snaps = new ConcurrentHashMap<>();
    private volatile long keepMs = 120_000;

    public void keepSeconds(int seconds) {
        keepMs = Math.max(10, seconds) * 1000L;
    }

    public String take(Player p, boolean ender) {
        sweep();
        String id = Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, 36);
        id = id.substring(0, Math.min(6, id.length()));
        long expires = System.currentTimeMillis() + keepMs;
        if (ender) {
            snaps.put(id, new Snap(p.getName(), true, copy(p.getEnderChest().getContents()), null, null, expires));
        } else {
            PlayerInventory inv = p.getInventory();
            snaps.put(id, new Snap(p.getName(), false, copy(inv.getStorageContents()), copy(inv.getArmorContents()),
                    inv.getItemInOffHand().clone(), expires));
        }
        return id;
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) out[i] = items[i] == null ? null : items[i].clone();
        return out;
    }

    /** Drops expired snapshots. Also called on its own timer so they do not just sit in memory between uses. */
    public void sweep() {
        long now = System.currentTimeMillis();
        snaps.values().removeIf(s -> s.expires < now);
    }

    /** Opens the snapshot for a viewer. Returns false if it is gone. */
    public boolean open(Player viewer, String id) {
        sweep();
        Snap s = snaps.get(id);
        if (s == null) return false;
        Component title = Component.text(s.owner + (s.ender ? "'s Ender Chest" : "'s Inventory"));
        Holder holder = new Holder();
        Inventory view;
        if (s.ender) {
            view = Bukkit.createInventory(holder, 27, title);
            for (int i = 0; i < 27 && i < s.items.length; i++) view.setItem(i, s.items[i]);
        } else {
            view = Bukkit.createInventory(holder, 54, title);
            // armor and off hand on top, the bag below it, the hotbar last
            for (int i = 0; i < s.armor.length && i < 4; i++) view.setItem(3 - i, s.armor[i]);
            view.setItem(4, s.offhand);
            for (int i = 9; i < 36 && i < s.items.length; i++) view.setItem(i, s.items[i]);
            for (int i = 0; i < 9 && i < s.items.length; i++) view.setItem(36 + i, s.items[i]);
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            var meta = pane.getItemMeta();
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
            for (int i = 5; i < 9; i++) view.setItem(i, pane);
            for (int i = 45; i < 54; i++) view.setItem(i, pane);
        }
        holder.inventory = view;
        viewer.openInventory(view);
        return true;
    }
}
