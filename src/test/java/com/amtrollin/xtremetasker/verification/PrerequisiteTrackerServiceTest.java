package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import net.runelite.api.Varbits;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrerequisiteTrackerServiceTest
{
    @Test
    public void startedQuestAndSubquestPrerequisitesAcceptProgressOrCompletion() throws Exception
    {
        String[] prerequisites = {
                "Start Freeing Pirate Pete Recipe for Disaster subquest",
                "Start Perilous Moons quest"
        };
        Quest[] quests = {Quest.RECIPE_FOR_DISASTER__PIRATE_PETE, Quest.PERILOUS_MOONS};
        for (int i = 0; i < prerequisites.length; i++)
        {
            for (int state : new int[] {1, 0, 2})
            {
                PrerequisiteTrackerService service = serviceWithQuestState(quests[i], state);
                PrerequisiteStatus status = service.evaluate(prerequisites[i]).get(0);
                org.junit.Assert.assertEquals(prerequisites[i] + " state " + state,
                        state != 1, status.isCompleted());
                assertTrue(status.getMarkerIcons().contains(PrerequisiteStatus.MarkerIcon.START_QUEST));
                org.junit.Assert.assertEquals("Completion wording must still require finishing", state == 2,
                        service.evaluate(prerequisites[i].substring("Start ".length())).get(0).isCompleted());
            }
        }
    }

    @Test
    public void combatPrerequisitesUsePlayerLevelAtAndAboveThreshold() throws Exception
    {
        int[] combatLevel = {69};
        net.runelite.api.Player player = (net.runelite.api.Player) Proxy.newProxyInstance(
                net.runelite.api.Player.class.getClassLoader(), new Class<?>[] {net.runelite.api.Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCombatLevel")) return combatLevel[0];
                    throw new AssertionError(method.getName());
                });
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLocalPlayer")) return player;
                    throw new AssertionError(method.getName());
                });
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Field field = PrerequisiteTrackerService.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        for (int required : new int[] {40, 70, 75, 100})
        {
            for (int actual : new int[] {required - 1, required, required + 1})
            {
                combatLevel[0] = actual;
                for (String suffix : new String[] {" Combat", " Combat level"})
                {
                    PrerequisiteStatus status = service.evaluate(required + suffix).get(0);
                    org.junit.Assert.assertEquals("Combat " + actual + " against " + required + suffix,
                            actual >= required, status.isCompleted());
                    assertTrue(status.getMarkerIcons().contains(PrerequisiteStatus.MarkerIcon.COMBAT));
                }
            }
        }
        field.set(service, null);
        assertFalse("Missing player data must not complete a requirement",
                service.evaluate("70 Combat").get(0).isCompleted());
    }

    @Test
    public void numericNonSkillRequirementsReachTheirDedicatedChecks() throws Exception
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getVarpValue")) return 50;
                    if (method.getName().equals("getTotalLevel")) return 1000;
                    if (method.getName().equals("getRealSkillLevel")) return 60;
                    throw new AssertionError(method.getName());
                });
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Field field = PrerequisiteTrackerService.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        assertTrue(service.evaluate("50 Quest Points").get(0).isCompleted());
        assertFalse(service.evaluate("51 Quest Points").get(0).isCompleted());
        assertTrue(service.evaluate("1000 Total level").get(0).isCompleted());
        assertFalse(service.evaluate("1001 Total level").get(0).isCompleted());
        assertTrue(service.evaluate("60 Mining").get(0).isCompleted());
        assertFalse(service.evaluate("61 Mining").get(0).isCompleted());
        assertFalse(service.evaluate("70 Unknown Skill").get(0).isCompleted());
    }

    @Test
    public void diaryChangeMatchesItsVarbitOrContainingVarp() throws Exception
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[] {Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getVarbit"))
                    {
                        org.junit.Assert.assertEquals(Varbits.DIARY_ARDOUGNE_EASY, args[0]);
                        return Proxy.newProxyInstance(net.runelite.api.VarbitComposition.class.getClassLoader(),
                                new Class<?>[] {net.runelite.api.VarbitComposition.class},
                                (definition, definitionMethod, definitionArgs) -> {
                                    if (definitionMethod.getName().equals("getIndex")) return 1234;
                                    throw new AssertionError(definitionMethod.getName());
                                });
                    }
                    throw new AssertionError(method.getName());
                });
        Field field = PrerequisiteTrackerService.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        assertTrue(service.isDiaryCompletionVariable("ardougne", "easy", -1, Varbits.DIARY_ARDOUGNE_EASY));
        assertTrue(service.isDiaryCompletionVariable("ardougne", "easy", 1234, -1));
        assertFalse(service.isDiaryCompletionVariable("ardougne", "easy", 4321, -1));
        assertFalse(service.isDiaryCompletionVariable("unknown", "easy", 1234, -1));
    }

    @Test
    public void recipeForDisasterSubquestsTrackTheirOwnCompletion() throws Exception
    {
        String[] prerequisites = {
                "Freeing Skrach Uglogwee Recipe for Disaster subquest",
                "Skrach Uglogwee Recipe for Disaster subquest",
                "Recipe for Disaster - Skrach Uglogwee subquest",
                "King Awowogei Recipe for Disaster subquest"
        };
        for (String prerequisite : prerequisites)
        {
            Quest chapter = prerequisite.contains("Awowogei")
                    ? Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI
                    : Quest.RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE;
            for (int state : new int[] {1, 0, 2})
            {
                PrerequisiteTrackerService service = serviceWithQuestState(chapter, state);
                PrerequisiteStatus status = service.evaluate(prerequisite).get(0);
                org.junit.Assert.assertEquals(prerequisite + " state " + state,
                        state == 2, status.isCompleted());
                assertTrue(status.getMarkerIcons().contains(PrerequisiteStatus.MarkerIcon.QUEST));
            }
        }
    }

    @Test
    public void unknownSubquestStaysIncomplete() throws Exception
    {
        PrerequisiteTrackerService service = serviceWithQuestState(Quest.RECIPE_FOR_DISASTER, 2);
        assertFalse(service.evaluate("Unknown Recipe for Disaster subquest").get(0).isCompleted());
    }

    private static PrerequisiteTrackerService serviceWithQuestState(Quest expectedQuest, int state) throws Exception
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[] {Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("runScript"))
                    {
                        Object[] scriptArgs = (Object[]) args[0];
                        org.junit.Assert.assertEquals(expectedQuest.getId(), scriptArgs[1]);
                        return null;
                    }
                    if (method.getName().equals("getIntStack"))
                    {
                        return new int[] {state};
                    }
                    throw new AssertionError("Unexpected client call: " + method.getName());
                });
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        Field field = PrerequisiteTrackerService.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        return service;
    }

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

    @Test
    public void shortDiaryPrerequisiteUsesVarbitAndDiaryIcon()
    {
        PrerequisiteTrackerService service = serviceWithVarbitValue(Varbits.DIARY_WILDERNESS_MEDIUM, 1);
        List<PrerequisiteStatus> statuses = service.evaluate("Wilderness Medium Diary");

        assertTrue(statuses.get(0).isCompleted());
        assertTrue(statuses.get(0).getMarkerIcons().contains(
                PrerequisiteStatus.MarkerIcon.ACHIEVEMENT_DIARY));
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
