package dev.quill.chat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
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

    // Inventory window (6 rows): row 0 helmet, chestplate, leggings, boots, off hand (then spare slots), rows 1-3 the
    // bag, row 4 a divider, row 5 the hotbar.
    static final int OFFHAND_SLOT = 4;

    /** Window slot for a {@code getArmorContents()} index (0 boots .. 3 helmet): helmet first. */
    static int armorSlot(int armorIndex) {
        return 3 - armorIndex;
    }

    /** Window slot for a {@code getStorageContents()} index (0-8 hotbar, 9-35 bag). */
    static int storageSlot(int index) {
        return index < 9 ? 45 + index : index;
    }

    /** Slots that hold no item: the spare ones beside the off hand and the divider row. */
    static boolean isFiller(int slot) {
        return (slot >= 5 && slot <= 8) || (slot >= 36 && slot <= 44);
    }

    /** A pane with no name and no tooltip. */
    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.empty()));
        pane.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return pane;
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
            ItemStack pane = filler();
            for (int slot = 0; slot < 54; slot++) if (isFiller(slot)) view.setItem(slot, pane.clone());
            for (int i = 0; i < s.armor.length && i < 4; i++) view.setItem(armorSlot(i), s.armor[i]);
            view.setItem(OFFHAND_SLOT, s.offhand);
            for (int i = 0; i < 36 && i < s.items.length; i++) view.setItem(storageSlot(i), s.items[i]);
        }
        holder.inventory = view;
        viewer.openInventory(view);
        return true;
    }
}
