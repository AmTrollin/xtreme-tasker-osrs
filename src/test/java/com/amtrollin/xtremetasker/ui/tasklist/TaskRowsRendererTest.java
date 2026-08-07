package com.amtrollin.xtremetasker.ui.tasklist;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class TaskRowsRendererTest
{
    @Test
    public void timeSpentKeepsMinutesForHourDurations() throws Exception
    {
        assertEquals("7m", formatDuration(7 * 60));
        assertEquals("59m", formatDuration(59 * 60 + 59));
        assertEquals("1h", formatDuration(60 * 60));
        assertEquals("1h 24m", formatDuration(84 * 60));
        assertEquals("3h 5m", formatDuration(185 * 60));
    }

    @Test
    public void timeSpentNeverConvertsHoursToDays() throws Exception
    {
        assertEquals("24h", formatDuration(24 * 60 * 60));
        assertEquals("48h", formatDuration(48 * 60 * 60));
        assertEquals("54h 23m", formatDuration((54 * 60 + 23) * 60));
    }

    private static String formatDuration(long seconds) throws Exception
    {
        Method method = TaskRowsRenderer.class.getDeclaredMethod("formatDuration", long.class);
        method.setAccessible(true);
        return (String) method.invoke(null, seconds);
    }
}
