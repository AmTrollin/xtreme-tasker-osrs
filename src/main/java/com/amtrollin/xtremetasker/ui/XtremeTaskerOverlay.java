

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.tasklist.TaskListPipeline;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasks.TaskControlsRenderer;
import com.amtrollin.xtremetasker.ui.tasks.TaskDetailsPopup;
import com.amtrollin.xtremetasker.ui.tasks.CollectionLogIconGridRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import com.amtrollin.xtremetasker.ui.tasks.TasksTabRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasks.models.TasksTabState;
import com.amtrollin.xtremetasker.ui.current.CurrentTabLayout;
import com.amtrollin.xtremetasker.ui.current.CurrentTabRenderer;
import com.amtrollin.xtremetasker.ui.current.CurrentTabViewRenderer;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.input.OverlayInputAccess;
import com.amtrollin.xtremetasker.ui.input.OverlayKeyHandler;
import com.amtrollin.xtremetasker.ui.input.OverlayMouseHandler;
import com.amtrollin.xtremetasker.ui.input.OverlayWheelHandler;
import com.amtrollin.xtremetasker.ui.rules.RulesTabLayout;
import com.amtrollin.xtremetasker.ui.rules.RulesTabRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListViewController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskSelectionModel;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CA;
import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CLOGS;
import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.DAS;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;
import static com.amtrollin.xtremetasker.ui.style.UiStrings.*;
import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.tierLabel;

public class XtremeTaskerOverlay extends Overlay {
    private static final BufferedImage PLUGIN_ICON = loadPluginIconSafe();
    private static final BufferedImage HEADER_ICON = loadHeaderIconSafe();
    private static final UiPalette P = UiPalette.DEFAULT;

    private static BufferedImage loadPluginIconSafe() {
        try (InputStream in = XtremeTaskerOverlay.class.getResourceAsStream("/icons/xtreme_tasker_icon.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage loadHeaderIconSafe() {
        try (InputStream in = XtremeTaskerOverlay.class.getResourceAsStream("/icons/xtreme_tasker_header.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---- bounds / layout ----
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle panelDragBarBounds = new Rectangle();
    private final Rectangle panelCloseBounds = new Rectangle();
    private final Rectangle panelModeToggleBounds = new Rectangle();
    private final Rectangle iconBounds = new Rectangle();

    private final Rectangle currentTabBounds = new Rectangle();
    private final Rectangle tasksTabBounds = new Rectangle();
    private final Rectangle rulesTabBounds = new Rectangle();

    private final Rectangle taskListViewportBounds = new Rectangle();
    private final Rectangle taskScrollbarRailBounds = new Rectangle();
    private final Rectangle taskScrollbarThumbBounds = new Rectangle();
    private final Rectangle rulesViewportBounds = new Rectangle();
    private final Rectangle markAllIncompleteConfirmBounds = new Rectangle();
    private final Rectangle markAllIncompleteYesBounds = new Rectangle();
    private final Rectangle markAllIncompleteNoBounds = new Rectangle();
    private final Rectangle markIncompleteDontShowBounds = new Rectangle();
    private final Rectangle syncMismatchReviewBounds = new Rectangle();
    private final Rectangle syncMismatchViewportBounds = new Rectangle();
    private final Rectangle syncMismatchCloseBounds = new Rectangle();
    private final Rectangle syncMismatchMarkAllBounds = new Rectangle();
    private final Rectangle syncMismatchApplyBounds = new Rectangle();
    private final Rectangle syncMismatchCancelBounds = new Rectangle();
    private final Rectangle syncMismatchConfirmBounds = new Rectangle();
    private final Rectangle syncMismatchConfirmYesBounds = new Rectangle();
    private final Rectangle syncMismatchConfirmNoBounds = new Rectangle();
    private final Rectangle syncMismatchScrollbarRailBounds = new Rectangle();
    private final Rectangle syncMismatchScrollbarThumbBounds = new Rectangle();
    private final Rectangle syncMismatchDescriptionBounds = new Rectangle();
    private final Rectangle syncMismatchDescriptionCloseBounds = new Rectangle();
    private final Rectangle keyboardHintsButtonBounds = new Rectangle();
    private final Rectangle keyboardHintsPopupBounds = new Rectangle();
    private final Rectangle taskViewModeBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = Collections.synchronizedMap(new EnumMap<>(TaskTier.class));
    private final Map<XtremeTask, Rectangle> taskRowBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchTaskBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchTaskNameBounds = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> selectedSyncMismatchTaskIds = Collections.synchronizedSet(new HashSet<>());

    // Current tab bounds (now come from CurrentTabLayout)
    private final CurrentTabLayout currentLayout = new CurrentTabLayout();
    // Rules tab bounds (now come from RulesTabLayout)
    private final RulesTabLayout rulesLayout = new RulesTabLayout();
    private RulesTabLayout.SubTab rulesSubTab = RulesTabLayout.SubTab.RULES;

    private boolean panelOpen = false;
    private boolean compactPanelMode = true;
    private boolean draggingPanel = false;
    private boolean keyboardHintsOpen = false;
    private boolean markIncompleteDontShowChecked = false;
    private boolean syncMismatchApplyConfirmOpen = false;
    private boolean syncMismatchReviewOpen = false;
    private TaskSource syncMismatchReviewSource = null;
    private XtremeTask syncMismatchDescriptionTask = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private XtremeTask pendingMarkAllIncompleteTask = null;
    private boolean pendingMarkAllIncompleteGroupMode = false;

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;
    private double panelScale = 1.0;
    private net.runelite.api.Point panelRenderMouse = null;
    private boolean panelBoundsScaledForInput = false;

    private boolean draggingIcon = false;
    private int iconDragOffsetX = 0;
    private int iconDragOffsetY = 0;
    private Integer iconXOverride = null;
    private Integer iconYOverride = null;
    // Resizable mode: icon stored as (distFromRightEdge, distFromTop) so it tracks
    // horizontal resizes without relying on widget bounds every frame.
    private int resizableOffsetX = 0;  // canvasWidth - iconX - ICON_WIDTH
    private int resizableOffsetY = 0;  // iconY (absolute from top)
    private boolean resizableOffsetInitialized = false;
    private int fixedOffsetX = 0;       // canvasWidth - iconX - ICON_WIDTH
    private int fixedOffsetY = 0;       // iconY (absolute from top)
    private boolean fixedOffsetInitialized = false;
    private boolean iconPositionLoaded = false;
    private Boolean iconLayoutResized = null;
    private boolean panelPositionLoaded = false;

    private static final int ICON_RESIZABLE_DEFAULT_RIGHT_MARGIN = 62;
    private static final int ICON_RESIZABLE_DEFAULT_Y = 165;

    private static final int PANEL_W_TASKS = 690;
    private static final int PANEL_H_TASKS = 460;
    private static final int PANEL_W_COMPACT = 370;
    private static final int PANEL_H_COMPACT = 370;
    private static final double PANEL_SCALE_MIN = 0.82;
    private static final double PANEL_SCALE_AUTO_START_W = 1400.0;
    private static final double PANEL_SCALE_AUTO_START_H = 900.0;
    private static final double PANEL_SCALE_AUTO_RANGE_W = 1000.0;
    private static final double PANEL_SCALE_AUTO_RANGE_H = 700.0;


    // ---- animations (extracted) ----
    private final OverlayAnimations animations = new OverlayAnimations(COMPLETE_ANIM_MS, ROLL_ANIM_MS);

    // ---- client/plugin ----
    private final Client client;
    private final XtremeTaskerPlugin plugin;
    private final SpriteManager spriteManager;

    @Getter
    private final MouseAdapter mouseAdapter;
    @Getter
    private final MouseWheelListener mouseWheelListener;
    @Getter
    private final KeyListener keyListener;

    private enum MainTab {CURRENT, TASKS, RULES}

    private MainTab activeTab = MainTab.CURRENT;

    private static final List<TaskTier> TIER_TABS = Arrays.asList(TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER);
    private TaskTier activeTierTab = TaskTier.EASY;
    private final Map<XtremeTask, Rectangle> taskCheckboxBounds = Collections.synchronizedMap(new HashMap<>());

    // CA tier sword sprite IDs (SpriteID.CaTierSwords: _0=Easy, _1=Medium, _2=Hard, _3=Elite, _4=Master, _5=Grandmaster)
    private static final java.util.EnumMap<TaskTier, Integer> CA_TIER_SPRITE_IDS = new java.util.EnumMap<>(TaskTier.class);
    static {
        CA_TIER_SPRITE_IDS.put(TaskTier.EASY,        3393);
        CA_TIER_SPRITE_IDS.put(TaskTier.MEDIUM,      3394);
        CA_TIER_SPRITE_IDS.put(TaskTier.HARD,        3395);
        CA_TIER_SPRITE_IDS.put(TaskTier.ELITE,       3396);
        CA_TIER_SPRITE_IDS.put(TaskTier.MASTER,      3397);
        CA_TIER_SPRITE_IDS.put(TaskTier.GRANDMASTER, 3398);
    }

    private java.awt.image.BufferedImage resolveTaskIcon(XtremeTask task) {
        if (task == null) return null;
        Integer id = task.getIconItemId();
        if (id != null && id > 0) return plugin.getItemImage(id);
        if (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT && task.getTier() != null) {
            Integer spriteId = CA_TIER_SPRITE_IDS.get(task.getTier());
            if (spriteId != null) return spriteManager.getSprite(spriteId, 0);
        }
        return null;
    }

    private CollectionLogRequirementPreview buildCollectionLogRequirementPreview(XtremeTask task) {
        return buildCollectionLogRequirementPreview(task, true);
    }

    private static boolean isCollectionLogSyncSource(TaskSource source)
    {
        return source == TaskSource.COLLECTION_LOG || source == TaskSource.DIARY_ACHIEVEMENT;
    }

    private CollectionLogRequirementPreview buildCollectionLogRequirementPreview(
            XtremeTask task,
            boolean completedInstanceCanApplyAll)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG) return null;

        TaskVerification verification = task.getVerification();
        if (verification == null || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG) return null;

        int[] itemIds = verification.getItemIds();
        if (itemIds == null || itemIds.length == 0) return null;
        if (itemIds.length == 1) return null;

        List<XtremeTask> requirementSequence = collectionLogRequirementSequence(task, itemIds);
        boolean isCountedSequence = requirementSequence.size() > 1;
        boolean repeatedDistinctPool = isCountedSequence && itemIds.length > 1;

        int requiredCount = collectionLogPreviewRequiredCount(task, verification, itemIds);
        requiredCount = Math.max(1, Math.min(requiredCount, itemIds.length));
        int previousRequiredCount = collectionLogPreviewPreviousRequiredCount(task, itemIds);
        int totalObtainedCount = plugin.countObtainedCollectionLogItems(itemIds);
        int obtainedCount = Math.max(0, totalObtainedCount - previousRequiredCount);
        int shownObtainedCount = Math.min(obtainedCount, requiredCount);
        RepeatedCollectionLogRequirementState repeatedRequirementState = repeatedDistinctPool
                ? repeatedCollectionLogRequirementState(
                        task,
                        requirementSequence,
                        countedRequirementThresholds(requirementSequence),
                        totalObtainedCount,
                        completedInstanceCanApplyAll)
                : null;

        Map<Integer, CollectionLogRequirementItem.Status> statusByItemId = collectionLogRequirementStatuses(
                itemIds,
                repeatedDistinctPool,
                repeatedRequirementState);
        Map<String, CollectionLogRequirementItem.Status> statusByItemName = new LinkedHashMap<>();
        Map<String, Integer> itemIdByItemName = new LinkedHashMap<>();
        for (int itemId : itemIds) {
            String itemName = plugin.getItemName(itemId);
            CollectionLogRequirementItem.Status status = statusByItemId.getOrDefault(
                    itemId,
                    CollectionLogRequirementItem.Status.MISSING);

            CollectionLogRequirementItem.Status existingStatus = statusByItemName.get(itemName);
            if (existingStatus == null || requirementStatusPriority(status) > requirementStatusPriority(existingStatus)) {
                statusByItemName.put(itemName, status);
                itemIdByItemName.put(itemName, itemId);
            }
        }

        List<CollectionLogRequirementItem> items = new ArrayList<>(statusByItemName.size());
        for (Map.Entry<String, CollectionLogRequirementItem.Status> entry : statusByItemName.entrySet()) {
            items.add(new CollectionLogRequirementItem(itemIdByItemName.getOrDefault(entry.getKey(), -1), entry.getKey(), entry.getValue()));
        }

        boolean sameNameFamily = statusByItemName.size() == 1;
        String summaryText = repeatedDistinctPool
            ? repeatedRequirementState.summaryText
            : sameNameFamily
                ? shownObtainedCount + "/" + requiredCount + " " + pluralizeRequirementName(items.get(0).getName(), requiredCount) + " obtained"
            : "";
        return new CollectionLogRequirementPreview(summaryText, sameNameFamily || repeatedDistinctPool, true, items);
    }

    private Map<Integer, CollectionLogRequirementItem.Status> collectionLogRequirementStatuses(
            int[] itemIds,
            boolean repeatedDistinctPool,
            RepeatedCollectionLogRequirementState repeatedRequirementState)
    {
        Map<Integer, CollectionLogRequirementItem.Status> statusByItemId = new HashMap<>();
        if (itemIds == null || itemIds.length == 0)
        {
            return statusByItemId;
        }

        List<Integer> obtainedItemIds = new ArrayList<>();
        Map<Integer, Integer> itemIndex = new HashMap<>();
        for (int i = 0; i < itemIds.length; i++)
        {
            int itemId = itemIds[i];
            itemIndex.putIfAbsent(itemId, i);
            if (plugin.isCollectionLogItemObtained(itemId))
            {
                obtainedItemIds.add(itemId);
            }
            else
            {
                statusByItemId.put(itemId, CollectionLogRequirementItem.Status.MISSING);
            }
        }

        obtainedItemIds.sort((a, b) -> {
            int byOrder = Long.compare(plugin.getCollectionLogItemObtainedOrder(a), plugin.getCollectionLogItemObtainedOrder(b));
            if (byOrder != 0)
            {
                return byOrder;
            }
            return Integer.compare(itemIndex.getOrDefault(a, Integer.MAX_VALUE), itemIndex.getOrDefault(b, Integer.MAX_VALUE));
        });

        int appliedRemaining = repeatedDistinctPool && repeatedRequirementState != null
                ? Math.max(0, repeatedRequirementState.appliedObtainedCount)
                : 0;
        int availableRemaining = repeatedDistinctPool && repeatedRequirementState != null
                ? Math.max(0, repeatedRequirementState.availableObtainedCount)
                : Integer.MAX_VALUE;

        for (Integer itemId : obtainedItemIds)
        {
            CollectionLogRequirementItem.Status status;
            if (appliedRemaining > 0)
            {
                status = CollectionLogRequirementItem.Status.APPLIED;
                appliedRemaining--;
            }
            else if (availableRemaining > 0)
            {
                status = CollectionLogRequirementItem.Status.OBTAINED;
                availableRemaining--;
            }
            else
            {
                status = CollectionLogRequirementItem.Status.APPLIED;
            }
            statusByItemId.put(itemId, status);
        }

        return statusByItemId;
    }

    private static int requirementStatusPriority(CollectionLogRequirementItem.Status status)
    {
        if (status == CollectionLogRequirementItem.Status.OBTAINED)
        {
            return 2;
        }
        if (status == CollectionLogRequirementItem.Status.APPLIED)
        {
            return 1;
        }
        return 0;
    }

    private int collectionLogPreviewRequiredCount(XtremeTask task, TaskVerification verification, int[] itemIds) {
        int count = verification.getCount() == null ? 1 : verification.getCount();
        if (count <= 1 || task == null || task.getId() == null)
        {
            return Math.max(1, count);
        }

        CountedRequirementPosition position = countedRequirementPosition(task, itemIds);
        if (position == null)
        {
            return count;
        }

        int currentThreshold = position.currentThreshold;
        int previousThreshold = position.previousThreshold;
        return Math.max(1, currentThreshold - previousThreshold);
    }

    private int collectionLogPreviewPreviousRequiredCount(XtremeTask task, int[] itemIds) {
        if (task == null || task.getId() == null)
        {
            return 0;
        }

        CountedRequirementPosition position = countedRequirementPosition(task, itemIds);
        return position == null ? 0 : Math.max(0, position.previousThreshold);
    }

    private CountedRequirementPosition countedRequirementPosition(XtremeTask task, int[] itemIds) {
        List<XtremeTask> sequence = collectionLogRequirementSequence(task, itemIds);
        if (sequence.isEmpty())
        {
            return null;
        }

        int index = -1;
        for (int i = 0; i < sequence.size(); i++)
        {
            XtremeTask candidate = sequence.get(i);
            if (candidate != null && Objects.equals(task.getId(), candidate.getId()))
            {
                index = i;
                break;
            }
        }

        if (index < 0)
        {
            return null;
        }

        List<Integer> thresholds = countedRequirementThresholds(sequence);
        if (thresholds.isEmpty())
        {
            return null;
        }

        int current = thresholds.get(index);
        int previous = index <= 0 ? 0 : thresholds.get(index - 1);
        return new CountedRequirementPosition(previous, current);
    }

    private List<XtremeTask> collectionLogRequirementSequence(XtremeTask task, int[] itemIds) {
        if (task == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> sequence = new ArrayList<>();
        for (XtremeTask candidate : plugin.getDummyTasks())
        {
            if (sameCollectionLogRequirementSignature(task, candidate, itemIds))
            {
                sequence.add(candidate);
            }
        }
        return sequence;
    }

    private boolean sameCollectionLogRequirementSignature(XtremeTask target, XtremeTask candidate, int[] targetItemIds) {
        if (target == null || candidate == null)
        {
            return false;
        }

        TaskVerification targetVerification = target.getVerification();
        TaskVerification candidateVerification = candidate.getVerification();
        if (target.getSource() != TaskSource.COLLECTION_LOG
                || candidate.getSource() != TaskSource.COLLECTION_LOG
                || targetVerification == null
                || candidateVerification == null
                || targetVerification.getType() != TaskVerification.VerificationType.COLLECTION_LOG
                || candidateVerification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return false;
        }

        int[] candidateItemIds = candidateVerification.getItemIds();
        return canonicalItemIds(targetItemIds).equals(canonicalItemIds(candidateItemIds));
    }

    private String canonicalItemIds(int[] itemIds) {
        if (itemIds == null || itemIds.length == 0)
        {
            return "";
        }

        return Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Integer> countedRequirementThresholds(List<XtremeTask> sequence) {
        if (sequence == null || sequence.isEmpty())
        {
            return Collections.emptyList();
        }

        List<Integer> explicit = new ArrayList<>();
        boolean strictlyIncreasing = true;
        int previous = 0;
        for (XtremeTask groupedTask : sequence)
        {
            TaskVerification groupedVerification = groupedTask == null ? null : groupedTask.getVerification();
            Integer count = groupedVerification == null ? null : groupedVerification.getCount();
            if (count == null || count <= 0)
            {
                strictlyIncreasing = false;
                count = 1;
            }

            if (!explicit.isEmpty() && count <= previous)
            {
                strictlyIncreasing = false;
            }

            explicit.add(count);
            previous = count;
        }

        if (strictlyIncreasing)
        {
            return explicit;
        }

        int base = explicit.stream().filter(Objects::nonNull).min(Integer::compareTo).orElse(1);
        List<Integer> inferred = new ArrayList<>(sequence.size());
        for (int i = 0; i < sequence.size(); i++)
        {
            inferred.add(base + i);
        }
        return inferred;
    }

    private static final class CountedRequirementPosition {
        private final int previousThreshold;
        private final int currentThreshold;

        private CountedRequirementPosition(int previousThreshold, int currentThreshold) {
            this.previousThreshold = previousThreshold;
            this.currentThreshold = currentThreshold;
        }
    }

    private RepeatedCollectionLogRequirementState repeatedCollectionLogRequirementState(
            XtremeTask task,
            List<XtremeTask> sequence,
            List<Integer> thresholds,
            int totalObtainedCount,
            boolean completedInstanceCanApplyAll)
    {
        if (sequence == null || sequence.isEmpty() || thresholds == null || thresholds.isEmpty())
        {
            return new RepeatedCollectionLogRequirementState(0, 0, "");
        }

        int completedCount = 0;
        for (int i = 0; i < sequence.size(); i++)
        {
            XtremeTask groupedTask = sequence.get(i);
            if (groupedTask != null && plugin.isTaskCompleted(groupedTask))
            {
                completedCount++;
            }
        }

        int completedThreshold = thresholdAt(thresholds, completedCount - 1);
        int currentDone = 0;
        int currentRequired = 0;
        int appliedObtainedCount = Math.max(0, Math.min(totalObtainedCount, completedThreshold));
        int availableObtainedCount = Math.max(0, totalObtainedCount - appliedObtainedCount);

        boolean exactInstanceCompleted = plugin.isTaskCompleted(task);
        if ((completedInstanceCanApplyAll && exactInstanceCompleted)
                || (plugin.isTaskCompleted(task) && isTaskGroupFullyCompleted(task)))
        {
            return new RepeatedCollectionLogRequirementState(
                    Math.max(0, totalObtainedCount),
                    0,
                    repeatedCollectionLogRequirementSummary(totalObtainedCount, currentDone, currentRequired)
            );
        }

        int currentIndex = currentRequirementIndex(task, sequence);
        if (currentIndex >= 0)
        {
            int previousThreshold = currentIndex <= 0 ? 0 : thresholdAt(thresholds, currentIndex - 1);
            previousThreshold = Math.max(previousThreshold, completedThreshold);
            int currentThreshold = Math.max(thresholdAt(thresholds, currentIndex), previousThreshold);
            currentRequired = Math.max(1, currentThreshold - previousThreshold);
            currentDone = Math.min(Math.max(0, totalObtainedCount - previousThreshold), currentRequired);
            appliedObtainedCount = Math.max(0, Math.min(totalObtainedCount, previousThreshold + currentDone));
            availableObtainedCount = Math.max(0, totalObtainedCount - appliedObtainedCount);
        }

        return new RepeatedCollectionLogRequirementState(
                appliedObtainedCount,
                availableObtainedCount,
                repeatedCollectionLogRequirementSummary(totalObtainedCount, currentDone, currentRequired)
        );
    }

    private boolean isTaskGroupFullyCompleted(XtremeTask task)
    {
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return plugin.isTaskCompleted(task);
        }

        for (XtremeTask groupedTask : group)
        {
            if (groupedTask == null || !plugin.isTaskCompleted(groupedTask))
            {
                return false;
            }
        }
        return true;
    }

    private int currentRequirementIndex(XtremeTask task, List<XtremeTask> sequence)
    {
        XtremeTask current = plugin.getCurrentTask();
        if (task == null
                || task.getId() == null
                || current == null
                || current.getId() == null
                || !Objects.equals(task.getId(), current.getId())
                || plugin.isTaskCompleted(current))
        {
            return -1;
        }

        return requirementIndex(task, sequence);
    }

    private int requirementIndex(XtremeTask task, List<XtremeTask> sequence)
    {
        if (task == null || task.getId() == null || sequence == null)
        {
            return -1;
        }

        for (int i = 0; i < sequence.size(); i++)
        {
            XtremeTask groupedTask = sequence.get(i);
            if (groupedTask != null && Objects.equals(task.getId(), groupedTask.getId()))
            {
                return i;
            }
        }
        return -1;
    }

    private static int thresholdAt(List<Integer> thresholds, int index)
    {
        if (thresholds == null || thresholds.isEmpty() || index < 0)
        {
            return 0;
        }
        return Math.max(0, thresholds.get(Math.min(index, thresholds.size() - 1)));
    }

    private static String repeatedCollectionLogRequirementSummary(int totalObtainedCount, int currentDone, int currentRequired)
    {
        String summary = "total obtained: " + totalObtainedCount;
        return currentRequired > 1 ? summary + " | current task: " + currentDone + "/" + currentRequired : summary;
    }

    private static final class RepeatedCollectionLogRequirementState {
        private final int appliedObtainedCount;
        private final int availableObtainedCount;
        private final String summaryText;

        private RepeatedCollectionLogRequirementState(int appliedObtainedCount, int availableObtainedCount, String summaryText) {
            this.appliedObtainedCount = appliedObtainedCount;
            this.availableObtainedCount = availableObtainedCount;
            this.summaryText = summaryText == null ? "" : summaryText;
        }
    }

    private String pluralizeRequirementName(String name, int requiredCount) {
        String clean = name == null ? "items" : name.trim();
        if (clean.isEmpty())
        {
            return "items";
        }
        if (requiredCount == 1 || clean.endsWith("s"))
        {
            return clean;
        }
        if (clean.endsWith("y") && clean.length() > 1)
        {
            char beforeY = Character.toLowerCase(clean.charAt(clean.length() - 2));
            if ("aeiou".indexOf(beforeY) < 0)
            {
                return clean.substring(0, clean.length() - 1) + "ies";
            }
        }
        return clean + "s";
    }

    private boolean sameCollectionLogRequirementSequence(XtremeTask target, XtremeTask candidate, int[] targetItemIds) {
        if (target == null || candidate == null)
        {
            return false;
        }

        TaskVerification verification = candidate.getVerification();
        return candidate.getSource() == TaskSource.COLLECTION_LOG
                && Objects.equals(normalizeRequirementSequenceName(target.getName()), normalizeRequirementSequenceName(candidate.getName()))
                && verification != null
                && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                && Arrays.equals(targetItemIds, verification.getItemIds());
    }

    private String normalizeRequirementSequenceName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // Task Details popup
    private final TaskDetailsPopup taskDetailsPopup =
            new TaskDetailsPopup(P, new TaskListScrollController(SCROLL_ROWS_PER_NOTCH));


    // ==========================
    // Extracted state/controllers
    // ==========================
    private final TaskListQuery taskQuery = new TaskListQuery();
    private boolean tasksSourceFilterInitialized = false;

    private final TaskControlsLayout controls = new TaskControlsLayout();
    private final TaskControlsRenderer controlsRenderer = new TaskControlsRenderer(PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT, P.TAB_INACTIVE_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.INPUT_BG, P.INPUT_FOCUS_OUTLINE, P.PILL_ON_BG, P.PILL_OFF_BG);

    private final TaskSelectionModel selectionModel = new TaskSelectionModel();
    private final TaskListScrollController tasksScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListViewController taskListView = new TaskListViewController(selectionModel, tasksScroll);

    private final TaskListScrollController rulesScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListScrollController currentScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListScrollController syncMismatchScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private static final long COMPACT_EDGE_BOUNCE_SUPPRESS_MS = 180L;
    private static final double COMPACT_EDGE_BOUNCE_RELEASE_PX = 28.0;
    private int compactCurrentScrollPx = 0;
    private int compactCurrentVisibleContentPx = 0;
    private double compactCurrentWheelRemainderPx = 0.0;
    private long compactCurrentEdgeBounceUntilMs = 0L;
    private int compactCurrentEdgeBounceDirection = 0;
    private double compactCurrentEdgeBounceOppositePx = 0.0;

    private final TaskRowsRenderer taskRowsRenderer = new TaskRowsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT, TASK_TEXT_PAD_LEFT, P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE, P.ROW_DONE_BG, P.ROW_LINE, P.STRIKE_COLOR, P.UI_TEXT, P.UI_TEXT_DIM, P.PIP_RING, P.PIP_DONE_FILL, P.PIP_DONE_RING, P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);

    private final CurrentTabRenderer currentTabRenderer = new CurrentTabRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.BTN_ENABLED_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, WIKI_BUTTON_TEXT);
    private final CurrentTabViewRenderer currentTabViewRenderer = new CurrentTabViewRenderer(currentTabRenderer, P);
    private final CurrentTabState currentTabState = new CurrentTabState(currentLayout);


    private final TaskControlsRenderer controlsRendererTasks = new TaskControlsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, P.TAB_INACTIVE_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.INPUT_BG, P.INPUT_FOCUS_OUTLINE, P.PILL_ON_BG, P.PILL_OFF_BG);

    private final TaskRowsRenderer taskRowsRendererTasks = new TaskRowsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT + 4, TASK_TEXT_PAD_LEFT + 4, P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE, P.ROW_DONE_BG, P.ROW_LINE, P.STRIKE_COLOR, P.UI_TEXT, P.UI_TEXT_DIM, P.PIP_RING, P.PIP_DONE_FILL, P.PIP_DONE_RING, P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);

