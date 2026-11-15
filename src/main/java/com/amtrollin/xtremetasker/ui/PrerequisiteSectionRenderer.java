package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.Skill;

import static com.amtrollin.xtremetasker.ui.text.TextUtils.*;

public final class PrerequisiteSectionRenderer
{
    private static final int COLUMN_GAP = 20;
    private static final int SECTION_GAP = 3;
    private static final int HEADING_BOTTOM_GAP = 3;
    private static final int SECTION_INSET = 0;
    private static final Color COLUMN_DIVIDER = new Color(180, 180, 180, 55);
    private static final Color SUBSECTION_TITLE = new Color(180, 180, 180, 235);
    private static final Set<MarkerIcon> MINIQUEST_ICONS = Set.of(
            MarkerIcon.BARBARIAN_MINIQUEST, MarkerIcon.LAIR_OF_TARN_RAZORLOR,
            MarkerIcon.MAGE_ARENA_1, MarkerIcon.ENTER_THE_ABYSS, MarkerIcon.ALFRED_GRIMHANDS_BARCRAWL,
            MarkerIcon.VALE_TOTEMS, MarkerIcon.MINIQUEST);
    private static final int HELP_SIZE = 13;
    private static final int HELP_GAP = 4;
    private final Map<Rectangle, String> milestoneHelp = new LinkedHashMap<>();
    private final int rowHeight;
    private final Color uiText;
    private final Color uiTextDim;

    public PrerequisiteSectionRenderer(int rowHeight, Color uiText, Color uiTextDim)
    {
        this.rowHeight = rowHeight;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
    }

