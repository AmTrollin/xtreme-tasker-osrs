package com.amtrollin.xtremetasker.ui.current.models;

import com.amtrollin.xtremetasker.ui.current.CurrentTabLayout;

public final class CurrentTabState
{
    private final CurrentTabLayout layout;

    public CurrentTabState(CurrentTabLayout layout)
    {
        this.layout = layout;
    }

    public CurrentTabLayout layout()
    {
        return layout;
    }
}
