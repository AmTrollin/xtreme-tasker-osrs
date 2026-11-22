package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Skill;

public final class PrerequisiteIconRenderer
{
    private static final int ICON_SIZE = 17;
    private static final int ICON_GAP = 2;
    private static final int TEXT_GAP = 6;
    private static final Map<BufferedImage, BufferedImage> SIZED_ICON_CACHE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final BufferedImage BULLET_ICON = createBulletIconImage();

    private PrerequisiteIconRenderer()
    {
    }

    public static int markerWidth(
            FontMetrics fm,
            PrerequisiteStatus status,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        List<Skill> skills = status == null ? List.of() : status.getSkillIcons();
        List<MarkerIcon> markerIcons = status == null ? List.of() : status.getMarkerIcons();
        if ((markerIcons == null || markerIcons.isEmpty()) && (skills == null || skills.isEmpty() || imageProvider == null))
        {
            return fm.stringWidth("- ");
        }

        if (firstIconImage(markerIcons, skills, imageProvider, markerImageProvider) == null)
        {
            return fm.stringWidth("- ");
        }

        return ICON_SIZE + TEXT_GAP;
    }

    public static int markerWidth(FontMetrics fm, BufferedImage markerImage)
    {
        return markerImage == null ? fm.stringWidth("- ") : ICON_SIZE + TEXT_GAP;
    }

    public static int lineHeight(int defaultLineHeight, PrerequisiteStatus status)
    {
        boolean hasIcons = status != null
                && ((status.getMarkerIcons() != null && !status.getMarkerIcons().isEmpty())
                || (status.getSkillIcons() != null && !status.getSkillIcons().isEmpty()));
        return hasIcons ? Math.max(defaultLineHeight, ICON_SIZE + 2) : defaultLineHeight;
    }

    public static int textWidth(
            FontMetrics fm,
            int maxWidth,
            PrerequisiteStatus status,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        return Math.max(0, maxWidth - markerWidth(fm, status, imageProvider, markerImageProvider));
    }

    public static int textWidth(FontMetrics fm, int maxWidth, BufferedImage markerImage)
    {
        return Math.max(0, maxWidth - markerWidth(fm, markerImage));
    }

    public static int textX(
            FontMetrics fm,
            int x,
            PrerequisiteStatus status,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        return x + markerWidth(fm, status, imageProvider, markerImageProvider);
    }

    public static int textX(FontMetrics fm, int x, BufferedImage markerImage)
    {
        return x + markerWidth(fm, markerImage);
    }

    public static BufferedImage resolveMarkerImage(
            PrerequisiteStatus status,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        List<Skill> skills = status == null ? List.of() : status.getSkillIcons();
        List<MarkerIcon> markerIcons = status == null ? List.of() : status.getMarkerIcons();
        if ((markerIcons == null || markerIcons.isEmpty()) && (skills == null || skills.isEmpty() || imageProvider == null))
        {
            return null;
        }

        return firstIconImage(markerIcons, skills, imageProvider, markerImageProvider);
    }

    public static void drawMarker(
            Graphics2D g,
            FontMetrics fm,
            PrerequisiteStatus status,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider,
            int x,
            int baselineY
    )
    {
        List<Skill> skills = status == null ? List.of() : status.getSkillIcons();
        List<MarkerIcon> markerIcons = status == null ? List.of() : status.getMarkerIcons();
        if ((markerIcons == null || markerIcons.isEmpty()) && (skills == null || skills.isEmpty() || imageProvider == null))
        {
            g.drawString("-", x, baselineY);
            return;
        }

        BufferedImage image = firstIconImage(markerIcons, skills, imageProvider, markerImageProvider);
        if (image == null)
        {
            g.drawString("-", x, baselineY);
            return;
        }

        int iconY = baselineY - fm.getAscent() + (fm.getHeight() - ICON_SIZE) / 2;
        g.drawImage(sizedIcon(image), x, iconY, null);
    }

    public static void drawMarker(
            Graphics2D g,
            FontMetrics fm,
            BufferedImage markerImage,
            int x,
            int baselineY
    )
    {
        if (markerImage == null)
        {
            g.drawString("-", x, baselineY);
            return;
        }

        int iconY = baselineY - fm.getAscent() + (fm.getHeight() - ICON_SIZE) / 2;
        g.drawImage(sizedIcon(markerImage), x, iconY, null);
    }

    private static BufferedImage firstIconImage(
            List<MarkerIcon> markerIcons,
            List<Skill> skills,
            Function<Skill, BufferedImage> imageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        if (markerIcons != null)
        {
            for (MarkerIcon markerIcon : markerIcons)
            {
                BufferedImage markerImage = markerImage(markerIcon, markerImageProvider);
                if (markerImage != null)
                {
                    return markerImage;
                }
            }
        }

        if (skills != null && imageProvider != null)
        {
            for (Skill skill : skills)
            {
                if (skill == null)
                {
                    continue;
                }

                BufferedImage image = imageProvider.apply(skill);
                if (image != null)
                {
                    return image;
                }
            }
        }

        return null;
    }

    private static BufferedImage markerImage(MarkerIcon markerIcon, Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        if (markerIcon == MarkerIcon.BULLET)
        {
            return BULLET_ICON;
        }
        return markerIcon == null || markerImageProvider == null ? null : markerImageProvider.apply(markerIcon);
    }

    private static BufferedImage sizedIcon(BufferedImage image)
    {
        if (image == null)
        {
            return null;
        }
        if (image == BULLET_ICON)
        {
            return image;
        }

        BufferedImage cached = SIZED_ICON_CACHE.get(image);
        if (cached != null)
        {
            return cached;
        }

        // Sprite canvases contain different amounts of transparent padding. Normalize the
        // visible artwork, then right-align it in the shared slot so every text gap matches.
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        BufferedImage sized = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        if (maxX >= minX && maxY >= minY)
        {
            int visibleWidth = maxX - minX + 1;
            int visibleHeight = maxY - minY + 1;
            double scale = Math.min((double) ICON_SIZE / visibleWidth, (double) ICON_SIZE / visibleHeight);
            int width = Math.max(1, (int) Math.round(visibleWidth * scale));
            int height = Math.max(1, (int) Math.round(visibleHeight * scale));
            int left = ICON_SIZE - width;
            int top = (ICON_SIZE - height) / 2;
            Graphics2D g = sized.createGraphics();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, left, top, left + width, top + height,
                        minX, minY, maxX + 1, maxY + 1, null);
            }
            finally { g.dispose(); }
        }

        SIZED_ICON_CACHE.put(image, sized);
        return sized;
    }

    private static BufferedImage createBulletIconImage()
    {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 125));
        g.fillOval(5, 6, 8, 8);
        g.setColor(new Color(224, 209, 155));
        g.fillOval(4, 5, 8, 8);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }
}
