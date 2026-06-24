package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import net.runelite.api.Skill;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.PANEL_PADDING;

public final class CurrentTabViewRenderer
{
    private final CurrentTabRenderer baseRenderer;
    private final UiPalette palette;
    private final ButtonRenderer buttonRenderer;

    public CurrentTabViewRenderer(CurrentTabRenderer baseRenderer, UiPalette palette)
    {
        this.baseRenderer = baseRenderer;
        this.palette = palette;
        this.buttonRenderer = new ButtonRenderer(palette);
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            CurrentTabState state,
            boolean hasTasksLoaded,
            XtremeTask current,
            boolean currentCompleted,
            boolean rolling,
            Function<TaskTier, String> tierProgressLabel,
            Function<XtremeTask, String> currentLineProvider,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<Skill, BufferedImage> prerequisiteSkillImageProvider,
            Function<MarkerIcon, BufferedImage> prerequisiteMarkerImageProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            Function<Integer, BufferedImage> collectionLogItemImageProvider,
            TaskTier tierForProgress,
            TaskSource currentSource,
            XtremeTaskerConfig.RollSourceFilter rollSourceFilter,
            String rollSkipNotice,
            java.awt.Point mousePoint,
            int scrollOffsetPx,
            int viewportH,
            boolean showTips,
            BufferedImage taskIcon,
            Long taskTimeTicks,
            XtremeTask recentCompletedTask,
            CompletionInfo recentCompletionInfo,
            Long recentTaskTimeTicks,
            boolean canUndoRecentCompletion,
            boolean skipEnabled,
            int skippedTaskCount,
            boolean currentCompletionCriteriaMet,
            boolean keyboardHintsOpen,
            Rectangle keyboardHintsButtonBounds,
            Rectangle keyboardHintsPopupBounds,
            int hoverX,
            int hoverY
    )
    {
        CurrentTabLayout layout = baseRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                hasTasksLoaded,
                current,
                currentCompleted,
                rolling,
                tierProgressLabel,
                currentLineProvider,
                prerequisiteStatusProvider,
                prerequisiteSkillImageProvider,
                prerequisiteMarkerImageProvider,
                collectionLogRequirementPreviewProvider,
                collectionLogItemImageProvider,
                tierForProgress,
                currentSource,
                rollSourceFilter,
                rollSkipNotice,
                mousePoint,
                scrollOffsetPx,
                viewportH,
                showTips,
                taskIcon,
                taskTimeTicks,
                recentCompletedTask,
                recentCompletionInfo,
                recentTaskTimeTicks,
                canUndoRecentCompletion,
                skipEnabled,
                skippedTaskCount
        );

        state.layout().wikiButtonBounds.setBounds(layout.wikiButtonBounds);
        state.layout().rollButtonBounds.setBounds(layout.rollButtonBounds);
        state.layout().completeButtonBounds.setBounds(layout.completeButtonBounds);
        state.layout().skipButtonBounds.setBounds(layout.skipButtonBounds);
        state.layout().undoButtonBounds.setBounds(layout.undoButtonBounds);
        state.layout().viewportBounds.setBounds(layout.viewportBounds);
        state.layout().scrollbarRailBounds.setBounds(layout.scrollbarRailBounds);
        state.layout().scrollbarThumbBounds.setBounds(layout.scrollbarThumbBounds);
        state.layout().totalContentPx = layout.totalContentPx;

        if (rolling)
        {
            state.layout().rollButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().completeButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().skipButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().undoButtonBounds.setBounds(0, 0, 0, 0);
            renderKeyboardHints(g, fm, panelBounds, keyboardHintsOpen, keyboardHintsButtonBounds, keyboardHintsPopupBounds, hoverX, hoverY);
            return;
        }

        boolean showComplete = (current != null) && !currentCompleted;
        String activeText = showComplete ? "Mark complete" : "Roll task";
        Rectangle activeBounds = showComplete ? state.layout().completeButtonBounds : state.layout().rollButtonBounds;

