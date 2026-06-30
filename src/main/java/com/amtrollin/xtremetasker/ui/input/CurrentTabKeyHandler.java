package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.ui.text.UiText;
import java.awt.event.KeyEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class CurrentTabKeyHandler
{
    private final OverlayInputAccess a;

    private static final long MSG_COOLDOWN_MS = 900;
    private long lastMsgAtMs = 0;
    private String lastMsg = null;

    public boolean handleKeyPressed(KeyEvent e)
    {
        int code = e.getKeyCode();
        boolean handledOrBlocked = false;

        if (code == KeyEvent.VK_R)
        {
            var cur = a.plugin().getCurrentTask();
            boolean curDone = (cur != null) && a.plugin().isTaskCompleted(cur);
            boolean rollEnabled = (cur == null) || curDone;

            if (!rollEnabled)
            {
                notifyUser(UiText.get("input.roll_blocked"));
                return true;
            }

            handledOrBlocked = true;
        }

        if (code == KeyEvent.VK_C)
        {
            var cur = a.plugin().getCurrentTask();
            if (cur == null)
            {
                notifyUser("No current task to complete.");
                return true;
            }

            handledOrBlocked = true;
        }

        if (a.handleCurrentKey(e))
        {
            return true;
        }

        return handledOrBlocked;
    }

    private void notifyUser(String msg)
    {
        if (msg == null || msg.trim().isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (msg.equals(lastMsg) && (now - lastMsgAtMs) < MSG_COOLDOWN_MS)
        {
            return;
        }

        lastMsgAtMs = now;
        lastMsg = msg;

        a.plugin().pushGameMessage(msg);
    }
}
