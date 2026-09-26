package com.confirmspellbook;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
    name = "Confirm Spellbook",
    description = "Confirm your spellbook after bank withdrawals and check for missing thrall supplies",
    tags = {"confirm", "spellbook", "ancient", "ancients", "ancient magicks", "standard", "lunar", "arceuus", "thrall", "thralls", "book of the dead", "rune pouch", "runes", "magic", "reminder", "warning", "loadout", "endrit"}
)
public class ConfirmSpellbookPlugin extends Plugin
{
    private static final int ARCEUUS_SPELLBOOK = 3;

    @Inject
    private Client client;

    @Inject
    private ConfirmSpellbookConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfirmSpellbookOverlay overlay;

    @Inject
    private Notifier notifier;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private RuneLiteConfig runeLiteConfig;

    @Inject
    private ClientUI clientUI;

    @Inject
    private KeyManager keyManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ConfirmMouseListener confirmMouseListener;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PlayerLoadout loadout;

    private boolean hasArceuusSpellbook = false;
    private boolean hasSufficientThrallRunes = false;
    private boolean hasBookOfTheDead = false;
    private boolean hasRunePouch = false;
    private boolean carriedItemsKnown = false;
    private boolean checksArmed = false;
    private boolean acknowledgmentNeedsReset = false;
    private volatile boolean arceuusSpellbookConfirmed = false;
    private int spellbook = -1;
    private int warningSpellbook = -1;
    private volatile boolean warningShown = false;
    private volatile MissingCondition currentMissingCondition = MissingCondition.NONE;
    private volatile long warningVersion = 0;
    private boolean stateDirty = true;
    private volatile boolean active;
    private int magicLevel = 1;

