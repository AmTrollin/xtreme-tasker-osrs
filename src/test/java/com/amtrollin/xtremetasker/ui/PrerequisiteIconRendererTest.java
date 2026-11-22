package com.amtrollin.xtremetasker.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrerequisiteIconRendererTest
{
    @Test
    public void paddedQuestAndSkillSpritesHaveTheSameVisibleGapBeforeText()
    {
        BufferedImage quest = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage skill = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        Graphics2D q = quest.createGraphics();
        q.setColor(Color.BLUE);
        q.fillRect(10, 5, 6, 22);
        q.dispose();
        Graphics2D s = skill.createGraphics();
        s.setColor(Color.RED);
        s.fillRect(1, 3, 14, 11);
        s.dispose();

        for (BufferedImage sprite : new BufferedImage[] {quest, skill})
        {
            BufferedImage canvas = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            try
            {
                PrerequisiteIconRenderer.drawMarker(g, g.getFontMetrics(), sprite, 5, 30);
                int lastVisiblePixel = -1;
                for (int y = 0; y < canvas.getHeight(); y++)
                    for (int x = 0; x < canvas.getWidth(); x++)
                        if ((canvas.getRGB(x, y) >>> 24) != 0) lastVisiblePixel = Math.max(lastVisiblePixel, x);
                assertTrue(lastVisiblePixel >= 5);
                int textStart = PrerequisiteIconRenderer.textX(g.getFontMetrics(), 5, sprite);
                assertEquals("Both sprites must leave a six-pixel visible gap", 6, textStart - lastVisiblePixel - 1);
            }
            finally { g.dispose(); }
        }
    }
}