    public int measureTip(String tip, FontMetrics fm, int maxWidth, int maxLines)
    {
        return rowHeight * Math.min(wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40)).size(), maxLines);
    }

    public int drawTip(Graphics2D g, FontMetrics fm, String tip, int x, int y, int maxWidth, int maxLines)
    {
        List<String> lines = wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40));
        g.setColor(uiTextDim);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++)
        {
            g.drawString(truncateToWidth(lines.get(i), fm, maxWidth), x, y);
            y += rowHeight;
        }
        return y;
    }

    public int measure(
            String prereqs,
            List<PrerequisiteStatus> statuses,
            FontMetrics fm,
            int maxWidth,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        List<Map.Entry<String, List<PrerequisiteStatus>>> groups = new ArrayList<>(sections(prereqs, statuses).entrySet());
        if (groups.isEmpty()) return rowHeight * 2; // Prereqs + None
        int width = columnWidth(maxWidth, groups.size());
        int[] columnHeights = new int[2];
        for (int i = 0; i < groups.size(); i++)
        {
            int column = i % 2;
            if (i >= 2) columnHeights[column] += SECTION_GAP;
            columnHeights[column] += sectionHeight(groups.get(i), fm, width, skillImageProvider, markerImageProvider);
        }
        return rowHeight + Math.max(columnHeights[0], columnHeights[1]);
    }

    public int render(
            Graphics2D g, FontMetrics fm, int x, int y, int maxWidth,
            String prereqs, List<PrerequisiteStatus> statuses,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider, Color rawTextColor)
    {
        milestoneHelp.clear();
        g.setColor(UiPalette.DEFAULT.UI_GOLD);
        g.drawString("Prereqs", x, y);
        y += rowHeight;
        List<Map.Entry<String, List<PrerequisiteStatus>>> groups = new ArrayList<>(sections(prereqs, statuses).entrySet());
        if (groups.isEmpty())
        {
            g.setColor(uiTextDim);
            g.drawString("None", x, y);
            return y + rowHeight;
        }
        int width = columnWidth(maxWidth, groups.size());
        int left = x + SECTION_INSET;
        int top = y;
        int[] columnBottoms = {y, y};
        for (int i = 0; i < groups.size(); i++)
        {
            int column = i % 2;
            if (i >= 2) columnBottoms[column] += SECTION_GAP;
            columnBottoms[column] = drawSection(g, fm, groups.get(i), left + column * (width + COLUMN_GAP),
                    columnBottoms[column], width, skillImageProvider, markerImageProvider);
        }
        int bottom = Math.max(columnBottoms[0], columnBottoms[1]);
        if (groups.size() > 1)
        {
            int dividerX = left + width + COLUMN_GAP / 2;
            g.setColor(COLUMN_DIVIDER);
            g.drawLine(dividerX, top - fm.getAscent(), dividerX, bottom - rowHeight + fm.getDescent());
        }
        return bottom;
    }

    private static int columnWidth(int maxWidth, int sectionCount)
    {
        int available = Math.max(1, maxWidth - SECTION_INSET);
        return sectionCount == 1 ? available : Math.max(1, (available - COLUMN_GAP) / 2);
    }

    private int sectionHeight(Map.Entry<String, List<PrerequisiteStatus>> section, FontMetrics fm, int width,
            Function<Skill, BufferedImage> skillImageProvider, Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        int height = rowHeight * wrapRequirementText(section.getKey(), fm, width).size() + HEADING_BOTTOM_GAP;
        for (PrerequisiteStatus status : section.getValue())
        {
            BufferedImage marker = PrerequisiteIconRenderer.resolveMarkerImage(status, skillImageProvider, markerImageProvider);
            height += entryHeight(status, fm, width, marker);
        }
        return height;
    }

    private int drawSection(Graphics2D g, FontMetrics fm, Map.Entry<String, List<PrerequisiteStatus>> section,
            int x, int y, int width, Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        g.setColor(SUBSECTION_TITLE);
        for (String line : wrapRequirementText(section.getKey(), fm, width))
        {
            g.drawString(line, x, y);
            y += rowHeight;
        }
        return drawStatuses(g, fm, x, y + HEADING_BOTTOM_GAP, width, section.getValue(), skillImageProvider, markerImageProvider);
    }

    // Unlike the general-purpose wrapper, preserve long quest words instead of ellipsizing them.
    static List<String> wrapRequirementText(String text, FontMetrics fm, int width)
    {
        int maxWidth = Math.max(1, width);
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R"))
        {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+"))
            {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth)
                {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0)
                {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                for (int offset = 0; offset < word.length();)
                {
                    int end = offset + 1;
                    while (end < word.length() && fm.stringWidth(word.substring(offset, end + 1)) <= maxWidth) end++;
                    String part = word.substring(offset, end);
                    if (end < word.length()) lines.add(part);
                    else line.append(part);
                    offset = end;
                }
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        return lines;
    }

    // Shared by measuring and drawing so long diary lists keep the same scroll geometry.
    static Map<String, List<PrerequisiteStatus>> sections(String prereqs, List<PrerequisiteStatus> statuses)
    {
        Map<String, List<PrerequisiteStatus>> sections = new LinkedHashMap<>();
        for (String heading : List.of("Skills", "Quests", "Required activities", "Other requirements"))
            sections.put(heading, new ArrayList<>());
        List<PrerequisiteStatus> entries = statuses;
        if (entries == null || entries.isEmpty())
        {
            entries = new ArrayList<>();
            for (String line : formatText(normalizeText(prereqs)).split("\n"))
                if (!line.trim().isEmpty()) entries.add(new PrerequisiteStatus(line.trim(), false));
        }
        Set<String> seen = new HashSet<>();
        for (PrerequisiteStatus status : entries)
        {
            if (status == null || !seen.add((status.getText() + "\n" + safe(status.getDetail())).trim().toLowerCase(Locale.ROOT))) continue;
            sections.get(category(status)).add(status);
        }
        sections.values().removeIf(List::isEmpty);
        // Preserve authored order so meeting a prerequisite never moves its row.
        return sections;
    }

    private static String category(PrerequisiteStatus status)
    {
        String text = status.getText().trim().toLowerCase(Locale.ROOT);
        if (status.getMarkerIcons().contains(MarkerIcon.START_QUEST)
                || text.matches("^(start|partially complete|progress)\\b.*"))
            return "Quests";
        if (!text.matches(".*\\bquest points?\\b.*")
                && (status.hasQuestIcon() || !Collections.disjoint(status.getMarkerIcons(), MINIQUEST_ICONS) || text.matches(".*\\b(quest|subquest|miniquest)\\b.*")))
            return "Quests";
        if (!status.getSkillIcons().isEmpty() || status.getMarkerIcons().contains(MarkerIcon.COMBAT)
                || status.getMarkerIcons().contains(MarkerIcon.TOTAL)
                || text.startsWith("combined ") || text.startsWith("a combined ")
                || text.matches("^\\d+\\+? (attack|strength|defence|ranged|prayer|magic|runecrafting|construction|hitpoints|agility|herblore|thieving|crafting|fletching|slayer|hunter|mining|smithing|fishing|cooking|firemaking|woodcutting|farming|sailing|combat|total)(\\b.*)"))
            return "Skills";
        if (text.matches("^(defeat|kill|open|catch|craft|equip|wear|acquire|reach)\\b.*")
                && !text.matches(".*\\bquest points?\\b.*"))
            return "Required activities";
        return "Other requirements";
    }

    public int measureSuggestedStats(String stats, FontMetrics fm, int width,
            Function<Skill, BufferedImage> skillImageProvider)
    {
        List<PrerequisiteStatus> items = suggestedStatItems(stats);
        if (items.isEmpty()) return 0;
        return 8 + sectionHeight(Map.entry("Suggested stats", items), fm, width, skillImageProvider, null);
    }

    public int renderSuggestedStats(Graphics2D g, FontMetrics fm, int x, int y, int width, String stats,
            Function<Skill, BufferedImage> skillImageProvider)
    {
        List<PrerequisiteStatus> items = suggestedStatItems(stats);
        if (items.isEmpty()) return y;
        y += 8;
        g.setColor(UiPalette.DEFAULT.UI_GOLD);
        for (String line : wrapRequirementText("Suggested stats", fm, width))
        {
            g.drawString(line, x, y);
            y += rowHeight;
        }
        return drawStatuses(g, fm, x, y + HEADING_BOTTOM_GAP, width, items, skillImageProvider, null);
    }

    private static List<PrerequisiteStatus> suggestedStatItems(String stats)
    {
        List<PrerequisiteStatus> items = new ArrayList<>();
        for (String line : formatText(normalizeText(stats)).split("\n"))
        {
            if (line.isEmpty()) continue;
            String skillName = line.replaceFirst("^\\d+\\+?\\s+", "").trim();
            List<Skill> icons = new ArrayList<>();
            for (Skill skill : Skill.values())
            {
                if (skill.getName().equalsIgnoreCase(skillName)
                        || skill == Skill.RUNECRAFT && skillName.equalsIgnoreCase("Runecrafting"))
                {
                    icons.add(skill);
                    break;
                }
            }
            // Suggested levels are advisory: they never become completion checks.
            items.add(new PrerequisiteStatus(line, false, List.of(), icons));
        }
        return items;
    }

    public static String normalizeText(String prereqs)
    {
        String normalized = safe(prereqs).replace("\r", "").trim();
        return normalized.isEmpty() || normalized.equalsIgnoreCase("none") || normalized.equalsIgnoreCase("n/a") || normalized.equals("-")
                ? ""
                : normalized;
    }

    private static String formatText(String prereqs)
    {
        return safe(prereqs).replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
    }

    private int drawStatuses(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int y,
            int maxWidth,
            List<PrerequisiteStatus> statuses,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        for (PrerequisiteStatus status : statuses)
        {
            BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, skillImageProvider, markerImageProvider);
            int markerX = x;
            int markerWidth = PrerequisiteIconRenderer.markerWidth(fm, markerImage);
            int textX = markerX + markerWidth;
            int lineHeight = PrerequisiteIconRenderer.lineHeight(rowHeight, status);
            boolean firstLine = true;
            String title = displayText(status);
            int titleOffset = 0;
            List<String> lines = titleLines(status, fm, maxWidth, markerImage);
            for (String line : lines)
            {
                if (firstLine)
                {
                    PrerequisiteIconRenderer.drawMarker(g, fm, markerImage, markerX, y);
                }
                int offset = title.indexOf(line, titleOffset);
                drawStatusLine(g, fm, status, line, textX, y, offset);
                titleOffset = offset < 0 ? titleOffset : offset + line.length();
                y += lineHeight;
                firstLine = false;
            }
            String detail = milestoneDetail(status);
            if (!detail.isEmpty())
            {
                String lastLine = lines.get(lines.size() - 1);
                Rectangle help = new Rectangle(textX + fm.stringWidth(lastLine) + HELP_GAP,
                        y - lineHeight - fm.getAscent() + (fm.getHeight() - HELP_SIZE) / 2, HELP_SIZE, HELP_SIZE);
                drawInfoIcon(g, help);
                milestoneHelp.put(help, detail);
            }
        }
        return y;
    }

    private int entryHeight(PrerequisiteStatus status, FontMetrics fm, int width, BufferedImage marker)
    {
        return PrerequisiteIconRenderer.lineHeight(rowHeight, status) * titleLines(status, fm, width, marker).size();
    }

    private static List<String> titleLines(PrerequisiteStatus status, FontMetrics fm, int width, BufferedImage marker)
    {
        int helpWidth = milestoneDetail(status).isEmpty() ? 0 : HELP_GAP + HELP_SIZE;
        return wrapRequirementText(displayText(status), fm,
                Math.max(1, width - PrerequisiteIconRenderer.markerWidth(fm, marker) - helpWidth));
    }

    private static void drawInfoIcon(Graphics2D g, Rectangle bounds)
    {
        g.setColor(new Color(20, 15, 10, 220));
        g.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(SUBSECTION_TITLE);
        g.drawOval(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        Font original = g.getFont();
        g.setFont(original.deriveFont(Font.BOLD));
        java.awt.geom.Rectangle2D glyph = g.getFont().createGlyphVector(g.getFontRenderContext(), "i").getVisualBounds();
        g.drawString("i", (float) (bounds.x + (bounds.width - glyph.getWidth()) / 2 - glyph.getX()),
                (float) (bounds.y + (bounds.height - glyph.getHeight()) / 2 - glyph.getY()));
        g.setFont(original);
    }

    static String displayText(PrerequisiteStatus status)
    {
        String text = status.getText();
        if (text.equalsIgnoreCase("Part 1 of Barbarian Firemaking")) return "Reach milestone in Barbarian Training";
        if (!"Quests".equals(category(status)) || text.startsWith("Reach milestone")
                || text.startsWith("Start ")) return text;
        // Lowercase suffixes are type labels; preserve proper names such as Olaf's Quest.
        return text.replaceFirst("^Complete ", "").replaceFirst(" (miniquest|subquest|quest)$", "");
    }

    static String milestoneDetail(PrerequisiteStatus status)
    {
        if (status.getText().equalsIgnoreCase("Part 1 of Barbarian Firemaking"))
            return "Complete the first stage of Barbarian Firemaking.";
        return displayText(status).startsWith("Reach milestone") ? safe(status.getDetail()).trim() : "";
    }

    /** Draw after the scrollable content so milestone help stays above subsequent sections. */
    public void renderHover(Graphics2D g, FontMetrics fm, Point mouse, Rectangle viewport, Rectangle bounds)
    {
        if (mouse == null || viewport == null || bounds == null || !viewport.contains(mouse)) return;
        for (Map.Entry<Rectangle, String> help : milestoneHelp.entrySet())
        {
            if (!help.getKey().contains(mouse)) continue;
            // Match the wrapped, soft-gray hover text used by the other help icons.
            int padX = 8;
            int padY = 6;
            List<String> lines = wrapRequirementText(help.getValue(), fm, Math.max(1, bounds.width - 24 - padX * 2));
            int textWidth = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
            int height = fm.getHeight() * lines.size() + padY * 2;
            int x = Math.max(bounds.x + 6, Math.min(help.getKey().x + help.getKey().width - padX,
                    bounds.x + bounds.width - textWidth - padX * 2 - 6));
            int y = Math.max(bounds.y + 6, help.getKey().y - height + 2);
            Shape oldClip = g.getClip();
            g.clip(bounds);
            g.setColor(uiTextDim);
            int baseline = y + padY + fm.getAscent();
            for (String line : lines)
            {
                g.drawString(line, x + padX, baseline);
                baseline += fm.getHeight();
            }
            g.setClip(oldClip);
            return;
        }
    }

    private void drawStatusLine(Graphics2D g, FontMetrics fm, PrerequisiteStatus status, String line, int x, int y, int titleOffset)
    {
        boolean hasCheckSpans = status.getCheckSpans() != null && !status.getCheckSpans().isEmpty();
        Color textColor = !hasCheckSpans && status.isCompleted() ? uiTextDim : uiText;
        String title = displayText(status);
        int actionLength = title.startsWith("Reach milestone") ? "Reach milestone".length()
                : title.startsWith("Start ") ? "Start".length() : 0;
        int coloredChars = titleOffset < 0 ? 0 : Math.max(0, Math.min(line.length(), actionLength - titleOffset));
        g.setColor(textColor);
        g.drawString(line, x, y);
        if (coloredChars > 0)
        {
            g.setColor(UiPalette.TIER_COMPLETE_GLOW);
            g.drawString(line.substring(0, coloredChars), x, y);
        }

        if (!hasCheckSpans)
        {
            return;
        }

        for (PrerequisiteStatus.CheckSpan span : status.getCheckSpans())
        {
            if (!span.isCompleted() || span.getStart() < 0 || span.getEnd() > status.getText().length() || span.getStart() >= span.getEnd())
            {
                continue;
            }

            String spanText = status.getText().substring(span.getStart(), span.getEnd());
            int lineIndex = line.indexOf(spanText);
            if (lineIndex < 0)
            {
                continue;
            }

            int spanX = x + fm.stringWidth(line.substring(0, lineIndex));
            g.setColor(uiTextDim);
            g.drawString(spanText, spanX, y);

        }
    }

}
