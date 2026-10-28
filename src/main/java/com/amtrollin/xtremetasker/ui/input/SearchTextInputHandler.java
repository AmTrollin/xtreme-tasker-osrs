package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import java.awt.event.KeyEvent;
import lombok.RequiredArgsConstructor;
import net.runelite.api.VarClientInt;
import net.runelite.client.input.KeyListener;

/** Handles text entry only while the task search field is focused. */
@RequiredArgsConstructor
public final class SearchTextInputHandler implements KeyListener
{
    private final XtremeTaskerOverlay a;

    private boolean isSearchActive()
    {
        return a.plugin().isOverlayEnabled() && a.isPanelOpen()
                && a.activeTab() == XtremeTaskerOverlay.MainTab.TASKS
                && a.taskQuery().searchFocused
                && a.client().getVarcIntValue(VarClientInt.INPUT_TYPE) == 0;
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (e == null || !isSearchActive() || e.isControlDown() || e.isMetaDown() || e.isAltDown())
        {
            return;
        }
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_BACK_SPACE) {
            String s = a.taskQuery().searchText;
            int ss = a.taskQuery().searchSelStart;
            int se = a.taskQuery().searchSelEnd;
            if (ss >= 0 && se > ss && s != null && se <= s.length()) {
                a.taskQuery().searchText = s.substring(0, ss) + s.substring(se);
                a.taskQuery().searchSelStart = -1;
                a.taskQuery().searchSelEnd = -1;
            } else if (s != null && !s.isEmpty()) {
                a.taskQuery().searchText = s.substring(0, s.length() - 1);
            }
            a.resetTaskListViewAfterQueryChange();
            e.consume();
            return;
        }

    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        if (e == null || !isSearchActive() || e.isControlDown() || e.isMetaDown() || e.isAltDown())
        {
            return;
        }
        char c = e.getKeyChar();

        // Ignore control characters
        if (c < 32 || c == 127) {
            return;
        }

        // Normalize to lower-case so search feels consistent
        c = Character.toLowerCase(c);

        // If there's a selection, replace it with the typed character
        int selStart = a.taskQuery().searchSelStart;
        int selEnd = a.taskQuery().searchSelEnd;
        String s = a.taskQuery().searchText;
        if (s == null) s = "";
        if (selStart >= 0 && selEnd > selStart && selEnd <= s.length()) {
            s = s.substring(0, selStart) + s.substring(selEnd);
            a.taskQuery().searchSelStart = -1;
            a.taskQuery().searchSelEnd = -1;
            a.taskQuery().searchText = s;
        }

        if (s.length() < 40) {
            a.taskQuery().searchText = s + c;
            a.resetTaskListViewAfterQueryChange();
        }
        e.consume();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // no-op
    }

}