    private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hideReminderHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            confirmWarning(warningVersion);
        }
    };

    @Override
    protected void startUp() throws Exception
    {
        active = true;
        clearWarning();
        overlayManager.add(overlay);
        keyManager.registerKeyListener(hotkeyListener);
        confirmMouseListener.reset();
        mouseManager.registerMouseListener(confirmMouseListener);
        clientThread.invokeLater(this::refreshPlayerState);
        log.info("Confirm Spellbook started!");
    }

    @Override
    protected void shutDown() throws Exception
    {
        active = false;
        clearWarning();
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(hotkeyListener);
        mouseManager.unregisterMouseListener(confirmMouseListener);
        confirmMouseListener.reset();
        log.info("Confirm Spellbook stopped!");
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // The server can update the backing varp without emitting an individual varbit id.
        if (event.getVarpId() >= 0 || event.getVarbitId() == VarbitID.SPELLBOOK
            || PlayerLoadout.isRunePouchVarbit(event.getVarbitId()))
        {
            stateDirty = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (stateDirty)
        {
            refreshPlayerState();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            stateDirty = true;
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            clearWarning();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (isInventoryOrEquipment(event.getContainerId()))
        {
            if (active && client.getGameState() == GameState.LOGGED_IN)
            {
                // Preserve item transitions even when deposit and withdrawal occur before the next tick.
                checkCarriedItems(loadout.carriedItems(), true);
            }
            stateDirty = true;
        }
    }

    // Magic experience arrives constantly in combat, so only a level change can move the auto tier.
    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        boolean magicLevelChanged = event.getSkill() == Skill.MAGIC && event.getLevel() != magicLevel;
        if (magicLevelChanged)
        {
            stateDirty = true;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!ConfirmSpellbookConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        clientThread.invokeLater(this::refreshPlayerState);
    }

    private void refreshPlayerState()
    {
        if (!active || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        stateDirty = false;
        if (!checksArmed)
        {
            // Login/startup only establishes what is already carried, without reading pouch runes.
            checkCarriedItems(loadout.carriedItems(), false);
            return;
        }
        magicLevel = client.getRealSkillLevel(Skill.MAGIC);
        checkSpellbook();
        PlayerLoadout.Snapshot snapshot = loadout.snapshot();
        checkThrallRunes(snapshot);
        checkCarriedItems(snapshot.getCarriedItems(), false);
        evaluateWarningState();
    }

    private boolean isInventoryOrEquipment(int containerId)
    {
        return containerId == InventoryID.INVENTORY.getId()
            || containerId == InventoryID.EQUIPMENT.getId();
    }

    private void checkSpellbook()
    {
        int currentSpellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
        if (spellbook != currentSpellbook)
        {
            arceuusSpellbookConfirmed = false;
        }
        spellbook = currentSpellbook;
        hasArceuusSpellbook = spellbook == ARCEUUS_SPELLBOOK;
    }

    private void checkThrallRunes(PlayerLoadout.Snapshot snapshot)
    {
        ThrallTier tier = ThrallTier.highestCastableAt(magicLevel);
        hasSufficientThrallRunes = snapshot.castsAvailable(tier) >= 1;
    }

    private void checkCarriedItems(PlayerLoadout.CarriedItems carriedItems, boolean containerEvent)
    {
        boolean bookPresent = carriedItems.hasBookOfTheDead();
        boolean pouchPresent = carriedItems.hasRunePouch();
        if (carriedItemsKnown && ((!hasBookOfTheDead && bookPresent) || (!hasRunePouch && pouchPresent)))
        {
            if (!checksArmed && containerEvent)
            {
                Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
                checksArmed = bank != null && !bank.isHidden();
            }
            acknowledgmentNeedsReset = true;
            arceuusSpellbookConfirmed = false;
            // A click queued for the previous loadout must not acknowledge the new one.
            warningVersion++;
            overlay.clearConfirmTarget();
        }
        // The book itself confirms Arceuus. If it is removed later, ask again.
        if (bookPresent || !pouchPresent)
        {
            arceuusSpellbookConfirmed = false;
        }
        hasBookOfTheDead = bookPresent;
        hasRunePouch = pouchPresent;
        carriedItemsKnown = true;
    }

    private void evaluateWarningState()
    {
        // Confirm the spellbook first; carrying the book already confirms Arceuus.
        boolean checkCarriedItems = config.checkCarriedRunePouch();
        if (!checkCarriedItems || !config.notifyOnWrongSpellbook())
        {
            arceuusSpellbookConfirmed = false;
        }
        MissingCondition missingCondition;
        if (checkCarriedItems && (hasBookOfTheDead || hasRunePouch)
            && (!hasArceuusSpellbook || (!hasBookOfTheDead && !arceuusSpellbookConfirmed))
            && config.notifyOnWrongSpellbook())
        {
            missingCondition = MissingCondition.SPELLBOOK_CONFIRMATION;
        }
        else if (checkCarriedItems && hasRunePouch && !hasSufficientThrallRunes && config.notifyOnMissingRunes())
        {
            missingCondition = MissingCondition.THRALL_RUNES;
        }
        else
        {
            missingCondition = countConditionsMet() == 2 ? determineMissingCondition() : MissingCondition.NONE;
        }

        if (!isConditionNotificationEnabled(missingCondition))
        {
            missingCondition = MissingCondition.NONE;
        }

        // Keep the condition after Confirm, so unrelated inventory updates cannot re-show it.
        boolean rearmAcknowledgedWarning = acknowledgmentNeedsReset && !warningShown;
        acknowledgmentNeedsReset = false;
        int nextWarningSpellbook = missingCondition == MissingCondition.SPELLBOOK_CONFIRMATION ? spellbook : -1;
        if (missingCondition == currentMissingCondition && nextWarningSpellbook == warningSpellbook
            && !rearmAcknowledgedWarning)
        {
            return;
        }

        currentMissingCondition = missingCondition;
        warningSpellbook = nextWarningSpellbook;
        warningVersion++;
        warningShown = missingCondition != MissingCondition.NONE;
        overlay.clearConfirmTarget();
        if (warningShown)
        {
            sendNotification();
        }
    }

    private int countConditionsMet()
    {
        int count = 0;
        if (hasArceuusSpellbook) count++;
        if (hasSufficientThrallRunes) count++;
        if (hasBookOfTheDead) count++;
        return count;
    }

    private boolean isConditionNotificationEnabled(MissingCondition condition)
    {
        switch (condition)
        {
            case BOOK_OF_THE_DEAD:
                return config.notifyOnMissingBook();
            case THRALL_RUNES:
                return config.notifyOnMissingRunes();
            case SPELLBOOK_CONFIRMATION:
                return config.notifyOnWrongSpellbook();
            default:
                return false;
        }
    }

    private MissingCondition determineMissingCondition()
    {
        if (!hasBookOfTheDead)
        {
            return MissingCondition.BOOK_OF_THE_DEAD;
        }

        if (!hasArceuusSpellbook)
        {
            return MissingCondition.SPELLBOOK_CONFIRMATION;
        }

        if (!hasSufficientThrallRunes)
        {
            return MissingCondition.THRALL_RUNES;
        }

        return MissingCondition.NONE;
    }

    public MissingCondition getCurrentMissingCondition()
    {
        return currentMissingCondition;
    }

    public String getReminderLongText()
    {
        if (currentMissingCondition == MissingCondition.SPELLBOOK_CONFIRMATION)
        {
            return "Confirm spellbook : " + getSpellbookName();
        }
        return currentMissingCondition.getLongText();
    }

    public String getReminderShortText()
    {
        if (currentMissingCondition == MissingCondition.SPELLBOOK_CONFIRMATION)
        {
            return getSpellbookName() + "!";
        }
        return currentMissingCondition.getShortText();
    }

    private void sendNotification()
    {
        Notification notification = config.notification();
        if (!notification.isEnabled())
        {
            return;
        }

        if (!notification.isOverride() || !notification.isInitialized())
        {
            // Resolve inherited settings before disabling the default chat copy.
            notification = new Notification(true, true, true,
                runeLiteConfig.enableTrayNotifications(), notification.getTrayIconType(),
                runeLiteConfig.notificationRequestFocus(), runeLiteConfig.notificationSound(), null,
                runeLiteConfig.notificationVolume(), runeLiteConfig.notificationTimeout(),
                runeLiteConfig.enableGameMessageNotification(), runeLiteConfig.flashNotification(),
                runeLiteConfig.notificationFlashColor(), runeLiteConfig.sendNotificationsWhenFocused());
        }

        String message = getReminderLongText();
        // Check focus before Notifier can bring the game window to the foreground.
        boolean sendChat = notification.isGameMessage() && client.getGameState() == GameState.LOGGED_IN
            && (notification.isSendWhenFocused() || !clientUI.isFocused());
        notifier.notify(notification.withGameMessage(false), message);
        if (sendChat)
        {
            chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(new ChatMessageBuilder()
                    .append(config.chatNotificationColor(), message).build())
                .build());
        }
    }

    public long getWarningVersion()
    {
        return warningVersion;
    }

    private String getSpellbookName()
    {
        switch (warningSpellbook)
        {
            case 0:
                return "Standard";
            case 1:
                return "Ancients";
            case 2:
                return "Lunar";
            case ARCEUUS_SPELLBOOK:
                return "Arceuus";
            default:
                return "Unknown (" + warningSpellbook + ")";
        }
    }

    // Both mouse events and hotkeys arrive off the client thread.
    public void confirmWarning(long expectedVersion)
    {
        clientThread.invokeLater(() ->
        {
            if (active && warningShown && warningVersion == expectedVersion)
            {
                warningShown = false;
                acknowledgmentNeedsReset = false;
                overlay.clearConfirmTarget();
                if (currentMissingCondition == MissingCondition.SPELLBOOK_CONFIRMATION
                    && warningSpellbook == ARCEUUS_SPELLBOOK)
                {
                    // Confirming the spellbook must not dismiss a separate supply warning.
                    arceuusSpellbookConfirmed = true;
                    evaluateWarningState();
                }
            }
        });
    }

    private void clearWarning()
    {
        hasBookOfTheDead = false;
        hasRunePouch = false;
        carriedItemsKnown = false;
        checksArmed = false;
        acknowledgmentNeedsReset = false;
        arceuusSpellbookConfirmed = false;
        warningShown = false;
        currentMissingCondition = MissingCondition.NONE;
        warningSpellbook = -1;
        warningVersion++;
        stateDirty = true;
        overlay.clearConfirmTarget();
    }

    public boolean shouldShowWarning()
    {
        return warningShown;
    }

    public boolean shouldShowConfirmButton()
    {
        return warningShown && (currentMissingCondition == MissingCondition.SPELLBOOK_CONFIRMATION
            || (currentMissingCondition == MissingCondition.BOOK_OF_THE_DEAD && arceuusSpellbookConfirmed));
    }

    @Provides
    ConfirmSpellbookConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ConfirmSpellbookConfig.class);
    }
}
