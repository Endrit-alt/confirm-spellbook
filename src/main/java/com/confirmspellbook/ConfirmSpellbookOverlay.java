package com.confirmspellbook;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.Text;

@Singleton
public class ConfirmSpellbookOverlay extends Overlay
{
    private static final int PADDING = 6;
    private static final int BUTTON_GAP = 12;
    private static final int BUTTON_INSET = 3;
    private static final String CONFIRM_TEXT = "Confirm";
    private static final BasicStroke BUTTON_BORDER_STROKE = new BasicStroke(0.5f);

    private final Client client;
    private final ConfirmSpellbookPlugin plugin;
    private final ConfirmSpellbookConfig config;

    // Publish bounds and warning identity together, from the renderer to the AWT mouse listener.
    private volatile ConfirmTarget confirmTarget;

    @Inject
    ConfirmSpellbookOverlay(Client client, ConfirmSpellbookPlugin plugin, ConfirmSpellbookConfig config)
    {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.getGameState() != GameState.LOGGED_IN || !plugin.shouldShowWarning())
        {
            clearConfirmTarget();
            return null;
        }

        String displayText = getDisplayText();
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(Text.removeTags(displayText));
        int buttonWidth = metrics.stringWidth(CONFIRM_TEXT) + PADDING * 2;
        int height = metrics.getHeight() + PADDING * 2;
        int buttonX = PADDING + textWidth + BUTTON_GAP;
        boolean showConfirm = plugin.getCurrentMissingCondition() == MissingCondition.SPELLBOOK_CONFIRMATION;
        int width = showConfirm ? buttonX + buttonWidth + 2 : textWidth + PADDING * 2;

        Color reminderColor = config.reminderColor();
        Color warningColor = config.flashReminderBox() && client.getGameCycle() % 40 >= 20
            ? config.flashColor() : reminderColor;
        BackgroundComponent background = new BackgroundComponent();
        background.setRectangle(new Rectangle(0, 0, width, height));
        background.setBackgroundColor(warningColor);
        background.render(graphics);

        int baseline = PADDING + metrics.getAscent();
        drawText(graphics, displayText, PADDING, baseline, Color.WHITE);
        if (!showConfirm)
        {
            clearConfirmTarget();
            return new Dimension(width, height);
        }

        Rectangle button = new Rectangle(buttonX, BUTTON_INSET, width - buttonX - BUTTON_INSET,
            height - BUTTON_INSET * 2);
        Rectangle canvasButton = new Rectangle(button);
        canvasButton.translate(getBounds().x, getBounds().y);
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        boolean hovered = mouse != null && !client.isMenuOpen() && canvasButton.contains(mouse.getX(), mouse.getY());
        // An opaque fill keeps the shared panel's flash from bleeding through the button.
        Color buttonColor = new Color(12 + reminderColor.getRed() / 4, 12 + reminderColor.getGreen() / 4,
            12 + reminderColor.getBlue() / 4);
        graphics.setColor(hovered ? new Color(40, 150, 60) : buttonColor);
        graphics.fillRect(button.x, button.y, button.width, button.height);
        Graphics2D borderGraphics = (Graphics2D) graphics.create();
        try
        {
            borderGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            borderGraphics.setStroke(BUTTON_BORDER_STROKE);
            borderGraphics.setColor(Color.WHITE);
            borderGraphics.drawRect(button.x, button.y, button.width - 1, button.height - 1);
        }
        finally
        {
            borderGraphics.dispose();
        }

        drawText(graphics, CONFIRM_TEXT, buttonX + PADDING, baseline, Color.WHITE);
        confirmTarget = new ConfirmTarget(canvasButton, plugin.getWarningVersion());
        return new Dimension(width, height);
    }

    private void drawText(Graphics2D graphics, String text, int x, int y, Color color)
    {
        TextComponent component = new TextComponent();
        component.setText(text);
        component.setPosition(new Point(x, y));
        component.setColor(color);
        component.render(graphics);
    }

    boolean confirmAt(Point point)
    {
        ConfirmTarget target = confirmTarget;
        if (target == null || !plugin.shouldShowWarning()
            || plugin.getCurrentMissingCondition() != MissingCondition.SPELLBOOK_CONFIRMATION
            || client.getGameState() != GameState.LOGGED_IN
            || client.isMenuOpen() || !target.bounds.contains(point))
        {
            return false;
        }

        clearConfirmTarget();
        plugin.confirmWarning(target.warningVersion);
        return true;
    }

    void clearConfirmTarget()
    {
        confirmTarget = null;
    }

    private String getDisplayText()
    {
        switch (config.reminderStyle())
        {
            case CUSTOM_TEXT:
                return config.customText() == null ? "" : config.customText();
            case SHORT_TEXT:
                return plugin.getReminderShortText();
            default:
                return plugin.getReminderLongText();
        }
    }

    private static final class ConfirmTarget
    {
        private final Rectangle bounds;
        private final long warningVersion;

        private ConfirmTarget(Rectangle bounds, long warningVersion)
        {
            this.bounds = bounds;
            this.warningVersion = warningVersion;
        }
    }
}
