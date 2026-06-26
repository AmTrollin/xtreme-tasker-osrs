package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.models.XtremeTask;
import lombok.RequiredArgsConstructor;
import net.runelite.client.input.MouseWheelListener;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.List;

@RequiredArgsConstructor
public final class OverlayWheelHandler implements MouseWheelListener
{
    private final OverlayInputAccess a;

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
    {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen())
        {
            return e;
        }

        e.consume();
        Point p = e.getPoint();

        double precise = e.getPreciseWheelRotation();
        if (precise == 0.0)
        {
            return e;
        }

        if (a.isSyncMismatchReviewOpen() && a.syncMismatchReviewBounds().contains(p))
        {
            int taskCount = a.syncMismatchVisibleTaskCount();
            a.syncMismatchScroll().onWheel(
                    precise,
                    a.syncMismatchViewportBounds().height,
                    a.syncMismatchRowBlock(),
                    taskCount <= 0 ? 1 : taskCount,
                    null
            );
            a.client().getCanvas().repaint();
            return e;
        }

        // ------------------------------------------
        // DETAILS POPUP scroll (highest priority)
        // ------------------------------------------
        // Requires the popup hooks on OverlayInputAccess (see below).
        if (a.isTaskDetailsOpen() && a.taskDetailsViewportBounds().contains(p))
        {
            if (a.taskDetailsViewportBounds().height <= 0)
            {
                return e;
            }

            int total = a.taskDetailsTotalContentRows();
            a.taskDetailsScroll().onWheel(
                    precise,
                    a.taskDetailsViewportBounds().height,
                    a.taskDetailsRowBlock(),
                    total <= 0 ? 1 : total,
                    null
            );
            return e;
        }


        // TASKS scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskListViewportBounds().contains(p))
        {
            Rectangle vp = a.taskListViewportBounds();
            if (vp.height <= 0)
            {
                return e;
            }

            List<XtremeTask> tasks = a.getSortedTasksForTier(a.activeTier());
            int total = tasks.size();

            a.taskListView().onWheel(
                    precise,
                    vp.height,
                    a.tasksRowBlock(),
                    total
            );

            return e;
        }

        // CURRENT scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT && a.currentViewportBounds().contains(p))
        {
            Rectangle cvp = a.currentViewportBounds();
            if (cvp.height <= 0) return e;
            int logicalRowBlock = com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;
            int totalRows = (a.currentLayout().totalContentPx + logicalRowBlock - 1) / logicalRowBlock;
            a.currentScroll().onWheel(precise, cvp.height, a.currentRowBlock(), totalRows <= 0 ? 1 : totalRows, null);
            return e;
        }

        return e;
    }
}
