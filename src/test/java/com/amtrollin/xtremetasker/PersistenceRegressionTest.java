package com.amtrollin.xtremetasker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PersistenceRegressionTest
{
    @Test
    public void accountNameKeySeparatesCharactersSharingLegacyHash()
    {
        String xtremeTaskrKey = accountNameKey("XtremeTaskr");
        String xtremeTaskrDifferentCaseKey = accountNameKey("xtremetaskr");
        String amTrollinKey = accountNameKey("AmTrollin");

        assertEquals("Character key should be stable across display-name case changes",
                xtremeTaskrKey, xtremeTaskrDifferentCaseKey);
        assertFalse("Different characters must not share the same scoped save key",
                xtremeTaskrKey.equals(amTrollinKey));
        assertEquals("17438951129000919538",
                legacyAccountKeyFromScopedKey("17438951129000919538_" + xtremeTaskrKey));
    }

    private static String accountNameKey(String characterName)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        return plugin.accountNameKey(characterName);
    }

    private static String legacyAccountKeyFromScopedKey(String accountKey)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        return plugin.legacyAccountKeyFromScopedKey(accountKey);
    }
}
