package com.confirmspellbook;

import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.client.input.MouseAdapter;

/** Consumes the entire Confirm click so it cannot also interact with the game underneath. */
public class ConfirmMouseListener extends MouseAdapter
{
    private final ConfirmSpellbookOverlay overlay;
    private boolean confirming;

    @Inject
    ConfirmMouseListener(ConfirmSpellbookOverlay overlay)
    {
        this.overlay = overlay;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (event.getButton() == MouseEvent.BUTTON1)
        {
            // Leave Alt-dragging to RuneLite's overlay positioning controls.
            confirming = !event.isConsumed() && !event.isAltDown() && overlay.confirmAt(event.getPoint());
            if (confirming)
            {
                event.consume();
            }
        }
        return event;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        if (confirming && event.getButton() == MouseEvent.BUTTON1)
        {
            event.consume();
        }
        return event;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        if (confirming && event.getButton() == MouseEvent.BUTTON1)
        {
            event.consume();
            confirming = false;
        }
        return event;
    }

    void reset()
    {
        confirming = false;
    }
}
