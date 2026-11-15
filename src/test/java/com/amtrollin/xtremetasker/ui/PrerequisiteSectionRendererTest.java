package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrerequisiteSectionRendererTest
{
    @Test
    public void groupsDiaryRequirementsWithoutTurningQuestPointsIntoAQuest()
    {
        PrerequisiteStatus skill = new PrerequisiteStatus("95 Slayer", false, List.of(), List.of(Skill.SLAYER));
        PrerequisiteStatus quest = new PrerequisiteStatus("Dream Mentor quest", true, List.of(), List.of(), true);
        PrerequisiteStatus start = new PrerequisiteStatus("Start Eagles' Peak quest", false, List.of(), List.of(), List.of(MarkerIcon.START_QUEST));
        PrerequisiteStatus points = new PrerequisiteStatus("180 Quest Points", false, List.of(), List.of(), true);
        PrerequisiteStatus activity = new PrerequisiteStatus("Defeat Skotizo", false);
        Map<String, List<PrerequisiteStatus>> sections = PrerequisiteSectionRenderer.sections("", List.of(quest, points, activity, start, skill));
        assertEquals(List.of("Skills", "Quests", "Required activities", "Other requirements"), List.copyOf(sections.keySet()));
        assertEquals(List.of(skill), sections.get("Skills"));
        assertEquals(List.of(quest, start), sections.get("Quests"));
        assertEquals(List.of(points), sections.get("Other requirements"));
        assertEquals(List.of(activity), sections.get("Required activities"));
    }

    @Test
    public void miniquestsShareTheQuestSectionAndRetainTheirIcon()
    {
        PrerequisiteStatus miniquest = new PrerequisiteStatus("Vale Totems miniquest", false,
                List.of(), List.of(), List.of(MarkerIcon.VALE_TOTEMS));
        assertEquals(List.of(miniquest), PrerequisiteSectionRenderer.sections("", List.of(miniquest)).get("Quests"));
        assertEquals("Vale Totems", PrerequisiteSectionRenderer.displayText(miniquest));
        assertEquals(List.of(MarkerIcon.VALE_TOTEMS), miniquest.getMarkerIcons());
    }

    @Test
    public void keepsCompoundConditionsAndAuthoredOrderWithoutDroppingDistinctLevels()
    {
        PrerequisiteStatus met = new PrerequisiteStatus("60 Magic", true);
        PrerequisiteStatus unmet = new PrerequisiteStatus("70 Magic", false);
        PrerequisiteStatus combined = new PrerequisiteStatus("Combined Strength and Attack level of 130, or 99 in either", false, List.of(), List.of(Skill.STRENGTH, Skill.ATTACK));
        List<PrerequisiteStatus> skills = PrerequisiteSectionRenderer.sections("", List.of(met, unmet, combined, unmet)).get("Skills");
        assertEquals(List.of(met, unmet, combined), skills);
        assertTrue(PrerequisiteSectionRenderer.sections("None", List.of()).isEmpty());
        assertTrue(PrerequisiteSectionRenderer.sections("n/a", null).isEmpty());
    }

    @Test
    public void stacksEachColumnIndependentlyWithoutGapsFromTheOtherColumn()
    {
        BufferedImage image = new BufferedImage(520, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            PrerequisiteSectionRenderer renderer = new PrerequisiteSectionRenderer(16, Color.WHITE, Color.GRAY);
            String two = "60 Magic; Dream Mentor quest";
            assertEquals(51, renderer.measure(two, null, fm, 500, null, null));
            assertEquals(71, renderer.render(g, fm, 0, 20, 500, two, null, null, null, Color.WHITE));
            // The separator occupies the gutter, with a low-opacity gray rather than a hard edge.
            Color divider = new Color(image.getRGB(250, 40), true);
            assertEquals(55, divider.getAlpha());
            assertEquals(divider.getRed(), divider.getBlue());
            String three = two + "; 180 Quest Points";
            assertEquals(89, renderer.measure(three, null, fm, 500, null, null));
            assertEquals(109, renderer.render(g, fm, 0, 20, 500, three, null, null, null, Color.WHITE));
            String tallQuests = "60 Magic; One quest; Two quest; Three quest; Four quest; 180 Quest Points";
            assertEquals("The third section should fit beneath Skills without waiting for Quests", 99,
                    renderer.measure(tallQuests, null, fm, 500, null, null));
            assertEquals(119, renderer.render(g, fm, 0, 20, 500, tallQuests, null, null, null, Color.WHITE));
        }
        finally { g.dispose(); }
    }

    @Test
    public void milestoneTitlesWrapAndDifferentSubquestsUnderTheSameParentRemainVisible()
    {
        PrerequisiteStatus pete = new PrerequisiteStatus("Reach milestone in Recipe for Disaster", false,
                List.of(), List.of(), List.of(MarkerIcon.QUEST), "Start Freeing Pirate Pete and unlock diving with Murphy.", false);
        PrerequisiteStatus king = new PrerequisiteStatus("Reach milestone in Recipe for Disaster", true,
                List.of(), List.of(), List.of(MarkerIcon.QUEST), "Complete Freeing King Awowogei.", true);
        assertEquals(2, PrerequisiteSectionRenderer.sections("", List.of(pete, king)).get("Quests").size());
        assertEquals("Dream Mentor", PrerequisiteSectionRenderer.displayText(new PrerequisiteStatus("Dream Mentor quest", false)));
        assertEquals("Legends' Quest", PrerequisiteSectionRenderer.displayText(new PrerequisiteStatus("Legends' Quest", false)));
        assertEquals("Elemental Workshop II", PrerequisiteSectionRenderer.displayText(new PrerequisiteStatus(
                "Complete Elemental Workshop II", false, List.of(), List.of(), true)));
        assertEquals("Start The Giant Dwarf", PrerequisiteSectionRenderer.displayText(new PrerequisiteStatus(
                "Start The Giant Dwarf", false, List.of(), List.of(), List.of(MarkerIcon.START_QUEST))));
        Graphics2D g = new BufferedImage(520, 1800, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            PrerequisiteSectionRenderer renderer = new PrerequisiteSectionRenderer(16, Color.WHITE, Color.GRAY);
            List<PrerequisiteStatus> entries = List.of(new PrerequisiteStatus("60 Magic", true), pete, king);
            for (int width : new int[] {160, 320, 500})
            {
                int measured = renderer.measure("", entries, fm, width, null, null);
                assertEquals(measured + 20, renderer.render(g, fm, 0, 20, width, "", entries, null, null, Color.WHITE));
            }
        }
        finally { g.dispose(); }
    }

    @Test
    public void onlyMilestoneQuestsExposeDetailsThroughHelp()
    {
        PrerequisiteStatus complete = new PrerequisiteStatus("Complete Dream Mentor", true,
                List.of(), List.of(), List.of(MarkerIcon.QUEST), "An ordinary quest description.", true);
        PrerequisiteStatus start = new PrerequisiteStatus("Start The Giant Dwarf", false,
                List.of(), List.of(), List.of(MarkerIcon.START_QUEST), "An opening sequence description.", true);
        PrerequisiteStatus milestone = new PrerequisiteStatus("Reach milestone in Watchtower", false,
                List.of(), List.of(), List.of(MarkerIcon.QUEST), "Unlock access to the Skavid Caves.", true);
        assertEquals("", PrerequisiteSectionRenderer.milestoneDetail(complete));
        assertEquals("", PrerequisiteSectionRenderer.milestoneDetail(start));
        assertEquals(milestone.getDetail(), PrerequisiteSectionRenderer.milestoneDetail(milestone));

        BufferedImage image = new BufferedImage(520, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            PrerequisiteSectionRenderer renderer = new PrerequisiteSectionRenderer(16, Color.WHITE, Color.GRAY);
            Rectangle viewport = new Rectangle(0, 50, 500, 300);
            Rectangle bounds = new Rectangle(0, 0, 520, 400);
            int bottom = renderer.render(g, fm, 10, 60, 480, "", List.of(milestone), null, null, Color.WHITE);
            assertEquals(60 + renderer.measure("", List.of(milestone), fm, 480, null, null), bottom);
            assertEquals("Inline info needs no extra row", 60 + 16 * 2 + 3 + 19, bottom);
            int[] withoutHover = image.getRGB(0, 0, 520, 400, null, 0, 520);
            int questBaseline = 60 + 16 * 2 + 3;
            Point help = new Point(10 + PrerequisiteIconRenderer.markerWidth(fm, (BufferedImage) null)
                    + fm.stringWidth(milestone.getText()) + 5,
                    questBaseline - fm.getAscent() + (fm.getHeight() - 13) / 2 + 1);

            renderer.renderHover(g, fm, new Point(300, help.y), viewport, bounds);
            assertArrayEquals(withoutHover, image.getRGB(0, 0, 520, 400, null, 0, 520));
            renderer.renderHover(g, fm, help, new Rectangle(0, 200, 500, 100), bounds);
            assertArrayEquals("Scrolled-out help cannot be hovered", withoutHover,
                    image.getRGB(0, 0, 520, 400, null, 0, 520));
            renderer.renderHover(g, fm, help, viewport, bounds);
            assertFalse("Hover should reveal milestone details", java.util.Arrays.equals(withoutHover,
                    image.getRGB(0, 0, 520, 400, null, 0, 520)));

            renderer.render(g, fm, 10, 60, 480, "", List.of(complete, start), null, null, Color.WHITE);
            int[] ordinaryQuests = image.getRGB(0, 0, 520, 400, null, 0, 520);
            renderer.renderHover(g, fm, help, viewport, bounds);
            assertArrayEquals("Changing tasks clears previous milestone help", ordinaryQuests,
                    image.getRGB(0, 0, 520, 400, null, 0, 520));
        }
        finally { g.dispose(); }
    }

    @Test
    public void narrowColumnsPreserveLongQuestWordsWithoutTruncatingThem()
    {
        Graphics2D g = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            FontMetrics fm = g.getFontMetrics();
            String name = "RumDealAndTroubleBrewing";
            List<String> lines = PrerequisiteSectionRenderer.wrapRequirementText(name, fm, 45);
            assertTrue(lines.size() > 1);
            assertEquals(name, String.join("", lines));
            for (String line : lines) assertTrue(fm.stringWidth(line) <= 45);
        }
        finally { g.dispose(); }
    }

    @Test
    public void suggestedStatsResolveSkillIconsAndMeasureTheirWrappedRows()
    {
        Graphics2D g = new BufferedImage(300, 700, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            java.util.Set<Skill> requested = new java.util.HashSet<>();
            BufferedImage icon = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
            java.util.function.Function<Skill, BufferedImage> provider = skill -> {
                requested.add(skill);
                return icon;
            };
            PrerequisiteSectionRenderer renderer = new PrerequisiteSectionRenderer(16, Color.WHITE, Color.GRAY);
            String stats = "60 Magic; 50 Prayer; 50 Defence";
            int height = renderer.measureSuggestedStats(stats, fm, 120, provider);
            assertEquals(20 + height, renderer.renderSuggestedStats(g, fm, 0, 20, 120, stats, provider));
            assertEquals(java.util.Set.of(Skill.MAGIC, Skill.PRAYER, Skill.DEFENCE), requested);
            assertEquals(0, renderer.measureSuggestedStats(null, fm, 120, provider));
        }
        finally { g.dispose(); }
    }

    @Test
    public void measuresExactlyWhatItDrawsForWrappedDiaryAndSuggestedStats()
    {
        Graphics2D g = new BufferedImage(300, 1500, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            PrerequisiteSectionRenderer renderer = new PrerequisiteSectionRenderer(16, Color.WHITE, Color.GRAY);
            String prereqs = "95 Slayer; Dream Mentor quest; Start Eagles' Peak quest; Defeat a Hydra in the Karuulm Slayer Dungeon; 180 Quest Points";
            for (int width : new int[] {110, 260})
            {
                int measured = renderer.measure(prereqs, null, fm, width, null, null);
                assertEquals(measured + 20, renderer.render(g, fm, 0, 20, width, prereqs, null, null, null, Color.WHITE));
                String stats = "80 Ranged; 70 Prayer; For a comfortable ranged approach";
                assertEquals(20 + renderer.measureSuggestedStats(stats, fm, width, null), renderer.renderSuggestedStats(g, fm, 0, 20, width, stats, null));
            }
            assertEquals(32, renderer.measure("None", null, fm, 260, null, null));
            assertEquals(52, renderer.render(g, fm, 0, 20, 260, "None", null, null, null, Color.WHITE));
            assertEquals(20, renderer.renderSuggestedStats(g, fm, 0, 20, 260, null, null));
        }
        finally { g.dispose(); }
    }
}
