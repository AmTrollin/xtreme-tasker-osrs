package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.enums.*;
import com.amtrollin.xtremetasker.models.*;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasks.models.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskDetailsLayoutTest
{
    @Test
    public void combatAndDiaryRequirementsSupportCompletedDropdown() throws Exception
    {
        for (TaskSource source : new TaskSource[] {TaskSource.COMBAT_ACHIEVEMENT, TaskSource.DIARY_ACHIEVEMENT})
        {
            TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
            popup.open(new XtremeTask("task", "Task", source, TaskTier.EASY));
            render(popup);
            int collapsed = popup.totalContentRows();
            Rectangle toggle = rectangle(popup, "requirementsToggleBounds");
            assertFalse(toggle.isEmpty());
            assertTrue(popup.toggleSectionAt(center(toggle)));
            render(popup);
            assertTrue(popup.totalContentRows() > collapsed);
            assertFalse(popup.markIncompleteBounds().isEmpty());
        }
    }

    @Test
    public void singleTaskKeepsCompletionDetailsWithoutHistoryDropdown() throws Exception
    {
        TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
        XtremeTask task = new XtremeTask("mask", "Get a black mask", TaskSource.COLLECTION_LOG,
                TaskTier.EASY, 8901, null, null, "58 Slayer; Cabin Fever quest", null, null, null);
        popup.open(task);
        render(popup);
        int collapsed = popup.totalContentRows();
        Rectangle fixedBounds = new Rectangle(popup.bounds());
        Rectangle requirements = rectangle(popup, "requirementsToggleBounds");
        assertTrue(requirements.width > 0);
        assertTrue(popup.toggleSectionAt(center(requirements)));
        render(popup);
        assertTrue(popup.totalContentRows() > collapsed);
        assertEquals("Expanding requirements keeps the popup fixed", fixedBounds, popup.bounds());
        Rectangle history = rectangle(popup, "historyToggleBounds");
        assertTrue("A single task must not have a completion history dropdown", history.isEmpty());
        assertTrue(popup.instanceRemoveBounds().isEmpty());
        assertFalse("The compact completion entry keeps its incomplete action", popup.markIncompleteBounds().isEmpty());
        popup.open(task);
        render(popup);
        assertEquals(collapsed, popup.totalContentRows());
        assertFalse(popup.toggleSectionAt(new Point(-1, -1)));
    }

    @Test
    public void groupedHistoryLivesBelowRequirements() throws Exception
    {
        for (int total : new int[] {4, 12})
        {
            TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
            popup.open(new XtremeTask("group", "Get 1 unique from Tempoross", TaskSource.COLLECTION_LOG,
                    TaskTier.EASY, null, null, null, "35 Fishing", null, null, null));
            render(popup, total);
            Rectangle history = rectangle(popup, "historyToggleBounds");
            Rectangle requirements = rectangle(popup, "requirementsToggleBounds");
            assertTrue("Requirements precede history", requirements.y < history.y);
            Rectangle bounds = new Rectangle(popup.bounds());
            int collapsedRows = popup.totalContentRows();
            assertTrue(popup.toggleSectionAt(center(history)));
            render(popup, total);
            assertTrue(popup.totalContentRows() > collapsedRows);
            assertEquals(bounds, popup.bounds());
            assertEquals("Each completed instance keeps its history action", 2, popup.instanceRemoveBounds().size());
        }
    }

    @Test
    public void emptyGroupedHistoryIsHiddenWithoutCompletionActions() throws Exception
    {
        TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
        popup.open(new XtremeTask("group", "Get 1 unique from Tempoross", TaskSource.COLLECTION_LOG,
                TaskTier.EASY, null, null, null, "35 Fishing", null, null, null));
        render(popup, 4, 0);
        assertTrue(rectangle(popup, "historyToggleBounds").isEmpty());
        assertTrue(popup.instanceRemoveBounds().isEmpty());
    }

    @Test
    public void mismatchedTitleUsesWarningRed()
    {
        TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
        popup.open(new XtremeTask("halo", "Get 1 new halo", TaskSource.COLLECTION_LOG,
                TaskTier.EASY, null, null, null, null, null, null, null));
        // The fixture marks this task complete and reports a Collection Log mismatch.
        BufferedImage image = render(popup, 0, 2);
        Rectangle bounds = popup.bounds();
        int redPixels = 0;
        for (int y = bounds.y + 4; y < bounds.y + 35; y++)
            for (int x = bounds.x + 12; x < bounds.x + bounds.width - 40; x++)
            {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getRed() > 200 && pixel.getGreen() < 120 && pixel.getBlue() < 120) redPixels++;
            }
        assertTrue("A Collection Log mismatch must use warning styling", redPixels > 10);
    }

    @Test
    public void ignoreDialogStaysDismissedAfterReopening() throws Exception
    {
        TaskDetailsPopup popup = new TaskDetailsPopup(UiPalette.DEFAULT, new TaskListScrollController(3));
        java.util.concurrent.atomic.AtomicBoolean ignored = new java.util.concurrent.atomic.AtomicBoolean();
        popup.configureMismatchActions(t -> ignored.get(), t -> ignored.set(true));
        XtremeTask task = new XtremeTask("halo", "Get 1 new halo", TaskSource.COLLECTION_LOG, TaskTier.EASY);
        popup.open(task);
        render(popup);
        Rectangle popupBounds = popup.bounds();
        net.runelite.api.Point iconMouse = new net.runelite.api.Point(popupBounds.x + 17, popupBounds.y + 16);
        render(popup, 0, 2, iconMouse);
        Rectangle dialog = rectangle(popup, "mismatchDialogBounds");
        Rectangle ignore = rectangle(popup, "mismatchIgnoreBounds");
        assertFalse(dialog.isEmpty());
        assertTrue(dialog.y + dialog.height < popup.bounds().y);
        assertTrue(popup.mismatchIgnoreContains(center(ignore)));
        assertEquals(popup.bounds().x + popup.bounds().width / 2, ignore.x + ignore.width / 2, 1);
        render(popup);
        assertTrue("Warning hides when the pointer leaves", rectangle(popup, "mismatchDialogBounds").isEmpty());
        render(popup, 0, 2, iconMouse);
        render(popup, 0, 2, new net.runelite.api.Point(ignore.x + 2, ignore.y + 2));
        assertFalse("Ignore remains reachable", rectangle(popup, "mismatchIgnoreBounds").isEmpty());
        popup.ignoreMismatch();
        assertTrue(ignored.get());
        render(popup);
        assertTrue(rectangle(popup, "mismatchDialogBounds").isEmpty());
        popup.close();
        popup.open(task);
        render(popup);
        assertTrue(rectangle(popup, "mismatchIgnoreBounds").isEmpty());
    }

    private static Point center(Rectangle bounds)
    {
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    private static Rectangle rectangle(TaskDetailsPopup popup, String name) throws Exception
    {
        java.lang.reflect.Field field = TaskDetailsPopup.class.getDeclaredField(name);
        field.setAccessible(true);
        return new Rectangle((Rectangle) field.get(popup));
    }

    private static void render(TaskDetailsPopup popup)
    {
        render(popup, 0);
    }

    private static void render(TaskDetailsPopup popup, int total)
    {
        render(popup, total, 2);
    }

    private static BufferedImage render(TaskDetailsPopup popup, int total, int completedCount)
    {
        return render(popup, total, completedCount, null);
    }

    private static BufferedImage render(TaskDetailsPopup popup, int total, int completedCount, net.runelite.api.Point mouse)
    {
        java.util.ArrayList<XtremeTask> instances = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++)
            instances.add(new XtremeTask("instance_" + i, "Get 1 unique from Tempoross", TaskSource.COLLECTION_LOG,
                    TaskTier.EASY, null, null, null, "35 Fishing", null, null, null));
        BufferedImage image = new BufferedImage(850, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            popup.render(g, g.getFontMetrics(), new Rectangle(0, 0, 850, 900),
                    t -> total == 0 || instances.indexOf(t) >= 0 && instances.indexOf(t) < completedCount,
                    t -> null, t -> 100L, t -> total == 0 ? null : new TaskGroupProgress(completedCount, total), t -> instances,
                    t -> List.of(
                            new PrerequisiteStatus("58 Slayer", false, List.of(), List.of(net.runelite.api.Skill.SLAYER)),
                            new PrerequisiteStatus("Cabin Fever quest", true, List.of(), List.of(), true)),
                    null, null,
                    t -> new CollectionLogRequirementPreview("", false, true,
                            total == 0 ? List.of(new CollectionLogRequirementItem("Black mask", false))
                                    : List.of(new CollectionLogRequirementItem("Fish barrel", false), new CollectionLogRequirementItem("Tome of water", false))),
                    t -> "", t -> true, null, t -> List.of(), mouse, null, true);
        }
        finally { g.dispose(); }
        return image;
    }
}
