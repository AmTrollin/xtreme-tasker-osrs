package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import net.runelite.client.ui.FontManager;

import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;
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

    private final int panelPadding;
    private final int rowHeight;

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
            int panelPadding,
            int rowHeight,
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
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;

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
        final int leftPad = 8;
        final int rightPad = 8;

        final int chipGap = 6;           // blank space between chips
        final int pillPadX = 10;          // inside-pill horizontal padding

        // ================================
        // Row 1: Search (full width)
        // ================================
        cursorY += 5; // small gap above search
        int searchRowTop = cursorY - fm.getAscent();
        int searchRowH = rowHeight + 10;

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
            g.setColor(withAlpha(uiGold, 90));
            g.fillRect(sx, selTop, Math.max(1, ex - sx), selH);
        }

        boolean caretOn = query.searchFocused && isCaretVisible();
        String caretText = caretOn ? (shown + "|") : shown;
        g.drawString(truncateToWidth(caretText, fm, layout.searchBox.width - 16), textX, baseY);

        // extra padding below search (you wanted this)
        cursorY += searchRowH + 14;

        // ================================
        // Row 2: "See New Tasks" button (session-only, shown only when new tasks exist)
        // ================================
        if (hasNewTasks) {
            int newRowTop = cursorY - fm.getAscent();
            int newRowH = rowHeight + 6;
            int helpSize = Math.min(16, newRowH - 4);
            int helpGap = 6;
            int newBtnW = Math.min(rowW - helpGap - helpSize, Math.max(150, rowW / 2));
            int groupX = rowX + (rowW - newBtnW) / 2;

            layout.filterNewTasks.setBounds(groupX, newRowTop, newBtnW, newRowH);

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

            int helpX = layout.filterNewTasks.x + layout.filterNewTasks.width + helpGap;
            int helpY = newRowTop + (newRowH - helpSize) / 2;
            layout.filterNewTasksHelp.setBounds(helpX, helpY, helpSize, helpSize);
            drawHelpIcon(g, fm, layout.filterNewTasksHelp);
            if (layout.filterNewTasksHelp.contains(mouseX, mouseY))
            {
                drawTooltipBelowRightAligned(g, fm, "New tasks have been added since your last login", layout.filterNewTasksHelp, panelX + panelW + 5);
            }

            cursorY += newRowH + fm.getHeight() + 8;
        } else {
            layout.filterNewTasks.setBounds(0, 0, 0, 0);
            layout.filterNewTasksHelp.setBounds(0, 0, 0, 0);
        }

        // ================================
        // Row 3: Filters header + applied state
        // ================================
        cursorY += 6;
        layout.filtersExpanded = true;
        layout.filtersHeaderBounds.setBounds(0, 0, 0, 0);
        FontMetrics bodyFm = fm;
        java.awt.Font savedHeaderFont = g.getFont();
        java.awt.Font sectionHeaderFont = net.runelite.client.ui.FontManager.getRunescapeBoldFont().deriveFont(java.awt.Font.BOLD, 16f);
        g.setFont(sectionHeaderFont);
        FontMetrics headerFm = g.getFontMetrics();
        g.setColor(uiGold);
        g.drawString("Filters", rowX + leftPad, cursorY);
        drawHeaderClearLink(g, headerFm, bodyFm, layout.clearFilters, rowX, rowW, leftPad, cursorY, "Filters", mouseX, mouseY);
        g.setFont(savedHeaderFont);
        fm = bodyFm;

        cursorY += headerFm.getHeight() + 3;

        // ================================
        // Rows 3-5: Filter chips
        // ================================
        int rowH = rowHeight + 6;
        int rowTop = 0;
        rowTop = cursorY - fm.getAscent();

        drawLabelCell(g, fm, rowX, rowTop, rowW, rowH, SOURCE_LABEL, leftPad);
        cursorY += rowH - 2;
        rowTop = cursorY - fm.getAscent();

        final String SRC_ALL = "All";
        final String SRC_CA = "CAs";
        final String SRC_CL = "CLOGs";
        final String SRC_DA = "ADs";

        int availableSource = rowW - leftPad - rightPad;

        int wAll = pillWidth(fm, SRC_ALL, pillPadX, 42, availableSource);
        int wCA = pillWidth(fm, SRC_CA, pillPadX, 42, availableSource);
        int wCL = pillWidth(fm, SRC_CL, pillPadX, 52, availableSource);
        int wDA = pillWidth(fm, SRC_DA, pillPadX, 42, availableSource);

        int sx = rightAlignedX(rowX, rowW, rightPad, chipGap, wAll, wCA, wCL, wDA);
        layout.filterSourceAll.setBounds(sx, rowTop, wAll, rowH);
        sx += wAll + chipGap;

        layout.filterCA.setBounds(sx, rowTop, wCA, rowH);
        sx += wCA + chipGap;

        layout.filterCL.setBounds(sx, rowTop, wCL, rowH);
        sx += wCL + chipGap;

        layout.filterDA.setBounds(sx, rowTop, wDA, rowH);

        boolean sourceAll = query.isSourceAllSelected();
        drawPill(g, fm, layout.filterSourceAll, SRC_ALL, sourceAll);
        drawPill(g, fm, layout.filterCA, SRC_CA, !sourceAll && query.sourceCASelected);
        drawPill(g, fm, layout.filterCL, SRC_CL, !sourceAll && query.sourceClogsSelected);
        drawPill(g, fm, layout.filterDA, SRC_DA, !sourceAll && query.sourceDasSelected);

        cursorY += rowH + 8;

        // ================================
        // Row 4: Status chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, rowW, rowH, STATUS_LABEL, leftPad);
        cursorY += rowH - 2;
        rowTop = cursorY - fm.getAscent();

        final String ST_ALL = "All";
        final String ST_INC = "Incomplete";
        final String ST_COMP = "Complete";

        int availableStatus = rowW - leftPad - rightPad;

        int wAllS = pillWidth(fm, ST_ALL, pillPadX, 42, availableStatus);
        int wIncS = pillWidth(fm, ST_INC, pillPadX, 70, availableStatus);
        int wCompS = pillWidth(fm, ST_COMP, pillPadX, 70, availableStatus);

        int stx = rightAlignedX(rowX, rowW, rightPad, chipGap, wAllS, wIncS, wCompS);
        layout.filterStatusAll.setBounds(stx, rowTop, wAllS, rowH);
        stx += wAllS + chipGap;

        layout.filterIncomplete.setBounds(stx, rowTop, wIncS, rowH);
        stx += wIncS + chipGap;

        layout.filterComplete.setBounds(stx, rowTop, wCompS, rowH);

        drawPill(g, fm, layout.filterStatusAll, ST_ALL, query.statusFilter == TaskListQuery.StatusFilter.ALL);
        drawPill(g, fm, layout.filterIncomplete, ST_INC, query.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE);
        drawPill(g, fm, layout.filterComplete, ST_COMP, query.statusFilter == TaskListQuery.StatusFilter.COMPLETE);

        cursorY += rowH + 8;

        // ================================
        // Row 5: Tier scope chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, rowW, rowH, TIER_LABEL, leftPad);
        cursorY += rowH - 2;
        rowTop = cursorY - fm.getAscent();

        final String T_THIS = "This Tier [" + activeTierLabel + "]";
        final String T_ALL = "All Tiers";

        int availableTier = rowW - leftPad - rightPad;

        int wThis = pillWidth(fm, T_THIS, pillPadX, 70, availableTier);
        int wAllT = pillWidth(fm, T_ALL, pillPadX, 70, availableTier);
        wThis = Math.min(wThis, Math.max(70, availableTier - chipGap - wAllT));

        int tx = rowX + leftPad;
        layout.filterTierThis.setBounds(tx, rowTop, wThis, rowH);
        tx += wThis + chipGap;

        layout.filterTierAll.setBounds(tx, rowTop, wAllT, rowH);

        drawTierScopePill(g, fm, layout.filterTierThis, T_THIS, query.tierScope == TaskListQuery.TierScope.THIS_TIER);
        drawPill(g, fm, layout.filterTierAll, T_ALL, query.tierScope == TaskListQuery.TierScope.ALL_TIERS);

        cursorY += rowH + 20;

        layout.sortDate.setBounds(0, 0, 0, 0);
        layout.sortTimeTicks.setBounds(0, 0, 0, 0);
        layout.sortTier.setBounds(0, 0, 0, 0);
        layout.sortSource.setBounds(0, 0, 0, 0);

        return cursorY;

    }

    // ================================
    // Helpers
    // ================================
    private void drawLabelCell(Graphics2D g, FontMetrics fm, int rowX, int rowTop, int labelColW, int rowH, String label, int leftPad)
    {
        // Plain label — no box, no bevel, just subdued text
        int baseline = rowTop + ((rowH - fm.getHeight()) / 2) + fm.getAscent();
        g.setColor(withAlpha(uiTextDim, 170));
        g.drawString(label, rowX + leftPad, baseline);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on)
    {
        Color bg = on ? pillOnBg : pillOffBg;

        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        int outlineAlpha = on ? 200 : 60;
        g.setColor(withAlpha(uiGold, outlineAlpha));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setColor(on ? uiText : uiTextDim);

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

    private int rightAlignedX(int rowX, int rowW, int rightPad, int chipGap, int... widths)
    {
        int total = 0;
        for (int i = 0; i < widths.length; i++)
        {
            total += widths[i];
            if (i > 0)
            {
                total += chipGap;
            }
        }
        return Math.max(rowX, rowX + rowW - rightPad - total);
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
        int maxTextW = Math.max(0, bounds.width - 8);
        if (totalW > maxTextW)
        {
            String drawText = truncateToWidth(fullText, fm, maxTextW);
            g.setColor(on ? uiText : uiTextDim);
            g.drawString(drawText, bounds.x + Math.max(0, (bounds.width - fm.stringWidth(drawText)) / 2),
                    centeredTextBaseline(bounds, fm));
            return;
        }

        int tx = bounds.x + (bounds.width - totalW) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(on ? uiText : uiTextDim);
        g.drawString(main, tx, ty);

        Color metaColor = on ? withAlpha(uiText, 150) : withAlpha(uiTextDim, 160);
        g.setColor(metaColor);
        g.drawString(meta, tx + mainW, ty);
    }

    private void drawTooltipBelowRightAligned(Graphics2D g, FontMetrics fm, String text, Rectangle anchor, int textRightX)
    {
        int padX = 8;
        int padY = 3;

        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        int w = tw + padX * 2;
        int h = th + padY * 2;
        int x = textRightX - padX - tw;
        int y = anchor.y + anchor.height + 4;

        Rectangle r = new Rectangle(x, y, w, h);

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

        g.setColor(withAlpha(uiGold, 55));
        g.drawLine(x1, y, x2, y);
    }

    private void drawHeaderClearLink(
            Graphics2D g,
            FontMetrics headerFm,
            FontMetrics clearFm,
            Rectangle clearBounds,
            int rowX,
            int rowW,
            int leftPad,
            int baselineY,
            String title,
            int mouseX,
            int mouseY
    )
    {
        final String clearLabel = "[clear]";
        int clearW = clearFm.stringWidth(clearLabel) + 10;
        int clearX = rowX + rowW - clearW;
        int clearBaselineY = baselineY - headerFm.getAscent() + ((headerFm.getHeight() - clearFm.getHeight()) / 2) + clearFm.getAscent();
        int clearTop = clearBaselineY - clearFm.getAscent() - 1;
        clearBounds.setBounds(clearX, clearTop, clearW, clearFm.getHeight() + 2);

        int ruleX1 = rowX + leftPad + headerFm.stringWidth(title) + 10;
        int ruleX2 = clearX - 8;
        drawHeaderRule(g, ruleX1, baselineY - headerFm.getAscent() + headerFm.getHeight() / 2, ruleX2);

        boolean hovered = clearBounds.contains(mouseX, mouseY);
        g.setColor(withAlpha(uiTextDim, hovered ? 230 : 180));
        if (rowW > clearW)
        {
            Font savedFont = g.getFont();
            g.setFont(FontManager.getRunescapeFont());
            g.drawString(clearLabel, clearX + 5, clearBaselineY);
            g.setFont(savedFont);
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
