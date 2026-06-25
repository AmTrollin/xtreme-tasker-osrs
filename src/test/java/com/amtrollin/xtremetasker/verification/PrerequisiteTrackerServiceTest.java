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
    public void favourPrerequisitesUseBulletMarker()
    {
        PrerequisiteStatus taiBwo = serviceWithVarbitValue(4600, 100).evaluate("Tai Bwo Wannai Cleanup favour").get(0);
        PrerequisiteStatus kourend = new PrerequisiteTrackerService(id -> 100).evaluate("100% Hosidius favour").get(0);

        assertTrue(taiBwo.isCompleted());
        assertEquals(List.of(MarkerIcon.BULLET), taiBwo.getMarkerIcons());
        assertFalse(kourend.isCompleted());
        assertEquals(List.of(MarkerIcon.BULLET), kourend.getMarkerIcons());
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
    public void nonSkillPrerequisiteFallsBackToBulletMarker()
    {
        PrerequisiteStatus status = new PrerequisiteTrackerService(id -> 0).evaluate("Defeat the Giant Mole").get(0);

        assertEquals(List.of(MarkerIcon.BULLET), status.getMarkerIcons());
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
