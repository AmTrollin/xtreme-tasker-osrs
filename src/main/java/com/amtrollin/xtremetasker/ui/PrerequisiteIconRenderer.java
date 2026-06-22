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
    private static final BufferedImage FAVOUR_ICON = createFavourIconImage();
    private static final BufferedImage BARBARIAN_MINIQUEST_ICON = createBarbarianMiniquestIconImage();
    private static final BufferedImage LAIR_OF_TARN_RAZORLOR_ICON = createLairOfTarnRazorlorIconImage();
    private static final BufferedImage MAGE_ARENA_1_ICON = createMageArena1IconImage();
    private static final BufferedImage ENTER_THE_ABYSS_ICON = createEnterTheAbyssIconImage();
    private static final BufferedImage VALE_TOTEMS_ICON = createValeTotemsIconImage();
    private static final BufferedImage ALFRED_GRIMHANDS_BARCRAWL_ICON = createAlfredGrimhandsBarcrawlIconImage();
    private static final BufferedImage JAGEX_ACCOUNT_ICON = createJagexAccountIconImage();
    private static final BufferedImage THERMONUCLEAR_SMOKE_DEVIL_ICON = createThermonuclearSmokeDevilIconImage();
    private static final BufferedImage RAID_WIKI_ICON = createRaidWikiIconImage();
    private static final BufferedImage GIANT_MOLE_ICON = createGiantMoleIconImage();
    private static final BufferedImage PROSPECTOR_HELMET_ICON = createProspectorHelmetIconImage();
    private static final BufferedImage LIZARDMAN_SHAMAN_ICON = createLizardmanShamanIconImage();
    private static final BufferedImage BONES_TO_PEACHES_ICON = createBonesToPeachesIconImage();
    private static final BufferedImage ZULRAH_ICON = createZulrahIconImage();
    private static final BufferedImage CHAOS_ELEMENTAL_ICON = createChaosElementalIconImage();
    private static final BufferedImage CHOMPY_BIRD_ICON = createChompyBirdIconImage();
    private static final BufferedImage KALPHITE_QUEEN_ICON = createKalphiteQueenIconImage();
    private static final BufferedImage PENANCE_QUEEN_ICON = createPenanceQueenIconImage();
    private static final BufferedImage PYRAMID_PLUNDER_ICON = createPyramidPlunderIconImage();
    private static final BufferedImage SKOTIZO_ICON = createSkotizoIconImage();
    private static final BufferedImage HYDRA_ICON = createHydraIconImage();
    private static final BufferedImage WHITE_KNIGHT_ICON = createWhiteKnightIconImage();
    private static final BufferedImage KET_ZEK_ICON = createKetZekIconImage();
    private static final BufferedImage TZHAAR_FIGHT_CAVE_ICON = createTzhaarFightCaveIconImage();
    private static final BufferedImage BARROWS_CHEST_ICON = createBarrowsChestIconImage();
    private static final BufferedImage CHAMBERS_OF_XERIC_ICON = createChambersOfXericIconImage();
    private static final BufferedImage CRAZY_ARCHAEOLOGIST_ICON = createCrazyArchaeologistIconImage();
    private static final BufferedImage CHAOS_FANATIC_ICON = createChaosFanaticIconImage();
    private static final BufferedImage SCORPIA_ICON = createScorpiaIconImage();
    private static final BufferedImage SARADOMIN_STRIKE_ICON = createGodSpellIconImage(new Color(47, 202, 255));
    private static final BufferedImage CLAWS_OF_GUTHIX_ICON = createGodSpellIconImage(new Color(42, 225, 64));
    private static final BufferedImage FLAMES_OF_ZAMORAK_ICON = createGodSpellIconImage(new Color(244, 25, 14));
    private static final BufferedImage DAGANNOTH_REX_ICON = createDagannothIconImage(new Color(111, 116, 92), new Color(154, 161, 66));
    private static final BufferedImage DAGANNOTH_SUPREME_ICON = createDagannothIconImage(new Color(96, 107, 96), new Color(166, 66, 45));
    private static final BufferedImage DAGANNOTH_PRIME_ICON = createDagannothIconImage(new Color(96, 112, 93), new Color(9, 65, 45));
    private static final BufferedImage CALLISTO_ICON = createCallistoIconImage();
    private static final BufferedImage VENENATIS_ICON = createVenenatisIconImage();
    private static final BufferedImage VETION_ICON = createVetionIconImage();
    private static final BufferedImage KRIL_TSUTSAROTH_ICON = createKrilTsutsarothIconImage();
    private static final BufferedImage KREEARRA_ICON = createKreearraIconImage();
    private static final BufferedImage COMMANDER_ZILYANA_ICON = createCommanderZilyanaIconImage();
    private static final BufferedImage GENERAL_GRAARDOR_ICON = createGeneralGraardorIconImage();
    private static final BufferedImage VOID_TOP_ICON = createVoidTopIconImage();
    private static final BufferedImage VOID_ROBE_ICON = createVoidRobeIconImage();
    private static final BufferedImage VOID_GLOVES_ICON = createVoidGlovesIconImage();
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
            case FAVOUR:
                return FAVOUR_ICON;
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
            case JAGEX_ACCOUNT:
                return JAGEX_ACCOUNT_ICON;
            case THERMONUCLEAR_SMOKE_DEVIL:
                return THERMONUCLEAR_SMOKE_DEVIL_ICON;
            case RAID_WIKI:
                return RAID_WIKI_ICON;
            case GIANT_MOLE:
                return GIANT_MOLE_ICON;
            case PROSPECTOR_HELMET:
                return PROSPECTOR_HELMET_ICON;
            case LIZARDMAN_SHAMAN:
                return LIZARDMAN_SHAMAN_ICON;
            case BONES_TO_PEACHES:
                return BONES_TO_PEACHES_ICON;
            case ZULRAH:
                return ZULRAH_ICON;
            case CHAOS_ELEMENTAL:
                return CHAOS_ELEMENTAL_ICON;
            case CHOMPY_BIRD:
                return CHOMPY_BIRD_ICON;
            case KALPHITE_QUEEN:
                return KALPHITE_QUEEN_ICON;
            case PENANCE_QUEEN:
                return PENANCE_QUEEN_ICON;
            case PYRAMID_PLUNDER:
                return PYRAMID_PLUNDER_ICON;
            case SKOTIZO:
                return SKOTIZO_ICON;
            case HYDRA:
                return HYDRA_ICON;
            case WHITE_KNIGHT:
                return WHITE_KNIGHT_ICON;
            case KET_ZEK:
                return KET_ZEK_ICON;
            case TZHAAR_FIGHT_CAVE:
                return TZHAAR_FIGHT_CAVE_ICON;
            case BARROWS_CHEST:
                return BARROWS_CHEST_ICON;
            case CHAMBERS_OF_XERIC:
                return CHAMBERS_OF_XERIC_ICON;
            case CRAZY_ARCHAEOLOGIST:
                return CRAZY_ARCHAEOLOGIST_ICON;
            case CHAOS_FANATIC:
                return CHAOS_FANATIC_ICON;
            case SCORPIA:
                return SCORPIA_ICON;
            case SARADOMIN_STRIKE:
                return SARADOMIN_STRIKE_ICON;
            case CLAWS_OF_GUTHIX:
                return CLAWS_OF_GUTHIX_ICON;
            case FLAMES_OF_ZAMORAK:
                return FLAMES_OF_ZAMORAK_ICON;
            case DAGANNOTH_REX:
                return DAGANNOTH_REX_ICON;
            case DAGANNOTH_SUPREME:
                return DAGANNOTH_SUPREME_ICON;
            case DAGANNOTH_PRIME:
                return DAGANNOTH_PRIME_ICON;
            case CALLISTO:
                return CALLISTO_ICON;
            case VENENATIS:
                return VENENATIS_ICON;
            case VETION:
                return VETION_ICON;
            case KRIL_TSUTSAROTH:
                return KRIL_TSUTSAROTH_ICON;
            case KREEARRA:
                return KREEARRA_ICON;
            case COMMANDER_ZILYANA:
                return COMMANDER_ZILYANA_ICON;
            case GENERAL_GRAARDOR:
                return GENERAL_GRAARDOR_ICON;
            case VOID_TOP:
                return VOID_TOP_ICON;
            case VOID_ROBE:
                return VOID_ROBE_ICON;
            case VOID_GLOVES:
                return VOID_GLOVES_ICON;
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

    private static BufferedImage createTzhaarFightCaveIconImage()
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

        g.setColor(new Color(208, 96, 90));
        g.fillPolygon(new Polygon(
                new int[]{32, 48, 55, 48, 32, 16, 9, 16},
                new int[]{10, 16, 32, 48, 55, 48, 32, 16},
                8));

        g.setColor(new Color(151, 69, 65, 125));
        g.fillPolygon(new Polygon(
                new int[]{32, 43, 49, 43, 32, 21, 15, 21},
                new int[]{15, 21, 32, 43, 49, 43, 32, 21},
                8));

        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(197, 72, 65));
        g.drawLine(8, 32, 56, 32);
        g.drawLine(32, 6, 32, 58);

        g.setColor(new Color(198, 116, 112));
        g.drawLine(14, 14, 30, 30);
        g.drawLine(14, 50, 30, 34);

        g.setColor(new Color(174, 58, 52));
        g.drawLine(50, 14, 34, 30);
        g.drawLine(34, 34, 50, 50);

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

    private static BufferedImage createJagexAccountIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(238, 121, 0));
        g.fillPolygon(new Polygon(
                new int[]{10, 27, 34, 26, 35, 28, 10, 18, 29, 22},
                new int[]{7, 7, 23, 23, 38, 56, 56, 39, 31, 23},
                10));
        g.fillPolygon(new Polygon(
                new int[]{54, 37, 30, 38, 29, 36, 54, 46, 35, 42},
                new int[]{7, 7, 23, 23, 38, 56, 56, 39, 31, 23},
                10));
        g.fillPolygon(new Polygon(
                new int[]{31, 40, 33, 24},
                new int[]{7, 7, 56, 56},
                4));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createThermonuclearSmokeDevilIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(37, 45, 43));
        g.fillOval(14, 11, 37, 36);
        g.setColor(new Color(82, 95, 91));
        g.fillOval(19, 15, 29, 27);
        g.setColor(new Color(23, 25, 25));
        g.fillPolygon(new Polygon(new int[]{27, 33, 29}, new int[]{26, 24, 31}, 3));
        g.fillPolygon(new Polygon(new int[]{43, 37, 40}, new int[]{26, 24, 31}, 3));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(28, 31, 30, 170));
        g.drawLine(10, 28, 24, 25);
        g.drawLine(9, 34, 24, 31);
        g.drawLine(8, 40, 23, 37);
        g.setColor(new Color(213, 170, 121));
        g.fillOval(15, 30, 12, 11);
        g.setColor(new Color(210, 214, 204));
        g.fillPolygon(new Polygon(new int[]{6, 28, 21}, new int[]{43, 38, 50}, 3));
        g.fillPolygon(new Polygon(new int[]{27, 38, 31}, new int[]{39, 36, 49}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createRaidWikiIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(24, 22, 21));
        g.fillPolygon(new Polygon(
                new int[]{24, 40, 56, 64, 64, 56, 40, 24, 8, 0, 0, 8},
                new int[]{0, 0, 8, 24, 40, 56, 64, 64, 56, 40, 24, 8},
                12));
        g.setColor(new Color(198, 190, 190));
        g.fillPolygon(new Polygon(
                new int[]{25, 39, 53, 60, 60, 53, 39, 25, 11, 4, 4, 11},
                new int[]{4, 4, 11, 25, 39, 53, 60, 60, 53, 39, 25, 11},
                12));
        g.setColor(new Color(136, 92, 39));
        g.fillPolygon(new Polygon(new int[]{32, 48, 55, 48, 32, 16, 9, 16}, new int[]{10, 16, 32, 48, 55, 48, 32, 16}, 8));
        g.setColor(new Color(89, 57, 28, 120));
        g.fillPolygon(new Polygon(new int[]{32, 43, 49, 43, 32, 21, 15, 21}, new int[]{15, 21, 32, 43, 49, 43, 32, 21}, 8));
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(86, 50, 22));
        g.drawLine(8, 32, 56, 32);
        g.drawLine(32, 6, 32, 58);
        g.setColor(new Color(180, 109, 43));
        g.drawLine(14, 14, 30, 30);
        g.drawLine(14, 50, 30, 34);
        g.setColor(new Color(70, 38, 18));
        g.drawLine(50, 14, 34, 30);
        g.drawLine(34, 34, 50, 50);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createGiantMoleIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(55, 49, 39));
        g.fillOval(18, 12, 41, 36);
        g.setColor(new Color(76, 70, 58));
        g.fillOval(7, 18, 31, 27);
        g.setColor(new Color(106, 95, 78, 170));
        g.fillOval(17, 20, 18, 16);
        g.setColor(new Color(19, 17, 16));
        g.fillPolygon(new Polygon(new int[]{22, 29, 25}, new int[]{28, 27, 34}, 3));
        g.setColor(new Color(224, 174, 126));
        g.fillOval(7, 34, 11, 9);
        g.setColor(new Color(220, 222, 215));
        g.fillPolygon(new Polygon(new int[]{0, 19, 11}, new int[]{47, 40, 55}, 3));
        g.fillPolygon(new Polygon(new int[]{18, 32, 24}, new int[]{42, 39, 56}, 3));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(18, 16, 15, 150));
        g.drawLine(3, 32, 15, 31);
        g.drawLine(2, 37, 15, 35);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createProspectorHelmetIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(61, 56, 18));
        g.fillPolygon(new Polygon(new int[]{11, 32, 52, 58, 46, 18}, new int[]{27, 15, 19, 43, 54, 49}, 6));
        g.setColor(new Color(178, 178, 25));
        g.fillPolygon(new Polygon(new int[]{13, 32, 50, 55, 45, 19}, new int[]{28, 18, 22, 42, 50, 46}, 6));
        g.setColor(new Color(60, 53, 45));
        g.fillRoundRect(5, 24, 17, 18, 6, 6);
        g.fillRoundRect(42, 22, 18, 18, 6, 6);
        g.fillRoundRect(25, 28, 17, 17, 6, 6);
        g.setColor(new Color(226, 232, 219));
        g.fillOval(11, 29, 8, 8);
        g.fillOval(30, 32, 8, 8);
        g.fillOval(47, 27, 9, 9);
        g.setColor(new Color(31, 26, 28));
        g.fillPolygon(new Polygon(new int[]{25, 37, 34, 28}, new int[]{17, 18, 28, 28}, 4));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createLizardmanShamanIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(74, 87, 40));
        g.fillOval(14, 10, 35, 45);
        g.setColor(new Color(121, 132, 72));
        g.fillOval(18, 20, 28, 31);
        g.setColor(new Color(204, 201, 140));
        g.fillOval(23, 30, 19, 21);
        g.setColor(new Color(44, 46, 39));
        g.fillPolygon(new Polygon(new int[]{26, 38, 43, 35, 28}, new int[]{5, 5, 18, 16, 18}, 5));
        g.setColor(new Color(128, 41, 160));
        g.fillPolygon(new Polygon(new int[]{23, 30, 27}, new int[]{21, 20, 27}, 3));
        g.fillPolygon(new Polygon(new int[]{39, 32, 35}, new int[]{21, 20, 27}, 3));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(91, 105, 50));
        g.drawLine(17, 36, 8, 52);
        g.drawLine(47, 36, 56, 52);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createBonesToPeachesIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.drawLine(11, 12, 34, 35);
        g.drawLine(34, 12, 11, 35);
        g.setColor(new Color(42, 44, 44));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(11, 12, 34, 35);
        g.drawLine(34, 12, 11, 35);
        g.setColor(new Color(227, 104, 28));
        g.fillOval(34, 31, 22, 22);
        g.setColor(new Color(247, 157, 62));
        g.fillOval(37, 33, 13, 13);
        g.setColor(new Color(72, 103, 42));
        g.fillPolygon(new Polygon(new int[]{45, 54, 48}, new int[]{29, 31, 35}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createZulrahIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(104, 128, 20));
        g.drawArc(10, 6, 46, 51, 235, 245);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(163, 191, 29));
        g.drawArc(14, 9, 38, 44, 235, 245);
        g.setColor(new Color(92, 109, 31));
        g.fillPolygon(new Polygon(new int[]{23, 43, 48, 33}, new int[]{22, 15, 29, 33}, 4));
        g.setColor(new Color(235, 235, 220));
        g.fillPolygon(new Polygon(new int[]{26, 35, 29}, new int[]{19, 15, 25}, 3));
        g.fillPolygon(new Polygon(new int[]{34, 43, 37}, new int[]{18, 22, 27}, 3));
        g.setColor(new Color(155, 19, 24));
        g.fillPolygon(new Polygon(new int[]{32, 41, 33}, new int[]{29, 28, 35}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createChaosElementalIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(92, 83, 119));
        g.fillOval(11, 22, 42, 27);
        g.setColor(new Color(121, 109, 151));
        g.fillOval(18, 17, 28, 22);
        g.setColor(new Color(68, 57, 98));
        g.fillOval(30, 27, 25, 20);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(83, 45, 125));
        g.drawArc(4, 6, 38, 42, 95, 70);
        g.drawArc(25, 3, 35, 41, 20, 72);
        g.setColor(new Color(203, 126, 108));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(31, 20, 38, 9);
        g.drawLine(38, 10, 45, 19);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createChompyBirdIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(184, 184, 112));
        g.fillOval(21, 24, 34, 24);
        g.setColor(new Color(150, 151, 88));
        g.fillOval(15, 13, 22, 24);
        g.setColor(new Color(222, 165, 52));
        g.fillPolygon(new Polygon(new int[]{12, 0, 13}, new int[]{24, 35, 38}, 3));
        g.setColor(new Color(235, 179, 56));
        g.fillPolygon(new Polygon(new int[]{12, 0, 14}, new int[]{20, 27, 32}, 3));
        g.setColor(new Color(36, 26, 25));
        g.fillRect(23, 20, 5, 9);
        g.setColor(new Color(94, 130, 99));
        g.fillPolygon(new Polygon(new int[]{39, 56, 62, 51}, new int[]{31, 23, 45, 42}, 4));
        g.setColor(new Color(72, 105, 88));
        g.fillPolygon(new Polygon(new int[]{51, 63, 54}, new int[]{26, 17, 41}, 3));
        g.setColor(new Color(109, 87, 46));
        g.fillPolygon(new Polygon(new int[]{33, 42, 37}, new int[]{46, 58, 47}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createKalphiteQueenIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(92, 111, 45));
        g.fillOval(14, 13, 38, 37);
        g.setColor(new Color(132, 155, 69));
        g.fillOval(19, 18, 26, 26);
        g.setColor(new Color(159, 70, 43));
        g.fillPolygon(new Polygon(new int[]{25, 39, 44, 32}, new int[]{17, 16, 29, 31}, 4));
        g.setColor(new Color(206, 209, 115));
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(28, 14, 17, 5);
        g.drawLine(39, 15, 50, 5);
        g.drawLine(17, 31, 3, 22);
        g.drawLine(47, 31, 61, 22);
        g.setColor(new Color(54, 67, 33));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(17, 40, 7, 55);
        g.drawLine(47, 40, 57, 55);
        g.setColor(new Color(80, 51, 75));
        g.fillPolygon(new Polygon(new int[]{24, 28, 23}, new int[]{9, 18, 18}, 3));
        g.fillPolygon(new Polygon(new int[]{43, 38, 43}, new int[]{9, 18, 18}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createPenanceQueenIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(85, 98, 81));
        g.fillOval(20, 15, 34, 31);
        g.setColor(new Color(123, 137, 109));
        g.fillOval(12, 12, 21, 31);
        g.setColor(new Color(190, 177, 124));
        g.fillOval(8, 7, 18, 24);
        g.setColor(new Color(151, 37, 28));
        g.fillOval(13, 7, 8, 8);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(68, 81, 68));
        g.drawLine(19, 34, 8, 50);
        g.drawLine(35, 41, 26, 56);
        g.drawLine(49, 38, 60, 51);
        g.setColor(new Color(210, 176, 37));
        g.drawLine(8, 50, 5, 62);
        g.drawLine(26, 56, 24, 63);
        g.drawLine(60, 51, 62, 63);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createPyramidPlunderIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(126, 120, 0));
        g.fillPolygon(new Polygon(new int[]{9, 48, 58, 19}, new int[]{18, 9, 43, 54}, 4));
        g.setColor(new Color(211, 199, 10));
        g.fillPolygon(new Polygon(new int[]{11, 46, 54, 20}, new int[]{20, 12, 40, 50}, 4));
        g.setColor(new Color(236, 224, 28));
        g.fillPolygon(new Polygon(new int[]{16, 44, 49, 23}, new int[]{20, 15, 31, 36}, 4));
        g.setColor(new Color(180, 171, 10));
        g.fillPolygon(new Polygon(new int[]{16, 49, 54, 21}, new int[]{37, 32, 40, 50}, 4));
        g.setColor(new Color(151, 143, 7));
        g.fillPolygon(new Polygon(new int[]{23, 49, 47, 25}, new int[]{38, 33, 44, 48}, 4));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(185, 190, 200));
        g.drawLine(10, 43, 0, 50);
        g.drawLine(52, 31, 64, 24);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createSkotizoIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(30, 28, 31));
        g.fillOval(15, 16, 38, 40);
        g.setColor(new Color(63, 57, 66));
        g.fillOval(22, 19, 26, 28);
        g.setColor(new Color(88, 20, 134));
        g.fillPolygon(new Polygon(new int[]{28, 38, 46, 34}, new int[]{20, 18, 35, 41}, 4));
        g.setColor(new Color(116, 44, 171));
        g.fillPolygon(new Polygon(new int[]{29, 37, 42, 33}, new int[]{25, 23, 34, 37}, 4));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(50, 46, 51));
        g.drawLine(22, 18, 8, 3);
        g.drawLine(39, 18, 55, 3);
        g.drawLine(21, 34, 5, 52);
        g.drawLine(45, 35, 60, 51);
        g.setColor(new Color(40, 35, 39));
        g.drawLine(28, 51, 24, 62);
        g.drawLine(43, 50, 48, 62);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createHydraIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(37, 81, 83));
        g.drawLine(9, 41, 27, 29);
        g.drawLine(24, 36, 42, 21);
        g.drawLine(35, 42, 56, 29);
        g.setColor(new Color(86, 141, 126));
        g.drawLine(11, 38, 27, 28);
        g.drawLine(27, 34, 42, 21);
        g.drawLine(37, 39, 54, 29);
        g.setColor(new Color(22, 55, 63));
        g.fillPolygon(new Polygon(new int[]{24, 38, 33, 19}, new int[]{28, 30, 44, 42}, 4));
        g.setColor(new Color(83, 139, 121));
        g.fillPolygon(new Polygon(new int[]{26, 36, 32, 22}, new int[]{30, 32, 40, 39}, 4));
        g.setColor(new Color(141, 205, 200));
        g.fillPolygon(new Polygon(new int[]{9, 22, 18}, new int[]{34, 30, 42}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createWhiteKnightIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(222, 222, 218));
        g.fillOval(25, 8, 17, 18);
        g.fillPolygon(new Polygon(new int[]{21, 43, 48, 17}, new int[]{25, 25, 51, 53}, 4));
        g.setColor(new Color(244, 244, 241));
        g.fillPolygon(new Polygon(new int[]{24, 39, 42, 21}, new int[]{28, 28, 48, 49}, 4));
        g.setColor(new Color(22, 24, 28));
        g.fillRect(26, 17, 17, 4);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(207, 207, 204));
        g.drawLine(18, 34, 9, 51);
        g.drawLine(47, 34, 55, 51);
        g.setColor(new Color(220, 221, 218));
        g.fillOval(20, 53, 11, 7);
        g.fillOval(43, 53, 11, 7);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(205, 205, 207));
        g.drawLine(9, 7, 9, 50);
        g.setColor(new Color(245, 218, 51));
        g.fillOval(5, 47, 8, 8);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createKetZekIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(118, 10, 34));
        g.fillOval(9, 16, 44, 29);
        g.setColor(new Color(87, 8, 27));
        g.fillPolygon(new Polygon(new int[]{11, 41, 56, 29}, new int[]{34, 34, 47, 50}, 4));
        g.setColor(new Color(75, 9, 25));
        g.fillRect(15, 41, 10, 16);
        g.fillRect(42, 39, 11, 17);
        g.setColor(new Color(248, 211, 40));
        g.fillPolygon(new Polygon(new int[]{29, 37, 32}, new int[]{27, 25, 34}, 3));
        g.setColor(new Color(70, 5, 18));
        g.fillPolygon(new Polygon(new int[]{4, 19, 11}, new int[]{19, 20, 29}, 3));
        g.fillPolygon(new Polygon(new int[]{44, 62, 52}, new int[]{14, 22, 28}, 3));
        g.setColor(new Color(246, 230, 39));
        g.fillOval(53, 38, 9, 14);
        g.setColor(new Color(40, 23, 68));
        g.fillOval(18, 55, 8, 5);
        g.fillOval(44, 54, 9, 5);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createBarrowsChestIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(38, 40, 25));
        g.fillPolygon(new Polygon(new int[]{6, 56, 61, 12}, new int[]{24, 21, 50, 56}, 4));
        g.setColor(new Color(86, 88, 61));
        g.fillPolygon(new Polygon(new int[]{10, 52, 56, 15}, new int[]{26, 24, 47, 52}, 4));
        g.setColor(new Color(55, 57, 35));
        g.fillPolygon(new Polygon(new int[]{6, 17, 58, 48}, new int[]{23, 7, 12, 24}, 4));
        g.setColor(new Color(71, 73, 47));
        g.fillPolygon(new Polygon(new int[]{12, 19, 52, 47}, new int[]{23, 12, 16, 24}, 4));
        g.setColor(new Color(102, 103, 78));
        g.fillPolygon(new Polygon(new int[]{3, 12, 15, 6}, new int[]{23, 7, 53, 60}, 4));
        g.fillPolygon(new Polygon(new int[]{51, 59, 61, 56}, new int[]{13, 22, 50, 47}, 4));
        g.setColor(new Color(46, 49, 29));
        g.fillRect(22, 31, 8, 12);
        g.fillRect(40, 29, 8, 13);
        g.setColor(new Color(25, 27, 17));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(11, 29, 55, 27);
        g.drawLine(14, 52, 55, 47);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createChambersOfXericIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(20, 22, 21));
        g.fillPolygon(new Polygon(new int[]{9, 55, 62, 54, 10, 2}, new int[]{20, 20, 32, 45, 45, 32}, 6));
        g.setColor(new Color(67, 112, 70));
        g.fillOval(13, 5, 38, 54);
        g.setColor(new Color(24, 25, 24));
        g.fillPolygon(new Polygon(new int[]{8, 56, 60, 52, 12, 4}, new int[]{22, 22, 32, 42, 42, 32}, 6));
        g.setColor(new Color(54, 57, 55));
        g.fillPolygon(new Polygon(new int[]{10, 54, 57, 50, 14, 7}, new int[]{24, 24, 32, 39, 39, 32}, 6));
        g.setColor(new Color(163, 111, 54));
        g.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD, 26));
        g.drawString("X", 23, 41);
        g.setColor(new Color(116, 155, 104));
        g.fillPolygon(new Polygon(new int[]{32, 21, 43}, new int[]{8, 22, 22}, 3));
        g.fillPolygon(new Polygon(new int[]{32, 20, 44}, new int[]{56, 44, 44}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createCrazyArchaeologistIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(82, 67, 25));
        g.fillPolygon(new Polygon(new int[]{12, 43, 58, 29}, new int[]{14, 4, 17, 27}, 4));
        g.setColor(new Color(120, 99, 42));
        g.fillPolygon(new Polygon(new int[]{5, 40, 52, 21}, new int[]{24, 10, 20, 35}, 4));
        g.setColor(new Color(116, 75, 44));
        g.fillOval(23, 26, 23, 22);
        g.setColor(new Color(64, 43, 25));
        g.fillPolygon(new Polygon(new int[]{7, 23, 19}, new int[]{30, 40, 57}, 3));
        g.fillPolygon(new Polygon(new int[]{37, 55, 43}, new int[]{39, 47, 57}, 3));
        g.setColor(new Color(224, 170, 119));
        g.fillOval(29, 29, 8, 7);
        g.setColor(new Color(235, 229, 207));
        g.fillOval(31, 35, 5, 4);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createChaosFanaticIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(231, 226, 212));
        g.fillOval(28, 4, 17, 18);
        g.fillPolygon(new Polygon(new int[]{23, 45, 51, 19}, new int[]{20, 21, 51, 54}, 4));
        g.setColor(new Color(247, 247, 240));
        g.fillPolygon(new Polygon(new int[]{25, 42, 45, 23}, new int[]{23, 24, 47, 49}, 4));
        g.setColor(new Color(54, 26, 78));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(14, 24, 5, 45);
        g.drawLine(14, 24, 29, 35);
        g.drawLine(5, 45, 24, 44);
        g.setColor(new Color(70, 7, 82));
        g.fillPolygon(new Polygon(new int[]{13, 25, 18}, new int[]{53, 52, 62}, 3));
        g.fillPolygon(new Polygon(new int[]{43, 56, 50}, new int[]{50, 49, 61}, 3));
        g.setColor(new Color(28, 22, 19));
        g.fillRect(30, 15, 17, 4);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createScorpiaIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(72, 67, 69));
        g.fillOval(18, 25, 32, 20);
        g.setColor(new Color(105, 98, 101));
        g.fillOval(25, 21, 22, 17);
        g.setColor(new Color(100, 22, 16));
        g.fillPolygon(new Polygon(new int[]{24, 44, 40, 27}, new int[]{32, 30, 42, 42}, 4));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(76, 70, 72));
        g.drawLine(28, 25, 36, 12);
        g.drawLine(36, 12, 46, 4);
        g.drawLine(20, 34, 4, 28);
        g.drawLine(48, 34, 61, 29);
        g.drawLine(21, 42, 6, 54);
        g.drawLine(48, 42, 60, 55);
        g.setColor(new Color(205, 205, 198));
        g.fillPolygon(new Polygon(new int[]{22, 30, 25}, new int[]{44, 44, 53}, 3));
        g.fillPolygon(new Polygon(new int[]{39, 47, 43}, new int[]{43, 42, 52}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createGodSpellIconImage(Color color)
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(12, 14, 16, 175));
        g.drawArc(10, 12, 32, 16, 205, 155);
        g.drawArc(21, 24, 31, 16, 205, 155);
        g.drawArc(10, 36, 32, 16, 205, 155);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        g.drawArc(10, 12, 32, 16, 205, 155);
        g.drawArc(21, 24, 31, 16, 205, 155);
        g.drawArc(10, 36, 32, 16, 205, 155);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createDagannothIconImage(Color body, Color accent)
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(body.darker());
        g.fillOval(16, 12, 32, 43);
        g.setColor(body);
        g.fillOval(19, 16, 26, 34);
        g.setColor(accent);
        g.fillPolygon(new Polygon(new int[]{30, 44, 40, 26}, new int[]{22, 28, 39, 36}, 4));
        g.setColor(body.darker().darker());
        g.fillPolygon(new Polygon(new int[]{21, 31, 25}, new int[]{14, 4, 24}, 3));
        g.fillPolygon(new Polygon(new int[]{38, 50, 39}, new int[]{15, 6, 26}, 3));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(body.darker());
        g.drawLine(19, 38, 5, 50);
        g.drawLine(45, 38, 59, 50);
        g.drawLine(26, 48, 20, 61);
        g.drawLine(39, 48, 45, 61);
        g.setColor(new Color(220, 132, 112));
        g.fillPolygon(new Polygon(new int[]{18, 28, 21}, new int[]{43, 40, 51}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createCallistoIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(69, 66, 66));
        g.fillOval(12, 18, 42, 28);
        g.setColor(new Color(92, 88, 88));
        g.fillOval(7, 21, 24, 20);
        g.setColor(new Color(238, 238, 226));
        g.fillPolygon(new Polygon(new int[]{20, 29, 25}, new int[]{28, 28, 44}, 3));
        g.fillPolygon(new Polygon(new int[]{30, 39, 35}, new int[]{28, 28, 43}, 3));
        g.setColor(new Color(224, 36, 28));
        g.fillRect(14, 31, 8, 3);
        g.setColor(new Color(45, 43, 44));
        g.fillRect(17, 43, 8, 12);
        g.fillRect(44, 42, 8, 12);
        g.setColor(new Color(38, 36, 37));
        g.fillPolygon(new Polygon(new int[]{10, 20, 11}, new int[]{19, 10, 27}, 3));
        g.fillPolygon(new Polygon(new int[]{25, 35, 27}, new int[]{19, 11, 27}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createVenenatisIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(43, 34, 35));
        g.fillOval(19, 19, 30, 25);
        g.setColor(new Color(101, 25, 20));
        g.fillPolygon(new Polygon(new int[]{27, 44, 40, 25}, new int[]{21, 25, 37, 38}, 4));
        g.setColor(new Color(139, 44, 35));
        g.fillPolygon(new Polygon(new int[]{31, 43, 38, 29}, new int[]{23, 26, 34, 35}, 4));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(48, 38, 39));
        g.drawLine(23, 25, 4, 18);
        g.drawLine(23, 34, 5, 43);
        g.drawLine(45, 25, 61, 18);
        g.drawLine(45, 34, 60, 43);
        g.drawLine(28, 42, 15, 57);
        g.drawLine(43, 42, 54, 57);
        g.setColor(new Color(216, 202, 178));
        g.fillPolygon(new Polygon(new int[]{28, 34, 31}, new int[]{37, 37, 47}, 3));
        g.fillPolygon(new Polygon(new int[]{39, 45, 42}, new int[]{37, 37, 46}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createVetionIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(126, 56, 132));
        g.fillPolygon(new Polygon(new int[]{22, 43, 50, 35, 19}, new int[]{7, 11, 40, 55, 45}, 5));
        g.setColor(new Color(161, 82, 165));
        g.fillPolygon(new Polygon(new int[]{26, 39, 44, 34, 23}, new int[]{13, 15, 38, 48, 41}, 5));
        g.setColor(new Color(219, 214, 177));
        g.fillOval(31, 17, 17, 13);
        g.setColor(new Color(63, 57, 71));
        g.fillRect(38, 25, 17, 24);
        g.setColor(new Color(81, 79, 145));
        g.fillPolygon(new Polygon(new int[]{43, 59, 55, 39}, new int[]{24, 34, 51, 47}, 4));
        g.setColor(new Color(193, 185, 157));
        g.fillOval(20, 44, 9, 10);
        g.fillOval(34, 51, 11, 8);
        g.setColor(new Color(42, 36, 61));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(17, 31, 6, 50);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createKrilTsutsarothIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(118, 21, 22));
        g.fillOval(19, 16, 27, 37);
        g.setColor(new Color(88, 27, 22));
        g.fillPolygon(new Polygon(new int[]{31, 50, 58, 43}, new int[]{9, 1, 27, 24}, 4));
        g.setColor(new Color(181, 184, 179));
        g.fillPolygon(new Polygon(new int[]{18, 44, 51, 13}, new int[]{24, 22, 39, 43}, 4));
        g.setColor(new Color(58, 55, 56));
        g.fillOval(23, 26, 17, 14);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(180, 184, 184));
        g.drawLine(15, 41, 4, 57);
        g.drawLine(46, 40, 58, 56);
        g.setColor(new Color(90, 84, 90));
        g.drawLine(11, 50, 5, 60);
        g.drawLine(53, 49, 61, 60);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createKreearraIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(212, 178, 32));
        g.fillOval(22, 12, 21, 27);
        g.setColor(new Color(201, 206, 211));
        g.fillPolygon(new Polygon(new int[]{20, 6, 21}, new int[]{23, 0, 51}, 3));
        g.fillPolygon(new Polygon(new int[]{44, 58, 43}, new int[]{23, 0, 51}, 3));
        g.setColor(new Color(52, 77, 96));
        g.fillPolygon(new Polygon(new int[]{27, 39, 44, 21}, new int[]{34, 34, 57, 57}, 4));
        g.setColor(new Color(236, 232, 214));
        g.fillPolygon(new Polygon(new int[]{24, 32, 29}, new int[]{12, 4, 21}, 3));
        g.fillPolygon(new Polygon(new int[]{40, 32, 35}, new int[]{12, 4, 21}, 3));
        g.setColor(new Color(238, 195, 47));
        g.fillPolygon(new Polygon(new int[]{28, 37, 32}, new int[]{23, 23, 31}, 3));
        g.setColor(new Color(129, 97, 38));
        g.fillPolygon(new Polygon(new int[]{14, 25, 21}, new int[]{36, 41, 53}, 3));
        g.fillPolygon(new Polygon(new int[]{50, 39, 43}, new int[]{36, 41, 53}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createCommanderZilyanaIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(226, 226, 222));
        g.fillOval(25, 9, 17, 17);
        g.fillPolygon(new Polygon(new int[]{21, 43, 48, 17}, new int[]{25, 25, 51, 53}, 4));
        g.setColor(new Color(245, 245, 242));
        g.fillPolygon(new Polygon(new int[]{25, 40, 42, 22}, new int[]{28, 28, 47, 49}, 4));
        g.setColor(new Color(35, 42, 76));
        g.fillRect(27, 16, 16, 4);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(190, 190, 194));
        g.drawLine(12, 5, 12, 55);
        g.drawLine(48, 8, 55, 55);
        g.setColor(new Color(28, 31, 63));
        g.drawLine(20, 36, 8, 52);
        g.drawLine(47, 36, 58, 52);
        g.setColor(new Color(214, 168, 29));
        g.fillOval(8, 52, 8, 8);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createGeneralGraardorIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(184, 154, 55));
        g.fillOval(18, 11, 30, 38);
        g.setColor(new Color(95, 75, 50));
        g.fillPolygon(new Polygon(new int[]{20, 44, 50, 15}, new int[]{28, 26, 52, 53}, 4));
        g.setColor(new Color(182, 184, 176));
        g.fillPolygon(new Polygon(new int[]{16, 26, 20}, new int[]{12, 3, 30}, 3));
        g.fillPolygon(new Polygon(new int[]{45, 55, 45}, new int[]{12, 3, 30}, 3));
        g.setColor(new Color(64, 57, 45));
        g.fillPolygon(new Polygon(new int[]{24, 40, 45, 18}, new int[]{25, 23, 47, 49}, 4));
        g.setColor(new Color(220, 220, 210));
        g.fillPolygon(new Polygon(new int[]{24, 39, 36, 28}, new int[]{7, 7, 17, 18}, 4));
        g.setColor(new Color(77, 73, 66));
        g.fillOval(11, 44, 13, 12);
        g.fillOval(42, 44, 13, 12);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createVoidTopIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(15, 15, 17));
        g.fillPolygon(new Polygon(new int[]{18, 46, 52, 12}, new int[]{9, 9, 52, 52}, 4));
        g.setColor(new Color(48, 48, 49));
        g.fillPolygon(new Polygon(new int[]{22, 42, 47, 17}, new int[]{14, 14, 47, 47}, 4));
        g.setColor(new Color(83, 82, 78));
        g.fillRect(24, 16, 14, 4);
        g.setColor(new Color(28, 28, 30));
        g.fillRect(25, 27, 15, 15);
        g.setColor(new Color(104, 101, 94));
        g.drawLine(20, 48, 48, 48);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createVoidRobeIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(12, 12, 14));
        g.fillPolygon(new Polygon(new int[]{31, 45, 55, 40, 25, 9, 19}, new int[]{5, 34, 59, 59, 39, 59, 33}, 7));
        g.setColor(new Color(47, 47, 50));
        g.fillPolygon(new Polygon(new int[]{31, 41, 48, 38, 26, 16, 22}, new int[]{12, 35, 52, 52, 37, 52, 34}, 7));
        g.setColor(new Color(87, 84, 78));
        g.drawLine(31, 14, 31, 39);
        g.drawLine(22, 34, 40, 34);
        g.setColor(new Color(26, 26, 29));
        g.fillPolygon(new Polygon(new int[]{30, 38, 34}, new int[]{39, 53, 53}, 3));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createVoidGlovesIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(7, 12, 38));
        g.fillPolygon(new Polygon(new int[]{9, 24, 35, 29, 14}, new int[]{31, 22, 32, 43, 42}, 5));
        g.fillPolygon(new Polygon(new int[]{31, 48, 58, 52, 38}, new int[]{31, 23, 35, 46, 43}, 5));
        g.setColor(new Color(16, 24, 65));
        g.fillPolygon(new Polygon(new int[]{14, 25, 30, 26, 16}, new int[]{31, 27, 33, 39, 39}, 5));
        g.fillPolygon(new Polygon(new int[]{37, 48, 53, 50, 40}, new int[]{32, 28, 36, 42, 40}, 5));
        g.setColor(new Color(28, 37, 87));
        g.drawLine(24, 28, 30, 22);
        g.drawLine(47, 29, 53, 23);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }

    private static BufferedImage createFavourIconImage()
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(62, 41, 19));
        g.fillRect(2, 2, 60, 60);
        g.setColor(new Color(152, 103, 43));
        g.drawRect(4, 4, 55, 55);
        g.setColor(new Color(19, 19, 19));
        g.fillOval(11, 9, 42, 46);
        g.setColor(new Color(104, 103, 99));
        g.fillOval(14, 12, 36, 40);

        g.setColor(new Color(207, 209, 207));
        g.fillPolygon(new Polygon(
                new int[]{20, 30, 31, 24},
                new int[]{17, 20, 42, 47},
                4));
        g.fillPolygon(new Polygon(
                new int[]{44, 34, 33, 40},
                new int[]{17, 20, 42, 47},
                4));
        g.setColor(new Color(152, 154, 153));
        g.fillPolygon(new Polygon(
                new int[]{24, 31, 31, 28},
                new int[]{23, 24, 32, 34},
                4));
        g.fillPolygon(new Polygon(
                new int[]{40, 33, 33, 36},
                new int[]{23, 24, 32, 34},
                4));
        g.setColor(new Color(43, 43, 43));
        g.fillOval(25, 27, 4, 4);
        g.fillOval(35, 27, 4, 4);
        g.drawLine(32, 33, 32, 39);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g.dispose();
        return image;
    }
}
