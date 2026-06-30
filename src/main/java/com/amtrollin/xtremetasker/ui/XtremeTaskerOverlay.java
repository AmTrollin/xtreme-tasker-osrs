

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.*;
import com.amtrollin.xtremetasker.enums.*;
import com.amtrollin.xtremetasker.models.*;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.*;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.current.*;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.input.*;
import com.amtrollin.xtremetasker.ui.rules.*;
import com.amtrollin.xtremetasker.ui.style.*;
import com.amtrollin.xtremetasker.ui.tasklist.*;
import com.amtrollin.xtremetasker.ui.tasks.*;
import com.amtrollin.xtremetasker.ui.tasks.models.*;
import com.amtrollin.xtremetasker.ui.text.*;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import java.awt.*;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.widgets.*;
import net.runelite.client.game.*;
import net.runelite.client.input.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.LinkBrowser;
import org.slf4j.*;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;
import static com.amtrollin.xtremetasker.ui.style.UiDraw.centeredTextBaseline;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;
import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.*;

public class XtremeTaskerOverlay extends Overlay {
    private static final Logger log = LoggerFactory.getLogger(XtremeTaskerOverlay.class);
    private static final BufferedImage PLUGIN_ICON = loadPluginIconSafe();
    private static final BufferedImage HEADER_ICON = loadHeaderIconSafe();
    private static final UiPalette P = UiPalette.DEFAULT;
    private static final Color POPUP_BG = new Color(45, 36, 24, 252);
    private static final Color POPUP_BG_SOFT = new Color(45, 36, 24, 248);
    private static final int ANCIENT_PAGE_FIRST_ITEM_ID = 11341;
    private static final int ANCIENT_PAGE_LAST_ITEM_ID = 11366;
    private static final int COLLECTION_LOG_PREVIEW_CACHE_LIMIT = 256;
    private static final int COLLECTION_LOG_SEQUENCE_CACHE_LIMIT = 256;
    private static final int ITEM_IMAGE_CACHE_LIMIT = 512;
    private static final int SKILL_IMAGE_CACHE_LIMIT = 32;
    private static final int SPRITE_CACHE_LIMIT = 32;
    private static final int SORTED_TASK_LIST_CACHE_LIMIT = 48;
    private static final int PREREQUISITE_STATUS_CACHE_LIMIT = 512;
    private static final int SYNC_REVIEW_VISIBLE_TASKS_CACHE_LIMIT = 8;
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
    private static final List<WikiLink> SARADOMIN_HOOD_CLOAK_WIKI_LINKS = List.of(
            new WikiLink("Saradomin hood", "https://oldschool.runescape.wiki/w/Castlewars_hood_(Saradomin)"),
            new WikiLink("Saradomin cloak", "https://oldschool.runescape.wiki/w/Castlewars_cloak_(Saradomin)")
    );
    private static final List<WikiLink> FRESH_CRAB_CLAW_SHELL_WIKI_LINKS = List.of(
            new WikiLink("Fresh crab claw", "https://oldschool.runescape.wiki/w/Fresh_crab_claw"),
            new WikiLink("Fresh crab shell", "https://oldschool.runescape.wiki/w/Fresh_crab_shell")
    );
    private static final List<WikiLink> MOLE_CLAW_SKIN_WIKI_LINKS = List.of(
            new WikiLink("Mole claw", "https://oldschool.runescape.wiki/w/Mole_claw"),
            new WikiLink("Mole skin", "https://oldschool.runescape.wiki/w/Mole_skin")
    );
    private static final Map<String, List<WikiLink>> TASK_DETAILS_WIKI_LINKS_BY_ID = createTaskDetailsWikiLinksById();
    private static final Map<String, List<WikiLink>> TASK_DETAILS_WIKI_LINKS_BY_NAME = createTaskDetailsWikiLinksByName();
    private static final DateTimeFormatter SYNC_REVIEW_COMPLETION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yy").withZone(ZoneId.systemDefault());

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
        links.put(normalizeWikiTaskName("Get the Castle Wars Zamorak hood & cloak"), ZAMORAK_HOOD_CLOAK_WIKI_LINKS);
        links.put(normalizeWikiTaskName("Get the Saradomin hood & cloak"), SARADOMIN_HOOD_CLOAK_WIKI_LINKS);
        links.put(normalizeWikiTaskName("Get a Fresh crab claw & Fresh crab shell"), FRESH_CRAB_CLAW_SHELL_WIKI_LINKS);
        links.put(normalizeWikiTaskName("Get a Mole claw + skin"), MOLE_CLAW_SKIN_WIKI_LINKS);
        links.put(normalizeWikiTaskName("Get a Mole claw & skin"), MOLE_CLAW_SKIN_WIKI_LINKS);
        return Collections.unmodifiableMap(links);
    }

    private static Map<String, List<WikiLink>> createTaskDetailsWikiLinksById()
    {
        Map<String, List<WikiLink>> links = new HashMap<>();
        links.put("collection_log_easy_get-the-zamorak-hood-cloak_001_53931281fb", ZAMORAK_HOOD_CLOAK_WIKI_LINKS);
        links.put("collection_log_easy_get-the-saradomin-hood-cloak_001_9827fe8228", SARADOMIN_HOOD_CLOAK_WIKI_LINKS);
        links.put("collection_log_easy_get-a-fresh-crab-claw-fresh-crab-shell_001_0b0bfc6713", FRESH_CRAB_CLAW_SHELL_WIKI_LINKS);
        links.put("collection_log_easy_get-a-mole-claw-skin_001_79bd65ec5d", MOLE_CLAW_SKIN_WIKI_LINKS);
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
    private final Rectangle taskDetailsIncompleteConfirmBounds = new Rectangle();
    private final Rectangle taskDetailsIncompleteConfirmYesBounds = new Rectangle();
    private final Rectangle taskDetailsIncompleteConfirmNoBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = Collections.synchronizedMap(new EnumMap<>(TaskTier.class));
    private final Map<XtremeTask, Rectangle> taskRowBounds = Collections.synchronizedMap(new HashMap<>());
    private final Map<XtremeTask, Rectangle> syncMismatchTaskBounds = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> selectedSyncMismatchTaskIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> selectedTaskDetailsIncompleteTaskIds = Collections.synchronizedSet(new HashSet<>());

    // Current tab bounds (now come from CurrentTabLayout)
    private final CurrentTabLayout currentLayout = new CurrentTabLayout();
    // Rules tab bounds (now come from RulesTabLayout)
    private final RulesTabLayout rulesLayout = new RulesTabLayout();
    private RulesTabLayout.SubTab rulesSubTab = RulesTabLayout.SubTab.RULES;

    private boolean panelOpen = false;
    private boolean compactPanelMode = true;
    private boolean draggingPanel = false;
    private boolean markIncompleteDontShowChecked = false;
    private boolean syncMismatchApplyConfirmOpen = false;
    private boolean syncMismatchReviewOpen = false;
    private enum SyncReviewMode { MISMATCH, COMPLETION_CANDIDATES }
    private SyncReviewMode syncReviewMode = SyncReviewMode.MISMATCH;
    private TaskSource syncMismatchReviewSource = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private XtremeTask pendingMarkAllIncompleteTask = null;
    private boolean pendingMarkAllIncompleteGroupMode = false;
    private boolean taskDetailsIncompleteConfirmOpen = false;
    private boolean taskDetailsIncompleteConfirmCloseAfter = true;

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;
    private double panelScale = 1.0;
    private int panelInputAnchorX = 0;
    private int panelInputAnchorY = 0;
    private net.runelite.api.Point panelRenderMouse = null;

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
    private static final int PANEL_H_COMPACT = 260;
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
    private final Map<String, List<XtremeTask>> syncReviewVisibleTasksCache = lruCache(SYNC_REVIEW_VISIBLE_TASKS_CACHE_LIMIT);
    private long lastSlowPreviewLogMs = 0L;
    private long lastSlowTaskListLogMs = 0L;
    private long lastSlowPanelRenderLogMs = 0L;
    private boolean rollExecutionPending = false;
    private TaskTier rollingDisplayTier = null;
    private List<XtremeTask> rollingDisplayPool = Collections.emptyList();

    @Getter
    private final MouseAdapter mouseAdapter;
    @Getter
    private final MouseWheelListener mouseWheelListener;
    @Getter
    private final KeyListener keyListener;

    private enum MainTab {CURRENT, TASKS, RULES}

    private static final MainTab[] MAIN_TABS = {MainTab.CURRENT, MainTab.TASKS, MainTab.RULES};
    private static final String[] MAIN_TAB_LABELS = {"Current", "Tasks", "Help"};
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
        if (task.getSource() == TaskSource.DIARY_ACHIEVEMENT) return getCachedSprite(SpriteID.QUESTS_PAGE_ICON_GREEN_ACHIEVEMENT_DIARIES);
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

    private BufferedImage getCachedSprite(int spriteId)
    {
        if (spriteManager == null)
        {
            return null;
        }

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

    private BufferedImage resolvePrerequisiteMarkerImage(MarkerIcon markerIcon)
    {
        if (markerIcon == null)
        {
            return null;
        }

        switch (markerIcon)
        {
            case QUEST:
                return getCachedSprite(SpriteID.QUESTS_PAGE_ICON_BLUE_QUESTS);
            case START_QUEST:
                return getCachedSprite(SpriteID.QUESTS_PAGE_ICON_BLUE_QUESTS);
            case ACHIEVEMENT_DIARY:
                return getCachedSprite(SpriteID.QUESTS_PAGE_ICON_GREEN_ACHIEVEMENT_DIARIES);
            case COMBAT:
                return getCachedSprite(SpriteID.MULTI_COMBAT_ZONE_CROSSED_SWORDS);
            case TOTAL:
                return getCachedSprite(SpriteID.SKILL_TOTAL);
            case BULLET:
                return null;
            case BARBARIAN_MINIQUEST:
            case LAIR_OF_TARN_RAZORLOR:
            case MAGE_ARENA_1:
            case ENTER_THE_ABYSS:
            case VALE_TOTEMS:
            case ALFRED_GRIMHANDS_BARCRAWL:
                return getCachedSprite(SpriteID.QUESTS_PAGE_ICON_RED_MINIGAMES);
            case WILDERNESS:
                return getCachedSprite(SpriteID.PLAYER_KILLER_SKULL);
            case CURRENCY:
                return getCachedSprite(SpriteID.WELCOME_SCREEN_COINS);
            default:
                return null;
        }
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
                + "|pendingIncomplete=" + pendingTaskDetailsIncompleteSignature()
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

    private String pendingTaskDetailsIncompleteSignature()
    {
        if (selectedTaskDetailsIncompleteTaskIds.isEmpty())
        {
            return "";
        }
        List<String> ids = new ArrayList<>(selectedTaskDetailsIncompleteTaskIds);
        Collections.sort(ids);
        return String.join(",", ids);
    }

    private boolean isTaskCompletedForCollectionLogPreview(XtremeTask task)
    {
        if (task == null || !plugin.isTaskCompleted(task))
        {
            return false;
        }
        if (taskDetailsIncompleteConfirmOpen)
        {
            return true;
        }
        String id = task.getId();
        return id == null || !selectedTaskDetailsIncompleteTaskIds.contains(id);
    }

    private boolean isTaskCompletedForTaskDetails(XtremeTask task)
    {
        return isTaskCompletedForCollectionLogPreview(task);
    }

    private TaskGroupProgress taskDetailsGroupProgress(XtremeTask task)
    {
        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress == null
                || !progress.isGrouped()
                || selectedTaskDetailsIncompleteTaskIds.isEmpty()
                || taskDetailsIncompleteConfirmOpen)
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
                    && selectedTaskDetailsIncompleteTaskIds.contains(id)
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
        String pendingSummary = ancientPageRequirement
                ? pendingOpenClogSummary(plugin.getPendingAncientPageDropCountSinceLastSync())
                : medallionFragmentRequirement
                ? pendingOpenClogSummary(plugin.getPendingMedallionFragmentDropCountSinceLastSync())
                : "";
        if (!pendingSummary.isEmpty())
        {
            summaryText = summaryText.isEmpty() ? pendingSummary : pendingSummary + "\n" + summaryText;
        }
        String titleText;
        if (showSequenceTierSections)
        {
            titleText = "Eligible Collection Log items";
        }
        else if (itemIds.length == 1)
        {
            titleText = "Needed Collection Log item";
        }
        else
        {
            titleText = "Needed Collection Log items";
        }
        String secondaryTitleText = secondaryItems.isEmpty()
                ? ""
                : allRequiredItemsObtained ? "Now assemble:" : "Need all " + requiredCount + " fragments to assemble:";
        return new CollectionLogRequirementPreview(
                summaryText,
                titleText,
                !summaryText.isEmpty() && (!singleEligibleItem && !hasCompletionItem && (sameNameFamily || repeatedDistinctPool)
                        || !pendingSummary.isEmpty() || showSequenceTierSections),
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
                    statusByItemId.getOrDefault(itemId, CollectionLogRequirementItem.Status.MISSING)));
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
            items.add(new CollectionLogRequirementItem(itemId, entry.getKey(), entry.getValue()));
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
        return List.of(new CollectionLogRequirementItem(completionItemId, plugin.getItemName(completionItemId), status, true));
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

    private static String pendingOpenClogSummary(int pendingDrops)
    {
        if (pendingDrops <= 0)
        {
            return "";
        }

        return "+" + UiText.format("clog.pending_summary", pendingDrops, pendingDrops == 1 ? "item" : "items");
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

            if (!plugin.isCollectionLogItemObtained(itemId))
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

            CollectionLogRequirementItem.Status status = plugin.isCollectionLogItemObtained(itemId)
                    ? CollectionLogRequirementItem.Status.OBTAINED
                    : CollectionLogRequirementItem.Status.MISSING;
            itemsByTier.computeIfAbsent(sequenceTask.getTier(), ignored -> new ArrayList<>())
                    .add(new CollectionLogRequirementItem(
                            itemId,
                            collectionLogRequirementItemName(itemId),
                            status));
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
        for (XtremeTask candidate : plugin.getTasks())
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
                6908, 6910, 6912, 6914,
                31732, 31733, 31734,
                31744, 31745, 31746,
                31756, 31757, 31758
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

        int completed = 0;
        for (int i = 0; i < itemIds.length; i++)
        {
            boolean itemObtained = plugin.isCollectionLogItemObtained(itemIds[i]);
            if (!itemObtained)
            {
                break;
            }
            completed++;
        }
        return completed;
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
        for (XtremeTask candidate : plugin.getTasks())
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
    private final TaskSelectionModel selectionModel = new TaskSelectionModel();
    private final TaskListScrollController tasksScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListViewController taskListView = new TaskListViewController(selectionModel, tasksScroll);

    private final TaskListScrollController currentScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListScrollController syncMismatchScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);

    private final CurrentTabRenderer currentTabRenderer = new CurrentTabRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);
    private final CurrentTabViewRenderer currentTabViewRenderer = new CurrentTabViewRenderer(currentTabRenderer, P);
    private final CurrentTabState currentTabState = new CurrentTabState(currentLayout);


    private final TaskControlsRenderer controlsRendererTasks = new TaskControlsRenderer(PANEL_PADDING, ROW_HEIGHT, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.INPUT_BG, P.INPUT_FOCUS_OUTLINE, P.PILL_ON_BG, P.PILL_OFF_BG);

    private final TaskRowsRenderer taskRowsRendererTasks = new TaskRowsRenderer(PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT + 4, TASK_TEXT_PAD_LEFT + 4, P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE, P.STRIKE_COLOR, P.UI_TEXT, P.UI_TEXT_DIM, P.PIP_RING, P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);

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
            taskScrollbarThumbBounds
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

            taskQuery.searchFocused = false;
        });

        this.mouseWheelListener = new OverlayWheelHandler(access);
    }

    /** Returns true while the roll animation is in progress. */
    public boolean isRolling() {
        return animations.isRolling();
    }

    private void requestRollTask()
    {
        if (rollExecutionPending || animations.isRolling())
        {
            return;
        }

        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);
        if (current != null && !currentCompleted)
        {
            return;
        }

        rollingDisplayTier = current == null ? plugin.getCurrentTier() : current.getTier();
        if (rollingDisplayTier == null)
        {
            rollingDisplayTier = TaskTier.EASY;
        }
        rollingDisplayPool = Collections.unmodifiableList(new ArrayList<>(getTasksForTier(rollingDisplayTier)));
        animations.startRoll();
        rollExecutionPending = true;
    }

    private void processPendingRollExecution()
    {
        if (!rollExecutionPending)
        {
            return;
        }

        rollExecutionPending = false;
        plugin.rollRandomTaskAndPersist();
    }

    private void cancelPendingRollExecution()
    {
        rollExecutionPending = false;
        rollingDisplayTier = null;
        rollingDisplayPool = Collections.emptyList();
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

    public void refreshTaskListViewMode()
    {
        sortedTaskListCache.clear();
        resetTaskListViewAfterQueryChange();
        taskDetailsPopup.close();
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

    private int currentRowBlock() {
        return scaleInputValue(ROW_HEIGHT);
    }

    private int scaleInputValue(int value) {
        return Math.max(1, (int) Math.round(value * panelScale));
    }

    private double computePanelScale(int canvasW, int canvasH, int panelW, int panelH) {
        double maxScale = client.isResized() ? 1.0 : PANEL_SCALE_MIN;
        double widthPressure = (canvasW - PANEL_SCALE_AUTO_START_W) / PANEL_SCALE_AUTO_RANGE_W;
        double heightPressure = (canvasH - PANEL_SCALE_AUTO_START_H) / PANEL_SCALE_AUTO_RANGE_H;
        double pressure = Math.max(widthPressure, heightPressure);
        double autoScale = 1.0 - (Math.max(0.0, Math.min(1.0, pressure)) * (1.0 - PANEL_SCALE_MIN));

        double fitW = canvasW <= 8 ? 1.0 : (canvasW - 8.0) / panelW;
        double fitH = canvasH <= 4 ? 1.0 : (canvasH - 4.0) / panelH;
        double fitScale = Math.min(maxScale, Math.min(fitW, fitH));

        double scale = Math.min(autoScale, fitScale);
        if (fitScale < PANEL_SCALE_MIN) {
            return Math.max(0.55, Math.min(maxScale, scale));
        }
        return Math.max(PANEL_SCALE_MIN, Math.min(maxScale, scale));
    }

    private net.runelite.api.Point toPanelRenderMouse(net.runelite.api.Point mouse, AffineTransform transform) {
        if (mouse == null || transform == null || transform.isIdentity()) {
            return mouse;
        }

        try {
            Point2D source = new Point2D.Double(mouse.getX(), mouse.getY());
            Point2D logical = transform.inverseTransform(source, null);
            return new net.runelite.api.Point((int) Math.round(logical.getX()), (int) Math.round(logical.getY()));
        } catch (NoninvertibleTransformException ignored) {
            return mouse;
        }
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
            g.setColor(withAlpha(P.UI_GOLD, 180));
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
        panelInputAnchorX = panelX;
        panelInputAnchorY = panelY;

        panelBounds.setBounds(panelX, panelY, panelW, panelHeight);
        AffineTransform oldTransform = g.getTransform();
        if (panelScale != 1.0) {
            g.translate(panelX, panelY);
            g.scale(panelScale, panelScale);
            g.translate(-panelX, -panelY);
        }
        AffineTransform panelTransform = g.getTransform();
        panelRenderMouse = toPanelRenderMouse(client.getMouseCanvasPosition(), panelTransform);

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
        g.setColor(withAlpha(P.UI_EDGE_LIGHT, hoveringClose ? 95 : 55));
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
        g.setColor(withAlpha(P.UI_GOLD, 180));
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
                clearBounds(markAllIncompleteConfirmBounds, markAllIncompleteYesBounds,
                        markAllIncompleteNoBounds, markIncompleteDontShowBounds);
            }

            animations.prune();
            g.setTransform(oldTransform);
            scalePanelInputBounds(panelTransform);
            panelRenderMouse = null;
            logSlowPanelRender(panelRenderStartNanos);
            processPendingRollExecution();
            return new Dimension(physicalPanelW, physicalPanelH);
        }

        // tabs
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = panelInnerWidth();
        int tabW = (availableTabsW - 8) / 3;

        Rectangle[] tabBounds = {currentTabBounds, tasksTabBounds, rulesTabBounds};
        for (int i = 0; i < tabBounds.length; i++)
        {
            tabBounds[i].setBounds(panelX + PANEL_PADDING + i * (tabW + 4), cursorY, tabW, tabH);
            buttonRenderer.drawTab(g, tabBounds[i], MAIN_TAB_LABELS[i], activeTab == MAIN_TABS[i]);
        }

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
            clearBounds(markAllIncompleteConfirmBounds, markAllIncompleteYesBounds,
                    markAllIncompleteNoBounds, markIncompleteDontShowBounds);
        }

        if (syncMismatchReviewOpen && !visibleSyncMismatchTasks().isEmpty()) {
            renderSyncMismatchReview(g, fm);
        } else {
            clearSyncMismatchReviewBounds();
        }

        animations.prune();
        g.setTransform(oldTransform);
        scalePanelInputBounds(panelTransform);
        panelRenderMouse = null;
        renderTaskDetailsPopupUnscaled(g, fm);
        if (isTaskDetailsIncompleteConfirmOpen()) {
            renderTaskDetailsIncompleteConfirm(g, fm);
        }
        if (taskDetailsPopup.isOpen() && pendingMarkAllIncompleteTask != null) {
            renderMarkAllIncompleteConfirm(g, fm);
        }
        logSlowPanelRender(panelRenderStartNanos);
        processPendingRollExecution();
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
                this::resolvePrerequisiteMarkerImage,
                task -> buildCollectionLogRequirementPreview(task, !useCondensedTaskRows()),
                plugin::getCollectionLogSequenceStepLabel,
                plugin::isTaskSyncMismatch,
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

    private void renderCompactPanel(Graphics2D g, FontMetrics fm, int panelX, int contentTop) {
        resetCurrentLayoutBounds();
        int innerX = panelX + PANEL_PADDING;
        int innerW = panelBounds.width - PANEL_PADDING * 2;
        int bottom = panelBounds.y + panelBounds.height - PANEL_PADDING;
        int cardH = Math.max(120, bottom - contentTop);
        Rectangle card = new Rectangle(innerX, contentTop, innerW, cardH);
        drawBevelBox(g, card, new Color(26, 17, 10, 225));

        if (!plugin.hasTaskPackLoaded()) {
            drawCompactCenteredText(g, fm, card, "No tasks loaded.", P.UI_TEXT_DIM);
            return;
        }

        XtremeTask current = plugin.getCurrentTask();
        boolean rolling = animations.isRolling();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);
        boolean showCurrentTask = current != null && !rolling;
        boolean showCompleteAction = showCurrentTask && !currentCompleted;
        boolean currentCompletionCriteriaMet = showCompleteAction && plugin.isCurrentTaskCompletionCriteriaMet();

        Shape oldClip = g.getClip();
        g.setClip(new Rectangle(card.x + 2, card.y + 2, card.width - 4, card.height - 4));
        if (rolling) {
            drawCompactRolling(g, fm, card, current);
        } else if (showCurrentTask) {
            drawCompactCurrentIdentity(g, fm, card, current);
        } else {
            drawCompactEmptyIdentity(g, fm, card);
        }
        g.setClip(oldClip);

        if (rolling) {
            return;
        }

        int actionH = ROW_HEIGHT + 8;
        int actionY = card.y + card.height - actionH - 14;
        if (showCompleteAction) {
            int actionW = Math.max(260, Math.min(card.width - 36, fm.stringWidth("Mark complete") + 58));
            int actionX = card.x + (card.width - actionW) / 2;
            currentLayout.completeButtonBounds.setBounds(actionX, actionY, actionW, actionH);
            buttonRenderer.drawPrimaryButton(
                    g,
                    currentLayout.completeButtonBounds,
                    "Mark complete",
                    currentCompletionCriteriaMet ? UiPalette.TIER_COMPLETE_GLOW : null);
        } else {
            int rollW = Math.max(260, Math.min(card.width - 36, fm.stringWidth("Roll task") + 58));
            currentLayout.rollButtonBounds.setBounds(card.x + (card.width - rollW) / 2, actionY, rollW, actionH);
            buttonRenderer.drawPrimaryButton(g, currentLayout.rollButtonBounds, "Roll task");
        }
    }

    private void drawCompactRolling(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask current) {
        int x = card.x + 14;
        int innerW = card.width - 28;
        g.setColor(Color.WHITE);
        g.drawString("Rolling...", x, card.y + 12 + fm.getAscent());

        Font oldFont = g.getFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 17f);
        g.setFont(nameFont);
        FontMetrics nameFm = g.getFontMetrics();
        String name = computeCurrentLineForRender(current, false, nameFm);
        List<String> nameLines = TextUtils.wrapText(name, nameFm, innerW);
        int lineCount = Math.max(1, Math.min(2, nameLines.size()));
        int blockH = lineCount * nameFm.getHeight();
        int nameY = card.y + (card.height - blockH) / 2 + nameFm.getAscent();

        g.setColor(P.UI_GOLD);
        for (int i = 0; i < lineCount; i++) {
            String line = TextUtils.truncateToWidth(nameLines.get(i), nameFm, innerW);
            int lineX = card.x + (card.width - nameFm.stringWidth(line)) / 2;
            g.drawString(line, lineX, nameY);
            nameY += nameFm.getHeight();
        }
        g.setFont(oldFont);
    }

    private void drawCompactEmptyIdentity(Graphics2D g, FontMetrics fm, Rectangle card) {
        Font oldFont = g.getFont();
        Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f);
        g.setFont(titleFont);
        FontMetrics titleFm = g.getFontMetrics();
        String title = TextUtils.truncateToWidth("No current task", titleFm, card.width - 28);
        int titleY = card.y + (card.height - titleFm.getHeight()) / 2 + titleFm.getAscent();
        g.setColor(Color.WHITE);
        g.drawString(title, card.x + (card.width - titleFm.stringWidth(title)) / 2, titleY);
        g.setFont(oldFont);
    }

    private void drawCompactCurrentIdentity(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask current) {
        int pad = 14;
        int textW = card.width - pad * 2;
        int y = card.y + pad;

        y = Math.max(y, drawCompactBadges(g, fm, card, current) + 6);
        y = drawCompactTaskIcon(g, current, card.x + pad, y, textW) + 8;

        Font oldFont = g.getFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 17f);
        g.setFont(nameFont);
        FontMetrics nameFm = g.getFontMetrics();
        List<String> nameLines = TextUtils.wrapText(compactTaskTitle(current), nameFm, textW);
        int textY = y + nameFm.getAscent();
        g.setColor(P.UI_GOLD);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            String line = TextUtils.truncateToWidth(nameLines.get(i), nameFm, textW);
            int lineX = card.x + (card.width - nameFm.stringWidth(line)) / 2;
            g.drawString(line, lineX, textY);
            textY += nameFm.getHeight();
        }

        g.setFont(oldFont);
        Long ticks = plugin.getTaskTimeTicks(current);
        String time = ticks != null && ticks < 0
                ? "Completed before rolled"
                : compactFormatTicks(Math.round((ticks == null ? 0L : ticks) * 0.6));
        time = TextUtils.truncateToWidth(time, fm, textW);
        int timeX = card.x + (card.width - fm.stringWidth(time)) / 2;
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(time, timeX, textY + 2);
    }

    private int drawCompactBadges(Graphics2D g, FontMetrics fm, Rectangle card, XtremeTask task) {
        String sourceText = shortSource(task.getSource());
        String tierText = tierLabel(task.getTier());
        int gap = 5;
        int y = card.y + 8;
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics badgeFm = g.getFontMetrics();
        int sourceW = Math.max(24, badgeFm.stringWidth(sourceText) + 14);
        int tierW = Math.max(24, badgeFm.stringWidth(tierText) + 14);
        int x = card.x + card.width - 12 - sourceW - gap - tierW;
        g.setFont(oldFont);

        x += TaskRowsRenderer.drawSourceBadge(g, x, y, sourceText, P.UI_EDGE_DARK, P.UI_EDGE_LIGHT, P.UI_GOLD, P.UI_TEXT) + gap;
        TaskRowsRenderer.drawSourceBadge(g, x, y, tierText, P.UI_EDGE_DARK, P.UI_EDGE_LIGHT, P.UI_GOLD, P.UI_TEXT);
        return y + 18;
    }

    private int drawCompactTaskIcon(Graphics2D g, XtremeTask task, int x, int y, int maxW) {
        int iconSize = 44;
        int iconX = x + (maxW - iconSize) / 2;
        BufferedImage taskIcon = resolveTaskIcon(task);
        if (taskIcon != null) {
            g.drawImage(taskIcon, iconX, y, iconSize, iconSize, null);
        }
        return y + iconSize;
    }

    private String compactTaskTitle(XtremeTask task) {
        return task == null ? "" : task.getName();
    }

    private void drawCompactCenteredText(Graphics2D g, FontMetrics fm, Rectangle card, String text, Color color) {
        String drawText = TextUtils.truncateToWidth(text, fm, card.width - 28);
        int x = card.x + (card.width - fm.stringWidth(drawText)) / 2;
        int y = card.y + (card.height - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(color);
        g.drawString(drawText, x, y);
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
        g.setColor(withAlpha(P.UI_EDGE_LIGHT, hovered ? 95 : 55));
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
            drawSmallTooltip(g, fm, compactPanelMode ? "Full view" : "Compact view", panelModeToggleBounds);
        }
    }

    private void drawSmallTooltip(Graphics2D g, FontMetrics fm, String text, Rectangle anchor) {
        int padX = 6;
        int padY = 4;
        String label = text == null ? "" : text.replace('\n', ' ').trim();
        int textW = fm.stringWidth(label);
        int w = textW + padX * 2;
        int h = fm.getHeight() + padY * 2;
        int x = anchor.x;
        int y = anchor.y + anchor.height + 5;
        if (x + w > panelBounds.x + panelBounds.width - PANEL_PADDING) {
            x = panelBounds.x + panelBounds.width - PANEL_PADDING - w;
        }
        if (y + h > panelBounds.y + panelBounds.height - PANEL_PADDING) {
            y = anchor.y - h - 5;
        }

        g.setColor(new Color(75, 75, 75, 235));
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(new Color(155, 155, 155, 220));
        g.drawRoundRect(x, y, w, h, 4, 4);
        g.setColor(Color.WHITE);
        g.drawString(label, x + padX, y + padY + fm.getAscent());
    }

    private void resetCurrentLayoutBounds() {
        clearBounds(currentLayout.wikiButtonBounds, currentLayout.rollButtonBounds,
                currentLayout.completeButtonBounds, currentLayout.undoButtonBounds,
                currentLayout.rollSourceIconBounds, currentLayout.viewportBounds,
                currentLayout.scrollbarRailBounds, currentLayout.scrollbarThumbBounds);
        currentLayout.totalContentPx = 0;
    }

    private void clearFullPanelTabBounds() {
        clearBounds(currentTabBounds, tasksTabBounds, rulesTabBounds, taskListViewportBounds,
                taskScrollbarRailBounds, taskScrollbarThumbBounds);
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

    private void renderTaskDetailsIncompleteConfirm(Graphics2D g, FontMetrics fm)
    {
        int count = Math.max(1, selectedIncompleteTasksForDetails().size());
        String taskLabel = count == 1 ? "task" : "tasks";
        drawConfirmationPopup(
                g,
                fm,
                panelBounds,
                taskDetailsIncompleteConfirmBounds,
                taskDetailsIncompleteConfirmYesBounds,
                taskDetailsIncompleteConfirmNoBounds,
                "Mark " + count + " " + taskLabel + " incomplete?",
                UiText.get("overlay.confirm.clear_completion_times"));
    }

    private String syncReviewCompletionDateText(XtremeTask task)
    {
        CompletionInfo info = plugin.getCompletionInfo(task);
        if (info == null || info.timestamp <= 0)
        {
            return "";
        }
        return SYNC_REVIEW_COMPLETION_DATE_FORMAT.format(Instant.ofEpochMilli(info.timestamp));
    }

    private void handleTaskDetailsMarkIncompleteButton(XtremeTask task)
    {
        if (task == null || !isTaskCompletedForIncompleteAction(task))
        {
            return;
        }

        selectedTaskDetailsIncompleteTaskIds.clear();
        if (task.getId() != null)
        {
            selectedTaskDetailsIncompleteTaskIds.add(task.getId());
        }
        taskDetailsIncompleteConfirmCloseAfter = false;
        taskDetailsIncompleteConfirmOpen = true;
    }

    private void handleTaskDetailsInstanceMarkIncompleteButton(XtremeTask task)
    {
        if (task == null || task.getId() == null || !plugin.isTaskCompleted(task))
        {
            return;
        }

        selectedTaskDetailsIncompleteTaskIds.clear();
        selectedTaskDetailsIncompleteTaskIds.add(task.getId());
        taskDetailsIncompleteConfirmCloseAfter = false;
        taskDetailsIncompleteConfirmOpen = true;
    }

    private boolean isTaskCompletedForIncompleteAction(XtremeTask task)
    {
        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        return progress != null && progress.isGrouped()
                ? progress.isComplete()
                : plugin.isTaskCompleted(task);
    }

    private void closeTaskDetailsWithPendingIncompleteCheck()
    {
        closeTaskDetailsNow();
    }

    private void closeTaskDetailsNow()
    {
        taskDetailsPopup.close();
        selectedTaskDetailsIncompleteTaskIds.clear();
        closeTaskDetailsIncompleteConfirm();
    }

    private void confirmTaskDetailsIncompleteSelection()
    {
        List<XtremeTask> tasksToMarkIncomplete = selectedIncompleteTasksForDetails();
        if (!tasksToMarkIncomplete.isEmpty())
        {
            plugin.markSyncMismatchTasksIncompleteAndPersist(tasksToMarkIncomplete);
        }
        if (taskDetailsIncompleteConfirmCloseAfter)
        {
            closeTaskDetailsNow();
            return;
        }

        selectedTaskDetailsIncompleteTaskIds.clear();
        closeTaskDetailsIncompleteConfirm();
    }

    private List<XtremeTask> selectedIncompleteTasksForDetails()
    {
        XtremeTask task = taskDetailsPopup.task();
        if (task == null || selectedTaskDetailsIncompleteTaskIds.isEmpty())
        {
            return Collections.emptyList();
        }

        return resolveTasksForTask(task).stream()
                .filter(candidate -> candidate != null
                        && candidate.getId() != null
                        && selectedTaskDetailsIncompleteTaskIds.contains(candidate.getId()))
                .collect(Collectors.toList());
    }

    private boolean isTaskDetailsIncompleteConfirmOpen()
    {
        return taskDetailsIncompleteConfirmOpen;
    }

    private void closeTaskDetailsIncompleteConfirm()
    {
        selectedTaskDetailsIncompleteTaskIds.clear();
        taskDetailsIncompleteConfirmOpen = false;
        taskDetailsIncompleteConfirmCloseAfter = true;
        clearBounds(taskDetailsIncompleteConfirmBounds, taskDetailsIncompleteConfirmYesBounds,
                taskDetailsIncompleteConfirmNoBounds);
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
        clearBounds(syncMismatchReviewBounds, syncMismatchViewportBounds, syncMismatchCloseBounds,
                syncMismatchMarkAllBounds, syncMismatchApplyBounds, syncMismatchCancelBounds,
                syncMismatchConfirmBounds, syncMismatchConfirmYesBounds, syncMismatchConfirmNoBounds,
                syncMismatchScrollbarRailBounds, syncMismatchScrollbarThumbBounds);
        syncMismatchReviewOpen = false;
        syncReviewMode = SyncReviewMode.MISMATCH;
        syncMismatchReviewSource = null;
        syncMismatchApplyConfirmOpen = false;
        selectedSyncMismatchTaskIds.clear();
        syncReviewVisibleTasksCache.clear();
        synchronized (syncMismatchTaskBounds) {
            syncMismatchTaskBounds.clear();
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

        drawScrim(g, panelBounds, 135);

        int maxReviewW = 470;
        int w = Math.min(panelBounds.width - 36, maxReviewW);
        int h = Math.min(panelBounds.height - 56, 390);
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = panelBounds.y + (panelBounds.height - h) / 2;
        syncMismatchReviewBounds.setBounds(x, y, w, h);
        drawBevelBox(g, syncMismatchReviewBounds, POPUP_BG_SOFT);

        int pad = 12;
        int closeW = 28;
        int buttonH = ROW_HEIGHT + 8;
        syncMismatchCloseBounds.setBounds(x + w - pad - closeW, y + pad - 2, closeW, buttonH);
        UiDraw.drawCloseX(g, syncMismatchCloseBounds);

        boolean reviewingCompletionCandidates = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES;
        boolean hasCollectionLogReview = mismatches.stream()
                .anyMatch(task -> isCollectionLogSyncSource(task.getSource()));
        boolean hasCombatAchievementReview = mismatches.stream()
                .anyMatch(task -> task.getSource() == TaskSource.COMBAT_ACHIEVEMENT);
        int nextY = drawWrappedBlock(g, fm,
                syncReviewIntroMessage(reviewingCompletionCandidates, hasCollectionLogReview, hasCombatAchievementReview),
                x + pad, y + pad, w - pad * 3 - closeW, P.UI_GOLD) + fm.getHeight();
        boolean showCollectionLogRefreshHint = hasCollectionLogReview && !reviewingCompletionCandidates;
        nextY = drawWrappedBlock(g, fm, syncReviewInstruction(reviewingCompletionCandidates),
                x + pad, nextY, w - pad * 2, P.UI_TEXT_DIM);
        if (showCollectionLogRefreshHint)
        {
            g.setColor(P.UI_TEXT);
            String refreshHint = UiText.get("overlay.sync_review.refresh_hint");
            g.drawString(TextUtils.truncateToWidth(refreshHint, fm, w - pad * 2),
                    x + pad, nextY + fm.getAscent());
            nextY += fm.getHeight();
        }

        int headerTop = nextY + fm.getHeight() + 14;
        int footerH = buttonH + fm.getHeight() + 10;
        int listBottom = y + h - pad - footerH;
        Rectangle listFrame = new Rectangle(x + pad, 0, w - pad * 2, 0);
        int actionColumnW = 28;
        int reviewRowHeight = ROW_HEIGHT + 8;
        int rowCheckboxSize = Math.min(20, reviewRowHeight - 4);
        int listTop = headerTop + fm.getHeight() + 7;
        syncMismatchViewportBounds.setBounds(listFrame.x, listTop, listFrame.width, Math.max(0, listBottom - listTop));
        int rowBlock = reviewRowHeight + LIST_ROW_SPACING;
        int visible = Math.max(1, syncMismatchViewportBounds.height / rowBlock);
        int scrollBarW = 6;
        int scrollBarGap = 3;
        boolean needsScrollbar = mismatches.size() > visible;
        int scrollLaneW = needsScrollbar ? scrollBarW + scrollBarGap : 0;
        int rowW = Math.max(0, syncMismatchViewportBounds.width - scrollLaneW);
        int actionColumnX = listFrame.x + rowW - actionColumnW;
        g.setColor(P.UI_TEXT_DIM);
        g.drawString("Task", listFrame.x, headerTop + fm.getAscent());

        int selectedVisibleCount = selectedVisibleSyncMismatchCount(mismatches);
        int selectableVisibleCount = selectableVisibleSyncMismatchCount(mismatches);
        boolean allMismatchTasksSelected = selectableVisibleCount > 0 && selectedVisibleCount >= selectableVisibleCount;
        String actionHeader = reviewingCompletionCandidates ? "Mark complete?" : "Mark incomplete?";
        int actionHeaderRight = actionColumnX + (actionColumnW - rowCheckboxSize) / 2 - 6;
        int actionHeaderMaxW = Math.max(0, actionHeaderRight - listFrame.x - 8);
        String actionHeaderText = TextUtils.truncateToWidth(actionHeader, fm, actionHeaderMaxW);
        g.drawString(actionHeaderText, actionHeaderRight - fm.stringWidth(actionHeaderText), headerTop + fm.getAscent());
        syncMismatchMarkAllBounds.setBounds(
                actionColumnX + (actionColumnW - rowCheckboxSize) / 2,
                headerTop + Math.max(0, (fm.getHeight() - rowCheckboxSize) / 2) - 4,
                rowCheckboxSize,
                rowCheckboxSize);
        drawSyncMismatchCheckbox(g, syncMismatchMarkAllBounds, allMismatchTasksSelected);
        if (!allMismatchTasksSelected)
        {
            drawSyncMismatchHeaderInnerBorder(g, syncMismatchMarkAllBounds);
        }

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

        g.setColor(P.ROW_LINE);
        g.drawLine(syncMismatchViewportBounds.x, listTop - 3,
                syncMismatchViewportBounds.x + rowW, listTop - 3);

        synchronized (syncMismatchTaskBounds) {
            syncMismatchTaskBounds.clear();
        }

        Shape oldClip = g.getClip();
        g.setClip(syncMismatchViewportBounds);
        int maxOffset = Math.max(0, mismatches.size() - visible);
        if (syncMismatchScroll.offsetRows > maxOffset)
        {
            syncMismatchScroll.offsetRows = maxOffset;
        }

        int end = Math.min(mismatches.size(), syncMismatchScroll.offsetRows + visible + 1);
        int rowY = syncMismatchViewportBounds.y + 2;
        for (int i = syncMismatchScroll.offsetRows; i < end; i++)
        {
            XtremeTask task = mismatches.get(i);
            boolean currentTask = isCurrentTask(task);
            boolean selected = !currentTask && selectedSyncMismatchTaskIds.contains(task.getId());
            Rectangle row = new Rectangle(syncMismatchViewportBounds.x, rowY,
                    rowW, reviewRowHeight);

            Color rowFill = i % 2 == 0 ? new Color(62, 50, 34, 235) : new Color(53, 43, 31, 235);
            if (currentTask)
            {
                rowFill = i % 2 == 0 ? new Color(48, 43, 36, 215) : new Color(43, 38, 33, 215);
            }
            g.setColor(rowFill);
            g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(new Color(30, 24, 18, 170));
            g.fillRect(actionColumnX, row.y + 1, actionColumnW, row.height - 1);
            g.setColor(P.ROW_LINE);
            g.drawRect(row.x, row.y, row.width, row.height);
            g.drawLine(actionColumnX, row.y, actionColumnX, row.y + row.height);

            FontMetrics badgeFm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
            int rowTextX = row.x + 8;
            int badgeY = row.y + (row.height - (ROW_HEIGHT + 4)) / 2;
            int sourceBadgeW = drawSyncReviewRowBadge(g, badgeFm, rowTextX, badgeY, shortSource(task.getSource()));
            rowTextX += sourceBadgeW + 4;
            if (task.getTier() != null)
            {
                int tierBadgeW = drawSyncReviewRowBadge(g, badgeFm, rowTextX, badgeY, tierLabel(task.getTier()));
                rowTextX += tierBadgeW + 6;
            }

            String completedDate = syncReviewCompletionDateText(task);
            Font oldFont = g.getFont();
            Font dateFont = oldFont.deriveFont(Font.ITALIC);
            FontMetrics dateFm = g.getFontMetrics(dateFont);
            int dateGap = completedDate.isEmpty() ? 0 : 8;
            int dateW = completedDate.isEmpty() ? 0 : dateFm.stringWidth(completedDate);
            String label = TextUtils.truncateToWidth(syncReviewRowLabel(task), fm,
                    actionColumnX - rowTextX - dateW - dateGap - 8);
            int textY = row.y + ((row.height - fm.getHeight()) / 2) + fm.getAscent();
            g.setColor(currentTask ? P.UI_TEXT_DIM : P.UI_TEXT);
            g.drawString(label, rowTextX, textY);
            if (!completedDate.isEmpty())
            {
                g.setFont(dateFont);
                g.setColor(P.UI_TEXT_DIM);
                g.drawString(completedDate, rowTextX + fm.stringWidth(label) + dateGap, textY);
                g.setFont(oldFont);
            }
            Rectangle btn = new Rectangle(
                    actionColumnX + (actionColumnW - rowCheckboxSize) / 2,
                    row.y + (row.height - rowCheckboxSize) / 2,
                    rowCheckboxSize,
                    rowCheckboxSize);
            Rectangle hitTarget = new Rectangle(actionColumnX, row.y, actionColumnW, row.height);
            synchronized (syncMismatchTaskBounds) {
                if (!currentTask)
                {
                    syncMismatchTaskBounds.put(task, hitTarget);
                }
            }
            drawSyncMismatchCheckbox(g, btn, selected, currentTask);
            rowY += rowBlock;
        }
        g.setClip(oldClip);

        if (needsScrollbar)
        {
            UiDraw.drawScrollbar(g, new Rectangle(syncMismatchViewportBounds.x + syncMismatchViewportBounds.width - scrollBarW,
                    syncMismatchViewportBounds.y, scrollBarW, syncMismatchViewportBounds.height),
                    mismatches.size(), visible, syncMismatchScroll.offsetRows, syncMismatchScrollbarRailBounds,
                    syncMismatchScrollbarThumbBounds, P.UI_EDGE_DARK, P.UI_EDGE_LIGHT, P.UI_GOLD);
            syncMismatchScrollbarRailBounds.setBounds(
                    syncMismatchScrollbarRailBounds.x - scrollBarGap,
                    syncMismatchScrollbarRailBounds.y,
                    syncMismatchScrollbarRailBounds.width + scrollBarGap,
                    syncMismatchScrollbarRailBounds.height);
            syncMismatchScrollbarThumbBounds.setBounds(
                    syncMismatchScrollbarThumbBounds.x - scrollBarGap,
                    syncMismatchScrollbarThumbBounds.y,
                    syncMismatchScrollbarThumbBounds.width + scrollBarGap,
                    syncMismatchScrollbarThumbBounds.height);
        }
        else
        {
            clearBounds(syncMismatchScrollbarRailBounds, syncMismatchScrollbarThumbBounds);
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

        if (syncMismatchApplyConfirmOpen)
        {
            renderSyncMismatchApplyConfirm(g, fm);
        }
    }

    private String syncReviewIntroMessage(boolean completionCandidates, boolean hasCollectionLogReview, boolean hasCombatAchievementReview)
    {
        String prefix = hasCollectionLogReview && hasCombatAchievementReview
                ? UiText.get("overlay.sync_review.completion_prefix.collection_and_ca")
                : hasCollectionLogReview
                ? UiText.get("overlay.sync_review.completion_prefix.collection")
                : UiText.get("overlay.sync_review.completion_prefix.ca");
        String suffix = completionCandidates
                ? UiText.get("overlay.sync_review.candidates_suffix")
                : UiText.get("overlay.sync_review.mismatch_suffix");
        return prefix + " " + suffix;
    }

    private String syncReviewInstruction(boolean completionCandidates)
    {
        return UiText.format("overlay.sync_review.instruction", completionCandidates ? "complete" : "incomplete");
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
        List<XtremeTask> snapshot = tasks == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(tasks));
        syncReviewVisibleTasksCache.put(cacheKey, snapshot);
        return snapshot;
    }

    private String syncReviewVisibleTasksCacheKey()
    {
        return "mode=" + syncReviewMode
                + "|source=" + syncMismatchReviewSource
                + "|taskState=" + plugin.getTaskListRenderStateHash()
                + "|clState=" + plugin.getCollectionLogStateVersion()
                + "|lastSync=" + plugin.getLastSyncResultAtLocalTime()
                + "|lastCa=" + plugin.getLastCombatAchievementSyncResultAtLocalTime();
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

            if (!isCurrentTask(task) && selectedSyncMismatchTaskIds.contains(task.getId()))
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

            if (isCurrentTask(task))
            {
                continue;
            }

            count++;
        }
        return count;
    }

    private int drawSyncReviewRowBadge(Graphics2D g, FontMetrics fm, int x, int y, String text)
    {
        String label = text == null || text.trim().isEmpty() ? "?" : text.trim();
        int width = Math.max(24, fm.stringWidth(label) + 14);
        TaskRowsRenderer.drawSourceBadge(g, x, y, label, P.UI_EDGE_DARK, P.UI_EDGE_LIGHT, P.UI_GOLD, P.UI_TEXT);
        return width;
    }

    private void drawSyncMismatchCheckbox(Graphics2D g, Rectangle box, boolean checked)
    {
        drawSyncMismatchCheckbox(g, box, checked, false);
    }

    private void drawSyncMismatchCheckbox(Graphics2D g, Rectangle box, boolean checked, boolean disabled)
    {
        drawBevelBox(g, box, disabled ? P.BTN_DISABLED_BG : checked ? P.BTN_ENABLED_BG : P.INPUT_BG);
        g.setColor(disabled ? new Color(110, 104, 96, 170) : checked ? P.UI_GOLD : P.UI_TEXT_DIM);
        g.drawRect(box.x + 1, box.y + 1, box.width - 3, box.height - 3);
        if (checked && !disabled)
        {
            UiDraw.drawCheckmark(g, box, 5);
        }
    }

    private void drawSyncMismatchHeaderInnerBorder(Graphics2D g, Rectangle box)
    {
        g.setColor(P.UI_GOLD);
        g.drawRect(box.x + 1, box.y + 1, box.width - 3, box.height - 3);
    }

    private String syncReviewRowLabel(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        String currentTaskSuffix = isCurrentTask(task) ? " (Current task)" : "";

        if (isDecoratedSequenceTaskName(task.getName()))
        {
            return task.getName() + currentTaskSuffix;
        }

        List<XtremeTask> group = plugin.getTaskGroupInstances(task);
        if (group == null || group.size() <= 1)
        {
            return task.getName() + currentTaskSuffix;
        }

        TaskGroupProgress progress = plugin.getTaskGroupProgress(task);
        if (progress == null || !progress.isGrouped())
        {
            return task.getName() + currentTaskSuffix;
        }

        String sequenceSuffix = collectionLogSequenceSuffix(task);
        if (!sequenceSuffix.isEmpty())
        {
            return task.getName() + currentTaskSuffix + ": " + sequenceSuffix;
        }

        int instanceOrdinal = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES
                ? instanceOrdinalInGroup(group, task)
                : completedInstanceOrdinalInGroup(group, task);
        if (instanceOrdinal <= 0)
        {
            return task.getName() + currentTaskSuffix + ": " + progress.label();
        }
        return task.getName() + currentTaskSuffix + ": " + instanceOrdinal + "/" + group.size();
    }

    private String collectionLogSequenceSuffix(XtremeTask task)
    {
        String suffix = plugin.getCollectionLogSequenceStepLabel(task);
        return suffix == null ? "" : suffix.trim();
    }

    private boolean isCurrentTask(XtremeTask task)
    {
        XtremeTask current = plugin.getCurrentTask();
        String currentId = current == null ? null : current.getId();
        String taskId = task == null ? null : task.getId();
        return currentId != null && taskId != null && currentId.equals(taskId);
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
        int count = selectedVisibleSyncMismatchCount(visibleSyncMismatchTasks());
        boolean reviewingCompletionCandidates = syncReviewMode == SyncReviewMode.COMPLETION_CANDIDATES;
        String message = reviewingCompletionCandidates
                ? "Mark " + count + " task(s) complete?"
                : "Mark " + count + " task(s) incomplete?";
        String warning = reviewingCompletionCandidates
                ? UiText.get("overlay.sync_review.confirm_sync_source")
                : UiText.get("overlay.confirm.clear_completion_times");
        drawConfirmationPopup(
                g,
                fm,
                syncMismatchReviewBounds,
                syncMismatchConfirmBounds,
                syncMismatchConfirmYesBounds,
                syncMismatchConfirmNoBounds,
                message,
                warning);
    }

    private void drawConfirmationPopup(
            Graphics2D g,
            FontMetrics fm,
            Rectangle parentBounds,
            Rectangle confirmBounds,
            Rectangle yesBounds,
            Rectangle noBounds,
            String message,
            String warning)
    {
        drawScrim(g, parentBounds, 115);

        int w = Math.min(parentBounds.width - 50,
                Math.max(fm.stringWidth(message), fm.stringWidth(warning)) + 36);
        int h = 104;
        int x = parentBounds.x + (parentBounds.width - w) / 2;
        int y = parentBounds.y + (parentBounds.height - h) / 2;
        confirmBounds.setBounds(x, y, w, h);
        drawBevelBox(g, confirmBounds, POPUP_BG);

        int firstY = y + 18 + fm.getAscent();
        g.setColor(P.UI_TEXT);
        g.drawString(message, x + (w - fm.stringWidth(message)) / 2, firstY);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(warning, x + (w - fm.stringWidth(warning)) / 2, firstY + fm.getHeight() + 2);

        int buttonW = 70;
        int buttonH = ROW_HEIGHT + 8;
        int gap = 8;
        int buttonY = y + h - buttonH - 10;
        layoutButtonPair(yesBounds, noBounds, x, w, buttonY, buttonW, buttonH, gap);
        buttonRenderer.drawPlainButton(g, yesBounds, "Confirm", P.BTN_ENABLED_BG, P.UI_TEXT, P.UI_GOLD);
        buttonRenderer.drawPlainButton(g, noBounds, "Cancel", P.BTN_DISABLED_BG);
    }

    private void renderMarkAllIncompleteConfirm(Graphics2D g, FontMetrics fm)
    {
        drawScrim(g, panelBounds, 125);

        XtremeTask pending = pendingMarkAllIncompleteTask;
        boolean multipleTasks = pending != null && pendingMarkAllIncompleteGroupMode;
        String message = multipleTasks
                ? UiText.get("overlay.mark_incomplete.all_instances")
                : UiText.get("overlay.mark_incomplete.single");
        String warning = multipleTasks
                ? UiText.get("overlay.mark_incomplete.all_warning")
                : UiText.get("overlay.mark_incomplete.single_warning");
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

        drawBevelBox(g, markAllIncompleteConfirmBounds, POPUP_BG_SOFT);

        int buttonW = 70;
        int buttonH = ROW_HEIGHT + 6;
        int gap = 8;
        int buttonY = y + h - buttonH - 10;
        layoutButtonPair(markAllIncompleteYesBounds, markAllIncompleteNoBounds, x, w, buttonY, buttonW, buttonH, gap);

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
            g.setColor(withAlpha(P.UI_TEXT_DIM, 150));
            g.drawRect(checkboxX, boxY, boxSize, boxSize);
            if (markIncompleteDontShowChecked)
            {
                g.setColor(P.UI_GOLD);
                UiDraw.drawCheckmark(g, new Rectangle(checkboxX, boxY, boxSize, boxSize), 2);
            }

            g.setColor(P.UI_TEXT_DIM);
            g.drawString(dontShowText, checkboxX + boxSize + 6, checkboxY + ((checkboxH - fm.getHeight()) / 2) + fm.getAscent());
        }
        else
        {
            clearBounds(markIncompleteDontShowBounds);
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
                withAlpha(P.UI_TEXT_DIM, 150)
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
                this::resolvePrerequisiteMarkerImage,
                this::buildCollectionLogRequirementPreview,
                this::getCachedItemImage,
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
                plugin.isCurrentTaskCompletionCriteriaMet()
        );
    }

    private static String formatShortDuration(long seconds) {
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
                hoverY
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
                rulesSubTab,
                plugin.getLastSyncResult(),
                plugin.getLastSyncResultAtLocalTime(),
                plugin.getSyncCompletionCandidateTasks(TaskSource.COMBAT_ACHIEVEMENT).size(),
                plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG).size()
        );

        rulesLayout.viewportBounds.setBounds(layout.viewportBounds);
        rulesLayout.githubReadmeLinkBounds.setBounds(layout.githubReadmeLinkBounds);
        rulesLayout.syncProgressButtonBounds.setBounds(layout.syncProgressButtonBounds);
        rulesLayout.syncCaFoundReviewButtonBounds.setBounds(layout.syncCaFoundReviewButtonBounds);
        rulesLayout.subTabRulesBounds.setBounds(layout.subTabRulesBounds);
        rulesLayout.subTabDataSyncsBounds.setBounds(layout.subTabDataSyncsBounds);


        if (rulesLayout.syncProgressButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncProgressButtonBounds, "Sync CAs + ADs", P.BTN_DISABLED_BG);
        }
        if (rulesLayout.syncCaFoundReviewButtonBounds.width > 0) {
            buttonRenderer.drawPlainButton(g, rulesLayout.syncCaFoundReviewButtonBounds, "Review",
                    P.BTN_ENABLED_BG, P.UI_TEXT, new Color(111, 190, 92));
        }
    }

    // --------- rolling line logic ---------
    private String computeCurrentLineForRender(XtremeTask current, boolean currentCompleted, FontMetrics fm) {
        final int maxW = panelInnerTextMaxWidth();

        if (!animations.isRolling()) {
            rollingDisplayTier = null;
            rollingDisplayPool = Collections.emptyList();
            if (current == null) {
                return TextUtils.truncateToWidth("Click \"Roll task\" to get a task", fm, maxW);
            }

            return TextUtils.truncateToWidth(current.getName(), fm, maxW); // prefix drawn by renderer
        }

        TaskTier tier = rollingDisplayTier != null ? rollingDisplayTier : (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = !rollingDisplayPool.isEmpty() && rollingDisplayTier == tier
                ? rollingDisplayPool
                : getTasksForTier(tier);
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

        selectedTaskDetailsIncompleteTaskIds.clear();
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
            requestRollTask();
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
        taskListView.resetAfterQueryChange(activeTierTab, tasks, plugin::isTaskCompleted);
    }

    private void prepareTasksTabOnOpen()
    {
        TaskTier tier = null;
        XtremeTask cur = plugin.getCurrentTask();
        if (cur != null)
        {
            tier = cur.getTier();
        }
        if (tier == null)
        {
            tier = plugin.getCurrentTier();
        }
        if (tier != null)
        {
            activeTierTab = tier;
        }

        if (!tasksSourceFilterInitialized)
        {
            tasksSourceFilterInitialized = true;
            XtremeTaskerConfig.RollSourceFilter rsf = plugin.getRollSourceFilter();
            if (rsf == XtremeTaskerConfig.RollSourceFilter.CA_ONLY)
            {
                taskQuery.setOnlySource(TaskListQuery.SourceFilter.CA);
            }
            else if (rsf == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY)
            {
                taskQuery.setCollectionLogAndDiarySources();
            }
            else
            {
                taskQuery.selectAllSources();
            }
        }

        resetTaskListViewAfterQueryChange();
    }

    // --------- data + pipeline ---------
    private List<XtremeTask> getTasksForTier(TaskTier tier) {
        List<XtremeTask> out = new ArrayList<>();
        for (XtremeTask t : plugin.getTasks()) {
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
        List<XtremeTask> sorted = TaskListPipeline.apply(
                base,
                taskQuery,
                plugin::isTaskCompleted,
                plugin::isNewTask,
                plugin::getCompletionInfo,
                plugin::getTaskTimeTicks);
        List<XtremeTask> result = useCondensedTaskRows() ? TaskGroupUtils.collapsePreservingOrder(sorted) : sorted;
        List<XtremeTask> immutableResult = Collections.unmodifiableList(new ArrayList<>(result));
        sortedTaskListCache.put(cacheKey, immutableResult);
        logSlowTaskListBuild(startNanos, tier, base.size(), immutableResult.size());
        return immutableResult;
    }

    private boolean useCondensedTaskRows()
    {
        return plugin.condenseRepeatedTasks();
    }

    private String sortedTaskListCacheKey(TaskTier tier)
    {
        return "tier=" + tier
                + "|scope=" + taskQuery.tierScope
                + "|search=" + (taskQuery.searchText == null ? "" : taskQuery.searchText)
                + "|ca=" + taskQuery.sourceCASelected
                + "|cl=" + taskQuery.sourceClogsSelected
                + "|da=" + taskQuery.sourceDasSelected
                + "|status=" + taskQuery.statusFilter
                + "|sort=" + taskQuery.sortColumn
                + "|dir=" + taskQuery.sortDirection
                + "|showDate=" + taskQuery.showDateCompletedColumn
                + "|showSpent=" + taskQuery.showTimeSpentColumn
                + "|newOnly=" + taskQuery.showNewTasksFilter
                + "|condensed=" + useCondensedTaskRows()
                + "|state=" + plugin.getTaskListRenderStateHash()
                + "|timerState=" + taskListTimerCacheState();
    }

    private int taskListTimerCacheState()
    {
        return taskQuery.sortColumn == TaskListQuery.SortColumn.SPENT
                && taskQuery.sortDirection != TaskListQuery.SortDirection.OFF
                ? plugin.getTaskListTimerStateVersion()
                : 0;
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
        log.debug("Slow Xtreme Tasker panel render: tab={}, detailsOpen={}, elapsed={}ms",
                activeTab, taskDetailsPopup.isOpen(), elapsedNanos / 1_000_000L);
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
                if (!open) {
                    cancelPendingRollExecution();
                }
            }

            @Override
            public boolean isCompactPanelMode() {
                return compactPanelMode;
            }

            @Override
            public void setCompactPanelMode(boolean compact) {
                recenterPanelForMode(compact);
                compactPanelMode = compact;
                taskDetailsPopup.close();
                syncMismatchReviewOpen = false;
                currentScroll.reset();
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
            public OverlayInputAccess.MainTab activeTab() {
                return OverlayInputAccess.MainTab.valueOf(activeTab.name());
            }

            @Override
            public void setActiveTab(OverlayInputAccess.MainTab tab) {
                activeTab = XtremeTaskerOverlay.MainTab.valueOf(tab.name());
                if (activeTab == XtremeTaskerOverlay.MainTab.TASKS)
                {
                    prepareTasksTabOnOpen();
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
            public Point toPanelLogicalPoint(Point point) {
                if (point == null || panelScale == 1.0) {
                    return point;
                }
                int x = panelInputAnchorX + (int) Math.round((point.x - panelInputAnchorX) / panelScale);
                int y = panelInputAnchorY + (int) Math.round((point.y - panelInputAnchorY) / panelScale);
                return new Point(x, y);
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
                }
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
                }
            }

            @Override
            public void closeSyncMismatchReview() {
                syncMismatchReviewOpen = false;
                syncReviewMode = SyncReviewMode.MISMATCH;
                syncMismatchReviewSource = null;
                selectedSyncMismatchTaskIds.clear();
                syncMismatchApplyConfirmOpen = false;
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
            public TaskListScrollController currentScroll() {
                return currentScroll;
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
            public void requestRollTask() {
                XtremeTaskerOverlay.this.requestRollTask();
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
            public boolean isTaskDetailsOpen() {
                return taskDetailsPopup.isOpen();
            }

            @Override
            public void openTaskDetails(XtremeTask task) {
                selectedTaskDetailsIncompleteTaskIds.clear();
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
            public Rectangle taskDetailsMarkIncompleteBounds() {
                return taskDetailsPopup.markIncompleteBounds();
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
            public Map<XtremeTask, Rectangle> taskDetailsInstanceRemoveBounds() {
                return taskDetailsPopup.instanceRemoveBounds();
            }

            @Override
            public void handleTaskDetailsMarkIncompleteButton(XtremeTask task) {
                XtremeTaskerOverlay.this.handleTaskDetailsMarkIncompleteButton(task);
            }

            @Override
            public void handleTaskDetailsInstanceMarkIncompleteButton(XtremeTask task) {
                XtremeTaskerOverlay.this.handleTaskDetailsInstanceMarkIncompleteButton(task);
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
                clearBounds(markAllIncompleteConfirmBounds, markAllIncompleteYesBounds,
                        markAllIncompleteNoBounds, markIncompleteDontShowBounds);
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
            public TaskListScrollController syncMismatchScroll() {
                return syncMismatchScroll;
            }

            @Override
            public int syncMismatchRowBlock() {
                return scaleInputValue(ROW_HEIGHT + 8 + LIST_ROW_SPACING);
            }

            @Override
            public int syncMismatchVisibleTaskCount() {
                return visibleSyncMismatchTasks().size();
            }

            @Override
            public boolean isSyncMismatchTaskSelected(XtremeTask task) {
                return task != null
                        && task.getId() != null
                        && !isCurrentTask(task)
                        && selectedSyncMismatchTaskIds.contains(task.getId());
            }

            @Override
            public void toggleSyncMismatchTaskSelected(XtremeTask task) {
                if (task == null || task.getId() == null || isCurrentTask(task)) return;
                if (!selectedSyncMismatchTaskIds.remove(task.getId())) {
                    selectedSyncMismatchTaskIds.add(task.getId());
                }
                syncMismatchApplyConfirmOpen = false;
            }

            @Override
            public void selectAllSyncMismatchTasks() {
                for (XtremeTask task : visibleSyncMismatchTasks()) {
                    if (task == null || task.getId() == null || isCurrentTask(task)) {
                        continue;
                    }
                    selectedSyncMismatchTaskIds.add(task.getId());
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
                    if (!isCurrentTask(task)
                            && selectedSyncMismatchTaskIds.contains(task.getId())
                            && seen.add(task.getId())) {
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

    private void scalePanelInputBounds(AffineTransform transform) {
        if (transform == null || transform.isIdentity()) {
            return;
        }

        transformRects(transform,
                panelBounds, panelDragBarBounds, panelCloseBounds, panelModeToggleBounds,
                currentTabBounds, tasksTabBounds, rulesTabBounds,
                taskListViewportBounds, taskScrollbarRailBounds, taskScrollbarThumbBounds,
                markAllIncompleteConfirmBounds, markAllIncompleteYesBounds, markAllIncompleteNoBounds,
                markIncompleteDontShowBounds, syncMismatchReviewBounds, syncMismatchViewportBounds,
                syncMismatchCloseBounds, syncMismatchMarkAllBounds, syncMismatchApplyBounds,
                syncMismatchCancelBounds, syncMismatchConfirmBounds, syncMismatchConfirmYesBounds,
                syncMismatchConfirmNoBounds, syncMismatchScrollbarRailBounds, syncMismatchScrollbarThumbBounds,
                taskDetailsIncompleteConfirmBounds,
                taskDetailsIncompleteConfirmYesBounds, taskDetailsIncompleteConfirmNoBounds);
        transformCurrentLayoutBounds(transform);
        transformRulesLayoutBounds(transform);
        transformRectMap(taskRowBounds, transform);
        transformRectMap(taskCheckboxBounds, transform);
        transformRectMap(syncMismatchTaskBounds, transform);
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

    private void transformCurrentLayoutBounds(AffineTransform transform) {
        transformRects(transform,
                currentLayout.wikiButtonBounds, currentLayout.rollButtonBounds,
                currentLayout.completeButtonBounds, currentLayout.undoButtonBounds,
                currentLayout.rollSourceIconBounds, currentLayout.viewportBounds,
                currentLayout.scrollbarRailBounds, currentLayout.scrollbarThumbBounds);
    }

    private void transformRulesLayoutBounds(AffineTransform transform) {
        transformRects(transform,
                rulesLayout.viewportBounds,
                rulesLayout.githubReadmeLinkBounds,
                rulesLayout.syncProgressButtonBounds, rulesLayout.syncCaFoundReviewButtonBounds,
                rulesLayout.subTabRulesBounds, rulesLayout.subTabDataSyncsBounds);
    }

    private void transformRectMap(Map<?, Rectangle> map, AffineTransform transform) {
        synchronized (map) {
            for (Rectangle r : map.values()) {
                transformRect(r, transform);
            }
        }
    }

    private void transformRects(AffineTransform transform, Rectangle... rects)
    {
        for (Rectangle rect : rects)
        {
            transformRect(rect, transform);
        }
    }

    private void transformRect(Rectangle r, AffineTransform transform) {
        if (r == null || r.width <= 0 || r.height <= 0) {
            return;
        }

        Point2D p1 = transform.transform(new Point2D.Double(r.x, r.y), null);
        Point2D p2 = transform.transform(new Point2D.Double(r.x + r.width, r.y), null);
        Point2D p3 = transform.transform(new Point2D.Double(r.x, r.y + r.height), null);
        Point2D p4 = transform.transform(new Point2D.Double(r.x + r.width, r.y + r.height), null);

        double minX = Math.min(Math.min(p1.getX(), p2.getX()), Math.min(p3.getX(), p4.getX()));
        double minY = Math.min(Math.min(p1.getY(), p2.getY()), Math.min(p3.getY(), p4.getY()));
        double maxX = Math.max(Math.max(p1.getX(), p2.getX()), Math.max(p3.getX(), p4.getX()));
        double maxY = Math.max(Math.max(p1.getY(), p2.getY()), Math.max(p3.getY(), p4.getY()));

        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);
        int w = Math.max(1, (int) Math.ceil(maxX) - x);
        int h = Math.max(1, (int) Math.ceil(maxY) - y);
        r.setBounds(x, y, w, h);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        buttonRenderer.drawBevelBox(g, r, fill);
    }

    private void drawScrim(Graphics2D g, Rectangle r, int alpha)
    {
        g.setColor(new Color(0, 0, 0, alpha));
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    private int drawWrappedBlock(Graphics2D g, FontMetrics fm, String text, int x, int top, int maxW, Color color)
    {
        g.setColor(color);
        for (String line : TextUtils.wrapText(text, fm, maxW))
        {
            g.drawString(line, x, top + fm.getAscent());
            top += fm.getHeight();
        }
        return top;
    }

    private static void clearBounds(Rectangle... bounds)
    {
        for (Rectangle bound : bounds)
        {
            bound.setBounds(0, 0, 0, 0);
        }
    }

    private void layoutButtonPair(Rectangle left, Rectangle right, int x, int w, int y, int buttonW, int buttonH, int gap)
    {
        int buttonsW = buttonW * 2 + gap;
        left.setBounds(x + (w - buttonsW) / 2, y, buttonW, buttonH);
        right.setBounds(left.x + buttonW + gap, y, buttonW, buttonH);
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
            for (XtremeTask t : plugin.getTasks()) {
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
