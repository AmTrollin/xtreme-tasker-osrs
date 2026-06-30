package com.amtrollin.xtremetasker.ui.text;

import org.junit.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TextUtilsTest
{
    @Test
    public void wrapTextKeepsParentheticalPhraseTogether()
    {
        FontMetrics fm = fontMetrics();
        String prefix = "Get reward from Tempor Tantrum";
        String suffix = "(Stormy key)";
        int maxWidth = Math.max(fm.stringWidth(prefix), fm.stringWidth(suffix));

        List<String> lines = TextUtils.wrapText(prefix + " " + suffix, fm, maxWidth);

        assertEquals(2, lines.size());
        assertEquals(prefix, lines.get(0));
        assertEquals(suffix, lines.get(1));
    }

    @Test
    public void wrapTextSplitsBetweenFullWords()
    {
        FontMetrics fm = fontMetrics();
        int maxWidth = fm.stringWidth("Get 1") + 1;

        List<String> lines = TextUtils.wrapText("Get 1 unique from boss", fm, maxWidth);

        assertTrue(lines.contains("unique"));
        for (String line : lines)
        {
            assertTrue("Line should not contain a partial unique split: " + line,
                    !line.contains("uni") || line.equals("unique"));
        }
    }

    private static FontMetrics fontMetrics()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(new Font("Dialog", Font.PLAIN, 12));
        return graphics.getFontMetrics();
    }
}
