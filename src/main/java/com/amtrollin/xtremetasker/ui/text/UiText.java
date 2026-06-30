package com.amtrollin.xtremetasker.ui.text;

import java.util.Locale;
import java.util.ResourceBundle;

public final class UiText
{
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("ui_text", Locale.ROOT);

    private UiText()
    {
    }

    public static String get(String key)
    {
        return BUNDLE.getString(key);
    }

    public static String format(String key, Object... args)
    {
        return String.format(Locale.US, get(key), args);
    }
}
