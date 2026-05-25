package com.amtrollin.xtremetasker.models;

/** Holds when and how a task was completed. */
public final class CompletionInfo {
    public enum Source { MANUAL, SYNCED }

    public final long timestamp;
    public final Source source;

    public CompletionInfo(long timestamp, Source source) {
        this.timestamp = timestamp;
        this.source = source;
    }
}
