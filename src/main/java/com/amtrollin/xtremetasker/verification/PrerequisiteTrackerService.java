package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import lombok.NonNull;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class PrerequisiteTrackerService
{
    private static final Pattern SKILL_PREREQ_PATTERN = Pattern.compile("^(\\d+)\\s+([A-Za-z][A-Za-z\\- ]+)$");
    private static final Pattern SKILL_PREREQ_PLUS_PATTERN = Pattern.compile("^(\\d+)\\+\\s+([A-Za-z][A-Za-z\\- ]+)$");
    private static final Pattern QUEST_PREREQ_PATTERN = Pattern.compile("^(.+?)\\s+quest$", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_QUEST_PREREQ_PATTERN = Pattern.compile("^start\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBQUEST_PREREQ_PATTERN = Pattern.compile("^(.+?)\\s+subquest$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINIQUEST_PREREQ_PATTERN = Pattern.compile("^(.+?)\\s+miniquest$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARBARIAN_FIREMAKING_PART_1_PATTERN = Pattern.compile(
        "^part\\s+1\\s+of\\s+barbarian\\s+firemaking$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUEST_POINTS_PATTERN = Pattern.compile("^(\\d+)\\s+quest\\s+points?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMBAT_LEVEL_PATTERN = Pattern.compile("^(\\d+)\\s+combat(?:\\s+level)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_LEVEL_PATTERN = Pattern.compile("^(\\d+)\\s+total\\s+level$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVOR_PERCENT_PATTERN = Pattern.compile("^(?:reach\\s+)?(\\d+)%\\s+(.+?)\\s+favou?r$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVOR_PREREQ_PATTERN = Pattern.compile("^(?:reach\\s+)?(.+?)\\s+favou?r$", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAGEX_ACCOUNT_PATTERN = Pattern.compile("^own\\s+a\\s+jagex\\s+account$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS_PATTERN = Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb]?)\\s*(?:coins?|gp)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POINTS_PATTERN = Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)([kmb]?)\\s+(.+?)\\s+points?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIARY_PREREQ_PATTERN = Pattern.compile(
        "^complete\\s+the\\s+(.+?)\\s+(easy|medium|hard|elite)\\s+diary$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMBINED_LEVEL_PATTERN = Pattern.compile(
        "^(?:a\\s+)?combined\\s+([A-Za-z][A-Za-z\\- ]+?)\\s+and\\s+([A-Za-z][A-Za-z\\- ]+?)\\s+level\\s+of\\s+(?:at\\s+least\\s+)?(\\d+)(?:\\s*[,\\(]?\\s*or\\s+(?:level\\s+)?(\\d+)\\s+in\\s+(?:either(?:\\s+skill)?|one)\\)?)?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRACKABLE_SKILL_SPAN_PATTERN = Pattern.compile(
        "\\b(\\d+)\\+?\\s+([A-Za-z][A-Za-z\\- ]*?)(?=\\s*(?:\\(|\\bor\\b|\\band\\b|,|;|$))",
        Pattern.CASE_INSENSITIVE
    );

    private final Map<String, Skill> skillsByName = new HashMap<>();
    private final Map<String, Quest> questsByName = new HashMap<>();
    private final Map<String, Integer> varbitsByName = new HashMap<>();
    private final IntUnaryOperator varbitReader;
    private final ToIntFunction<Skill> skillLevelReader;

    @Inject
    private Client client;

    public PrerequisiteTrackerService()
    {
        this(null, null);
    }

    PrerequisiteTrackerService(IntUnaryOperator varbitReader)
    {
        this(varbitReader, null);
    }

    PrerequisiteTrackerService(IntUnaryOperator varbitReader, ToIntFunction<Skill> skillLevelReader)
    {
        registerSkills();
        registerQuests();
        registerVarbits();
        this.varbitReader = varbitReader;
        this.skillLevelReader = skillLevelReader;
    }

    public List<PrerequisiteStatus> evaluate(@NonNull String prereqs)
    {
        List<PrerequisiteStatus> out = new ArrayList<>();
        for (String token : splitPrereqs(prereqs))
        {
            String text = token.trim();
            if (text.isEmpty())
            {
                continue;
            }

            out.add(new PrerequisiteStatus(text, isSatisfied(text), checkSpans(text), skillIcons(text), markerIcons(text)));
        }
        return out;
    }

    /**
     * Checks whether an achievement diary has been completed using the region slug
     * (e.g. "ardougne", "kourend-and-kebos") and difficulty (e.g. "easy", "hard", "elite")
     * as stored in the verification block of tasks.json.
     */
    public boolean isDiaryComplete(String region, String difficulty)
    {
        if (region == null || difficulty == null)
        {
            return false;
        }
        String regionKey = toDiaryRegionKey(region);
        if (regionKey == null)
        {
            return false;
        }
        String difficultyKey = difficulty.toUpperCase(Locale.ROOT);
        Integer varbitId = varbitsByName.get("DIARY_" + regionKey + "_" + difficultyKey);
        if (varbitId == null)
        {
            return false;
        }

        int value = getVarbitValue(varbitId);
        Integer requiredValue = diaryCompletionThreshold(regionKey, difficultyKey);
        return requiredValue == null ? value > 0 : value >= requiredValue;
    }

    private Integer diaryCompletionThreshold(String regionKey, String difficultyKey)
    {
        if (!"KARAMJA".equals(regionKey))
        {
            return null;
        }

        switch (difficultyKey)
        {
            case "EASY":
                return 10;
            case "MEDIUM":
                return 19;
            case "HARD":
                return 10;
            case "ELITE":
                return 5;
            default:
                return null;
        }
    }

    /**
     * Counts how many skills from the given set of skill names are at level 99.
     * Skill names are as used in tasks.json experience maps (e.g. "attack", "runecraft", "sailing").
     * Unknown skills (e.g. Sailing before RuneLite support) are silently skipped.
     */
    public int countSkillsAt99(java.util.Set<String> skillNames)
    {
        int count = 0;
        for (String name : skillNames)
        {
            Skill skill = findSkill(name);
            if (skill != null && realSkillLevel(skill) >= 99)
            {
                count++;
            }
        }
        return count;
    }

    private List<String> splitPrereqs(String prereqs)
    {
        String normalized = prereqs.replace("\r", "")
                .replaceAll("\\s*;\\s*", "\n")
                .replaceAll("\n{2,}", "\n")
                .trim();

        if (normalized.isEmpty() || "none".equalsIgnoreCase(normalized))
        {
            return List.of();
        }

        String[] pieces = normalized.split("\\n");
        List<String> out = new ArrayList<>(pieces.length);
        for (String piece : pieces)
        {
            String p = piece.trim();
            if (!p.isEmpty())
            {
                out.add(p);
            }
        }
        return out;
    }

    private boolean isSatisfied(String prerequisite)
    {
        String normalizedPrereq = stripLeadingLabel(cleanupToken(prerequisite)).replaceFirst("(?i)^either\\s+", "");

        Matcher combinedMatcher = COMBINED_LEVEL_PATTERN.matcher(normalizedPrereq);
        if (combinedMatcher.matches())
        {
            Skill firstSkill = findSkill(combinedMatcher.group(1));
            Skill secondSkill = findSkill(combinedMatcher.group(2));
            if (firstSkill == null || secondSkill == null)
            {
                return false;
            }

            int requiredCombined = Integer.parseInt(combinedMatcher.group(3));
            int firstLevel = realSkillLevel(firstSkill);
            int secondLevel = realSkillLevel(secondSkill);
            if ((firstLevel + secondLevel) >= requiredCombined)
            {
                return true;
            }

            String eitherThreshold = combinedMatcher.group(4);
            return eitherThreshold != null
                    && (firstLevel >= Integer.parseInt(eitherThreshold)
                    || secondLevel >= Integer.parseInt(eitherThreshold));
        }

        Matcher diaryMatcher = DIARY_PREREQ_PATTERN.matcher(normalizedPrereq);
        if (diaryMatcher.matches())
        {
            return isDiaryComplete(diaryMatcher.group(1), diaryMatcher.group(2));
        }

        String[] disjunctions = normalizedPrereq.split("(?i)\\s+or\\s+");
        if (disjunctions.length > 1)
        {
            for (String option : disjunctions)
            {
                if (isSatisfiedAtomic(option))
                {
                    return true;
                }
            }
            return false;
        }

        return isSatisfiedAtomic(normalizedPrereq);
    }

    private boolean isSatisfiedAtomic(String prerequisite)
    {
        String normalized = stripLeadingLabel(cleanupToken(prerequisite)).replaceFirst("(?i)^either\\s+", "");

        if (BARBARIAN_FIREMAKING_PART_1_PATTERN.matcher(normalized).matches())
        {
            return isBarbarianFiremakingPart1Complete();
        }

        String skillCandidate = stripTrailingParenthetical(normalized);
        Matcher skillMatcher = SKILL_PREREQ_PATTERN.matcher(skillCandidate);
        if (skillMatcher.matches())
        {
            int requiredLevel = Integer.parseInt(skillMatcher.group(1));
            Skill skill = findSkill(skillMatcher.group(2));
            return skill != null && realSkillLevel(skill) >= requiredLevel;
        }

        Matcher skillPlusMatcher = SKILL_PREREQ_PLUS_PATTERN.matcher(skillCandidate);
        if (skillPlusMatcher.matches())
        {
            int requiredLevel = Integer.parseInt(skillPlusMatcher.group(1));
            Skill skill = findSkill(skillPlusMatcher.group(2));
            return skill != null && realSkillLevel(skill) >= requiredLevel;
        }

        Matcher questPointsMatcher = QUEST_POINTS_PATTERN.matcher(normalized);
        if (questPointsMatcher.matches())
        {
            int requiredPoints = Integer.parseInt(questPointsMatcher.group(1));
            return client != null && client.getVarpValue(VarPlayer.QUEST_POINTS) >= requiredPoints;
        }

        Matcher favorMatcher = FAVOR_PERCENT_PATTERN.matcher(normalized);
        if (favorMatcher.matches())
        {
            int requiredFavor = Integer.parseInt(favorMatcher.group(1));
            Integer currentFavor = getFavorFor(favorMatcher.group(2));
            return currentFavor != null && currentFavor >= requiredFavor;
        }

        Matcher bareFavorMatcher = FAVOR_PREREQ_PATTERN.matcher(normalized);
        if (bareFavorMatcher.matches())
        {
            Integer currentFavor = getFavorFor(bareFavorMatcher.group(1));
            return currentFavor != null && currentFavor >= 100;
        }

        Matcher coinsMatcher = COINS_PATTERN.matcher(normalized);
        if (coinsMatcher.matches())
        {
            long requiredCoins = parseScaledNumber(coinsMatcher.group(1), coinsMatcher.group(2));
            if (requiredCoins <= 0)
            {
                return false;
            }

            return getKnownCoins() >= requiredCoins;
        }

        Matcher pointsMatcher = POINTS_PATTERN.matcher(normalized);
        if (pointsMatcher.matches())
        {
            long requiredPoints = parseScaledNumber(pointsMatcher.group(1), pointsMatcher.group(2));
            if (requiredPoints <= 0)
            {
                return false;
            }

            Integer currentPoints = getPointsFor(pointsMatcher.group(3));
            return currentPoints != null && currentPoints >= requiredPoints;
        }

        Matcher combatMatcher = COMBAT_LEVEL_PATTERN.matcher(normalized);
        if (combatMatcher.matches())
        {
            int requiredCombat = Integer.parseInt(combatMatcher.group(1));
            return client != null && client.getLocalPlayer() != null && client.getLocalPlayer().getCombatLevel() >= requiredCombat;
        }

        Matcher totalLevelMatcher = TOTAL_LEVEL_PATTERN.matcher(normalized);
        if (totalLevelMatcher.matches())
        {
            int requiredTotal = Integer.parseInt(totalLevelMatcher.group(1));
            return client != null && client.getTotalLevel() >= requiredTotal;
        }

        Matcher startQuestMatcher = START_QUEST_PREREQ_PATTERN.matcher(normalized);
        if (startQuestMatcher.matches())
        {
            if (client == null)
            {
                return false;
            }

            Quest quest = findQuestWithOptionalQuestSuffix(startQuestMatcher.group(1));
            if (quest == null)
            {
                return false;
            }

            QuestState state = quest.getState(client);
            return state == QuestState.IN_PROGRESS || state == QuestState.FINISHED;
        }

        Matcher questMatcher = QUEST_PREREQ_PATTERN.matcher(normalized);
        if (questMatcher.matches())
        {
            if (client == null)
            {
                return false;
            }

            Quest quest = findQuestWithOptionalQuestSuffix(normalized);
            return quest != null && quest.getState(client) == QuestState.FINISHED;
        }

        return false;
    }

    private boolean isBarbarianFiremakingPart1Complete()
    {
        return getVarbitValue(VarbitID.BRUT_FIRE) > 0
                || (client != null && Quest.BARBARIAN_TRAINING.getState(client) == QuestState.FINISHED);
    }

    private Skill findSkill(String name)
    {
        return skillsByName.get(normalize(name));
    }

    private Quest findQuest(String name)
    {
        return questsByName.get(normalize(name));
    }

    private Quest findQuestWithOptionalQuestSuffix(String name)
    {
        Quest direct = findQuest(name);
        if (direct != null)
        {
            return direct;
        }

        Matcher questMatcher = QUEST_PREREQ_PATTERN.matcher(name);
        return questMatcher.matches() ? findQuest(questMatcher.group(1)) : null;
    }

    private void registerSkills()
    {
        for (Skill skill : Skill.values())
        {
            skillsByName.put(normalize(skill.getName()), skill);
        }

        skillsByName.put(normalize("runecraft"), Skill.RUNECRAFT);
        skillsByName.put(normalize("runecrafting"), Skill.RUNECRAFT);
        skillsByName.put(normalize("hitpoints"), Skill.HITPOINTS);
    }

    private void registerQuests()
    {
        for (Quest quest : Quest.values())
        {
            String questName = quest.getName();
            questsByName.put(normalize(questName), quest);
            questsByName.put(normalize(toNumericQuestName(questName)), quest);
        }

        Quest elementalWorkshopI = questsByName.get(normalize("Elemental Workshop I"));
        if (elementalWorkshopI != null)
        {
            questsByName.put(normalize("Elemental Workshop 1"), elementalWorkshopI);
        }
    }

    private static String toNumericQuestName(String questName)
    {
        return questName
                .replaceAll("\\bIII\\b", "3")
                .replaceAll("\\bII\\b", "2")
                .replaceAll("\\bI\\b", "1");
    }

    private void registerVarbits()
    {
        registerDiaryVarbits();

        registerVarbit("SLAYER_POINTS", Varbits.SLAYER_POINTS);
        registerVarbit("NMZ_POINTS", Varbits.NMZ_POINTS);
        registerVarbit("TITHE_FARM_POINTS", Varbits.TITHE_FARM_POINTS);
        registerVarbit("BA_GC", Varbits.BA_GC);

        registerVarbit("KOUREND_FAVOR_ARCEUUS", Varbits.KOUREND_FAVOR_ARCEUUS);
        registerVarbit("KOUREND_FAVOR_HOSIDIUS", Varbits.KOUREND_FAVOR_HOSIDIUS);
        registerVarbit("KOUREND_FAVOR_LOVAKENGJ", Varbits.KOUREND_FAVOR_LOVAKENGJ);
        registerVarbit("KOUREND_FAVOR_PISCARILIUS", Varbits.KOUREND_FAVOR_PISCARILIUS);
        registerVarbit("KOUREND_FAVOR_SHAYZIEN", Varbits.KOUREND_FAVOR_SHAYZIEN);
    }

    private void registerDiaryVarbits()
    {
        registerVarbit("DIARY_ARDOUGNE_EASY", Varbits.DIARY_ARDOUGNE_EASY);
        registerVarbit("DIARY_ARDOUGNE_MEDIUM", Varbits.DIARY_ARDOUGNE_MEDIUM);
        registerVarbit("DIARY_ARDOUGNE_HARD", Varbits.DIARY_ARDOUGNE_HARD);
        registerVarbit("DIARY_ARDOUGNE_ELITE", Varbits.DIARY_ARDOUGNE_ELITE);
        registerVarbit("DIARY_DESERT_EASY", Varbits.DIARY_DESERT_EASY);
        registerVarbit("DIARY_DESERT_MEDIUM", Varbits.DIARY_DESERT_MEDIUM);
        registerVarbit("DIARY_DESERT_HARD", Varbits.DIARY_DESERT_HARD);
        registerVarbit("DIARY_DESERT_ELITE", Varbits.DIARY_DESERT_ELITE);
        registerVarbit("DIARY_FALADOR_EASY", Varbits.DIARY_FALADOR_EASY);
        registerVarbit("DIARY_FALADOR_MEDIUM", Varbits.DIARY_FALADOR_MEDIUM);
        registerVarbit("DIARY_FALADOR_HARD", Varbits.DIARY_FALADOR_HARD);
        registerVarbit("DIARY_FALADOR_ELITE", Varbits.DIARY_FALADOR_ELITE);
        registerVarbit("DIARY_FREMENNIK_EASY", Varbits.DIARY_FREMENNIK_EASY);
        registerVarbit("DIARY_FREMENNIK_MEDIUM", Varbits.DIARY_FREMENNIK_MEDIUM);
        registerVarbit("DIARY_FREMENNIK_HARD", Varbits.DIARY_FREMENNIK_HARD);
        registerVarbit("DIARY_FREMENNIK_ELITE", Varbits.DIARY_FREMENNIK_ELITE);
        registerVarbit("DIARY_KANDARIN_EASY", Varbits.DIARY_KANDARIN_EASY);
        registerVarbit("DIARY_KANDARIN_MEDIUM", Varbits.DIARY_KANDARIN_MEDIUM);
        registerVarbit("DIARY_KANDARIN_HARD", Varbits.DIARY_KANDARIN_HARD);
        registerVarbit("DIARY_KANDARIN_ELITE", Varbits.DIARY_KANDARIN_ELITE);
        registerVarbit("DIARY_KARAMJA_EASY", VarbitID.KARAMJA_EASY_COUNT);
        registerVarbit("DIARY_KARAMJA_MEDIUM", VarbitID.KARAMJA_MED_COUNT);
        registerVarbit("DIARY_KARAMJA_HARD", VarbitID.KARAMJA_HARD_COUNT);
        registerVarbit("DIARY_KARAMJA_ELITE", VarbitID.KARAMJA_ELITE_COUNT);
        registerVarbit("DIARY_KOUREND_EASY", Varbits.DIARY_KOUREND_EASY);
        registerVarbit("DIARY_KOUREND_MEDIUM", Varbits.DIARY_KOUREND_MEDIUM);
        registerVarbit("DIARY_KOUREND_HARD", Varbits.DIARY_KOUREND_HARD);
        registerVarbit("DIARY_KOUREND_ELITE", Varbits.DIARY_KOUREND_ELITE);
        registerVarbit("DIARY_LUMBRIDGE_EASY", Varbits.DIARY_LUMBRIDGE_EASY);
        registerVarbit("DIARY_LUMBRIDGE_MEDIUM", Varbits.DIARY_LUMBRIDGE_MEDIUM);
        registerVarbit("DIARY_LUMBRIDGE_HARD", Varbits.DIARY_LUMBRIDGE_HARD);
        registerVarbit("DIARY_LUMBRIDGE_ELITE", Varbits.DIARY_LUMBRIDGE_ELITE);
        registerVarbit("DIARY_MORYTANIA_EASY", Varbits.DIARY_MORYTANIA_EASY);
        registerVarbit("DIARY_MORYTANIA_MEDIUM", Varbits.DIARY_MORYTANIA_MEDIUM);
        registerVarbit("DIARY_MORYTANIA_HARD", Varbits.DIARY_MORYTANIA_HARD);
        registerVarbit("DIARY_MORYTANIA_ELITE", Varbits.DIARY_MORYTANIA_ELITE);
        registerVarbit("DIARY_VARROCK_EASY", Varbits.DIARY_VARROCK_EASY);
        registerVarbit("DIARY_VARROCK_MEDIUM", Varbits.DIARY_VARROCK_MEDIUM);
        registerVarbit("DIARY_VARROCK_HARD", Varbits.DIARY_VARROCK_HARD);
        registerVarbit("DIARY_VARROCK_ELITE", Varbits.DIARY_VARROCK_ELITE);
        registerVarbit("DIARY_WESTERN_EASY", Varbits.DIARY_WESTERN_EASY);
        registerVarbit("DIARY_WESTERN_MEDIUM", Varbits.DIARY_WESTERN_MEDIUM);
        registerVarbit("DIARY_WESTERN_HARD", Varbits.DIARY_WESTERN_HARD);
        registerVarbit("DIARY_WESTERN_ELITE", Varbits.DIARY_WESTERN_ELITE);
        registerVarbit("DIARY_WILDERNESS_EASY", Varbits.DIARY_WILDERNESS_EASY);
        registerVarbit("DIARY_WILDERNESS_MEDIUM", Varbits.DIARY_WILDERNESS_MEDIUM);
        registerVarbit("DIARY_WILDERNESS_HARD", Varbits.DIARY_WILDERNESS_HARD);
        registerVarbit("DIARY_WILDERNESS_ELITE", Varbits.DIARY_WILDERNESS_ELITE);
    }

    private void registerVarbit(String name, int varbitId)
    {
        varbitsByName.put(name, varbitId);
    }

    private String toDiaryRegionKey(String diaryRegion)
    {
        String normalized = normalize(diaryRegion);
        if (normalized.contains("ardougne"))
        {
            return "ARDOUGNE";
        }
        if (normalized.contains("desert"))
        {
            return "DESERT";
        }
        if (normalized.contains("falador"))
        {
            return "FALADOR";
        }
        if (normalized.contains("fremennik"))
        {
            return "FREMENNIK";
        }
        if (normalized.contains("kandarin"))
        {
            return "KANDARIN";
        }
        if (normalized.contains("karamja"))
        {
            return "KARAMJA";
        }
        if (normalized.contains("kourend") || normalized.contains("kebos"))
        {
            return "KOUREND";
        }
        if (normalized.contains("lumbridge") || normalized.contains("draynor"))
        {
            return "LUMBRIDGE";
        }
        if (normalized.contains("morytania"))
        {
            return "MORYTANIA";
        }
        if (normalized.contains("varrock"))
        {
            return "VARROCK";
        }
        if (normalized.contains("western"))
        {
            return "WESTERN";
        }
        if (normalized.contains("wilderness"))
        {
            return "WILDERNESS";
        }
        return null;
    }

    private static String cleanupToken(String token)
    {
        return token
                .replaceAll("^[\\s,:]+", "")
                .replaceAll("[\\s,:]+$", "")
                .trim();
    }

    private List<PrerequisiteStatus.CheckSpan> checkSpans(String prerequisite)
    {
        List<PrerequisiteStatus.CheckSpan> spans = new ArrayList<>();
        Matcher matcher = TRACKABLE_SKILL_SPAN_PATTERN.matcher(prerequisite);
        while (matcher.find())
        {
            Skill skill = findSkill(matcher.group(2));
            if (skill == null)
            {
                continue;
            }

            int requiredLevel;
            try
            {
                requiredLevel = Integer.parseInt(matcher.group(1));
            }
            catch (NumberFormatException ignored)
            {
                continue;
            }

            spans.add(new PrerequisiteStatus.CheckSpan(
                    matcher.start(),
                    matcher.end(),
                    realSkillLevel(skill) >= requiredLevel
            ));
        }
        return spans;
    }

    private List<Skill> skillIcons(String prerequisite)
    {
        Set<Skill> skills = new LinkedHashSet<>();

        String normalizedPrereq = stripLeadingLabel(cleanupToken(prerequisite)).replaceFirst("(?i)^either\\s+", "");
        addBarbarianAccessSkillIcons(normalizedPrereq, skills);

        Matcher combinedMatcher = COMBINED_LEVEL_PATTERN.matcher(normalizedPrereq);
        if (combinedMatcher.matches())
        {
            Skill firstSkill = findSkill(combinedMatcher.group(1));
            Skill secondSkill = findSkill(combinedMatcher.group(2));
            if (firstSkill != null)
            {
                skills.add(firstSkill);
            }
            if (secondSkill != null)
            {
                skills.add(secondSkill);
            }
        }

        Matcher matcher = TRACKABLE_SKILL_SPAN_PATTERN.matcher(prerequisite);
        while (matcher.find())
        {
            Skill skill = findSkill(matcher.group(2));
            if (skill != null)
            {
                skills.add(skill);
            }
        }

        return new ArrayList<>(skills);
    }

    private void addBarbarianAccessSkillIcons(String prerequisite, Set<Skill> skills)
    {
        String normalized = normalize(prerequisite);
        if (!normalized.startsWith("accesstobarbarian"))
        {
            return;
        }

        if (normalized.contains("fishing"))
        {
            skills.add(Skill.FISHING);
        }
        if (normalized.contains("firemaking"))
        {
            skills.add(Skill.FIREMAKING);
        }
        if (normalized.contains("smithing"))
        {
            skills.add(Skill.SMITHING);
        }
        if (normalized.contains("herblore"))
        {
            skills.add(Skill.HERBLORE);
        }
    }

    private List<MarkerIcon> markerIcons(String prerequisite)
    {
        Set<MarkerIcon> icons = new LinkedHashSet<>();
        String normalizedPrereq = stripLeadingLabel(cleanupToken(prerequisite)).replaceFirst("(?i)^either\\s+", "");
        String[] disjunctions = normalizedPrereq.split("(?i)\\s+or\\s+");
        for (String option : disjunctions)
        {
            String normalized = cleanupToken(option);
            MarkerIcon miniquestIcon = miniquestMarkerIcon(normalized);
            if (miniquestIcon != null)
            {
                icons.add(miniquestIcon);
                continue;
            }
            if (BARBARIAN_FIREMAKING_PART_1_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.BARBARIAN_MINIQUEST);
                continue;
            }
            if (QUEST_POINTS_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.QUEST);
                continue;
            }
            if (DIARY_PREREQ_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.ACHIEVEMENT_DIARY);
                continue;
            }
            if (COMBAT_LEVEL_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.COMBAT);
                continue;
            }
            if (TOTAL_LEVEL_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.TOTAL);
                continue;
            }
            if (FAVOR_PERCENT_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.FAVOUR);
                continue;
            }
            if (FAVOR_PREREQ_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.FAVOUR);
                continue;
            }
            if (JAGEX_ACCOUNT_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.JAGEX_ACCOUNT);
                continue;
            }
            List<MarkerIcon> namedPrereqIcons = namedPrerequisiteMarkerIcons(normalized);
            if (!namedPrereqIcons.isEmpty())
            {
                icons.addAll(namedPrereqIcons);
                continue;
            }
            if (COINS_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.CURRENCY);
                continue;
            }
            if (POINTS_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.CURRENCY);
                continue;
            }

            normalized = stripTrailingParenthetical(normalized);
            if (START_QUEST_PREREQ_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.START_QUEST);
            }
            else if (QUEST_PREREQ_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.QUEST);
            }
            else if (SUBQUEST_PREREQ_PATTERN.matcher(normalized).matches())
            {
                icons.add(MarkerIcon.QUEST);
            }
            else if (normalize(normalized).contains("wilderness"))
            {
                icons.add(MarkerIcon.WILDERNESS);
            }
        }
        return new ArrayList<>(icons);
    }

    private List<MarkerIcon> namedPrerequisiteMarkerIcons(String prerequisite)
    {
        String normalized = normalize(prerequisite);
        if (normalized.contains("thermonuclearsmokedevil"))
        {
            return List.of(MarkerIcon.THERMONUCLEAR_SMOKE_DEVIL);
        }

        if (normalized.contains("chompy") || normalized.contains("jubblybirdkills"))
        {
            return List.of(MarkerIcon.CHOMPY_BIRD);
        }

        if (normalized.contains("kalphitequeen"))
        {
            return List.of(MarkerIcon.KALPHITE_QUEEN);
        }

        if (normalized.contains("penancequeen"))
        {
            return List.of(MarkerIcon.PENANCE_QUEEN);
        }

        switch (normalized)
        {
            case "defeatthecrazyarchaeologistchaosfanaticandscorpia":
                return List.of(MarkerIcon.CRAZY_ARCHAEOLOGIST, MarkerIcon.CHAOS_FANATIC, MarkerIcon.SCORPIA);
            case "accesstoatleastoneofthethreegodspells":
                return List.of(MarkerIcon.SARADOMIN_STRIKE, MarkerIcon.CLAWS_OF_GUTHIX, MarkerIcon.FLAMES_OF_ZAMORAK);
            case "defeateachofthedagannothkings":
                return List.of(MarkerIcon.DAGANNOTH_REX, MarkerIcon.DAGANNOTH_SUPREME, MarkerIcon.DAGANNOTH_PRIME);
            case "defeatcallistoartiovenenatisspindelandvetioncalvarion":
                return List.of(MarkerIcon.CALLISTO, MarkerIcon.VENENATIS, MarkerIcon.VETION);
            case "defeatallgodwarsdungeongeneralsexceptnex":
                return List.of(MarkerIcon.KRIL_TSUTSAROTH, MarkerIcon.KREEARRA, MarkerIcon.COMMANDER_ZILYANA, MarkerIcon.GENERAL_GRAARDOR);
            case "acquireandwearanycompletevoidset":
                return List.of(MarkerIcon.VOID_TOP, MarkerIcon.VOID_ROBE, MarkerIcon.VOID_GLOVES);
            case "seewikibasedonraid":
                return List.of(MarkerIcon.RAID_WIKI);
            case "defeatthegiantmole":
                return List.of(MarkerIcon.GIANT_MOLE);
            case "acquireaprospectorhelmetfromthemotherlodemine":
                return List.of(MarkerIcon.PROSPECTOR_HELMET);
            case "defeatalizardmanshamaninthelizardmantemple":
                return List.of(MarkerIcon.LIZARDMAN_SHAMAN);
            case "accesstothebonestopeachesspellfromthemagetrainingarena":
                return List.of(MarkerIcon.BONES_TO_PEACHES);
            case "defeatzulrah":
                return List.of(MarkerIcon.ZULRAH);
            case "defeatthechaoselemental":
                return List.of(MarkerIcon.CHAOS_ELEMENTAL);
            case "openthegrandgoldchestinthefinalroomofpyramidplunder":
                return List.of(MarkerIcon.PYRAMID_PLUNDER);
            case "defeatskotizo":
                return List.of(MarkerIcon.SKOTIZO);
            case "defeatahydrainthekaruulmslayerdungeon":
                return List.of(MarkerIcon.HYDRA);
            case "reachtherankofwhiteknightmaster":
                return List.of(MarkerIcon.WHITE_KNIGHT);
            case "defeataketzekinthetzhaarfightcaveonthe31stwave":
                return List.of(MarkerIcon.KET_ZEK);
            case "tzhaarfightcave":
                return List.of(MarkerIcon.TZHAAR_FIGHT_CAVE);
            case "openthebarrowschestwhilewearingafullbarrowsset":
                return List.of(MarkerIcon.BARROWS_CHEST);
            case "chambersofxeric":
                return List.of(MarkerIcon.CHAMBERS_OF_XERIC);
            default:
                return List.of();
        }
    }

    private MarkerIcon miniquestMarkerIcon(String prerequisite)
    {
        Matcher matcher = MINIQUEST_PREREQ_PATTERN.matcher(prerequisite);
        if (!matcher.matches())
        {
            return null;
        }

        String miniquest = normalize(matcher.group(1));
        switch (miniquest)
        {
            case "lairoftarnrazorlor":
                return MarkerIcon.LAIR_OF_TARN_RAZORLOR;
            case "magearena1":
                return MarkerIcon.MAGE_ARENA_1;
            case "entertheabyss":
                return MarkerIcon.ENTER_THE_ABYSS;
            case "valetotems":
                return MarkerIcon.VALE_TOTEMS;
            case "alfredgrimhandsbarcrawl":
                return MarkerIcon.ALFRED_GRIMHANDS_BARCRAWL;
            default:
                return null;
        }
    }

    private int realSkillLevel(Skill skill)
    {
        return skillLevelReader == null ? client.getRealSkillLevel(skill) : skillLevelReader.applyAsInt(skill);
    }

    private static String stripLeadingLabel(String token)
    {
        int colon = token.indexOf(':');
        if (colon < 0 || colon >= token.length() - 1)
        {
            return token;
        }
        return cleanupToken(token.substring(colon + 1));
    }

    private static String stripTrailingParenthetical(String token)
    {
        return token.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    private Integer getPointsFor(String rawPointsLabel)
    {
        String label = normalize(rawPointsLabel);

        if (label.equals("slayer") || label.contains("slayer"))
        {
            Integer varbit = varbitsByName.get("SLAYER_POINTS");
            return varbit != null ? getVarbitValue(varbit) : null;
        }

        if (label.contains("nightmarezone") || label.equals("nmz"))
        {
            int nmzRewardPoints = client == null ? 0 : client.getVarpValue(VarPlayer.NMZ_REWARD_POINTS);
            Integer nmzPointsVarbit = varbitsByName.get("NMZ_POINTS");
            int nmzPoints = nmzPointsVarbit != null ? getVarbitValue(nmzPointsVarbit) : 0;
            return Math.max(nmzRewardPoints, nmzPoints);
        }

        if (label.contains("tithefarm"))
        {
            Integer varbit = varbitsByName.get("TITHE_FARM_POINTS");
            return varbit != null ? getVarbitValue(varbit) : null;
        }

        if (label.contains("barbarianassault") || label.contains("honour"))
        {
            Integer varbit = varbitsByName.get("BA_GC");
            return varbit != null ? getVarbitValue(varbit) : null;
        }

        if (label.contains("chambersofxeric") || label.equals("cox") || label.equals("raids") || label.equals("raid"))
        {
            return client == null ? null : client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
        }

        return null;
    }

    // Raw varbit ID for Tai Bwo Wannai Cleanup minigame favour (0–100 scale).
    // No constant exists in RuneLite's Varbits API for this; sourced from OSRS wiki varbit 4600.
    private static final int VARBIT_TAI_BWO_WANNAI_CLEANUP = 4600;

    private Integer getFavorFor(String rawFavorLabel)
    {
        String label = normalize(rawFavorLabel);

        if (label.contains("tai bwo") || label.contains("wannai"))
        {
            return getVarbitValue(VARBIT_TAI_BWO_WANNAI_CLEANUP);
        }

        if (label.contains("arceuus"))
        {
            return getVarbitByName("KOUREND_FAVOR_ARCEUUS");
        }
        if (label.contains("hosidius"))
        {
            return getVarbitByName("KOUREND_FAVOR_HOSIDIUS");
        }
        if (label.contains("lovakengj"))
        {
            return getVarbitByName("KOUREND_FAVOR_LOVAKENGJ");
        }
        if (label.contains("piscarilius"))
        {
            return getVarbitByName("KOUREND_FAVOR_PISCARILIUS");
        }
        if (label.contains("shayzien"))
        {
            return getVarbitByName("KOUREND_FAVOR_SHAYZIEN");
        }

        // For broad "Kourend favour" phrasing, require all five houses at threshold.
        if (label.contains("kourend") || label.contains("house"))
        {
            int arceuus = valueOrZero(getVarbitByName("KOUREND_FAVOR_ARCEUUS"));
            int hosidius = valueOrZero(getVarbitByName("KOUREND_FAVOR_HOSIDIUS"));
            int lovakengj = valueOrZero(getVarbitByName("KOUREND_FAVOR_LOVAKENGJ"));
            int piscarilius = valueOrZero(getVarbitByName("KOUREND_FAVOR_PISCARILIUS"));
            int shayzien = valueOrZero(getVarbitByName("KOUREND_FAVOR_SHAYZIEN"));
            return Math.min(Math.min(arceuus, hosidius), Math.min(Math.min(lovakengj, piscarilius), shayzien));
        }

        return null;
    }

    private Integer getVarbitByName(String varbitName)
    {
        Integer varbit = varbitsByName.get(varbitName);
        return varbit != null ? getVarbitValue(varbit) : null;
    }

    private int getVarbitValue(int varbitId)
    {
        return varbitReader == null ? client.getVarbitValue(varbitId) : varbitReader.applyAsInt(varbitId);
    }

    private static int valueOrZero(Integer value)
    {
        return value == null ? 0 : value;
    }

    private long getKnownCoins()
    {
        if (client == null)
        {
            return 0;
        }

        // Bank container is only populated while bank is open, so this is best-effort.
        return countCoinsIn(InventoryID.INVENTORY)
                + countCoinsIn(InventoryID.BANK)
                + countCoinsIn(InventoryID.GROUP_STORAGE_INV);
    }

    private long countCoinsIn(InventoryID inventoryId)
    {
        ItemContainer container = client.getItemContainer(inventoryId);
        if (container == null)
        {
            return 0;
        }

        long total = 0;
        for (Item item : container.getItems())
        {
            if (item == null)
            {
                continue;
            }

            int id = item.getId();
            if (id == ItemID.COINS || id == ItemID.COINS_995)
            {
                total += Math.max(0, item.getQuantity());
            }
        }
        return total;
    }

    private static long parseScaledNumber(String amount, String suffix)
    {
        try
        {
            double parsed = Double.parseDouble(amount.replace(",", ""));
            double scaled = parsed;

            String unit = suffix == null ? "" : suffix.trim().toLowerCase(Locale.ROOT);
            if ("k".equals(unit))
            {
                scaled *= 1_000d;
            }
            else if ("m".equals(unit))
            {
                scaled *= 1_000_000d;
            }
            else if ("b".equals(unit))
            {
                scaled *= 1_000_000_000d;
            }

            if (scaled < 0d || scaled > Long.MAX_VALUE)
            {
                return -1;
            }
            return (long) Math.floor(scaled);
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }

    private static String normalize(String text)
    {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.replaceAll("[^a-z0-9]", "");
    }
}
