package com.confirmspellbook;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PlayerLoadoutTest
{
    private static final int[] TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
        VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
    private static final int[] QUANTITIES = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
        VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5,
        VarbitID.RUNE_POUCH_QUANTITY_6};

    @Mock private Client client;
    @Mock private ItemManager itemManager;
    @Mock private ItemContainer inventory;
    @Mock private ItemContainer equipment;
    @Mock private EnumComposition runeEnum;
    @InjectMocks private PlayerLoadout loadout;

    @Before
    public void setUp()
    {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(inventory.getItems()).thenReturn(new Item[0]);
        when(equipment.getItems()).thenReturn(new Item[0]);
        when(itemManager.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
        when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runeEnum);
    }

    @Test
    public void ancientRunePouchDoesNotSupplyGreaterThralls()
    {
        inventory(ItemID.BH_RUNE_POUCH, 1);
        slot(0, ItemID.WATERRUNE, 1000);
        slot(1, ItemID.DEATHRUNE, 1000);
        slot(2, ItemID.BLOODRUNE, 1000);
        assertTrue(loadout.carriedItems().hasRunePouch());
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void allPouchVariantsSupplyThrallRunes()
    {
        slot(0, ItemID.FIRERUNE, 100);
        slot(1, ItemID.BLOODRUNE, 50);
        slot(2, ItemID.COSMICRUNE, 10);
        for (int pouch : new int[]{ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
            ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER})
        {
            inventory(pouch, 1);
            assertEquals(10, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
        }
    }

    @Test
    public void inventoryAndPouchQuantitiesAreCombined()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.DIVINE_RUNE_POUCH, 1),
            new Item(ItemID.FIRERUNE, 5), new Item(ItemID.BLOODRUNE, 3)});
        slot(0, ItemID.FIRERUNE, 5);
        slot(1, ItemID.BLOODRUNE, 2);
        slot(3, ItemID.COSMICRUNE, 1);
        assertEquals(1, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void ancientPouchWithEnoughLooseRunesIsReady()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1),
            new Item(ItemID.FIRERUNE, 10), new Item(ItemID.COSMICRUNE, 1)});
        slot(0, ItemID.WATERRUNE, 100);
        slot(1, ItemID.DEATHRUNE, 100);
        slot(2, ItemID.BLOODRUNE, 100);
        assertEquals(1, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void bankedPouchContentsAreIgnored()
    {
        slot(0, ItemID.FIRERUNE, 100);
        slot(1, ItemID.BLOODRUNE, 50);
        slot(2, ItemID.COSMICRUNE, 10);
        assertFalse(loadout.carriedItems().hasRunePouch());
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void comboAndAetherRunesWorkInLaterPouchSlots()
    {
        inventory(ItemID.DIVINE_RUNE_POUCH, 1);
        slot(3, ItemID.LAVARUNE, 50);
        slot(4, ItemID.BLOODRUNE, 25);
        slot(5, ItemID.AETHERRUNE, 5);
        assertEquals(5, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void emptyAndUnknownSlotsDoNotCount()
    {
        inventory(ItemID.BH_RUNE_POUCH, 1);
        slot(0, ItemID.FIRERUNE, 100);
        slot(1, ItemID.BLOODRUNE, 50);
        slot(2, ItemID.COSMICRUNE, 0);
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
        slot(2, -1, 100);
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void equippedStaffCountsButInventoryStaffDoesNot()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.STAFF_OF_FIRE, 1),
            new Item(ItemID.BLOODRUNE, 50), new Item(ItemID.COSMICRUNE, 10)});
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
        Item[] wornItems = new Item[EquipmentInventorySlot.SHIELD.getSlotIdx() + 1];
        wornItems[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(ItemID.STAFF_OF_FIRE, 1);
        when(equipment.getItems()).thenReturn(wornItems);
        assertEquals(10, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void absentContainersAndEnumAreSafe()
    {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(null);
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        inventory(ItemID.BH_RUNE_POUCH, 1);
        slot(0, ItemID.FIRERUNE, 100);
        when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(null);
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void snapshotReadsEachContainerAndPouchSlotOnlyOnce()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.DIVINE_RUNE_POUCH, 1),
            new Item(ItemID.BOOK_OF_THE_DEAD, 1)});
        slot(0, ItemID.FIRERUNE, 100);
        slot(1, ItemID.BLOODRUNE, 50);
        slot(2, ItemID.COSMICRUNE, 10);
        PlayerLoadout.Snapshot snapshot = loadout.snapshot();
        assertTrue(snapshot.getCarriedItems().hasBookOfTheDead());
        assertTrue(snapshot.getCarriedItems().hasRunePouch());
        assertEquals(10, snapshot.castsAvailable(ThrallTier.GREATER));
        assertEquals(0, snapshot.castsAvailable(ThrallTier.LESSER));
        assertEquals(0, snapshot.castsAvailable(ThrallTier.SUPERIOR));
        verify(client, times(1)).getItemContainer(InventoryID.INVENTORY);
        verify(client, times(1)).getItemContainer(InventoryID.EQUIPMENT);
        verify(inventory, times(1)).getItems();
        verify(equipment, times(1)).getItems();
        verify(client, times(1)).getEnum(EnumID.RUNEPOUCH_RUNE);
        for (int slot = 0; slot < TYPES.length; slot++)
        {
            verify(client, times(1)).getVarbitValue(TYPES[slot]);
            verify(client, times(1)).getVarbitValue(QUANTITIES[slot]);
        }
        verifyNoMoreInteractions(client, inventory, equipment);
    }

    @Test
    public void combinedMaximumStacksCannotOverflow()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.DIVINE_RUNE_POUCH, 1),
            new Item(ItemID.FIRERUNE, Integer.MAX_VALUE), new Item(ItemID.LAVARUNE, Integer.MAX_VALUE),
            new Item(ItemID.BLOODRUNE, Integer.MAX_VALUE), new Item(ItemID.COSMICRUNE, Integer.MAX_VALUE),
            new Item(ItemID.AETHERRUNE, Integer.MAX_VALUE)});
        slot(0, ItemID.FIRERUNE, 16000);
        slot(1, ItemID.BLOODRUNE, 16000);
        slot(2, ItemID.COSMICRUNE, 16000);
        assertEquals(Integer.MAX_VALUE / 10, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void maximumPouchSlotsAndInfiniteSourceCannotOverflow()
    {
        inventory(ItemID.DIVINE_RUNE_POUCH, 1);
        slot(0, ItemID.FIRERUNE, Integer.MAX_VALUE);
        slot(1, ItemID.LAVARUNE, Integer.MAX_VALUE);
        slot(2, ItemID.BLOODRUNE, Integer.MAX_VALUE);
        slot(3, ItemID.COSMICRUNE, Integer.MAX_VALUE);
        slot(4, ItemID.AETHERRUNE, Integer.MAX_VALUE);
        Item[] wornItems = new Item[EquipmentInventorySlot.SHIELD.getSlotIdx() + 1];
        wornItems[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(ItemID.STAFF_OF_FIRE, 1);
        when(equipment.getItems()).thenReturn(wornItems);
        assertEquals(Integer.MAX_VALUE / 10, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void snapshotDoesNotChangeWhenLiveContainersChange()
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.FIRERUNE, 10),
            new Item(ItemID.BLOODRUNE, 5), new Item(ItemID.COSMICRUNE, 1)});
        PlayerLoadout.Snapshot snapshot = loadout.snapshot();
        when(inventory.getItems()).thenReturn(new Item[0]);
        assertEquals(1, snapshot.castsAvailable(ThrallTier.GREATER));
        assertEquals(0, loadout.snapshot().castsAvailable(ThrallTier.GREATER));
    }

    @Test
    public void itemObservationDoesNotReadPouchRunesAndRecognizesEquippedBook()
    {
        inventory(ItemID.DIVINE_RUNE_POUCH, 1);
        Item[] wornItems = new Item[EquipmentInventorySlot.SHIELD.getSlotIdx() + 1];
        wornItems[EquipmentInventorySlot.SHIELD.getSlotIdx()] = new Item(ItemID.BOOK_OF_THE_DEAD, 1);
        when(equipment.getItems()).thenReturn(wornItems);
        PlayerLoadout.CarriedItems carriedItems = loadout.carriedItems();
        assertTrue(carriedItems.hasBookOfTheDead());
        assertTrue(carriedItems.hasRunePouch());
        verify(client, never()).getVarbitValue(anyInt());
        verify(client, never()).getEnum(anyInt());
    }

    private void inventory(int item, int quantity)
    {
        when(inventory.getItems()).thenReturn(new Item[]{new Item(item, quantity)});
    }

    private void slot(int slot, int runeItem, int quantity)
    {
        int enumKey = slot + 1;
        when(client.getVarbitValue(TYPES[slot])).thenReturn(enumKey);
        when(client.getVarbitValue(QUANTITIES[slot])).thenReturn(quantity);
        when(runeEnum.getIntValue(enumKey)).thenReturn(runeItem);
    }
}
