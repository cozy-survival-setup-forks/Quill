package dev.quill.chat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotsTest {

    @Test
    void everyItemHasItsOwnSlotAndNeverLandsOnAFiller() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 4; i++) assertTrue(used.add(Snapshots.armorSlot(i)));
        assertTrue(used.add(Snapshots.OFFHAND_SLOT));
        for (int i = 0; i < 36; i++) assertTrue(used.add(Snapshots.storageSlot(i)));
        assertEquals(41, used.size());
        for (int slot : used) {
            assertTrue(slot >= 0 && slot < 54);
            assertFalse(Snapshots.isFiller(slot), "item on filler slot " + slot);
        }
    }

    @Test
    void fillersAreExactlyTheUnusedSlots() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 4; i++) used.add(Snapshots.armorSlot(i));
        used.add(Snapshots.OFFHAND_SLOT);
        for (int i = 0; i < 36; i++) used.add(Snapshots.storageSlot(i));
        for (int slot = 0; slot < 54; slot++) assertEquals(!used.contains(slot), Snapshots.isFiller(slot), "slot " + slot);
    }

    @Test
    void layoutRows() {
        assertEquals(0, Snapshots.armorSlot(3)); // helmet first
        assertEquals(3, Snapshots.armorSlot(0)); // boots last
        assertEquals(9, Snapshots.storageSlot(9)); // bag keeps rows 1-3
        assertEquals(35, Snapshots.storageSlot(35));
        assertEquals(45, Snapshots.storageSlot(0)); // hotbar on its own bottom row
        assertEquals(53, Snapshots.storageSlot(8));
    }
}
