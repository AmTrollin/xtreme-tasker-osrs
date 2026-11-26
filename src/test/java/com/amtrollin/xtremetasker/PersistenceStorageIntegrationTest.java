package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.Gson;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exercises actual plugin snapshots, account files, backups and config-profile mirrors. */
public class PersistenceStorageIntegrationTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private String originalHome;
    private final Gson gson = new Gson();
    private XtremeTaskerPlugin plugin;
    private ConfigManager config;
    private StateSaveQueue writer;

    @Before
    public void setUp() throws Exception
    {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temporary.getRoot().getAbsolutePath());
        // Construct RuneLite's real in-memory config implementation without its
        // periodic scheduler or connections to the user's profiles/cloud account.
        ScheduledExecutorService scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
                (proxy, method, args) -> null);
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        config = (ConfigManager) constructor.newInstance("test", scheduler, new EventBus(), null, gson, null, null, null);
        activateProfile(1);
        plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());
        writer = new StateSaveQueue();
        field(plugin, "gson", gson);
        field(plugin, "configManager", config);
        field(plugin, "stateSaveQueue", writer);
    }

    @After
    public void tearDown()
    {
        try { if (writer != null) writer.close(); }
        finally { System.setProperty("user.home", originalHome); }
    }

    @Test
    public void lateWriteKeepsAccountFileButCannotTouchNewProfilesConfig() throws Exception
    {
        ownAccount("account-a");
        plugin.manualCompletedTaskIdsForTesting().add("completed-a");
        save("account-a");
        writer.flush();
        plugin.manualCompletedTaskIdsForTesting().add("new-completion");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        writer.submit(() ->
        {
            started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Writer not released"); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        });
        try
        {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            save("account-a");
            plugin.manualCompletedTaskIdsForTesting().clear();
            activateProfile(2);
        }
        finally { release.countDown(); }
        writer.flush();
        PersistedState state = read("account-a");
        assertEquals(Set.of("completed-a", "new-completion"), state.getManualCompletedTaskIds());
        PersistedState previous = gson.fromJson(Files.readString(file("account-a").resolveSibling("account-a.json.backup_1")), PersistedState.class);
        assertEquals(Set.of("completed-a"), previous.getManualCompletedTaskIds());
        assertEquals("account-a", state.getAccountKey());
        assertNull(config.getConfiguration("xtremetasker", "state_account-a"));
        assertNull(config.getConfiguration("xtremetasker", "state_account-a_backup_1"));
    }

    @Test
    public void actualSavesRotateBackupsAndKeepCharactersSeparate() throws Exception
    {
        ownAccount("account-a");
        plugin.manualCompletedTaskIdsForTesting().add("first-a");
        save("account-a");
        writer.flush();
        plugin.manualCompletedTaskIdsForTesting().add("second-a");
        save("account-a");
        writer.flush();
        Path primary = file("account-a");
        PersistedState backup = gson.fromJson(Files.readString(primary.resolveSibling("account-a.json.backup_1")), PersistedState.class);
        assertEquals(Set.of("first-a"), backup.getManualCompletedTaskIds());
        assertEquals(Set.of("first-a", "second-a"), read("account-a").getManualCompletedTaskIds());
        assertNotNull(config.getConfiguration("xtremetasker", "state_account-a"));

        ownAccount("account-b");
        plugin.manualCompletedTaskIdsForTesting().clear();
        plugin.manualCompletedTaskIdsForTesting().add("only-b");
        save("account-b");
        writer.flush();
        assertEquals(Set.of("only-b"), read("account-b").getManualCompletedTaskIds());
        assertEquals(Set.of("first-a", "second-a"), read("account-a").getManualCompletedTaskIds());
    }

    @Test
    public void backupRecoverySkipsMalformedNewestBackup() throws Exception
    {
        ownAccount("account-a");
        plugin.manualCompletedTaskIdsForTesting().add("older-progress");
        save("account-a");
        writer.flush();
        String good = Files.readString(file("account-a"));
        Files.writeString(file("account-a").resolveSibling("account-a.json.backup_1"), "{broken");
        Files.writeString(file("account-a").resolveSibling("account-a.json.backup_2"), good);
        Method recovery = XtremeTaskerPlugin.class.getDeclaredMethod("loadNewestValidBackup", String.class);
        recovery.setAccessible(true);
        PersistedState restored = (PersistedState) recovery.invoke(plugin, "account-a");
        assertEquals(Set.of("older-progress"), restored.getManualCompletedTaskIds());
    }

    @Test
    public void acceptedUiActionRetainsItsWriterDuringReenable() throws Exception
    {
        ownAccount("account-a");
        java.util.List<Runnable> clientActions = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean clientThread = new java.util.concurrent.atomic.AtomicBoolean(false);
        plugin.setClientForTesting((net.runelite.api.Client) Proxy.newProxyInstance(net.runelite.api.Client.class.getClassLoader(),
                new Class<?>[]{net.runelite.api.Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isClientThread")) return clientThread.get();
                    if (method.getName().equals("getLocalPlayer")) return null;
                    if (method.getName().equals("getLauncherDisplayName")) return "Character A";
                    throw new AssertionError(method.getName());
                }));
        field(plugin, "clientThread", new net.runelite.client.callback.ClientThread()
        {
            @Override public void invokeLater(Runnable action) { clientActions.add(action); }
        });
        com.amtrollin.xtremetasker.models.XtremeTask task = new com.amtrollin.xtremetasker.models.XtremeTask(
                "accepted-task", "Accepted task", com.amtrollin.xtremetasker.enums.TaskSource.COMBAT_ACHIEVEMENT,
                com.amtrollin.xtremetasker.enums.TaskTier.EASY);
        plugin.tasksForTesting().add(task);
        StateSaveLifecycle oldLifecycle = new StateSaveLifecycle(new EventBus(), Runnable::run, writer,
                () -> { }, () -> { }, error -> fail(error.toString()));
        field(plugin, "saveLifecycle", oldLifecycle);
        field(plugin, "initializedLifecycle", oldLifecycle);
        plugin.toggleTaskCompletedAndPersist(task);
        assertFalse(plugin.isTaskCompleted(task));

        StateSaveQueue nextWriter = new StateSaveQueue();
        StateSaveLifecycle nextLifecycle = new StateSaveLifecycle(new EventBus(), Runnable::run, nextWriter,
                () -> { }, () -> { }, error -> fail(error.toString()));
        field(plugin, "stateSaveQueue", nextWriter);
        field(plugin, "saveLifecycle", nextLifecycle);
        nextWriter.close(); // routing the old action to this writer would now fail
        try
        {
            clientThread.set(true);
            clientActions.remove(0).run();
            writer.flush();
            assertEquals(Set.of("accepted-task"), read("account-a").getManualCompletedTaskIds());
        }
        finally
        {
            oldLifecycle.finish().join();
            nextLifecycle.finish().join();
        }
    }

    private void ownAccount(String key) throws Exception
    {
        field(plugin, "activeAccountKey", key);
        field(plugin, "loadedStateAccountKey", key);
    }

    private void save(String key) throws Exception
    {
        Method save = XtremeTaskerPlugin.class.getDeclaredMethod("saveStateForAccount", String.class);
        save.setAccessible(true);
        save.invoke(plugin, key);
    }

    private PersistedState read(String key) throws Exception
    {
        return gson.fromJson(Files.readString(file(key)), PersistedState.class);
    }

    private Path file(String key)
    {
        return temporary.getRoot().toPath().resolve(".runelite/xtreme-tasker-states/" + key + ".json");
    }

    private void activateProfile(long id) throws Exception
    {
        Constructor<ConfigProfile> profile = ConfigProfile.class.getDeclaredConstructor(long.class);
        profile.setAccessible(true);
        Class<?> dataClass = Class.forName("net.runelite.client.config.ConfigData");
        Constructor<?> data = dataClass.getDeclaredConstructor(java.io.File.class);
        data.setAccessible(true);
        synchronized (config)
        {
            field(config, "profile", profile.newInstance(id));
            field(config, "configProfile", data.newInstance(temporary.getRoot().toPath().resolve("profile-" + id + ".properties").toFile()));
        }
    }

    private static void field(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
