package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static com.amtrollin.xtremetasker.ui.text.TextUtils.truncateToWidth;

/**
 * Goals this version satisfies:
 * - Search + Filters header span full available width.
 * - Filter "chips" fit to text.
 * - Consistent label column width across Source/Status/Tier/Sort (labels NOT stretched).
 * - Visible blank space between chips and between label and first chip (no beveled "mini boxes" in gaps).
 * - No trailing row background behind the chips; row visuals are just a label cell + individual chips.
 * - Tier scope pill dims bracketed tier text.
 */
public class TaskControlsRenderer
{
    private static final BufferedImage QUESTION_ICON = loadQuestionIconSafe();

    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;

    private final Color tabInactiveBg;
    private final Color uiEdgeLight;
    private final Color uiEdgeDark;
    private final Color uiGold;
    private final Color uiText;
    private final Color uiTextDim;

    private final Color inputBg;
    private final Color inputFocusOutline;
    private final Color pillOnBg;
    private final Color pillOffBg;

    public TaskControlsRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            Color tabInactiveBg,
            Color uiEdgeLight,
            Color uiEdgeDark,
            Color uiGold,
            Color uiText,
            Color uiTextDim,
            Color inputBg,
            Color inputFocusOutline,
            Color pillOnBg,
            Color pillOffBg
    )
    {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;

        this.tabInactiveBg = tabInactiveBg;
        this.uiEdgeLight = uiEdgeLight;
        this.uiEdgeDark = uiEdgeDark;
        this.uiGold = uiGold;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;

        this.inputBg = inputBg;
        this.inputFocusOutline = inputFocusOutline;
        this.pillOnBg = pillOnBg;
        this.pillOffBg = pillOffBg;
    }

    /**
     * Draws the control block and mutates layout bounds.
     * Returns the new cursorY after controls are rendered.
     */
    public int render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorY,
            TaskControlsLayout layout,
            TaskListQuery query,
            String activeTierLabel,
            int panelW,
            int mouseX,
            int mouseY,
            boolean hasNewTasks
    )
    {
        // Shared geometry
        int rowX = panelX + panelPadding;
        int rowW = panelW - 2 * panelPadding;

        final String SOURCE_LABEL = "Source:";
        final String STATUS_LABEL = "Status:";
        final String TIER_LABEL = "Tier:";
        final String SORT_LABEL = "Sort by:";

        final int leftPad = 8;
        final int rightPad = 8;
        final int labelGap = 8;

        // Visual spacing (these control the look you want)
        final int labelToPillsGap = 8;   // blank space between label column and first chip
        final int chipGap = 6;           // blank space between chips
        final int pillPadX = 10;          // inside-pill horizontal padding

        // Fixed label column width so labels align and do NOT stretch unexpectedly
        final int labelColW = Math.max(
                fm.stringWidth(SOURCE_LABEL),
                Math.max(
                        fm.stringWidth(STATUS_LABEL),
                        Math.max(fm.stringWidth(TIER_LABEL), fm.stringWidth(SORT_LABEL))
                )
        ) + labelGap;

        // Chips start after label column + blank gap
        final int pillsStartX = rowX + leftPad + labelColW + labelToPillsGap;

        // ================================
        // Row 1: Search (full width)
        // ================================
        cursorY += 5; // small gap above search
        int searchRowTop = cursorY - fm.getAscent();
        int searchRowH = rowHeight + 6;

        layout.searchBox.setBounds(rowX, searchRowTop, rowW, searchRowH);

        drawBevelBox(g, layout.searchBox, inputBg, uiEdgeLight, uiEdgeDark);
        // Gold border when focused or has text; dim grey otherwise
        boolean searchActive = query.searchFocused || (query.searchText != null && !query.searchText.isEmpty());
        g.setColor(searchActive ? inputFocusOutline : withAlpha(uiTextDim, 100));
        g.drawRect(layout.searchBox.x, layout.searchBox.y, layout.searchBox.width, layout.searchBox.height);

        // Magnifying glass icon
        int mgX = layout.searchBox.x + 5;
        int mgY = layout.searchBox.y + (layout.searchBox.height - 9) / 2;
        g.setColor(searchActive ? withAlpha(uiGold, 210) : withAlpha(uiTextDim, 150));
        g.drawOval(mgX, mgY, 7, 7);
        g.drawLine(mgX + 6, mgY + 6, mgX + 9, mgY + 9);

        String placeholder = "Search tasks...";
        boolean empty = (query.searchText == null || query.searchText.isEmpty());
        String shown = (!query.searchFocused && empty) ? placeholder : (empty ? "" : query.searchText);

        g.setColor((!query.searchFocused && empty) ? uiTextDim : uiText);

        int textX = layout.searchBox.x + 18;  // offset for icon
        int baseY = centeredTextBaseline(layout.searchBox, fm);

        // Compute and store per-character pixel positions for mouse selection
        String rawText = (query.searchText != null) ? query.searchText : "";
        int[] charPositions = new int[rawText.length() + 1];
        for (int i = 0; i <= rawText.length(); i++) {
            charPositions[i] = fm.stringWidth(rawText.substring(0, i));
        }
        layout.searchTextX = textX;
        layout.searchCharXPositions = charPositions;

        // Draw selection highlight
        int selStart = query.searchSelStart;
        int selEnd = query.searchSelEnd;
        if (query.searchFocused && selStart >= 0 && selEnd > selStart
                && selStart <= rawText.length() && selEnd <= rawText.length()) {
            int sx = textX + charPositions[selStart];
            int ex = textX + charPositions[selEnd];
            int selTop = layout.searchBox.y + 3;
            int selH = layout.searchBox.height - 6;
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 90));
            g.fillRect(sx, selTop, Math.max(1, ex - sx), selH);
        }

        boolean caretOn = query.searchFocused && isCaretVisible();
        String caretText = caretOn ? (shown + "|") : shown;
        g.drawString(truncateToWidth(caretText, fm, layout.searchBox.width - 16), textX, baseY);

        // extra padding below search (you wanted this)
        cursorY += searchRowH + 12;

        // ================================
        // Row 2: Filters header + applied state
        // ================================
        layout.filtersExpanded = true;
        layout.filtersHeaderBounds.setBounds(0, 0, 0, 0);
        g.setColor(uiGold);
        g.drawString("Filters", rowX + leftPad, cursorY);
        drawHeaderRule(g, rowX + leftPad + fm.stringWidth("Filters") + 8, cursorY - fm.getAscent() + fm.getHeight() / 2, rowX + rowW);

        cursorY += fm.getHeight() + 2;
        boolean hasActiveFilters = query.sourceFilter != TaskListQuery.SourceFilter.ALL
                || query.statusFilter != TaskListQuery.StatusFilter.ALL
                || query.tierScope != TaskListQuery.TierScope.ALL_TIERS;
        drawClearLinkLine(g, fm, hasActiveFilters, layout.clearFilters, rowX + leftPad, rowW - leftPad, cursorY, mouseX, mouseY);
        cursorY += fm.getHeight() + 6;

        // ================================
        // Rows 3-5: Filter chips (only shown when expanded)
        // ================================
        int rowH = rowHeight + 6;
        int rowTop = 0;
        rowTop = cursorY - fm.getAscent();

        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, SOURCE_LABEL, leftPad);

        final String SRC_ALL = "All";
        final String SRC_CA = "CAs";
        final String SRC_CL = "CLOGs";
        final String SRC_DA = "DAs";

        int availableSource = (rowX + rowW - rightPad) - pillsStartX;

        int wAll = pillWidth(fm, SRC_ALL, pillPadX, 42, availableSource);
        int wCA = pillWidth(fm, SRC_CA, pillPadX, 42, availableSource);
        int wCL = pillWidth(fm, SRC_CL, pillPadX, 52, availableSource);
        int wDA = pillWidth(fm, SRC_DA, pillPadX, 42, availableSource);

        int sx = pillsStartX;
        layout.filterSourceAll.setBounds(sx, rowTop, wAll, rowH);
        sx += wAll + chipGap;

        layout.filterCA.setBounds(sx, rowTop, wCA, rowH);
        sx += wCA + chipGap;

        layout.filterCL.setBounds(sx, rowTop, wCL, rowH);
        sx += wCL + chipGap;

        layout.filterDA.setBounds(sx, rowTop, wDA, rowH);

        drawPill(g, fm, layout.filterSourceAll, SRC_ALL, query.sourceFilter == TaskListQuery.SourceFilter.ALL);
        drawPill(g, fm, layout.filterCA, SRC_CA, query.sourceFilter == TaskListQuery.SourceFilter.CA);
        drawPill(g, fm, layout.filterCL, SRC_CL, query.sourceFilter == TaskListQuery.SourceFilter.CLOGS);
        drawPill(g, fm, layout.filterDA, SRC_DA, query.sourceFilter == TaskListQuery.SourceFilter.DAS);

        cursorY += rowH + 6;

        // ================================
        // Row 4: Status chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, STATUS_LABEL, leftPad);

        final String ST_ALL = "All";
        final String ST_INC = "Incomplete";
        final String ST_COMP = "Complete";

        int availableStatus = (rowX + rowW - rightPad) - pillsStartX;

        int wAllS = pillWidth(fm, ST_ALL, pillPadX, 42, availableStatus);
        int wIncS = pillWidth(fm, ST_INC, pillPadX, 70, availableStatus);
        int wCompS = pillWidth(fm, ST_COMP, pillPadX, 70, availableStatus);

        int stx = pillsStartX;
        layout.filterStatusAll.setBounds(stx, rowTop, wAllS, rowH);
        stx += wAllS + chipGap;

        layout.filterIncomplete.setBounds(stx, rowTop, wIncS, rowH);
        stx += wIncS + chipGap;

        layout.filterComplete.setBounds(stx, rowTop, wCompS, rowH);

        drawPill(g, fm, layout.filterStatusAll, ST_ALL, query.statusFilter == TaskListQuery.StatusFilter.ALL);
        drawPill(g, fm, layout.filterIncomplete, ST_INC, query.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE);
        drawPill(g, fm, layout.filterComplete, ST_COMP, query.statusFilter == TaskListQuery.StatusFilter.COMPLETE);

        cursorY += rowH + 6;

        // ================================
        // Row 5: Tier scope chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, TIER_LABEL, leftPad);

        final String T_THIS = "This Tier [" + activeTierLabel + "]";
        final String T_ALL = "All Tiers";

        int availableTier = (rowX + rowW - rightPad) - pillsStartX;

        int wThis = pillWidth(fm, T_THIS, pillPadX, 70, availableTier);
        int wAllT = pillWidth(fm, T_ALL, pillPadX, 70, availableTier);

        int tx = pillsStartX;
        layout.filterTierThis.setBounds(tx, rowTop, wThis, rowH);
        tx += wThis + chipGap;

        layout.filterTierAll.setBounds(tx, rowTop, wAllT, rowH);

        drawTierScopePill(g, fm, layout.filterTierThis, T_THIS, query.tierScope == TaskListQuery.TierScope.THIS_TIER);
        drawPill(g, fm, layout.filterTierAll, T_ALL, query.tierScope == TaskListQuery.TierScope.ALL_TIERS);

        cursorY += rowH + 10;

