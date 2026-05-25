package com.amtrollin.xtremetasker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class XtremeTaskerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(XtremeTaskerPlugin.class);
        RuneLite.main(args);
    }
}
