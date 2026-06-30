package com.amtrollin.xtremetasker.ui.input;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.awt.event.KeyEvent;
import lombok.RequiredArgsConstructor;
import net.runelite.api.VarClientInt;
import net.runelite.client.input.KeyListener;

public final class OverlayKeyHandler implements KeyListener {
    private final OverlayInputAccess a;
    private final TasksTabKeyHandler tasksTabKeyHandler;
    private final CurrentTabKeyHandler currentTabKeyHandler;

    public OverlayKeyHandler(OverlayInputAccess a)
    {
        this.a = a;
        this.tasksTabKeyHandler = new TasksTabKeyHandler(a);
        this.currentTabKeyHandler = new CurrentTabKeyHandler(a);
    }


    /**
     * Returns true when the client is currently accepting text input (chatbox, dialogs, etc).
     * Prevents overlay hotkeys from leaking characters into chat.
     */
    private boolean isClientTyping() {
        return a.client().getVarcIntValue(VarClientInt.INPUT_TYPE) != 0;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e == null) {
            return;
        }

        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        int code = e.getKeyCode();

        // ESC always closes panel (even if search focused or client typing)
        if (code == KeyEvent.VK_ESCAPE) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return;
        }

        // If the user is typing in chat or another input, don't steal hotkeys.
        // This prevents keys like "r" from appearing in chat.
        if (isClientTyping()) {
            return;
        }

        // If search is focused, handle ONLY non-text keys here
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskQuery().searchFocused) {
            boolean ctrl = e.isControlDown() || e.isMetaDown();

            if (ctrl && code == KeyEvent.VK_A) {
                String s = a.taskQuery().searchText != null ? a.taskQuery().searchText : "";
                a.taskQuery().searchSelStart = 0;
                a.taskQuery().searchSelEnd = s.length();
                e.consume();
                return;
            }

            if (ctrl && code == KeyEvent.VK_C) {
                String s = a.taskQuery().searchText != null ? a.taskQuery().searchText : "";
                int ss = a.taskQuery().searchSelStart;
                int se = a.taskQuery().searchSelEnd;
                String toCopy = (ss >= 0 && se > ss && se <= s.length()) ? s.substring(ss, se) : s;
                setClipboard(toCopy);
                e.consume();
                return;
            }

            if (ctrl && code == KeyEvent.VK_V) {
                String pasted = getClipboard();
                if (pasted != null && !pasted.isEmpty()) {
                    pasted = pasted.replaceAll("[\\p{Cntrl}]", "").toLowerCase();
                    String s = a.taskQuery().searchText != null ? a.taskQuery().searchText : "";
                    int ss = a.taskQuery().searchSelStart;
                    int se = a.taskQuery().searchSelEnd;
                    String result;
                    if (ss >= 0 && se > ss && ss <= s.length() && se <= s.length()) {
                        result = s.substring(0, ss) + pasted + s.substring(se);
                    } else {
                        result = s + pasted;
                    }
                    if (result.length() > 40) result = result.substring(0, 40);
                    a.taskQuery().searchText = result;
                    a.taskQuery().searchSelStart = -1;
                    a.taskQuery().searchSelEnd = -1;
                    a.resetTaskListViewAfterQueryChange();
                }
                e.consume();
                return;
            }

            if (ctrl && code == KeyEvent.VK_X) {
                String s = a.taskQuery().searchText != null ? a.taskQuery().searchText : "";
                int ss = a.taskQuery().searchSelStart;
                int se = a.taskQuery().searchSelEnd;
                if (ss >= 0 && se > ss && se <= s.length()) {
                    setClipboard(s.substring(ss, se));
                    a.taskQuery().searchText = s.substring(0, ss) + s.substring(se);
                    a.taskQuery().searchSelStart = -1;
                    a.taskQuery().searchSelEnd = -1;
                    a.resetTaskListViewAfterQueryChange();
                }
                e.consume();
                return;
            }

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

            if (code == KeyEvent.VK_ENTER) {
                a.taskQuery().searchFocused = false;
                e.consume();
                return;
            }

            // Let keyTyped handle actual characters.
            return;
        }

        // Normal key handling when search is NOT focused
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS) {
            if (tasksTabKeyHandler.handleKeyPressed(e)) {
                e.consume();
            }
            return;
        }

        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT) {
            if (currentTabKeyHandler.handleKeyPressed(e)) {
                e.consume();
            }
        }

    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e == null) {
            return;
        }

        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        // If we're not actively typing into the overlay search box,
        // swallow typed characters so overlay hotkeys don't leak into chat.
        if (a.activeTab() != OverlayInputAccess.MainTab.TASKS || !a.taskQuery().searchFocused) {
            e.consume();
            return;
        }

        // If the client is typing (chatbox/dialog), don't capture characters for search.
        // (Search focus should generally be false in this scenario anyway.)
        if (isClientTyping()) {
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
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // no-op
    }

    private void setClipboard(String text) {
        try {
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        } catch (Exception ignored) {}
    }

    private String getClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
            return null;
        }
    }
}
