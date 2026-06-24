package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import net.runelite.api.Skill;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PrerequisiteIconRenderer
{
    private static final int ICON_SIZE = 17;
    private static final int ICON_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final Map<BufferedImage, BufferedImage> SIZED_ICON_CACHE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final BufferedImage QUEST_ICON = createQuestIconImage();
    private static final BufferedImage START_QUEST_ICON = createStartQuestIconImage();
    private static final BufferedImage ACHIEVEMENT_DIARY_ICON = createAchievementDiaryIconImage();
    private static final BufferedImage COMBAT_ICON = loadClasspathImage("/skill_icons/combat.png");
    private static final BufferedImage TOTAL_ICON = loadClasspathImage("/skill_icons/overall.png");
    private static final BufferedImage BULLET_ICON = createBulletIconImage();
    private static final BufferedImage BARBARIAN_MINIQUEST_ICON = createBarbarianMiniquestIconImage();
    private static final BufferedImage LAIR_OF_TARN_RAZORLOR_ICON = createLairOfTarnRazorlorIconImage();
    private static final BufferedImage MAGE_ARENA_1_ICON = createMageArena1IconImage();
    private static final BufferedImage ENTER_THE_ABYSS_ICON = createEnterTheAbyssIconImage();
    private static final BufferedImage VALE_TOTEMS_ICON = createValeTotemsIconImage();
    private static final BufferedImage ALFRED_GRIMHANDS_BARCRAWL_ICON = createAlfredGrimhandsBarcrawlIconImage();
    private static final BufferedImage WILDERNESS_ICON = createWildernessIconImage();
    private static final BufferedImage CURRENCY_ICON = createCurrencyIconImage();

    private PrerequisiteIconRenderer()
    {
    }

    public static BufferedImage achievementDiaryIconImage()
    {
        return ACHIEVEMENT_DIARY_ICON;
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
        if (markerIcon == null)
        {
            return null;
        }

        BufferedImage provided = markerImageProvider == null ? null : markerImageProvider.apply(markerIcon);
        if (provided != null)
        {
            return provided;
        }

        switch (markerIcon)
        {
            case QUEST:
                return QUEST_ICON;
            case START_QUEST:
                return START_QUEST_ICON;
            case ACHIEVEMENT_DIARY:
                return ACHIEVEMENT_DIARY_ICON;
            case COMBAT:
                return COMBAT_ICON;
            case TOTAL:
                return TOTAL_ICON;
            case BULLET:
                return BULLET_ICON;
            case BARBARIAN_MINIQUEST:
                return BARBARIAN_MINIQUEST_ICON;
            case LAIR_OF_TARN_RAZORLOR:
                return LAIR_OF_TARN_RAZORLOR_ICON;
            case MAGE_ARENA_1:
                return MAGE_ARENA_1_ICON;
            case ENTER_THE_ABYSS:
                return ENTER_THE_ABYSS_ICON;
            case VALE_TOTEMS:
                return VALE_TOTEMS_ICON;
            case ALFRED_GRIMHANDS_BARCRAWL:
                return ALFRED_GRIMHANDS_BARCRAWL_ICON;
            case WILDERNESS:
                return WILDERNESS_ICON;
            case CURRENCY:
                return CURRENCY_ICON;
            default:
                return null;
        }
    }

    private static BufferedImage sizedIcon(BufferedImage image)
    {
        if (image == null)
        {
            return null;
        }
        if (image.getWidth() == ICON_SIZE && image.getHeight() == ICON_SIZE)
        {
            return image;
        }

        BufferedImage cached = SIZED_ICON_CACHE.get(image);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage sized = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sized.createGraphics();
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, ICON_SIZE, ICON_SIZE, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation != null ? oldInterpolation : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.dispose();

        SIZED_ICON_CACHE.put(image, sized);
        return sized;
    }

    private static BufferedImage loadClasspathImage(String path)
    {
        try (InputStream in = PrerequisiteIconRenderer.class.getResourceAsStream(path))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static BufferedImage createQuestIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0, 31, 28));
        g.fillPolygon(new Polygon(
                new int[]{24, 40, 56, 64, 64, 56, 40, 24, 8, 0, 0, 8},
                new int[]{0, 0, 8, 24, 40, 56, 64, 64, 56, 40, 24, 8},
                12));

        g.setColor(new Color(196, 188, 188));
        g.fillPolygon(new Polygon(
                new int[]{25, 39, 53, 60, 60, 53, 39, 25, 11, 4, 4, 11},
                new int[]{4, 4, 11, 25, 39, 53, 60, 60, 53, 39, 25, 11},
                12));

        g.setColor(new Color(86, 124, 226));
        g.fillPolygon(new Polygon(
                new int[]{32, 48, 55, 48, 32, 16, 9, 16},
                new int[]{10, 16, 32, 48, 55, 48, 32, 16},
                8));

        g.setColor(new Color(63, 82, 157, 125));
        g.fillPolygon(new Polygon(
                new int[]{32, 43, 49, 43, 32, 21, 15, 21},
                new int[]{15, 21, 32, 43, 49, 43, 32, 21},
                8));

        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(72, 109, 213));
        g.drawLine(8, 32, 56, 32);
        g.drawLine(32, 6, 32, 58);

        g.setColor(new Color(104, 144, 190));
        g.drawLine(14, 14, 30, 30);
        g.drawLine(14, 50, 30, 34);

        g.setColor(new Color(58, 96, 168));
        g.drawLine(50, 14, 34, 30);
        g.drawLine(34, 34, 50, 50);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createStartQuestIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(QUEST_ICON, 0, 0, null);

        Polygon play = new Polygon(
                new int[]{24, 24, 48},
                new int[]{20, 44, 32},
                3);
        g.setColor(new Color(255, 255, 255, 210));
        g.fillPolygon(play);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(play);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createWildernessIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(92, 80, 45));
        g.fillPolygon(new Polygon(new int[]{28, 37, 34, 25}, new int[]{34, 35, 62, 62}, 4));

        g.setColor(new Color(74, 61, 34));
        g.fillPolygon(new Polygon(new int[]{28, 3, 16, 33}, new int[]{50, 62, 62, 43}, 4));
        g.fillPolygon(new Polygon(new int[]{35, 61, 49, 31}, new int[]{50, 62, 62, 43}, 4));

        g.setColor(new Color(112, 100, 57));
        g.fillRoundRect(7, 10, 50, 32, 3, 3);
        g.setColor(new Color(194, 188, 145));
        g.fillRoundRect(10, 12, 44, 27, 2, 2);

        g.setColor(new Color(132, 128, 100));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(16, 18, 26, 20);
        g.drawLine(26, 20, 37, 21);
        g.drawLine(37, 21, 47, 24);
        g.drawLine(15, 34, 31, 35);
        g.drawLine(31, 35, 48, 34);

        g.setColor(new Color(117, 110, 83));
        g.fillPolygon(new Polygon(new int[]{23, 32, 42, 16}, new int[]{31, 21, 32, 32}, 4));
        g.setColor(new Color(151, 143, 101));
        g.fillPolygon(new Polygon(new int[]{25, 32, 39, 19}, new int[]{29, 23, 30, 30}, 4));
        g.setColor(new Color(99, 94, 76));
        g.fillPolygon(new Polygon(new int[]{22, 26, 30}, new int[]{25, 22, 26}, 3));
        g.fillPolygon(new Polygon(new int[]{34, 38, 42}, new int[]{25, 22, 27}, 3));

        g.setColor(new Color(56, 47, 31, 80));
        g.fillPolygon(new Polygon(new int[]{35, 57, 57, 37}, new int[]{12, 12, 42, 38}, 4));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createCurrencyIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(113, 82, 0, 125));
        g.fillOval(5, 50, 52, 8);

        drawGoldStack(g, 9, 26, 13, 27, new Color(221, 174, 0), new Color(255, 224, 33));
        drawGoldStack(g, 23, 17, 14, 36, new Color(225, 179, 0), new Color(255, 225, 36));
        drawGoldStack(g, 37, 25, 13, 28, new Color(218, 169, 0), new Color(255, 218, 29));
        drawGoldStack(g, 50, 9, 12, 44, new Color(225, 180, 0), new Color(255, 229, 42));

        g.setColor(new Color(159, 119, 0));
        g.fillRoundRect(10, 46, 14, 7, 2, 2);
        g.setColor(new Color(247, 205, 12));
        g.fillRoundRect(12, 45, 14, 7, 2, 2);

        g.setColor(new Color(136, 97, 0));
        g.fillPolygon(new Polygon(new int[]{32, 43, 44, 31}, new int[]{51, 48, 54, 57}, 4));
        g.setColor(new Color(249, 208, 19));
        g.fillPolygon(new Polygon(new int[]{31, 42, 43, 30}, new int[]{48, 45, 51, 54}, 4));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static void drawGoldStack(Graphics2D g, int x, int y, int width, int height, Color shadow, Color highlight)
    {
        g.setColor(new Color(119, 84, 0));
        g.fillPolygon(new Polygon(
                new int[]{x, x + width - 2, x + width + 3, x + 4},
                new int[]{y + 4, y, y + height - 3, y + height + 2},
                4));
        g.setColor(shadow);
        g.fillPolygon(new Polygon(
                new int[]{x + 2, x + width - 2, x + width + 1, x + 5},
                new int[]{y + 6, y + 3, y + height - 5, y + height - 1},
                4));
        g.setColor(highlight);
        g.fillPolygon(new Polygon(
                new int[]{x + 3, x + width - 4, x + width - 2, x + 6},
                new int[]{y + 5, y + 3, y + 14, y + 16},
                4));
    }

    private static BufferedImage createAchievementDiaryIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0, 31, 28));
        g.fillPolygon(new Polygon(
                new int[]{24, 40, 56, 64, 64, 56, 40, 24, 8, 0, 0, 8},
                new int[]{0, 0, 8, 24, 40, 56, 64, 64, 56, 40, 24, 8},
                12));

        g.setColor(new Color(196, 188, 188));
        g.fillPolygon(new Polygon(
                new int[]{25, 39, 53, 60, 60, 53, 39, 25, 11, 4, 4, 11},
                new int[]{4, 4, 11, 25, 39, 53, 60, 60, 53, 39, 25, 11},
                12));

        g.setColor(new Color(41, 139, 54));
        g.fillPolygon(new Polygon(
                new int[]{32, 48, 55, 48, 32, 16, 9, 16},
                new int[]{10, 16, 32, 48, 55, 48, 32, 16},
                8));

        g.setColor(new Color(9, 100, 46, 125));
        g.fillPolygon(new Polygon(
                new int[]{32, 43, 49, 43, 32, 21, 15, 21},
                new int[]{15, 21, 32, 43, 49, 43, 32, 21},
                8));

        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(0, 112, 19));
        g.drawLine(8, 32, 56, 32);
        g.drawLine(32, 6, 32, 58);

        g.setColor(new Color(81, 171, 87));
        g.drawLine(14, 14, 30, 30);
        g.drawLine(14, 50, 30, 34);

        g.setColor(new Color(0, 91, 20));
        g.drawLine(50, 14, 34, 30);
        g.drawLine(34, 34, 50, 50);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createBarbarianMiniquestIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(31, 39, 39));
        g.fillRoundRect(15, 8, 37, 48, 4, 4);
        g.setColor(new Color(9, 14, 14));
        g.fillRoundRect(18, 11, 31, 42, 4, 4);
        g.setColor(new Color(54, 65, 63));
        g.fillPolygon(new Polygon(
                new int[]{21, 47, 43, 17},
                new int[]{13, 18, 49, 44},
                4));
        g.setColor(new Color(14, 20, 20));
        g.fillPolygon(new Polygon(
                new int[]{25, 47, 43, 21},
                new int[]{17, 21, 45, 41},
                4));
        g.setColor(new Color(83, 101, 93, 140));
        g.drawLine(25, 20, 42, 23);
        g.drawLine(24, 26, 41, 29);
        g.drawLine(23, 32, 39, 35);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createLairOfTarnRazorlorIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(22, 18, 12));
        g.fillPolygon(new Polygon(
                new int[]{17, 48, 55, 24},
                new int[]{15, 8, 42, 52},
                4));
        g.setColor(new Color(96, 76, 41));
        g.fillPolygon(new Polygon(
                new int[]{19, 46, 52, 24},
                new int[]{16, 11, 39, 49},
                4));
        g.setColor(new Color(142, 113, 62));
        g.fillPolygon(new Polygon(
                new int[]{24, 46, 49, 27},
                new int[]{16, 13, 35, 43},
                4));
        g.setColor(new Color(58, 43, 23));
        g.fillPolygon(new Polygon(
                new int[]{19, 24, 27, 24},
                new int[]{16, 16, 43, 49},
                4));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(190, 154, 88, 180));
        g.drawLine(30, 21, 43, 18);
        g.drawLine(31, 27, 45, 24);
        g.drawLine(32, 33, 45, 30);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createMageArena1IconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(9, 34, 20));
        g.fillPolygon(new Polygon(
                new int[]{34, 48, 31, 18, 24},
                new int[]{7, 57, 59, 46, 24},
                5));
        g.setColor(new Color(19, 141, 52));
        g.fillPolygon(new Polygon(
                new int[]{34, 45, 31, 21, 27},
                new int[]{10, 54, 56, 45, 25},
                5));
        g.setColor(new Color(45, 199, 76));
        g.fillPolygon(new Polygon(
                new int[]{34, 39, 30, 24, 29},
                new int[]{13, 50, 53, 44, 28},
                5));
        g.setColor(new Color(6, 69, 29, 180));
        g.fillPolygon(new Polygon(
                new int[]{34, 45, 39, 35},
                new int[]{10, 54, 56, 29},
                4));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createEnterTheAbyssIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(28, 38, 35));
        g.fillOval(10, 9, 45, 46);
        g.setColor(new Color(146, 165, 154));
        g.fillOval(13, 12, 39, 40);
        g.setColor(new Color(189, 207, 196));
        g.fillOval(17, 14, 28, 28);
        g.setColor(new Color(108, 128, 120, 170));
        g.fillOval(21, 22, 28, 27);
        g.setColor(new Color(225, 238, 230, 170));
        g.fillOval(21, 17, 10, 8);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createValeTotemsIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(20, 16, 11));
        g.drawLine(19, 53, 45, 18);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(134, 84, 27));
        g.drawLine(22, 50, 42, 23);
        g.setColor(new Color(218, 157, 53));
        g.fillPolygon(new Polygon(
                new int[]{40, 53, 44, 33},
                new int[]{7, 20, 26, 19},
                4));
        g.setColor(new Color(251, 203, 84));
        g.fillPolygon(new Polygon(
                new int[]{42, 50, 43, 36},
                new int[]{11, 19, 23, 19},
                4));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(238, 180, 64));
        g.drawLine(15, 45, 26, 54);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createAlfredGrimhandsBarcrawlIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(29, 28, 25));
        g.fillPolygon(new Polygon(
                new int[]{15, 45, 52, 22},
                new int[]{14, 10, 46, 55},
                4));
        g.setColor(new Color(221, 216, 202));
        g.fillPolygon(new Polygon(
                new int[]{17, 43, 49, 23},
                new int[]{16, 13, 43, 52},
                4));
        g.setColor(new Color(104, 99, 88));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(24, 23, 40, 20);
        g.drawLine(25, 29, 43, 26);
        g.drawLine(27, 35, 40, 33);
        g.drawLine(29, 41, 44, 38);
        g.setColor(new Color(56, 51, 44));
        g.fillOval(22, 22, 3, 3);
        g.fillOval(24, 28, 3, 3);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createBulletIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0, 0, 0, 145));
        g.fillOval(23, 25, 18, 18);
        g.setColor(new Color(224, 209, 155));
        g.fillOval(20, 22, 18, 18);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

}