// ================================
// Row 6: Sort header + applied state
// ================================
        layout.sortExpanded = true;
        layout.sortHeaderBounds.setBounds(0, 0, 0, 0);
        g.setColor(uiGold);
        g.drawString("Sort", rowX + leftPad, cursorY);
        drawHeaderRule(g, rowX + leftPad + fm.stringWidth("Sort") + 8, cursorY - fm.getAscent() + fm.getHeight() / 2, rowX + rowW);

        cursorY += fm.getHeight() + 2;
        boolean hasActiveSorts = query.sortByCompletion || query.sortByTier || query.sortByDate || query.sortByTimeTicks;
        drawClearLinkLine(g, fm, hasActiveSorts, layout.clearSort, rowX + leftPad, rowW - leftPad, cursorY, mouseX, mouseY);
        cursorY += fm.getHeight() + 6;

// ================================
// Row 7: Sort chips (filters-style: label cell + chips only)
// ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, SORT_LABEL, leftPad);

        String completionText = query.sortByCompletion
                ? (query.completedFirst ? "Completed first" : "Incomplete first")
                : "Status";

        String tierText = query.sortByTier
                ? (query.easyTierFirst ? "Easy tier first" : "Master tier first")
                : "Tier";

        String dateText = query.sortByDate
                ? (query.newestFirst ? "Most recent first" : "Oldest first")
                : "Completion date";

