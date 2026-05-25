package com.amtrollin.xtremetasker.ui.text;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

public final class TextUtils
{
    private TextUtils() {}

    public static String truncateToWidth(String text, FontMetrics fm, int maxWidth)
    {
        if (text == null)
        {
            return "";
        }

        if (fm.stringWidth(text) <= maxWidth)
        {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray())
        {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth)
            {
                break;
            }
            sb.append(c);
        }
        sb.append(ellipsis);
        return sb.toString();
    }

    public static List<String> wrapText(String text, FontMetrics fm, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        if (text == null)
        {
            return lines;
        }

        String cleaned = text.trim().replace("\r", "");
        if (cleaned.isEmpty())
        {
            return lines;
        }

        for (String paragraph : cleaned.split("\n"))
        {
            String p = paragraph.trim();
            if (p.isEmpty())
            {
                lines.add("");
                continue;
            }

            String[] words = p.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String w : words)
            {
                String candidate = (line.length() == 0) ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxWidth)
                {
                    line.setLength(0);
                    line.append(candidate);
                }
                else
                {
                    if (line.length() > 0)
                    {
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(w);
                    }
                    else
                    {
                        lines.add(truncateToWidth(w, fm, maxWidth));
                    }
                }
            }

            if (line.length() > 0)
            {
                lines.add(line.toString());
            }
        }

        return lines;
    }
}
