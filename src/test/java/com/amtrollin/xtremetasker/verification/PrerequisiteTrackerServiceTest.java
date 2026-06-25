package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import net.runelite.api.Varbits;
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
    public void nonKaramjaDiaryPrerequisiteUsesPositiveVarbit()
    {
        PrerequisiteTrackerService service = serviceWithVarbitValue(Varbits.DIARY_ARDOUGNE_EASY, 1);
        List<PrerequisiteStatus> statuses = service.evaluate("Complete the Ardougne easy diary");
        assertTrue(statuses.get(0).isCompleted());
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
