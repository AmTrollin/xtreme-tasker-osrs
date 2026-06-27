package com.amtrollin.xtremetasker.ui.rules;

import org.junit.Test;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RulesTabRendererTest
{
    @Test
    public void syncTabDoesNotLayOutRemovedMismatchReviewActions()
    {
        BufferedImage image = new BufferedImage(700, 460, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            FontMetrics fm = g.getFontMetrics();
            RulesTabRenderer renderer = new RulesTabRenderer(
                    690,
                    12,
                    16,
                    2,
                    new Color(221, 178, 85),
                    new Color(190, 190, 190)
            );

            RulesTabLayout layout = renderer.render(
                    g,
                    fm,
                    0,
                    40,
                    new Rectangle(0, 0, 690, 460),
                    RulesTabLayout.SubTab.DATA_SYNCS,
                    "Account progress sync done! No new completions found.",
                    "Jun 26, 2026 1:23 PM",
                    null,
                    null,
                    false,
                    0,
                    0
            );

            assertEquals(0, layout.syncCaReviewButtonBounds.width);
            assertEquals(0, layout.syncCaReviewButtonBounds.height);
            assertEquals(0, layout.syncCaReviewIgnoreButtonBounds.width);
            assertEquals(0, layout.syncCaReviewIgnoreButtonBounds.height);
        }
        finally
        {
            g.dispose();
        }
    }

    @Test
    public void syncTabStillLaysOutFoundCompletionReviewAction()
    {
        BufferedImage image = new BufferedImage(700, 460, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            FontMetrics fm = g.getFontMetrics();
            RulesTabRenderer renderer = new RulesTabRenderer(
                    690,
                    12,
                    16,
                    2,
                    new Color(221, 178, 85),
                    new Color(190, 190, 190)
            );

            RulesTabLayout layout = renderer.render(
                    g,
                    fm,
                    0,
                    40,
                    new Rectangle(0, 0, 690, 460),
                    RulesTabLayout.SubTab.DATA_SYNCS,
                    "Account progress sync done! 1 new completed task(s) found.",
                    "Jun 26, 2026 1:23 PM",
                    null,
                    null,
                    false,
                    1,
                    0
            );

            assertTrue(layout.syncCaFoundReviewButtonBounds.width > 0);
            assertTrue(layout.syncCaFoundReviewButtonBounds.height > 0);
        }
        finally
        {
            g.dispose();
        }
    }
}
