package com.confirmspellbook;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfirmOverlayTest
{
    private final Client client = mock(Client.class);
    private final ConfirmSpellbookPlugin plugin = mock(ConfirmSpellbookPlugin.class);
    private final ConfirmSpellbookConfig config = spy(new ConfirmSpellbookConfig() {});
    private ConfirmSpellbookOverlay overlay;
    private ConfirmMouseListener listener;
    private final Canvas canvas = new Canvas();

    @Before
    public void setUp()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(plugin.shouldShowWarning()).thenReturn(true);
        when(plugin.getWarningVersion()).thenReturn(7L);
        when(plugin.getCurrentMissingCondition()).thenReturn(MissingCondition.SPELLBOOK_CONFIRMATION);
        when(plugin.getReminderLongText()).thenReturn("Confirm spellbook : Ancients");
        when(plugin.getReminderShortText()).thenReturn("Ancients!");
        // Use actual Guice construction: the renderer and mouse listener must share one overlay.
        Injector injector = Guice.createInjector(new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(Client.class).toInstance(client);
                bind(ConfirmSpellbookPlugin.class).toProvider(() -> plugin);
                bind(ConfirmSpellbookConfig.class).toInstance(config);
            }
        });
        overlay = injector.getInstance(ConfirmSpellbookOverlay.class);
        listener = injector.getInstance(ConfirmMouseListener.class);
    }

    @Test
    public void confirmConsumesPressReleaseAndClick() throws Exception
    {
        Point button = render("confirm-normal");
        assertTrue(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, button, 0, MouseEvent.BUTTON1)).isConsumed());
        verify(plugin).confirmWarning(7L);
        when(plugin.shouldShowWarning()).thenReturn(false);
        assertTrue(listener.mouseReleased(event(MouseEvent.MOUSE_RELEASED, button, 0, MouseEvent.BUTTON1)).isConsumed());
        assertTrue(listener.mouseClicked(event(MouseEvent.MOUSE_CLICKED, button, 0, MouseEvent.BUTTON1)).isConsumed());
        assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, button, 0, MouseEvent.BUTTON1)).isConsumed());
    }

    @Test
    public void otherClicksAndAltDraggingPassThrough() throws Exception
    {
        Point button = render("confirm-drag");
        assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, new Point(55, 55), 0, MouseEvent.BUTTON1)).isConsumed());
        assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, button, 0, MouseEvent.BUTTON3)).isConsumed());
        assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, button, InputEvent.ALT_DOWN_MASK, MouseEvent.BUTTON1)).isConsumed());
        verify(plugin, never()).confirmWarning(anyLong());
    }

    @Test
    public void menuAndLoginScreenBlockConfirmation() throws Exception
    {
        Point button = render("confirm-menu");
        when(client.isMenuOpen()).thenReturn(true);
        assertFalse(overlay.confirmAt(button));
        when(client.isMenuOpen()).thenReturn(false);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(overlay.confirmAt(button));
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            assertNull(overlay.render(graphics));
        }
        finally
        {
            graphics.dispose();
        }
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        assertFalse(overlay.confirmAt(button));
    }

    @Test
    public void buttonTracksMovedOverlayAndEveryTextStyle() throws Exception
    {
        Point oldButton = render("confirm-long");
        overlay.getBounds().setLocation(350, 150);
        when(config.reminderStyle()).thenReturn(ConfirmSpellbookStyle.SHORT_TEXT);
        Point newButton = render("confirm-short");
        assertFalse(overlay.confirmAt(oldButton));
        assertTrue(overlay.confirmAt(newButton));
        when(config.reminderStyle()).thenReturn(ConfirmSpellbookStyle.CUSTOM_TEXT);
        when(config.customText()).thenReturn("<col=ffff00>Check your thrall supplies!</col>");
        assertTrue(overlay.confirmAt(render("confirm-custom")));
        when(config.customText()).thenReturn("");
        assertTrue(overlay.confirmAt(render("confirm-empty")));
    }

    @Test
    public void flashingSpellbookPromptKeepsConfirmVisible() throws Exception
    {
        when(config.flashReminderBox()).thenReturn(true);
        when(client.getGameCycle()).thenReturn(0);
        Point button = render("confirm-flash-base");
        when(client.getGameCycle()).thenReturn(25);
        assertTrue(overlay.confirmAt(render("confirm-flash")));
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(button.x, button.y));
        render("confirm-flash-hover");
        when(client.getGameCycle()).thenReturn(0);
        render("confirm-flash-hover-base");
    }

    @Test
    public void supplyWarningsRemoveButtonSpaceAndDoNotConsumeClicks() throws Exception
    {
        for (MissingCondition condition : new MissingCondition[] {
            MissingCondition.BOOK_OF_THE_DEAD, MissingCondition.THRALL_RUNES })
        {
            when(plugin.getReminderLongText()).thenReturn(condition.getLongText());
            when(plugin.getCurrentMissingCondition()).thenReturn(MissingCondition.SPELLBOOK_CONFIRMATION);
            Point oldButton = render("transition-" + condition);
            when(plugin.getCurrentMissingCondition()).thenReturn(condition);
            assertFalse(overlay.confirmAt(oldButton));
            Point warning = render("warning-" + condition);
            assertTrue(warning.x < oldButton.x - 40);
            assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, oldButton, 0, MouseEvent.BUTTON1)).isConsumed());
            assertFalse(listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, warning, 0, MouseEvent.BUTTON1)).isConsumed());
        }
        when(config.reminderStyle()).thenReturn(ConfirmSpellbookStyle.SHORT_TEXT);
        when(plugin.getReminderShortText()).thenReturn("Runes!");
        assertFalse(overlay.confirmAt(render("warning-runes-short")));
        when(config.reminderStyle()).thenReturn(ConfirmSpellbookStyle.CUSTOM_TEXT);
        when(config.customText()).thenReturn("Confirm spellbook : Ancients");
        assertFalse(overlay.confirmAt(render("warning-custom-no-button")));
        verify(plugin, never()).confirmWarning(anyLong());
    }

    @Test
    public void spellbookPromptsRenderWithConfirm() throws Exception
    {
        when(plugin.getReminderLongText()).thenReturn("Confirm spellbook : Ancients");
        Point button = render("confirm-ancients");
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(button.x, button.y));
        render("confirm-hover");
        assertTrue(overlay.confirmAt(button));
        when(client.getMouseCanvasPosition()).thenReturn(null);
        when(plugin.getReminderLongText()).thenReturn("Confirm spellbook : Standard");
        assertTrue(overlay.confirmAt(render("confirm-standard")));
        when(plugin.getReminderLongText()).thenReturn("Confirm spellbook : Lunar");
        assertTrue(overlay.confirmAt(render("confirm-lunar")));
        when(plugin.getReminderLongText()).thenReturn("Confirm spellbook : Arceuus");
        assertTrue(overlay.confirmAt(render("confirm-arceuus")));
        when(config.reminderStyle()).thenReturn(ConfirmSpellbookStyle.SHORT_TEXT);
        when(plugin.getReminderShortText()).thenReturn("Ancients!");
        render("confirm-ancients-short");
        when(plugin.getReminderShortText()).thenReturn("Standard!");
        render("confirm-standard-short");
        when(plugin.getReminderShortText()).thenReturn("Lunar!");
        assertTrue(overlay.confirmAt(render("confirm-lunar-short")));
        when(plugin.getReminderShortText()).thenReturn("Arceuus!");
        assertTrue(overlay.confirmAt(render("confirm-arceuus-short")));
    }

    private Point render(String filename) throws Exception
    {
        if (overlay.getBounds().x == 0)
        {
            overlay.getBounds().setLocation(50, 50);
        }
        BufferedImage image = new BufferedImage(850, 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Dimension size;
        try
        {
            graphics.setColor(new Color(30, 32, 35));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setFont(FontManager.getRunescapeFont());
            graphics.translate(overlay.getBounds().x, overlay.getBounds().y);
            size = overlay.render(graphics);
            assertNotNull(size);
            assertTrue(size.width > 40);
            assertTrue(size.height > 10);
        }
        finally
        {
            graphics.dispose();
        }
        File directory = new File("build/overlay-previews");
        directory.mkdirs();
        ImageIO.write(image, "png", new File(directory, filename + ".png"));
        return new Point(overlay.getBounds().x + size.width - 15, overlay.getBounds().y + size.height / 2);
    }

    private MouseEvent event(int id, Point point, int modifiers, int button)
    {
        return new MouseEvent(canvas, id, 0, modifiers, point.x, point.y, 1, false, button);
    }
}
