package com.amtrollin.xtremetasker.models;

import java.util.Collections;
import java.util.List;

public class PrerequisiteStatus
{
    private final String text;
    private final boolean completed;
    private final List<CheckSpan> checkSpans;

    public PrerequisiteStatus(String text, boolean completed)
    {
        this(text, completed, List.of());
    }

    public PrerequisiteStatus(String text, boolean completed, List<CheckSpan> checkSpans)
    {
        this.text = text;
        this.completed = completed;
        this.checkSpans = checkSpans == null ? List.of() : Collections.unmodifiableList(checkSpans);
    }

    public String getText()
    {
        return text;
    }

    public boolean isCompleted()
    {
        return completed;
    }

    public List<CheckSpan> getCheckSpans()
    {
        return checkSpans;
    }

    public static class CheckSpan
    {
        private final int start;
        private final int end;
        private final boolean completed;

        public CheckSpan(int start, int end, boolean completed)
        {
            this.start = start;
            this.end = end;
            this.completed = completed;
        }

        public int getStart()
        {
            return start;
        }

        public int getEnd()
        {
            return end;
        }

        public boolean isCompleted()
        {
            return completed;
        }
    }
}
