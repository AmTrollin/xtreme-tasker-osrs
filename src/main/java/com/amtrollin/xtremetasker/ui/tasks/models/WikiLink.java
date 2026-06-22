package com.amtrollin.xtremetasker.ui.tasks.models;

public final class WikiLink
{
    private final String label;
    private final String url;

    public WikiLink(String label, String url)
    {
        this.label = safe(label);
        this.url = safe(url);
    }

    public String label()
    {
        return label;
    }

    public String url()
    {
        return url;
    }

    public boolean isValid()
    {
        return !label.isEmpty() && !url.isEmpty();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value.trim();
    }
}
