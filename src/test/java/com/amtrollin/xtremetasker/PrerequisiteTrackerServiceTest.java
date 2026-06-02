package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.verification.PrerequisiteTrackerService;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

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

    private static PrerequisiteTrackerService serviceWithBrutFireValue(int brutFireValue) throws Exception
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Field clientField = PrerequisiteTrackerService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(service, clientWithBrutFireValue(brutFireValue));
        return service;
    }

    private static Client clientWithBrutFireValue(int brutFireValue)
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
                            && (Integer) args[0] == VarbitID.BRUT_FIRE)
                    {
                        return brutFireValue;
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
