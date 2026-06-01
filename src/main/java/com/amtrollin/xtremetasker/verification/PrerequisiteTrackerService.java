package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class PrerequisiteTrackerService
{
    private static final Pattern SKILL_PREREQ_PATTERN = Pattern.compile("^(\\d+)\\s+([A-Za-z][A-Za-z\\- ]+)$");
    private static final Pattern SKILL_PREREQ_PLUS_PATTERN = Pattern.compile("^(\\d+)\\+\\s+([A-Za-z][A-Za-z\\- ]+)$");
    private static final Pattern QUEST_PREREQ_PATTERN = Pattern.compile("^(.+?)\\s+quest$", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_QUEST_PREREQ_PATTERN = Pattern.compile("^start\\s+(.+?)\\s+quest$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEST_POINTS_PATTERN = Pattern.compile("^(\\d+)\\s+quest\\s+points?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMBAT_LEVEL_PATTERN = Pattern.compile("^(\\d+)\\s+combat(?:\\s+level)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_LEVEL_PATTERN = Pattern.compile("^(\\d+)\\s+total\\s+level$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVOR_PERCENT_PATTERN = Pattern.compile("^(\\d+)%\\s+(.+?)\\s+favou?r$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS_PATTERN = Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb]?)\\s*(?:coins?|gp)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POINTS_PATTERN = Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(.+?)\\s+points?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIARY_PREREQ_PATTERN = Pattern.compile(
        "^complete\\s+the\\s+(.+?)\\s+(easy|medium|hard|elite)\\s+diary$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMBINED_LEVEL_PATTERN = Pattern.compile(
        "^a\\s+combined\\s+([A-Za-z][A-Za-z\\- ]+)\\s+and\\s+([A-Za-z][A-Za-z\\- ]+)\\s+level\\s+of\\s+at\\s+least\\s+(\\d+)(?:.*?level\\s+(\\d+)\\s+in\\s+either\\s+skill)?",
        Pattern.CASE_INSENSITIVE
    );

    private final Map<String, Skill> skillsByName = new HashMap<>();
    private final Map<String, Quest> questsByName = new HashMap<>();
    private final Map<String, Integer> varbitsByName = new HashMap<>();

    @Inject
    private Client client;

    public PrerequisiteTrackerService()
    {
        registerSkills();
        registerQuests();
        registerVarbits();
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

            out.add(new PrerequisiteStatus(text, isSatisfied(text)));
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

        int value = client.getVarbitValue(varbitId);
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
                return 22;
            case "HARD":
                return 32;
            case "ELITE":
                return 44;
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
            if (skill != null && client.getRealSkillLevel(skill) >= 99)
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
        String normalizedPrereq = cleanupToken(prerequisite).replaceFirst("(?i)^either\\s+", "");

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
            int firstLevel = client.getRealSkillLevel(firstSkill);
            int secondLevel = client.getRealSkillLevel(secondSkill);
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
            String regionKey = toDiaryRegionKey(diaryMatcher.group(1));
            if (regionKey == null)
            {
                return false;
            }

            String difficultyKey = diaryMatcher.group(2).toUpperCase(Locale.ROOT);
            Integer varbitId = varbitsByName.get("DIARY_" + regionKey + "_" + difficultyKey);
            return varbitId != null && client.getVarbitValue(varbitId) > 0;
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
        String normalized = cleanupToken(prerequisite).replaceFirst("(?i)^either\\s+", "");

        Matcher skillMatcher = SKILL_PREREQ_PATTERN.matcher(normalized);
        if (skillMatcher.matches())
        {
            int requiredLevel = Integer.parseInt(skillMatcher.group(1));
            Skill skill = findSkill(skillMatcher.group(2));
            return skill != null && client.getRealSkillLevel(skill) >= requiredLevel;
        }

        Matcher skillPlusMatcher = SKILL_PREREQ_PLUS_PATTERN.matcher(normalized);
        if (skillPlusMatcher.matches())
        {
            int requiredLevel = Integer.parseInt(skillPlusMatcher.group(1));
            Skill skill = findSkill(skillPlusMatcher.group(2));
            return skill != null && client.getRealSkillLevel(skill) >= requiredLevel;
        }

        Matcher questPointsMatcher = QUEST_POINTS_PATTERN.matcher(normalized);
        if (questPointsMatcher.matches())
        {
            int requiredPoints = Integer.parseInt(questPointsMatcher.group(1));
            return client.getVarpValue(VarPlayer.QUEST_POINTS) >= requiredPoints;
        }

        Matcher favorMatcher = FAVOR_PERCENT_PATTERN.matcher(normalized);
        if (favorMatcher.matches())
        {
            int requiredFavor = Integer.parseInt(favorMatcher.group(1));
            Integer currentFavor = getFavorFor(favorMatcher.group(2));
            return currentFavor != null && currentFavor >= requiredFavor;
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
            long requiredPoints = parseScaledNumber(pointsMatcher.group(1), "");
            if (requiredPoints <= 0)
            {
                return false;
            }

            Integer currentPoints = getPointsFor(pointsMatcher.group(2));
            return currentPoints != null && currentPoints >= requiredPoints;
        }

        Matcher combatMatcher = COMBAT_LEVEL_PATTERN.matcher(normalized);
        if (combatMatcher.matches())
        {
            int requiredCombat = Integer.parseInt(combatMatcher.group(1));
            return client.getLocalPlayer() != null && client.getLocalPlayer().getCombatLevel() >= requiredCombat;
        }

        Matcher totalLevelMatcher = TOTAL_LEVEL_PATTERN.matcher(normalized);
        if (totalLevelMatcher.matches())
        {
            int requiredTotal = Integer.parseInt(totalLevelMatcher.group(1));
            return client.getTotalLevel() >= requiredTotal;
        }

        Matcher startQuestMatcher = START_QUEST_PREREQ_PATTERN.matcher(normalized);
        if (startQuestMatcher.matches())
        {
            Quest quest = findQuest(startQuestMatcher.group(1));
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
            Quest quest = findQuest(questMatcher.group(1));
            return quest != null && quest.getState(client) == QuestState.FINISHED;
        }

        return false;
    }

    private Skill findSkill(String name)
    {
        return skillsByName.get(normalize(name));
    }

    private Quest findQuest(String name)
    {
        return questsByName.get(normalize(name));
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
            questsByName.put(normalize(quest.getName()), quest);
        }

        Quest elementalWorkshopI = questsByName.get(normalize("Elemental Workshop I"));
        if (elementalWorkshopI != null)
        {
            questsByName.put(normalize("Elemental Workshop 1"), elementalWorkshopI);
        }
    }

    private void registerVarbits()
    {
        for (Field field : Varbits.class.getFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class)
            {
                continue;
            }

            try
            {
                varbitsByName.put(field.getName(), field.getInt(null));
            }
            catch (IllegalAccessException ignored)
            {
                // Best-effort cache; inaccessible fields are skipped.
            }
        }
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
                .replaceAll("^[\\s,:()]+", "")
                .replaceAll("[\\s,:()]+$", "")
                .trim();
    }

    private Integer getPointsFor(String rawPointsLabel)
    {
        String label = normalize(rawPointsLabel);

        if (label.equals("slayer") || label.contains("slayer"))
        {
            Integer varbit = varbitsByName.get("SLAYER_POINTS");
            return varbit != null ? client.getVarbitValue(varbit) : null;
        }

        if (label.contains("nightmarezone") || label.equals("nmz"))
        {
            int nmzRewardPoints = client.getVarpValue(VarPlayer.NMZ_REWARD_POINTS);
            Integer nmzPointsVarbit = varbitsByName.get("NMZ_POINTS");
            int nmzPoints = nmzPointsVarbit != null ? client.getVarbitValue(nmzPointsVarbit) : 0;
            return Math.max(nmzRewardPoints, nmzPoints);
        }

        if (label.contains("tithefarm"))
        {
            Integer varbit = varbitsByName.get("TITHE_FARM_POINTS");
            return varbit != null ? client.getVarbitValue(varbit) : null;
        }

        if (label.contains("barbarianassault") || label.contains("honour"))
        {
            Integer varbit = varbitsByName.get("BA_GC");
            return varbit != null ? client.getVarbitValue(varbit) : null;
        }

        if (label.contains("chambersofxeric") || label.equals("cox") || label.equals("raids") || label.equals("raid"))
        {
            return client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
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
            return client.getVarbitValue(VARBIT_TAI_BWO_WANNAI_CLEANUP);
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
        return varbit != null ? client.getVarbitValue(varbit) : null;
    }

    private static int valueOrZero(Integer value)
    {
        return value == null ? 0 : value;
    }

    private long getKnownCoins()
    {
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
