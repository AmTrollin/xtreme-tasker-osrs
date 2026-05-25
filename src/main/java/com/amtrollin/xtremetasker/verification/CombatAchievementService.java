package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class CombatAchievementService
{
    private static final int TASKS_PER_VARPLAYER = 32;

    // Varplayer IDs for CA task completion bits, in sort-order groups of 32.
    // Source: osrs-reldo/task-json-store task-types.json taskVarps field.
    // Tasks are not stored in a contiguous varplayer range; new CAs added over time
    // allocated new, non-sequential varplayer slots.
    private static final int[] CA_TASK_VARPS = {
        3116, 3117, 3118, 3119, 3120, 3121, 3122, 3123,
        3124, 3125, 3126, 3127, 3128, 3387, 3718, 3773,
        3774, 4204, 4496, 4721
    };

    @Inject
    private Client client;

    public boolean isTaskComplete(int sortId)
    {
        int varPlayerIndex = sortId / TASKS_PER_VARPLAYER;
        int bitPosition = sortId % TASKS_PER_VARPLAYER;

        if (varPlayerIndex < 0 || varPlayerIndex >= CA_TASK_VARPS.length)
        {
            log.debug("Invalid combat achievement task ID: {}", sortId);
            return false;
        }

        int varPlayerId = CA_TASK_VARPS[varPlayerIndex];
        int varPlayerValue = client.getVarpValue(varPlayerId);
        return (varPlayerValue & (1 << bitPosition)) != 0;
    }
}