// enabled rules
        final boolean completionDisabled = query.statusFilter != TaskListQuery.StatusFilter.ALL;
        final boolean tierEnabledScope = query.tierScope == TaskListQuery.TierScope.ALL_TIERS;
        final boolean dateEnabledScope = query.statusFilter == TaskListQuery.StatusFilter.COMPLETE;

        layout.hoverTooltipText = null;

        if (mouseX >= 0 && mouseY >= 0)
        {
            if (completionDisabled && layout.sortCompletion.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"Status\" filter currently applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortCompletion);
            }
            else if (!tierEnabledScope && layout.sortTier.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"All Tiers\" filter must be applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortTier);
            }
            else if (!dateEnabledScope && layout.sortDate.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"Complete\" filter must be applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortDate);
            }
            else if (!dateEnabledScope && layout.sortTimeTicks.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"Complete\" filter must be applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortTimeTicks);
            }
        }

        int availableSort = (rowX + rowW - rightPad) - pillsStartX;
        final int minW = 80;

        String timeTicksText = !query.sortByTimeTicks
                ? "Time spent"
                : (query.longestFirst ? "Longest first" : "Shortest first");

// Desired widths
        String completionMax =
                (fm.stringWidth("Incomplete first") >= fm.stringWidth("Status"))
                        ? "Incomplete first" : "Status";
        String tierMax =
                (fm.stringWidth("Master tier first") >= fm.stringWidth("Easy tier first"))
                        ? "Master tier first" : "Easy tier first";
        String dateMax =
                (fm.stringWidth("Most recent first") >= fm.stringWidth("Completion date"))
                        ? "Most recent first" : "Completion date";
        String timeMax =
                (fm.stringWidth("Longest first") >= fm.stringWidth("Time spent"))
                        ? "Longest first" : "Time spent";

        int columnW = Math.max(minW, (availableSort - chipGap) / 2);
        int wCompletion = Math.min(columnW, pillWidth(fm, completionMax, pillPadX, minW, columnW));
        int wTier = Math.min(columnW, pillWidth(fm, tierMax, pillPadX, minW, columnW));
        int wDate = Math.min(columnW, pillWidth(fm, dateMax, pillPadX, minW, columnW));
        int wTimeTicks = Math.min(columnW, pillWidth(fm, timeMax, pillPadX, minW, columnW));

        int sx2 = pillsStartX;
        layout.sortCompletion.setBounds(sx2, rowTop, wCompletion, rowH);
        sx2 += columnW + chipGap;

        layout.sortTier.setBounds(sx2, rowTop, wTier, rowH);

        int secondSortRowTop = rowTop + rowH + 6;
        layout.sortDate.setBounds(pillsStartX, secondSortRowTop, wDate, rowH);

        layout.sortTimeTicks.setBounds(pillsStartX + columnW + chipGap, secondSortRowTop, wTimeTicks, rowH);
        layout.sortReset.setBounds(0, 0, 0, 0);

        drawBracketMetaPill(g, fm, layout.sortCompletion, completionText, query.sortByCompletion, !completionDisabled);
        drawPill(g, fm, layout.sortTier, tierText, query.sortByTier, tierEnabledScope);
        drawPill(g, fm, layout.sortDate, dateText, query.sortByDate, dateEnabledScope);
        drawPill(g, fm, layout.sortTimeTicks, timeTicksText, query.sortByTimeTicks, dateEnabledScope);

        cursorY += (rowH * 2) + 12;

        // ================================
        // Row 8: "See New Tasks" button (session-only, shown only when new tasks exist)
        // ================================
        if (hasNewTasks) {
            int newRowTop = cursorY - fm.getAscent();
            int newRowH = rowHeight + 6;
            int newBtnW = Math.min(rowW, Math.max(150, rowW / 2));
            layout.filterNewTasks.setBounds(rowX, newRowTop, newBtnW, newRowH);

            boolean active = query.showNewTasksFilter;
            drawBevelBox(g, layout.filterNewTasks, active ? pillOnBg : pillOffBg, uiEdgeLight, uiEdgeDark);
            g.setColor(uiGold);
            g.drawRect(layout.filterNewTasks.x, layout.filterNewTasks.y, layout.filterNewTasks.width, layout.filterNewTasks.height);

            String btnLabel = active ? "Showing New Tasks" : "See New Tasks";
            g.setColor(active ? uiText : uiGold);
            int bw = fm.stringWidth(btnLabel);
            g.drawString(truncateToWidth(btnLabel, fm, newBtnW - leftPad * 2),
                    layout.filterNewTasks.x + (layout.filterNewTasks.width - Math.min(bw, newBtnW - leftPad * 2)) / 2,
                    centeredTextBaseline(layout.filterNewTasks, fm));

            int helpSize = Math.min(16, newRowH - 4);
            int helpX = layout.filterNewTasks.x + layout.filterNewTasks.width + 6;
            int helpY = newRowTop + (newRowH - helpSize) / 2;
            layout.filterNewTasksHelp.setBounds(helpX, helpY, helpSize, helpSize);
            drawHelpIcon(g, fm, layout.filterNewTasksHelp);
            if (layout.filterNewTasksHelp.contains(mouseX, mouseY))
            {
                drawTooltipRight(g, fm, "New tasks have been added since your last login", layout.filterNewTasksHelp);
            }

            cursorY += newRowH + 4;
        } else {
            layout.filterNewTasks.setBounds(0, 0, 0, 0);
            layout.filterNewTasksHelp.setBounds(0, 0, 0, 0);
        }

        if (layout.hoverTooltipText != null)
        {
            drawTooltip(g, fm, layout.hoverTooltipText, layout.hoverTooltipAnchor);
        }

        return cursorY;

    }

    // ================================
    // Helpers
    // ================================
    private static final class Row
    {
        final int top;
        final int h;
        final int baseline;
        final int nextY;

        private Row(int top, int h, int baseline, int nextY)
        {
            this.top = top;
            this.h = h;
            this.baseline = baseline;
            this.nextY = nextY;
        }
    }

    private void drawLabelCell(Graphics2D g, FontMetrics fm, int rowX, int rowTop, int labelColW, int rowH, String label, int leftPad)
    {
        // Plain label — no box, no bevel, just gold text
        int baseline = rowTop + ((rowH - fm.getHeight()) / 2) + fm.getAscent();
        g.setColor(withAlpha(uiGold, 160));
        g.drawString(label, rowX + leftPad, baseline);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on)
    {
        drawPill(g, fm, bounds, text, on, true);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on, boolean enabled)
    {
        Color bg;
        if (!enabled)
        {
            // “disabled” look: use off bg but dimmer
            bg = withAlpha(pillOffBg, 160);
        }
        else
        {
            bg = on ? pillOnBg : pillOffBg;
        }

        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        int outlineAlpha = !enabled ? 30 : (on ? 200 : 60);
        g.setColor(withAlpha(uiGold, outlineAlpha));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setColor(!enabled ? withAlpha(uiTextDim, 160) : (on ? uiText : uiTextDim));

        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);
        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }


    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color light, Color dark)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, dark, light);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private Color withAlpha(Color c, int a)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    private int clamp(int v)
    {
        return Math.max(0, Math.min(255, v));
    }

    // Blink timing: 500ms on, 500ms off
    private static final long CARET_BLINK_MS = 500L;

    private boolean isCaretVisible()
    {
        return (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0;
    }

    private int pillWidth(FontMetrics fm, String text, int pillPadX, int minW, int maxW)
    {
        int w = fm.stringWidth(text) + (pillPadX * 2);
        w = Math.max(minW, w);
        w = Math.min(maxW, w);
        return w;
    }

    private void drawTierScopePill(Graphics2D g, FontMetrics fm, Rectangle bounds, String fullText, boolean on)
    {
        int bracketIdx = fullText.indexOf('[');
        if (bracketIdx < 0)
        {
            drawPill(g, fm, bounds, fullText, on);
            return;
        }

        String main = fullText.substring(0, bracketIdx);
        String meta = fullText.substring(bracketIdx);

        Color bg = on ? pillOnBg : pillOffBg;
        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        g.setColor(on ? withAlpha(uiGold, 200) : withAlpha(uiGold, 60));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        int mainW = fm.stringWidth(main);
        int metaW = fm.stringWidth(meta);
        int totalW = mainW + metaW;

        int tx = bounds.x + (bounds.width - totalW) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(on ? uiText : uiTextDim);
        g.drawString(main, tx, ty);

        Color metaColor = on ? withAlpha(uiText, 150) : withAlpha(uiTextDim, 160);
        g.setColor(metaColor);
        g.drawString(meta, tx + mainW, ty);
    }

    private void drawBracketMetaPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String fullText, boolean on, boolean enabled)
    {
        int bracketIdx = fullText.indexOf('[');
        if (bracketIdx < 0)
        {
            drawPill(g, fm, bounds, fullText, on, enabled);
            return;
        }

        String main = fullText.substring(0, bracketIdx);
        String meta = fullText.substring(bracketIdx);

        // background + outline mimic drawPill(enabled)
        Color bg;
        if (!enabled)
        {
            bg = withAlpha(pillOffBg, 160);
        }
        else
        {
            bg = on ? pillOnBg : pillOffBg;
        }

        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        int outlineAlpha = !enabled ? 30 : (on ? 200 : 60);
        g.setColor(withAlpha(uiGold, outlineAlpha));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // centered main+meta
        int mainW = fm.stringWidth(main);
        int metaW = fm.stringWidth(meta);
        int totalW = mainW + metaW;

        int tx = bounds.x + (bounds.width - totalW) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        // main text color follows enabled/on
        Color mainColor = !enabled ? withAlpha(uiTextDim, 160) : (on ? uiText : uiTextDim);
        g.setColor(mainColor);
        g.drawString(main, tx, ty);

        // meta is always dimmer than main (and extra dim when disabled)
        Color metaColor;
        if (!enabled)
        {
            metaColor = withAlpha(uiTextDim, 150);
        }
        else
        {
            metaColor = on ? withAlpha(uiText, 150) : withAlpha(uiTextDim, 160);
        }

        g.setColor(metaColor);
        g.drawString(meta, tx + mainW, ty);
    }

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, Rectangle anchor)
    {
        int padX = 8;
        int padY = 3;

        int tw = fm.stringWidth(text);
        int th = fm.getHeight();

        int w = tw + padX * 2;
        int h = th + padY * 2;

        // Position: straddle the pill edge so disabled-sort hints stay attached to the button.
        int x = anchor.x + (anchor.width - w) / 2;
        int y = anchor.y + anchor.height - Math.max(6, h / 2);

        // Clamp inside panel a bit (optional)
        x = Math.max(4, x);

        Rectangle r = new Rectangle(x, y, w, h);

        g.setColor(uiTextDim);
        int baseline = centeredTextBaseline(r, fm);
        g.drawString(text, r.x + padX, baseline);
    }

    private void drawTooltipRight(Graphics2D g, FontMetrics fm, String text, Rectangle anchor)
    {
        int padX = 8;
        int padY = 3;

        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        Rectangle r = new Rectangle(
                anchor.x + anchor.width + 5,
                anchor.y + (anchor.height - (th + padY * 2)) / 2,
                tw + padX * 2,
                th + padY * 2);

        g.setColor(uiTextDim);
        int baseline = centeredTextBaseline(r, fm);
        g.drawString(text, r.x + padX, baseline);
    }

    private void drawHeaderRule(Graphics2D g, int x1, int y, int x2)
    {
        if (x2 <= x1)
        {
            return;
        }

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(x1, y, x2, y);
    }

    private void drawClearLinkLine(
            Graphics2D g,
            FontMetrics fm,
            boolean canClear,
            Rectangle clearBounds,
            int x,
            int width,
            int baselineY,
            int mouseX,
            int mouseY
    )
    {
        final String clearLabel = "[clear]";
        int clearW = fm.stringWidth(clearLabel) + 10;

        if (canClear && width > clearW)
        {
            int clearX = x;
            int clearTop = baselineY - fm.getAscent() - 1;
            clearBounds.setBounds(clearX, clearTop, clearW, fm.getHeight() + 2);
            boolean hovered = clearBounds.contains(mouseX, mouseY);
            g.setColor(new Color(
                    uiTextDim.getRed(),
                    uiTextDim.getGreen(),
                    uiTextDim.getBlue(),
                    hovered ? 230 : 180
            ));
            g.drawString(clearLabel, clearX + 5, baselineY);
        }
        else
        {
            clearBounds.setBounds(0, 0, 0, 0);
        }
    }

    private void drawHelpIcon(Graphics2D g, FontMetrics fm, Rectangle bounds)
    {
        if (QUESTION_ICON != null)
        {
            g.drawImage(QUESTION_ICON, bounds.x, bounds.y, bounds.width, bounds.height, null);
            return;
        }

        g.setColor(withAlpha(uiTextDim, 180));
        g.drawOval(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        String mark = "?";
        int tx = bounds.x + (bounds.width - fm.stringWidth(mark)) / 2;
        int ty = centeredTextBaseline(bounds, fm);
        g.drawString(mark, tx, ty);
    }

    private static BufferedImage loadQuestionIconSafe()
    {
        try (InputStream in = TaskControlsRenderer.class.getResourceAsStream("/icons/notifications/OSRS_question.png"))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

}
