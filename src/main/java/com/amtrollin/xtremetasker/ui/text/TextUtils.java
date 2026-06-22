package com.amtrollin.xtremetasker.ui.text;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextUtils
{
    private static final int TRUNCATE_CACHE_LIMIT = 2048;
    private static final int WRAP_CACHE_LIMIT = 1024;
    private static final Map<String, String> TRUNCATE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(TRUNCATE_CACHE_LIMIT + 1, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest)
                {
                    return size() > TRUNCATE_CACHE_LIMIT;
                }
            });
    private static final Map<String, List<String>> WRAP_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, List<String>>(WRAP_CACHE_LIMIT + 1, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest)
                {
                    return size() > WRAP_CACHE_LIMIT;
                }
            });

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

        String cacheKey = textCacheKey(text, fm, maxWidth);
        String cached = TRUNCATE_CACHE.get(cacheKey);
        if (cached != null)
        {
            return cached;
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
        String truncated = sb.toString();
        TRUNCATE_CACHE.put(cacheKey, truncated);
        return truncated;
    }

    public static List<String> wrapText(String text, FontMetrics fm, int maxWidth)
    {
        if (text == null)
        {
            return Collections.emptyList();
        }

        String cleaned = text.trim().replace("\r", "");
        if (cleaned.isEmpty())
        {
            return Collections.emptyList();
        }

        String cacheKey = textCacheKey(cleaned, fm, maxWidth);
        List<String> cached = WRAP_CACHE.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        List<String> lines = new ArrayList<>();
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

        List<String> wrapped = Collections.unmodifiableList(new ArrayList<>(lines));
        WRAP_CACHE.put(cacheKey, wrapped);
        return wrapped;
    }

    private static String textCacheKey(String text, FontMetrics fm, int maxWidth)
    {
        String fontKey = fm == null || fm.getFont() == null
                ? ""
                : fm.getFont().getFontName() + "|" + fm.getFont().getStyle() + "|" + fm.getFont().getSize2D();
        return fontKey + "|" + maxWidth + "|" + text;
    }
}
