

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.tasklist.TaskListPipeline;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasks.TaskControlsRenderer;
import com.amtrollin.xtremetasker.ui.tasks.TaskDetailsPopup;
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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;

import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CA;
import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CLOGS;
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

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;

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
    private boolean iconPositionLoaded = false;
    private boolean panelPositionLoaded = false;

    private static final int PANEL_W_TASKS = 520;
    private static final int PANEL_H_TASKS = 590;


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
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG) return null;

        TaskVerification verification = task.getVerification();
        if (verification == null || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG) return null;

        int[] itemIds = verification.getItemIds();
        if (itemIds == null || itemIds.length == 0) return null;
        if (itemIds.length == 1) return null;

        int requiredCount = collectionLogPreviewRequiredCount(task, verification, itemIds);
        requiredCount = Math.max(1, Math.min(requiredCount, itemIds.length));
        int previousRequiredCount = collectionLogPreviewPreviousRequiredCount(task, itemIds);
        int totalObtainedCount = plugin.countObtainedCollectionLogItems(itemIds);
        int obtainedCount = Math.max(0, totalObtainedCount - previousRequiredCount);
        int shownObtainedCount = Math.min(obtainedCount, requiredCount);

        Map<String, Boolean> obtainedByItemName = new LinkedHashMap<>();
        for (int itemId : itemIds) {
            String itemName = plugin.getItemName(itemId);
            boolean obtained = plugin.isCollectionLogItemObtained(itemId);
            obtainedByItemName.merge(itemName, obtained, Boolean::logicalOr);
        }

        List<CollectionLogRequirementItem> items = new ArrayList<>(obtainedByItemName.size());
        for (Map.Entry<String, Boolean> entry : obtainedByItemName.entrySet()) {
            items.add(new CollectionLogRequirementItem(entry.getKey(), entry.getValue()));
        }

        boolean sameNameFamily = obtainedByItemName.size() == 1;
        TaskGroupProgress groupProgress = plugin.getTaskGroupProgress(task);
        boolean repeatedDistinctPool = !sameNameFamily && groupProgress != null && groupProgress.isGrouped();
        String summaryText = sameNameFamily
                ? shownObtainedCount + "/" + requiredCount + " " + pluralizeRequirementName(items.get(0).getName(), requiredCount) + " obtained"
                : repeatedCollectionLogRequirementSummary(totalObtainedCount, previousRequiredCount, repeatedDistinctPool);
        return new CollectionLogRequirementPreview(summaryText, sameNameFamily || repeatedDistinctPool, !sameNameFamily, items);
    }

    private int collectionLogPreviewRequiredCount(XtremeTask task, TaskVerification verification, int[] itemIds) {
        int count = verification.getCount() == null ? 1 : verification.getCount();
        if (count <= 1 || task == null || task.getId() == null)
        {
            return Math.max(1, count);
        }

        XtremeTask previous = null;
        for (XtremeTask candidate : plugin.getDummyTasks())
        {
            if (!sameCollectionLogRequirementSequence(task, candidate, itemIds))
            {
                continue;
            }

            if (task.getId().equals(candidate.getId()))
            {
                break;
            }

            previous = candidate;
        }

        TaskVerification previousVerification = previous == null ? null : previous.getVerification();
        Integer previousCount = previousVerification == null ? null : previousVerification.getCount();
        if (previousCount != null && previousCount > 0 && count > previousCount)
        {
            return count - previousCount;
        }

        return count;
    }

    private int collectionLogPreviewPreviousRequiredCount(XtremeTask task, int[] itemIds) {
        if (task == null || task.getId() == null)
        {
            return 0;
        }

        XtremeTask previous = null;
        for (XtremeTask candidate : plugin.getDummyTasks())
        {
            if (!sameCollectionLogRequirementSequence(task, candidate, itemIds))
            {
                continue;
            }

            if (task.getId().equals(candidate.getId()))
            {
                break;
            }

            previous = candidate;
        }

        TaskVerification previousVerification = previous == null ? null : previous.getVerification();
        Integer previousCount = previousVerification == null ? null : previousVerification.getCount();
        return previousCount == null ? 0 : Math.max(0, previousCount);
    }

    private String repeatedCollectionLogRequirementSummary(int totalObtainedCount, int previousRequiredCount, boolean repeatedDistinctPool) {
        if (!repeatedDistinctPool)
        {
            return "";
        }

        int priorCount = Math.max(0, Math.min(previousRequiredCount, totalObtainedCount));
        if (priorCount <= 0)
        {
            return "";
        }
        return totalObtainedCount + " obtained; " + priorCount + " counted toward earlier completions.";
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
        iconXOverride = null;
        iconYOverride = null;
        resizableOffsetX = 0;
        resizableOffsetY = 0;
        resizableOffsetInitialized = false;
        plugin.clearIconPosition();
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
        iconPositionLoaded = false;
    }

    /** Returns a snapshot of the current icon bounds for use in menu entry checks. */
    public Rectangle getIconBounds() {
        return new Rectangle(iconBounds);
    }

    // -----------------------------
    // rowBlock accessors (for wheel)
    // -----------------------------
    int tasksRowBlock() {
        return taskRowsRendererTasks.rowBlock();
    }


    int rulesRowBlock() {
        return rulesTabRenderer.rowBlock();
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
            int[] savedPos = plugin.loadIconPosition();
            if (savedPos != null) {
                // Saved values are canvas-right-relative (resizable mode).
                resizableOffsetX = savedPos[0];
                resizableOffsetY = savedPos[1];
                resizableOffsetInitialized = true;
            }
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
        int panelW = PANEL_W_TASKS;
        int panelHeight = PANEL_H_TASKS;
        // In fixed layout the canvas is only ~503px tall — clamp so the panel always fits.
        if (!client.isResized()) {
            panelHeight = Math.min(panelHeight, canvasH - 4);
        }

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasW - panelW) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasH - panelHeight) / 2;

        panelX = Math.max(0, Math.min(panelX, canvasW - panelW));
        panelY = Math.max(0, Math.min(panelY, canvasH - panelHeight));

        panelBounds.setBounds(panelX, panelY, panelW, panelHeight);

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
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
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

        int cursorY = panelY + headerH;

        // gold divider under header
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 180));
        g.fillRect(panelX + 1, cursorY, panelW - 2, 1);
        cursorY += 1;

        panelDragBarBounds.setBounds(panelX, panelY, panelW, cursorY - panelY);

        g.setFont(oldFont);
        fm = g.getFontMetrics();
        cursorY += 4;

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

        if (taskDetailsPopup.isOpen()) {
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
                    this::buildCollectionLogRequirementPreview,
                    client.getMouseCanvasPosition(),
                    resolveTaskIcon(taskDetailsPopup.task()),
                    plugin.showTips()
            );
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
        return new Dimension(panelW, panelHeight);
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
                .anyMatch(task -> task.getSource() == TaskSource.COLLECTION_LOG);
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
            reviewMessage = "CLOG tasks marked complete in Xtreme Tasker but not detected in-game after sync";
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

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
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
                    int hintX = row.x + 8;
                    int hintMaxW = Math.max(0, actionColumnX - hintX - 8);
                    if (hintMaxW > 40)
                    {
                        String hint = TextUtils.truncateToWidth(gameProgress, fm, hintMaxW);
                        g.setColor(P.UI_TEXT_DIM);
                        int hintY = row.y - 3;
                        if (hintY < syncMismatchViewportBounds.y + fm.getAscent())
                        {
                            hintY = row.y + row.height + fm.getAscent();
                        }
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
        return task.getName() + " " + progress.label();
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
        boolean multipleTasks = pending != null
                && plugin.getTaskGroupProgress(pending) != null
                && plugin.getTaskGroupProgress(pending).isGrouped();
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

        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
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

    /** Height in px available for the scrollable body on the Current tab. */
    private int computeCurrentViewportH(Graphics2D g, FontMetrics fm, Rectangle pb, int cursorYBaseline) {
        // Only reserve hint line at the bottom; button lives inside scroll body
        int hintFooterH = fm.getHeight() + PANEL_PADDING + 8;
        int vpBottom = pb.y + pb.height - hintFooterH;
        int vpTop = cursorYBaseline - fm.getAscent();
        return Math.max(10, vpBottom - vpTop);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
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
        rulesLayout.subTabRulesBounds.setBounds(layout.subTabRulesBounds);
        rulesLayout.subTabDataSyncsBounds.setBounds(layout.subTabDataSyncsBounds);

        rulesViewportBounds.setBounds(layout.viewportBounds);

        if (rulesLayout.syncClogsButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncClogsButtonBounds, "Sync CLOGs", P.BTN_DISABLED_BG);
        }
        if (rulesLayout.syncCAsButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCAsButtonBounds, "Sync CAs", P.BTN_DISABLED_BG);
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
            taskQuery.sourceFilter = TaskListQuery.SourceFilter.ALL;
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_2) {
            taskQuery.sourceFilter = (taskQuery.sourceFilter == CA) ? TaskListQuery.SourceFilter.ALL : CA;
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_3) {
            taskQuery.sourceFilter = (taskQuery.sourceFilter == CLOGS) ? TaskListQuery.SourceFilter.ALL : CLOGS;
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
                                taskQuery.sourceFilter = TaskListQuery.SourceFilter.CA;
                            } else if (rsf == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY) {
                                taskQuery.sourceFilter = TaskListQuery.SourceFilter.CLOGS;
                            } else {
                                taskQuery.sourceFilter = TaskListQuery.SourceFilter.ALL;
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
            public Rectangle currentViewportBounds() {
                return currentLayout.viewportBounds;
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
                return taskRowsRendererTasks.rowBlock();
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
                return ROW_HEIGHT; // popup content uses ROW_HEIGHT rows
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
                pendingMarkAllIncompleteTask = task;
                markIncompleteDontShowChecked = false;
            }

            @Override
            public void closeMarkAllIncompleteConfirmation() {
                pendingMarkAllIncompleteTask = null;
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
                return ROW_HEIGHT + LIST_ROW_SPACING;
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
                    plugin.saveIconPosition(resizableOffsetX, resizableOffsetY);
                }
                // Fixed layout: always anchored to widget; nothing to save.
                iconXOverride = null;
                iconYOverride = null;
            }

            @Override
            public void clearIconPosition() {
                iconXOverride = null;
                iconYOverride = null;
                resizableOffsetX = 0;
                resizableOffsetY = 0;
                plugin.clearIconPosition();
            }

            @Override
            public void persistPanelPosition() {
                if (panelXOverride != null && panelYOverride != null) {
                    plugin.savePanelPosition(panelXOverride, panelYOverride);
                }
            }


        };
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        buttonRenderer.drawBevelBox(g, r, fill);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private Point computeIconPosition(int canvasWidth, int canvasHeight) {
        // Fixed layout: always anchor to the XP drops orb — ignore any saved/drag override.
        if (!client.isResized()) {
            Widget xpOrb = client.getWidget(WidgetInfo.MINIMAP_XP_ORB);
            if (xpOrb != null) {
                Rectangle b = xpOrb.getBounds();
                int x = b.x - ICON_ANCHOR_PAD - ICON_WIDTH;
                int y = b.y + (b.height - ICON_HEIGHT) / 2 - ICON_FIXED_NUDGE_UP;
                x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
                y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
                return new Point(x, y);
            }
            return new Point(canvasWidth - ICON_WIDTH - ICON_FALLBACK_RIGHT_MARGIN, ICON_FALLBACK_Y);
        }

        // Resizable: position is canvas-right-relative so it tracks horizontal resizes
        // without depending on widget bounds (which lag by one game tick).
        // Initialize from widget bounds once — but ONLY when bounds are valid (non-zero).
        // If the layout just switched from fixed the widget may not be ready yet; retry next frame.
        if (!resizableOffsetInitialized) {
            Widget mapArea = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA);
            if (mapArea == null) mapArea = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_STONES_DRAW_AREA);
            if (mapArea != null && mapArea.getBounds().width > 0 && mapArea.getBounds().height > 0) {
                Rectangle b = mapArea.getBounds();
                int defaultX = b.x + (b.width - ICON_WIDTH) / 2 + ICON_ANCHOR_RIGHT_OFFSET;
                int defaultY = b.y + b.height + ICON_ANCHOR_PAD + ICON_ANCHOR_EXTRA_DOWN;
                resizableOffsetX = canvasWidth - defaultX - ICON_WIDTH; // dist from right
                resizableOffsetY = defaultY;                             // dist from top
                resizableOffsetInitialized = true;
            }
            // Widget not ready yet — fall through to fallback position and retry next frame.
        }

        // Active drag: raw absolute position.
        if (iconXOverride != null && iconYOverride != null) {
            return new Point(iconXOverride, iconYOverride);
        }

        // Stable position: pure canvas-width arithmetic, no widget reads.
        // If still not initialized (widget not ready), show at fallback until next frame.
        int x = resizableOffsetInitialized
                ? canvasWidth - ICON_WIDTH - resizableOffsetX
                : canvasWidth - ICON_WIDTH - ICON_FALLBACK_RIGHT_MARGIN;
        int y = resizableOffsetInitialized ? resizableOffsetY : ICON_FALLBACK_Y;
        x += ICON_RESIZABLE_NUDGE_RIGHT;
        y += ICON_RESIZABLE_NUDGE_DOWN;
        x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
        y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));
        return new Point(x, y);
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
        return client.isResized() ? 4 : 3;
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
        return client.isResized() ? 110 : 94;
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
