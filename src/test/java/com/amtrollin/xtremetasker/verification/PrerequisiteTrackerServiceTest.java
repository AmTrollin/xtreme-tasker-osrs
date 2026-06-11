package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.util.List;

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
}
