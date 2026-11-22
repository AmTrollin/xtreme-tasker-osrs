package com.amtrollin.xtremetasker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class XtremeTaskerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        if (java.util.Arrays.asList(args).contains("--verify-runtime"))
        {
            java.nio.file.Path home = java.nio.file.Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
            if (!".dev-user-home".equals(home.getFileName().toString()))
                throw new IllegalStateException("Expected an isolated .dev-user-home: " + home);
            if (XtremeTaskerPluginTest.class.getResource("/ui_text.properties") == null)
                throw new IllegalStateException("XT UI resources are missing from the runtime classpath");
            System.out.println("Java home for user data: " + home);
            System.out.println("RuneLite directory: " + RuneLite.RUNELITE_DIR);
            System.out.println("XT saves: " + home.resolve(".runelite/xtreme-tasker-states"));
            if (!RuneLite.RUNELITE_DIR.toPath().toAbsolutePath().normalize().equals(home.resolve(".runelite")))
                throw new IllegalStateException("RuneLite data is outside the isolated home");
            System.out.println("Runtime isolation verified; no game client started or saves written.");
            return;
        }
        ExternalPluginManager.loadBuiltin(XtremeTaskerPlugin.class);
        RuneLite.main(args);
    }
}