    private final RulesTabRenderer rulesTabRenderer = new RulesTabRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, P.UI_GOLD, P.UI_TEXT_DIM);
    private final TasksTabRenderer tasksTabRenderer = new TasksTabRenderer(P);
    private final TasksTabState tasksTabState = new TasksTabState(
            taskQuery,
            controls,
            selectionModel,
            tasksScroll,
            taskListView,
            tierTabBounds,
            taskRowBounds,
            taskCheckboxBounds,
            taskListViewportBounds,
            taskScrollbarRailBounds,
            taskScrollbarThumbBounds,
            keyboardHintsButtonBounds,
            keyboardHintsPopupBounds,
            taskViewModeBounds
    );
    private final ButtonRenderer buttonRenderer = new ButtonRenderer(P);


    @Inject
    public XtremeTaskerOverlay(Client client, XtremeTaskerPlugin plugin, SpriteManager spriteManager) {
        this.client = client;
        this.plugin = plugin;
        this.spriteManager = spriteManager;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);

// -----------------------------
// Extracted input handlers
// -----------------------------
        OverlayInputAccess access = buildInputAccess();

        this.keyListener = new OverlayKeyHandler(access);

        this.mouseAdapter = new OverlayMouseHandler(access, () -> {
            draggingPanel = false;
            activeTab = MainTab.CURRENT;

            tasksScroll.reset();
            rulesScroll.reset();

            taskQuery.searchFocused = false;
        });

        this.mouseWheelListener = new OverlayWheelHandler(access);
    }

    /** Returns true while the roll animation is in progress. */
    public boolean isRolling() {
        return animations.isRolling();
    }

    /** Resets the draggable icon to its default position (clears overrides + persisted config). */
    public void resetIconPosition() {
        clearIconPositionState(true);
    }

    /**
     * Forces the icon position to be re-read from config on the next render frame.
     * Call this on account switch so each account's saved position is applied.
     */
    public void reloadIconPosition() {
        iconXOverride = null;
        iconYOverride = null;
        resizableOffsetX = 0;
        resizableOffsetY = 0;
        resizableOffsetInitialized = false;
        fixedOffsetX = 0;
        fixedOffsetY = 0;
        fixedOffsetInitialized = false;
        iconPositionLoaded = false;
        iconLayoutResized = null;
    }

    /** Returns a snapshot of the current icon bounds for use in menu entry checks. */
    public Rectangle getIconBounds() {
        return new Rectangle(iconBounds);
    }

    // -----------------------------
    // rowBlock accessors (for wheel)
    // -----------------------------
    int tasksRowBlock() {
        return scaleInputValue(taskRowsRendererTasks.rowBlock());
    }


    int rulesRowBlock() {
        return scaleInputValue(rulesTabRenderer.rowBlock());
    }

    private int currentRowBlock() {
        return scaleInputValue(ROW_HEIGHT);
    }

    private int scaleInputValue(int value) {
        return Math.max(1, (int) Math.round(value * panelScale));
    }

    private int unscaleInputValue(int value) {
        return Math.max(0, (int) Math.round(value / Math.max(0.01, panelScale)));
    }

    private double computePanelScale(int canvasW, int canvasH, int panelW, int panelH) {
        double widthPressure = (canvasW - PANEL_SCALE_AUTO_START_W) / PANEL_SCALE_AUTO_RANGE_W;
        double heightPressure = (canvasH - PANEL_SCALE_AUTO_START_H) / PANEL_SCALE_AUTO_RANGE_H;
        double pressure = Math.max(widthPressure, heightPressure);
        double autoScale = 1.0 - (Math.max(0.0, Math.min(1.0, pressure)) * (1.0 - PANEL_SCALE_MIN));

        double fitW = canvasW <= 8 ? 1.0 : (canvasW - 8.0) / panelW;
        double fitH = canvasH <= 4 ? 1.0 : (canvasH - 4.0) / panelH;
        double fitScale = Math.min(1.0, Math.min(fitW, fitH));

        double scale = Math.min(autoScale, fitScale);
        if (fitScale < PANEL_SCALE_MIN) {
            return Math.max(0.55, Math.min(1.0, scale));
        }
        return Math.max(PANEL_SCALE_MIN, Math.min(1.0, scale));
    }

    private net.runelite.api.Point toPanelRenderMouse(net.runelite.api.Point mouse, int anchorX, int anchorY, double scale) {
        if (mouse == null || scale == 1.0) {
            return mouse;
        }

        int x = anchorX + (int) Math.round((mouse.getX() - anchorX) / scale);
        int y = anchorY + (int) Math.round((mouse.getY() - anchorY) / scale);
        return new net.runelite.api.Point(x, y);
    }

    private net.runelite.api.Point mouseCanvasPositionForPanelRender() {
        return panelRenderMouse != null ? panelRenderMouse : client.getMouseCanvasPosition();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!plugin.isOverlayEnabled()) {
            return null;
        }
        if (!plugin.isLoggedIn()) {
            return null;
        }

        // If new tasks were cleared (e.g. on logout/relog), turn off the filter so it
        // doesn't stay active as a stale state next session.
        if (!plugin.hasNewTasks()) {
            taskQuery.showNewTasksFilter = false;
        }

        // Lazy-load saved icon position (safe here — all injected fields are ready).
        if (!iconPositionLoaded) {
            iconPositionLoaded = true;
            int[] savedResizablePos = plugin.loadIconPosition(true);
            if (savedResizablePos != null) {
                resizableOffsetX = savedResizablePos[0];
                resizableOffsetY = savedResizablePos[1];
                resizableOffsetInitialized = true;
            }
            int[] savedFixedPos = plugin.loadIconPosition(false);
            if (savedFixedPos != null) {
                fixedOffsetX = savedFixedPos[0];
                fixedOffsetY = savedFixedPos[1];
                fixedOffsetInitialized = true;
            }
        }
        boolean currentIconLayoutResized = client.isResized();
        if (iconLayoutResized == null) {
            iconLayoutResized = currentIconLayoutResized;
        } else if (iconLayoutResized != currentIconLayoutResized) {
            clearIconPositionState(true);
            iconLayoutResized = currentIconLayoutResized;
        }

        // Lazy-load saved panel position.
        if (!panelPositionLoaded) {
            panelPositionLoaded = true;
            int[] savedPanel = plugin.loadPanelPosition();
            if (savedPanel != null) {
                panelXOverride = savedPanel[0];
                panelYOverride = savedPanel[1];
            }
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();

        int canvasW = client.getCanvasWidth();
        int canvasH = client.getCanvasHeight();

        // icon — circle
        Point iconPos = computeIconPosition(canvasW, canvasH);
        iconBounds.setBounds(iconPos.x, iconPos.y, ICON_WIDTH, ICON_HEIGHT);

        if (PLUGIN_ICON != null)
        {
            Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            Object oldRendering = g.getRenderingHint(RenderingHints.KEY_RENDERING);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(PLUGIN_ICON, iconBounds.x, iconBounds.y, ICON_WIDTH, ICON_HEIGHT, null);
            if (oldInterpolation != null)
            {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            }
            if (oldRendering != null)
            {
                g.setRenderingHint(RenderingHints.KEY_RENDERING, oldRendering);
            }
        }
        else
        {
            // Fallback: old XT circle if the PNG resource is unavailable.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(40, 32, 22, 220));
            g.fillOval(iconBounds.x, iconBounds.y, ICON_WIDTH, ICON_HEIGHT);
            g.setColor(P.UI_EDGE_DARK);
            g.drawOval(iconBounds.x, iconBounds.y, ICON_WIDTH - 1, ICON_HEIGHT - 1);
            g.setColor(P.UI_EDGE_LIGHT);
            g.drawArc(iconBounds.x + 1, iconBounds.y + 1, ICON_WIDTH - 3, ICON_HEIGHT - 3, 45, 180);
            g.setColor(P.UI_EDGE_DARK);
            g.drawArc(iconBounds.x + 1, iconBounds.y + 1, ICON_WIDTH - 3, ICON_HEIGHT - 3, 225, 180);
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 180));
            g.drawOval(iconBounds.x + 1, iconBounds.y + 1, ICON_WIDTH - 3, ICON_HEIGHT - 3);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

            g.setColor(P.UI_TEXT);
            String iconLabel = "XT";
            int iconTextW = fm.stringWidth(iconLabel);
            g.drawString(iconLabel, iconBounds.x + (ICON_WIDTH - iconTextW) / 2, centeredTextBaseline(iconBounds, fm));
        }

        return new Dimension(ICON_WIDTH, ICON_HEIGHT);
    }

    /** Called by XtremeTaskerPanelOverlay (ALWAYS_ON_TOP) to draw the panel above widgets. */
    Dimension renderPanel(Graphics2D g) {
        if (!plugin.isOverlayEnabled() || !plugin.isLoggedIn() || !panelOpen) {
            return null;
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        int canvasW = client.getCanvasWidth();
        int canvasH = client.getCanvasHeight();

        // Row bounds are replaced atomically by the active tab renderer later in the frame.
        // Keeping the previous frame's rows until then avoids mouse hit-test flicker.
        synchronized (tierTabBounds) {
            tierTabBounds.clear();
        }
        int panelW = compactPanelMode ? PANEL_W_COMPACT : PANEL_W_TASKS;
        int panelHeight = compactPanelMode ? PANEL_H_COMPACT : PANEL_H_TASKS;
        panelScale = computePanelScale(canvasW, canvasH, panelW, panelHeight);

        int physicalPanelW = Math.max(1, (int) Math.round(panelW * panelScale));
        int physicalPanelH = Math.max(1, (int) Math.round(panelHeight * panelScale));

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasW - physicalPanelW) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasH - physicalPanelH) / 2;

        panelX = Math.max(0, Math.min(panelX, canvasW - physicalPanelW));
        panelY = Math.max(0, Math.min(panelY, canvasH - physicalPanelH));

        panelBounds.setBounds(panelX, panelY, panelW, panelHeight);
        panelBoundsScaledForInput = false;
        panelRenderMouse = toPanelRenderMouse(client.getMouseCanvasPosition(), panelX, panelY, panelScale);
        AffineTransform oldTransform = g.getTransform();
        if (panelScale != 1.0) {
            g.translate(panelX, panelY);
            g.scale(panelScale, panelScale);
            g.translate(-panelX, -panelY);
        }

        // header — fit the largest font that leaves room for close button + padding
        Font oldFont = g.getFont();
        final String title = "Xtreme Tasker";
        final int titleReserved = PANEL_PADDING * 4 + 28; // close button (~28px) + padding both sides
        float fontSize = 26f;
        Font titleFont;
        FontMetrics hfm;
        do {
            titleFont = FontManager.getRunescapeFont().deriveFont(Font.PLAIN, fontSize);
            hfm = g.getFontMetrics(titleFont);
            if (hfm.stringWidth(title) <= panelW - titleReserved) break;
            fontSize -= 1f;
        } while (fontSize > 12f);
        g.setFont(titleFont);
        hfm = g.getFontMetrics();

        final int headerPadY = getHeaderPadY();
        final int titleVisualH = getHeaderTitleHeight(hfm, panelW);
        int headerH = headerPadY + titleVisualH + headerPadY;

        // 1. header fill (dark)
        g.setColor(new Color(28, 22, 14, 245));
        g.fillRect(panelX, panelY, panelW, headerH);

        // 2. body fill (lighter brown) below header
        g.setColor(P.UI_BG);
        g.fillRect(panelX, panelY + headerH, panelW, panelHeight - headerH);

        // 3. panel border + bevel on top of both fills
        g.setColor(P.UI_EDGE_DARK);
        g.drawRect(panelX, panelY, panelW, panelHeight);
        g.setColor(P.UI_EDGE_LIGHT);
        g.drawLine(panelX + 1, panelY + 1, panelX + panelW - 2, panelY + 1);
        g.drawLine(panelX + 1, panelY + 1, panelX + 1, panelY + panelHeight - 2);
        g.setColor(P.UI_EDGE_DARK);
        g.drawLine(panelX + 1, panelY + panelHeight - 2, panelX + panelW - 2, panelY + panelHeight - 2);
        g.drawLine(panelX + panelW - 2, panelY + 1, panelX + panelW - 2, panelY + panelHeight - 2);

        if (HEADER_ICON != null) {
            drawHeaderLogo(g, panelX, panelY, panelW, headerH);
        } else {
            int titleW = hfm.stringWidth(title);
            int titleX = panelX + (panelWidth() - titleW) / 2;
            int titleY = panelY + headerPadY + hfm.getAscent();
            // faux semi-bold: fill + thin outline via GlyphVector
            g.setColor(P.UI_GOLD);
            g.drawString(title, titleX, titleY);
            java.awt.font.GlyphVector gv = titleFont.createGlyphVector(g.getFontRenderContext(), title);
            java.awt.Shape outline = gv.getOutline(titleX, titleY);
            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(0.6f));
            g.draw(outline);
            g.setStroke(oldStroke);
        }

        // X close button in top-right of header — fixed size independent of title font
        final int closeSize = 16;
        int closeInset = getHeaderPadY() + 2;
        int closeX = panelX + panelW - closeInset - closeSize;
        int closeY = panelY + closeInset;
        panelCloseBounds.setBounds(closeX, closeY, closeSize, closeSize);
        net.runelite.api.Point rlMouse = mouseCanvasPositionForPanelRender();
        boolean hoveringClose = rlMouse != null && panelCloseBounds.contains(rlMouse.getX(), rlMouse.getY());

        g.setColor(hoveringClose ? new Color(22, 17, 11, 235) : new Color(18, 14, 9, 185));
        g.fillRoundRect(closeX - 2, closeY - 2, closeSize + 4, closeSize + 4, 4, 4);
        g.setColor(new Color(P.UI_EDGE_LIGHT.getRed(), P.UI_EDGE_LIGHT.getGreen(), P.UI_EDGE_LIGHT.getBlue(), hoveringClose ? 95 : 55));
        g.drawRoundRect(closeX - 2, closeY - 2, closeSize + 4, closeSize + 4, 4, 4);

        g.setColor(hoveringClose ? Color.WHITE : new Color(200, 200, 200, 180));
        int cx = closeX + closeSize / 2;
        int cy = closeY + closeSize / 2;
        int arm = closeSize / 2 - 2;
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - arm, cy - arm, cx + arm, cy + arm);
        g.drawLine(cx + arm, cy - arm, cx - arm, cy + arm);
        g.setStroke(new BasicStroke(1f));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        g.setFont(oldFont);
        fm = g.getFontMetrics();

        int cursorY = panelY + headerH;

        int modeW = closeSize;
        int modeH = closeSize;
        int modeX = panelX + closeInset;
        int modeY = closeY;
        panelModeToggleBounds.setBounds(modeX, modeY, modeW, modeH);
        drawPanelModeToggle(g, fm, rlMouse);

        // gold divider under header
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 180));
        g.fillRect(panelX + 1, cursorY, panelW - 2, 1);
        cursorY += 1;

        panelDragBarBounds.setBounds(panelX, panelY, panelW, cursorY - panelY);

        cursorY += 4;

        if (compactPanelMode) {
            clearFullPanelTabBounds();
            activeTab = MainTab.CURRENT;
            renderCompactPanel(g, fm, panelX, cursorY);

            if (pendingMarkAllIncompleteTask != null) {
                renderMarkAllIncompleteConfirm(g, fm);
            } else {
                markAllIncompleteConfirmBounds.setBounds(0, 0, 0, 0);
                markAllIncompleteYesBounds.setBounds(0, 0, 0, 0);
                markAllIncompleteNoBounds.setBounds(0, 0, 0, 0);
                markIncompleteDontShowBounds.setBounds(0, 0, 0, 0);
            }

            animations.prune();
            g.setTransform(oldTransform);
            scalePanelInputBounds(panelX, panelY, panelScale);
            panelBoundsScaledForInput = true;
            panelRenderMouse = null;
            return new Dimension(physicalPanelW, physicalPanelH);
        }

        // tabs
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = panelInnerWidth();
        int tabW = (availableTabsW - 8) / 3;

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        currentTabBounds.setBounds(tab1X, cursorY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, cursorY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, cursorY, tabW, tabH);

        buttonRenderer.drawTab(g, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        buttonRenderer.drawTab(g, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        buttonRenderer.drawTab(g, rulesTabBounds, "Help", activeTab == MainTab.RULES);

        cursorY += tabH + 10;

        int textCursorY = cursorY + fm.getAscent();

        if (activeTab == MainTab.CURRENT) {
            renderCurrentTab(g, fm, panelX, textCursorY);
        } else if (activeTab == MainTab.TASKS) {
            renderTasksTab(g, fm, panelX, textCursorY);
        } else {
            renderRulesTab(g, fm, panelX, textCursorY);
        }

        if (pendingMarkAllIncompleteTask != null) {
            renderMarkAllIncompleteConfirm(g, fm);
        } else {
            markAllIncompleteConfirmBounds.setBounds(0, 0, 0, 0);
            markAllIncompleteYesBounds.setBounds(0, 0, 0, 0);
            markAllIncompleteNoBounds.setBounds(0, 0, 0, 0);
            markIncompleteDontShowBounds.setBounds(0, 0, 0, 0);
        }

        if (syncMismatchReviewOpen && !visibleSyncMismatchTasks().isEmpty()) {
            renderSyncMismatchReview(g, fm);
        } else {
            clearSyncMismatchReviewBounds();
        }

        animations.prune();
        g.setTransform(oldTransform);
        scalePanelInputBounds(panelX, panelY, panelScale);
        panelBoundsScaledForInput = true;
        panelRenderMouse = null;
        renderTaskDetailsPopupUnscaled(g, fm);
        if (taskDetailsPopup.isOpen() && pendingMarkAllIncompleteTask != null) {
            renderMarkAllIncompleteConfirm(g, fm);
        }
        return new Dimension(physicalPanelW, physicalPanelH);
    }

    private void renderTaskDetailsPopupUnscaled(Graphics2D g, FontMetrics fm) {
        if (!taskDetailsPopup.isOpen()) {
            return;
        }

        taskDetailsPopup.render(
                g,
                fm,
                panelBounds,
                plugin::isTaskCompleted,
                plugin::getCompletionInfo,
                plugin::getTaskTimeTicks,
                useCondensedTaskRows() ? plugin::getTaskGroupProgress : null,
                useCondensedTaskRows() ? plugin::getTaskGroupInstances : null,
                plugin::getPrerequisiteStatuses,
                task -> buildCollectionLogRequirementPreview(task, !useCondensedTaskRows()),
                plugin::getItemImage,
                client.getMouseCanvasPosition(),
                resolveTaskIcon(taskDetailsPopup.task()),
                plugin.showTips()
        );
    }

    private void clearSyncMismatchReviewBounds()
    {
        syncMismatchReviewBounds.setBounds(0, 0, 0, 0);
        syncMismatchViewportBounds.setBounds(0, 0, 0, 0);
        syncMismatchCloseBounds.setBounds(0, 0, 0, 0);
        syncMismatchMarkAllBounds.setBounds(0, 0, 0, 0);
        syncMismatchApplyBounds.setBounds(0, 0, 0, 0);
        syncMismatchCancelBounds.setBounds(0, 0, 0, 0);
        syncMismatchConfirmBounds.setBounds(0, 0, 0, 0);
        syncMismatchConfirmYesBounds.setBounds(0, 0, 0, 0);
        syncMismatchConfirmNoBounds.setBounds(0, 0, 0, 0);
        syncMismatchScrollbarRailBounds.setBounds(0, 0, 0, 0);
        syncMismatchScrollbarThumbBounds.setBounds(0, 0, 0, 0);
        syncMismatchDescriptionBounds.setBounds(0, 0, 0, 0);
        syncMismatchDescriptionCloseBounds.setBounds(0, 0, 0, 0);
        syncMismatchReviewOpen = false;
        syncMismatchReviewSource = null;
        syncMismatchApplyConfirmOpen = false;
        syncMismatchDescriptionTask = null;
        selectedSyncMismatchTaskIds.clear();
        synchronized (syncMismatchTaskBounds) {
            syncMismatchTaskBounds.clear();
        }
        synchronized (syncMismatchTaskNameBounds) {
            syncMismatchTaskNameBounds.clear();
        }
    }

    private void renderSyncMismatchReview(Graphics2D g, FontMetrics fm)
    {
        List<XtremeTask> mismatches = visibleSyncMismatchTasks();
        if (mismatches.isEmpty())
        {
            clearSyncMismatchReviewBounds();
            return;
        }

        g.setColor(new Color(0, 0, 0, 135));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        int w = Math.min(panelBounds.width - 36, 470);
        int h = Math.min(panelBounds.height - 56, 390);
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        syncMismatchReviewBounds.setBounds(x, y, w, h);
        drawBevelBox(g, syncMismatchReviewBounds, new Color(45, 36, 24, 248));

        int pad = 12;
        int closeW = 28;
        int buttonH = ROW_HEIGHT + 8;
        syncMismatchCloseBounds.setBounds(x + w - pad - closeW, y + pad - 2, closeW, buttonH);
        drawPopupCloseX(g, syncMismatchCloseBounds);

        boolean hasCollectionLogReview = mismatches.stream()
                .anyMatch(task -> isCollectionLogSyncSource(task.getSource()));
        boolean hasCombatAchievementReview = mismatches.stream()
                .anyMatch(task -> task.getSource() == TaskSource.COMBAT_ACHIEVEMENT);
        g.setColor(P.UI_GOLD);
        String reviewMessage;
        if (hasCollectionLogReview && hasCombatAchievementReview)
        {
            reviewMessage = "Tasks marked complete in Xtreme Tasker but not detected in-game after sync";
        }
        else if (hasCollectionLogReview)
        {
            reviewMessage = "CLOG/AD tasks marked complete in Xtreme Tasker but not detected in-game after sync";
        }
        else
        {
            reviewMessage = "CA tasks marked complete in Xtreme Tasker but not detected in-game after sync";
        }
        int reviewMessageMaxW = w - pad * 3 - closeW;
        List<String> reviewMessageLines = TextUtils.wrapText(reviewMessage, fm, reviewMessageMaxW);
        int titleY = y + pad + fm.getAscent();
        for (String line : reviewMessageLines)
        {
            g.drawString(line, x + pad, titleY);
            titleY += fm.getHeight();
        }

        int nextY = titleY + 2;
        if (hasCollectionLogReview)
        {
            g.setColor(P.UI_TEXT_DIM);
            String refreshHint = "If new CLOG items are missing, open your Collection Log in game, then sync again.";
            g.drawString(TextUtils.truncateToWidth(refreshHint, fm, w - pad * 2),
                    x + pad, nextY + fm.getAscent());
            nextY += fm.getHeight();
        }

        int headerTop = nextY + (hasCollectionLogReview ? fm.getHeight() + 14 : 14);
        int footerH = buttonH + fm.getHeight() + 10;
        int listBottom = y + h - pad - footerH;
        Rectangle listFrame = new Rectangle(x + pad, 0, w - pad * 2, 0);
        int actionColumnW = 112;
        int actionColumnX = listFrame.x + listFrame.width - actionColumnW;
        g.setColor(P.UI_TEXT_DIM);
        g.drawString("Task", listFrame.x, headerTop + fm.getAscent());

        String actionHeader = "Mark incomplete?";
        int headerCheckboxSize = 16;
        int actionHeaderW = fm.stringWidth(actionHeader);
        int actionHeaderContentW = actionHeaderW + 5 + headerCheckboxSize;
        int actionHeaderX = actionColumnX + Math.max(0, (actionColumnW - actionHeaderContentW) / 2);
        g.drawString(actionHeader, actionHeaderX, headerTop + fm.getAscent());

        int selectedVisibleCount = selectedVisibleSyncMismatchCount(mismatches);
        boolean allMismatchTasksSelected = selectedVisibleCount >= mismatches.size();
        syncMismatchMarkAllBounds.setBounds(
                actionHeaderX + actionHeaderW + 5,
                headerTop + Math.max(0, (fm.getHeight() - headerCheckboxSize) / 2) - 4,
                headerCheckboxSize,
                headerCheckboxSize);
        drawSyncMismatchCheckbox(g, syncMismatchMarkAllBounds, allMismatchTasksSelected);

        net.runelite.api.Point mouse = mouseCanvasPositionForPanelRender();
        int mouseX = mouse == null ? -1 : mouse.getX();
        int mouseY = mouse == null ? -1 : mouse.getY();
        if (syncMismatchMarkAllBounds.contains(mouseX, mouseY))
        {
            String hint = allMismatchTasksSelected ? "Deselect all" : "Select all";
            int hintX = syncMismatchMarkAllBounds.x + syncMismatchMarkAllBounds.width - fm.stringWidth(hint);
            int hintY = Math.max(y + pad + fm.getAscent(), syncMismatchMarkAllBounds.y - 3);
            g.setColor(P.UI_TEXT_DIM);
            g.drawString(hint, hintX, hintY);
        }

        int listTop = headerTop + fm.getHeight() + 5;
        syncMismatchViewportBounds.setBounds(listFrame.x, listTop, listFrame.width, Math.max(0, listBottom - listTop));
        g.setColor(P.ROW_LINE);
        g.drawLine(syncMismatchViewportBounds.x, listTop - 3,
                syncMismatchViewportBounds.x + syncMismatchViewportBounds.width, listTop - 3);

        synchronized (syncMismatchTaskBounds) {
            syncMismatchTaskBounds.clear();
        }
        synchronized (syncMismatchTaskNameBounds) {
            syncMismatchTaskNameBounds.clear();
        }

        Shape oldClip = g.getClip();
        g.setClip(syncMismatchViewportBounds);
        int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
        int visible = Math.max(1, syncMismatchViewportBounds.height / rowBlock);
        int maxOffset = Math.max(0, mismatches.size() - visible);
        if (syncMismatchScroll.offsetRows > maxOffset)
        {
            syncMismatchScroll.offsetRows = maxOffset;
        }

        int end = Math.min(mismatches.size(), syncMismatchScroll.offsetRows + visible + 1);
        int rowY = syncMismatchViewportBounds.y;
        for (int i = syncMismatchScroll.offsetRows; i < end; i++)
        {
            XtremeTask task = mismatches.get(i);
            Rectangle row = new Rectangle(syncMismatchViewportBounds.x, rowY,
                    syncMismatchViewportBounds.width, ROW_HEIGHT + 2);

            Color rowFill = i % 2 == 0 ? new Color(62, 50, 34, 235) : new Color(53, 43, 31, 235);
            g.setColor(rowFill);
            g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(new Color(30, 24, 18, 170));
            g.fillRect(actionColumnX, row.y + 1, actionColumnW, row.height - 1);
            g.setColor(P.ROW_LINE);
            g.drawRect(row.x, row.y, row.width, row.height);
            g.drawLine(actionColumnX, row.y, actionColumnX, row.y + row.height);

            String label = TextUtils.truncateToWidth(syncMismatchRowLabel(task), fm, actionColumnX - row.x - 16);
            Rectangle taskNameArea = new Rectangle(row.x, row.y, Math.max(0, actionColumnX - row.x), row.height);
            boolean hoverRow = row.contains(mouseX, mouseY);
            boolean hoverTaskName = taskNameArea.contains(mouseX, mouseY);
            boolean clickableTaskName = task.getSource() == TaskSource.COMBAT_ACHIEVEMENT;
            g.setColor(clickableTaskName && hoverTaskName ? P.UI_GOLD : P.UI_TEXT);
            g.drawString(label, row.x + 8, row.y + ((row.height - fm.getHeight()) / 2) + fm.getAscent());
            if (hoverTaskName)
            {
                if (clickableTaskName)
                {
                    int underlineY = row.y + ((row.height - fm.getHeight()) / 2) + fm.getAscent() + 2;
                    g.drawLine(row.x + 8, underlineY, row.x + 8 + fm.stringWidth(label), underlineY);
                }
            }
            if (hoverRow)
            {
                String gameProgress = plugin.getSyncMismatchGameProgressLabel(task);
                if (gameProgress != null && !gameProgress.isEmpty())
                {
                    int hintX = row.x + 8 + fm.stringWidth(label) + 8;
                    int hintMaxW = Math.max(0, row.x + row.width - hintX - 8);
                    if (hintMaxW > 40)
                    {
                        String hint = TextUtils.truncateToWidth(gameProgress, fm, hintMaxW);
                        g.setColor(P.UI_TEXT_DIM);
                        int hintY = row.y + ((row.height - fm.getHeight()) / 2) + fm.getAscent();
                        g.drawString(hint, hintX, hintY);
                    }
                }
            }

            int smallBtn = Math.min(20, row.height - 4);
            Rectangle btn = new Rectangle(
                    actionColumnX + (actionColumnW - smallBtn) / 2,
                    row.y + (row.height - smallBtn) / 2,
                    smallBtn,
                    smallBtn);
            Rectangle hitTarget = new Rectangle(actionColumnX, row.y, actionColumnW, row.height);
            synchronized (syncMismatchTaskBounds) {
                syncMismatchTaskBounds.put(task, hitTarget);
            }
            if (clickableTaskName)
            {
                synchronized (syncMismatchTaskNameBounds) {
                    syncMismatchTaskNameBounds.put(task, taskNameArea);
                }
            }
            drawSyncMismatchCheckbox(g, btn, selectedSyncMismatchTaskIds.contains(task.getId()));
            rowY += rowBlock;
        }
        g.setClip(oldClip);

        syncMismatchScrollbarRailBounds.setBounds(0, 0, 0, 0);
        syncMismatchScrollbarThumbBounds.setBounds(0, 0, 0, 0);
        if (mismatches.size() > visible)
        {
            int scrollBarW = 6;
            int sbX = syncMismatchViewportBounds.x + syncMismatchViewportBounds.width - scrollBarW;
            syncMismatchScrollbarRailBounds.setBounds(sbX, syncMismatchViewportBounds.y, scrollBarW, syncMismatchViewportBounds.height);
            g.setColor(new Color(18, 14, 9, 200));
            g.fillRect(sbX, syncMismatchViewportBounds.y, scrollBarW, syncMismatchViewportBounds.height);

            float thumbRatio = (float) visible / mismatches.size();
            int thumbH = Math.max(14, (int) (syncMismatchViewportBounds.height * thumbRatio));
            float scrollRatio = maxOffset > 0 ? (float) syncMismatchScroll.offsetRows / maxOffset : 0f;
            int thumbY = syncMismatchViewportBounds.y + (int) ((syncMismatchViewportBounds.height - thumbH) * scrollRatio);
            syncMismatchScrollbarThumbBounds.setBounds(sbX, thumbY, scrollBarW, thumbH);
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 160));
            g.fillRoundRect(sbX, thumbY, scrollBarW, thumbH, 3, 3);
        }

        String scrollHint = "Displaying " + (syncMismatchScroll.offsetRows + 1) + "-"
                + Math.min(mismatches.size(), syncMismatchScroll.offsetRows + visible)
                + " / " + mismatches.size();
        int footerY = syncMismatchViewportBounds.y + syncMismatchViewportBounds.height + 4 + fm.getAscent();
        g.setColor(P.UI_TEXT_DIM);
        String selectedText = selectedVisibleCount + " selected";
        g.drawString(selectedText, syncMismatchViewportBounds.x, footerY);
        g.drawString(scrollHint, syncMismatchViewportBounds.x + syncMismatchViewportBounds.width - fm.stringWidth(scrollHint),
                footerY);

        int actionY = footerY + 6;
        int buttonW = 70;
        int gap = 8;
        syncMismatchCancelBounds.setBounds(syncMismatchViewportBounds.x + syncMismatchViewportBounds.width - buttonW, actionY, buttonW, buttonH);
        syncMismatchApplyBounds.setBounds(syncMismatchCancelBounds.x - gap - buttonW, actionY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, syncMismatchApplyBounds, "Apply",
                selectedVisibleCount == 0 ? P.BTN_DISABLED_BG : P.BTN_ENABLED_BG,
                selectedVisibleCount == 0 ? P.UI_TEXT_DIM : P.UI_TEXT,
                selectedVisibleCount == 0 ? null : P.UI_GOLD);
        buttonRenderer.drawPlainButton(g, syncMismatchCancelBounds, "Cancel", P.BTN_DISABLED_BG);

        if (syncMismatchDescriptionTask != null)
        {
            renderSyncMismatchDescription(g, fm);
        }

        if (syncMismatchApplyConfirmOpen)
        {
            renderSyncMismatchApplyConfirm(g, fm);
        }
    }

    private List<XtremeTask> visibleSyncMismatchTasks()
    {
        return plugin.getSyncMismatchTasks(syncMismatchReviewSource);
    }

    private int selectedVisibleSyncMismatchCount(List<XtremeTask> mismatches)
    {
        int count = 0;
        for (XtremeTask task : mismatches)
        {
            if (task != null && task.getId() != null && selectedSyncMismatchTaskIds.contains(task.getId()))
            {
                count++;
            }
        }
        return count;
    }

    private void renderSyncMismatchDescription(Graphics2D g, FontMetrics fm)
    {
        XtremeTask task = syncMismatchDescriptionTask;
        if (task == null)
        {
            return;
        }

        String description = task.getDescription();
        if (description == null || description.trim().isEmpty())
        {
            description = "No description available.";
        }

        int pad = 12;
        int iconSize = 32;
        int tierPillGap = 4;
        int tierPillH = 18;
        int closeW = 28;
        int w = Math.min(syncMismatchReviewBounds.width - 44, 360);
        String tierPillText = task.getSource() == TaskSource.COMBAT_ACHIEVEMENT && task.getTier() != null
                ? tierLabel(task.getTier())
                : null;
        FontMetrics smallFm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        int tierPillW = tierPillText == null ? 0 : Math.max(24, smallFm.stringWidth(tierPillText) + 14);
        int iconColumnW = Math.max(iconSize, tierPillW);
        int iconStackH = iconSize + (tierPillText == null ? 0 : tierPillGap + tierPillH);
        int textXOffset = pad + iconColumnW + pad;
        int textW = w - textXOffset - closeW - pad;
        List<String> lines = TextUtils.wrapText(description.trim(), fm, textW);
        int visibleLines = Math.max(1, Math.min(lines.size(), 6));
        int textBlockH = visibleLines * fm.getHeight();
        int contentH = Math.max(iconStackH, textBlockH);
        int h = Math.max(86, Math.min(160, pad * 2 + contentH));
        int x = syncMismatchReviewBounds.x + (syncMismatchReviewBounds.width - w) / 2;
        int y = syncMismatchReviewBounds.y + (syncMismatchReviewBounds.height - h) / 2;
        syncMismatchDescriptionBounds.setBounds(x, y, w, h);

        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(syncMismatchReviewBounds.x, syncMismatchReviewBounds.y,
                syncMismatchReviewBounds.width, syncMismatchReviewBounds.height);
        drawBevelBox(g, syncMismatchDescriptionBounds, new Color(45, 36, 24, 252));

        syncMismatchDescriptionCloseBounds.setBounds(x + w - pad - closeW, y + pad - 4, closeW, ROW_HEIGHT + 8);
        drawPopupCloseX(g, syncMismatchDescriptionCloseBounds);

        BufferedImage icon = resolveTaskIcon(task);
        int contentY = y + (h - contentH) / 2;
        int iconStackY = contentY + Math.max(0, (contentH - iconStackH) / 2);
        int iconX = x + pad + Math.max(0, (iconColumnW - iconSize) / 2);
        int iconY = iconStackY;
        if (icon != null)
        {
            g.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
        }
        else
        {
            drawBevelBox(g, new Rectangle(iconX, iconY, iconSize, iconSize), P.INPUT_BG);
        }
        if (tierPillText != null)
        {
            TaskRowsRenderer.drawSourceBadge(
                    g,
                    x + pad + Math.max(0, (iconColumnW - tierPillW) / 2),
                    iconY + iconSize + tierPillGap,
                    tierPillText,
                    P.UI_EDGE_DARK,
                    P.UI_EDGE_LIGHT,
                    P.UI_GOLD,
                    P.UI_TEXT);
        }

        Shape oldClip = g.getClip();
        int textTop = contentY + Math.max(0, (contentH - textBlockH) / 2);
        Rectangle textBounds = new Rectangle(x + textXOffset, textTop, textW, textBlockH);
        g.setClip(textBounds);
        g.setColor(P.UI_TEXT);
        int textY = textBounds.y + fm.getAscent();
        for (String line : lines)
        {
            if (textY > textBounds.y + textBounds.height)
            {
                break;
            }
            g.drawString(TextUtils.truncateToWidth(line, fm, textBounds.width), textBounds.x, textY);
            textY += fm.getHeight();
        }
        g.setClip(oldClip);
    }

    private void drawSyncMismatchCheckbox(Graphics2D g, Rectangle box, boolean checked)
    {
        drawBevelBox(g, box, checked ? P.BTN_ENABLED_BG : P.INPUT_BG);
        g.setColor(checked ? P.UI_GOLD : P.UI_TEXT_DIM);
        g.drawRect(box.x + 1, box.y + 1, box.width - 3, box.height - 3);
        if (checked)
        {
            g.drawLine(box.x + 5, box.y + box.height / 2, box.x + box.width / 2 - 1, box.y + box.height - 6);
            g.drawLine(box.x + box.width / 2 - 1, box.y + box.height - 6, box.x + box.width - 5, box.y + 5);
        }
    }

    private void drawPopupCloseX(Graphics2D g, Rectangle bounds)
    {
        g.setColor(new Color(200, 200, 200, 180));
        int ccx = bounds.x + bounds.width / 2;
        int ccy = bounds.y + bounds.height / 2;
        int carm = 6;
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(ccx - carm, ccy - carm, ccx + carm, ccy + carm);
        g.drawLine(ccx + carm, ccy - carm, ccx - carm, ccy + carm);
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    private String syncMismatchRowLabel(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.size() <= 1)
        {
            return task.getName();
        }

        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress == null || !progress.isGrouped())
        {
            return task.getName();
        }

        int instanceOrdinal = completedInstanceOrdinalInGroup(group, task);
        if (instanceOrdinal <= 0)
        {
            return task.getName() + " " + progress.label();
        }
        return task.getName() + " (" + instanceOrdinal + "/" + group.size() + ")";
    }

    private int completedInstanceOrdinalInGroup(List<XtremeTask> group, XtremeTask task)
    {
        if (group == null || group.isEmpty() || task == null)
        {
            return -1;
        }

        List<XtremeTask> completed = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && plugin.isTaskCompleted(groupedTask))
            {
                completed.add(groupedTask);
            }
        }

        completed.sort((a, b) -> {
            int byTimestamp = Long.compare(completionSortTimestamp(a), completionSortTimestamp(b));
            if (byTimestamp != 0)
            {
                return byTimestamp;
            }
            return Integer.compare(group.indexOf(a), group.indexOf(b));
        });

        for (int i = 0; i < completed.size(); i++)
        {
            XtremeTask completedTask = completed.get(i);
            if (completedTask != null && Objects.equals(completedTask.getId(), task.getId()))
            {
                return i + 1;
            }
        }
        return -1;
    }

    private long completionSortTimestamp(XtremeTask task)
    {
        CompletionInfo info = plugin.getCompletionInfo(task);
        return info != null && info.timestamp > 0 ? info.timestamp : Long.MAX_VALUE;
    }

    private void renderSyncMismatchApplyConfirm(Graphics2D g, FontMetrics fm)
    {
        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(syncMismatchReviewBounds.x, syncMismatchReviewBounds.y,
                syncMismatchReviewBounds.width, syncMismatchReviewBounds.height);

        int count = selectedVisibleSyncMismatchCount(visibleSyncMismatchTasks());
        String message = "Mark " + count + " task(s) incomplete?";
        String warning = "Completion dates and time spent will be cleared.";
        int w = Math.min(syncMismatchReviewBounds.width - 50,
                Math.max(fm.stringWidth(message), fm.stringWidth(warning)) + 36);
        int h = 104;
        int x = syncMismatchReviewBounds.x + (syncMismatchReviewBounds.width - w) / 2;
        int y = syncMismatchReviewBounds.y + (syncMismatchReviewBounds.height - h) / 2;
        syncMismatchConfirmBounds.setBounds(x, y, w, h);
        drawBevelBox(g, syncMismatchConfirmBounds, new Color(45, 36, 24, 252));

        int firstY = y + 18 + fm.getAscent();
        g.setColor(P.UI_TEXT);
        g.drawString(message, x + (w - fm.stringWidth(message)) / 2, firstY);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(warning, x + (w - fm.stringWidth(warning)) / 2, firstY + fm.getHeight() + 2);

        int buttonW = 70;
        int buttonH = ROW_HEIGHT + 8;
        int gap = 8;
        int buttonsW = buttonW * 2 + gap;
        int buttonY = y + h - buttonH - 10;
        syncMismatchConfirmYesBounds.setBounds(x + (w - buttonsW) / 2, buttonY, buttonW, buttonH);
        syncMismatchConfirmNoBounds.setBounds(syncMismatchConfirmYesBounds.x + buttonW + gap, buttonY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, syncMismatchConfirmYesBounds, "Confirm", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
        buttonRenderer.drawPlainButton(g, syncMismatchConfirmNoBounds, "Cancel", P.BTN_DISABLED_BG);
    }

    private void renderMarkAllIncompleteConfirm(Graphics2D g, FontMetrics fm)
    {
        g.setColor(new Color(0, 0, 0, 125));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        XtremeTask pending = pendingMarkAllIncompleteTask;
        boolean multipleTasks = pending != null && pendingMarkAllIncompleteGroupMode;
        String message = multipleTasks
                ? "Mark ALL repeated task instances incomplete?"
                : "Are you sure you want to mark this task incomplete?";
        String warning = multipleTasks
                ? "This clears every completed instance, including dates and time spent."
                : "Completion date and time spent will be lost.";
        String dontShowText = "Do not show this again";
        int textW = Math.max(fm.stringWidth(message), fm.stringWidth(warning));
        if (!multipleTasks)
        {
            textW = Math.max(textW, fm.stringWidth("[ ] " + dontShowText));
        }
        int w = Math.min(panelBounds.width - 48, textW + 36);
        int h = multipleTasks ? 104 : 124;
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        markAllIncompleteConfirmBounds.setBounds(x, y, w, h);

        drawBevelBox(g, markAllIncompleteConfirmBounds, new Color(45, 36, 24, 248));

        int buttonW = 70;
        int buttonH = ROW_HEIGHT + 6;
        int gap = 8;
        int buttonsW = buttonW * 2 + gap;
        int buttonY = y + h - buttonH - 10;
        markAllIncompleteYesBounds.setBounds(x + (w - buttonsW) / 2, buttonY, buttonW, buttonH);
        markAllIncompleteNoBounds.setBounds(markAllIncompleteYesBounds.x + buttonW + gap, buttonY, buttonW, buttonH);

        String drawMessage = TextUtils.truncateToWidth(message, fm, w - 20);
        String drawWarning = TextUtils.truncateToWidth(warning, fm, w - 20);
        int textAreaTop = y + 10;
        int checkboxH = multipleTasks ? 0 : fm.getHeight() + 6;
        int textAreaH = Math.max(0, buttonY - textAreaTop - 8 - checkboxH);
        int lineGap = 2;
        int textBlockH = fm.getHeight() * 2 + lineGap;
        int firstBaseline = textAreaTop + Math.max(0, (textAreaH - textBlockH) / 2) + fm.getAscent();

        g.setColor(P.UI_TEXT);
        g.drawString(drawMessage, x + (w - fm.stringWidth(drawMessage)) / 2, firstBaseline);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(drawWarning, x + (w - fm.stringWidth(drawWarning)) / 2, firstBaseline + fm.getHeight() + lineGap);

        if (!multipleTasks)
        {
            int boxSize = Math.max(10, fm.getAscent());
            int checkboxY = buttonY - checkboxH;
            int checkboxTextW = fm.stringWidth(dontShowText);
            int checkboxTotalW = boxSize + 6 + checkboxTextW;
            int checkboxX = x + (w - checkboxTotalW) / 2;
            markIncompleteDontShowBounds.setBounds(checkboxX, checkboxY - 2, checkboxTotalW, checkboxH);

            int boxY = checkboxY + (checkboxH - boxSize) / 2 - 1;
            g.setColor(new Color(32, 26, 17, 235));
            g.fillRect(checkboxX, boxY, boxSize, boxSize);
            g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 150));
            g.drawRect(checkboxX, boxY, boxSize, boxSize);
            if (markIncompleteDontShowChecked)
            {
                g.setColor(P.UI_GOLD);
                g.drawLine(checkboxX + 2, boxY + boxSize / 2, checkboxX + boxSize / 2 - 1, boxY + boxSize - 3);
                g.drawLine(checkboxX + boxSize / 2 - 1, boxY + boxSize - 3, checkboxX + boxSize - 2, boxY + 2);
            }

            g.setColor(P.UI_TEXT_DIM);
            g.drawString(dontShowText, checkboxX + boxSize + 6, checkboxY + ((checkboxH - fm.getHeight()) / 2) + fm.getAscent());
        }
        else
        {
            markIncompleteDontShowBounds.setBounds(0, 0, 0, 0);
        }

        buttonRenderer.drawPlainButton(
                g,
                markAllIncompleteYesBounds,
                "Yes",
                new Color(54, 39, 32, 235),
                new Color(205, 180, 170, 205),
                new Color(128, 88, 76, 115)
        );
        buttonRenderer.drawPlainButton(
                g,
                markAllIncompleteNoBounds,
                "No",
                new Color(58, 48, 32, 245),
                P.UI_TEXT,
                new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 150)
        );
    }

    // ----------------------------
    // CURRENT TAB (renderer + overlay draws buttons)
    // ----------------------------
    private void renderCurrentTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);
        XtremeTask recentCompleted = current == null ? plugin.getMostRecentCompletedTask() : null;

        TaskTier tierForProgress = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tierForProgress == null) tierForProgress = TaskTier.EASY;

        boolean rolling = animations.isRolling();

        TaskSource src = (current != null) ? current.getSource() : null;

        net.runelite.api.Point rlMouse = mouseCanvasPositionForPanelRender();
        java.awt.Point mousePoint = rlMouse == null ? null : new java.awt.Point(rlMouse.getX(), rlMouse.getY());

        currentTabViewRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                currentTabState,
                plugin.hasTaskPackLoaded(),
                current,
                currentCompleted,
                rolling,
                plugin::getTierProgressLabel,
                (ignored) -> computeCurrentLineForRender(current, currentCompleted, fm),
                plugin::getPrerequisiteStatuses,
                this::buildCollectionLogRequirementPreview,
                plugin::getItemImage,
                this::getTasksForTier,
                tierForProgress,
                src,
                plugin.getRollSourceFilter(),
                // Show pending skip notice if present, otherwise the post-roll one
                Optional.ofNullable(plugin.getPendingRollSkipNotice()).orElse(plugin.getRollSkipNotice()),
                mousePoint,
                currentScroll.offsetRows * ROW_HEIGHT,
                computeCurrentViewportH(g, fm, panelBounds, cursorYBaseline),
                plugin.showTips(),
                rolling ? null : resolveTaskIcon(current),
                plugin.getTaskTimeTicks(current),
                recentCompleted,
                plugin.getCompletionInfo(recentCompleted),
                plugin.getTaskTimeTicks(recentCompleted),
                keyboardHintsOpen,
                keyboardHintsButtonBounds,
                keyboardHintsPopupBounds,
                mousePoint == null ? -1 : mousePoint.x,
                mousePoint == null ? -1 : mousePoint.y
        );
    }

    private void renderCompactPanel(Graphics2D g, FontMetrics fm, int panelX, int contentTop) {
        resetCurrentLayoutBounds();
        keyboardHintsButtonBounds.setBounds(0, 0, 0, 0);
        keyboardHintsPopupBounds.setBounds(0, 0, 0, 0);
        taskViewModeBounds.setBounds(0, 0, 0, 0);

        int innerX = panelX + PANEL_PADDING;
        int innerW = panelBounds.width - PANEL_PADDING * 2;
        int y = contentTop;

        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", innerX, y + fm.getAscent());
            return;
        }

        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);
        boolean rolling = animations.isRolling();
        TaskTier tierForProgress = current != null ? current.getTier() : plugin.getCurrentTier();
        if (tierForProgress == null) {
            tierForProgress = TaskTier.EASY;
        }

        String progress = tierLabel(tierForProgress) + ": " + plugin.getTierProgressLabel(tierForProgress);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(progress, fm, innerW), innerX, y + fm.getAscent());
        y += fm.getHeight() + 3;

        int actionH = ROW_HEIGHT + 6;
        int cardH = 110;
        Rectangle card = new Rectangle(innerX, y, innerW, cardH);
        drawBevelBox(g, card, new Color(26, 17, 10, 225));

        net.runelite.api.Point rlMouse = mouseCanvasPositionForPanelRender();
        java.awt.Point mousePoint = rlMouse == null ? null : new java.awt.Point(rlMouse.getX(), rlMouse.getY());

        if (rolling) {
            drawCompactRolling(g, fm, card);
        } else if (current != null) {
            drawCompactCurrentIdentity(g, fm, card, current, currentCompleted, mousePoint);
        } else {
            drawCompactEmptyIdentity(g, fm, card);
        }

        y += cardH + 4;

        boolean rollEnabled = current == null || currentCompleted;
        boolean completeEnabled = current != null && !currentCompleted;
        int buttonGap = 6;
        int wikiW = current != null && current.getWikiUrl() != null && !current.getWikiUrl().trim().isEmpty()
                ? Math.max(48, fm.stringWidth("Wiki") + 18)
                : 0;
        int actionW = wikiW > 0 ? innerW - wikiW - buttonGap : innerW;
        Rectangle actionBounds = new Rectangle(innerX, y, actionW, actionH);
        if (completeEnabled) {
            currentLayout.completeButtonBounds.setBounds(actionBounds);
            buttonRenderer.drawPrimaryButton(g, currentLayout.completeButtonBounds, "Mark complete");
        } else if (rollEnabled) {
            currentLayout.rollButtonBounds.setBounds(actionBounds);
            buttonRenderer.drawPrimaryButton(g, currentLayout.rollButtonBounds, "Roll task");
        }

        if (wikiW > 0) {
            currentLayout.wikiButtonBounds.setBounds(innerX + actionW + buttonGap, y, wikiW, actionH);
            buttonRenderer.drawPlainButton(g, currentLayout.wikiButtonBounds, "Wiki", new Color(30, 25, 18, 220));
        }

        y += actionH + 5;

        int detailBottom = panelBounds.y + panelBounds.height - PANEL_PADDING - 2;
        int detailH = Math.max(0, detailBottom - y);
        Rectangle viewport = new Rectangle(innerX, y, innerW, detailH);
        currentLayout.viewportBounds.setBounds(viewport);

        List<CompactLine> lines = compactLines(current, rolling);
        int textW = Math.max(0, viewport.width - 8 - 14);
        int totalPx = 0;
        for (CompactLine line : lines) {
            totalPx += compactLineHeight(line, textW);
        }
        currentLayout.totalContentPx = totalPx;

        drawBevelBox(g, viewport, new Color(32, 24, 15, 160));
        Shape oldClip = g.getClip();
        Rectangle textClip = new Rectangle(viewport.x + 4, viewport.y + 4, Math.max(0, viewport.width - 8), Math.max(0, viewport.height - 8));
        int visiblePx = Math.max(0, textClip.height);
        compactCurrentVisibleContentPx = visiblePx;
        int maxOffsetPx = Math.max(0, totalPx - visiblePx);
        if (compactCurrentScrollPx > maxOffsetPx) {
            compactCurrentScrollPx = maxOffsetPx;
        }
        int scrollPx = compactCurrentScrollPx;

        g.setClip(textClip);
        int textY = textClip.y + fm.getAscent() + 2 - scrollPx;
        int textX = textClip.x + 4;
        for (CompactLine line : lines) {
            if (line.collectionLogPreview != null) {
                textY = CollectionLogIconGridRenderer.render(
                        g,
                        fm,
                        textX,
                        textY,
                        textW,
                        line.collectionLogPreview.getItems(),
                        plugin::getItemImage,
                        mousePoint,
                        textClip,
                        P.UI_TEXT,
                        P.UI_TEXT_DIM,
                        P.UI_EDGE_LIGHT,
                        P.UI_EDGE_DARK);
            } else {
                g.setColor(line.heading ? P.UI_GOLD : (line.dim ? P.UI_TEXT_DIM : P.UI_TEXT));
                g.drawString(TextUtils.truncateToWidth(line.text, fm, textW), textX, textY);
                textY += ROW_HEIGHT;
            }
        }
        g.setClip(oldClip);

        drawCompactScrollbar(g, viewport, totalPx, visiblePx, scrollPx);
    }

    private void drawCompactRolling(Graphics2D g, FontMetrics fm, Rectangle card) {
        g.setColor(Color.WHITE);
        g.drawString("Rolling...", card.x + 14, card.y + 10 + fm.getAscent());

        XtremeTask current = plugin.getCurrentTask();
        Font oldFont = g.getFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 17f);
        g.setFont(nameFont);
        FontMetrics nameFm = g.getFontMetrics();
        String name = computeCurrentLineForRender(current, false, nameFm);
        String drawName = TextUtils.truncateToWidth(name, nameFm, card.width - 28);
        int nameX = card.x + (card.width - nameFm.stringWidth(drawName)) / 2;
        int nameY = card.y + (card.height - nameFm.getHeight()) / 2 + nameFm.getAscent();
        g.setColor(P.UI_GOLD);
        g.drawString(drawName, nameX, nameY);
        g.setFont(oldFont);
    }

    private void drawCompactEmptyIdentity(Graphics2D g, FontMetrics fm, Rectangle card) {
        g.setColor(Color.WHITE);
        g.drawString("No current task", card.x + 14, card.y + 10 + fm.getAscent());

        XtremeTask recent = plugin.getMostRecentCompletedTask();
        CompletionInfo info = plugin.getCompletionInfo(recent);
        if (recent != null && info != null) {
            drawCompactRecentCompletionSummary(g, fm, card, recent, info, plugin.getTaskTimeTicks(recent));
        }
    }

    private void drawCompactRecentCompletionSummary(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask task, CompletionInfo info, Long ticks) {
        int x = card.x + 14;
        int y = card.y + 33 + fm.getAscent();
        int maxW = card.width - 28;
        int lineH = fm.getHeight() + 1;

        g.setColor(P.UI_GOLD);
        g.drawString(TextUtils.truncateToWidth("Most recent completed:", fm, maxW), x, y);
        y += lineH;

        g.setColor(P.UI_TEXT);
        g.drawString(TextUtils.truncateToWidth(task.getName() + compactCompletionSourceSuffix(info), fm, maxW), x, y);
        y += lineH;

        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(compactCompletionSummary(info), fm, maxW), x, y);
        y += lineH;

        if (ticks != null && ticks > 0) {
            String time = "Time spent: " + compactFormatTicks(Math.round(ticks * 0.6));
            g.drawString(TextUtils.truncateToWidth(time, fm, maxW), x, y);
        }
    }

    private void drawCompactCurrentIdentity(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask current, boolean completed, java.awt.Point mousePoint) {
        int pad = 12;
        drawCompactBadges(g, fm, card, current, mousePoint);

        int iconSize = 36;
        int iconX = card.x + (card.width - iconSize) / 2;
        int iconY = card.y + 9;
        BufferedImage taskIcon = resolveTaskIcon(current);
        if (taskIcon != null) {
            g.drawImage(taskIcon, iconX, iconY, iconSize, iconSize, null);
        }

        int textW = card.width - pad * 2;
        Font oldFont = g.getFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 17f);
        g.setFont(nameFont);
        FontMetrics nameFm = g.getFontMetrics();
        List<String> nameLines = TextUtils.wrapText(current.getName(), nameFm, textW);
        int textY = iconY + iconSize + 5 + nameFm.getAscent();
        g.setColor(P.UI_GOLD);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            String line = TextUtils.truncateToWidth(nameLines.get(i), nameFm, textW);
            int lineX = card.x + (card.width - nameFm.stringWidth(line)) / 2;
            g.drawString(line, lineX, textY);
            textY += nameFm.getHeight();
        }

        g.setFont(oldFont);
        Long ticks = plugin.getTaskTimeTicks(current);
        if (ticks != null && ticks > 0) {
            String time = compactFormatTicks(Math.round(ticks * 0.6));
            int timeX = card.x + (card.width - fm.stringWidth(time)) / 2;
            g.setColor(P.UI_TEXT_DIM);
            g.drawString(time, timeX, textY + 2);
        }

        if (completed) {
            String done = "Complete";
            int doneW = fm.stringWidth(done) + 14;
            Rectangle doneBadge = new Rectangle(card.x + card.width - doneW - pad, card.y + 7, doneW, ROW_HEIGHT + 2);
            drawBevelBox(g, doneBadge, new Color(38, 56, 32, 220));
            g.setColor(new Color(120, 210, 130, 230));
            g.drawString(done, doneBadge.x + 7, doneBadge.y + ((doneBadge.height - fm.getHeight()) / 2) + fm.getAscent());
        }
    }

    private void drawCompactBadges(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask task, java.awt.Point mousePoint) {
        int totalW = compactBadgeWidth(fm, compactSourceLabel(task.getSource()))
                + 5
                + compactBadgeWidth(fm, tierLabel(task.getTier()));
        int x = card.x + card.width - 12 - totalW;
        int y = card.y + 7;
        Rectangle sourceBadge = new Rectangle(x, y, compactBadgeWidth(fm, compactSourceLabel(task.getSource())), ROW_HEIGHT + 2);
        x = drawCompactBadge(g, fm, compactSourceLabel(task.getSource()), x, y);
        if (mousePoint != null && sourceBadge.contains(mousePoint)) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString(compactSourceHoverLabel(task.getSource()), sourceBadge.x, sourceBadge.y - 4);
        }
        drawCompactBadge(g, fm, tierLabel(task.getTier()), x + 5, y);
    }

    private int compactBadgeWidth(FontMetrics fm, String text) {
        return Math.max(32, fm.stringWidth(text) + 14);
    }

    private int drawCompactBadge(Graphics2D g, FontMetrics fm, String text, int x, int y) {
        int w = compactBadgeWidth(fm, text);
        Rectangle r = new Rectangle(x, y, w, ROW_HEIGHT + 2);
        drawBevelBox(g, r, new Color(42, 34, 22, 220));
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + ((r.height - fm.getHeight()) / 2) + fm.getAscent());
        return x + w;
    }

    private List<CompactLine> compactLines(XtremeTask current, boolean rolling) {
        List<CompactLine> lines = new ArrayList<>();
        if (rolling) {
            lines.add(new CompactLine("Rolling a new task...", false, true));
            return lines;
        }
        if (current == null) {
            String notice = Optional.ofNullable(plugin.getPendingRollSkipNotice()).orElse(plugin.getRollSkipNotice());
            if (notice != null && !notice.trim().isEmpty()) {
                lines.add(new CompactLine("Notice", true, false));
                for (String line : TextUtils.wrapText(notice.trim(), fontMetrics(), PANEL_W_COMPACT - PANEL_PADDING * 2 - 18)) {
                    lines.add(new CompactLine(line, false, false));
                }
            } else {
                lines.add(new CompactLine("No active task.", false, true));
            }
            return lines;
        }

        String desc = current.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            lines.add(new CompactLine("Description", true, false));
            lines.addAll(wrappedCompactLines(desc.trim(), false));
            lines.add(CompactLine.spacer());
        }

        lines.add(new CompactLine("Prereqs", true, false));
        List<PrerequisiteStatus> statuses = plugin.getPrerequisiteStatuses(current);
        if (statuses != null && !statuses.isEmpty()) {
            for (PrerequisiteStatus status : statuses) {
                lines.addAll(wrappedCompactLines("- " + status.getText(), true));
            }
        } else if (current.getPrereqs() != null && !current.getPrereqs().trim().isEmpty()) {
            String formatted = current.getPrereqs().replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
            for (String prereq : formatted.split("\n")) {
                lines.addAll(wrappedCompactLines("- " + prereq, true));
            }
        } else {
            lines.add(new CompactLine("None", false, true));
        }

        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreview(current);
        if (preview != null && preview.hasItems()) {
            lines.add(CompactLine.spacer());
            lines.add(new CompactLine("Eligible CLOGs", true, false));
            if (preview.showSummaryText()) {
                lines.addAll(wrappedCompactLines(preview.summaryText(), true));
            }
            if (preview.showItemList()) {
                lines.add(CompactLine.collectionLogIcons(preview));
            }
        }

        return lines;
    }

    private List<CompactLine> wrappedCompactLines(String text, boolean dim) {
        List<CompactLine> out = new ArrayList<>();
        for (String line : TextUtils.wrapText(text, fontMetrics(), PANEL_W_COMPACT - PANEL_PADDING * 2 - 26)) {
            out.add(new CompactLine(line, false, dim));
        }
        return out;
    }

    private int compactLineHeight(CompactLine line, int maxWidth) {
        if (line != null && line.collectionLogPreview != null) {
            return CollectionLogIconGridRenderer.measureHeight(line.collectionLogPreview.getItems().size(), maxWidth);
        }
        return ROW_HEIGHT;
    }

    private FontMetrics fontMetrics() {
        return client.getCanvas().getFontMetrics(FontManager.getRunescapeSmallFont());
    }

    private void drawCompactScrollbar(Graphics2D g, Rectangle viewport, int totalPx, int visiblePx, int scrollPx) {
        currentLayout.scrollbarRailBounds.setBounds(0, 0, 0, 0);
        currentLayout.scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        if (viewport.height <= 0 || visiblePx <= 0 || totalPx <= visiblePx) {
            return;
        }

        Rectangle rail = taskRowsRendererTasks.scrollbarRailBounds(viewport);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(rail.x, rail.y, rail.width, rail.height);

        float thumbRatio = (float) visiblePx / (float) totalPx;
        int thumbH = Math.min(rail.height, Math.max(12, Math.round(rail.height * thumbRatio)));
        int maxScrollPx = Math.max(1, totalPx - visiblePx);
        float scrollRatio = Math.max(0f, Math.min(1f, (float) scrollPx / (float) maxScrollPx));
        int thumbY = rail.y + (int) ((rail.height - thumbH) * scrollRatio);
        Rectangle thumb = new Rectangle(rail.x, thumbY, Math.max(0, rail.width - 1), Math.max(0, thumbH - 1));

        currentLayout.scrollbarRailBounds.setBounds(rail);
        currentLayout.scrollbarThumbBounds.setBounds(thumb);

        drawBevelBox(g, thumb, new Color(78, 62, 38, 200));

        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    private void scrollCompactCurrent(double preciseWheelRotation) {
        if (preciseWheelRotation == 0.0 || currentLayout.viewportBounds.height <= 0) {
            return;
        }

        int visiblePx = compactCurrentVisibleContentPx;
        int maxOffsetPx = Math.max(0, currentLayout.totalContentPx - visiblePx);
        if (maxOffsetPx <= 0) {
            compactCurrentScrollPx = 0;
            compactCurrentWheelRemainderPx = 0.0;
            clearCompactEdgeBounceGuard();
            return;
        }

        int beforePx = compactCurrentScrollPx;
        double pixels = (preciseWheelRotation * SCROLL_ROWS_PER_NOTCH * ROW_HEIGHT) + compactCurrentWheelRemainderPx;
        int deltaPx = pixels > 0 ? (int) Math.floor(pixels) : (int) Math.ceil(pixels);
        compactCurrentWheelRemainderPx = pixels - deltaPx;
        if (deltaPx == 0) {
            return;
        }

        int direction = Integer.compare(deltaPx, 0);
        if (shouldSuppressCompactEdgeBounce(direction, Math.abs(pixels), maxOffsetPx, System.currentTimeMillis())) {
            compactCurrentWheelRemainderPx = 0.0;
            return;
        }

        compactCurrentScrollPx = Math.max(0, Math.min(maxOffsetPx, compactCurrentScrollPx + deltaPx));
        updateCompactEdgeBounceGuard(direction, beforePx, deltaPx, maxOffsetPx);
    }

    private void setCompactCurrentScrollFraction(double fraction) {
        int maxOffset = Math.max(0, currentLayout.totalContentPx - compactCurrentVisibleContentPx);
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        compactCurrentScrollPx = (int) Math.round(clamped * maxOffset);
        compactCurrentWheelRemainderPx = 0.0;
        clearCompactEdgeBounceGuard();
    }

    private void resetCompactScroll() {
        compactCurrentScrollPx = 0;
        compactCurrentVisibleContentPx = 0;
        compactCurrentWheelRemainderPx = 0.0;
        clearCompactEdgeBounceGuard();
        currentScroll.reset();
    }

    private boolean shouldSuppressCompactEdgeBounce(int direction, double absPixels, int maxOffsetPx, long now) {
        if (direction == 0 || compactCurrentEdgeBounceDirection == 0 || now > compactCurrentEdgeBounceUntilMs) {
            clearCompactEdgeBounceGuard();
            return false;
        }

        boolean atLockedEdge = compactCurrentEdgeBounceDirection > 0
                ? compactCurrentScrollPx >= maxOffsetPx
                : compactCurrentScrollPx <= 0;
        if (!atLockedEdge) {
            clearCompactEdgeBounceGuard();
            return false;
        }

        if (direction == compactCurrentEdgeBounceDirection) {
            compactCurrentEdgeBounceUntilMs = now + COMPACT_EDGE_BOUNCE_SUPPRESS_MS;
            compactCurrentEdgeBounceOppositePx = 0.0;
            return false;
        }

        compactCurrentEdgeBounceOppositePx += absPixels;
        if (compactCurrentEdgeBounceOppositePx < COMPACT_EDGE_BOUNCE_RELEASE_PX) {
            return true;
        }

        clearCompactEdgeBounceGuard();
        return false;
    }

    private void updateCompactEdgeBounceGuard(int direction, int beforePx, int deltaPx, int maxOffsetPx) {
        long now = System.currentTimeMillis();
        if (direction > 0 && compactCurrentScrollPx >= maxOffsetPx && beforePx + deltaPx >= maxOffsetPx) {
            compactCurrentEdgeBounceDirection = 1;
            compactCurrentEdgeBounceUntilMs = now + COMPACT_EDGE_BOUNCE_SUPPRESS_MS;
            compactCurrentEdgeBounceOppositePx = 0.0;
        } else if (direction < 0 && compactCurrentScrollPx <= 0 && beforePx + deltaPx <= 0) {
            compactCurrentEdgeBounceDirection = -1;
            compactCurrentEdgeBounceUntilMs = now + COMPACT_EDGE_BOUNCE_SUPPRESS_MS;
            compactCurrentEdgeBounceOppositePx = 0.0;
        } else if (compactCurrentScrollPx > 0 && compactCurrentScrollPx < maxOffsetPx) {
            clearCompactEdgeBounceGuard();
        }
    }

    private void clearCompactEdgeBounceGuard() {
        compactCurrentEdgeBounceDirection = 0;
        compactCurrentEdgeBounceUntilMs = 0L;
        compactCurrentEdgeBounceOppositePx = 0.0;
    }

    private void drawPanelModeToggle(Graphics2D g, FontMetrics fm, net.runelite.api.Point rlMouse) {
        boolean hovered = rlMouse != null && panelModeToggleBounds.contains(rlMouse.getX(), rlMouse.getY());
        g.setColor(hovered ? new Color(22, 17, 11, 235) : new Color(18, 14, 9, 185));
        g.fillRoundRect(
                panelModeToggleBounds.x - 2,
                panelModeToggleBounds.y - 2,
                panelModeToggleBounds.width + 4,
                panelModeToggleBounds.height + 4,
                4,
                4
        );
        g.setColor(new Color(P.UI_EDGE_LIGHT.getRed(), P.UI_EDGE_LIGHT.getGreen(), P.UI_EDGE_LIGHT.getBlue(), hovered ? 95 : 55));
        g.drawRoundRect(
                panelModeToggleBounds.x - 2,
                panelModeToggleBounds.y - 2,
                panelModeToggleBounds.width + 4,
                panelModeToggleBounds.height + 4,
                4,
                4
        );

        g.setColor(hovered ? Color.WHITE : new Color(200, 200, 200, 180));
        int cx = panelModeToggleBounds.x + panelModeToggleBounds.width / 2;
        int cy = panelModeToggleBounds.y + panelModeToggleBounds.height / 2;
        int arm = panelModeToggleBounds.width / 2 - 2;
        int x = cx - arm;
        int y = cy - arm;
        int w = arm * 2;
        int h = arm * 2;
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (compactPanelMode) {
            // Expand/full-view corners.
            int leg = Math.max(4, arm - 1);
            g.drawLine(x, y + leg, x, y);
            g.drawLine(x, y, x + leg, y);
            g.drawLine(x + w - leg, y, x + w, y);
            g.drawLine(x + w, y, x + w, y + leg);
            g.drawLine(x, y + h - leg, x, y + h);
            g.drawLine(x, y + h, x + leg, y + h);
            g.drawLine(x + w - leg, y + h, x + w, y + h);
            g.drawLine(x + w, y + h, x + w, y + h - leg);
        } else {
            // Collapse/compact-view inward corners.
            int inset = 4;
            g.drawLine(x, y, x + inset, y);
            g.drawLine(x, y, x, y + inset);
            g.drawLine(x + w - inset, y, x + w, y);
            g.drawLine(x + w, y, x + w, y + inset);
            g.drawLine(x, y + h - inset, x, y + h);
            g.drawLine(x, y + h, x + inset, y + h);
            g.drawLine(x + w - inset, y + h, x + w, y + h);
            g.drawLine(x + w, y + h - inset, x + w, y + h);
            g.drawLine(x + inset + 1, y + inset + 1, x + w - inset - 1, y + h - inset - 1);
            g.drawLine(x + w - inset - 1, y + inset + 1, x + inset + 1, y + h - inset - 1);
        }
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        if (hovered) {
            drawSmallTooltip(g, fm, compactPanelMode ? "Full\nview" : "Compact view", panelModeToggleBounds);
        }
    }

    private void drawSmallTooltip(Graphics2D g, FontMetrics fm, String text, Rectangle anchor) {
        int padX = 0;
        int padY = 4;
        String[] lines = text.split("\\n", -1);
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, fm.stringWidth(line));
        }
        int w = textW + padX * 2;
        int h = fm.getHeight() * lines.length + padY * 2;
        int x = anchor.x;
        int y = anchor.y + anchor.height + 5;
        if (x + w > panelBounds.x + panelBounds.width - PANEL_PADDING) {
            x = panelBounds.x + panelBounds.width - PANEL_PADDING - w;
        }
        if (y + h > panelBounds.y + panelBounds.height - PANEL_PADDING) {
            y = anchor.y - h - 5;
        }

        g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 190));
        int textY = y + padY + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, x + padX, textY);
            textY += fm.getHeight();
        }
    }

    private void resetCurrentLayoutBounds() {
        currentLayout.wikiButtonBounds.setBounds(0, 0, 0, 0);
        currentLayout.rollButtonBounds.setBounds(0, 0, 0, 0);
        currentLayout.completeButtonBounds.setBounds(0, 0, 0, 0);
        currentLayout.rollSourceIconBounds.setBounds(0, 0, 0, 0);
        currentLayout.viewportBounds.setBounds(0, 0, 0, 0);
        currentLayout.scrollbarRailBounds.setBounds(0, 0, 0, 0);
        currentLayout.scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        currentLayout.totalContentPx = 0;
    }

    private void clearFullPanelTabBounds() {
        currentTabBounds.setBounds(0, 0, 0, 0);
        tasksTabBounds.setBounds(0, 0, 0, 0);
        rulesTabBounds.setBounds(0, 0, 0, 0);
        taskListViewportBounds.setBounds(0, 0, 0, 0);
        taskScrollbarRailBounds.setBounds(0, 0, 0, 0);
        taskScrollbarThumbBounds.setBounds(0, 0, 0, 0);
        rulesViewportBounds.setBounds(0, 0, 0, 0);
    }

    private String compactSourceLabel(TaskSource source) {
        if (source == TaskSource.COMBAT_ACHIEVEMENT) {
            return "CA";
        }
        if (source == TaskSource.COLLECTION_LOG) {
            return "CL";
        }
        if (source == TaskSource.DIARY_ACHIEVEMENT) {
            return "AD";
        }
        return "Task";
    }

    private String compactSourceHoverLabel(TaskSource source) {
        if (source == TaskSource.COMBAT_ACHIEVEMENT) {
            return "Combat Achievement";
        }
        if (source == TaskSource.COLLECTION_LOG) {
            return "Collection Log";
        }
        if (source == TaskSource.DIARY_ACHIEVEMENT) {
            return "Achievement Diary";
        }
        return "Task";
    }

    private static String compactFormatTicks(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes < 60) return remSeconds > 0 ? minutes + "m " + remSeconds + "s" : minutes + "m";
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (hours < 24) return remMinutes > 0 ? hours + "h " + remMinutes + "m" : hours + "h";
        long days = hours / 24;
        long remHours = hours % 24;
        return remHours > 0 ? days + "d " + remHours + "h" : days + "d";
    }

    private static String compactCompletionSummary(CompletionInfo info) {
        if (info == null || info.timestamp <= 0) {
            return "Completed: date unknown";
        }
        return "Completed: " + new java.text.SimpleDateFormat("MMM d, h:mm a").format(new Date(info.timestamp));
    }

    private static String compactCompletionSourceSuffix(CompletionInfo info) {
        if (info == null || info.timestamp <= 0 || info.source == null) {
            return "";
        }
        return info.source == CompletionInfo.Source.SYNCED ? " (synced)" : "";
    }

    private static final class CompactLine {
        private final String text;
        private final boolean heading;
        private final boolean dim;
        private final CollectionLogRequirementPreview collectionLogPreview;

        private CompactLine(String text, boolean heading, boolean dim) {
            this.text = text == null ? "" : text;
            this.heading = heading;
            this.dim = dim;
            this.collectionLogPreview = null;
        }

        private CompactLine(CollectionLogRequirementPreview collectionLogPreview) {
            this.text = "";
            this.heading = false;
            this.dim = false;
            this.collectionLogPreview = collectionLogPreview;
        }

        private static CompactLine spacer() {
            return new CompactLine("", false, true);
        }

        private static CompactLine collectionLogIcons(CollectionLogRequirementPreview collectionLogPreview) {
            return new CompactLine(collectionLogPreview);
        }
    }

    /** Height in px available for the scrollable body on the Current tab. */
    private int computeCurrentViewportH(Graphics2D g, FontMetrics fm, Rectangle pb, int cursorYBaseline) {
        // Only reserve hint line at the bottom; button lives inside scroll body
        int hintFooterH = fm.getHeight() + PANEL_PADDING + 8;
        int vpBottom = pb.y + pb.height - hintFooterH;
        int vpTop = cursorYBaseline - fm.getAscent();
        return Math.max(10, vpBottom - vpTop);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        net.runelite.api.Point rlMouse = mouseCanvasPositionForPanelRender();
        int hoverX = rlMouse == null ? -1 : rlMouse.getX();
        int hoverY = rlMouse == null ? -1 : rlMouse.getY();

        tasksTabRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                tasksTabState,
                controlsRendererTasks,
                taskRowsRendererTasks,
                plugin,
                animations,
                TIER_TABS,
                activeTierTab,
                this::getSortedTasksForTier,
                hoverX,
                hoverY,
                keyboardHintsOpen
        );
    }


    // ----------------------------
    // RULES TAB
    // ----------------------------
    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        RulesTabLayout layout = rulesTabRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                rulesScroll.offsetRows,
                rulesSubTab,
                plugin.getLastCombatAchievementSyncResult(),
                plugin.getLastCombatAchievementSyncResultAtLocalTime(),
                plugin.getLastCollectionLogSyncResult(),
                plugin.getLastCollectionLogSyncResultAtLocalTime(),
                plugin.getLastCombatAchievementSyncedTaskNames(),
                plugin.getLastCollectionLogSyncedTaskNames(),
                plugin.isCombatAchievementSyncedTasksExpanded(),
                plugin.isCollectionLogSyncedTasksExpanded(),
                plugin.isCollectionLogSyncPending(),
                plugin.getSyncMismatchTasks(TaskSource.COMBAT_ACHIEVEMENT).size(),
                plugin.getSyncMismatchTasks(TaskSource.COLLECTION_LOG).size()
        );

        rulesLayout.viewportBounds.setBounds(layout.viewportBounds);
        rulesLayout.reloadButtonBounds.setBounds(layout.reloadButtonBounds);
        rulesLayout.totalContentRows = layout.totalContentRows;
        rulesLayout.taskerFaqLinkBounds.setBounds(layout.taskerFaqLinkBounds);
        rulesLayout.githubReadmeLinkBounds.setBounds(layout.githubReadmeLinkBounds);
        rulesLayout.syncClogsButtonBounds.setBounds(layout.syncClogsButtonBounds);
        rulesLayout.syncCAsButtonBounds.setBounds(layout.syncCAsButtonBounds);
        rulesLayout.syncCaReviewButtonBounds.setBounds(layout.syncCaReviewButtonBounds);
        rulesLayout.syncCaReviewIgnoreButtonBounds.setBounds(layout.syncCaReviewIgnoreButtonBounds);
        rulesLayout.syncClogReviewButtonBounds.setBounds(layout.syncClogReviewButtonBounds);
        rulesLayout.syncClogReviewIgnoreButtonBounds.setBounds(layout.syncClogReviewIgnoreButtonBounds);
        rulesLayout.syncCaMarkedTasksToggleBounds.setBounds(layout.syncCaMarkedTasksToggleBounds);
        rulesLayout.syncClogMarkedTasksToggleBounds.setBounds(layout.syncClogMarkedTasksToggleBounds);
        rulesLayout.scrollbarRailBounds.setBounds(layout.scrollbarRailBounds);
        rulesLayout.scrollbarThumbBounds.setBounds(layout.scrollbarThumbBounds);
        rulesLayout.subTabRulesBounds.setBounds(layout.subTabRulesBounds);
        rulesLayout.subTabDataSyncsBounds.setBounds(layout.subTabDataSyncsBounds);

        rulesViewportBounds.setBounds(layout.viewportBounds);

        if (rulesLayout.syncClogsButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncClogsButtonBounds, "SYNC CLOGs + ADs", P.BTN_DISABLED_BG);
        }
        if (rulesLayout.syncCAsButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCAsButtonBounds, "SYNC CAs", P.BTN_DISABLED_BG);
        }
        if (rulesLayout.syncCaReviewButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCaReviewButtonBounds, "Review", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
        }
        if (rulesLayout.syncCaReviewIgnoreButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCaReviewIgnoreButtonBounds, "Ignore", P.BTN_DISABLED_BG, P.UI_TEXT_DIM, null);
        }
        if (rulesLayout.syncClogReviewButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncClogReviewButtonBounds, "Review", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
        }
        if (rulesLayout.syncClogReviewIgnoreButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncClogReviewIgnoreButtonBounds, "Ignore", P.BTN_DISABLED_BG, P.UI_TEXT_DIM, null);
        }
        if (rulesLayout.taskerFaqLinkBounds.width > 0) {
            Rectangle lb = rulesLayout.taskerFaqLinkBounds;
            FontMetrics lfm = g.getFontMetrics();
            String linkLabel = "TaskerFAQ";
            int textW = lfm.stringWidth(linkLabel);
            int textX = lb.x;
            int textY = lb.y + ((lb.height - lfm.getHeight()) / 2) + lfm.getAscent();
            g.setColor(P.UI_TEXT);
            g.drawString(linkLabel, textX, textY);
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 180));
            g.drawLine(textX, textY + 2, textX + textW, textY + 2);
        }
        if (rulesLayout.githubReadmeLinkBounds.width > 0) {
            Rectangle lb = rulesLayout.githubReadmeLinkBounds;
            FontMetrics lfm = g.getFontMetrics();
            String linkLabel = "Github README";
            int textW = lfm.stringWidth(linkLabel);
            int textX = lb.x;
            int textY = lb.y + ((lb.height - lfm.getHeight()) / 2) + lfm.getAscent();
            g.setColor(P.UI_TEXT);
            g.drawString(linkLabel, textX, textY);
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 180));
            g.drawLine(textX, textY + 2, textX + textW, textY + 2);
        }

        int rb = rulesTabRenderer.rowBlock();
        int visible = rulesScroll.visibleRows(rulesLayout.viewportBounds.height, rb);

        if (rulesLayout.totalContentRows > visible && visible > 0 && rulesLayout.viewportBounds.height > 0) {
            // Use the 520-width rows renderer for consistent scrollbar styling/placement
            taskRowsRendererTasks.drawScrollbar(g, rulesLayout.totalContentRows, visible, rulesScroll.offsetRows, rulesLayout.viewportBounds);
            rulesLayout.scrollbarRailBounds.setBounds(taskRowsRendererTasks.scrollbarRailBounds(rulesLayout.viewportBounds));
            rulesLayout.scrollbarThumbBounds.setBounds(taskRowsRendererTasks.scrollbarThumbBounds(
                    rulesLayout.totalContentRows,
                    visible,
                    rulesScroll.offsetRows,
                    rulesLayout.viewportBounds
            ));
        } else {
            rulesLayout.scrollbarRailBounds.setBounds(0, 0, 0, 0);
            rulesLayout.scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        }
    }

    // --------- rolling line logic ---------
    private String computeCurrentLineForRender(XtremeTask current, boolean currentCompleted, FontMetrics fm) {
        final int maxW = panelInnerTextMaxWidth();

        if (!animations.isRolling()) {
            if (current == null) {
                return TextUtils.truncateToWidth("Click \"Roll task\" to get a task", fm, maxW);
            }

            return TextUtils.truncateToWidth(current.getName(), fm, maxW); // prefix drawn by renderer
        }

        TaskTier tier = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = getTasksForTier(tier);
        if (pool.isEmpty()) {
            return TextUtils.truncateToWidth("Rolling...", fm, maxW);
        }

        long elapsed = animations.rollElapsedMs();
        float t = Math.min(1f, (float) elapsed / (float) ROLL_ANIM_MS);
        float eased = 1f - (float) Math.pow(1f - t, 3);

        int spins = pool.size() * 2;
        int idx = Math.min(pool.size() - 1, ((int) (eased * spins)) % pool.size());

        String name = pool.get(idx).getName();
        if (name == null || name.trim().isEmpty()) name = "Rolling...";

        return TextUtils.truncateToWidth(name, fm, maxW); // "Rolling..." drawn by renderer
    }


    // --------- keyboard navigation ---------
    private boolean handleTasksKey(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_LEFT) {
            shiftTier(-1);
            return true;
        }
        if (code == KeyEvent.VK_RIGHT) {
            shiftTier(1);
            return true;
        }

        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
            return toggleSelectedTaskFromKeyboard();
        }

        // S toggles completion sort (new model)
        if (code == KeyEvent.VK_S) {
            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                return false;
            }

            if (!taskQuery.sortByCompletion) {
                taskQuery.sortByCompletion = true;
            } else {
                taskQuery.completedFirst = !taskQuery.completedFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // T toggles tier sort
        if (code == KeyEvent.VK_T) {
            if (taskQuery.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
                return false;
            }

            if (!taskQuery.sortByTier) {
                taskQuery.sortByTier = true;
            } else {
                taskQuery.easyTierFirst = !taskQuery.easyTierFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // D toggles time-completed sort (requires Complete filter)
        if (code == KeyEvent.VK_D) {
            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.COMPLETE) {
                return false;
            }

            if (!taskQuery.sortByDate) {
                taskQuery.sortByDate = true;
                taskQuery.newestFirst = true;
            } else {
                taskQuery.newestFirst = !taskQuery.newestFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // M toggles time-spent sort (requires Complete filter)
        if (code == KeyEvent.VK_M) {
            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.COMPLETE) {
                return false;
            }

            if (!taskQuery.sortByTimeTicks) {
                taskQuery.sortByTimeTicks = true;
                taskQuery.longestFirst = true;
            } else {
                taskQuery.longestFirst = !taskQuery.longestFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // R resets sorts (optional)
        if (code == KeyEvent.VK_R) {
            boolean changed = false;

            if (taskQuery.sortByCompletion) {
                taskQuery.sortByCompletion = false;
                changed = true;
            }
            if (taskQuery.sortByTier) {
                taskQuery.sortByTier = false;
                changed = true;
            }
            if (taskQuery.sortByDate) {
                taskQuery.sortByDate = false;
                changed = true;
            }
            if (taskQuery.sortByTimeTicks) {
                taskQuery.sortByTimeTicks = false;
                changed = true;
            }

            if (changed) {
                resetTaskListViewAfterQueryChange();
                return true;
            }
            return false;
        }

        if (code == KeyEvent.VK_1) {
            taskQuery.selectAllSources();
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_2) {
            taskQuery.toggleSource(CA);
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_3) {
            taskQuery.toggleSource(CLOGS);
            resetTaskListViewAfterQueryChange();
            return true;
        }
        if (code == KeyEvent.VK_4) {
            taskQuery.toggleSource(DAS);
            resetTaskListViewAfterQueryChange();
            return true;
        }
// Status filter
        if (code == KeyEvent.VK_Q) {
            taskQuery.statusFilter = TaskListQuery.StatusFilter.ALL;
            resetTaskListViewAfterQueryChange();
            return true;
        }
        if (code == KeyEvent.VK_W) {
            taskQuery.statusFilter = (taskQuery.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE) ? TaskListQuery.StatusFilter.ALL : TaskListQuery.StatusFilter.INCOMPLETE;

            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                taskQuery.sortByCompletion = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }
        if (code == KeyEvent.VK_E) {
            taskQuery.statusFilter = (taskQuery.statusFilter == TaskListQuery.StatusFilter.COMPLETE) ? TaskListQuery.StatusFilter.ALL : TaskListQuery.StatusFilter.COMPLETE;

            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                taskQuery.sortByCompletion = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // Tier scope toggle
        if (code == KeyEvent.VK_A) {
            taskQuery.tierScope = (taskQuery.tierScope == TaskListQuery.TierScope.ALL_TIERS) ? TaskListQuery.TierScope.THIS_TIER : TaskListQuery.TierScope.ALL_TIERS;

            if (taskQuery.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
                taskQuery.sortByTier = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && taskDetailsPopup.isOpen()) {
            taskDetailsPopup.close();
            return true;
        }


        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        if (tasks.isEmpty()) {
            return false;
        }

        selectionModel.setActiveTier(activeTierTab);

        if (code == KeyEvent.VK_UP) {
            selectionModel.moveUp(tasks.size());
            return true;
        }
        if (code == KeyEvent.VK_DOWN) {
            selectionModel.moveDown(tasks.size());
            return true;
        }
        if (code == KeyEvent.VK_PAGE_UP) {
            selectionModel.pageUp(tasks.size(), 10);
            return true;
        }
        if (code == KeyEvent.VK_PAGE_DOWN) {
            selectionModel.pageDown(tasks.size(), 10);
            return true;
        }

        return false;
    }

    private boolean toggleSelectedTaskFromKeyboard()
    {
        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        if (tasks.isEmpty()) {
            return false;
        }

        selectionModel.setActiveTier(activeTierTab);
        int idx = selectionModel.getSelectedIndex();
        idx = Math.max(0, Math.min(idx, tasks.size() - 1));

        XtremeTask task = tasks.get(idx);
        if (task == null) {
            return false;
        }

        boolean condensedRows = useCondensedTaskRows();
        boolean wasDone = condensedRows
                ? plugin.getTaskGroupProgress(task).isComplete()
                : plugin.isTaskCompleted(task);
        if (!wasDone) {
            animations.startCompletionAnim(task.getId());
        }

        if (wasDone) {
            boolean groupedTask = condensedRows
                    && plugin.getTaskGroupProgress(task) != null
                    && plugin.getTaskGroupProgress(task).isGrouped();
            if (groupedTask || !plugin.skipSingleIncompleteConfirmation()) {
                pendingMarkAllIncompleteTask = task;
                pendingMarkAllIncompleteGroupMode = groupedTask;
                markIncompleteDontShowChecked = false;
            } else {
                plugin.toggleTaskCompletedAndPersist(task);
            }
        } else if (condensedRows) {
            if (plugin.getTaskGroupProgress(task).isGrouped()) {
                plugin.toggleTaskGroupProgressAndPersist(task);
            } else {
                plugin.toggleTaskCompletedAndPersist(task);
            }
        } else {
            plugin.toggleTaskCompletedAndPersist(task);
        }

        List<XtremeTask> tasksAfter = getSortedTasksForTier(activeTierTab);
        selectionModel.setSelectionToTask(activeTierTab, tasksAfter, task);
        return true;
    }


    private boolean handleCurrentKey(KeyEvent e) {
        int code = e.getKeyCode();

        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        if (code == KeyEvent.VK_R && rollEnabled) {
            animations.startRoll();
            plugin.rollRandomTaskAndPersist();
            return true;
        }

        if (code == KeyEvent.VK_C && completeEnabled) {
            animations.startCompletionAnim(current.getId());
            plugin.completeCurrentTaskAndPersist();
            return true;
        }

        if (code == KeyEvent.VK_W && current != null) {
            String url = current.getWikiUrl();
            if (url != null && !url.trim().isEmpty()) {
                LinkBrowser.browse(url);
                return true;
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && taskDetailsPopup.isOpen()) {
            taskDetailsPopup.close();
            return true;
        }


        return false;
    }

    private void shiftTier(int delta) {
        int idx = TIER_TABS.indexOf(activeTierTab);
        if (idx < 0) idx = 0;

        int next = Math.max(0, Math.min(TIER_TABS.size() - 1, idx + delta));
        activeTierTab = TIER_TABS.get(next);
        resetTaskListViewAfterQueryChange();
    }


    private void resetTaskListViewAfterQueryChange() {
        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        taskListView.resetAfterQueryChange(activeTierTab, tasks, taskQuery.completedFirst, plugin::isTaskCompleted);
    }

    // --------- data + pipeline ---------
    private List<XtremeTask> getTasksForTier(TaskTier tier) {
        List<XtremeTask> out = new ArrayList<>();
        for (XtremeTask t : plugin.getDummyTasks()) {
            if (t.getTier() == tier) {
                out.add(t);
            }
        }
        return out;
    }

    private List<XtremeTask> getSortedTasksForTier(TaskTier tier) {
        List<XtremeTask> base = getTasksForScope(taskQuery.tierScope, activeTierTab);

        List<XtremeTask> sorted = TaskListPipeline.apply(base, taskQuery, plugin::isTaskCompleted, plugin::isNewTask, plugin::getCompletionInfo, plugin::getTaskTimeTicks);
        return useCondensedTaskRows() ? TaskGroupUtils.collapsePreservingOrder(sorted) : sorted;
    }

    private boolean useCondensedTaskRows()
    {
        return plugin.condenseRepeatedTasks() && !taskQuery.sortByDate && !taskQuery.sortByTimeTicks;
    }

    // -----------------------------
    // OverlayInputAccess bridge
    // -----------------------------
    private OverlayInputAccess buildInputAccess() {
        return new OverlayInputAccess() {
            @Override
            public Client client() {
                return client;
            }

            @Override
            public XtremeTaskerPlugin plugin() {
                return (XtremeTaskerPlugin) plugin;
            }

            @Override
            public OverlayAnimations animations() {
                return animations;
            }

            @Override
            public boolean isPanelOpen() {
                return panelOpen;
            }

            @Override
            public TaskControlsLayout controlsLayout() {
                return controls;
            }

            @Override
            public void setPanelOpen(boolean open) {
                panelOpen = open;
            }

            @Override
            public boolean isCompactPanelMode() {
                return compactPanelMode;
            }

            @Override
            public void setCompactPanelMode(boolean compact) {
                recenterPanelForMode(compact);
                compactPanelMode = compact;
                keyboardHintsOpen = false;
                taskDetailsPopup.close();
                syncMismatchReviewOpen = false;
                syncMismatchDescriptionTask = null;
                currentScroll.reset();
                resetCompactScroll();
                if (compact) {
                    activeTab = XtremeTaskerOverlay.MainTab.CURRENT;
                }
            }

            @Override
            public Rectangle panelModeToggleBounds() {
                return panelModeToggleBounds;
            }

            @Override
            public boolean isDraggingPanel() {
                return draggingPanel;
            }

            @Override
            public void setDraggingPanel(boolean dragging) {
                draggingPanel = dragging;
            }

            @Override
            public void setDragOffset(int dx, int dy) {
                dragOffsetX = dx;
                dragOffsetY = dy;
            }

            @Override
            public int dragOffsetX() {
                return dragOffsetX;
            }

            @Override
            public int dragOffsetY() {
                return dragOffsetY;
            }

            @Override
            public void setPanelOverride(Integer x, Integer y) {
                panelXOverride = x;
                panelYOverride = y;
            }

            @Override
            public MainTab activeTab() {
                switch (activeTab) {
                    case TASKS:
                        return MainTab.TASKS;
                    case RULES:
                        return MainTab.RULES;
                    default:
                        return MainTab.CURRENT;
                }
            }

            @Override
            public void setActiveTab(MainTab tab) {
                keyboardHintsOpen = false;
                keyboardHintsButtonBounds.setBounds(0, 0, 0, 0);
                keyboardHintsPopupBounds.setBounds(0, 0, 0, 0);
                taskViewModeBounds.setBounds(0, 0, 0, 0);
                switch (tab) {
                    case CURRENT:
                        activeTab = XtremeTaskerOverlay.MainTab.CURRENT;
                        break;

                    case TASKS:
                        activeTab = XtremeTaskerOverlay.MainTab.TASKS;

                        // Default Tasks tab to the tier the user is currently working on
                        TaskTier tier = null;
                        XtremeTask cur = plugin.getCurrentTask();
                        if (cur != null) {
                            tier = cur.getTier();
                        }
                        if (tier == null) {
                            tier = plugin.getCurrentTier();
                        }
                        if (tier != null) {
                            activeTierTab = tier;
                        }

                        // Default source filter to match the roll source config (only on first visit)
                        if (!tasksSourceFilterInitialized) {
                            tasksSourceFilterInitialized = true;
                            XtremeTaskerConfig.RollSourceFilter rsf = plugin.getRollSourceFilter();
                            if (rsf == XtremeTaskerConfig.RollSourceFilter.CA_ONLY) {
                                taskQuery.setOnlySource(TaskListQuery.SourceFilter.CA);
                            } else if (rsf == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY) {
                                taskQuery.selectAllSources();
                            } else {
                                taskQuery.selectAllSources();
                            }
                        }

                        // Reset selection/scroll for the new tier
                        XtremeTaskerOverlay.this.resetTaskListViewAfterQueryChange();
                        break;

                    case RULES:
                        activeTab = XtremeTaskerOverlay.MainTab.RULES;
                        break;
                }
            }


            @Override
            public TaskTier activeTier() {
                return activeTierTab;
            }

            @Override
            public void setActiveTier(TaskTier tier) {
                activeTierTab = tier;
            }

            @Override
            public Rectangle iconBounds() {
                return iconBounds;
            }

            @Override
            public Rectangle panelBounds() {
                return panelBounds;
            }

            @Override
            public Rectangle panelDragBarBounds() {
                return panelDragBarBounds;
            }

            @Override
            public Rectangle panelCloseBounds() {
                return panelCloseBounds;
            }

            @Override
            public Rectangle currentTabBounds() {
                return currentTabBounds;
            }

            @Override
            public Rectangle tasksTabBounds() {
                return tasksTabBounds;
            }

            @Override
            public Rectangle rulesTabBounds() {
                return rulesTabBounds;
            }

            @Override
            public Rectangle taskListViewportBounds() {
                return taskListViewportBounds;
            }

            @Override
            public Rectangle taskScrollbarRailBounds() {
                return taskScrollbarRailBounds;
            }

            @Override
            public Rectangle taskScrollbarThumbBounds() {
                return taskScrollbarThumbBounds;
            }

            @Override
            public Rectangle rulesViewportBounds() {
                return rulesViewportBounds;
            }

            @Override
            public Map<TaskTier, Rectangle> tierTabBounds() {
                return tierTabBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> taskRowBounds() {
                return taskRowBounds;
            }

            @Override
            public CurrentTabLayout currentLayout() {
                return currentLayout;
            }

            @Override
            public RulesTabLayout rulesLayout() {
                return rulesLayout;
            }

            @Override
            public RulesTabLayout.SubTab rulesSubTab() {
                return rulesSubTab;
            }

            @Override
            public void setRulesSubTab(RulesTabLayout.SubTab subTab) {
                if (subTab != rulesSubTab) {
                    rulesSubTab = subTab;
                    rulesScroll.reset();
                }
            }

            @Override
            public void toggleCaSyncedTasksExpanded() {
                plugin.setCombatAchievementSyncedTasksExpanded(!plugin.isCombatAchievementSyncedTasksExpanded());
            }

            @Override
            public void toggleClogSyncedTasksExpanded() {
                plugin.setCollectionLogSyncedTasksExpanded(!plugin.isCollectionLogSyncedTasksExpanded());
            }

            @Override
            public void setCaSyncedTasksExpanded(boolean expanded) {
                plugin.setCombatAchievementSyncedTasksExpanded(expanded);
            }

            @Override
            public void setClogSyncedTasksExpanded(boolean expanded) {
                plugin.setCollectionLogSyncedTasksExpanded(expanded);
            }

            @Override
            public void openSyncMismatchReview() {
                openSyncMismatchReview(null);
            }

            @Override
            public void openSyncMismatchReview(TaskSource source) {
                if (!plugin.getSyncMismatchTasks(source).isEmpty()) {
                    syncMismatchReviewSource = source;
                    syncMismatchReviewOpen = true;
                    syncMismatchScroll.reset();
                    selectedSyncMismatchTaskIds.clear();
                    syncMismatchApplyConfirmOpen = false;
                }
            }

            @Override
            public void closeSyncMismatchReview() {
                syncMismatchReviewOpen = false;
                syncMismatchReviewSource = null;
                selectedSyncMismatchTaskIds.clear();
                syncMismatchApplyConfirmOpen = false;
                syncMismatchDescriptionTask = null;
            }

            @Override
            public TaskSource syncMismatchReviewSource() {
                return syncMismatchReviewSource;
            }

            @Override
            public TaskListQuery taskQuery() {
                return taskQuery;
            }

            @Override
            public TaskSelectionModel selectionModel() {
                return selectionModel;
            }

            @Override
            public TaskListScrollController tasksScroll() {
                return tasksScroll;
            }

            @Override
            public TaskListScrollController rulesScroll() {
                return rulesScroll;
            }

            @Override
            public TaskListScrollController currentScroll() {
                return currentScroll;
            }

            @Override
            public void scrollCompactCurrent(double preciseWheelRotation) {
                XtremeTaskerOverlay.this.scrollCompactCurrent(preciseWheelRotation);
            }

            @Override
            public void setCompactCurrentScrollFraction(double fraction) {
                XtremeTaskerOverlay.this.setCompactCurrentScrollFraction(fraction);
            }

            @Override
            public Rectangle currentViewportBounds() {
                return currentLayout.viewportBounds;
            }

            @Override
            public int currentRowBlock() {
                return XtremeTaskerOverlay.this.currentRowBlock();
            }

            @Override
            public TaskListViewController taskListView() {
                return taskListView;
            }

            @Override
            public void resetTaskListViewAfterQueryChange() {
                XtremeTaskerOverlay.this.resetTaskListViewAfterQueryChange();
            }

            @Override
            public int taskRowBlock() {
                return XtremeTaskerOverlay.this.tasksRowBlock();
            }

            @Override
            public boolean useCondensedTaskRows() {
                return XtremeTaskerOverlay.this.useCondensedTaskRows();
            }

            @Override
            public void shiftTier(int delta) {
                XtremeTaskerOverlay.this.shiftTier(delta);
            }

            @Override
            public boolean handleTasksKey(KeyEvent e) {
                return XtremeTaskerOverlay.this.handleTasksKey(e);
            }

            @Override
            public boolean handleCurrentKey(KeyEvent e) {
                return XtremeTaskerOverlay.this.handleCurrentKey(e);
            }


            @Override
            public List<XtremeTask> getSortedTasksForTier(TaskTier tier) {
                return XtremeTaskerOverlay.this.getSortedTasksForTier(tier);
            }

            @Override
            public Map<XtremeTask, Rectangle> taskCheckboxBounds() {
                return taskCheckboxBounds;
            }


            // -----------------------------
            // rowBlock accessors for wheel
            // -----------------------------
            @Override
            public int tasksRowBlock() {
                return XtremeTaskerOverlay.this.tasksRowBlock();
            }

            @Override
            public int rulesRowBlock() {
                return XtremeTaskerOverlay.this.rulesRowBlock();
            }

            @Override
            public boolean isTaskDetailsOpen() {
                return taskDetailsPopup.isOpen();
            }

            @Override
            public void openTaskDetails(XtremeTask task) {
                taskDetailsPopup.open(task);
            }

            @Override
            public void closeTaskDetails() {
                taskDetailsPopup.close();
            }

            @Override
            public XtremeTask taskDetailsTask() {
                return taskDetailsPopup.task();
            }

            @Override
            public Rectangle taskDetailsBounds() {
                return taskDetailsPopup.bounds();
            }

            @Override
            public Rectangle taskDetailsViewportBounds() {
                return taskDetailsPopup.viewportBounds();
            }

            @Override
            public int taskDetailsTotalContentRows() {
                return taskDetailsPopup.totalContentRows();
            }

            @Override
            public int taskDetailsRowBlock() {
                return ROW_HEIGHT; // popup is rendered unscaled in screen coordinates
            }

            @Override
            public TaskListScrollController taskDetailsScroll() {
                return taskDetailsPopup.scroll();
            }

            @Override
            public Rectangle taskDetailsCloseBounds() {
                return taskDetailsPopup.closeBounds();
            }

            @Override
            public Rectangle taskDetailsWikiBounds() {
                return taskDetailsPopup.wikiBounds();
            }

            @Override
            public Rectangle taskDetailsToggleBounds() {
                return taskDetailsPopup.toggleBounds();
            }

            @Override
            public Rectangle taskDetailsScrollbarRailBounds() {
                return taskDetailsPopup.scrollbarRailBounds();
            }

            @Override
            public Rectangle taskDetailsScrollbarThumbBounds() {
                return taskDetailsPopup.scrollbarThumbBounds();
            }

            @Override
            public Rectangle taskDetailsDecrementGroupBounds() {
                return taskDetailsPopup.decrementGroupBounds();
            }

            @Override
            public Rectangle taskDetailsIncrementGroupBounds() {
                return taskDetailsPopup.incrementGroupBounds();
            }

            @Override
            public Map<XtremeTask, Rectangle> taskDetailsInstanceRemoveBounds() {
                return taskDetailsPopup.instanceRemoveBounds();
            }

            @Override
            public boolean isMarkAllIncompleteConfirmOpen() {
                return pendingMarkAllIncompleteTask != null;
            }

            @Override
            public void requestMarkAllIncompleteConfirmation(XtremeTask task) {
                requestMarkAllIncompleteConfirmation(task, false);
            }

            @Override
            public void requestMarkAllIncompleteConfirmation(XtremeTask task, boolean groupMode) {
                pendingMarkAllIncompleteTask = task;
                pendingMarkAllIncompleteGroupMode = groupMode;
                markIncompleteDontShowChecked = false;
            }

            @Override
            public void closeMarkAllIncompleteConfirmation() {
                pendingMarkAllIncompleteTask = null;
                pendingMarkAllIncompleteGroupMode = false;
                markAllIncompleteConfirmBounds.setBounds(0, 0, 0, 0);
                markAllIncompleteYesBounds.setBounds(0, 0, 0, 0);
                markAllIncompleteNoBounds.setBounds(0, 0, 0, 0);
                markIncompleteDontShowBounds.setBounds(0, 0, 0, 0);
                markIncompleteDontShowChecked = false;
            }

            @Override
            public XtremeTask markAllIncompleteConfirmationTask() {
                return pendingMarkAllIncompleteTask;
            }

            @Override
            public boolean markAllIncompleteConfirmationGroupMode() {
                return pendingMarkAllIncompleteGroupMode;
            }

            @Override
            public Rectangle markAllIncompleteConfirmBounds() {
                return markAllIncompleteConfirmBounds;
            }

            @Override
            public Rectangle markAllIncompleteYesBounds() {
                return markAllIncompleteYesBounds;
            }

            @Override
            public Rectangle markAllIncompleteNoBounds() {
                return markAllIncompleteNoBounds;
            }

            @Override
            public Rectangle markIncompleteDontShowBounds() {
                return markIncompleteDontShowBounds;
            }

            @Override
            public boolean markIncompleteDontShowChecked() {
                return markIncompleteDontShowChecked;
            }

            @Override
            public void setMarkIncompleteDontShowChecked(boolean checked) {
                markIncompleteDontShowChecked = checked;
            }

            @Override
            public boolean isSyncMismatchReviewOpen() {
                return syncMismatchReviewOpen && !visibleSyncMismatchTasks().isEmpty();
            }

            @Override
            public Rectangle syncMismatchReviewBounds() {
                return syncMismatchReviewBounds;
            }

            @Override
            public Rectangle syncMismatchViewportBounds() {
                return syncMismatchViewportBounds;
            }

            @Override
            public Rectangle syncMismatchCloseBounds() {
                return syncMismatchCloseBounds;
            }

            @Override
            public Rectangle syncMismatchMarkAllBounds() {
                return syncMismatchMarkAllBounds;
            }

            @Override
            public Rectangle syncMismatchApplyBounds() {
                return syncMismatchApplyBounds;
            }

            @Override
            public Rectangle syncMismatchCancelBounds() {
                return syncMismatchCancelBounds;
            }

            @Override
            public boolean isSyncMismatchApplyConfirmOpen() {
                return syncMismatchApplyConfirmOpen;
            }

            @Override
            public Rectangle syncMismatchConfirmBounds() {
                return syncMismatchConfirmBounds;
            }

            @Override
            public Rectangle syncMismatchConfirmYesBounds() {
                return syncMismatchConfirmYesBounds;
            }

            @Override
            public Rectangle syncMismatchConfirmNoBounds() {
                return syncMismatchConfirmNoBounds;
            }

            @Override
            public Rectangle syncMismatchScrollbarRailBounds() {
                return syncMismatchScrollbarRailBounds;
            }

            @Override
            public Rectangle syncMismatchScrollbarThumbBounds() {
                return syncMismatchScrollbarThumbBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> syncMismatchTaskBounds() {
                return syncMismatchTaskBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> syncMismatchTaskNameBounds() {
                return syncMismatchTaskNameBounds;
            }

            @Override
            public TaskListScrollController syncMismatchScroll() {
                return syncMismatchScroll;
            }

            @Override
            public int syncMismatchRowBlock() {
                return scaleInputValue(ROW_HEIGHT + LIST_ROW_SPACING);
            }

            @Override
            public boolean isSyncMismatchDescriptionOpen() {
                return syncMismatchDescriptionTask != null;
            }

            @Override
            public Rectangle syncMismatchDescriptionBounds() {
                return syncMismatchDescriptionBounds;
            }

            @Override
            public Rectangle syncMismatchDescriptionCloseBounds() {
                return syncMismatchDescriptionCloseBounds;
            }

            @Override
            public void openSyncMismatchDescription(XtremeTask task) {
                syncMismatchDescriptionTask = task;
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public void closeSyncMismatchDescription() {
                syncMismatchDescriptionTask = null;
            }

            @Override
            public boolean isSyncMismatchTaskSelected(XtremeTask task) {
                return task != null && task.getId() != null && selectedSyncMismatchTaskIds.contains(task.getId());
            }

            @Override
            public void toggleSyncMismatchTaskSelected(XtremeTask task) {
                if (task == null || task.getId() == null) return;
                if (!selectedSyncMismatchTaskIds.remove(task.getId())) {
                    selectedSyncMismatchTaskIds.add(task.getId());
                }
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public void selectAllSyncMismatchTasks() {
                for (XtremeTask task : visibleSyncMismatchTasks()) {
                    if (task != null && task.getId() != null) {
                        selectedSyncMismatchTaskIds.add(task.getId());
                    }
                }
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public void clearSyncMismatchSelection() {
                selectedSyncMismatchTaskIds.clear();
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public int syncMismatchSelectedCount() {
                return selectedVisibleSyncMismatchCount(visibleSyncMismatchTasks());
            }

            @Override
            public List<XtremeTask> selectedSyncMismatchTasks() {
                List<XtremeTask> out = new ArrayList<>();
                for (XtremeTask task : visibleSyncMismatchTasks()) {
                    if (task != null && task.getId() != null && selectedSyncMismatchTaskIds.contains(task.getId())) {
                        out.add(task);
                    }
                }
                return out;
            }

            @Override
            public void requestSyncMismatchApplyConfirm() {
                if (syncMismatchSelectedCount() > 0) {
                    syncMismatchApplyConfirmOpen = true;
                }
            }

            @Override
            public void closeSyncMismatchApplyConfirm() {
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public boolean isKeyboardHintsOpen() {
                return keyboardHintsOpen;
            }

            @Override
            public void setKeyboardHintsOpen(boolean open) {
                keyboardHintsOpen = open;
            }

            @Override
            public Rectangle keyboardHintsButtonBounds() {
                return keyboardHintsButtonBounds;
            }

            @Override
            public Rectangle keyboardHintsPopupBounds() {
                return keyboardHintsPopupBounds;
            }

            @Override
            public Rectangle taskViewModeBounds() {
                return taskViewModeBounds;
            }

            @Override
            public boolean isDraggingIcon() {
                return draggingIcon;
            }

            @Override
            public void setDraggingIcon(boolean dragging) {
                draggingIcon = dragging;
            }

            @Override
            public void setIconDragOffset(int dx, int dy) {
                iconDragOffsetX = dx;
                iconDragOffsetY = dy;
            }

            @Override
            public int iconDragOffsetX() {
                return iconDragOffsetX;
            }

            @Override
            public int iconDragOffsetY() {
                return iconDragOffsetY;
            }

            @Override
            public void setIconOverride(int x, int y) {
                iconXOverride = x;
                iconYOverride = y;
            }

            @Override
            public void persistIconPosition() {
                if (iconXOverride == null || iconYOverride == null) return;
                if (client.isResized()) {
                    // Convert drag absolute position to canvas-right-relative.
                    resizableOffsetX = client.getCanvasWidth() - (iconXOverride - ICON_RESIZABLE_NUDGE_RIGHT) - ICON_WIDTH;
                    resizableOffsetY = iconYOverride - ICON_RESIZABLE_NUDGE_DOWN;
                    resizableOffsetInitialized = true;
                    plugin.saveIconPosition(resizableOffsetX, resizableOffsetY, true);
                } else {
                    fixedOffsetX = client.getCanvasWidth() - iconXOverride - ICON_WIDTH;
                    fixedOffsetY = iconYOverride;
                    fixedOffsetInitialized = true;
                    plugin.saveIconPosition(fixedOffsetX, fixedOffsetY, false);
                }
                iconXOverride = null;
                iconYOverride = null;
            }

            @Override
            public void clearIconPosition() {
                clearIconPositionState(true);
            }

            @Override
            public void persistPanelPosition() {
                if (panelXOverride != null && panelYOverride != null) {
                    plugin.savePanelPosition(panelXOverride, panelYOverride);
                }
            }


        };
    }

    private void clearIconPositionState(boolean clearPersisted) {
        iconXOverride = null;
        iconYOverride = null;
        resizableOffsetX = 0;
        resizableOffsetY = 0;
        resizableOffsetInitialized = false;
        fixedOffsetX = 0;
        fixedOffsetY = 0;
        fixedOffsetInitialized = false;
        if (clearPersisted) {
            plugin.clearIconPosition();
        }
    }

    private void recenterPanelForMode(boolean targetCompactMode) {
        if (!panelOpen || panelBounds.width <= 0 || panelBounds.height <= 0) {
            return;
        }

        int canvasW = client.getCanvasWidth();
        int canvasH = client.getCanvasHeight();
        int targetW = targetCompactMode ? PANEL_W_COMPACT : PANEL_W_TASKS;
        int targetH = targetCompactMode ? PANEL_H_COMPACT : PANEL_H_TASKS;
        double targetScale = computePanelScale(canvasW, canvasH, targetW, targetH);
        int physicalTargetW = Math.max(1, (int) Math.round(targetW * targetScale));
        int physicalTargetH = Math.max(1, (int) Math.round(targetH * targetScale));

        int centerX = panelBounds.x + panelBounds.width / 2;
        panelXOverride = Math.max(0, Math.min(centerX - physicalTargetW / 2, Math.max(0, canvasW - physicalTargetW)));
        panelYOverride = Math.max(0, Math.min(panelBounds.y, Math.max(0, canvasH - physicalTargetH)));
    }

    private void scalePanelInputBounds(int anchorX, int anchorY, double scale) {
        if (scale == 1.0) {
            return;
        }

        scaleRect(panelBounds, anchorX, anchorY, scale);
        scaleRect(panelDragBarBounds, anchorX, anchorY, scale);
        scaleRect(panelCloseBounds, anchorX, anchorY, scale);
        scaleRect(panelModeToggleBounds, anchorX, anchorY, scale);

        scaleRect(currentTabBounds, anchorX, anchorY, scale);
        scaleRect(tasksTabBounds, anchorX, anchorY, scale);
        scaleRect(rulesTabBounds, anchorX, anchorY, scale);

        scaleRect(taskListViewportBounds, anchorX, anchorY, scale);
        scaleRect(taskScrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(taskScrollbarThumbBounds, anchorX, anchorY, scale);
        scaleRect(rulesViewportBounds, anchorX, anchorY, scale);
        scaleRect(markAllIncompleteConfirmBounds, anchorX, anchorY, scale);
        scaleRect(markAllIncompleteYesBounds, anchorX, anchorY, scale);
        scaleRect(markAllIncompleteNoBounds, anchorX, anchorY, scale);
        scaleRect(markIncompleteDontShowBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchReviewBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchViewportBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchCloseBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchMarkAllBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchApplyBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchCancelBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchConfirmBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchConfirmYesBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchConfirmNoBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchScrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchScrollbarThumbBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchDescriptionBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchDescriptionCloseBounds, anchorX, anchorY, scale);
        scaleRect(keyboardHintsButtonBounds, anchorX, anchorY, scale);
        scaleRect(keyboardHintsPopupBounds, anchorX, anchorY, scale);
        scaleRect(taskViewModeBounds, anchorX, anchorY, scale);

        scaleCurrentLayoutBounds(anchorX, anchorY, scale);
        scaleRulesLayoutBounds(anchorX, anchorY, scale);
        scaleControlsLayoutBounds(controls, anchorX, anchorY, scale);
        scaleRectMap(taskRowBounds, anchorX, anchorY, scale);
        scaleRectMap(taskCheckboxBounds, anchorX, anchorY, scale);
        scaleRectMap(syncMismatchTaskBounds, anchorX, anchorY, scale);
        scaleRectMap(syncMismatchTaskNameBounds, anchorX, anchorY, scale);
    }

    private void scaleCurrentLayoutBounds(int anchorX, int anchorY, double scale) {
        scaleRect(currentLayout.wikiButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.rollButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.completeButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.rollSourceIconBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.viewportBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.scrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.scrollbarThumbBounds, anchorX, anchorY, scale);
    }

    private void scaleRulesLayoutBounds(int anchorX, int anchorY, double scale) {
        scaleRect(rulesLayout.viewportBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.reloadButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.taskerFaqLinkBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.taskerFaqButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.githubReadmeLinkBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogsButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncCAsButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncCaReviewButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncCaReviewIgnoreButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogReviewButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogReviewIgnoreButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncCaMarkedTasksToggleBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogMarkedTasksToggleBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.scrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.scrollbarThumbBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.subTabRulesBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.subTabDataSyncsBounds, anchorX, anchorY, scale);
    }

    private void scaleControlsLayoutBounds(TaskControlsLayout layout, int anchorX, int anchorY, double scale) {
        scaleRect(layout.searchBox, anchorX, anchorY, scale);
        scaleRect(layout.filtersHeaderBounds, anchorX, anchorY, scale);
        scaleRect(layout.sortHeaderBounds, anchorX, anchorY, scale);
        scaleRect(layout.filterSourceAll, anchorX, anchorY, scale);
        scaleRect(layout.filterCA, anchorX, anchorY, scale);
        scaleRect(layout.filterCL, anchorX, anchorY, scale);
        scaleRect(layout.filterDA, anchorX, anchorY, scale);
        scaleRect(layout.filterStatusAll, anchorX, anchorY, scale);
        scaleRect(layout.filterIncomplete, anchorX, anchorY, scale);
        scaleRect(layout.filterComplete, anchorX, anchorY, scale);
        scaleRect(layout.filterTierThis, anchorX, anchorY, scale);
        scaleRect(layout.filterTierAll, anchorX, anchorY, scale);
        scaleRect(layout.sortCompletion, anchorX, anchorY, scale);
        scaleRect(layout.sortTier, anchorX, anchorY, scale);
        scaleRect(layout.sortDate, anchorX, anchorY, scale);
        scaleRect(layout.sortTimeTicks, anchorX, anchorY, scale);
        scaleRect(layout.sortReset, anchorX, anchorY, scale);
        scaleRect(layout.clearFilters, anchorX, anchorY, scale);
        scaleRect(layout.clearSort, anchorX, anchorY, scale);
        scaleRect(layout.hoverTooltipAnchor, anchorX, anchorY, scale);
        scaleRect(layout.filterNewTasks, anchorX, anchorY, scale);
        scaleRect(layout.filterNewTasksHelp, anchorX, anchorY, scale);
        layout.searchTextX = scaleX(layout.searchTextX, anchorX, scale);
        for (int i = 0; i < layout.searchCharXPositions.length; i++) {
            layout.searchCharXPositions[i] = scaleX(layout.searchCharXPositions[i], anchorX, scale);
        }
    }

    private void scaleTaskDetailsPopupBounds(int anchorX, int anchorY, double scale) {
        scaleRect(taskDetailsPopup.bounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.viewportBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.closeBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.wikiBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.toggleBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.scrollbarRailBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.scrollbarThumbBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.decrementGroupBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.incrementGroupBounds(), anchorX, anchorY, scale);
        scaleRectMap(taskDetailsPopup.instanceRemoveBounds(), anchorX, anchorY, scale);
    }

    private void scaleRectMap(Map<?, Rectangle> map, int anchorX, int anchorY, double scale) {
        synchronized (map) {
            for (Rectangle r : map.values()) {
                scaleRect(r, anchorX, anchorY, scale);
            }
        }
    }

    private void scaleRect(Rectangle r, int anchorX, int anchorY, double scale) {
        if (r == null || r.width <= 0 || r.height <= 0) {
            return;
        }

        int x1 = scaleX(r.x, anchorX, scale);
        int y1 = scaleY(r.y, anchorY, scale);
        int x2 = scaleX(r.x + r.width, anchorX, scale);
        int y2 = scaleY(r.y + r.height, anchorY, scale);
        r.setBounds(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    private int scaleX(int x, int anchorX, double scale) {
        return anchorX + (int) Math.round((x - anchorX) * scale);
    }

    private int scaleY(int y, int anchorY, double scale) {
        return anchorY + (int) Math.round((y - anchorY) * scale);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        buttonRenderer.drawBevelBox(g, r, fill);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private Point computeIconPosition(int canvasWidth, int canvasHeight) {
        // Active drag: raw absolute position.
        if (iconXOverride != null && iconYOverride != null) {
            return new Point(iconXOverride, iconYOverride);
        }

        // Fixed layout: follow the XP drops orb by default. Only use an offset
        // after the user drags the icon in the current fixed-layout session.
        if (!client.isResized()) {
            if (fixedOffsetInitialized) {
                int x = canvasWidth - ICON_WIDTH - fixedOffsetX;
                int y = fixedOffsetY;
                x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
                y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
                return new Point(x, y);
            }
            return defaultFixedIconPosition(canvasWidth, canvasHeight);
        }

        // Resizable: position is canvas-right-relative so it tracks horizontal resizes
        // after a user drag. Default placement is computed live so a stale widget
        // read during layout switches cannot become a stuck position.
        if (!resizableOffsetInitialized) {
            return defaultResizableIconPosition(canvasWidth, canvasHeight);
        }

        int x = canvasWidth - ICON_WIDTH - resizableOffsetX;
        int y = resizableOffsetY;
        x += ICON_RESIZABLE_NUDGE_RIGHT;
        y += ICON_RESIZABLE_NUDGE_DOWN;
        x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
        y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
        return new Point(x, y);
    }

    private Point defaultFixedIconPosition(int canvasWidth, int canvasHeight) {
        int x;
        int y;
        Widget xpOrb = client.getWidget(ComponentID.MINIMAP_XP_ORB);
        if (xpOrb != null && hasUsableBounds(xpOrb)) {
            Rectangle b = xpOrb.getBounds();
            x = b.x - ICON_ANCHOR_PAD - ICON_WIDTH;
            y = b.y + (b.height - ICON_HEIGHT) / 2 - ICON_FIXED_NUDGE_UP;
        } else {
            x = canvasWidth - ICON_WIDTH - ICON_FALLBACK_RIGHT_MARGIN;
            y = ICON_FALLBACK_Y;
        }
        x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
        y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
        return new Point(x, y);
    }

    private Point defaultResizableIconPosition(int canvasWidth, int canvasHeight) {
        int x = canvasWidth - ICON_WIDTH - ICON_RESIZABLE_DEFAULT_RIGHT_MARGIN;
        int y = ICON_RESIZABLE_DEFAULT_Y;
        x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
        y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
        return new Point(x, y);
    }

    private boolean hasUsableBounds(Widget widget) {
        return widget != null && widget.getBounds().width > 0 && widget.getBounds().height > 0;
    }

    private int getHeaderTitleHeight(FontMetrics fallbackMetrics, int panelW) {
        if (HEADER_ICON == null) {
            return fallbackMetrics.getHeight();
        }
        return headerLogoSize(panelW).height;
    }

    private void drawHeaderLogo(Graphics2D g, int panelX, int panelY, int panelW, int headerH) {
        Dimension logoSize = headerLogoSize(panelW);
        int logoW = logoSize.width;
        int logoH = logoSize.height;
        int logoX = panelX + (panelW - logoW) / 2;
        int logoY = panelY + (headerH - logoH) / 2;

        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(HEADER_ICON, logoX, logoY, logoW, logoH, null);
        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private int getHeaderPadY() {
        return client.isResized() ? 3 : 2;
    }

    private Dimension headerLogoSize(int panelW) {
        int maxLogoW = Math.max(1, panelW - 6);
        int maxLogoH = getHeaderLogoMaxHeight();
        double scale = Math.min((double) maxLogoW / HEADER_ICON.getWidth(), (double) maxLogoH / HEADER_ICON.getHeight());
        int logoW = Math.max(1, (int) Math.round(HEADER_ICON.getWidth() * scale));
        int logoH = Math.max(1, (int) Math.round(HEADER_ICON.getHeight() * scale));
        return new Dimension(logoW, logoH);
    }

    private int getHeaderLogoMaxHeight() {
        if (compactPanelMode) {
            return 54;
        }
        return client.isResized() ? 82 : 70;
    }

    // ---- panel sizing helpers ----
    private int panelWidth() {
        return panelBounds.width;
    }

    private int panelInnerWidth() {
        return Math.max(0, panelBounds.width - 2 * PANEL_PADDING);
    }

    /**
     * Convenience for truncation calls.
     */
    private int panelInnerTextMaxWidth() {
        return panelInnerWidth();
    }

    private List<XtremeTask> getTasksForScope(TaskListQuery.TierScope scope, TaskTier activeTier) {
        if (scope == TaskListQuery.TierScope.ALL_TIERS) {
            // all tiers
            List<XtremeTask> out = new ArrayList<>();
            for (XtremeTask t : plugin.getDummyTasks()) {
                if (t.getTier() != null) {
                    out.add(t);
                }
            }
            return out;
        }

        // default: only active tier
        return getTasksForTier(activeTier);
    }

}
