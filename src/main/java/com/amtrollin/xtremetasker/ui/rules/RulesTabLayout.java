package com.amtrollin.xtremetasker.ui.rules;

import java.awt.Rectangle;

public final class RulesTabLayout
{
    public enum SubTab { RULES, DATA_SYNCS }

    public final Rectangle viewportBounds = new Rectangle();
    public final Rectangle githubReadmeLinkBounds = new Rectangle();
    public final Rectangle syncProgressButtonBounds = new Rectangle();
    public final Rectangle syncCaFoundReviewButtonBounds = new Rectangle();
    public final Rectangle syncCaReviewButtonBounds = new Rectangle();
    public final Rectangle syncCaReviewIgnoreButtonBounds = new Rectangle();

    // Sub-tab toggles (rendered at top of help panel)
    public final Rectangle subTabRulesBounds = new Rectangle();
    public final Rectangle subTabDataSyncsBounds = new Rectangle();
}
