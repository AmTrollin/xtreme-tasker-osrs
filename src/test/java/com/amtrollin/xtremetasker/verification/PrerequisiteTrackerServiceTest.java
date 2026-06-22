package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrerequisiteTrackerServiceTest
{
    @Test
    public void barbarianFiremakingPartOneUsesBrutFireVarbit()
    {
        PrerequisiteTrackerService incompleteService = serviceWithBrutFireValue(0);
        List<PrerequisiteStatus> incomplete = incompleteService.evaluate("Part 1 of Barbarian Firemaking");
        assertFalse(incomplete.get(0).isCompleted());

        PrerequisiteTrackerService completeService = serviceWithBrutFireValue(1);
        List<PrerequisiteStatus> complete = completeService.evaluate("Part 1 of Barbarian Firemaking");
        assertTrue(complete.get(0).isCompleted());
    }

    @Test
    public void karamjaDiaryPrerequisiteUsesCompletionThreshold()
    {
        PrerequisiteTrackerService partialService = serviceWithVarbitValue(VarbitID.KARAMJA_EASY_COUNT, 1);
        List<PrerequisiteStatus> partial = partialService.evaluate("Complete the Karamja easy diary");
        assertFalse("Karamja diary varbit increments by task and should not complete at 1/10",
                partial.get(0).isCompleted());

        PrerequisiteTrackerService completeService = serviceWithVarbitValue(VarbitID.KARAMJA_EASY_COUNT, 10);
        List<PrerequisiteStatus> complete = completeService.evaluate("Complete the Karamja easy diary");
        assertTrue(complete.get(0).isCompleted());
    }

    @Test
    public void karamjaDiarySyncUsesPerTierTaskCounts()
    {
        assertKaramjaDiaryThreshold(VarbitID.KARAMJA_MED_COUNT, "medium", 18, 19);
        assertKaramjaDiaryThreshold(VarbitID.KARAMJA_HARD_COUNT, "hard", 9, 10);
        assertKaramjaDiaryThreshold(VarbitID.KARAMJA_ELITE_COUNT, "elite", 4, 5);
    }

    @Test
    public void nonKaramjaDiaryPrerequisiteUsesPositiveVarbit()
    {
        PrerequisiteTrackerService service = serviceWithVarbitValue(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 1);
        List<PrerequisiteStatus> statuses = service.evaluate("Complete the Ardougne easy diary");
        assertTrue(statuses.get(0).isCompleted());
    }

    @Test
    public void achievementDiaryPrerequisiteUsesAchievementDiaryIconMarker()
    {
        PrerequisiteStatus status = serviceWithVarbitValue(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 1)
                .evaluate("Complete the Ardougne easy diary")
                .get(0);

        assertEquals(List.of(MarkerIcon.ACHIEVEMENT_DIARY), status.getMarkerIcons());
    }

    @Test
    public void skillOrPrerequisiteWithParentheticalNotesTracksEachSkillRequirement()
    {
        PrerequisiteTrackerService service = serviceWithSkillLevel(Skill.SLAYER, 23);
        PrerequisiteStatus status = service.evaluate("Iron: 17 Slayer (Cave slimes) or 25 Slayer (Cockatrices)").get(0);

        assertTrue("17 Slayer option should satisfy the OR at level 23", status.isCompleted());
        assertEquals(2, status.getCheckSpans().size());
        assertEquals("17 Slayer", status.getText().substring(
                status.getCheckSpans().get(0).getStart(),
                status.getCheckSpans().get(0).getEnd()
        ));
        assertTrue(status.getCheckSpans().get(0).isCompleted());
        assertEquals("25 Slayer", status.getText().substring(
                status.getCheckSpans().get(1).getStart(),
                status.getCheckSpans().get(1).getEnd()
        ));
        assertFalse(status.getCheckSpans().get(1).isCompleted());
        assertEquals(List.of(Skill.SLAYER), status.getSkillIcons());
    }

    @Test
    public void questPrerequisiteUsesQuestIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("Some Unknown Quest quest").get(0);

        assertTrue(status.hasQuestIcon());
        assertEquals(List.of(MarkerIcon.QUEST), status.getMarkerIcons());
        assertTrue(status.getSkillIcons().isEmpty());
    }

    @Test
    public void questPointPrerequisiteUsesQuestIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("1 quest point").get(0);

        assertTrue(status.hasQuestIcon());
        assertEquals(List.of(MarkerIcon.QUEST), status.getMarkerIcons());
    }

    @Test
    public void barbarianFiremakingPrerequisiteUsesMiniquestIconMarker()
    {
        PrerequisiteStatus status = serviceWithBrutFireValue(1).evaluate("Part 1 of Barbarian Firemaking").get(0);

        assertEquals(List.of(MarkerIcon.BARBARIAN_MINIQUEST), status.getMarkerIcons());
    }

    @Test
    public void namedMiniquestPrerequisitesUseSpecificMiniquestIconMarkers()
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService(id -> 0);

        assertEquals(List.of(MarkerIcon.LAIR_OF_TARN_RAZORLOR),
                service.evaluate("Lair of Tarn Razorlor miniquest").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.MAGE_ARENA_1),
                service.evaluate("Mage Arena 1 miniquest").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.ENTER_THE_ABYSS),
                service.evaluate("Enter the Abyss miniquest").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.VALE_TOTEMS),
                service.evaluate("Vale Totems miniquest").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.ALFRED_GRIMHANDS_BARCRAWL),
                service.evaluate("Alfred Grimhand's Barcrawl miniquest").get(0).getMarkerIcons());
    }

    @Test
    public void combatLevelPrerequisiteUsesCombatIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("100 combat level").get(0);

        assertEquals(List.of(MarkerIcon.COMBAT), status.getMarkerIcons());
    }

    @Test
    public void totalLevelPrerequisiteUsesTotalIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("1500 total level").get(0);

        assertEquals(List.of(MarkerIcon.TOTAL), status.getMarkerIcons());
    }

    @Test
    public void bareFavourPrerequisiteUsesFavourIconMarker()
    {
        PrerequisiteStatus status = serviceWithVarbitValue(4600, 100).evaluate("Tai Bwo Wannai Cleanup favour").get(0);

        assertTrue(status.isCompleted());
        assertEquals(List.of(MarkerIcon.FAVOUR), status.getMarkerIcons());
    }

    @Test
    public void pointCurrencyPrerequisiteUsesCurrencyIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("400k NMZ Points").get(0);

        assertEquals(List.of(MarkerIcon.CURRENCY), status.getMarkerIcons());
    }

    @Test
    public void coinPrerequisiteUsesCurrencyIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("100k GP").get(0);

        assertEquals(List.of(MarkerIcon.CURRENCY), status.getMarkerIcons());
    }

    @Test
    public void jagexAccountPrerequisiteUsesJagexAccountIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0)
                .evaluate("Own a Jagex Account or set up the older RuneScape Authenticator")
                .get(0);

        assertEquals(List.of(MarkerIcon.JAGEX_ACCOUNT), status.getMarkerIcons());
    }

    @Test
    public void namedNonSkillPrerequisitesUseSpecificIconMarkers()
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService(id -> 0);

        assertEquals(List.of(MarkerIcon.THERMONUCLEAR_SMOKE_DEVIL),
                service.evaluate("Defeat the Thermonuclear smoke devil").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.RAID_WIKI),
                service.evaluate("See Wiki (Based on Raid)").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.GIANT_MOLE),
                service.evaluate("Defeat the Giant Mole").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.PROSPECTOR_HELMET),
                service.evaluate("Acquire a prospector helmet from the Motherlode Mine").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.LIZARDMAN_SHAMAN),
                service.evaluate("Defeat a Lizardman Shaman in the Lizardman Temple").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.BONES_TO_PEACHES),
                service.evaluate("Access to the Bones to Peaches spell from the Mage Training Arena").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.ZULRAH),
                service.evaluate("Defeat Zulrah").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.CHAOS_ELEMENTAL),
                service.evaluate("Defeat the Chaos elemental").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.THERMONUCLEAR_SMOKE_DEVIL),
                service.evaluate("Access to Thermonuclear smoke devil").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.CHOMPY_BIRD),
                service.evaluate("Reach 1,000 chompy or jubbly bird kills").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.KALPHITE_QUEEN),
                service.evaluate("Defeat the Kalphite Queen and obtain her tattered head").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.PENANCE_QUEEN),
                service.evaluate("Defeat the Penance Queen and acquire level 5 in all Barbarian Assault roles").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.PYRAMID_PLUNDER),
                service.evaluate("Open the Grand Gold Chest in the final room of Pyramid Plunder").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.SKOTIZO),
                service.evaluate("Defeat Skotizo").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.HYDRA),
                service.evaluate("Defeat a Hydra in the Karuulm Slayer Dungeon").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.WHITE_KNIGHT),
                service.evaluate("Reach the rank of White Knight Master").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.KET_ZEK),
                service.evaluate("Defeat a Ket-Zek in the TzHaar Fight Cave on the 31st wave").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.TZHAAR_FIGHT_CAVE),
                service.evaluate("TzHaar Fight Cave").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.BARROWS_CHEST),
                service.evaluate("Open the Barrows chest while wearing a full Barrows set").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.CHAMBERS_OF_XERIC),
                service.evaluate("Chambers of Xeric").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.CRAZY_ARCHAEOLOGIST, MarkerIcon.CHAOS_FANATIC, MarkerIcon.SCORPIA),
                service.evaluate("Defeat the Crazy Archaeologist, Chaos Fanatic, and Scorpia").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.SARADOMIN_STRIKE, MarkerIcon.CLAWS_OF_GUTHIX, MarkerIcon.FLAMES_OF_ZAMORAK),
                service.evaluate("Access to at least one of the three god spells").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.DAGANNOTH_REX, MarkerIcon.DAGANNOTH_SUPREME, MarkerIcon.DAGANNOTH_PRIME),
                service.evaluate("Defeat each of the Dagannoth Kings").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.CALLISTO, MarkerIcon.VENENATIS, MarkerIcon.VETION),
                service.evaluate("Defeat Callisto/Artio, Venenatis/Spindel, and Vet'ion/Calvar'ion").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.KRIL_TSUTSAROTH, MarkerIcon.KREEARRA, MarkerIcon.COMMANDER_ZILYANA, MarkerIcon.GENERAL_GRAARDOR),
                service.evaluate("Defeat all God Wars Dungeon generals except Nex").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.VOID_TOP, MarkerIcon.VOID_ROBE, MarkerIcon.VOID_GLOVES),
                service.evaluate("Acquire and wear any complete void set").get(0).getMarkerIcons());
        assertEquals(List.of(MarkerIcon.WILDERNESS),
                service.evaluate("Access to the Wilderness God Wars Dungeon").get(0).getMarkerIcons());
    }

    @Test
    public void subquestPrerequisiteUsesQuestIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0)
                .evaluate("King Awowogei Recipe for Disaster subquest")
                .get(0);

        assertTrue(status.hasQuestIcon());
        assertEquals(List.of(MarkerIcon.QUEST), status.getMarkerIcons());
    }

    @Test
    public void barbarianAccessPrerequisitesUseNamedSkillIcons()
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService(id -> 0);

        assertEquals(List.of(Skill.FISHING),
                service.evaluate("Access to Barbarian Fishing").get(0).getSkillIcons());
        assertEquals(List.of(Skill.FISHING, Skill.FIREMAKING, Skill.SMITHING),
                service.evaluate("Access to Barbarian Fishing, Firemaking, and Smithing").get(0).getSkillIcons());
        assertEquals(List.of(Skill.FISHING, Skill.FIREMAKING, Skill.SMITHING, Skill.HERBLORE),
                service.evaluate("Access to Barbarian Fishing, Firemaking, Smithing, and Herblore").get(0).getSkillIcons());
    }

    @Test
    public void startQuestPrerequisiteUsesStartQuestIconMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("Start One Small Favour quest").get(0);

        assertTrue(status.hasQuestIcon());
        assertEquals(List.of(MarkerIcon.START_QUEST), status.getMarkerIcons());
    }

    @Test
    public void combinedStrengthAndAttackPrerequisiteUsesCombinedOrEitherThreshold()
    {
        String prereq = "Combined Strength and Attack level of 130, or 99 in either";

        assertTrue(serviceWithAttackStrengthLevels(65, 65).evaluate(prereq).get(0).isCompleted());
        assertTrue(serviceWithAttackStrengthLevels(99, 1).evaluate(prereq).get(0).isCompleted());
        assertTrue(serviceWithAttackStrengthLevels(1, 99).evaluate(prereq).get(0).isCompleted());
        assertFalse(serviceWithAttackStrengthLevels(64, 65).evaluate(prereq).get(0).isCompleted());
        assertEquals(List.of(Skill.STRENGTH, Skill.ATTACK),
                serviceWithAttackStrengthLevels(1, 1).evaluate(prereq).get(0).getSkillIcons());
    }

    private static void assertKaramjaDiaryThreshold(int varbitId, String difficulty, int partialValue, int completeValue)
    {
        PrerequisiteTrackerService partialService = serviceWithVarbitValue(varbitId, partialValue);
        List<PrerequisiteStatus> partial = partialService.evaluate("Complete the Karamja " + difficulty + " diary");
        assertFalse("Karamja " + difficulty + " diary should not complete below its tier task count",
                partial.get(0).isCompleted());

        PrerequisiteTrackerService completeService = serviceWithVarbitValue(varbitId, completeValue);
        List<PrerequisiteStatus> complete = completeService.evaluate("Complete the Karamja " + difficulty + " diary");
        assertTrue("Karamja " + difficulty + " diary should complete at its tier task count",
                complete.get(0).isCompleted());
    }

    private static PrerequisiteTrackerService serviceWithBrutFireValue(int brutFireValue)
    {
        return serviceWithVarbitValue(VarbitID.BRUT_FIRE, brutFireValue);
    }

    private static PrerequisiteTrackerService serviceWithVarbitValue(int varbitId, int varbitValue)
    {
        return new PrerequisiteTrackerService(id -> id == varbitId ? varbitValue : 0);
    }

    private static PrerequisiteTrackerService serviceWithSkillLevel(Skill targetSkill, int level)
    {
        return new PrerequisiteTrackerService(id -> 0, skill -> skill == targetSkill ? level : 1);
    }

    private static PrerequisiteTrackerService serviceWithAttackStrengthLevels(int attackLevel, int strengthLevel)
    {
        return new PrerequisiteTrackerService(id -> 0, skill -> {
            if (skill == Skill.ATTACK)
            {
                return attackLevel;
            }

            if (skill == Skill.STRENGTH)
            {
                return strengthLevel;
            }

            return 1;
        });
    }
}
