package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.enums.*;
import com.amtrollin.xtremetasker.models.*;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.Skill;

public final class CurrentTabViewRenderer
{
    private final CurrentTabRenderer baseRenderer;
    private final ButtonRenderer buttonRenderer;

    public CurrentTabViewRenderer(CurrentTabRenderer baseRenderer, UiPalette palette)
    {
        this.baseRenderer = baseRenderer;
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
            boolean currentCompletionCriteriaMet
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
                canUndoRecentCompletion
        );

        state.layout().wikiButtonBounds.setBounds(layout.wikiButtonBounds);
        state.layout().rollButtonBounds.setBounds(layout.rollButtonBounds);
        state.layout().completeButtonBounds.setBounds(layout.completeButtonBounds);
        state.layout().undoButtonBounds.setBounds(layout.undoButtonBounds);
        state.layout().viewportBounds.setBounds(layout.viewportBounds);
        state.layout().scrollbarRailBounds.setBounds(layout.scrollbarRailBounds);
        state.layout().scrollbarThumbBounds.setBounds(layout.scrollbarThumbBounds);
        state.layout().totalContentPx = layout.totalContentPx;

        if (rolling)
        {
            state.layout().rollButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().completeButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().undoButtonBounds.setBounds(0, 0, 0, 0);
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

        if (canUndoRecentCompletion
                && state.layout().undoButtonBounds.width > 0
                && state.layout().undoButtonBounds.height > 0)
        {
            buttonRenderer.drawPlainButton(g, state.layout().undoButtonBounds, "Undo");
        }
    }
}
