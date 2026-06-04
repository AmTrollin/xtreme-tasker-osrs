package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.verification.PrerequisiteTrackerService;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrerequisiteTrackerServiceTest
{
    @Test
    public void barbarianFiremakingPartOneUsesBrutFireVarbit() throws Exception
    {
        PrerequisiteTrackerService incompleteService = serviceWithBrutFireValue(0);
        List<PrerequisiteStatus> incomplete = incompleteService.evaluate("Part 1 of Barbarian Firemaking");
        assertFalse(incomplete.get(0).isCompleted());

        PrerequisiteTrackerService completeService = serviceWithBrutFireValue(1);
        List<PrerequisiteStatus> complete = completeService.evaluate("Part 1 of Barbarian Firemaking");
        assertTrue(complete.get(0).isCompleted());
    }

    @Test
    public void karamjaDiaryPrerequisiteUsesCompletionThreshold() throws Exception
    {
        PrerequisiteTrackerService partialService = serviceWithDiaryValue("DIARY_KARAMJA_EASY", 1001, 1);
        List<PrerequisiteStatus> partial = partialService.evaluate("Complete the Karamja easy diary");
        assertFalse("Karamja diary varbit increments by task and should not complete at 1/10",
                partial.get(0).isCompleted());

        PrerequisiteTrackerService completeService = serviceWithDiaryValue("DIARY_KARAMJA_EASY", 1001, 10);
        List<PrerequisiteStatus> complete = completeService.evaluate("Complete the Karamja easy diary");
        assertTrue(complete.get(0).isCompleted());
    }

    @Test
    public void nonKaramjaDiaryPrerequisiteUsesPositiveVarbit() throws Exception
    {
        PrerequisiteTrackerService service = serviceWithDiaryValue("DIARY_ARDOUGNE_EASY", 1002, 1);
        List<PrerequisiteStatus> statuses = service.evaluate("Complete the Ardougne easy diary");
        assertTrue(statuses.get(0).isCompleted());
    }

    private static PrerequisiteTrackerService serviceWithBrutFireValue(int brutFireValue) throws Exception
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Field clientField = PrerequisiteTrackerService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(service, clientWithBrutFireValue(brutFireValue));
        return service;
    }

    @SuppressWarnings("unchecked")
    private static PrerequisiteTrackerService serviceWithDiaryValue(String varbitName, int varbitId, int varbitValue)
            throws Exception
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();

        Field varbitsField = PrerequisiteTrackerService.class.getDeclaredField("varbitsByName");
        varbitsField.setAccessible(true);
        Map<String, Integer> varbitsByName = (Map<String, Integer>) varbitsField.get(service);
        varbitsByName.put(varbitName, varbitId);

        Field clientField = PrerequisiteTrackerService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(service, clientWithVarbitValue(varbitId, varbitValue));
        return service;
    }

    private static Client clientWithBrutFireValue(int brutFireValue)
    {
        return clientWithVarbitValue(VarbitID.BRUT_FIRE, brutFireValue);
    }

    private static Client clientWithVarbitValue(int varbitId, int varbitValue)
    {
        return (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[]{Client.class},
                (proxy, method, args) ->
                {
                    if ("getVarbitValue".equals(method.getName())
                            && args != null
                            && args.length == 1
                            && args[0] instanceof Integer
                            && (Integer) args[0] == varbitId)
                    {
                        return varbitValue;
                    }
                    if ("getIntStack".equals(method.getName()))
                    {
                        return new int[]{1};
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class)
                    {
                        return false;
                    }
                    if (returnType == int.class)
                    {
                        return 0;
                    }
                    if (returnType == long.class)
                    {
                        return 0L;
                    }
                    if (returnType == double.class)
                    {
                        return 0d;
                    }
                    if (returnType == float.class)
                    {
                        return 0f;
                    }
                    return null;
                });
    }
}
