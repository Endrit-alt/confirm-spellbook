package com.confirmspellbook;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/** Reads the current loadout on the client thread; returned snapshots need no further client reads. */
@Singleton
public class PlayerLoadout
{
    private static final int[] RUNE_POUCH_TYPE_VARBITS = {
        VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
        VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
    };
    private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
        VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
        VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
    };
    private static final Set<Integer> RUNE_POUCH_ITEM_IDS = Set.of(
        ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
        ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER);
    private static final ThrallRune[] RUNES = ThrallRune.values();

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    public static boolean isRunePouchVarbit(int varbitId)
    {
        for (int slot = 0; slot < RUNE_POUCH_TYPE_VARBITS.length; slot++)
        {
            if (RUNE_POUCH_TYPE_VARBITS[slot] == varbitId || RUNE_POUCH_QUANTITY_VARBITS[slot] == varbitId)
            {
                return true;
            }
        }
        return false;
    }

    /** One scan of each container, one enum lookup, and one read of each pouch slot. */
    public Snapshot snapshot()
    {
        Readout readout = readContainers(true);
        if (readout.pouch)
        {
            readRunePouch(readout);
        }
        return new Snapshot(readout.carriedItems(), readout.runes);
    }

    /** Lightweight event observation, without reading rune totals or pouch varbits. */
    public CarriedItems carriedItems()
    {
        return readContainers(false).carriedItems();
    }

    private Readout readContainers(boolean includeRunes)
    {
        Readout readout = new Readout();
        readContainer(client.getItemContainer(InventoryID.INVENTORY), false, includeRunes, readout);
        readContainer(client.getItemContainer(InventoryID.EQUIPMENT), true, includeRunes, readout);
        return readout;
    }

    private void readContainer(ItemContainer container, boolean equipment, boolean includeRunes, Readout readout)
    {
        if (container == null)
        {
            return;
        }
        Item[] items = container.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            Item item = items[slot];
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
            {
                continue;
            }
            int canonicalId = itemManager.canonicalize(item.getId());
            readout.book |= canonicalId == ItemID.BOOK_OF_THE_DEAD;
            readout.pouch |= RUNE_POUCH_ITEM_IDS.contains(canonicalId);
            if (!includeRunes)
            {
                continue;
            }
            if (!equipment)
            {
                // Use the actual id for runes: noted runes cannot be cast from.
                readout.addRunes(item.getId(), item.getQuantity());
            }
            else if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx()
                || slot == EquipmentInventorySlot.SHIELD.getSlotIdx())
            {
                for (ThrallRune rune : RUNES)
                {
                    if (rune.isInfiniteSource(canonicalId))
                    {
                        readout.runes.put(rune, Integer.MAX_VALUE);
                    }
                }
            }
        }
    }

    private void readRunePouch(Readout readout)
    {
        EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        if (runeEnum == null)
        {
            return;
        }
        for (int slot = 0; slot < RUNE_POUCH_TYPE_VARBITS.length; slot++)
        {
            int runeType = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[slot]);
            int quantity = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[slot]);
            if (runeType != 0 && quantity > 0)
            {
                readout.addRunes(runeEnum.getIntValue(runeType), quantity);
            }
        }
    }

    private static final class Readout
    {
        private boolean book;
        private boolean pouch;
        private final Map<ThrallRune, Integer> runes = new EnumMap<>(ThrallRune.class);

        private void addRunes(int itemId, int quantity)
        {
            for (ThrallRune rune : RUNES)
            {
                if (rune.isSatisfiedByRune(itemId))
                {
                    long total = (long) runes.getOrDefault(rune, 0) + quantity;
                    runes.put(rune, (int) Math.min(Integer.MAX_VALUE, total));
                }
            }
        }

        private CarriedItems carriedItems()
        {
            return new CarriedItems(book, pouch);
        }
    }

    public static final class CarriedItems
    {
        private final boolean book;
        private final boolean pouch;

        CarriedItems(boolean book, boolean pouch)
        {
            this.book = book;
            this.pouch = pouch;
        }

        public boolean hasBookOfTheDead()
        {
            return book;
        }

        public boolean hasRunePouch()
        {
            return pouch;
        }
    }

    public static final class Snapshot
    {
        private final CarriedItems carriedItems;
        private final Map<ThrallRune, Integer> runes;

        Snapshot(CarriedItems carriedItems, Map<ThrallRune, Integer> runes)
        {
            this.carriedItems = carriedItems;
            this.runes = new EnumMap<>(ThrallRune.class);
            this.runes.putAll(runes);
        }

        public CarriedItems getCarriedItems()
        {
            return carriedItems;
        }

        public int castsAvailable(ThrallTier tier)
        {
            return tier.castsAvailable(rune -> runes.getOrDefault(rune, 0));
        }
    }
}