        if (activeBounds.width > 0 && activeBounds.height > 0)
        {
            buttonRenderer.drawPrimaryButton(
                    g,
                    activeBounds,
                    activeText,
                    showComplete && currentCompletionCriteriaMet ? UiPalette.TIER_COMPLETE_GLOW : null);
        }

        if (skipEnabled
                && current != null
                && !currentCompleted
                && state.layout().skipButtonBounds.width > 0
                && state.layout().skipButtonBounds.height > 0)
        {
            buttonRenderer.drawPlainButton(g, state.layout().skipButtonBounds, "Skip");
        }

        if (canUndoRecentCompletion
                && state.layout().undoButtonBounds.width > 0
                && state.layout().undoButtonBounds.height > 0)
        {
            buttonRenderer.drawPlainButton(g, state.layout().undoButtonBounds, "Undo");
        }

        renderKeyboardHints(g, fm, panelBounds, keyboardHintsOpen, keyboardHintsButtonBounds, keyboardHintsPopupBounds, hoverX, hoverY);
    }

    private void renderKeyboardHints(
            Graphics2D g,
            FontMetrics fm,
            Rectangle panelBounds,
            boolean keyboardHintsOpen,
            Rectangle buttonBounds,
            Rectangle popupBounds,
            int hoverX,
            int hoverY
    )
    {
        int innerW = panelBounds.width - (PANEL_PADDING * 2);
        int baselineY = panelBounds.y + panelBounds.height - 12;
        drawKeyboardHintsButton(g, fm, panelBounds.x, baselineY, innerW, buttonBounds, hoverX, hoverY);
        if (keyboardHintsOpen)
        {
            drawKeyboardHintsPopup(g, fm, panelBounds, buttonBounds, popupBounds);
        }
        else
        {
            popupBounds.setBounds(0, 0, 0, 0);
        }
    }

    private void drawKeyboardHintsButton(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int baselineY,
            int innerW,
            Rectangle buttonBounds,
            int hoverX,
            int hoverY
    )
    {
        String label = "[Keyboard hints]";
        int padX = 9;
        int h = fm.getHeight() + 6;
        int w = padX * 2 + fm.stringWidth(label);
        int x = panelX + PANEL_PADDING + (innerW - w) / 2;
        int y = baselineY - fm.getAscent() - 3;
        buttonBounds.setBounds(x, y, w, h);

        boolean hovered = buttonBounds.contains(hoverX, hoverY);
        g.setColor(hovered
                ? new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 230)
                : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 180));
        g.drawString(label, x + padX, baselineY);
    }

    private void drawKeyboardHintsPopup(Graphics2D g, FontMetrics fm, Rectangle panelBounds, Rectangle buttonBounds, Rectangle popupBounds)
    {
        String title = "Keyboard hints";
        String[] lines = {
                "R - roll task",
                "C - complete current task",
                "W - open wiki"
        };

        int pad = 10;
        int contentW = fm.stringWidth(title);
        for (String line : lines)
        {
            contentW = Math.max(contentW, fm.stringWidth(line));
        }

        int w = Math.min(panelBounds.width - (PANEL_PADDING * 2), contentW + pad * 2);
        int h = pad * 2 + fm.getHeight() * (lines.length + 1) + 5;
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = buttonBounds.y - h - 8;
        popupBounds.setBounds(x, y, w, h);

        buttonRenderer.drawBevelBox(g, popupBounds, new Color(45, 36, 24, 248));

        int textX = x + pad;
        int textY = y + pad + fm.getAscent();
        g.setColor(palette.UI_GOLD);
        g.drawString(TextUtils.truncateToWidth(title, fm, w - pad * 2), textX, textY);
        textY += fm.getHeight() + 5;

        g.setColor(new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 190));
        for (String line : lines)
        {
            g.drawString(TextUtils.truncateToWidth(line, fm, w - pad * 2), textX, textY);
            textY += fm.getHeight();
        }
    }
}
