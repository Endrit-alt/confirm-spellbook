package com.confirmspellbook;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ReminderBehaviorTest
{
    @Mock private Client client;
    @Mock private PlayerLoadout loadout;
    @Mock private Notifier notifier;
    @Mock private ClientThread clientThread;
    @Mock private OverlayManager overlayManager;
    @Mock private ConfirmSpellbookOverlay overlay;
    @Mock private KeyManager keyManager;
    @Mock private MouseManager mouseManager;
    @Mock private ConfirmMouseListener confirmMouseListener;
    @Spy private ConfirmSpellbookConfig config = new ConfirmSpellbookConfig() {};
    @InjectMocks private ConfirmSpellbookPlugin plugin;
    private boolean bookPresent = true;
    private boolean pouchPresent;
    private int suppliedCasts = 10;

    @Before
    public void setUp() throws Exception
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(3);
        bookPresent = true;
        suppliedCasts = 10;
        when(loadout.carriedItems()).thenAnswer(call -> new PlayerLoadout.CarriedItems(bookPresent, pouchPresent));
        when(loadout.snapshot()).thenAnswer(call ->
        {
            Map<ThrallRune, Integer> runes = new EnumMap<>(ThrallRune.class);
            runes.put(ThrallRune.FIRE, suppliedCasts * 10);
            runes.put(ThrallRune.BLOOD, suppliedCasts * 5);
            runes.put(ThrallRune.COSMIC, suppliedCasts);
            return new PlayerLoadout.Snapshot(new PlayerLoadout.CarriedItems(bookPresent, pouchPresent), runes);
        });
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(clientThread).invokeLater(any(Runnable.class));
        plugin.startUp();
    }

    @Test
    public void confirmsAncientsEvenWithoutBookOrThrallRunes()
    {
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        bookPresent = false;
        pouchPresent = true;
        suppliedCasts = 0;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.SPELLBOOK_CONFIRMATION, plugin.getCurrentMissingCondition());
        assertEquals("Confirm spellbook : Ancients", plugin.getReminderLongText());
        assertEquals("Ancients!", plugin.getReminderShortText());
        plugin.confirmWarning(plugin.getWarningVersion());
        refresh();
        assertFalse(plugin.shouldShowWarning());
    }

    @Test
    public void confirmsStandardAndLunarRegardlessOfRuneSupply()
    {
        pouchPresent = true;
        bookPresent = false;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(0);
        refresh();
        assertEquals("Confirm spellbook : Standard", plugin.getReminderLongText());
        assertEquals("Standard!", plugin.getReminderShortText());
        assertTrue(plugin.shouldShowWarning());
        plugin.confirmWarning(plugin.getWarningVersion());
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(2);
        refresh();
        assertEquals("Lunar!", plugin.getReminderShortText());
        assertTrue(plugin.shouldShowWarning());
    }

    @Test
    public void bookAlonePromptsOnEveryOtherSpellbookAndRearmsAfterBanking()
    {
        suppliedCasts = 0;
        pouchPresent = false;
        String[] spellbooks = {"Standard", "Ancients", "Lunar"};
        for (int spellbook = 0; spellbook < spellbooks.length; spellbook++)
        {
            when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(spellbook);
            bookPresent = false;
            refresh();
            assertFalse(plugin.shouldShowWarning());
            bookPresent = true;
            refresh();
            assertTrue(plugin.shouldShowWarning());
            assertEquals("Confirm spellbook : " + spellbooks[spellbook], plugin.getReminderLongText());
            plugin.confirmWarning(plugin.getWarningVersion());
            refresh();
            assertFalse(plugin.shouldShowWarning());
            bookPresent = false;
            refresh();
            bookPresent = true;
            refresh();
            assertTrue(plugin.shouldShowWarning());
        }
    }

    @Test
    public void bookOnlyPromptHonorsExtendedCheckAndSpellbookToggles()
    {
        suppliedCasts = 0;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        when(config.checkCarriedRunePouch()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
        when(config.checkCarriedRunePouch()).thenReturn(true);
        when(config.notifyOnWrongSpellbook()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
        when(config.notifyOnWrongSpellbook()).thenReturn(true);
        refresh();
        assertTrue(plugin.shouldShowWarning());
    }

    @Test
    public void changingFromConfirmedAncientsToStandardRequiresNewConfirmation()
    {
        pouchPresent = true;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        long oldVersion = plugin.getWarningVersion();
        plugin.confirmWarning(oldVersion);
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(0);
        refresh();
        plugin.confirmWarning(oldVersion);
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Standard", plugin.getReminderLongText());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void switchingToArceuusAsksConfirmationBeforeShowingMissingSupplies()
    {
        pouchPresent = true;
        bookPresent = false;
        suppliedCasts = 0;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(3);
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
        assertEquals("Arceuus!", plugin.getReminderShortText());
        long confirmationVersion = plugin.getWarningVersion();
        plugin.confirmWarning(confirmationVersion);
        assertEquals("Missing thrall runes", plugin.getReminderLongText());
        plugin.confirmWarning(confirmationVersion);
        assertTrue(plugin.shouldShowWarning());
        refresh();
        assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
        suppliedCasts = 10;
        refresh();
        assertEquals(MissingCondition.BOOK_OF_THE_DEAD, plugin.getCurrentMissingCondition());
    }

    @Test
    public void arceuusConfirmationKeepsSupplyChecksSeparateAndDoesNotLoop()
    {
        pouchPresent = true;
        bookPresent = false;
        refresh();
        assertEquals(MissingCondition.SPELLBOOK_CONFIRMATION, plugin.getCurrentMissingCondition());
        plugin.confirmWarning(plugin.getWarningVersion());
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.BOOK_OF_THE_DEAD, plugin.getCurrentMissingCondition());
        plugin.confirmWarning(plugin.getWarningVersion());
        refresh();
        refresh();
        assertFalse(plugin.shouldShowWarning());
        suppliedCasts = 0;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
        verify(notifier, times(3)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void bookAutomaticallyConfirmsArceuusWithOrWithoutPouch()
    {
        for (boolean carryPouch : new boolean[] {false, true})
        {
            pouchPresent = carryPouch;
            suppliedCasts = 10;
            refresh();
            assertFalse(plugin.shouldShowWarning());
            suppliedCasts = 0;
            refresh();
            assertTrue(plugin.shouldShowWarning());
            assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
        }
    }

    @Test
    public void withdrawingBookResolvesArceuusPromptButStillChecksRunes()
    {
        pouchPresent = true;
        for (int casts : new int[] {0, 10})
        {
            suppliedCasts = casts;
            bookPresent = false;
            refresh();
            assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
            long oldVersion = plugin.getWarningVersion();
            bookPresent = true;
            refresh();
            plugin.confirmWarning(oldVersion);
            assertEquals(casts == 0, plugin.shouldShowWarning());
            assertEquals(casts == 0 ? MissingCondition.THRALL_RUNES : MissingCondition.NONE,
                plugin.getCurrentMissingCondition());
        }
        bookPresent = false;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
    }

    @Test
    public void fastPouchBankingRearmsArceuusEvenAfterSupplyWarningWasDismissed()
    {
        pouchPresent = true;
        bookPresent = false;
        refresh();
        long oldVersion = plugin.getWarningVersion();
        plugin.confirmWarning(oldVersion);
        plugin.confirmWarning(plugin.getWarningVersion());
        assertFalse(plugin.shouldShowWarning());
        pouchPresent = false;
        inventoryChanged();
        pouchPresent = true;
        inventoryChanged();
        plugin.onGameTick(new GameTick());
        plugin.confirmWarning(oldVersion);
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
        refresh();
        verify(notifier, times(3)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void arceuusPromptHonorsTogglesAndStaysHiddenWhenSuppliesAreDisabled()
    {
        pouchPresent = true;
        bookPresent = false;
        when(config.notifyOnMissingBook()).thenReturn(false);
        when(config.notifyOnMissingRunes()).thenReturn(false);
        when(config.checkCarriedRunePouch()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
        when(config.checkCarriedRunePouch()).thenReturn(true);
        when(config.notifyOnWrongSpellbook()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
        when(config.notifyOnWrongSpellbook()).thenReturn(true);
        refresh();
        assertTrue(plugin.shouldShowWarning());
        plugin.confirmWarning(plugin.getWarningVersion());
        refresh();
        suppliedCasts = 0;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void returningToArceuusRequiresNewPouchConfirmation()
    {
        pouchPresent = true;
        bookPresent = false;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        assertEquals("Confirm spellbook : Ancients", plugin.getReminderLongText());
        plugin.confirmWarning(plugin.getWarningVersion());
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(3);
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
    }

    @Test
    public void restartingClearsArceuusConfirmation() throws Exception
    {
        pouchPresent = true;
        bookPresent = false;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        plugin.shutDown();
        plugin.startUp();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Arceuus", plugin.getReminderLongText());
    }

    @Test
    public void disablingSpellbookPromptStillAllowsRuneWarning()
    {
        pouchPresent = true;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        suppliedCasts = 0;
        when(config.notifyOnWrongSpellbook()).thenReturn(false);
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
    }

    @Test
    public void extendedPouchRuleCanBeDisabled()
    {
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        pouchPresent = true;
        suppliedCasts = 0;
        when(config.checkCarriedRunePouch()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
    }

    @Test
    public void runeNotificationToggleStillApplies()
    {
        pouchPresent = true;
        suppliedCasts = 0;
        when(config.notifyOnMissingRunes()).thenReturn(false);
        refresh();
        assertFalse(plugin.shouldShowWarning());
    }

    @Test
    public void noPouchKeepsOriginalTwoOfThreeRule()
    {
        bookPresent = false;
        suppliedCasts = 0;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        suppliedCasts = 10;
        refresh();
        assertEquals(MissingCondition.BOOK_OF_THE_DEAD, plugin.getCurrentMissingCondition());
        assertTrue(plugin.shouldShowWarning());
    }

    @Test
    public void hotkeyDismissesRuneWarningWithoutButtonAndSurvivesInventoryUpdates()
    {
        suppliedCasts = 0;
        refresh();
        ArgumentCaptor<HotkeyListener> hotkey = ArgumentCaptor.forClass(HotkeyListener.class);
        verify(keyManager).registerKeyListener(hotkey.capture());
        hotkey.getValue().hotkeyPressed();
        refresh();
        refresh();
        assertFalse(plugin.shouldShowWarning());
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void warningRearmsAfterRequirementsAreFixed()
    {
        suppliedCasts = 0;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        suppliedCasts = 10;
        refresh();
        suppliedCasts = 0;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void changedWarningNotifiesAndCannotBeDismissedByAnOldClick()
    {
        suppliedCasts = 0;
        refresh();
        long oldWarning = plugin.getWarningVersion();
        suppliedCasts = 10;
        bookPresent = false;
        refresh();
        plugin.confirmWarning(oldWarning);
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.BOOK_OF_THE_DEAD, plugin.getCurrentMissingCondition());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void intermediateContainerUpdatesAreCoalesced()
    {
        suppliedCasts = 0;
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INVENTORY.getId(), null));
        suppliedCasts = 10;
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldShowWarning());
        verifyNoInteractions(notifier);
    }

    @Test
    public void backingVarpUpdatesRefreshPouchRunes()
    {
        suppliedCasts = 0;
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(-1);
        event.setVarpId(123);
        plugin.onVarbitChanged(event);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.shouldShowWarning());
    }

    @Test
    public void oneCastIsEnoughAndOnlyDepletionWarns()
    {
        suppliedCasts = 3;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        suppliedCasts = 1;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        verifyNoInteractions(notifier);
        suppliedCasts = 0;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Missing thrall runes", plugin.getReminderLongText());
        assertEquals("Runes!", plugin.getReminderShortText());
        refresh();
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void requiredRunesFollowAutomaticTierAtMagicLevelBoundaries()
    {
        for (ThrallTier tier : ThrallTier.values())
        {
            Map<ThrallRune, Integer> runes = new EnumMap<>(ThrallRune.class);
            runes.putAll(tier.getRunesPerCast());
            when(loadout.snapshot()).thenReturn(new PlayerLoadout.Snapshot(
                new PlayerLoadout.CarriedItems(true, false), runes));
            when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(tier.getMagicLevel());
            refresh();
            assertFalse(plugin.shouldShowWarning());
            if (tier != ThrallTier.LESSER)
            {
                when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(tier.getMagicLevel() - 1);
                refresh();
                assertTrue(plugin.shouldShowWarning());
                assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
            }
        }
    }

    @Test
    public void logoutAndRestartClearConfirmation() throws Exception
    {
        suppliedCasts = 0;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
        assertFalse(plugin.shouldShowWarning());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        event.setGameState(GameState.LOGGED_IN);
        plugin.onGameStateChanged(event);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.shouldShowWarning());
        plugin.confirmWarning(plugin.getWarningVersion());
        plugin.shutDown();
        verify(mouseManager).unregisterMouseListener(confirmMouseListener);
        plugin.startUp();
        assertTrue(plugin.shouldShowWarning());
    }

    @Test
    public void bankingOnlyBookThenWithdrawingRearmsAncientsPrompt()
    {
        pouchPresent = true;
        suppliedCasts = 0;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        bookPresent = false;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        bookPresent = true;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Ancients", plugin.getReminderLongText());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
        plugin.confirmWarning(plugin.getWarningVersion());
        refresh();
        assertFalse(plugin.shouldShowWarning());
    }

    @Test
    public void bankingOnlyPouchThenWithdrawingRearmsUnchangedRuneWarning()
    {
        pouchPresent = true;
        suppliedCasts = 0;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        pouchPresent = false;
        refresh();
        assertFalse(plugin.shouldShowWarning());
        pouchPresent = true;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        assertEquals(MissingCondition.THRALL_RUNES, plugin.getCurrentMissingCondition());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void fastBookDepositAndWithdrawalBeforeTickStillRearms()
    {
        assertFastBankingRearms(true);
    }

    @Test
    public void fastPouchDepositAndWithdrawalBeforeTickStillRearms()
    {
        assertFastBankingRearms(false);
    }

    private void assertFastBankingRearms(boolean bankBook)
    {
        pouchPresent = true;
        suppliedCasts = 0;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        long oldVersion = plugin.getWarningVersion();
        plugin.confirmWarning(oldVersion);
        clearInvocations(loadout);
        if (bankBook) bookPresent = false;
        else pouchPresent = false;
        inventoryChanged();
        bookPresent = true;
        pouchPresent = true;
        inventoryChanged();
        verify(loadout, never()).snapshot();
        plugin.onGameTick(new GameTick());
        verify(loadout, times(1)).snapshot();
        plugin.confirmWarning(oldVersion);
        assertTrue(plugin.shouldShowWarning());
        assertEquals("Confirm spellbook : Ancients", plugin.getReminderLongText());
        verify(notifier, times(2)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void withdrawingPouchWithFixedRunesDoesNotShowFalseWarning()
    {
        pouchPresent = true;
        suppliedCasts = 0;
        refresh();
        plugin.confirmWarning(plugin.getWarningVersion());
        pouchPresent = false;
        inventoryChanged();
        pouchPresent = true;
        suppliedCasts = 10;
        inventoryChanged();
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldShowWarning());
        assertEquals(MissingCondition.NONE, plugin.getCurrentMissingCondition());
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void alreadyVisibleWarningDoesNotNotifyAgainForBankingBook()
    {
        pouchPresent = true;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        bookPresent = false;
        refresh();
        bookPresent = true;
        refresh();
        assertTrue(plugin.shouldShowWarning());
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    @Test
    public void confirmingCurrentLoadoutBeforeNextTickDoesNotImmediatelyRearm()
    {
        pouchPresent = true;
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        refresh();
        bookPresent = false;
        inventoryChanged();
        bookPresent = true;
        inventoryChanged();
        plugin.confirmWarning(plugin.getWarningVersion());
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldShowWarning());
        verify(notifier, times(1)).notify(eq(config.notification()), anyString());
    }

    private void inventoryChanged()
    {
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INVENTORY.getId(), null));
    }

    private void refresh()
    {
        inventoryChanged();
        plugin.onGameTick(new GameTick());
    }
}
