package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import net.runelite.client.ui.FontManager;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.tasklist.TaskListPipeline.safe;

public final class TaskRowsRenderer {
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    // indicator spacing
    private final int statusPipSize;
    private final int statusPipPadLeft;
    private final int taskTextPadLeft;

    // colors
    private final Color rowHoverBg;
    private final Color rowSelectedBg;
    private final Color rowSelectedOutline;
    private final Color rowDoneBg;
    private final Color rowLine;
    private final Color strikeColor;
    private final Color uiText;
    private final Color uiTextDim;
    private final Color pipRing;
    private final Color pipDoneFill;
    private final Color pipDoneRing;
    private final Color uiGold;
    private final Color edgeLight;
    private final Color edgeDark;

    // --- Visual boost (Option 2: more prominent checkbox/pip without changing layout) ---
    // Increase this to make the circle larger. Keep small so it doesn't collide with text.
    private static final int PIP_VISUAL_BOOST_PX = 4; // try 2–6; 4 is a nice "more prominent" bump

    private static final int VIEWPORT_TOP_PAD = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 3;
    private static final int ROW_BADGE_Y_OFFSET = 2;

    public TaskRowsRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            int listRowSpacing,
            int statusPipSize,
            int statusPipPadLeft,
            int taskTextPadLeft,
            Color rowHoverBg,
            Color rowSelectedBg,
            Color rowSelectedOutline,
            Color rowDoneBg,
            Color rowLine,
            Color strikeColor,
            Color uiText,
            Color uiTextDim,
            Color pipRing,
            Color pipDoneFill,
            Color pipDoneRing,
            Color uiGold,
            Color edgeLight,
            Color edgeDark
    ) {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.listRowSpacing = listRowSpacing;
        this.statusPipSize = statusPipSize;
        this.statusPipPadLeft = statusPipPadLeft;
        this.taskTextPadLeft = taskTextPadLeft;

        this.rowHoverBg = rowHoverBg;
        this.rowSelectedBg = rowSelectedBg;
        this.rowSelectedOutline = rowSelectedOutline;
        this.rowDoneBg = rowDoneBg;
        this.rowLine = rowLine;
        this.strikeColor = strikeColor;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
        this.pipRing = pipRing;
        this.pipDoneFill = pipDoneFill;
        this.pipDoneRing = pipDoneRing;
        this.uiGold = uiGold;
        this.edgeLight = edgeLight;
        this.edgeDark = edgeDark;
    }

    public int rowBlock() {
        return rowHeight + listRowSpacing + 2;
    }

    private static String prettyTier(TaskTier t)
    {
        return TaskLabelFormatter.tierLabel(t);
    }

    /**
     * Renders the task list and returns layout containing viewport + per-row bounds.
     *
     * @param selectedIndex        selected index in the tasks list
     * @param scrollOffsetRows     current scroll offset in rows
     * @param hoverMouseX/mouseY   pass RuneLite mouse coordinates (or -1 if none)
     * @param animProgressProvider returns completion "pop" progress [0..1] for task id
     * @param isCompleted          task completion check
     */
    public TaskRowsLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            List<XtremeTask> tasks,
            int selectedIndex,
            int scrollOffsetRows,
            int hoverMouseX,
            int hoverMouseY,
            Function<String, Float> animProgressProvider,
            Function<XtremeTask, Boolean> isCompleted,
            Function<XtremeTask, TaskGroupProgress> groupProgressProvider,
            Function<XtremeTask, Boolean> isNewTask,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider,
            boolean showCompletionMeta,
            boolean showTimeMeta
    ) {
        TaskRowsLayout layout = new TaskRowsLayout();
        layout.rowBounds.clear();

        int viewportX = panelX + panelPadding;
        int viewportY = cursorYBaseline - fm.getAscent();
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - panelPadding;        int viewportW = panelWidth - 2 * panelPadding;
        if (viewportH < 0) viewportH = 0;

        layout.viewportBounds.setBounds(viewportX, viewportY, viewportW, viewportH);

        int rb = rowBlock();
        int visibleRows = (rb <= 0) ? 0 : Math.max(0, viewportH / rb);
        boolean needsScrollbar = tasks.size() > visibleRows && visibleRows > 0 && viewportH > 0;
        int rowW = Math.max(0, viewportW - (needsScrollbar ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0));

        int start = clamp(scrollOffsetRows, Math.max(0, tasks.size() - visibleRows));
        int end = Math.min(tasks.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);

        int drawY = cursorYBaseline + VIEWPORT_TOP_PAD;

        for (int i = start; i < end; i++) {
            XtremeTask task = tasks.get(i);
            TaskGroupProgress progress = groupProgressProvider == null ? null : groupProgressProvider.apply(task);
            boolean completed = progress != null
                    ? progress.isComplete()
                    : isCompleted != null && Boolean.TRUE.equals(isCompleted.apply(task));
            boolean partialGroup = progress != null
                    && progress.isGrouped()
                    && progress.getCompleted() > 0
                    && !progress.isComplete();

            Rectangle rowBounds = new Rectangle(
                    viewportX,
                    (drawY - fm.getAscent()) - 2,
                    rowW,
                    rowHeight + 4
            );
            layout.rowBounds.put(task, rowBounds);

            boolean hovered = (hoverMouseX >= 0 && hoverMouseY >= 0) && rowBounds.contains(hoverMouseX, hoverMouseY);
            boolean selected = (i == selectedIndex);

            if (hovered) {
                g.setColor(rowHoverBg);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            }

            if (selected) {
                g.setColor(rowSelectedBg);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
                g.setColor(rowSelectedOutline);
                g.drawRect(rowBounds.x + 1, rowBounds.y + 1, rowBounds.width - 2, rowBounds.height - 2);
            }

            if (partialGroup || completed) {
                Color rail = completed
                        ? new Color(105, 205, 128, 175)
                        : new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 150);
                g.setColor(rail);
                g.fillRect(rowBounds.x, rowBounds.y + 1, 2, rowBounds.height - 2);
            }

            // pip center aligned with glyph center
            int pipCenterX = rowBounds.x + statusPipPadLeft + (statusPipSize / 2);
            int pipCenterY = drawY - (fm.getAscent() / 2) + (fm.getDescent() / 2);

            int hitSize = Math.max(statusPipSize + 10, 18);
            layout.checkboxBounds.put(task, new Rectangle(
                    pipCenterX - hitSize / 2,
                    pipCenterY - hitSize / 2,
                    hitSize,
                    hitSize
            ));

            // Checkbox hit target (bigger than the visual pip)
            int hitX = pipCenterX - (hitSize / 2);
            int hitY = pipCenterY - (hitSize / 2);

            Rectangle hit = new Rectangle(hitX, hitY, hitSize, hitSize);
            layout.checkboxBounds.put(task, hit);

            float anim = 0f;
            if (animProgressProvider != null && task != null) {
                Float v = animProgressProvider.apply(task.getId());
                anim = v == null ? 0f : v;
            }

            // Draw slightly larger pip for better prominence (visual only)
            drawStatusPip(g, pipCenterX, pipCenterY, completed, partialGroup, anim);

            boolean isNew = isNewTask != null && Boolean.TRUE.equals(isNewTask.apply(task));

            int textX = viewportX + taskTextPadLeft;
            int textMaxW = Math.max(0, rowW - taskTextPadLeft - 10);

            assert task != null;

            // --- Right-side columns: source badge then tier badge, right-aligned ---
            String srcText = task.getSource() != null
                    ? TaskLabelFormatter.shortSource(task.getSource()) : "?";
            String tierText = task.getTier() != null
                    ? TaskLabelFormatter.tierLabel(task.getTier()) : "?";

            // Measure both badges using small font.
            // Tier pill is fixed to the width of the widest tier ("Grandmaster") so all rows align.
            Font smallFont = FontManager.getRunescapeSmallFont();
            FontMetrics sfm = g.getFontMetrics(smallFont);
            final int pillPadX = 7;
            final int pillH = 14;
            final int pillArc = 4;
            final int pillGap = 4;
            int srcW = Math.max(sfm.stringWidth("CA"), Math.max(sfm.stringWidth("CL"), sfm.stringWidth("DA"))) + pillPadX * 2; // fixed width for all sources
            int tierW = sfm.stringWidth("Master") + pillPadX * 2; // fixed width for all tiers
            int newW = sfm.stringWidth("NEW") + pillPadX * 2;
            int progressW = (progress != null && progress.isGrouped()) ? sfm.stringWidth(progress.label()) + 6 : 0;
            String metaText = rowMetaText(task, completionInfoProvider, taskTicksProvider, showCompletionMeta, showTimeMeta);
            int metaW = metaText == null ? 0 : sfm.stringWidth(metaText) + 6;
            int rightColW = metaW + (metaW > 0 ? pillGap : 0)
                    + progressW + (progressW > 0 ? pillGap : 0)
                    + srcW + pillGap + tierW + (isNew ? pillGap + newW : 0) + 6; // 6px margin from row right edge

            int nameMaxW = Math.max(0, textMaxW - rightColW);
            String taskName = TextUtils.truncateToWidth(safe(task.getName()), fm, nameMaxW);

            g.setColor(completed
                    ? new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 220)
                    : uiText);
            g.drawString(taskName, textX, drawY);

            // Draw tier pill, then source pill, right-aligned
            int pillRightEdge = viewportX + rowW - 6;
            int pillTop = drawY - fm.getAscent() + (fm.getHeight() - pillH) / 2 + ROW_BADGE_Y_OFFSET;

            // source (rightmost)
            int srcX = pillRightEdge - srcW;
            g.setFont(smallFont);
            g.setColor(new Color(40, 30, 15, 190));
            g.fillRoundRect(srcX, pillTop, srcW, pillH, pillArc, pillArc);
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 60));
            g.drawRoundRect(srcX, pillTop, srcW - 1, pillH - 1, pillArc, pillArc);
            g.setColor(new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 210));
            g.drawString(srcText, srcX + (srcW - sfm.stringWidth(srcText)) / 2,
                    pillTop + ((pillH - sfm.getHeight()) / 2) + sfm.getAscent());

            // tier (left of source)
            int tierX = srcX - pillGap - tierW;
            g.setColor(new Color(40, 30, 15, 190));
            g.fillRoundRect(tierX, pillTop, tierW, pillH, pillArc, pillArc);
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 60));
            g.drawRoundRect(tierX, pillTop, tierW - 1, pillH - 1, pillArc, pillArc);
            g.setColor(tierTextColor(task.getTier()));
            g.drawString(tierText, tierX + (tierW - sfm.stringWidth(tierText)) / 2,
                    pillTop + ((pillH - sfm.getHeight()) / 2) + sfm.getAscent());

            g.setFont(fm.getFont()); // restore row font

            int leftBadgeX = tierX;
            if (progress != null && progress.isGrouped()) {
                g.setFont(smallFont);
                String progressText = progress.label();
                int progressX = tierX - pillGap - progressW;
                g.setColor(progress.isComplete()
                        ? new Color(120, 200, 140, 225)
                        : partialGroup
                        ? new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 230)
                        : new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 225));
                g.drawString(progressText, progressX + 3,
                        pillTop + ((pillH - sfm.getHeight()) / 2) + sfm.getAscent());
                g.setFont(fm.getFont());
                leftBadgeX = progressX;
            }

            if (metaText != null) {
                g.setFont(smallFont);
                int metaX = leftBadgeX - pillGap - metaW;
                g.setColor(new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 210));
                g.drawString(metaText, metaX + 3,
                        pillTop + ((pillH - sfm.getHeight()) / 2) + sfm.getAscent());
                g.setFont(fm.getFont());
                leftBadgeX = metaX;
            }

            // NEW marker: subtle gold text, no filled badge.
            if (isNew) {
                int newX = leftBadgeX - pillGap - newW;
                g.setFont(smallFont);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 70));
                g.drawRoundRect(newX, pillTop, newW - 1, pillH - 1, pillArc, pillArc);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 210));
                g.drawString("NEW", newX + (newW - sfm.stringWidth("NEW")) / 2,
                        pillTop + ((pillH - sfm.getHeight()) / 2) + sfm.getAscent());
                g.setFont(fm.getFont());
            }

            if (completed) {
                int textW = fm.stringWidth(taskName);
                int strikeY = drawY - (fm.getAscent() * 3 / 5);

                g.setColor(strikeColor);
                g.drawLine(textX, strikeY, textX + textW, strikeY);
            }

            drawY += rb;
        }

        // Shrink viewportBounds to actual content height so no empty gap shows below last row
        if (end > start)
        {
            int lastRowBottom = (drawY - rb) - fm.getAscent() + rowHeight + 4;
            int contentH = Math.max(0, lastRowBottom - viewportY);
            if (contentH < layout.viewportBounds.height)
            {
                layout.viewportBounds.height = contentH;
            }
        }
        else if (end == start)
        {
            layout.viewportBounds.height = 0;
        }

        g.setClip(oldClip);

        // scrollbar if needed
        if (needsScrollbar) {
            layout.scrollbarRailBounds.setBounds(scrollbarRailBounds(layout.viewportBounds));
            layout.scrollbarThumbBounds.setBounds(scrollbarThumbBounds(tasks.size(), visibleRows, start, layout.viewportBounds));
            drawScrollbar(g, tasks.size(), visibleRows, start, layout.viewportBounds);
        }

        return layout;
    }

    private static Color tierTextColor(TaskTier tier) {
        if (tier == null) return new Color(180, 180, 180, 210);
        switch (tier) {
            case EASY:        return new Color(100, 220,  80, 230); // green
            case MEDIUM:      return new Color(230, 150,  40, 230); // orange
            case HARD:        return new Color(220,  70,  60, 230); // red
            case ELITE:       return new Color(160,  35,  35, 230); // dark red
            case MASTER:
            case GRANDMASTER: return new Color(170,  80, 210, 230); // purple
            default:          return new Color(180, 180, 180, 210);
        }
    }

    private void drawStatusPip(Graphics2D g, int cx, int cy, boolean done, boolean partial, float animProgress) {
        // Visual size boost (does not change layout spacing)
        int drawSize = Math.max(6, statusPipSize + PIP_VISUAL_BOOST_PX);
        int r = drawSize / 2;

        int x = cx - r;
        int y = cy - r;

        // Slightly thicker ring for readability: draw twice (offset by 1px)
        Color doneRing = new Color(120, 200, 140, 230);
        Color doneFill = new Color(78, 160, 96, 235);
        g.setColor(done ? doneRing : (partial ? new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 190) : pipRing));
        g.drawOval(x, y, drawSize, drawSize);
        g.drawOval(x + 1, y + 1, drawSize - 2, drawSize - 2);

        // Add a faint inner ring when not done (makes "toggle target" feel more obvious)
        if (!done) {
            if (partial) {
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 120));
                g.fillArc(x + 3, y + 3, drawSize - 6, drawSize - 6, 90, -180);
                return;
            }
            g.setColor(new Color(pipRing.getRed(), pipRing.getGreen(), pipRing.getBlue(), 60));
            g.drawOval(x + 2, y + 2, drawSize - 4, drawSize - 4);
            return;
        }

        float scale = 1.0f;
        int alphaBoost = 0;
        if (animProgress > 0f) {
            scale = 1.0f + (0.18f * (1.0f - animProgress));
            alphaBoost = (int) (60 * (1.0f - animProgress));
        }

        int fillBase = Math.max(1, drawSize - 2);
        int fillSize = Math.max(1, Math.round(fillBase * scale));
        int fx = cx - (fillSize / 2);
        int fy = cy - (fillSize / 2);

        Color fill = new Color(
                doneFill.getRed(),
                doneFill.getGreen(),
                doneFill.getBlue(),
                clamp(doneFill.getAlpha() + alphaBoost, 255)
        );

        g.setColor(fill);
        g.fillOval(fx, fy, fillSize, fillSize);

        // check mark (scaled to drawSize)
        g.setColor(new Color(30, 25, 18, 220));

        // Use proportions based on drawSize so it stays centered and crisp
        int left = x;
        int top = y;

        int x1 = left + Math.max(2, drawSize / 6);
        int y1 = top + (drawSize / 2) + Math.max(1, drawSize / 12);

        int x2 = left + (drawSize / 2) - Math.max(1, drawSize / 10);
        int y2 = top + drawSize - Math.max(3, drawSize / 5);

        int x3 = left + drawSize - Math.max(2, drawSize / 6);
        int y3 = top + Math.max(2, drawSize / 6);

        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);
    }

    private static String rowMetaText(
            XtremeTask task,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider,
            boolean showCompletionMeta,
            boolean showTimeMeta)
    {
        if (task == null)
        {
            return null;
        }

        if (showCompletionMeta && completionInfoProvider != null)
        {
            CompletionInfo info = completionInfoProvider.apply(task);
            if (info == null)
            {
                return null;
            }
            if (info.timestamp <= 0)
            {
                return "date ?";
            }
            return new SimpleDateFormat("MMM d").format(new Date(info.timestamp));
        }

        if (showTimeMeta && taskTicksProvider != null)
        {
            Long ticks = taskTicksProvider.apply(task);
            if (ticks == null || ticks <= 0)
            {
                return "time ?";
            }
            return formatDuration(Math.round(ticks * 0.6));
        }

        return null;
    }

    private static String formatDuration(long seconds)
    {
        if (seconds < 60)
        {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes < 60)
        {
            return remSeconds > 0 ? minutes + "m " + remSeconds + "s" : minutes + "m";
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        return remMinutes > 0 ? hours + "h " + remMinutes + "m" : hours + "h";
    }

    public void drawScrollbar(Graphics2D g, int totalRows, int visibleRows, int offsetRows, Rectangle viewport) {
        Rectangle rail = scrollbarRailBounds(viewport);

        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(rail.x, rail.y, rail.width, rail.height);

        Rectangle thumb = scrollbarThumbBounds(totalRows, visibleRows, offsetRows, viewport);
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200));

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    public Rectangle scrollbarRailBounds(Rectangle viewport) {
        return new Rectangle(
                viewport.x + viewport.width - SCROLLBAR_WIDTH,
                viewport.y,
                SCROLLBAR_WIDTH,
                viewport.height
        );
    }

    public Rectangle scrollbarThumbBounds(int totalRows, int visibleRows, int offsetRows, Rectangle viewport) {
        Rectangle rail = scrollbarRailBounds(viewport);
        if (totalRows <= 0 || visibleRows <= 0 || rail.height <= 0) {
            return new Rectangle(rail.x, rail.y, Math.max(0, rail.width - 1), 0);
        }

        float fracVisible = (float) visibleRows / (float) totalRows;
        int thumbH = Math.min(rail.height, Math.max(12, Math.round(rail.height * fracVisible)));

        int maxOffset = Math.max(1, totalRows - visibleRows);
        float fracOffset = (float) clamp(offsetRows, maxOffset) / (float) maxOffset;

        int thumbY = rail.y + (int) ((rail.height - thumbH) * fracOffset);
        return new Rectangle(rail.x, thumbY, Math.max(0, rail.width - 1), Math.max(0, thumbH - 1));
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        drawBevelBoxLogic(g, r, fill, edgeDark, edgeLight);
    }

    /**
     * Draws a source badge. Canonical shared implementation — all HUD/panel locations should call this.
     * Sets font to RunescapeSmallFont internally and restores the previous font before returning.
     * @return the width of the badge drawn
     */
    public static int drawSourceBadge(Graphics2D g, int x, int yTop, String text,
            Color edgeDark, Color edgeLight, Color gold, Color uiText) {
        Font savedFont = g.getFont();
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        final int padX = 7;
        final int h = 18;
        final int arc = 6;
        int textW = fm.stringWidth(text);
        int w = Math.max(24, textW + padX * 2);
        // Flat pill — no bevel, no 3D effect
        g.setColor(new Color(60, 48, 28, 200));
        g.fillRoundRect(x, yTop, w, h, arc, arc);
        // Single thin dim border
        g.setColor(new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 70));
        g.drawRoundRect(x, yTop, w - 1, h - 1, arc, arc);
        // Label text
        g.setColor(new Color(uiText.getRed(), uiText.getGreen(), uiText.getBlue(), 210));
        g.drawString(text, x + (w - textW) / 2, yTop + ((h - fm.getHeight()) / 2) + fm.getAscent());
        g.setFont(savedFont);
        return w;
    }

    public static void drawBevelBoxLogic(Graphics2D g, Rectangle r, Color fill, Color edgeDark, Color edgeLight) {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setColor(edgeDark);
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(edgeLight);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        g.setColor(edgeDark);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
