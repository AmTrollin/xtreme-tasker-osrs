

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
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
import com.amtrollin.xtremetasker.ui.tasks.models.WikiLink;
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
import net.runelite.api.Skill;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SkillIconManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final Logger log = LoggerFactory.getLogger(XtremeTaskerOverlay.class);
    private static final BufferedImage PLUGIN_ICON = loadPluginIconSafe();
    private static final BufferedImage HEADER_ICON = loadHeaderIconSafe();
    private static final UiPalette P = UiPalette.DEFAULT;
    private static final int ANCIENT_PAGE_FIRST_ITEM_ID = 11341;
    private static final int ANCIENT_PAGE_LAST_ITEM_ID = 11366;
    private static final int COLLECTION_LOG_PREVIEW_CACHE_LIMIT = 256;
    private static final int COLLECTION_LOG_SEQUENCE_CACHE_LIMIT = 256;
    private static final int ITEM_IMAGE_CACHE_LIMIT = 512;
    private static final int SKILL_IMAGE_CACHE_LIMIT = 32;
    private static final int SPRITE_CACHE_LIMIT = 32;
    private static final int SORTED_TASK_LIST_CACHE_LIMIT = 48;
    private static final int PREREQUISITE_STATUS_CACHE_LIMIT = 512;
    private static final int COMPACT_LINES_CACHE_LIMIT = 128;
    private static final int SYNC_REVIEW_VISIBLE_TASKS_CACHE_LIMIT = 8;
    private static final String MEDALLION_ASSEMBLY_TITLE_PREFIX = "Need all ";
    private static final int COMPACT_MEDALLION_ASSEMBLY_TITLE_GAP = 6;
    private static final int COMPACT_SECONDARY_SECTION_GAP = 6;
    private static final int TIER_SECTION_ICON_GAP = 5;
    private static final int TIER_SECTION_LABEL_TOP_GAP = 4;
    private static final String OTHER_SEQUENCE_CLOGS_DIVIDER = "___";
    private static final String OTHER_SEQUENCE_CLOGS_LABEL = "Other clogs in this task sequence, but different tier:";
    private static final int TASK_RESOLVE_TOGGLE_SIZE = 18;
    private static final int TASK_RESOLVE_TOGGLE_GAP = 6;
    private static final long SLOW_PREVIEW_LOG_THRESHOLD_NANOS = 8_000_000L;
    private static final long SLOW_PREVIEW_LOG_INTERVAL_MS = 2_000L;
    private static final long SLOW_TASK_LIST_LOG_THRESHOLD_NANOS = 8_000_000L;
    private static final long SLOW_TASK_LIST_LOG_INTERVAL_MS = 2_000L;
    private static final long SLOW_PANEL_RENDER_LOG_THRESHOLD_NANOS = 12_000_000L;
    private static final long SLOW_PANEL_RENDER_LOG_INTERVAL_MS = 2_000L;
    private static final List<WikiLink> ZAMORAK_HOOD_CLOAK_WIKI_LINKS = List.of(
            new WikiLink("Zamorak hood", "https://oldschool.runescape.wiki/w/Castlewars_hood_(Zamorak)"),
            new WikiLink("Zamorak cloak", "https://oldschool.runescape.wiki/w/Castlewars_cloak_(Zamorak)")
    );
    private static final Map<String, List<WikiLink>> TASK_DETAILS_WIKI_LINKS_BY_ID = createTaskDetailsWikiLinksById();
    private static final Map<String, List<WikiLink>> TASK_DETAILS_WIKI_LINKS_BY_NAME = createTaskDetailsWikiLinksByName();
    private static final DateTimeFormatter COMPACT_COMPLETION_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TASK_RESOLVE_COMPLETION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private static BufferedImage loadPluginIconSafe() {
        try (InputStream in = XtremeTaskerOverlay.class.getResourceAsStream("/icons/xtreme_tasker_icon.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, List<WikiLink>> createTaskDetailsWikiLinksByName()
    {
        Map<String, List<WikiLink>> links = new HashMap<>();
        links.put(normalizeWikiTaskName("Get the Zamorak hood & cloak"), ZAMORAK_HOOD_CLOAK_WIKI_LINKS);
        return Collections.unmodifiableMap(links);
    }

    private static Map<String, List<WikiLink>> createTaskDetailsWikiLinksById()
    {
        Map<String, List<WikiLink>> links = new HashMap<>();
        links.put("collection_log_easy_get-the-zamorak-hood-cloak_001_53931281fb", ZAMORAK_HOOD_CLOAK_WIKI_LINKS);
        return Collections.unmodifiableMap(links);
    }

    private static String normalizeWikiTaskName(String value)
    {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static BufferedImage loadHeaderIconSafe() {
        try (InputStream in = XtremeTaskerOverlay.class.getResourceAsStream("/icons/xtreme_tasker_header.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static <K, V> Map<K, V> lruCache(int maxEntries)
    {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
            {
                return size() > maxEntries;
            }
        });
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
    private final Rectangle syncMismatchGuardBounds = new Rectangle();
    private final Rectangle syncMismatchGuardOkBounds = new Rectangle();
    private final Rectangle syncMismatchGuardViewportBounds = new Rectangle();
    private final Rectangle syncMismatchGuardScrollbarRailBounds = new Rectangle();
    private final Rectangle syncMismatchGuardScrollbarThumbBounds = new Rectangle();
    private final Rectangle syncMismatchScrollbarRailBounds = new Rectangle();
    private final Rectangle syncMismatchScrollbarThumbBounds = new Rectangle();
    private final Rectangle syncMismatchDescriptionBounds = new Rectangle();
    private final Rectangle syncMismatchDescriptionCloseBounds = new Rectangle();
    private final Rectangle syncMismatchGroupResolveBounds = new Rectangle();
    private final Rectangle syncMismatchGroupResolveSaveBounds = new Rectangle();
    private final Rectangle syncMismatchGroupResolveCancelBounds = new Rectangle();
    private final Rectangle taskResolveBounds = new Rectangle();
    private final Rectangle taskResolveCloseBounds = new Rectangle();
    private final Rectangle taskResolveSaveBounds = new Rectangle();
    private final Rectangle taskResolveCancelBounds = new Rectangle();
    private final Rectangle taskSyncResultBounds = new Rectangle();
    private final Rectangle taskSyncResultCloseBounds = new Rectangle();
    private final Rectangle taskDetailsIncompleteConfirmBounds = new Rectangle();
    private final Rectangle taskDetailsIncompleteConfirmYesBounds = new Rectangle();
    private final Rectangle taskDetailsIncompleteConfirmNoBounds = new Rectangle();
    private final Rectangle keyboardHintsButtonBounds = new Rectangle();
    private final Rectangle keyboardHintsPopupBounds = new Rectangle();
    private final Rectangle taskViewModeBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = Collections.synchronizedMap(new EnumMap<>(TaskTier.class));
    private final Map<XtremeTask, Rectangle> taskRowBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchTaskBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchTaskNameBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchGroupResolveToggleBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> taskResolveInstanceToggleBounds = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> selectedSyncMismatchTaskIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> selectedSyncMismatchGroupResolveTaskIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> originalSyncMismatchGroupResolveTaskIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> selectedTaskResolveIncompleteTaskIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> taskResolveOriginalIncompleteTaskIds = Collections.synchronizedSet(new HashSet<>());

    // Current tab bounds (now come from CurrentTabLayout)
    private final CurrentTabLayout currentLayout = new CurrentTabLayout();
    // Rules tab bounds (now come from RulesTabLayout)
    private final RulesTabLayout rulesLayout = new RulesTabLayout();
    private RulesTabLayout.SubTab rulesSubTab = RulesTabLayout.SubTab.RULES;

    private boolean panelOpen = false;
    private boolean compactPanelMode = true;
    private boolean draggingPanel = false;
    private boolean keyboardHintsOpen = false;
    private static final long KEYBOARD_TRIGGERED_TOOLTIP_MS = 3000L;
    private String keyboardTriggeredTaskTooltipText = null;
    private final Rectangle keyboardTriggeredTaskTooltipAnchor = new Rectangle();
    private long keyboardTriggeredTaskTooltipUntilMs = 0L;
    private boolean markIncompleteDontShowChecked = false;
    private boolean syncMismatchApplyConfirmOpen = false;
    private String syncMismatchGuardMessage = null;
    private boolean syncMismatchReviewOpen = false;
    private enum SyncReviewMode { MISMATCH, COMPLETION_CANDIDATES }
    private SyncReviewMode syncReviewMode = SyncReviewMode.MISMATCH;
    private TaskSource syncMismatchReviewSource = null;
    private XtremeTask syncMismatchDescriptionTask = null;
    private XtremeTask syncMismatchGroupResolveTask = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private XtremeTask pendingMarkAllIncompleteTask = null;
    private boolean pendingMarkAllIncompleteGroupMode = false;
    private XtremeTask taskResolveTask = null;
    private boolean taskResolveSavedIncompleteEdits = false;
    private String taskDetailsSyncPendingTaskId = null;
    private String taskDetailsSyncStartedAt = null;
    private int taskDetailsSyncStartedObservedCount = -1;
    private XtremeTask taskSyncResultTask = null;
    private String taskSyncResultText = "";
    private boolean taskSyncResultResolvable = false;
    private boolean taskSyncResultShowFreshnessHint = false;
    private boolean taskDetailsIncompleteConfirmOpen = false;

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
    private final SkillIconManager skillIconManager;
    private final Map<String, CollectionLogRequirementPreview> collectionLogPreviewCache = lruCache(COLLECTION_LOG_PREVIEW_CACHE_LIMIT);
    private final Map<String, List<XtremeTask>> collectionLogSequenceCache = lruCache(COLLECTION_LOG_SEQUENCE_CACHE_LIMIT);
    private final Map<Integer, BufferedImage> itemImageCache = lruCache(ITEM_IMAGE_CACHE_LIMIT);
    private final Map<Skill, BufferedImage> skillImageCache = lruCache(SKILL_IMAGE_CACHE_LIMIT);
    private final Map<Integer, BufferedImage> spriteCache = lruCache(SPRITE_CACHE_LIMIT);
    private final Map<String, List<XtremeTask>> sortedTaskListCache = lruCache(SORTED_TASK_LIST_CACHE_LIMIT);
    private final Map<String, List<PrerequisiteStatus>> prerequisiteStatusCache = lruCache(PREREQUISITE_STATUS_CACHE_LIMIT);
    private final Map<String, List<CompactLine>> compactLinesCache = lruCache(COMPACT_LINES_CACHE_LIMIT);
    private final Map<String, List<XtremeTask>> syncReviewVisibleTasksCache = lruCache(SYNC_REVIEW_VISIBLE_TASKS_CACHE_LIMIT);
    private long lastSlowPreviewLogMs = 0L;
    private long lastSlowTaskListLogMs = 0L;
    private long lastSlowPanelRenderLogMs = 0L;

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

    private static final Map<String, SkillcapeDisplay> SKILLCAPE_DISPLAYS = skillcapeDisplays();

    private static Map<String, SkillcapeDisplay> skillcapeDisplays()
    {
        Map<String, SkillcapeDisplay> displays = new LinkedHashMap<>();
        addSkillcapeDisplay(displays, "fishing", "Fishing skillcape", 9799, 9798);
        addSkillcapeDisplay(displays, "fletching", "Fletching skillcape", 9784, 9783);
        addSkillcapeDisplay(displays, "herblore", "Herblore skillcape", 9775, 9774);
        addSkillcapeDisplay(displays, "hunter", "Hunter skillcape", 9949, 9948);
        addSkillcapeDisplay(displays, "magic", "Magic skillcape", 9763, 9762);
        addSkillcapeDisplay(displays, "mining", "Mining skillcape", 9793, 9792);
        addSkillcapeDisplay(displays, "prayer", "Prayer skillcape", 9760, 9759);
        addSkillcapeDisplay(displays, "ranged", "Ranging skillcape", 9757, 9756);
        addSkillcapeDisplay(displays, "runecraft", "Runecraft skillcape", 9766, 9765);
        addSkillcapeDisplay(displays, "sailing", "Sailing skillcape", 31290, 31289);
        addSkillcapeDisplay(displays, "slayer", "Slayer skillcape", 9787, 9786);
        addSkillcapeDisplay(displays, "smithing", "Smithing skillcape", 9796, 9795);
        addSkillcapeDisplay(displays, "strength", "Strength skillcape", 9751, 9750);
        addSkillcapeDisplay(displays, "thieving", "Thieving skillcape", 9778, 9777);
        addSkillcapeDisplay(displays, "woodcutting", "Woodcutting skillcape", 9808, 9807);
        addSkillcapeDisplay(displays, "agility", "Agility skillcape", 9772, 9771, 13341, 13340);
        addSkillcapeDisplay(displays, "attack", "Attack skillcape", 9748, 9747);
        addSkillcapeDisplay(displays, "construction", "Construction skillcape", 9790, 9789);
        addSkillcapeDisplay(displays, "cooking", "Cooking skillcape", 9802, 9801);
        addSkillcapeDisplay(displays, "crafting", "Crafting skillcape", 9781, 9780);
        addSkillcapeDisplay(displays, "defence", "Defence skillcape", 9754, 9753);
        addSkillcapeDisplay(displays, "farming", "Farming skillcape", 9811, 9810);
        addSkillcapeDisplay(displays, "firemaking", "Firemaking skillcape", 9805, 9804);
        addSkillcapeDisplay(displays, "hitpoints", "Hitpoints skillcape", 9769, 9768);
        return Collections.unmodifiableMap(displays);
    }

    private static void addSkillcapeDisplay(Map<String, SkillcapeDisplay> displays, String skillKey, String name, int... itemIds)
    {
        displays.put(skillKey, new SkillcapeDisplay(name, itemIds));
    }

    private java.awt.image.BufferedImage resolveTaskIcon(XtremeTask task) {
        if (task == null) return null;
        if (task.getSource() == TaskSource.DIARY_ACHIEVEMENT) return PrerequisiteIconRenderer.achievementDiaryIconImage();
        Integer sequenceItemId = sequencePreviewFocusItemId(task);
        if (sequenceItemId != null && sequenceItemId > 0) return getCachedItemImage(sequenceItemId);
        Integer id = task.getIconItemId();
        if (id != null && id > 0) return getCachedItemImage(id);
        Integer singleCollectionLogItemId = singleCollectionLogPreviewItemId(task);
        if (singleCollectionLogItemId != null && singleCollectionLogItemId > 0) return getCachedItemImage(singleCollectionLogItemId);
        if (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT && task.getTier() != null) {
            Integer spriteId = CA_TIER_SPRITE_IDS.get(task.getTier());
            if (spriteId != null) return getCachedSprite(spriteId);
        }
        return null;
    }

    private Integer singleCollectionLogPreviewItemId(XtremeTask task)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return null;
        }

        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreview(task);
        if (preview == null || !preview.showItemList() || preview.getItems().size() != 1)
        {
            return null;
        }

        CollectionLogRequirementItem item = preview.getItems().get(0);
        return item == null || item.getItemId() <= 0 ? null : item.getItemId();
    }

    private BufferedImage getCachedItemImage(int itemId)
    {
        if (itemId <= 0)
        {
            return null;
        }

        BufferedImage cached = itemImageCache.get(itemId);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage image = plugin.getItemImage(itemId);
        if (image != null)
        {
            itemImageCache.put(itemId, image);
        }
        return image;
    }

    private BufferedImage getCachedSkillImage(Skill skill)
    {
        if (skill == null)
        {
            return null;
        }

        BufferedImage cached = skillImageCache.get(skill);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage image = skillIconManager == null ? null : skillIconManager.getSkillImage(skill, true);
        if (image != null)
        {
            skillImageCache.put(skill, image);
        }
        return image;
    }

    private BufferedImage getCachedPrerequisiteMarkerImage(MarkerIcon markerIcon)
    {
        return null;
    }

    private BufferedImage getCachedSprite(int spriteId)
    {
        BufferedImage cached = spriteCache.get(spriteId);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage image = spriteManager.getSprite(spriteId, 0);
        if (image != null)
        {
            spriteCache.put(spriteId, image);
        }
        return image;
    }

    private List<PrerequisiteStatus> getCachedPrerequisiteStatuses(XtremeTask task)
    {
        if (task == null || task.getPrereqs() == null)
        {
            return List.of();
        }

        String cacheKey = safeTaskId(task)
                + "|tick=" + client.getTickCount()
                + "|taskState=" + plugin.getTaskListRenderStateHash()
                + "|clState=" + plugin.getCollectionLogStateVersion()
                + "|prereqs=" + task.getPrereqs();
        List<PrerequisiteStatus> cached = prerequisiteStatusCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        List<PrerequisiteStatus> statuses = plugin.getPrerequisiteStatuses(task);
        List<PrerequisiteStatus> safeStatuses = statuses == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(statuses));
        prerequisiteStatusCache.put(cacheKey, safeStatuses);
        return safeStatuses;
    }

    private CollectionLogRequirementPreview buildCollectionLogRequirementPreview(XtremeTask task) {
        return buildCollectionLogRequirementPreview(task, true);
    }

    private String collectionLogRequirementPreviewCacheKey(XtremeTask task, boolean completedInstanceCanApplyAll)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return null;
        }

        TaskVerification verification = task.getVerification();
        if (verification == null)
        {
            return null;
        }

        if (verification.getType() == TaskVerification.VerificationType.SKILL)
        {
            return skillcapePreviewCacheKey(task, verification, completedInstanceCanApplyAll);
        }

        if (verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return null;
        }

        int[] itemIds = verification.getItemIds();
        if (itemIds == null || itemIds.length == 0)
        {
            return null;
        }

        String canonicalItemIds = canonicalItemIds(itemIds);
        XtremeTask current = plugin.getCurrentTask();
        Integer baselineCount = plugin.getCurrentTaskCollectionLogBaselineCount(canonicalItemIds);

        return "cl|pack=" + plugin.getLoadedPackVersion()
                + "|task=" + safeTaskId(task)
                + "|count=" + verification.getCount()
                + "|completion=" + verification.getCompletionItemId()
                + "|applyAll=" + completedInstanceCanApplyAll
                + "|items=" + canonicalItemIds
                + "|taskState=" + plugin.getTaskListRenderStateHash()
                + "|pendingIncomplete=" + pendingTaskResolveIncompleteSignature()
                + "|clState=" + plugin.getCollectionLogStateVersion()
                + "|current=" + safeTaskId(current)
                + "|baseline=" + baselineCount
                + "|pendingPages=" + plugin.getPendingAncientPageDropCountSinceLastSync()
                + "|pendingMedallionFragments=" + plugin.getPendingMedallionFragmentDropCountSinceLastSync();
    }

    private String skillcapePreviewCacheKey(XtremeTask task, TaskVerification verification, boolean completedInstanceCanApplyAll)
    {
        if (verification.getExperience() == null || verification.getExperience().isEmpty())
        {
            return null;
        }

        return "skillcape|pack=" + plugin.getLoadedPackVersion()
                + "|task=" + safeTaskId(task)
                + "|applyAll=" + completedInstanceCanApplyAll
                + "|xp=" + verification.getExperience().keySet()
                + "|clState=" + plugin.getCollectionLogStateVersion();
    }

    private static String safeTaskId(XtremeTask task)
    {
        return task == null || task.getId() == null ? "" : task.getId();
    }

    private String pendingTaskResolveIncompleteSignature()
    {
        if (selectedTaskResolveIncompleteTaskIds.isEmpty())
        {
            return "";
        }
        List<String> ids = new ArrayList<>(selectedTaskResolveIncompleteTaskIds);
        Collections.sort(ids);
        return String.join(",", ids);
    }

    private boolean isTaskCompletedForCollectionLogPreview(XtremeTask task)
    {
        if (task == null || !plugin.isTaskCompleted(task))
        {
            return false;
        }
        String id = task.getId();
        return id == null || !selectedTaskResolveIncompleteTaskIds.contains(id);
    }

    private boolean isTaskCompletedForTaskDetails(XtremeTask task)
    {
        return isTaskCompletedForCollectionLogPreview(task);
    }

    private TaskGroupProgress taskDetailsGroupProgress(XtremeTask task)
    {
        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress == null || !progress.isGrouped() || selectedTaskResolveIncompleteTaskIds.isEmpty())
        {
            return progress;
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return progress;
        }

        int stagedIncompleteCompletedCount = 0;
        for (XtremeTask groupedTask : group)
        {
            String id = groupedTask == null ? null : groupedTask.getId();
            if (id != null
                    && selectedTaskResolveIncompleteTaskIds.contains(id)
                    && plugin.isTaskCompleted(groupedTask))
            {
                stagedIncompleteCompletedCount++;
            }
        }
        if (stagedIncompleteCompletedCount <= 0)
        {
            return progress;
        }

        return new TaskGroupProgress(
                Math.max(0, progress.getCompleted() - stagedIncompleteCompletedCount),
                progress.getTotal());
    }

    private void logSlowPreviewBuild(long startNanos, XtremeTask task, CollectionLogRequirementPreview preview)
    {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos < SLOW_PREVIEW_LOG_THRESHOLD_NANOS)
        {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSlowPreviewLogMs < SLOW_PREVIEW_LOG_INTERVAL_MS)
        {
            return;
        }

        lastSlowPreviewLogMs = nowMs;
        int itemCount = preview == null ? 0 : preview.getItems().size();
        log.debug("Slow collection-log preview build: taskId={}, items={}, elapsed={}ms",
                safeTaskId(task), itemCount, elapsedNanos / 1_000_000L);
    }

    private static boolean isCollectionLogSyncSource(TaskSource source)
    {
        return source == TaskSource.COLLECTION_LOG || source == TaskSource.DIARY_ACHIEVEMENT;
    }

    private CollectionLogRequirementPreview buildCollectionLogRequirementPreview(
            XtremeTask task,
            boolean completedInstanceCanApplyAll)
    {
        String cacheKey = collectionLogRequirementPreviewCacheKey(task, completedInstanceCanApplyAll);
        if (cacheKey == null)
        {
            return null;
        }

        CollectionLogRequirementPreview cached = collectionLogPreviewCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        long startNanos = System.nanoTime();
        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreviewUncached(task, completedInstanceCanApplyAll);
        if (preview != null)
        {
            collectionLogPreviewCache.put(cacheKey, preview);
        }
        logSlowPreviewBuild(startNanos, task, preview);
        return preview;
    }

    private CollectionLogRequirementPreview buildCollectionLogRequirementPreviewUncached(
            XtremeTask task,
            boolean completedInstanceCanApplyAll)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG) return null;

        TaskVerification verification = task.getVerification();
        if (verification == null) return null;

        if (verification.getType() == TaskVerification.VerificationType.SKILL)
        {
            return buildSkillcapeRequirementPreview(task, verification);
        }

        if (verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG) return null;

        int[] itemIds = verification.getItemIds();
        if (itemIds == null || itemIds.length == 0) return null;

        List<XtremeTask> requirementSequence = collectionLogRequirementSequence(task, itemIds);
        boolean isCountedSequence = requirementSequence.size() > 1;
        boolean repeatedDistinctPool = isCountedSequence && itemIds.length > 1;
        boolean singleEligibleItem = itemIds.length == 1;
        boolean hasCompletionItem = verification.getCompletionItemId() != null && verification.getCompletionItemId() > 0;

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
                        itemIds,
                        totalObtainedCount,
                        completedInstanceCanApplyAll)
                : null;

        Map<Integer, CollectionLogRequirementItem.Status> statusByItemId = collectionLogRequirementStatuses(
                itemIds,
                repeatedDistinctPool,
                repeatedRequirementState);
        applySequenceTaskCompletionStatuses(task, itemIds, statusByItemId);
        boolean ancientPageRequirement = isAncientPageRequirement(itemIds);
        boolean medallionFragmentRequirement = isMedallionFragmentRequirement(itemIds);
        List<CollectionLogRequirementItem> items = hasCompletionItem
                ? distinctCollectionLogRequirementItems(itemIds, statusByItemId)
                : coalescedCollectionLogRequirementItems(itemIds, statusByItemId);
        List<CollectionLogRequirementPreview.TierSection> sequenceTierSections = sequenceTierSections(task);
        boolean showSequenceTierSections = !sequenceTierSections.isEmpty();
        boolean sameNameFamily = !hasCompletionItem && coalescedItemNameCount(itemIds) == 1;
        boolean allRequiredItemsObtained = totalObtainedCount >= requiredCount;
        List<CollectionLogRequirementItem> secondaryItems = completionRequirementItems(verification, allRequiredItemsObtained);
        String sequenceSummaryText = showSequenceTierSections
                ? fullSequencePreviewSummaryText(task)
                : sequencePreviewSummaryText(task, itemIds);
        String summaryText = showSequenceTierSections
            ? sequenceSummaryText
            : singleEligibleItem
            ? ""
            : !sequenceSummaryText.isEmpty()
            ? sequenceSummaryText
            : repeatedDistinctPool
                ? repeatedRequirementState.summaryText
            : sameNameFamily
                ? shownObtainedCount + "/" + requiredCount + " " + pluralizeRequirementName(items.get(0).getName(), requiredCount) + " obtained"
            : "";
        String pendingAncientPageSummary = ancientPageRequirement ? pendingAncientPageSummary() : "";
        if (!pendingAncientPageSummary.isEmpty())
        {
            summaryText = summaryText.isEmpty() ? pendingAncientPageSummary : summaryText + "  " + pendingAncientPageSummary;
        }
        String pendingMedallionFragmentSummary = medallionFragmentRequirement ? pendingMedallionFragmentSummary() : "";
        if (!pendingMedallionFragmentSummary.isEmpty())
        {
            summaryText = summaryText.isEmpty() ? pendingMedallionFragmentSummary : summaryText + "  " + pendingMedallionFragmentSummary;
        }
        String titleText = showSequenceTierSections
                ? "Eligible Collection Log items"
                : requiredCount >= itemIds.length
                ? "Needed Collection Log items"
                : "";
        String secondaryTitleText = secondaryItems.isEmpty()
                ? ""
                : allRequiredItemsObtained ? "Now assemble:" : "Need all " + requiredCount + " fragments to assemble:";
        return new CollectionLogRequirementPreview(
                summaryText,
                titleText,
                !summaryText.isEmpty() && (!singleEligibleItem && !hasCompletionItem && (sameNameFamily || repeatedDistinctPool)
                        || !pendingMedallionFragmentSummary.isEmpty() || showSequenceTierSections),
                !showSequenceTierSections,
                items,
                8,
                secondaryTitleText,
                secondaryItems,
                1,
                sequenceTierSections);
    }

    private List<CollectionLogRequirementItem> distinctCollectionLogRequirementItems(
            int[] itemIds,
            Map<Integer, CollectionLogRequirementItem.Status> statusByItemId)
    {
        List<CollectionLogRequirementItem> items = new ArrayList<>();
        for (int itemId : itemIds)
        {
            items.add(new CollectionLogRequirementItem(
                    itemId,
                    collectionLogRequirementItemName(itemId),
                    statusByItemId.getOrDefault(itemId, CollectionLogRequirementItem.Status.MISSING),
                    collectionLogRequirementBadgeText(itemId)));
        }
        return items;
    }

    private List<CollectionLogRequirementItem> coalescedCollectionLogRequirementItems(
            int[] itemIds,
            Map<Integer, CollectionLogRequirementItem.Status> statusByItemId)
    {
        Map<String, CollectionLogRequirementItem.Status> statusByItemName = new LinkedHashMap<>();
        Map<String, Integer> itemIdByItemName = new LinkedHashMap<>();
        for (int itemId : itemIds) {
            String itemName = collectionLogRequirementItemName(itemId);
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
            int itemId = itemIdByItemName.getOrDefault(entry.getKey(), -1);
            items.add(new CollectionLogRequirementItem(itemId, entry.getKey(), entry.getValue(), collectionLogRequirementBadgeText(itemId)));
        }
        return items;
    }

    private int coalescedItemNameCount(int[] itemIds)
    {
        Set<String> names = new HashSet<>();
        for (int itemId : itemIds)
        {
            names.add(collectionLogRequirementItemName(itemId));
        }
        return names.size();
    }

    private List<CollectionLogRequirementItem> completionRequirementItems(
            TaskVerification verification,
            boolean allRequiredItemsObtained)
    {
        Integer completionItemId = verification == null ? null : verification.getCompletionItemId();
        if (completionItemId == null || completionItemId <= 0)
        {
            return List.of();
        }

        CollectionLogRequirementItem.Status status = plugin.isCollectionLogItemObtained(completionItemId)
                ? CollectionLogRequirementItem.Status.OBTAINED
                : allRequiredItemsObtained
                ? CollectionLogRequirementItem.Status.READY
                : CollectionLogRequirementItem.Status.MISSING;
        return List.of(new CollectionLogRequirementItem(completionItemId, plugin.getItemName(completionItemId), status, "", true));
    }

    private String collectionLogRequirementItemName(int itemId)
    {
        int medallionFragmentNumber = medallionFragmentNumber(itemId);
        if (medallionFragmentNumber > 0)
        {
            return "Medallion fragment #" + medallionFragmentNumber;
        }

        int ancientPageNumber = ancientPageNumber(itemId);
        if (ancientPageNumber > 0)
        {
            return "Page " + ancientPageNumber;
        }
        return plugin.getItemName(itemId);
    }

    private String collectionLogRequirementBadgeText(int itemId)
    {
        int ancientPageNumber = ancientPageNumber(itemId);
        if (ancientPageNumber > 0)
        {
            return String.valueOf(ancientPageNumber);
        }
        return "";
    }

    private String pendingMedallionFragmentSummary()
    {
        int pendingDrops = plugin.getPendingMedallionFragmentDropCountSinceLastSync();
        if (pendingDrops <= 0)
        {
            return "";
        }

        return pendingDrops + " Medallion fragment " + (pendingDrops == 1 ? "drop needs" : "drops need")
                + " CLOG sync to identify fragment number.";
    }

    private String pendingAncientPageSummary()
    {
        int pendingDrops = plugin.getPendingAncientPageDropCountSinceLastSync();
        if (pendingDrops <= 0)
        {
            return "";
        }

        return pendingDrops + " Ancient page " + (pendingDrops == 1 ? "drop needs" : "drops need")
                + " CLOG sync to identify page number.";
    }

    private static int ancientPageNumber(int itemId)
    {
        if (itemId < ANCIENT_PAGE_FIRST_ITEM_ID || itemId > ANCIENT_PAGE_LAST_ITEM_ID)
        {
            return -1;
        }
        return itemId - ANCIENT_PAGE_FIRST_ITEM_ID + 1;
    }

    private static int medallionFragmentNumber(int itemId)
    {
        if (itemId < 32388 || itemId > 32395)
        {
            return -1;
        }
        return itemId - 32388 + 1;
    }

    private static boolean isMedallionFragmentRequirement(int[] itemIds)
    {
        if (itemIds == null || itemIds.length != 8)
        {
            return false;
        }

        int[] sorted = Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .toArray();
        if (sorted.length != 8)
        {
            return false;
        }

        for (int i = 0; i < sorted.length; i++)
        {
            if (sorted[i] != 32388 + i)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isAncientPageRequirement(int[] itemIds)
    {
        int expectedCount = ANCIENT_PAGE_LAST_ITEM_ID - ANCIENT_PAGE_FIRST_ITEM_ID + 1;
        if (itemIds == null || itemIds.length != expectedCount)
        {
            return false;
        }

        int[] sorted = Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .toArray();
        if (sorted.length != expectedCount)
        {
            return false;
        }

        for (int i = 0; i < sorted.length; i++)
        {
            if (sorted[i] != ANCIENT_PAGE_FIRST_ITEM_ID + i)
            {
                return false;
            }
        }
        return true;
    }

    private CollectionLogRequirementPreview buildSkillcapeRequirementPreview(XtremeTask task, TaskVerification verification)
    {
        if (task == null
                || task.getName() == null
                || !task.getName().toLowerCase(Locale.ROOT).contains("level 99 skillcape")
                || verification == null
                || verification.getExperience() == null
                || verification.getExperience().isEmpty())
        {
            return null;
        }

        List<CollectionLogRequirementItem> items = new ArrayList<>();
        for (String skillKey : SKILLCAPE_DISPLAYS.keySet())
        {
            if (!verification.getExperience().containsKey(skillKey))
            {
                continue;
            }

            SkillcapeDisplay display = SKILLCAPE_DISPLAYS.get(skillKey);
            items.add(new CollectionLogRequirementItem(
                    display.displayItemId(),
                    display.name,
                    isAnyCollectionLogItemObtained(display.itemIds)
                            ? CollectionLogRequirementItem.Status.OBTAINED
                            : CollectionLogRequirementItem.Status.MISSING));
        }

        if (items.isEmpty())
        {
            return null;
        }

        return new CollectionLogRequirementPreview("", "Eligible Skillcapes", false, true, items);
    }

    private boolean isAnyCollectionLogItemObtained(int[] itemIds)
    {
        if (itemIds == null)
        {
            return false;
        }

        for (int itemId : itemIds)
        {
            if (plugin.isCollectionLogItemObtained(itemId))
            {
                return true;
            }
        }
        return false;
    }

    private String sequencePreviewSummaryText(XtremeTask task, int[] itemIds)
    {
        if (!isDisplaySequenceTaskName(task == null ? null : task.getName()))
        {
            return "";
        }
        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress != null && progress.isComplete())
        {
            return "";
        }

        Integer itemId = sequencePreviewFocusItemId(task, itemIds);
        if (itemId == null || itemId <= 0)
        {
            return "";
        }

        String itemName = plugin.getItemName(itemId);
        if (itemName == null || itemName.trim().isEmpty())
        {
            itemName = "item " + itemId;
        }

        return "Next: " + itemName;
    }

    private String fullSequencePreviewSummaryText(XtremeTask task)
    {
        Integer itemId = fullSequencePreviewFocusItemId(task);
        if (itemId == null || itemId <= 0)
        {
            return "";
        }

        String itemName = plugin.getItemName(itemId);
        if (itemName == null || itemName.trim().isEmpty())
        {
            itemName = "item " + itemId;
        }
        return "Next: " + itemName;
    }

    private Integer fullSequencePreviewFocusItemId(XtremeTask task)
    {
        List<XtremeTask> sequence = fullDisplaySequenceTasks(task);
        if (sequence.isEmpty())
        {
            return null;
        }

        for (XtremeTask sequenceTask : sequence)
        {
            Integer itemId = sequenceItemId(sequenceTask);
            if (itemId == null || itemId <= 0)
            {
                continue;
            }

            if (!isTaskCompletedForCollectionLogPreview(sequenceTask) && !plugin.isCollectionLogItemObtained(itemId))
            {
                return itemId;
            }
        }
        return null;
    }

    private List<CollectionLogRequirementPreview.TierSection> sequenceTierSections(XtremeTask task)
    {
        List<XtremeTask> sequence = fullDisplaySequenceTasks(task);
        if (sequence.isEmpty())
        {
            return List.of();
        }

        LinkedHashMap<TaskTier, List<CollectionLogRequirementItem>> itemsByTier = new LinkedHashMap<>();
        for (XtremeTask sequenceTask : sequence)
        {
            Integer itemId = sequenceItemId(sequenceTask);
            if (itemId == null || itemId <= 0)
            {
                continue;
            }

            CollectionLogRequirementItem.Status status = isTaskCompletedForCollectionLogPreview(sequenceTask)
                    ? CollectionLogRequirementItem.Status.APPLIED
                    : plugin.isCollectionLogItemObtained(itemId)
                    ? CollectionLogRequirementItem.Status.OBTAINED
                    : CollectionLogRequirementItem.Status.MISSING;
            itemsByTier.computeIfAbsent(sequenceTask.getTier(), ignored -> new ArrayList<>())
                    .add(new CollectionLogRequirementItem(
                            itemId,
                            collectionLogRequirementItemName(itemId),
                            status,
                            collectionLogRequirementBadgeText(itemId)));
        }

        if (itemsByTier.size() <= 1)
        {
            return List.of();
        }

        List<CollectionLogRequirementPreview.TierSection> sections = new ArrayList<>();
        TaskTier workingTier = plugin.getCurrentTier();
        for (Map.Entry<TaskTier, List<CollectionLogRequirementItem>> entry : itemsByTier.entrySet())
        {
            if (entry.getValue().isEmpty())
            {
                continue;
            }
            sections.add(new CollectionLogRequirementPreview.TierSection(
                    entry.getKey(),
                    Objects.equals(entry.getKey(), task.getTier()),
                    Objects.equals(entry.getKey(), workingTier),
                    entry.getValue(),
                    8));
        }
        return sections;
    }

    private List<XtremeTask> fullDisplaySequenceTasks(XtremeTask task)
    {
        String familyKey = displaySequenceFamilyKey(task);
        if (familyKey.isEmpty())
        {
            return List.of();
        }

        String cacheKey = plugin.getLoadedPackVersion() + "|full-sequence|" + familyKey;
        List<XtremeTask> cached = collectionLogSequenceCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        List<XtremeTask> sequence = new ArrayList<>();
        int sourceIndex = 0;
        Map<String, Integer> originalIndexById = new HashMap<>();
        for (XtremeTask candidate : plugin.getDummyTasks())
        {
            if (candidate != null && candidate.getId() != null)
            {
                originalIndexById.putIfAbsent(candidate.getId(), sourceIndex);
            }
            sourceIndex++;

            if (candidate == null
                    || candidate.getSource() != TaskSource.COLLECTION_LOG
                    || !familyKey.equals(displaySequenceFamilyKey(candidate))
                    || sequenceItemId(candidate) == null)
            {
                continue;
            }
            sequence.add(candidate);
        }

        sequence.sort(Comparator
                .comparingInt((XtremeTask candidate) -> sequenceItemSortIndex(sequenceItemId(candidate)))
                .thenComparingInt(candidate -> originalIndexById.getOrDefault(candidate.getId(), Integer.MAX_VALUE)));
        List<XtremeTask> immutable = Collections.unmodifiableList(sequence);
        collectionLogSequenceCache.put(cacheKey, immutable);
        return immutable;
    }

    private String displaySequenceFamilyKey(XtremeTask task)
    {
        String normalized = task == null || task.getName() == null
                ? ""
                : task.getName().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !isDisplaySequenceTaskName(normalized))
        {
            return "";
        }
        if (normalized.contains("metal boots"))
        {
            return "metal-boots";
        }
        if (normalized.contains("mta wand") || normalized.contains("magic training arena wand"))
        {
            return "mta-wand";
        }
        return normalized;
    }

    private Integer sequenceItemId(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        if (verification == null
                || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG
                || verification.getCount() == null
                || verification.getCount() <= 0)
        {
            return null;
        }

        int[] itemIds = verification.getItemIds();
        int index = verification.getCount() - 1;
        if (itemIds == null || index < 0 || index >= itemIds.length)
        {
            return null;
        }
        return itemIds[index];
    }

    private int sequenceItemSortIndex(Integer itemId)
    {
        if (itemId == null)
        {
            return Integer.MAX_VALUE;
        }

        int[] knownOrder = {
                4119, 4121, 4123, 4125, 4127, 4129, 4131,
                6908, 6910, 6912, 6914
        };
        for (int i = 0; i < knownOrder.length; i++)
        {
            if (knownOrder[i] == itemId)
            {
                return i;
            }
        }
        return 1000 + itemId;
    }

    private Integer sequencePreviewFocusItemId(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        if (verification == null || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return null;
        }
        return sequencePreviewFocusItemId(task, verification.getItemIds());
    }

    private Integer sequencePreviewFocusItemId(XtremeTask task, int[] itemIds)
    {
        if (!isDisplaySequenceTaskName(task == null ? null : task.getName()) || itemIds == null || itemIds.length == 0)
        {
            return null;
        }

        int completedPrefix = sequencePreviewCompletedPrefixCount(task, itemIds);
        if (completedPrefix >= itemIds.length)
        {
            return itemIds[itemIds.length - 1];
        }
        return itemIds[completedPrefix];
    }

    private int sequencePreviewCompletedPrefixCount(XtremeTask task, int[] itemIds)
    {
        if (!isDisplaySequenceTaskName(task == null ? null : task.getName()) || itemIds == null || itemIds.length == 0)
        {
            return 0;
        }

        List<XtremeTask> sequence = collectionLogRequirementSequence(task, itemIds);
        int completed = 0;
        for (int i = 0; i < itemIds.length; i++)
        {
            boolean taskComplete = i < sequence.size() && isTaskCompletedForCollectionLogPreview(sequence.get(i));
            boolean itemObtained = plugin.isCollectionLogItemObtained(itemIds[i]);
            if (!taskComplete && !itemObtained)
            {
                break;
            }
            completed++;
        }
        return completed;
    }

    private void applySequenceTaskCompletionStatuses(
            XtremeTask task,
            int[] itemIds,
            Map<Integer, CollectionLogRequirementItem.Status> statusByItemId)
    {
        if (!isDisplaySequenceTaskName(task == null ? null : task.getName())
                || itemIds == null
                || itemIds.length == 0
                || statusByItemId == null)
        {
            return;
        }

        List<XtremeTask> sequence = collectionLogRequirementSequence(task, itemIds);
        for (int i = 0; i < itemIds.length && i < sequence.size(); i++)
        {
            XtremeTask sequenceTask = sequence.get(i);
            if (sequenceTask != null && isTaskCompletedForCollectionLogPreview(sequenceTask))
            {
                statusByItemId.put(itemIds[i], CollectionLogRequirementItem.Status.APPLIED);
            }
        }
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
        if (status == CollectionLogRequirementItem.Status.READY)
        {
            return 1;
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

        String cacheKey = plugin.getLoadedPackVersion() + "|" + normalizeRequirementSequenceName(task.getName()) + "|" + canonicalItemIds(itemIds);
        List<XtremeTask> cached = collectionLogSequenceCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        List<XtremeTask> sequence = new ArrayList<>();
        for (XtremeTask candidate : plugin.getDummyTasks())
        {
            if (sameCollectionLogRequirementSignature(task, candidate, itemIds))
            {
                sequence.add(candidate);
            }
        }
        sequence.sort(Comparator.comparingInt(candidate -> {
            TaskVerification verification = candidate == null ? null : candidate.getVerification();
            Integer count = verification == null ? null : verification.getCount();
            return count == null ? Integer.MAX_VALUE : count;
        }));
        List<XtremeTask> immutableSequence = Collections.unmodifiableList(sequence);
        collectionLogSequenceCache.put(cacheKey, immutableSequence);
        return immutableSequence;
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
            int[] itemIds,
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
            if (groupedTask != null && isTaskCompletedForCollectionLogPreview(groupedTask))
            {
                completedCount++;
            }
        }

        int completedThreshold = thresholdAt(thresholds, completedCount - 1);
        int currentDone = 0;
        int currentRequired = 0;
        int appliedObtainedCount = Math.max(0, Math.min(totalObtainedCount, completedThreshold));
        int availableObtainedCount = Math.max(0, totalObtainedCount - appliedObtainedCount);

        boolean exactInstanceCompleted = isTaskCompletedForCollectionLogPreview(task);
        if ((completedInstanceCanApplyAll && exactInstanceCompleted)
                || (isTaskCompletedForCollectionLogPreview(task) && isTaskGroupFullyCompleted(task)))
        {
            return new RepeatedCollectionLogRequirementState(
                    Math.max(0, totalObtainedCount),
                    0,
                    repeatedCollectionLogRequirementSummary(totalObtainedCount, currentDone, currentRequired, 0)
            );
        }

        int currentIndex = currentRequirementIndex(task, sequence);
        if (currentIndex >= 0)
        {
            int previousThreshold = currentIndex <= 0 ? 0 : thresholdAt(thresholds, currentIndex - 1);
            int currentThreshold = Math.max(thresholdAt(thresholds, currentIndex), previousThreshold);
            currentRequired = Math.max(1, currentThreshold - previousThreshold);

            Integer baselineCount = plugin.getCurrentTaskCollectionLogBaselineCount(canonicalItemIds(itemIds));
            if (baselineCount != null)
            {
                int baseline = Math.min(Math.max(0, baselineCount), Math.max(0, completedThreshold));
                currentDone = Math.max(0, totalObtainedCount - baseline);
                appliedObtainedCount = Math.max(0, Math.min(totalObtainedCount, baseline));
            }
            else
            {
                previousThreshold = Math.max(previousThreshold, completedThreshold);
                currentDone = Math.max(0, totalObtainedCount - previousThreshold);
                appliedObtainedCount = Math.max(0, Math.min(totalObtainedCount, previousThreshold + currentDone));
            }
            availableObtainedCount = Math.max(0, totalObtainedCount - appliedObtainedCount);
        }

        return new RepeatedCollectionLogRequirementState(
                appliedObtainedCount,
                availableObtainedCount,
                repeatedCollectionLogRequirementSummary(totalObtainedCount, currentDone, currentRequired, availableObtainedCount)
        );
    }

    private boolean isTaskGroupFullyCompleted(XtremeTask task)
    {
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return isTaskCompletedForCollectionLogPreview(task);
        }

        for (XtremeTask groupedTask : group)
        {
            if (groupedTask == null || !isTaskCompletedForCollectionLogPreview(groupedTask))
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
                || isTaskCompletedForCollectionLogPreview(current))
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

    private static String repeatedCollectionLogRequirementSummary(
            int totalObtainedCount,
            int currentDone,
            int currentRequired,
            int notYetAppliedCount)
    {
        String summary = "total obtained: " + totalObtainedCount;
        if (currentRequired > 0)
        {
            return summary + " | current task: " + currentDone + "/" + currentRequired;
        }
        return notYetAppliedCount > 0 ? summary + " | not yet applied: " + notYetAppliedCount : summary;
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
    private final TaskListScrollController syncMismatchGuardScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private int syncMismatchGuardTotalRows = 0;
    private int compactCurrentScrollPx = 0;
    private int compactCurrentMaxOffsetPx = 0;
    private double compactCurrentWheelRemainderPx = 0.0;
    private double compactCurrentPendingWheelRotation = 0.0;

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
    public XtremeTaskerOverlay(Client client, XtremeTaskerPlugin plugin, SpriteManager spriteManager, SkillIconManager skillIconManager) {
        this.client = client;
        this.plugin = plugin;
        this.spriteManager = spriteManager;
        this.skillIconManager = skillIconManager;

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

        long panelRenderStartNanos = System.nanoTime();
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
            logSlowPanelRender(panelRenderStartNanos);
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
        if (isTaskSyncResultOpen()) {
            renderTaskSyncResultPopup(g, fm);
        }
        if (isTaskResolveOpen()) {
            renderTaskResolvePopup(g, fm);
        }
        if (isTaskDetailsIncompleteConfirmOpen()) {
            renderTaskDetailsIncompleteConfirm(g, fm);
        }
        if (taskDetailsPopup.isOpen() && pendingMarkAllIncompleteTask != null) {
            renderMarkAllIncompleteConfirm(g, fm);
        }
        logSlowPanelRender(panelRenderStartNanos);
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
                this::isTaskCompletedForTaskDetails,
                plugin::getCompletionInfo,
                plugin::getTaskTimeTicks,
                useCondensedTaskRows() ? this::taskDetailsGroupProgress : null,
                useCondensedTaskRows() ? plugin::getTaskGroupInstances : null,
                this::getCachedPrerequisiteStatuses,
                this::getCachedSkillImage,
                this::getCachedPrerequisiteMarkerImage,
                task -> buildCollectionLogRequirementPreview(task, !useCondensedTaskRows()),
                plugin::getCollectionLogSequenceStepLabel,
                plugin::isTaskSyncMismatch,
                this::taskDetailsSyncButtonLabel,
                this::taskDetailsMarkIncompleteButtonLabel,
                this::taskDetailsMarkIncompleteEnabled,
                task -> taskResolveSavedIncompleteEdits,
                this::getCachedItemImage,
                this::taskDetailsWikiLinks,
                client.getMouseCanvasPosition(),
                resolveTaskIcon(taskDetailsPopup.task()),
                plugin.showTips()
        );
    }

    private List<WikiLink> taskDetailsWikiLinks(XtremeTask task)
    {
        if (task == null)
        {
            return List.of();
        }

        List<WikiLink> links = TASK_DETAILS_WIKI_LINKS_BY_ID.get(task.getId());
        if (links != null)
        {
            return links;
        }

        links = TASK_DETAILS_WIKI_LINKS_BY_NAME.get(normalizeWikiTaskName(task.getName()));
        if (links != null)
        {
            return links;
        }

        String url = task.getWikiUrl();
        if (url == null || url.trim().isEmpty())
        {
            return List.of();
        }
        return List.of(new WikiLink("Wiki", url));
    }

    private void renderTaskSyncResultPopup(Graphics2D g, FontMetrics fm)
    {
        if (taskSyncResultTask == null || taskSyncResultText == null || taskSyncResultText.isEmpty())
        {
            return;
        }

        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        int pad = 14;
        int buttonH = ROW_HEIGHT + 8;
        int buttonW = 78;
        int textW = Math.min(300, panelBounds.width - 90);
        List<String> lines = TextUtils.wrapText(taskSyncResultText, fm, textW);
        String freshnessHint = "Open your Collection Log first, then sync again for the freshest data.";
        List<String> hintLines = taskSyncResultShowFreshnessHint
                ? TextUtils.wrapText(freshnessHint, fm, textW)
                : Collections.emptyList();
        int w = Math.min(panelBounds.width - 54, Math.max(240, textW + pad * 2));
        int h = Math.max(96, pad * 2 + (lines.size() + hintLines.size()) * fm.getHeight() + 18 + buttonH);
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        taskSyncResultBounds.setBounds(x, y, w, h);
        drawBevelBox(g, taskSyncResultBounds, new Color(45, 36, 24, 252));

        Color resultColor = taskSyncResultResolvable
                ? new Color(245, 92, 82, 245)
                : new Color(105, 220, 125, 245);
        g.setColor(resultColor);
        int textY = y + pad + fm.getAscent();
        for (String line : lines)
        {
            String drawLine = TextUtils.truncateToWidth(line, fm, w - pad * 2);
            g.drawString(drawLine, x + (w - fm.stringWidth(drawLine)) / 2, textY);
            textY += fm.getHeight();
        }
        if (!hintLines.isEmpty())
        {
            textY += 2;
            g.setColor(P.UI_TEXT_DIM);
            for (String line : hintLines)
            {
                String drawLine = TextUtils.truncateToWidth(line, fm, w - pad * 2);
                g.drawString(drawLine, x + (w - fm.stringWidth(drawLine)) / 2, textY);
                textY += fm.getHeight();
            }
        }

        int buttonY = y + h - pad - buttonH;
        taskSyncResultCloseBounds.setBounds(x + w - pad - buttonW, buttonY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, taskSyncResultCloseBounds, "Close", P.BTN_DISABLED_BG);
    }

    private void renderTaskResolvePopup(Graphics2D g, FontMetrics fm)
    {
        XtremeTask task = taskResolveTask;
        if (task == null)
        {
            return;
        }

        g.setColor(new Color(0, 0, 0, 135));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        List<XtremeTask> resolveTasks = resolveTasksForTask(task);
        boolean sequenceResolve = isTaskResolveSequence(resolveTasks);
        int pad = 12;
        int rowGap = 4;
        int rowH = ROW_HEIGHT + rowGap;
        int buttonH = ROW_HEIGHT + 8;
        int listRows = Math.max(1, resolveTasks.size());
        String title = "Mark instance(s) incomplete";
        String taskLine = "Task: " + task.getName();
        int w = taskResolvePopupWidth(fm, task, resolveTasks, title, taskLine, pad);
        int h = Math.min(panelBounds.height - 48,
                Math.max(132, pad * 2 + fm.getHeight() * (sequenceResolve ? 3 : 2) + 14 + listRows * rowH + 12 + buttonH));
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        taskResolveBounds.setBounds(x, y, w, h);
        drawBevelBox(g, taskResolveBounds, new Color(45, 36, 24, 252));
        taskResolveCloseBounds.setBounds(0, 0, 0, 0);

        g.setColor(P.UI_GOLD);
        g.drawString(title, x + pad, y + pad + fm.getAscent());
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(taskLine, fm, w - pad * 2),
                x + pad, y + pad + fm.getAscent() + fm.getHeight());
        if (sequenceResolve)
        {
            g.drawString(TextUtils.truncateToWidth("Completion must be edited in sequence.", fm, w - pad * 2),
                    x + pad, y + pad + fm.getAscent() + fm.getHeight() * 2);
        }

        int cursorY = y + pad + fm.getAscent() + fm.getHeight() * (sequenceResolve ? 4 : 3);
        taskResolveInstanceToggleBounds.clear();
        if (!resolveTasks.isEmpty())
        {
            for (XtremeTask instance : resolveTasks)
            {
                if (instance == null)
                {
                    continue;
                }
                boolean selectedIncomplete = instance.getId() != null && selectedTaskResolveIncompleteTaskIds.contains(instance.getId());
                boolean enabled = canToggleTaskResolveTaskIncomplete(instance);
                Rectangle toggle = new Rectangle(x + pad, cursorY - fm.getAscent() - 2,
                        TASK_RESOLVE_TOGGLE_SIZE, TASK_RESOLVE_TOGGLE_SIZE);
                taskResolveInstanceToggleBounds.put(instance, toggle);
                drawResolveIncompleteToggleGlyph(g, toggle, selectedIncomplete, enabled);
                String detailText = taskResolveCompletionDateText(instance)
                        + " | " + taskResolveTimeSpentText(instance);
                int lineX = toggle.x + toggle.width + TASK_RESOLVE_TOGGLE_GAP;
                int lineW = w - (pad + TASK_RESOLVE_TOGGLE_SIZE + TASK_RESOLVE_TOGGLE_GAP + pad);
                drawTaskResolveInstanceRow(g, fm, taskResolveInstanceMarker(task, instance), detailText, lineX, cursorY, lineW, enabled);
                cursorY += rowH;
            }
        }

        int actionY = y + h - pad - buttonH;
        int buttonW = 72;
        int gap = 8;
        int buttonsW = buttonW * 2 + gap;
        taskResolveSaveBounds.setBounds(x + (w - buttonsW) / 2, actionY, buttonW, buttonH);
        taskResolveCancelBounds.setBounds(taskResolveSaveBounds.x + buttonW + gap, actionY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, taskResolveSaveBounds, "Save",
                hasTaskResolveChanges() ? P.BTN_ENABLED_BG : P.BTN_DISABLED_BG,
                hasTaskResolveChanges() ? P.UI_TEXT : P.UI_TEXT_DIM,
                hasTaskResolveChanges() ? P.UI_GOLD : null);
        buttonRenderer.drawPlainButton(g, taskResolveCancelBounds, "Cancel", P.BTN_DISABLED_BG);
    }

    private void renderSyncMismatchGroupResolve(Graphics2D g, FontMetrics fm)
    {
        XtremeTask task = syncMismatchGroupResolveTask;
        if (task == null)
        {
            return;
        }

        List<XtremeTask> resolveTasks = syncMismatchGroupResolveTasks(task);
        if (resolveTasks.isEmpty())
        {
            closeSyncMismatchGroupResolve();
            return;
        }

        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(syncMismatchReviewBounds.x, syncMismatchReviewBounds.y,
                syncMismatchReviewBounds.width, syncMismatchReviewBounds.height);

        int pad = 12;
        int rowGap = 4;
        int rowH = fm.getHeight() + rowGap + 2;
        int buttonH = ROW_HEIGHT + 8;
        String title = syncReviewPopupTitle(task);
        List<String> titleLines = TextUtils.wrapText(title, fm, Math.max(120, syncMismatchReviewBounds.width - 80));
        List<String> summaryLines = TextUtils.wrapText(syncMismatchGroupResolveSummary(task), fm, Math.max(120, syncMismatchReviewBounds.width - 80));
        String sequenceBlockMessage = syncMismatchGroupResolveTierBlockMessage(task);
        int w = syncMismatchGroupResolvePopupWidth(fm, task, resolveTasks, titleLines, summaryLines, pad);
        List<String> sequenceBlockLines = sequenceBlockMessage.isEmpty()
                ? List.of()
                : TextUtils.wrapText(sequenceBlockMessage, fm, Math.max(120, w - pad * 2));
        int h = Math.min(syncMismatchReviewBounds.height - 34,
                Math.max(126, pad * 2 + (titleLines.size() + summaryLines.size()) * fm.getHeight() + 16
                        + (sequenceBlockLines.isEmpty() ? 0 : sequenceBlockLines.size() * fm.getHeight() + 8)
                        + Math.max(1, resolveTasks.size()) * rowH + 12 + buttonH));
        int x = syncMismatchReviewBounds.x + (syncMismatchReviewBounds.width - w) / 2;
        int y = syncMismatchReviewBounds.y + (syncMismatchReviewBounds.height - h) / 2;
        syncMismatchGroupResolveBounds.setBounds(x, y, w, h);
        drawBevelBox(g, syncMismatchGroupResolveBounds, new Color(45, 36, 24, 252));

        int textY = y + pad + fm.getAscent();
        g.setColor(P.UI_GOLD);
        for (String line : titleLines)
        {
            g.drawString(TextUtils.truncateToWidth(line, fm, w - pad * 2), x + pad, textY);
            textY += fm.getHeight();
        }

        g.setColor(P.UI_TEXT_DIM);
        for (String line : summaryLines)
        {
            g.drawString(TextUtils.truncateToWidth(line, fm, w - pad * 2), x + pad, textY);
            textY += fm.getHeight();
        }

        if (!sequenceBlockLines.isEmpty())
        {
            textY += 4;
            g.setColor(P.UI_TEXT_DIM);
            for (String line : sequenceBlockLines)
            {
                g.drawString(TextUtils.truncateToWidth(line, fm, w - pad * 2), x + pad, textY);
                textY += fm.getHeight();
            }
        }

        int cursorY = textY + 10;
        syncMismatchGroupResolveToggleBounds.clear();
        for (XtremeTask instance : resolveTasks)
        {
            if (instance == null || instance.getId() == null)
            {
                continue;
            }

            boolean selected = selectedSyncMismatchGroupResolveTaskIds.contains(instance.getId());
            boolean enabled = sequenceBlockLines.isEmpty() && canToggleSyncMismatchGroupResolveTask(instance);
            Rectangle toggle = new Rectangle(x + pad, cursorY - fm.getAscent() - 2,
                    TASK_RESOLVE_TOGGLE_SIZE, TASK_RESOLVE_TOGGLE_SIZE);
            syncMismatchGroupResolveToggleBounds.put(instance, toggle);
            if (syncReviewMode == SyncReviewMode.MISMATCH)
            {
                drawResolveIncompleteToggleGlyph(g, toggle, selected, enabled);
            }
            else
            {
                drawResolveToggleGlyph(g, toggle, selected, enabled);
            }

            String marker = taskResolveInstanceMarker(task, instance);
            String detailText = taskResolveCompletionDateText(instance)
                    + " | " + taskResolveTimeSpentText(instance);
            int lineX = toggle.x + toggle.width + TASK_RESOLVE_TOGGLE_GAP;
            int lineW = w - (pad + TASK_RESOLVE_TOGGLE_SIZE + TASK_RESOLVE_TOGGLE_GAP + pad);
            drawTaskResolveInstanceRow(g, fm, marker, detailText, lineX, cursorY, lineW, enabled);
            cursorY += rowH;
        }

        int actionY = y + h - pad - buttonH;
        int buttonW = 72;
        int gap = 8;
        int buttonsW = buttonW * 2 + gap;
        boolean saveEnabled = sequenceBlockLines.isEmpty() && hasSyncMismatchGroupResolveChanges();
        syncMismatchGroupResolveSaveBounds.setBounds(x + (w - buttonsW) / 2, actionY, buttonW, buttonH);
        syncMismatchGroupResolveCancelBounds.setBounds(syncMismatchGroupResolveSaveBounds.x + buttonW + gap, actionY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, syncMismatchGroupResolveSaveBounds, "Save",
                saveEnabled ? P.BTN_ENABLED_BG : P.BTN_DISABLED_BG,
                saveEnabled ? P.UI_TEXT : P.UI_TEXT_DIM,
                saveEnabled ? P.UI_GOLD : null);
        buttonRenderer.drawPlainButton(g, syncMismatchGroupResolveCancelBounds, "Cancel", P.BTN_DISABLED_BG);
    }

    private int syncMismatchGroupResolvePopupWidth(
            FontMetrics fm,
            XtremeTask task,
            List<XtremeTask> resolveTasks,
            List<String> titleLines,
            List<String> summaryLines,
            int pad)
    {
        int desiredW = 270;
        for (String line : titleLines)
        {
            desiredW = Math.max(desiredW, pad * 2 + fm.stringWidth(line));
        }
        for (String line : summaryLines)
        {
            desiredW = Math.max(desiredW, pad * 2 + fm.stringWidth(line));
        }

        int rowReserve = pad + TASK_RESOLVE_TOGGLE_SIZE + TASK_RESOLVE_TOGGLE_GAP + pad;
        int fitBuffer = fm.charWidth('W');
        for (XtremeTask instance : resolveTasks)
        {
            if (instance == null)
            {
                continue;
            }
            String marker = taskResolveInstanceMarker(task, instance);
            String detailText = taskResolveCompletionDateText(instance)
                    + " | " + taskResolveTimeSpentText(instance);
            String rowText = taskResolveInstanceLine(marker, detailText);
            desiredW = Math.max(desiredW, rowReserve + fm.stringWidth(rowText) + fitBuffer);
        }

        return Math.min(Math.max(190, syncMismatchReviewBounds.width - 36), desiredW);
    }

    private String syncMismatchGroupResolveSummary(XtremeTask task)
    {
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        int total = group == null || group.isEmpty() ? 1 : group.size();
        List<XtremeTask> pluginCompletedTasks = syncMismatchGroupCompletedTasks(group, task);
        boolean sequence = isTaskResolveSequence(group);
        if (syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES)
        {
            List<XtremeTask> syncFoundTasks = syncMismatchGroupFoundInGameTasks(task);
            if (sequence)
            {
                return "Sync found " + joinedSequenceStepLabels(syncFoundTasks)
                        + " completed in game, but " + joinedSequenceStepLabels(pluginCompletedTasks)
                        + " marked completed in plugin";
            }
            return "Sync found " + syncFoundTasks.size() + "/" + total
                    + " completed in game, but " + pluginCompletedTasks.size()
                    + " marked in plugin";
        }

        List<XtremeTask> syncFoundTasks = syncMismatchGroupFoundTasks(task);
        if (sequence)
        {
            return "Sync found " + joinedSequenceStepLabels(syncFoundTasks)
                    + " completed in game, but " + joinedSequenceStepLabels(pluginCompletedTasks)
                    + " marked completed in plugin";
        }
        return "Sync found " + syncFoundTasks.size() + "/" + total
                + " completed in game, but " + pluginCompletedTasks.size()
                + " marked in plugin";
    }

    private List<XtremeTask> syncMismatchGroupCompletedTasks(List<XtremeTask> group, XtremeTask fallbackTask)
    {
        if (group == null || group.isEmpty())
        {
            return fallbackTask != null && plugin.isTaskCompleted(fallbackTask)
                    ? List.of(fallbackTask)
                    : List.of();
        }

        List<XtremeTask> completed = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && plugin.isTaskCompleted(groupedTask))
            {
                completed.add(groupedTask);
            }
        }
        return completed;
    }

    private List<XtremeTask> syncMismatchGroupFoundInGameTasks(XtremeTask task)
    {
        if (task == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return syncMismatchGroupActionTasks(task);
        }

        Set<String> actionIds = syncMismatchGroupActionTasks(task).stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<XtremeTask> found = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null
                    && groupedTask.getId() != null
                    && (plugin.isTaskCompleted(groupedTask) || actionIds.contains(groupedTask.getId())))
            {
                found.add(groupedTask);
            }
        }
        return found;
    }

    private String joinedSequenceStepLabels(List<XtremeTask> tasks)
    {
        List<String> labels = tasks == null
                ? List.of()
                : tasks.stream()
                .map(this::collectionLogSequenceSuffix)
                .filter(label -> label != null && !label.trim().isEmpty())
                .map(label -> label.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        if (labels.isEmpty())
        {
            return "none";
        }
        return joinedPlainLabels(labels);
    }

    private static String joinedPlainLabels(List<String> labels)
    {
        if (labels == null || labels.isEmpty())
        {
            return "";
        }
        if (labels.size() == 1)
        {
            return labels.get(0);
        }
        if (labels.size() == 2)
        {
            return labels.get(0) + " and " + labels.get(1);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1))
                + " and " + labels.get(labels.size() - 1);
    }

    private int taskResolvePopupWidth(FontMetrics fm, XtremeTask task, List<XtremeTask> resolveTasks,
                                      String title, String taskLine, int pad)
    {
        int desiredW = 255;
        desiredW = Math.max(desiredW, pad * 2 + fm.stringWidth(title));
        desiredW = Math.max(desiredW, pad * 2 + fm.stringWidth(taskLine));
        if (isTaskResolveSequence(resolveTasks))
        {
            desiredW = Math.max(desiredW, pad * 2 + fm.stringWidth("Completion must be edited in sequence."));
        }

        int rowReserve = pad + TASK_RESOLVE_TOGGLE_SIZE + TASK_RESOLVE_TOGGLE_GAP + pad;
        int fitBuffer = fm.charWidth('W');
        for (XtremeTask instance : resolveTasks)
        {
            if (instance == null)
            {
                continue;
            }
            String detailText = taskResolveCompletionDateText(instance)
                    + " | " + taskResolveTimeSpentText(instance);
            String rowText = taskResolveInstanceLine(taskResolveInstanceMarker(task, instance), detailText);
            desiredW = Math.max(desiredW, rowReserve + fm.stringWidth(rowText) + fitBuffer);
        }

        return Math.min(Math.max(180, panelBounds.width - 50), desiredW);
    }

    private void drawTaskResolveInstanceRow(
            Graphics2D g,
            FontMetrics fm,
            String marker,
            String detailText,
            int x,
            int baseline,
            int maxW,
            boolean enabled)
    {
        String cleanDetail = detailText == null ? "" : detailText.trim();
        String prefix = marker == null || marker.trim().isEmpty() ? "" : marker.trim() + ": ";
        if (prefix.isEmpty())
        {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString(TextUtils.truncateToWidth(cleanDetail, fm, maxW), x, baseline);
            return;
        }

        String drawPrefix = TextUtils.truncateToWidth(prefix, fm, maxW);
        g.setColor(enabled ? P.UI_TEXT : P.UI_TEXT_DIM);
        g.drawString(drawPrefix, x, baseline);

        int detailX = x + fm.stringWidth(drawPrefix);
        int detailW = Math.max(0, maxW - fm.stringWidth(drawPrefix));
        if (detailW <= 0)
        {
            return;
        }
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(cleanDetail, fm, detailW), detailX, baseline);
    }

    private String taskResolveInstanceLine(String marker, String detailText)
    {
        String prefix = marker == null || marker.trim().isEmpty() ? "" : marker.trim() + ": ";
        return prefix + (detailText == null ? "" : detailText.trim());
    }

    private String taskResolveInstanceMarker(XtremeTask task, XtremeTask instance)
    {
        String sequenceSuffix = collectionLogSequenceSuffix(instance);
        if (!sequenceSuffix.isEmpty())
        {
            return titleCaseSequenceMarker(sequenceSuffix);
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        int total = Math.max(1, group == null || group.isEmpty() ? 1 : group.size());
        int index = -1;
        if (group != null)
        {
            for (int i = 0; i < group.size(); i++)
            {
                XtremeTask groupedTask = group.get(i);
                if (sameTask(groupedTask, instance))
                {
                    index = i;
                    break;
                }
            }
        }

        if (index < 0)
        {
            index = 0;
            total = 1;
        }
        if (index == 0 && total == 1)
        {
            return "";
        }
        return (index + 1) + "/" + total;
    }

    private String collectionLogSequenceSuffix(XtremeTask task)
    {
        String suffix = plugin.getCollectionLogSequenceStepLabel(task);
        return suffix == null ? "" : suffix.trim();
    }

    private static String titleCaseSequenceMarker(String value)
    {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty())
        {
            return "";
        }

        StringBuilder out = new StringBuilder(clean.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < clean.length(); i++)
        {
            char c = clean.charAt(i);
            if (Character.isLetter(c))
            {
                out.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
            else
            {
                out.append(c);
                capitalizeNext = Character.isWhitespace(c) || c == '-' || c == '\'';
            }
        }
        return out.toString();
    }

    private boolean sameTask(XtremeTask a, XtremeTask b)
    {
        if (a == b)
        {
            return true;
        }
        if (a == null || b == null)
        {
            return false;
        }
        String aId = a.getId();
        String bId = b.getId();
        return aId != null && aId.equals(bId);
    }

    private void renderTaskDetailsIncompleteConfirm(Graphics2D g, FontMetrics fm)
    {
        g.setColor(new Color(0, 0, 0, 135));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        String message = "Mark selected task(s) incomplete?";
        String warning = "This cannot be undone.";
        int pad = 14;
        int buttonH = ROW_HEIGHT + 8;
        int buttonW = 78;
        int gap = 8;
        int textW = Math.max(fm.stringWidth(message), fm.stringWidth(warning));
        int w = Math.min(panelBounds.width - 48, Math.max(240, textW + pad * 2));
        int h = 112;
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        taskDetailsIncompleteConfirmBounds.setBounds(x, y, w, h);
        drawBevelBox(g, taskDetailsIncompleteConfirmBounds, new Color(45, 36, 24, 252));

        int textY = y + pad + fm.getAscent() + 4;
        g.setColor(P.UI_TEXT);
        g.drawString(message, x + (w - fm.stringWidth(message)) / 2, textY);
        g.setColor(new Color(245, 92, 82, 245));
        g.drawString(warning, x + (w - fm.stringWidth(warning)) / 2, textY + fm.getHeight() + 3);

        int buttonsW = buttonW * 2 + gap;
        int buttonY = y + h - pad - buttonH;
        taskDetailsIncompleteConfirmYesBounds.setBounds(x + (w - buttonsW) / 2, buttonY, buttonW, buttonH);
        taskDetailsIncompleteConfirmNoBounds.setBounds(taskDetailsIncompleteConfirmYesBounds.x + buttonW + gap, buttonY, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, taskDetailsIncompleteConfirmYesBounds, "Confirm",
                new Color(86, 31, 24, 235), P.UI_TEXT, new Color(245, 92, 82, 220));
        buttonRenderer.drawPlainButton(g, taskDetailsIncompleteConfirmNoBounds, "Cancel", P.BTN_DISABLED_BG);
    }

    private void drawResolveToggleGlyph(Graphics2D g, Rectangle bounds, boolean selected, boolean enabled)
    {
        Color bg;
        Color strokeColor;
        if (!enabled)
        {
            bg = selected ? new Color(43, 45, 43, 210) : new Color(28, 28, 28, 185);
            strokeColor = new Color(135, 135, 135, selected ? 185 : 95);
        }
        else
        {
            bg = selected ? new Color(45, 68, 38, 230) : P.INPUT_BG;
            strokeColor = selected ? new Color(105, 220, 125, 245) : new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 185);
        }

        drawBevelBox(g, bounds, bg);
        g.setColor(strokeColor);
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
        if (!selected)
        {
            return;
        }

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(bounds.x + 4, bounds.y + bounds.height / 2, bounds.x + bounds.width / 2 - 1, bounds.y + bounds.height - 5);
        g.drawLine(bounds.x + bounds.width / 2 - 1, bounds.y + bounds.height - 5, bounds.x + bounds.width - 4, bounds.y + 4);
        g.setStroke(oldStroke);
    }

    private void drawResolveIncompleteToggleGlyph(Graphics2D g, Rectangle bounds, boolean selectedIncomplete, boolean enabled)
    {
        Color bg;
        Color strokeColor;
        if (!enabled)
        {
            bg = selectedIncomplete ? new Color(48, 34, 32, 210) : new Color(28, 28, 28, 185);
            strokeColor = new Color(135, 135, 135, selectedIncomplete ? 185 : 95);
        }
        else
        {
            bg = selectedIncomplete ? new Color(82, 36, 30, 230) : P.INPUT_BG;
            strokeColor = selectedIncomplete ? new Color(245, 92, 82, 245) : new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 185);
        }

        drawBevelBox(g, bounds, bg);
        g.setColor(strokeColor);
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
        if (!selectedIncomplete)
        {
            return;
        }

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(bounds.x + 5, bounds.y + 5, bounds.x + bounds.width - 5, bounds.y + bounds.height - 5);
        g.drawLine(bounds.x + bounds.width - 5, bounds.y + 5, bounds.x + 5, bounds.y + bounds.height - 5);
        g.setStroke(oldStroke);
    }

    private String taskResolveCompletionDateText(XtremeTask task)
    {
        CompletionInfo info = plugin.getCompletionInfo(task);
        if (info == null || info.timestamp <= 0)
        {
            return "date unknown";
        }
        return TASK_RESOLVE_COMPLETION_DATE_FORMAT.format(Instant.ofEpochMilli(info.timestamp));
    }

    private String taskResolveTimeSpentText(XtremeTask task)
    {
        Long ticks = plugin.getTaskTimeTicks(task);
        if (ticks == null || ticks <= 0)
        {
            return "time unknown";
        }
        return compactFormatTicks(Math.round(ticks * 0.6));
    }

    private String taskDetailsSyncButtonLabel(XtremeTask task)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return "";
        }
        updateTaskDetailsSyncResultIfReady(task);
        return "Sync";
    }

    private String taskDetailsMarkIncompleteButtonLabel(XtremeTask task)
    {
        if (taskResolveSavedIncompleteEdits)
        {
            return isMultiInstanceTask(task)
                    ? "Edit task(s) completion"
                    : "Edit task completion";
        }
        return isMultiInstanceTask(task)
                ? "Mark task(s) incomplete"
                : "Mark task incomplete";
    }

    private boolean taskDetailsMarkIncompleteEnabled(XtremeTask task)
    {
        return task != null && plugin.isTaskSyncMismatch(task);
    }

    private boolean isMultiInstanceTask(XtremeTask task)
    {
        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress != null && progress.getTotal() > 1)
        {
            return true;
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group != null && group.size() > 1)
        {
            return true;
        }

        TaskVerification verification = task == null ? null : task.getVerification();
        Integer count = verification == null ? null : verification.getCount();
        if (count != null && count > 1)
        {
            return true;
        }

        String name = task == null ? "" : String.valueOf(task.getName());
        return name.matches(".*\\b\\d+\\s*/\\s*\\d+\\b.*")
                || name.matches(".*\\b\\d+\\s*/\\s*#.*");
    }

    private void handleTaskDetailsMarkIncompleteButton(XtremeTask task)
    {
        if (!taskDetailsMarkIncompleteEnabled(task))
        {
            return;
        }
        openTaskResolve(task);
    }

    private void handleTaskDetailsSyncButton(XtremeTask task)
    {
        if (task == null)
        {
            return;
        }

        closeTaskSyncResult();
        taskDetailsSyncPendingTaskId = task.getId();
        taskDetailsSyncStartedAt = plugin.getLastCollectionLogSyncResultAtLocalTime();
        taskDetailsSyncStartedObservedCount = syncedCollectionLogCountForTask(task);
        plugin.syncCollectionLogTaskAndPersist(task);
    }

    private void updateTaskDetailsSyncResultIfReady(XtremeTask task)
    {
        if (task == null
                || task.getId() == null
                || !task.getId().equals(taskDetailsSyncPendingTaskId))
        {
            return;
        }

        String currentSyncAt = plugin.getLastCollectionLogSyncResultAtLocalTime();
        if (currentSyncAt == null || Objects.equals(currentSyncAt, taskDetailsSyncStartedAt))
        {
            return;
        }

        boolean stillMismatch = plugin.isCollectionLogTaskSyncMismatch(task);
        boolean foundRelevantCandidate = hasRelevantCompletionCandidate(task)
                || syncedCollectionLogCountForTask(task) > taskDetailsSyncStartedObservedCount;
        taskDetailsSyncPendingTaskId = null;
        taskDetailsSyncStartedObservedCount = -1;
        taskSyncResultTask = task;
        taskSyncResultResolvable = stillMismatch;
        taskSyncResultShowFreshnessHint = false;
        if (!stillMismatch)
        {
            taskSyncResultText = "Found CLOG(s) to complete task(s).";
        }
        else if (foundRelevantCandidate)
        {
            taskSyncResultText = "Found some CLOG(s) to complete task(s), still not enough.";
        }
        else
        {
            taskSyncResultText = "Did not find any new CLOG for this task.";
            taskSyncResultShowFreshnessHint = true;
        }
    }

    private int syncedCollectionLogCountForTask(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        if (verification == null || verification.getItemIds() == null || verification.getItemIds().length == 0)
        {
            return 0;
        }
        return plugin.countSyncedCollectionLogItems(verification.getItemIds());
    }

    private boolean hasRelevantCompletionCandidate(XtremeTask task)
    {
        Set<String> relevantIds = taskAndGroupIds(task);
        if (relevantIds.isEmpty())
        {
            return false;
        }

        for (XtremeTask candidate : plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG))
        {
            if (candidate != null && relevantIds.contains(candidate.getId()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isTaskSyncResultOpen()
    {
        return taskSyncResultTask != null && taskSyncResultText != null && !taskSyncResultText.isEmpty();
    }

    private void closeTaskSyncResult()
    {
        taskSyncResultTask = null;
        taskSyncResultText = "";
        taskSyncResultResolvable = false;
        taskSyncResultShowFreshnessHint = false;
        taskDetailsSyncStartedObservedCount = -1;
        taskSyncResultBounds.setBounds(0, 0, 0, 0);
        taskSyncResultCloseBounds.setBounds(0, 0, 0, 0);
    }

    private void closeTaskDetailsWithPendingIncompleteCheck()
    {
        if (!selectedTaskResolveIncompleteTaskIds.isEmpty())
        {
            taskDetailsIncompleteConfirmOpen = true;
            closeTaskResolveWithoutRestoring();
            closeTaskSyncResult();
            return;
        }

        closeTaskDetailsNow();
    }

    private void closeTaskDetailsNow()
    {
        taskDetailsPopup.close();
        taskResolveSavedIncompleteEdits = false;
        selectedTaskResolveIncompleteTaskIds.clear();
        taskResolveOriginalIncompleteTaskIds.clear();
        closeTaskResolveWithoutRestoring();
        closeTaskSyncResult();
        closeTaskDetailsIncompleteConfirm();
    }

    private void confirmTaskDetailsIncompleteSelection()
    {
        List<XtremeTask> tasksToMarkIncomplete = selectedIncompleteTasksForDetails();
        if (!tasksToMarkIncomplete.isEmpty())
        {
            plugin.markSyncMismatchTasksIncompleteAndPersist(tasksToMarkIncomplete);
        }
        closeTaskDetailsNow();
    }

    private List<XtremeTask> selectedIncompleteTasksForDetails()
    {
        XtremeTask task = taskDetailsPopup.task();
        if (task == null || selectedTaskResolveIncompleteTaskIds.isEmpty())
        {
            return Collections.emptyList();
        }

        return resolveTasksForTask(task).stream()
                .filter(candidate -> candidate != null
                        && candidate.getId() != null
                        && selectedTaskResolveIncompleteTaskIds.contains(candidate.getId()))
                .collect(Collectors.toList());
    }

    private boolean isTaskDetailsIncompleteConfirmOpen()
    {
        return taskDetailsIncompleteConfirmOpen;
    }

    private void closeTaskDetailsIncompleteConfirm()
    {
        taskDetailsIncompleteConfirmOpen = false;
        taskDetailsIncompleteConfirmBounds.setBounds(0, 0, 0, 0);
        taskDetailsIncompleteConfirmYesBounds.setBounds(0, 0, 0, 0);
        taskDetailsIncompleteConfirmNoBounds.setBounds(0, 0, 0, 0);
    }

    private Set<String> taskAndGroupIds(XtremeTask task)
    {
        if (task == null || task.getId() == null)
        {
            return Collections.emptySet();
        }

        Set<String> ids = new HashSet<>();
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group != null)
        {
            for (XtremeTask groupedTask : group)
            {
                if (groupedTask != null && groupedTask.getId() != null)
                {
                    ids.add(groupedTask.getId());
                }
            }
        }
        ids.add(task.getId());
        return ids;
    }

    private void openTaskResolve(XtremeTask task)
    {
        if (task == null)
        {
            return;
        }
        taskResolveTask = task;
        taskResolveOriginalIncompleteTaskIds.clear();
        taskResolveOriginalIncompleteTaskIds.addAll(selectedTaskResolveIncompleteTaskIds);
        selectedTaskResolveIncompleteTaskIds.clear();
        selectedTaskResolveIncompleteTaskIds.addAll(taskResolveOriginalIncompleteTaskIds);
    }

    private void closeTaskResolve()
    {
        selectedTaskResolveIncompleteTaskIds.clear();
        selectedTaskResolveIncompleteTaskIds.addAll(taskResolveOriginalIncompleteTaskIds);
        closeTaskResolveWithoutRestoring();
    }

    private void closeTaskResolveWithoutRestoring()
    {
        taskResolveTask = null;
        taskResolveOriginalIncompleteTaskIds.clear();
        taskResolveBounds.setBounds(0, 0, 0, 0);
        taskResolveCloseBounds.setBounds(0, 0, 0, 0);
        taskResolveSaveBounds.setBounds(0, 0, 0, 0);
        taskResolveCancelBounds.setBounds(0, 0, 0, 0);
        taskResolveInstanceToggleBounds.clear();
    }

    private boolean isTaskResolveOpen()
    {
        return taskResolveTask != null;
    }

    private void toggleTaskResolveTaskIncomplete(XtremeTask task)
    {
        if (task == null || task.getId() == null)
        {
            return;
        }
        if (!canToggleTaskResolveTaskIncomplete(task))
        {
            return;
        }
        if (!selectedTaskResolveIncompleteTaskIds.remove(task.getId()))
        {
            selectedTaskResolveIncompleteTaskIds.add(task.getId());
        }
    }

    private void saveTaskResolve()
    {
        taskResolveSavedIncompleteEdits = !selectedTaskResolveIncompleteTaskIds.isEmpty();
        closeTaskResolveWithoutRestoring();
        closeTaskSyncResult();
    }

    private boolean hasTaskResolveChanges()
    {
        return !selectedTaskResolveIncompleteTaskIds.equals(taskResolveOriginalIncompleteTaskIds);
    }

    private void openSyncMismatchGroupResolve(XtremeTask task)
    {
        if (!syncMismatchUsesGroupResolver(task))
        {
            return;
        }

        syncMismatchGroupResolveTask = task;
        syncMismatchDescriptionTask = null;
        syncMismatchApplyConfirmOpen = false;
        syncMismatchGuardMessage = null;
        selectedSyncMismatchGroupResolveTaskIds.clear();
        for (XtremeTask resolveTask : syncMismatchGroupResolveTasks(task))
        {
            if (resolveTask != null && resolveTask.getId() != null)
            {
                if (selectedSyncMismatchTaskIds.contains(resolveTask.getId()))
                {
                    selectedSyncMismatchGroupResolveTaskIds.add(resolveTask.getId());
                }
            }
        }
        originalSyncMismatchGroupResolveTaskIds.clear();
        originalSyncMismatchGroupResolveTaskIds.addAll(selectedSyncMismatchGroupResolveTaskIds);
    }

    private void closeSyncMismatchGroupResolve()
    {
        syncMismatchGroupResolveTask = null;
        selectedSyncMismatchGroupResolveTaskIds.clear();
        originalSyncMismatchGroupResolveTaskIds.clear();
        syncMismatchGroupResolveBounds.setBounds(0, 0, 0, 0);
        syncMismatchGroupResolveSaveBounds.setBounds(0, 0, 0, 0);
        syncMismatchGroupResolveCancelBounds.setBounds(0, 0, 0, 0);
        syncMismatchGroupResolveToggleBounds.clear();
    }

    private void saveSyncMismatchGroupResolve()
    {
        XtremeTask task = syncMismatchGroupResolveTask;
        if (task == null)
        {
            closeSyncMismatchGroupResolve();
            return;
        }

        for (XtremeTask selectableTask : syncMismatchGroupSelectableTasks(task))
        {
            if (selectableTask != null && selectableTask.getId() != null)
            {
                selectedSyncMismatchTaskIds.remove(selectableTask.getId());
            }
        }

        selectedSyncMismatchTaskIds.addAll(selectedSyncMismatchGroupResolveTaskIds);
        syncMismatchApplyConfirmOpen = false;
        syncMismatchGuardMessage = null;
        closeSyncMismatchGroupResolve();
    }

    private boolean hasSyncMismatchGroupResolveChanges()
    {
        return !selectedSyncMismatchGroupResolveTaskIds.equals(originalSyncMismatchGroupResolveTaskIds);
    }

    private void toggleSyncMismatchGroupResolveTask(XtremeTask task)
    {
        if (task == null || task.getId() == null || !canToggleSyncMismatchGroupResolveTask(task))
        {
            return;
        }

        if (!selectedSyncMismatchGroupResolveTaskIds.remove(task.getId()))
        {
            selectedSyncMismatchGroupResolveTaskIds.add(task.getId());
        }
    }

    private boolean canToggleSyncMismatchGroupResolveTask(XtremeTask task)
    {
        if (syncMismatchGroupResolveTask == null || task == null || task.getId() == null)
        {
            return false;
        }
        if (!syncMismatchGroupResolveTierBlockMessage(syncMismatchGroupResolveTask).isEmpty())
        {
            return false;
        }

        List<XtremeTask> resolveTasks = syncMismatchGroupResolveTasks(syncMismatchGroupResolveTask);
        if (!isTaskResolveSequence(plugin.getTaskGroupInstances(syncMismatchGroupResolveTask)))
        {
            return true;
        }

        int index = taskResolveTaskIndex(resolveTasks, task);
        if (index < 0)
        {
            return false;
        }

        int firstSelected = resolveTasks.size();
        for (int i = 0; i < resolveTasks.size(); i++)
        {
            XtremeTask candidate = resolveTasks.get(i);
            if (candidate != null
                    && candidate.getId() != null
                    && selectedSyncMismatchGroupResolveTaskIds.contains(candidate.getId()))
            {
                firstSelected = i;
                break;
            }
        }

        boolean selected = selectedSyncMismatchGroupResolveTaskIds.contains(task.getId());
        if (syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES)
        {
            int lastSelected = -1;
            for (int i = 0; i < resolveTasks.size(); i++)
            {
                XtremeTask candidate = resolveTasks.get(i);
                if (candidate != null
                        && candidate.getId() != null
                        && selectedSyncMismatchGroupResolveTaskIds.contains(candidate.getId()))
                {
                    lastSelected = i;
                }
            }

            if (selected)
            {
                return index == lastSelected;
            }
            return index == lastSelected + 1;
        }

        if (selected)
        {
            return index == firstSelected;
        }
        return index == firstSelected - 1;
    }

    private String syncMismatchGroupResolveTierBlockMessage(XtremeTask task)
    {
        if (task == null || task.getTier() == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return "";
        }

        String familyKey = displaySequenceFamilyKey(task);
        if (familyKey.isEmpty())
        {
            return "";
        }

        int currentTierRank = task.getTier().ordinal();
        List<XtremeTask> reviewTasks = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? plugin.getSyncCompletionCandidateTasks(syncMismatchReviewSource)
                : plugin.getSyncMismatchTasks(syncMismatchReviewSource);
        if (reviewTasks == null || reviewTasks.isEmpty())
        {
            return "";
        }

        boolean blocked = false;
        for (XtremeTask reviewTask : reviewTasks)
        {
            if (reviewTask == null
                    || reviewTask.getTier() == null
                    || reviewTask.getId() == null
                    || Objects.equals(reviewTask.getId(), task.getId())
                    || !familyKey.equals(displaySequenceFamilyKey(reviewTask)))
            {
                continue;
            }

            int reviewTierRank = reviewTask.getTier().ordinal();
            if (syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES)
            {
                if (reviewTierRank < currentTierRank)
                {
                    blocked = true;
                    break;
                }
            }
            else if (reviewTierRank > currentTierRank)
            {
                blocked = true;
                break;
            }
        }

        if (!blocked)
        {
            return "";
        }

        String action = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES ? "complete" : "incomplete";
        String direction = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES ? "lower" : "higher";
        return "This task must be addressed sequentially, mark tasks " + action + " in " + direction + " tiers first.";
    }

    private boolean canToggleTaskResolveTaskIncomplete(XtremeTask task)
    {
        if (taskResolveTask == null || task == null || task.getId() == null)
        {
            return false;
        }

        List<XtremeTask> resolveTasks = resolveTasksForTask(taskResolveTask);
        if (!isTaskResolveSequence(resolveTasks))
        {
            return true;
        }

        int index = taskResolveTaskIndex(resolveTasks, task);
        if (index < 0)
        {
            return false;
        }

        int firstIncomplete = resolveTasks.size();
        for (int i = 0; i < resolveTasks.size(); i++)
        {
            XtremeTask candidate = resolveTasks.get(i);
            if (candidate != null
                    && candidate.getId() != null
                    && selectedTaskResolveIncompleteTaskIds.contains(candidate.getId()))
            {
                firstIncomplete = i;
                break;
            }
        }

        boolean checked = !selectedTaskResolveIncompleteTaskIds.contains(task.getId());
        if (checked)
        {
            return index == firstIncomplete - 1;
        }
        return index == firstIncomplete;
    }

    private boolean isTaskResolveSequence(List<XtremeTask> resolveTasks)
    {
        if (resolveTasks == null || resolveTasks.size() <= 1)
        {
            return false;
        }

        for (XtremeTask resolveTask : resolveTasks)
        {
            if (resolveTask != null && !collectionLogSequenceSuffix(resolveTask).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private int taskResolveTaskIndex(List<XtremeTask> resolveTasks, XtremeTask task)
    {
        if (resolveTasks == null || task == null)
        {
            return -1;
        }

        for (int i = 0; i < resolveTasks.size(); i++)
        {
            if (sameTask(resolveTasks.get(i), task))
            {
                return i;
            }
        }
        return -1;
    }

    private List<XtremeTask> resolveTasksForTask(XtremeTask task)
    {
        if (task == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> out = new ArrayList<>();
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group != null)
        {
            for (XtremeTask instance : group)
            {
                if (instance != null && plugin.isTaskCompleted(instance))
                {
                    out.add(instance);
                }
            }
        }

        if (out.isEmpty() && plugin.isTaskCompleted(task))
        {
            out.add(task);
        }
        return out;
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
        closeSyncMismatchGroupResolve();
        syncMismatchReviewOpen = false;
        syncReviewMode = SyncReviewMode.MISMATCH;
        syncMismatchReviewSource = null;
        syncMismatchApplyConfirmOpen = false;
        syncMismatchDescriptionTask = null;
        selectedSyncMismatchTaskIds.clear();
        syncReviewVisibleTasksCache.clear();
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

        int maxReviewW = 470;
        int w = Math.min(panelBounds.width - 36, maxReviewW);
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

        boolean reviewingCompletionCandidates = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES;
        boolean hasCollectionLogReview = mismatches.stream()
                .anyMatch(task -> isCollectionLogSyncSource(task.getSource()));
        boolean hasCombatAchievementReview = mismatches.stream()
                .anyMatch(task -> task.getSource() == TaskSource.COMBAT_ACHIEVEMENT);
        g.setColor(P.UI_GOLD);
        String reviewMessage;
        String reviewHelper = "";
        String reviewInstruction = "";
        if (reviewingCompletionCandidates)
        {
            if (hasCollectionLogReview && hasCombatAchievementReview)
            {
                reviewMessage = "Tasks found completed in game via sync, but not marked completed in plugin.";
            }
            else if (hasCollectionLogReview)
            {
                reviewMessage = "Collection Log + Achievement Diary tasks found completed in game via sync, but not marked completed in plugin.";
            }
            else
            {
                reviewMessage = "Combat Achievement tasks found completed in game via sync, but not marked completed in plugin.";
            }
            reviewInstruction = "Choose which tasks to mark complete in plugin, then select Apply to save.";
        }
        else if (hasCollectionLogReview && hasCombatAchievementReview)
        {
            reviewMessage = "Tasks marked completed in plugin, but not found completed in game via sync.";
            reviewInstruction = "Choose which tasks to mark incomplete in plugin, then select Apply to save.";
        }
        else if (hasCollectionLogReview)
        {
            reviewMessage = "Collection Log + Achievement Diary tasks marked completed in plugin, but not found completed in game via sync.";
            reviewInstruction = "Choose which tasks to mark incomplete in plugin, then select Apply to save.";
        }
        else
        {
            reviewMessage = "Combat Achievement tasks marked completed in plugin, but not found completed in game via sync.";
            reviewInstruction = "Choose which tasks to mark incomplete in plugin, then select Apply to save.";
        }
        int reviewMessageMaxW = w - pad * 3 - closeW;
        List<String> reviewMessageLines = TextUtils.wrapText(reviewMessage, fm, reviewMessageMaxW);
        int titleY = y + pad + fm.getAscent();
        for (String line : reviewMessageLines)
        {
            g.drawString(line, x + pad, titleY);
            titleY += fm.getHeight();
        }

        int nextY = titleY - fm.getAscent() + fm.getHeight();
        if (!reviewHelper.isEmpty())
        {
            g.setColor(P.UI_TEXT_DIM);
            for (String line : TextUtils.wrapText(reviewHelper, fm, w - pad * 2))
            {
                g.drawString(line, x + pad, nextY + fm.getAscent());
                nextY += fm.getHeight();
            }
        }
        boolean showCollectionLogRefreshHint = hasCollectionLogReview && !reviewingCompletionCandidates;
        if (!reviewInstruction.isEmpty())
        {
            g.setColor(P.UI_TEXT_DIM);
            for (String line : TextUtils.wrapText(reviewInstruction, fm, w - pad * 2))
            {
                g.drawString(line, x + pad, nextY + fm.getAscent());
                nextY += fm.getHeight();
            }
        }
        if (showCollectionLogRefreshHint)
        {
            g.setColor(P.UI_TEXT);
            String refreshHint = "If new CLOG items are missing, open your Collection Log in game, then sync again.";
            g.drawString(TextUtils.truncateToWidth(refreshHint, fm, w - pad * 2),
                    x + pad, nextY + fm.getAscent());
            nextY += fm.getHeight();
        }

        int headerTop = nextY + ((showCollectionLogRefreshHint || !reviewHelper.isEmpty() || !reviewInstruction.isEmpty()) ? fm.getHeight() + 14 : 14);
        int footerH = buttonH + fm.getHeight() + 10;
        int listBottom = y + h - pad - footerH;
        Rectangle listFrame = new Rectangle(x + pad, 0, w - pad * 2, 0);
        int actionColumnW = 112;
        int actionColumnX = listFrame.x + listFrame.width - actionColumnW;
        g.setColor(P.UI_TEXT_DIM);
        g.drawString("Task", listFrame.x, headerTop + fm.getAscent());

        String actionHeader = reviewingCompletionCandidates ? "Mark complete?" : "Mark incomplete?";
        int headerCheckboxSize = 16;
        int actionHeaderW = fm.stringWidth(actionHeader);
        int actionHeaderContentW = actionHeaderW + 5 + headerCheckboxSize;
        int actionHeaderX = actionColumnX + Math.max(0, (actionColumnW - actionHeaderContentW) / 2);
        g.drawString(actionHeader, actionHeaderX, headerTop + fm.getAscent());

        int selectedVisibleCount = selectedVisibleSyncMismatchCount(mismatches);
        int selectableVisibleCount = selectableVisibleSyncMismatchCount(mismatches);
        boolean allMismatchTasksSelected = selectableVisibleCount > 0 && selectedVisibleCount >= selectableVisibleCount;
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

            String label = TextUtils.truncateToWidth(syncReviewRowLabel(task), fm, actionColumnX - row.x - 16);
            Rectangle taskNameArea = new Rectangle(row.x, row.y, Math.max(0, actionColumnX - row.x), row.height);
            boolean hoverTaskName = taskNameArea.contains(mouseX, mouseY);
            boolean clickableTaskName = hasSyncReviewPopup(task);
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
            if (syncMismatchUsesGroupResolver(task))
            {
                drawSyncMismatchGroupAction(g, fm, hitTarget, task);
            }
            else
            {
                drawSyncMismatchCheckbox(g, btn, selectedSyncMismatchTaskIds.contains(task.getId()));
            }
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
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRect(sbX, syncMismatchViewportBounds.y, scrollBarW, syncMismatchViewportBounds.height);

            float thumbRatio = (float) visible / mismatches.size();
            int thumbH = Math.min(syncMismatchViewportBounds.height,
                    Math.max(12, Math.round(syncMismatchViewportBounds.height * thumbRatio)));
            float scrollRatio = maxOffset > 0 ? (float) syncMismatchScroll.offsetRows / maxOffset : 0f;
            int thumbY = syncMismatchViewportBounds.y + (int) ((syncMismatchViewportBounds.height - thumbH) * scrollRatio);
            Rectangle thumb = new Rectangle(sbX, thumbY, Math.max(0, scrollBarW - 1), Math.max(0, thumbH - 1));
            syncMismatchScrollbarThumbBounds.setBounds(thumb);
            drawBevelBox(g, thumb, new Color(78, 62, 38, 200));
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 140));
            g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
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

        if (syncMismatchGroupResolveTask != null)
        {
            renderSyncMismatchGroupResolve(g, fm);
        }

        if (syncMismatchApplyConfirmOpen)
        {
            renderSyncMismatchApplyConfirm(g, fm);
        }

        if (syncMismatchGuardMessage != null)
        {
            renderSyncMismatchGuard(g, fm);
        }
    }

    private List<XtremeTask> visibleSyncMismatchTasks()
    {
        String cacheKey = syncReviewVisibleTasksCacheKey();
        List<XtremeTask> cached = syncReviewVisibleTasksCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        List<XtremeTask> tasks = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? plugin.getSyncCompletionCandidateTasks(syncMismatchReviewSource)
                : plugin.getSyncMismatchTasks(syncMismatchReviewSource);
        tasks = collapseSyncMismatchGroupRows(tasks);
        List<XtremeTask> snapshot = tasks == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(tasks));
        syncReviewVisibleTasksCache.put(cacheKey, snapshot);
        return snapshot;
    }

    private List<XtremeTask> collapseSyncMismatchGroupRows(List<XtremeTask> tasks)
    {
        if (tasks == null || tasks.isEmpty())
        {
            return Collections.emptyList();
        }

        List<XtremeTask> out = new ArrayList<>();
        Set<String> seenGroups = new HashSet<>();
        for (XtremeTask task : tasks)
        {
            if (task == null)
            {
                continue;
            }

            String groupKey = syncMismatchGroupKey(task);
            if (groupKey != null)
            {
                if (seenGroups.add(groupKey))
                {
                    out.add(task);
                }
                continue;
            }

            out.add(task);
        }
        return out;
    }

    private String syncMismatchGroupKey(XtremeTask task)
    {
        if (!syncMismatchUsesGroupResolver(task))
        {
            return null;
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return task.getId();
        }

        return group.stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(task.getId());
    }

    private String syncReviewVisibleTasksCacheKey()
    {
        return "mode=" + syncReviewMode
                + "|source=" + syncMismatchReviewSource
                + "|taskState=" + plugin.getTaskListRenderStateHash()
                + "|clState=" + plugin.getCollectionLogStateVersion()
                + "|lastSync=" + plugin.getLastSyncResultAtLocalTime()
                + "|lastCa=" + plugin.getLastCombatAchievementSyncResultAtLocalTime()
                + "|lastCl=" + plugin.getLastCollectionLogSyncResultAtLocalTime();
    }

    private int selectedVisibleSyncMismatchCount(List<XtremeTask> mismatches)
    {
        int count = 0;
        for (XtremeTask task : mismatches)
        {
            if (task == null || task.getId() == null)
            {
                continue;
            }

            if (syncMismatchUsesGroupResolver(task))
            {
                count += selectedSyncMismatchGroupCount(task);
            }
            else if (selectedSyncMismatchTaskIds.contains(task.getId()))
            {
                count++;
            }
        }
        return count;
    }

    private int selectableVisibleSyncMismatchCount(List<XtremeTask> mismatches)
    {
        if (mismatches == null || mismatches.isEmpty())
        {
            return 0;
        }

        int count = 0;
        for (XtremeTask task : mismatches)
        {
            if (task == null || task.getId() == null)
            {
                continue;
            }

            if (syncMismatchUsesGroupResolver(task))
            {
                count += syncMismatchGroupSelectableTasks(task).size();
            }
            else
            {
                count++;
            }
        }
        return count;
    }

    private boolean syncMismatchUsesGroupResolver(XtremeTask task)
    {
        if (task == null
                || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return false;
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        return group != null
                && group.size() > 1
                && !syncMismatchGroupResolveTasks(task).isEmpty();
    }

    private List<XtremeTask> syncMismatchGroupResolveTasks(XtremeTask task)
    {
        return syncMismatchGroupActionTasks(task);
    }

    private List<XtremeTask> syncMismatchGroupActionTasks(XtremeTask task)
    {
        if (task == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.size() <= 1)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> reviewTasks = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? plugin.getSyncCompletionCandidateTasks(syncMismatchReviewSource)
                : plugin.getSyncMismatchTasks(syncMismatchReviewSource);
        Set<String> reviewTaskIds = reviewTasks.stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (reviewTaskIds.isEmpty())
        {
            return Collections.emptyList();
        }

        List<XtremeTask> out = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && groupedTask.getId() != null && reviewTaskIds.contains(groupedTask.getId()))
            {
                out.add(groupedTask);
            }
        }
        return out;
    }

    private List<XtremeTask> syncMismatchGroupSelectableTasks(XtremeTask task)
    {
        if (!syncMismatchUsesGroupResolver(task))
        {
            return Collections.emptyList();
        }

        return syncMismatchGroupActionTasks(task);
    }

    private int selectedSyncMismatchGroupCount(XtremeTask task)
    {
        int count = 0;
        for (XtremeTask groupedTask : syncMismatchGroupSelectableTasks(task))
        {
            if (groupedTask != null
                    && groupedTask.getId() != null
                    && selectedSyncMismatchTaskIds.contains(groupedTask.getId()))
            {
                count++;
            }
        }
        return count;
    }

    private List<XtremeTask> syncMismatchGroupFoundTasks(XtremeTask task)
    {
        if (task == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> notFoundIds = syncMismatchGroupResolveTasks(task).stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<XtremeTask> found = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null
                    && groupedTask.getId() != null
                    && plugin.isTaskCompleted(groupedTask)
                    && !notFoundIds.contains(groupedTask.getId()))
            {
                found.add(groupedTask);
            }
        }
        return found;
    }

    private void drawSyncMismatchGroupAction(Graphics2D g, FontMetrics fm, Rectangle bounds, XtremeTask task)
    {
        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        int total = Math.max(1, syncMismatchGroupActionTasks(task).size());
        int selectedCount = selectedSyncMismatchGroupCount(task);
        String label = selectedCount + "/" + total;

        net.runelite.api.Point mouse = mouseCanvasPositionForPanelRender();
        boolean hover = mouse != null && bounds.contains(mouse.getX(), mouse.getY());
        g.setColor(hover ? P.UI_GOLD : P.UI_TEXT);
        int textX = bounds.x + (bounds.width - fm.stringWidth(label)) / 2;
        int textY = bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(label, textX, textY);
        g.drawLine(textX, textY + 2, textX + fm.stringWidth(label), textY + 2);
    }

    private boolean hasSyncReviewPopup(XtremeTask task)
    {
        if (task == null)
        {
            return false;
        }
        return task.getSource() == TaskSource.COMBAT_ACHIEVEMENT
                || task.getSource() == TaskSource.DIARY_ACHIEVEMENT
                || hasCollectionLogReviewItems(task);
    }

    private String syncReviewPopupTitle(XtremeTask task)
    {
        String title = syncReviewRowLabel(task);
        if (title == null || title.trim().isEmpty())
        {
            title = task == null ? "" : task.getName();
        }
        return title == null || title.trim().isEmpty() ? "Task" : title.trim();
    }

    private String syncReviewPopupHelperText(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        if (syncMismatchUsesGroupResolver(task))
        {
            return syncMismatchGroupResolveSummary(task);
        }

        return syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? "Sync found this completed in game, not marked in plugin"
                : "Sync did not find this completed in game, marked in plugin";
    }

    private boolean shouldShowSyncReviewPopupHelper(XtremeTask task)
    {
        return false;
    }

    private boolean hasCollectionLogReviewItems(XtremeTask task)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return false;
        }

        TaskVerification verification = task.getVerification();
        if (verification == null || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return false;
        }

        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreview(task);
        return preview != null && preview.hasItems();
    }

    private void renderSyncMismatchDescription(Graphics2D g, FontMetrics fm)
    {
        XtremeTask task = syncMismatchDescriptionTask;
        if (task == null)
        {
            return;
        }

        if (hasCollectionLogReviewItems(task))
        {
            renderSyncMismatchCollectionLogDescription(g, fm, task);
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
        String tierPillText = (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT
                || task.getSource() == TaskSource.DIARY_ACHIEVEMENT) && task.getTier() != null
                ? tierLabel(task.getTier())
                : null;
        FontMetrics smallFm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        int tierPillW = tierPillText == null ? 0 : Math.max(24, smallFm.stringWidth(tierPillText) + 14);
        int iconColumnW = Math.max(iconSize, tierPillW);
        int iconStackH = iconSize + (tierPillText == null ? 0 : tierPillGap + tierPillH);
        int textXOffset = pad + iconColumnW + pad;
        int textW = w - textXOffset - closeW - pad;
        List<String> titleLines = TextUtils.wrapText(syncReviewPopupTitle(task), fm, textW);
        List<String> helperLines = shouldShowSyncReviewPopupHelper(task)
                ? TextUtils.wrapText(syncReviewPopupHelperText(task), fm, textW)
                : List.of();
        List<String> lines = TextUtils.wrapText(description.trim(), fm, textW);
        boolean achievementDiaryTask = task.getSource() == TaskSource.DIARY_ACHIEVEMENT;
        int visibleLines = achievementDiaryTask ? Math.max(1, lines.size()) : Math.max(1, Math.min(lines.size(), 5));
        int titleLineCount = Math.max(1, Math.min(titleLines.size(), 2));
        int helperLineCount = Math.min(helperLines.size(), 2);
        int titleGap = Math.max(9, fm.getHeight() / 2);
        int helperGap = helperLineCount > 0 ? 5 : 0;
        int textBlockH = titleLineCount * fm.getHeight()
                + helperLineCount * fm.getHeight()
                + titleGap
                + helperGap
                + visibleLines * fm.getHeight();
        int contentH = Math.max(iconStackH, textBlockH);
        int requestedH = Math.max(96, pad * 2 + contentH);
        int maxH = achievementDiaryTask ? Math.max(96, syncMismatchReviewBounds.height - 36) : 184;
        int h = Math.min(requestedH, maxH);
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
        int textY = textBounds.y + fm.getAscent();
        g.setColor(P.UI_GOLD);
        for (int i = 0; i < titleLineCount; i++)
        {
            String line = titleLines.isEmpty() ? task.getName() : titleLines.get(i);
            g.drawString(TextUtils.truncateToWidth(line, fm, textBounds.width), textBounds.x, textY);
            textY += fm.getHeight();
        }
        g.setColor(P.UI_TEXT_DIM);
        for (int i = 0; i < helperLineCount; i++)
        {
            g.drawString(TextUtils.truncateToWidth(helperLines.get(i), fm, textBounds.width), textBounds.x, textY);
            textY += fm.getHeight();
        }
        textY += helperGap;
        textY += titleGap;
        g.setColor(P.UI_TEXT);
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

    private void renderSyncMismatchCollectionLogDescription(Graphics2D g, FontMetrics fm, XtremeTask task)
    {
        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreview(task);
        List<CollectionLogRequirementItem> items = syncReviewCollectionLogItems(preview);
        boolean showTierSections = preview != null && preview.showTierSections();
        if (showTierSections)
        {
            CollectionLogRequirementPreview.TierSection currentSection = preview.currentTierSection();
            items = currentSection == null ? List.of() : currentSection.items();
        }
        boolean simpleSingleItemTask = !showTierSections
                && items.size() == 1
                && !isMultiInstanceTask(task)
                && (preview == null || !preview.showSecondaryItemList());
        if (items.isEmpty())
        {
            return;
        }

        int pad = 12;
        int iconSize = 32;
        int itemIconSize = 18;
        int tierPillGap = 4;
        int tierPillH = 18;
        int closeW = 28;
        int rowGap = 3;
        int rowH = Math.max(fm.getHeight(), itemIconSize) + rowGap;
        int w = Math.min(syncMismatchReviewBounds.width - 48, 360);
        String tierPillText = task.getTier() == null ? null : tierLabel(task.getTier());
        FontMetrics smallFm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        int tierPillW = tierPillText == null ? 0 : Math.max(24, smallFm.stringWidth(tierPillText) + 14);
        int iconColumnW = Math.max(iconSize, tierPillW);
        int iconStackH = iconSize + (tierPillText == null ? 0 : tierPillGap + tierPillH);
        int listXOffset = pad + iconColumnW + pad;
        int listW = w - listXOffset - closeW - pad;
        int columnGap = 12;
        int columns = items.size() > 1 && listW >= 210 ? 2 : 1;
        int columnW = columns == 1 ? listW : Math.max(90, (listW - columnGap) / 2);
        int rows = (items.size() + columns - 1) / columns;
        int listH = simpleSingleItemTask
                ? 0
                : rows * rowH;
        List<String> titleLines = TextUtils.wrapText(syncReviewPopupTitle(task), fm, listW);
        List<String> helperLines = !simpleSingleItemTask && shouldShowSyncReviewPopupHelper(task)
                ? TextUtils.wrapText(syncReviewPopupHelperText(task), fm, listW)
                : List.of();
        List<String> summaryLines = !simpleSingleItemTask && preview.showSummaryText() && !showTierSections
                ? TextUtils.wrapText(preview.summaryText(), fm, listW)
                : List.of();
        int titleLineCount = Math.max(1, Math.min(titleLines.size(), 2));
        int helperLineCount = Math.min(helperLines.size(), 2);
        int summaryLineCount = Math.min(summaryLines.size(), 2);
        String listHeader = simpleSingleItemTask ? "" : collectionLogRequirementTitle(preview);
        boolean hasListContent = !listHeader.isEmpty() || summaryLineCount > 0 || listH > 0;
        int titleGap = helperLineCount > 0 ? 3 : hasListContent ? Math.max(9, fm.getHeight() / 2) : 0;
        int sectionGap = hasListContent ? Math.max(9, fm.getHeight() / 2) : 0;
        int summaryGap = showTierSections && summaryLineCount > 0 ? TIER_SECTION_ICON_GAP : 0;
        int headerGap = 0;
        int listBlockH = titleLineCount * fm.getHeight()
                + titleGap
                + helperLineCount * fm.getHeight()
                + sectionGap
                + (listHeader.isEmpty() ? 0 : fm.getHeight())
                + summaryLineCount * fm.getHeight()
                + summaryGap
                + headerGap
                + listH;
        int contentH = Math.max(iconStackH, listBlockH);
        int h = Math.max(106, Math.min(syncMismatchReviewBounds.height - 34, pad * 2 + contentH));
        int x = syncMismatchReviewBounds.x + (syncMismatchReviewBounds.width - w) / 2;
        int y = syncMismatchReviewBounds.y + (syncMismatchReviewBounds.height - h) / 2;
        syncMismatchDescriptionBounds.setBounds(x, y, w, h);

        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(syncMismatchReviewBounds.x, syncMismatchReviewBounds.y,
                syncMismatchReviewBounds.width, syncMismatchReviewBounds.height);
        drawBevelBox(g, syncMismatchDescriptionBounds, new Color(45, 36, 24, 252));

        syncMismatchDescriptionCloseBounds.setBounds(x + w - pad - closeW, y + pad - 4, closeW, ROW_HEIGHT + 8);
        drawPopupCloseX(g, syncMismatchDescriptionCloseBounds);

        int availableContentH = Math.max(0, h - pad * 2);
        int contentY = y + pad + Math.max(0, (availableContentH - Math.min(contentH, availableContentH)) / 2);
        int iconStackY = contentY + Math.max(0, (Math.min(contentH, availableContentH) - iconStackH) / 2);
        int iconX = x + pad + Math.max(0, (iconColumnW - iconSize) / 2);
        int iconY = iconStackY;
        BufferedImage icon = resolveTaskIcon(task);
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
        Rectangle listBounds = new Rectangle(x + listXOffset, y + pad, listW, availableContentH);
        g.setClip(listBounds);
        int listBlockTop = contentY + Math.max(0, (Math.min(contentH, availableContentH) - Math.min(listBlockH, availableContentH)) / 2);
        int textY = listBlockTop + fm.getAscent();
        g.setColor(P.UI_GOLD);
        for (int i = 0; i < titleLineCount; i++)
        {
            String line = titleLines.isEmpty() ? task.getName() : titleLines.get(i);
            g.drawString(TextUtils.truncateToWidth(line, fm, listBounds.width), listBounds.x, textY);
            textY += fm.getHeight();
        }
        textY += titleGap;
        g.setColor(P.UI_TEXT_DIM);
        for (int i = 0; i < helperLineCount; i++)
        {
            g.drawString(TextUtils.truncateToWidth(helperLines.get(i), fm, listBounds.width), listBounds.x, textY);
            textY += fm.getHeight();
        }
        textY += sectionGap;
        if (!listHeader.isEmpty())
        {
            g.setColor(P.UI_TEXT);
            g.drawString(TextUtils.truncateToWidth(listHeader, fm, listBounds.width), listBounds.x, textY);
            textY += fm.getHeight();
        }
        g.setColor(P.UI_TEXT_DIM);
        for (int i = 0; i < summaryLineCount; i++)
        {
            g.drawString(TextUtils.truncateToWidth(summaryLines.get(i), fm, listBounds.width), listBounds.x, textY);
            textY += fm.getHeight();
        }
        textY += summaryGap;
        int listTop = textY + headerGap;
        if (simpleSingleItemTask)
        {
            // The single required CLOG item is already represented by the icon column.
        }
        else
        {
            for (int i = 0; i < items.size(); i++)
            {
                CollectionLogRequirementItem item = items.get(i);
                if (item == null)
                {
                    continue;
                }

                int col = i / rows;
                int row = i % rows;
                int itemX = listBounds.x + col * (columnW + columnGap);
                int itemY = listTop + row * rowH;
                if (itemY > listBounds.y + listBounds.height)
                {
                    break;
                }
                drawSyncReviewCollectionLogItem(g, fm, item, itemX, itemY, columnW, itemIconSize);
            }
        }
        g.setClip(oldClip);
    }

    private int syncReviewTierSectionsHeight(CollectionLogRequirementPreview preview, int rowH, int listW, int columnGap)
    {
        int total = 0;
        List<CollectionLogRequirementPreview.TierSection> sections = preview == null ? List.of() : preview.tierSections();
        for (int i = 0; i < sections.size(); i++)
        {
            CollectionLogRequirementPreview.TierSection section = sections.get(i);
            if (section == null || section.items().isEmpty())
            {
                continue;
            }
            if (i > 0)
            {
                total += 8;
            }
            int columns = section.items().size() > 1 && listW >= 210 ? 2 : 1;
            int rows = (section.items().size() + columns - 1) / columns;
            total += rows * rowH;
        }
        return total;
    }

    private void drawSyncReviewTierSections(
            Graphics2D g,
            FontMetrics fm,
            CollectionLogRequirementPreview preview,
            int x,
            int y,
            int listW,
            int rowH,
            int columnGap,
            int itemIconSize,
            Rectangle clipBounds)
    {
        List<CollectionLogRequirementPreview.TierSection> sections = preview == null ? List.of() : preview.tierSections();
        for (int i = 0; i < sections.size(); i++)
        {
            CollectionLogRequirementPreview.TierSection section = sections.get(i);
            if (section == null || section.items().isEmpty())
            {
                continue;
            }
            if (i > 0)
            {
                y += 8;
            }
            int columns = section.items().size() > 1 && listW >= 210 ? 2 : 1;
            int columnW = columns == 1 ? listW : Math.max(90, (listW - columnGap) / 2);
            int rows = (section.items().size() + columns - 1) / columns;
            for (int itemIndex = 0; itemIndex < section.items().size(); itemIndex++)
            {
                int col = itemIndex / rows;
                int row = itemIndex % rows;
                int itemX = x + col * (columnW + columnGap);
                int itemY = y + row * rowH;
                if (itemY > clipBounds.y + clipBounds.height)
                {
                    return;
                }
                drawSyncReviewCollectionLogItem(g, fm, section.items().get(itemIndex), itemX, itemY, columnW, itemIconSize);
            }
            y += rows * rowH;
        }
    }

    private void drawSyncReviewTierLabel(Graphics2D g, FontMetrics fm, CollectionLogRequirementPreview.TierSection section, int x, int baseline)
    {
        String text = section.tier() == null ? "TIER" : tierLabel(section.tier()).toUpperCase();
        g.setColor(P.UI_GOLD);
        g.drawString(TextUtils.truncateToWidth(text, fm, Math.max(0, 180)), x, baseline);
    }

    private static String collectionLogRequirementTitle(CollectionLogRequirementPreview preview)
    {
        if (preview != null && preview.titleText() != null && !preview.titleText().trim().isEmpty())
        {
            return preview.titleText();
        }
        return preview != null && preview.showSummaryText() && !preview.showItemList()
                ? "Collection Log Progress"
                : "Eligible Collection Log items";
    }

    private List<CollectionLogRequirementItem> syncReviewCollectionLogItems(CollectionLogRequirementPreview preview)
    {
        if (preview == null)
        {
            return List.of();
        }

        List<CollectionLogRequirementItem> out = new ArrayList<>();
        if (preview.showItemList())
        {
            out.addAll(preview.getItems());
        }
        if (preview.showSecondaryItemList())
        {
            out.addAll(preview.secondaryItems());
        }
        return out;
    }

    private void drawSyncReviewCollectionLogItem(
            Graphics2D g,
            FontMetrics fm,
            CollectionLogRequirementItem item,
            int x,
            int y,
            int maxW,
            int iconSize)
    {
        Rectangle iconBounds = new Rectangle(x, y + Math.max(0, (fm.getHeight() - iconSize) / 2), iconSize, iconSize);
        BufferedImage image = item.getItemId() > 0 ? getCachedItemImage(item.getItemId()) : null;
        if (image != null)
        {
            g.drawImage(image, iconBounds.x, iconBounds.y, iconBounds.width, iconBounds.height, null);
        }
        else
        {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("?", iconBounds.x + 5, iconBounds.y + iconBounds.height - 4);
        }

        String name = item.getName() == null ? "" : item.getName();
        int textX = iconBounds.x + iconSize + 5;
        int textW = Math.max(0, maxW - iconSize - 5);
        String text = TextUtils.truncateToWidth(name, fm, textW);
        int textY = y + fm.getAscent();
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(text, textX, textY);
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

    private String syncReviewRowLabel(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (syncMismatchUsesGroupResolver(task) && group != null && !group.isEmpty())
        {
            XtremeTask first = group.stream().filter(Objects::nonNull).findFirst().orElse(task);
            return first.getName();
        }

        if (isDecoratedSequenceTaskName(task.getName()))
        {
            return task.getName();
        }
        if (group == null || group.size() <= 1)
        {
            return task.getName();
        }

        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress == null || !progress.isGrouped())
        {
            return task.getName();
        }

        String sequenceSuffix = collectionLogSequenceSuffix(task);
        if (!sequenceSuffix.isEmpty())
        {
            return task.getName() + " (" + sequenceSuffix + ")";
        }

        int instanceOrdinal = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? instanceOrdinalInGroup(group, task)
                : completedInstanceOrdinalInGroup(group, task);
        if (instanceOrdinal <= 0)
        {
            return task.getName() + " " + progress.label();
        }
        return task.getName() + " (" + instanceOrdinal + "/" + group.size() + ")";
    }

    private static boolean isDecoratedSequenceTaskName(String name)
    {
        if (name == null)
        {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return isDisplaySequenceTaskName(normalized)
                && normalized.matches(".*\\([^)]*\\)\\s*$");
    }

    private static boolean isDisplaySequenceTaskName(String name)
    {
        if (name == null)
        {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("next tier")
                || normalized.contains("first tier")
                || normalized.contains("next reward")
                || normalized.contains("mta wand")
                || normalized.equals("upgrade to apprentice wand")
                || normalized.equals("upgrade to teacher wand")
                || normalized.equals("upgrade to master wand");
    }

    private int instanceOrdinalInGroup(List<XtremeTask> group, XtremeTask task)
    {
        if (group == null || task == null || task.getId() == null)
        {
            return -1;
        }

        for (int i = 0; i < group.size(); i++)
        {
            XtremeTask groupedTask = group.get(i);
            if (groupedTask != null && Objects.equals(groupedTask.getId(), task.getId()))
            {
                return i + 1;
            }
        }
        return -1;
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
        boolean reviewingCompletionCandidates = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES;
        String message = reviewingCompletionCandidates
                ? "Mark " + count + " task(s) complete?"
                : "Mark " + count + " task(s) incomplete?";
        String warning = reviewingCompletionCandidates
                ? "Completion source will be recorded as sync."
                : "Completion dates and time spent will be cleared.";
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

    private void renderSyncMismatchGuard(Graphics2D g, FontMetrics fm)
    {
        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(syncMismatchReviewBounds.x, syncMismatchReviewBounds.y,
                syncMismatchReviewBounds.width, syncMismatchReviewBounds.height);

        String title = "Sequence blocked";
        int maxW = Math.max(120, syncMismatchReviewBounds.width - 70);
        int textW = Math.max(100, maxW - 36);
        List<String> lines = TextUtils.wrapText(syncMismatchGuardMessage == null ? "" : syncMismatchGuardMessage, fm, textW);
        syncMismatchGuardTotalRows = Math.max(1, lines.size());
        int visibleLineCapacity = Math.min(8, syncMismatchGuardTotalRows);
        int w = Math.min(maxW, Math.max(fm.stringWidth(title) + 36, widestLineWidth(lines, fm) + 36));
        int buttonW = 70;
        int buttonH = ROW_HEIGHT + 8;
        int lineH = syncMismatchGuardRowBlock();
        int h = Math.min(syncMismatchReviewBounds.height - 32, 64 + visibleLineCapacity * lineH + buttonH);
        int x = syncMismatchReviewBounds.x + (syncMismatchReviewBounds.width - w) / 2;
        int y = syncMismatchReviewBounds.y + (syncMismatchReviewBounds.height - h) / 2;
        syncMismatchGuardBounds.setBounds(x, y, w, h);
        drawBevelBox(g, syncMismatchGuardBounds, new Color(45, 36, 24, 252));

        int baseline = y + 16 + fm.getAscent();
        g.setColor(P.UI_GOLD);
        g.drawString(title, x + (w - fm.stringWidth(title)) / 2, baseline);

        g.setColor(P.UI_TEXT_DIM);
        int viewportY = baseline + fm.getHeight() + 8 - fm.getAscent();
        int viewportH = Math.max(lineH, y + h - buttonH - 18 - viewportY);
        boolean needsScrollbar = syncMismatchGuardTotalRows > visibleLineCapacity;
        int scrollbarReserve = needsScrollbar ? 12 : 0;
        syncMismatchGuardViewportBounds.setBounds(x + 12, viewportY, Math.max(20, w - 24 - scrollbarReserve), viewportH);

        int visibleLines = Math.max(1, syncMismatchGuardScroll.visibleRows(syncMismatchGuardViewportBounds.height, lineH));
        int maxOffset = Math.max(0, syncMismatchGuardTotalRows - visibleLines);
        syncMismatchGuardScroll.offsetRows = Math.max(0, Math.min(syncMismatchGuardScroll.offsetRows, maxOffset));
        int start = syncMismatchGuardScroll.offsetRows;
        int end = Math.min(syncMismatchGuardTotalRows, start + visibleLines);

        Shape oldClip = g.getClip();
        g.setClip(syncMismatchGuardViewportBounds);
        int textY = syncMismatchGuardViewportBounds.y + fm.getAscent();
        for (int i = start; i < end; i++)
        {
            String line = lines.isEmpty() ? "" : lines.get(i);
            line = TextUtils.truncateToWidth(line, fm, syncMismatchGuardViewportBounds.width);
            g.setColor(syncMismatchGuardLineColor(line));
            g.drawString(line, syncMismatchGuardViewportBounds.x, textY);
            textY += lineH;
        }
        g.setClip(oldClip);

        renderSyncMismatchGuardScrollbar(g, syncMismatchGuardTotalRows, visibleLines, start);

        syncMismatchGuardOkBounds.setBounds(x + (w - buttonW) / 2, y + h - buttonH - 10, buttonW, buttonH);
        buttonRenderer.drawPlainButton(g, syncMismatchGuardOkBounds, "OK", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
    }

    private int syncMismatchGuardRowBlock()
    {
        return ROW_HEIGHT;
    }

    private Color syncMismatchGuardLineColor(String line)
    {
        if (line != null && line.startsWith("Save blocked:"))
        {
            return new Color(245, 92, 82, 245);
        }
        if (line != null && line.startsWith("Task:"))
        {
            return P.UI_TEXT;
        }
        return P.UI_TEXT_DIM;
    }

    private void renderSyncMismatchGuardScrollbar(Graphics2D g, int totalRows, int visibleRows, int offsetRows)
    {
        syncMismatchGuardScrollbarRailBounds.setBounds(0, 0, 0, 0);
        syncMismatchGuardScrollbarThumbBounds.setBounds(0, 0, 0, 0);
        if (totalRows <= visibleRows || visibleRows <= 0 || syncMismatchGuardViewportBounds.height <= 0)
        {
            return;
        }

        int scrollBarW = 6;
        int sbX = syncMismatchGuardBounds.x + syncMismatchGuardBounds.width - 12 - scrollBarW;
        syncMismatchGuardScrollbarRailBounds.setBounds(sbX, syncMismatchGuardViewportBounds.y,
                scrollBarW, syncMismatchGuardViewportBounds.height);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(sbX, syncMismatchGuardViewportBounds.y, scrollBarW, syncMismatchGuardViewportBounds.height);

        float thumbRatio = (float) visibleRows / totalRows;
        int thumbH = Math.min(syncMismatchGuardViewportBounds.height,
                Math.max(12, Math.round(syncMismatchGuardViewportBounds.height * thumbRatio)));
        int maxOffset = Math.max(1, totalRows - visibleRows);
        float scrollRatio = (float) Math.max(0, Math.min(offsetRows, maxOffset)) / maxOffset;
        int thumbY = syncMismatchGuardViewportBounds.y
                + (int) ((syncMismatchGuardViewportBounds.height - thumbH) * scrollRatio);
        Rectangle thumb = new Rectangle(sbX, thumbY, Math.max(0, scrollBarW - 1), Math.max(0, thumbH - 1));
        syncMismatchGuardScrollbarThumbBounds.setBounds(thumb);
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200));
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    private static int widestLineWidth(List<String> lines, FontMetrics fm)
    {
        int width = 0;
        if (lines != null)
        {
            for (String line : lines)
            {
                width = Math.max(width, fm.stringWidth(line == null ? "" : line));
            }
        }
        return width;
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
                this::getCachedPrerequisiteStatuses,
                this::getCachedSkillImage,
                this::getCachedPrerequisiteMarkerImage,
                this::buildCollectionLogRequirementPreview,
                this::getCachedItemImage,
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
                plugin.canUndoRecentTaskCompletion(),
                plugin.isTaskSkippingEnabled(),
                plugin.getSkippedTaskCount(),
                plugin.isCurrentTaskCompletionCriteriaMet(),
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
        String skipped = "Skipped tasks: " + plugin.getSkippedTaskCount();
        int skippedW = fm.stringWidth(skipped);
        int progressW = Math.max(20, innerW - skippedW - 8);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(progress, fm, progressW), innerX, y + fm.getAscent());
        g.drawString(skipped, innerX + innerW - skippedW, y + fm.getAscent());
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
        boolean skipEnabled = plugin.isTaskSkippingEnabled();
        boolean canUndoRecentCompletion = plugin.canUndoRecentTaskCompletion();
        boolean currentCompletionCriteriaMet = plugin.isCurrentTaskCompletionCriteriaMet();
        int buttonGap = 6;
        int wikiW = current != null && current.getWikiUrl() != null && !current.getWikiUrl().trim().isEmpty()
                ? Math.max(48, fm.stringWidth("Wiki") + 18)
                : 0;
        int actionW = wikiW > 0 ? innerW - wikiW - buttonGap : innerW;
        if (completeEnabled) {
            if (skipEnabled) {
                int skipW = Math.min(64, Math.max(48, fm.stringWidth("Skip") + 22));
                int completeW = Math.max(90, actionW - skipW - buttonGap);
                currentLayout.completeButtonBounds.setBounds(innerX, y, completeW, actionH);
                currentLayout.skipButtonBounds.setBounds(innerX + completeW + buttonGap, y, skipW, actionH);
            } else {
                currentLayout.completeButtonBounds.setBounds(innerX, y, actionW, actionH);
            }
            buttonRenderer.drawPrimaryButton(
                    g,
                    currentLayout.completeButtonBounds,
                    "Mark complete",
                    currentCompletionCriteriaMet ? UiPalette.TIER_COMPLETE_GLOW : null);
            if (skipEnabled) {
                buttonRenderer.drawPlainButton(g, currentLayout.skipButtonBounds, "Skip");
            }
        } else if (rollEnabled) {
            if (canUndoRecentCompletion) {
                int undoW = Math.min(70, Math.max(48, fm.stringWidth("Undo") + 22));
                int rollW = Math.max(90, actionW - undoW - buttonGap);
                currentLayout.rollButtonBounds.setBounds(innerX, y, rollW, actionH);
                currentLayout.undoButtonBounds.setBounds(innerX + rollW + buttonGap, y, undoW, actionH);
                buttonRenderer.drawPrimaryButton(g, currentLayout.rollButtonBounds, "Roll task");
                buttonRenderer.drawPlainButton(g, currentLayout.undoButtonBounds, "Undo");
            } else {
                Rectangle actionBounds = new Rectangle(innerX, y, actionW, actionH);
                currentLayout.rollButtonBounds.setBounds(actionBounds);
                buttonRenderer.drawPrimaryButton(g, currentLayout.rollButtonBounds, "Roll task");
            }
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
        int maxOffsetPx = Math.max(0, totalPx - visiblePx);
        compactCurrentMaxOffsetPx = maxOffsetPx;
        applyCompactScrollInputs(maxOffsetPx);
        int scrollPx = compactCurrentScrollPx;

        g.setClip(textClip);
        int textY = textClip.y + fm.getAscent() + 2 - scrollPx;
        int textX = textClip.x + 4;
        for (CompactLine line : lines) {
            if (line.collectionLogPreview != null) {
                Composite oldComposite = g.getComposite();
                if (line.dimCollectionLogIcons) {
                    g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                }
                textY = CollectionLogIconGridRenderer.render(
                        g,
                        fm,
                        textX,
                        textY,
                        textW,
                        line.collectionLogPreview.getItems(),
                        this::getCachedItemImage,
                        mousePoint,
                        textClip,
                        P.UI_TEXT,
                        P.UI_TEXT_DIM,
                        P.UI_EDGE_LIGHT,
                        P.UI_EDGE_DARK,
                        line.collectionLogPreview.iconColumns());
                g.setComposite(oldComposite);
            } else if (line.tierSection != null) {
                drawCompactTierLabel(g, fm, line.tierSection, textX, textY);
                textY += compactLineHeight(line, textW);
            } else {
                drawCompactLine(g, fm, line, textX, textY, textW);
                textY += compactLineHeight(line, textW);
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
        String cacheKey = compactLinesCacheKey(current, rolling);
        List<CompactLine> cached = compactLinesCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<CompactLine> lines = new ArrayList<>();
        if (rolling) {
            lines.add(new CompactLine("Rolling a new task...", false, true));
            return cacheCompactLines(cacheKey, lines);
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
            return cacheCompactLines(cacheKey, lines);
        }

        String desc = current.getDescription();
        String tip = plugin.showTips() ? current.getTip() : null;
        boolean hasTip = tip != null && !tip.trim().isEmpty();
        if (hasTip) {
            tip = tip.trim();
        }
        if (desc != null && !desc.trim().isEmpty()) {
            lines.add(new CompactLine("Description", true, false));
            lines.addAll(wrappedCompactLines(desc.trim(), false));
            if (hasTip && current.getSource() != TaskSource.COLLECTION_LOG) {
                lines.add(CompactLine.spacer());
                lines.addAll(wrappedCompactLines("Tip: " + tip, true));
            }
            lines.add(CompactLine.spacer());
        }

        CollectionLogRequirementPreview preview = buildCollectionLogRequirementPreview(current);
        if (preview != null && preview.hasItems()) {
            if (hasTip) {
                lines.addAll(wrappedCompactLines("Tip: " + tip, true));
                lines.add(CompactLine.spacer());
            }
            lines.add(new CompactLine(compactCollectionLogRequirementTitle(preview), true, false));
            if (preview.showSummaryText()) {
                lines.addAll(wrappedCompactLines(preview.summaryText(), true));
                if (preview.showTierSections()) {
                    lines.add(CompactLine.verticalGap(TIER_SECTION_ICON_GAP));
                }
            }
            if (preview.showTierSections()) {
                CollectionLogRequirementPreview.TierSection currentSection = preview.currentTierSection();
                if (currentSection != null) {
                    lines.add(CompactLine.collectionLogIcons(singleSectionPreview(
                            currentSection.items(),
                            currentSection.iconColumns())));
                }
                List<CollectionLogRequirementPreview.TierSection> otherSections = preview.otherTierSectionsHardestFirst();
                if (!otherSections.isEmpty()) {
                    lines.add(CompactLine.verticalGap(COMPACT_SECONDARY_SECTION_GAP));
                    lines.addAll(wrappedCompactLines(OTHER_SEQUENCE_CLOGS_DIVIDER, true));
                    lines.addAll(wrappedCompactLines(OTHER_SEQUENCE_CLOGS_LABEL, true));
                }
                for (int i = 0; i < otherSections.size(); i++) {
                    CollectionLogRequirementPreview.TierSection section = otherSections.get(i);
                    if (i > 0) {
                        lines.add(CompactLine.verticalGap(6));
                    }
                    lines.add(CompactLine.verticalGap(TIER_SECTION_LABEL_TOP_GAP));
                    lines.add(CompactLine.tierLabel(section));
                    lines.add(CompactLine.verticalGap(TIER_SECTION_ICON_GAP));
                    lines.add(CompactLine.collectionLogIcons(singleSectionPreview(
                            section.items(),
                            section.iconColumns()), true));
                }
            } else if (preview.showItemList()) {
                lines.add(CompactLine.collectionLogIcons(preview));
            }
            if (preview.showSecondaryItemList()) {
                if (isMedallionAssemblyTitle(preview.secondaryTitleText())) {
                    lines.add(CompactLine.verticalGap(COMPACT_MEDALLION_ASSEMBLY_TITLE_GAP));
                }
                lines.addAll(wrappedCompactLines(preview.secondaryTitleText(), true));
                lines.add(CompactLine.collectionLogIcons(singleSectionPreview(
                        preview.secondaryItems(),
                        preview.secondaryIconColumns())));
            }
            lines.add(CompactLine.spacer());
        }

        lines.add(new CompactLine("Prereqs", true, false));
        List<PrerequisiteStatus> statuses = getCachedPrerequisiteStatuses(current);
        String prereqs = normalizeCompactPrereqs(current.getPrereqs());
        if (statuses != null && !statuses.isEmpty()) {
            for (PrerequisiteStatus status : statuses) {
                lines.addAll(wrappedCompactPrereqLines(status));
            }
        } else if (!prereqs.isEmpty()) {
            String formatted = prereqs.replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
            for (String prereq : formatted.split("\n")) {
                lines.addAll(wrappedCompactLines("- " + prereq, true));
            }
        } else {
            lines.add(new CompactLine("None", false, true));
        }

        return cacheCompactLines(cacheKey, lines);
    }

    private String compactLinesCacheKey(XtremeTask current, boolean rolling) {
        return "task=" + safeTaskId(current)
                + "|rolling=" + rolling
                + "|notice=" + Optional.ofNullable(plugin.getPendingRollSkipNotice()).orElse(plugin.getRollSkipNotice())
                + "|tips=" + plugin.showTips()
                + "|tick=" + client.getTickCount()
                + "|taskState=" + plugin.getTaskListRenderStateHash()
                + "|clState=" + plugin.getCollectionLogStateVersion()
                + "|width=" + (PANEL_W_COMPACT - PANEL_PADDING * 2 - 26);
    }

    private List<CompactLine> cacheCompactLines(String cacheKey, List<CompactLine> lines) {
        List<CompactLine> immutableLines = Collections.unmodifiableList(new ArrayList<>(lines));
        compactLinesCache.put(cacheKey, immutableLines);
        return immutableLines;
    }

    private static String compactCollectionLogRequirementTitle(CollectionLogRequirementPreview preview) {
        if (preview != null && preview.titleText() != null && !preview.titleText().trim().isEmpty()) {
            return preview.titleText();
        }
        return "Eligible Collection Log items";
    }

    private static boolean isMedallionAssemblyTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        return normalized.startsWith(MEDALLION_ASSEMBLY_TITLE_PREFIX)
                && normalized.toLowerCase().contains("fragments to assemble");
    }

    private static CollectionLogRequirementPreview singleSectionPreview(List<CollectionLogRequirementItem> items, int iconColumns) {
        return new CollectionLogRequirementPreview("", "", false, true, items, iconColumns);
    }

    private static String normalizeCompactPrereqs(String prereqs) {
        String normalized = prereqs == null ? "" : prereqs.replace("\r", "").trim();
        return isNoPrereqsText(normalized) ? "" : normalized;
    }

    private static boolean isNoPrereqsText(String prereqs) {
        return prereqs.isEmpty() || prereqs.equalsIgnoreCase("none") || prereqs.equalsIgnoreCase("n/a") || prereqs.equals("-");
    }

    private void drawCompactLine(Graphics2D g, FontMetrics fm, CompactLine line, int x, int y, int maxWidth) {
        if (line.fixedHeight >= 0) {
            return;
        }

        int drawX = x;
        int drawMaxWidth = maxWidth;
        PrerequisiteStatus status = line.prerequisiteStatus;
        if (status == null) {
            String drawLine = TextUtils.truncateToWidth(line.text, fm, maxWidth);
            g.setColor(line.heading ? P.UI_GOLD : (line.dim ? P.UI_TEXT_DIM : P.UI_TEXT));
            g.drawString(drawLine, x, y);
            return;
        }

        boolean hasCheckSpans = status.getCheckSpans() != null && !status.getCheckSpans().isEmpty();
        g.setColor(!hasCheckSpans && status.isCompleted() ? P.UI_TEXT_DIM : P.UI_TEXT);
        if (line.firstPrerequisiteLine) {
            PrerequisiteIconRenderer.drawMarker(g, fm, line.prerequisiteMarkerImage, x, y);
        }
        drawX = PrerequisiteIconRenderer.textX(fm, x, line.prerequisiteMarkerImage);
        drawMaxWidth = PrerequisiteIconRenderer.textWidth(fm, maxWidth, line.prerequisiteMarkerImage);
        String drawLine = TextUtils.truncateToWidth(line.text, fm, drawMaxWidth);
        g.drawString(drawLine, drawX, y);

        if (!hasCheckSpans) {
            if (status.isCompleted()) {
                drawCompactStrikeThrough(g, fm, drawLine, drawX, y);
            }
            return;
        }

        for (PrerequisiteStatus.CheckSpan span : status.getCheckSpans()) {
            if (!span.isCompleted() || span.getStart() < 0 || span.getEnd() > status.getText().length() || span.getStart() >= span.getEnd()) {
                continue;
            }

            String spanText = status.getText().substring(span.getStart(), span.getEnd());
            int lineIndex = drawLine.indexOf(spanText);
            if (lineIndex < 0) {
                continue;
            }

            int spanX = drawX + fm.stringWidth(drawLine.substring(0, lineIndex));
            g.setColor(P.UI_TEXT_DIM);
            g.drawString(spanText, spanX, y);
            drawCompactStrikeThrough(g, fm, spanText, spanX, y);
        }
    }

    private void drawCompactStrikeThrough(Graphics2D g, FontMetrics fm, String text, int x, int baselineY) {
        int lineW = fm.stringWidth(text);
        int strikeY = baselineY - (fm.getAscent() * 3 / 5);
        g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 170));
        g.drawLine(x, strikeY, x + lineW, strikeY);
    }

    private List<CompactLine> wrappedCompactLines(String text, boolean dim) {
        List<CompactLine> out = new ArrayList<>();
        for (String line : TextUtils.wrapText(text, fontMetrics(), PANEL_W_COMPACT - PANEL_PADDING * 2 - 26)) {
            out.add(new CompactLine(line, false, dim));
        }
        return out;
    }

    private List<CompactLine> wrappedCompactPrereqLines(PrerequisiteStatus status) {
        List<CompactLine> out = new ArrayList<>();
        String text = status == null ? "" : status.getText();
        FontMetrics fm = fontMetrics();
        int width = PANEL_W_COMPACT - PANEL_PADDING * 2 - 26;
        BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, this::getCachedSkillImage, this::getCachedPrerequisiteMarkerImage);
        int textWidth = PrerequisiteIconRenderer.textWidth(fm, width, markerImage);
        boolean firstLine = true;
        for (String line : TextUtils.wrapText(text, fm, textWidth)) {
            out.add(CompactLine.prerequisite(line, status, markerImage, firstLine));
            firstLine = false;
        }
        return out;
    }

    private int compactLineHeight(CompactLine line, int maxWidth) {
        if (line != null && line.fixedHeight >= 0) {
            return line.fixedHeight;
        }
        if (line != null && line.collectionLogPreview != null) {
            return CollectionLogIconGridRenderer.measureHeight(
                    line.collectionLogPreview.getItems().size(),
                    maxWidth,
                    line.collectionLogPreview.iconColumns());
        }
        if (line != null && line.tierSection != null) {
            return ROW_HEIGHT;
        }
        if (line != null && line.prerequisiteStatus != null) {
            return PrerequisiteIconRenderer.lineHeight(ROW_HEIGHT, line.prerequisiteStatus);
        }
        return ROW_HEIGHT;
    }

    private FontMetrics fontMetrics() {
        return client.getCanvas().getFontMetrics(FontManager.getRunescapeSmallFont());
    }

    private void drawCompactTierLabel(Graphics2D g, FontMetrics fm, CollectionLogRequirementPreview.TierSection section, int x, int baseline) {
        String text = section.tier() == null ? "TIER" : tierLabel(section.tier()).toUpperCase();
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(text, fm, Math.max(0, 160)), x, baseline);
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
        if (preciseWheelRotation == 0.0) {
            return;
        }

        compactCurrentPendingWheelRotation += preciseWheelRotation;
    }

    private void setCompactCurrentScrollFraction(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        compactCurrentScrollPx = (int) Math.round(clamped * Math.max(0, compactCurrentMaxOffsetPx));
        compactCurrentWheelRemainderPx = 0.0;
    }

    private void resetCompactScroll() {
        compactCurrentScrollPx = 0;
        compactCurrentMaxOffsetPx = 0;
        compactCurrentWheelRemainderPx = 0.0;
        compactCurrentPendingWheelRotation = 0.0;
    }

    private void applyCompactScrollInputs(int maxOffsetPx) {
        if (maxOffsetPx <= 0) {
            resetCompactScroll();
            return;
        }

        if (compactCurrentPendingWheelRotation != 0.0) {
            double pixels = compactCurrentPendingWheelRotation * SCROLL_ROWS_PER_NOTCH * ROW_HEIGHT + compactCurrentWheelRemainderPx;
            int deltaPx = pixels > 0 ? (int) Math.floor(pixels) : (int) Math.ceil(pixels);
            compactCurrentWheelRemainderPx = pixels - deltaPx;
            compactCurrentPendingWheelRotation = 0.0;
            compactCurrentScrollPx += deltaPx;
        }

        compactCurrentScrollPx = Math.max(0, Math.min(maxOffsetPx, compactCurrentScrollPx));
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
        currentLayout.skipButtonBounds.setBounds(0, 0, 0, 0);
        currentLayout.undoButtonBounds.setBounds(0, 0, 0, 0);
        currentLayout.rollSourceIconBounds.setBounds(0, 0, 0, 0);
        currentLayout.skippedTasksIconBounds.setBounds(0, 0, 0, 0);
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
        return "Completed: " + COMPACT_COMPLETION_DATE_TIME_FORMAT.format(Instant.ofEpochMilli(info.timestamp));
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
        private final boolean dimCollectionLogIcons;
        private final CollectionLogRequirementPreview.TierSection tierSection;
        private final PrerequisiteStatus prerequisiteStatus;
        private final BufferedImage prerequisiteMarkerImage;
        private final boolean firstPrerequisiteLine;
        private final int fixedHeight;

        private CompactLine(String text, boolean heading, boolean dim) {
            this.text = text == null ? "" : text;
            this.heading = heading;
            this.dim = dim;
            this.collectionLogPreview = null;
            this.dimCollectionLogIcons = false;
            this.tierSection = null;
            this.prerequisiteStatus = null;
            this.prerequisiteMarkerImage = null;
            this.firstPrerequisiteLine = false;
            this.fixedHeight = -1;
        }

        private CompactLine(CollectionLogRequirementPreview collectionLogPreview, boolean dimCollectionLogIcons) {
            this.text = "";
            this.heading = false;
            this.dim = false;
            this.collectionLogPreview = collectionLogPreview;
            this.dimCollectionLogIcons = dimCollectionLogIcons;
            this.tierSection = null;
            this.prerequisiteStatus = null;
            this.prerequisiteMarkerImage = null;
            this.firstPrerequisiteLine = false;
            this.fixedHeight = -1;
        }

        private CompactLine(String text, PrerequisiteStatus prerequisiteStatus, BufferedImage prerequisiteMarkerImage, boolean firstPrerequisiteLine) {
            this.text = text == null ? "" : text;
            this.heading = false;
            this.dim = false;
            this.collectionLogPreview = null;
            this.dimCollectionLogIcons = false;
            this.tierSection = null;
            this.prerequisiteStatus = prerequisiteStatus;
            this.prerequisiteMarkerImage = prerequisiteMarkerImage;
            this.firstPrerequisiteLine = firstPrerequisiteLine;
            this.fixedHeight = -1;
        }

        private CompactLine(int fixedHeight) {
            this.text = "";
            this.heading = false;
            this.dim = true;
            this.collectionLogPreview = null;
            this.dimCollectionLogIcons = false;
            this.tierSection = null;
            this.prerequisiteStatus = null;
            this.prerequisiteMarkerImage = null;
            this.firstPrerequisiteLine = false;
            this.fixedHeight = Math.max(0, fixedHeight);
        }

        private CompactLine(CollectionLogRequirementPreview.TierSection tierSection) {
            this.text = "";
            this.heading = false;
            this.dim = false;
            this.collectionLogPreview = null;
            this.dimCollectionLogIcons = false;
            this.tierSection = tierSection;
            this.prerequisiteStatus = null;
            this.prerequisiteMarkerImage = null;
            this.firstPrerequisiteLine = false;
            this.fixedHeight = -1;
        }

        private static CompactLine spacer() {
            return new CompactLine("", false, true);
        }

        private static CompactLine verticalGap(int height) {
            return new CompactLine(height);
        }

        private static CompactLine collectionLogIcons(CollectionLogRequirementPreview collectionLogPreview) {
            return new CompactLine(collectionLogPreview, false);
        }

        private static CompactLine collectionLogIcons(CollectionLogRequirementPreview collectionLogPreview, boolean dimCollectionLogIcons) {
            return new CompactLine(collectionLogPreview, dimCollectionLogIcons);
        }

        private static CompactLine tierLabel(CollectionLogRequirementPreview.TierSection tierSection) {
            return new CompactLine(tierSection);
        }

        private static CompactLine prerequisite(String text, PrerequisiteStatus status, BufferedImage markerImage, boolean firstLine) {
            return new CompactLine(text, status, markerImage, firstLine);
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
                keyboardHintsOpen,
                currentKeyboardTriggeredTaskTooltipText(),
                currentKeyboardTriggeredTaskTooltipAnchor()
        );
    }

    private void showKeyboardTriggeredTaskTooltip(String text, Rectangle anchor) {
        keyboardTriggeredTaskTooltipText = text;
        keyboardTriggeredTaskTooltipAnchor.setBounds(anchor == null ? new Rectangle() : anchor);
        keyboardTriggeredTaskTooltipUntilMs = System.currentTimeMillis() + KEYBOARD_TRIGGERED_TOOLTIP_MS;
    }

    private String currentKeyboardTriggeredTaskTooltipText() {
        if (keyboardTriggeredTaskTooltipText == null) {
            return null;
        }

        if (System.currentTimeMillis() > keyboardTriggeredTaskTooltipUntilMs) {
            keyboardTriggeredTaskTooltipText = null;
            keyboardTriggeredTaskTooltipAnchor.setBounds(0, 0, 0, 0);
            keyboardTriggeredTaskTooltipUntilMs = 0L;
            return null;
        }

        return keyboardTriggeredTaskTooltipText;
    }

    private Rectangle currentKeyboardTriggeredTaskTooltipAnchor() {
        return keyboardTriggeredTaskTooltipText == null ? null : keyboardTriggeredTaskTooltipAnchor;
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
                plugin.getSyncCompletionCandidateTasks(TaskSource.COMBAT_ACHIEVEMENT).size(),
                plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG).size(),
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
        rulesLayout.syncCaFoundReviewButtonBounds.setBounds(layout.syncCaFoundReviewButtonBounds);
        rulesLayout.syncCaFoundIgnoreButtonBounds.setBounds(layout.syncCaFoundIgnoreButtonBounds);
        rulesLayout.syncClogFoundReviewButtonBounds.setBounds(layout.syncClogFoundReviewButtonBounds);
        rulesLayout.syncClogFoundIgnoreButtonBounds.setBounds(layout.syncClogFoundIgnoreButtonBounds);
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
        if (rulesLayout.syncCaFoundReviewButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCaFoundReviewButtonBounds, "Update tasks", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
        }
        if (rulesLayout.syncClogFoundReviewButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncClogFoundReviewButtonBounds, "Update tasks", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
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
            return openSelectedTaskDetailsFromKeyboard();
        }

        // S toggles completion sort (new model)
        if (code == KeyEvent.VK_S) {
            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                showKeyboardTriggeredTaskTooltip("\"Status\" filter currently applied", controls.sortCompletion);
                return true;
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
                showKeyboardTriggeredTaskTooltip("\"All Tiers\" filter must be applied", controls.sortTier);
                return true;
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
                showKeyboardTriggeredTaskTooltip("\"Complete\" filter must be applied", controls.sortDate);
                return true;
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
                showKeyboardTriggeredTaskTooltip("\"Complete\" filter must be applied", controls.sortTimeTicks);
                return true;
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
            closeTaskDetailsWithPendingIncompleteCheck();
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

    private boolean openSelectedTaskDetailsFromKeyboard()
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

        taskResolveSavedIncompleteEdits = false;
        selectedTaskResolveIncompleteTaskIds.clear();
        taskResolveOriginalIncompleteTaskIds.clear();
        closeTaskResolveWithoutRestoring();
        closeTaskSyncResult();
        closeTaskDetailsIncompleteConfirm();
        taskDetailsPopup.open(task);
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
            closeTaskDetailsWithPendingIncompleteCheck();
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
        String cacheKey = sortedTaskListCacheKey(tier);
        List<XtremeTask> cached = sortedTaskListCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        long startNanos = System.nanoTime();
        List<XtremeTask> base = getTasksForScope(taskQuery.tierScope, tier);
        List<XtremeTask> sorted = TaskListPipeline.apply(base, taskQuery, plugin::isTaskCompleted, plugin::isNewTask, plugin::getCompletionInfo, plugin::getTaskTimeTicks);
        List<XtremeTask> result = useCondensedTaskRows() ? TaskGroupUtils.collapsePreservingOrder(sorted) : sorted;
        List<XtremeTask> immutableResult = Collections.unmodifiableList(new ArrayList<>(result));
        sortedTaskListCache.put(cacheKey, immutableResult);
        logSlowTaskListBuild(startNanos, tier, base.size(), immutableResult.size());
        return immutableResult;
    }

    private boolean useCondensedTaskRows()
    {
        return plugin.condenseRepeatedTasks() && !taskQuery.sortByDate && !taskQuery.sortByTimeTicks;
    }

    private String sortedTaskListCacheKey(TaskTier tier)
    {
        return "tier=" + tier
                + "|scope=" + taskQuery.tierScope
                + "|search=" + (taskQuery.searchText == null ? "" : taskQuery.searchText)
                + "|src=" + taskQuery.sourceFilter
                + "|ca=" + taskQuery.sourceCASelected
                + "|cl=" + taskQuery.sourceClogsSelected
                + "|da=" + taskQuery.sourceDasSelected
                + "|status=" + taskQuery.statusFilter
                + "|sortCompletion=" + taskQuery.sortByCompletion
                + "|completedFirst=" + taskQuery.completedFirst
                + "|sortTier=" + taskQuery.sortByTier
                + "|easyFirst=" + taskQuery.easyTierFirst
                + "|sortDate=" + taskQuery.sortByDate
                + "|newestFirst=" + taskQuery.newestFirst
                + "|sortTicks=" + taskQuery.sortByTimeTicks
                + "|longestFirst=" + taskQuery.longestFirst
                + "|newOnly=" + taskQuery.showNewTasksFilter
                + "|condensed=" + useCondensedTaskRows()
                + "|state=" + plugin.getTaskListRenderStateHash();
    }

    private void logSlowTaskListBuild(long startNanos, TaskTier tier, int inputCount, int outputCount)
    {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos < SLOW_TASK_LIST_LOG_THRESHOLD_NANOS)
        {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSlowTaskListLogMs < SLOW_TASK_LIST_LOG_INTERVAL_MS)
        {
            return;
        }

        lastSlowTaskListLogMs = nowMs;
        log.debug("Slow task list build: tier={}, input={}, output={}, elapsed={}ms",
                tier, inputCount, outputCount, elapsedNanos / 1_000_000L);
    }

    private void logSlowPanelRender(long startNanos)
    {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos < SLOW_PANEL_RENDER_LOG_THRESHOLD_NANOS)
        {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSlowPanelRenderLogMs < SLOW_PANEL_RENDER_LOG_INTERVAL_MS)
        {
            return;
        }

        lastSlowPanelRenderLogMs = nowMs;
        log.debug("Slow Xtreme Tasker panel render: tab={}, compact={}, detailsOpen={}, elapsed={}ms",
                activeTab, compactPanelMode, taskDetailsPopup.isOpen(), elapsedNanos / 1_000_000L);
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
            public void openSyncCompletionCandidateReview(TaskSource source) {
                if (!plugin.getSyncCompletionCandidateTasks(source).isEmpty()) {
                    syncMismatchReviewSource = source;
                    syncReviewMode = SyncReviewMode.COMPLETION_CANDIDATES;
                    syncMismatchReviewOpen = true;
                    syncMismatchScroll.reset();
                    selectedSyncMismatchTaskIds.clear();
                    syncMismatchApplyConfirmOpen = false;
                    syncMismatchGuardMessage = null;
                    closeSyncMismatchGroupResolve();
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
                    syncReviewMode = SyncReviewMode.MISMATCH;
                    syncMismatchReviewOpen = true;
                    syncMismatchScroll.reset();
                    selectedSyncMismatchTaskIds.clear();
                    syncMismatchApplyConfirmOpen = false;
                    syncMismatchGuardMessage = null;
                    closeSyncMismatchGroupResolve();
                }
            }

            @Override
            public void closeSyncMismatchReview() {
                syncMismatchReviewOpen = false;
                syncReviewMode = SyncReviewMode.MISMATCH;
                syncMismatchReviewSource = null;
                selectedSyncMismatchTaskIds.clear();
                syncMismatchApplyConfirmOpen = false;
                syncMismatchGuardMessage = null;
                syncMismatchDescriptionTask = null;
                closeSyncMismatchGroupResolve();
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
                taskResolveSavedIncompleteEdits = false;
                selectedTaskResolveIncompleteTaskIds.clear();
                taskResolveOriginalIncompleteTaskIds.clear();
                closeTaskResolveWithoutRestoring();
                closeTaskSyncResult();
                closeTaskDetailsIncompleteConfirm();
                taskDetailsPopup.open(task);
                plugin.refreshTaskSyncMismatchForTask(task);
            }

            @Override
            public void closeTaskDetails() {
                closeTaskDetailsWithPendingIncompleteCheck();
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
            public Rectangle taskDetailsWikiMenuBounds() {
                return taskDetailsPopup.wikiMenuBounds();
            }

            @Override
            public boolean isTaskDetailsWikiMenuOpen() {
                return taskDetailsPopup.isWikiMenuOpen();
            }

            @Override
            public void openTaskDetailsWikiMenu() {
                taskDetailsPopup.openWikiMenu();
            }

            @Override
            public void closeTaskDetailsWikiMenu() {
                taskDetailsPopup.closeWikiMenu();
            }

            @Override
            public WikiLink taskDetailsWikiLinkAt(Point point) {
                return taskDetailsPopup.wikiLinkAt(point);
            }

            @Override
            public List<WikiLink> taskDetailsWikiLinks(XtremeTask task) {
                return XtremeTaskerOverlay.this.taskDetailsWikiLinks(task);
            }

            @Override
            public Rectangle taskDetailsSyncBounds() {
                return taskDetailsPopup.syncBounds();
            }

            @Override
            public Rectangle taskDetailsIgnoreBounds() {
                return taskDetailsPopup.ignoreBounds();
            }

            @Override
            public Rectangle taskDetailsMarkIncompleteBounds() {
                return taskDetailsPopup.markIncompleteBounds();
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
            public String taskDetailsSyncButtonLabel(XtremeTask task) {
                return XtremeTaskerOverlay.this.taskDetailsSyncButtonLabel(task);
            }

            @Override
            public void handleTaskDetailsSyncButton(XtremeTask task) {
                XtremeTaskerOverlay.this.handleTaskDetailsSyncButton(task);
            }

            @Override
            public void handleTaskDetailsMarkIncompleteButton(XtremeTask task) {
                XtremeTaskerOverlay.this.handleTaskDetailsMarkIncompleteButton(task);
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
            public boolean isSyncCompletionCandidateReviewOpen() {
                return syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES;
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
            public boolean isSyncMismatchGuardOpen() {
                return syncMismatchGuardMessage != null;
            }

            @Override
            public Rectangle syncMismatchGuardBounds() {
                return syncMismatchGuardBounds;
            }

            @Override
            public Rectangle syncMismatchGuardOkBounds() {
                return syncMismatchGuardOkBounds;
            }

            @Override
            public Rectangle syncMismatchGuardViewportBounds() {
                return syncMismatchGuardViewportBounds;
            }

            @Override
            public Rectangle syncMismatchGuardScrollbarRailBounds() {
                return syncMismatchGuardScrollbarRailBounds;
            }

            @Override
            public Rectangle syncMismatchGuardScrollbarThumbBounds() {
                return syncMismatchGuardScrollbarThumbBounds;
            }

            @Override
            public TaskListScrollController syncMismatchGuardScroll() {
                return syncMismatchGuardScroll;
            }

            @Override
            public int syncMismatchGuardRowBlock() {
                return scaleInputValue(XtremeTaskerOverlay.this.syncMismatchGuardRowBlock());
            }

            @Override
            public int syncMismatchGuardTotalRows() {
                return syncMismatchGuardTotalRows;
            }

            @Override
            public void closeSyncMismatchGuard() {
                syncMismatchGuardMessage = null;
                syncMismatchGuardTotalRows = 0;
                syncMismatchGuardScroll.reset();
                syncMismatchGuardBounds.setBounds(0, 0, 0, 0);
                syncMismatchGuardOkBounds.setBounds(0, 0, 0, 0);
                syncMismatchGuardViewportBounds.setBounds(0, 0, 0, 0);
                syncMismatchGuardScrollbarRailBounds.setBounds(0, 0, 0, 0);
                syncMismatchGuardScrollbarThumbBounds.setBounds(0, 0, 0, 0);
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
            public int syncMismatchVisibleTaskCount() {
                return visibleSyncMismatchTasks().size();
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
                syncMismatchGuardMessage = null;
            }

            @Override
            public void closeSyncMismatchDescription() {
                syncMismatchDescriptionTask = null;
            }

            @Override
            public boolean isSyncMismatchGroupResolveOpen() {
                return syncMismatchGroupResolveTask != null;
            }

            @Override
            public Rectangle syncMismatchGroupResolveBounds() {
                return syncMismatchGroupResolveBounds;
            }

            @Override
            public Rectangle syncMismatchGroupResolveSaveBounds() {
                return syncMismatchGroupResolveSaveBounds;
            }

            @Override
            public Rectangle syncMismatchGroupResolveCancelBounds() {
                return syncMismatchGroupResolveCancelBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> syncMismatchGroupResolveToggleBounds() {
                return syncMismatchGroupResolveToggleBounds;
            }

            @Override
            public void openSyncMismatchGroupResolve(XtremeTask task) {
                XtremeTaskerOverlay.this.openSyncMismatchGroupResolve(task);
            }

            @Override
            public void closeSyncMismatchGroupResolve() {
                XtremeTaskerOverlay.this.closeSyncMismatchGroupResolve();
            }

            @Override
            public void saveSyncMismatchGroupResolve() {
                XtremeTaskerOverlay.this.saveSyncMismatchGroupResolve();
            }

            @Override
            public void toggleSyncMismatchGroupResolveTask(XtremeTask task) {
                XtremeTaskerOverlay.this.toggleSyncMismatchGroupResolveTask(task);
            }

            @Override
            public boolean canToggleSyncMismatchGroupResolveTask(XtremeTask task) {
                return XtremeTaskerOverlay.this.canToggleSyncMismatchGroupResolveTask(task);
            }

            @Override
            public boolean hasSyncMismatchGroupResolveChanges() {
                return XtremeTaskerOverlay.this.hasSyncMismatchGroupResolveChanges();
            }

            @Override
            public boolean isSyncMismatchGroupActionTask(XtremeTask task) {
                return syncMismatchUsesGroupResolver(task);
            }

            @Override
            public boolean isSyncMismatchTaskSelected(XtremeTask task) {
                return task != null && task.getId() != null && selectedSyncMismatchTaskIds.contains(task.getId());
            }

            @Override
            public void toggleSyncMismatchTaskSelected(XtremeTask task) {
                if (task == null || task.getId() == null) return;
                if (syncMismatchUsesGroupResolver(task)) {
                    openSyncMismatchGroupResolve(task);
                    return;
                }
                if (!selectedSyncMismatchTaskIds.remove(task.getId())) {
                    selectedSyncMismatchTaskIds.add(task.getId());
                }
                syncMismatchApplyConfirmOpen = false;
                syncMismatchGuardMessage = null;
            }

            @Override
            public void selectAllSyncMismatchTasks() {
                for (XtremeTask task : visibleSyncMismatchTasks()) {
                    if (task == null || task.getId() == null) {
                        continue;
                    }
                    if (syncMismatchUsesGroupResolver(task)) {
                        for (XtremeTask groupedTask : syncMismatchGroupSelectableTasks(task)) {
                            if (groupedTask != null && groupedTask.getId() != null) {
                                selectedSyncMismatchTaskIds.add(groupedTask.getId());
                            }
                        }
                    } else {
                        selectedSyncMismatchTaskIds.add(task.getId());
                    }
                }
                syncMismatchApplyConfirmOpen = false;
                syncMismatchGuardMessage = null;
            }

            @Override
            public void clearSyncMismatchSelection() {
                selectedSyncMismatchTaskIds.clear();
                syncMismatchApplyConfirmOpen = false;
                syncMismatchGuardMessage = null;
            }

            @Override
            public int syncMismatchSelectedCount() {
                return selectedVisibleSyncMismatchCount(visibleSyncMismatchTasks());
            }

            @Override
            public int syncMismatchSelectableCount() {
                return selectableVisibleSyncMismatchCount(visibleSyncMismatchTasks());
            }

            @Override
            public List<XtremeTask> selectedSyncMismatchTasks() {
                List<XtremeTask> out = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (XtremeTask task : visibleSyncMismatchTasks()) {
                    if (task == null || task.getId() == null) {
                        continue;
                    }
                    if (syncMismatchUsesGroupResolver(task)) {
                        for (XtremeTask groupedTask : syncMismatchGroupSelectableTasks(task)) {
                            if (groupedTask != null
                                    && groupedTask.getId() != null
                                    && selectedSyncMismatchTaskIds.contains(groupedTask.getId())
                                    && seen.add(groupedTask.getId())) {
                                out.add(groupedTask);
                            }
                        }
                    } else if (selectedSyncMismatchTaskIds.contains(task.getId()) && seen.add(task.getId())) {
                        out.add(task);
                    }
                }
                return out;
            }

            @Override
            public void requestSyncMismatchApplyConfirm() {
                if (syncMismatchSelectedCount() > 0) {
                    syncMismatchGuardMessage = null;
                    if (syncReviewMode == SyncReviewMode.MISMATCH) {
                        String guardMessage = plugin.getSyncMismatchIncompleteGuardMessage(selectedSyncMismatchTasks());
                        if (guardMessage != null && !guardMessage.trim().isEmpty()) {
                            syncMismatchGuardMessage = guardMessage;
                            syncMismatchGuardScroll.reset();
                            syncMismatchApplyConfirmOpen = false;
                            return;
                        }
                    }
                    syncMismatchApplyConfirmOpen = true;
                }
            }

            @Override
            public void closeSyncMismatchApplyConfirm() {
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public boolean isTaskResolveOpen() {
                return XtremeTaskerOverlay.this.isTaskResolveOpen();
            }

            @Override
            public boolean isTaskSyncResultOpen() {
                return XtremeTaskerOverlay.this.isTaskSyncResultOpen();
            }

            @Override
            public Rectangle taskSyncResultBounds() {
                return taskSyncResultBounds;
            }

            @Override
            public Rectangle taskSyncResultCloseBounds() {
                return taskSyncResultCloseBounds;
            }

            @Override
            public void closeTaskSyncResult() {
                XtremeTaskerOverlay.this.closeTaskSyncResult();
            }

            @Override
            public Rectangle taskResolveBounds() {
                return taskResolveBounds;
            }

            @Override
            public Rectangle taskResolveCloseBounds() {
                return taskResolveCloseBounds;
            }

            @Override
            public Rectangle taskResolveSaveBounds() {
                return taskResolveSaveBounds;
            }

            @Override
            public Rectangle taskResolveCancelBounds() {
                return taskResolveCancelBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> taskResolveInstanceToggleBounds() {
                return taskResolveInstanceToggleBounds;
            }

            @Override
            public void closeTaskResolve() {
                XtremeTaskerOverlay.this.closeTaskResolve();
            }

            @Override
            public void saveTaskResolve() {
                XtremeTaskerOverlay.this.saveTaskResolve();
            }

            @Override
            public void toggleTaskResolveTaskIncomplete(XtremeTask task) {
                XtremeTaskerOverlay.this.toggleTaskResolveTaskIncomplete(task);
            }

            @Override
            public boolean canToggleTaskResolveTaskIncomplete(XtremeTask task) {
                return XtremeTaskerOverlay.this.canToggleTaskResolveTaskIncomplete(task);
            }

            @Override
            public boolean hasTaskResolveChanges() {
                return XtremeTaskerOverlay.this.hasTaskResolveChanges();
            }

            @Override
            public boolean isTaskDetailsIncompleteConfirmOpen() {
                return XtremeTaskerOverlay.this.isTaskDetailsIncompleteConfirmOpen();
            }

            @Override
            public Rectangle taskDetailsIncompleteConfirmBounds() {
                return taskDetailsIncompleteConfirmBounds;
            }

            @Override
            public Rectangle taskDetailsIncompleteConfirmYesBounds() {
                return taskDetailsIncompleteConfirmYesBounds;
            }

            @Override
            public Rectangle taskDetailsIncompleteConfirmNoBounds() {
                return taskDetailsIncompleteConfirmNoBounds;
            }

            @Override
            public void confirmTaskDetailsIncompleteSelection() {
                XtremeTaskerOverlay.this.confirmTaskDetailsIncompleteSelection();
            }

            @Override
            public void closeTaskDetailsIncompleteConfirm() {
                XtremeTaskerOverlay.this.closeTaskDetailsIncompleteConfirm();
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
        scaleRect(syncMismatchGuardBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGuardOkBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGuardViewportBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGuardScrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGuardScrollbarThumbBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchScrollbarRailBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchScrollbarThumbBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchDescriptionBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchDescriptionCloseBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGroupResolveBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGroupResolveSaveBounds, anchorX, anchorY, scale);
        scaleRect(syncMismatchGroupResolveCancelBounds, anchorX, anchorY, scale);
        scaleRect(taskSyncResultBounds, anchorX, anchorY, scale);
        scaleRect(taskSyncResultCloseBounds, anchorX, anchorY, scale);
        scaleRect(taskResolveBounds, anchorX, anchorY, scale);
        scaleRect(taskResolveCloseBounds, anchorX, anchorY, scale);
        scaleRect(taskResolveSaveBounds, anchorX, anchorY, scale);
        scaleRect(taskResolveCancelBounds, anchorX, anchorY, scale);
        scaleRect(taskDetailsIncompleteConfirmBounds, anchorX, anchorY, scale);
        scaleRect(taskDetailsIncompleteConfirmYesBounds, anchorX, anchorY, scale);
        scaleRect(taskDetailsIncompleteConfirmNoBounds, anchorX, anchorY, scale);
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
        scaleRectMap(syncMismatchGroupResolveToggleBounds, anchorX, anchorY, scale);
        scaleRectMap(taskResolveInstanceToggleBounds, anchorX, anchorY, scale);
    }

    private void scaleCurrentLayoutBounds(int anchorX, int anchorY, double scale) {
        scaleRect(currentLayout.wikiButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.rollButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.completeButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.skipButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.undoButtonBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.rollSourceIconBounds, anchorX, anchorY, scale);
        scaleRect(currentLayout.skippedTasksIconBounds, anchorX, anchorY, scale);
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
        scaleRect(rulesLayout.syncCaFoundReviewButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncCaFoundIgnoreButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogFoundReviewButtonBounds, anchorX, anchorY, scale);
        scaleRect(rulesLayout.syncClogFoundIgnoreButtonBounds, anchorX, anchorY, scale);
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
        scaleRect(taskDetailsPopup.syncBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.ignoreBounds(), anchorX, anchorY, scale);
        scaleRect(taskDetailsPopup.markIncompleteBounds(), anchorX, anchorY, scale);
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

    private static final class SkillcapeDisplay
    {
        private final String name;
        private final int[] itemIds;

        private SkillcapeDisplay(String name, int[] itemIds)
        {
            this.name = name;
            this.itemIds = itemIds == null ? new int[0] : itemIds;
        }

        private int displayItemId()
        {
            return itemIds.length == 0 ? -1 : itemIds[0];
        }
    }
}
