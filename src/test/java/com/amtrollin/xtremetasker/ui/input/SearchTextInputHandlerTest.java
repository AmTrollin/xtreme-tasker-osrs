package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchTextInputHandlerTest
{
    @Test
    public void formerShortcutsPassThroughOnBothTabs() throws Exception
    {
        XtremeTaskerOverlay overlay = new XtremeTaskerOverlay(null, new XtremeTaskerPlugin(), null, null);
        overlay.setPanelOpen(true);
        SearchTextInputHandler handler = new SearchTextInputHandler(overlay);
        Canvas canvas = new Canvas();
        Field activeTab = XtremeTaskerOverlay.class.getDeclaredField("activeTab");
        activeTab.setAccessible(true);
        for (XtremeTaskerOverlay.MainTab tab : new XtremeTaskerOverlay.MainTab[] {
                XtremeTaskerOverlay.MainTab.CURRENT, XtremeTaskerOverlay.MainTab.TASKS})
        {
            activeTab.set(overlay, tab);
            for (int code : new int[] {KeyEvent.VK_R, KeyEvent.VK_C, KeyEvent.VK_W,
                    KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE,
                    KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                    KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN})
            {
                KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
                handler.keyPressed(event);
                assertFalse("Key must pass through: " + code, event.isConsumed());
                assertTrue("Escape must leave the panel open", overlay.isPanelOpen());
            }
            KeyEvent typed = new KeyEvent(canvas, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'r');
            handler.keyTyped(typed);
            assertFalse("Typing outside search must pass through", typed.isConsumed());
        }
    }
}
