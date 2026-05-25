package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public class TaskHudOverlay extends Overlay {
    private static final UiPalette P = UiPalette.DEFAULT;

    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int ARC = 4;
    private static final int SPRITE_SIZE = 28;
    private static final int SPRITE_GAP = 3;
    private static final int BADGE_H = 20;
    private static final int BADGE_GAP = 4;

    // CA tier sword sprite IDs (SpriteID.CaTierSwords: _0=Easy, _1=Medium, _2=Hard, _3=Elite, _4=Master, _5=Grandmaster)
    private static final EnumMap<TaskTier, Integer> CA_TIER_SPRITE_IDS = new EnumMap<>(TaskTier.class);
    static {
        CA_TIER_SPRITE_IDS.put(TaskTier.EASY,        3393);
        CA_TIER_SPRITE_IDS.put(TaskTier.MEDIUM,      3394);
        CA_TIER_SPRITE_IDS.put(TaskTier.HARD,        3395);
        CA_TIER_SPRITE_IDS.put(TaskTier.ELITE,       3396);
        CA_TIER_SPRITE_IDS.put(TaskTier.MASTER,      3397);
        CA_TIER_SPRITE_IDS.put(TaskTier.GRANDMASTER, 3398);
    }

    private final XtremeTaskerPlugin plugin;
    private final SpriteManager spriteManager;

    private BufferedImage cachedSprite = null;
    private String cachedSpriteKey = null;

    @Inject
    public TaskHudOverlay(XtremeTaskerPlugin plugin, SpriteManager spriteManager) {
        this.plugin = plugin;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    private static final int WRAP_CHARS = 30;

    @Override
    public Dimension render(Graphics2D g) {
        if (!plugin.isLoggedIn()) return null;
        boolean rolling = plugin.isRolling();
        XtremeTask task = rolling ? null : plugin.getCurrentTask();
        String label = rolling ? "Rolling..." : (task != null ? task.getName() : "No task assigned");

        // Resolve source badge
        String srcBadge = (task != null) ? TaskLabelFormatter.shortSource(task.getSource()) : null;
        boolean hasBadge = srcBadge != null;

        // Resolve item sprite
        String spriteKey = getSpriteKey(task);
        if (!java.util.Objects.equals(spriteKey, cachedSpriteKey)) {
            cachedSpriteKey = spriteKey;
            cachedSprite = resolveSprite(task);
        }
        boolean hasSprite = cachedSprite != null;

        g.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = g.getFontMetrics();

        // Split label into up to two lines at a word boundary after WRAP_CHARS.
        String line1, line2;
        if (label.length() <= WRAP_CHARS) {
            line1 = label;
            line2 = null;
        } else {
            int split = label.lastIndexOf(' ', WRAP_CHARS);
            if (split <= 0) split = WRAP_CHARS; // no space found — hard break
            line1 = label.substring(0, split).trim();
            line2 = label.substring(split).trim();
        }

        int line1W = fm.stringWidth(line1);
        int line2W = line2 != null ? fm.stringWidth(line2) : 0;
        int textH = fm.getHeight();
        int lineGap = 2;

        int badgeBoxW = 0, badgeW = 0;
        if (hasBadge) {
            FontMetrics sfm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
            badgeBoxW = Math.max(26, sfm.stringWidth(srcBadge) + 8 * 2);
            badgeW = badgeBoxW + BADGE_GAP;
        }
        int spriteW = hasSprite ? SPRITE_SIZE + SPRITE_GAP : 0;
        int textBlockW = Math.max(line1W, line2W);
        int textBlockH = line2 != null ? textH * 2 + lineGap : textH;
        int boxW = badgeW + spriteW + textBlockW + PADDING_X * 2;
        int boxH = Math.max(textBlockH + PADDING_Y * 2, Math.max(BADGE_H, SPRITE_SIZE) + PADDING_Y * 2);

        // Background
        g.setColor(P.UI_BG);
        g.fillRoundRect(0, 0, boxW, boxH, ARC, ARC);

        // Border
        g.setColor(P.UI_EDGE_LIGHT);
        g.drawRoundRect(0, 0, boxW - 1, boxH - 1, ARC, ARC);

        int textX = PADDING_X;

        // Source badge — centered vertically
        if (hasBadge) {
            int badgeY = (boxH - BADGE_H) / 2;
            TaskRowsRenderer.drawSourceBadge(g, textX, badgeY, srcBadge, P.UI_EDGE_DARK, P.UI_EDGE_LIGHT, P.UI_GOLD, P.UI_TEXT);
            g.setFont(FontManager.getRunescapeFont()); // restore after shared method
            textX += badgeBoxW + BADGE_GAP;
        }

        // Item sprite — centered vertically
        if (hasSprite) {
            int spriteY = (boxH - SPRITE_SIZE) / 2;
            g.drawImage(cachedSprite, textX, spriteY, SPRITE_SIZE, SPRITE_SIZE, null);
            textX += SPRITE_SIZE + SPRITE_GAP;
        }

        // Baselines: center the text block vertically
        int textBlockTop = (boxH - textBlockH) / 2;
        int baseline1 = textBlockTop + fm.getAscent();
        int baseline2 = baseline1 + textH + lineGap;

        g.setColor(P.UI_TEXT);
        g.drawString(line1, textX, baseline1);

        if (line2 != null) {
            g.drawString(line2, textX, baseline2);
        }

        return new Dimension(boxW, boxH);
    }

    private String getSpriteKey(XtremeTask task) {
        if (task == null) return null;
        if (task.getIconItemId() != null && task.getIconItemId() > 0) {
            return "item:" + task.getIconItemId();
        }
        if (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT && task.getTier() != null) {
            return "ca-sprite:" + task.getTier().name();
        }
        return null;
    }

    private BufferedImage resolveSprite(XtremeTask task) {
        if (task == null) return null;
        if (task.getIconItemId() != null && task.getIconItemId() > 0) {
            return plugin.getItemImage(task.getIconItemId());
        }
        if (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT && task.getTier() != null) {
            Integer spriteId = CA_TIER_SPRITE_IDS.get(task.getTier());
            if (spriteId != null) {
                return spriteManager.getSprite(spriteId, 0);
            }
        }
        return null;
    }
}
