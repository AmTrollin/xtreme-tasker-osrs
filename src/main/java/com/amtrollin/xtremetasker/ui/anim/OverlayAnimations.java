package com.amtrollin.xtremetasker.ui.anim;

import java.util.HashMap;
import java.util.Map;

public final class OverlayAnimations
{
    private final Map<String, Long> completionAnimStartMs = new HashMap<>();
    private long rollAnimStartMs = 0L;

    private final int completeAnimMs;
    private final int rollAnimMs;

    public OverlayAnimations(int completeAnimMs, int rollAnimMs)
    {
        this.completeAnimMs = completeAnimMs;
        this.rollAnimMs = rollAnimMs;
    }

    public void startCompletionAnim(String id)
    {
        if (id == null) return;
        completionAnimStartMs.put(id, System.currentTimeMillis());
    }

    /** 0..1 eased value while animating, otherwise 0. */
    public float completionProgress(String id)
    {
        if (id == null) return 0f;
        Long start = completionAnimStartMs.get(id);
        if (start == null) return 0f;

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed <= 0) return 0f;
        if (elapsed >= completeAnimMs) return 0f;

        float t = (float) elapsed / (float) completeAnimMs;
        // easeOutQuad
        return 1f - (1f - t) * (1f - t);
    }

    public void prune()
    {
        long now = System.currentTimeMillis();
        completionAnimStartMs.entrySet().removeIf(e -> (now - e.getValue()) > (completeAnimMs + 50));
        if (rollAnimStartMs > 0 && (now - rollAnimStartMs) >= rollAnimMs)
        {
            // let isRolling() go false without leaving stale long-term state
            rollAnimStartMs = 0L;
        }
    }

    public void startRoll()
    {
        rollAnimStartMs = System.currentTimeMillis();
    }

    public boolean isRolling()
    {
        return rollAnimStartMs > 0 && (System.currentTimeMillis() - rollAnimStartMs) < rollAnimMs;
    }

    public long rollStartMs()
    {
        return rollAnimStartMs;
    }

    /** For your rolling-line logic that needs elapsed. */
    public long rollElapsedMs()
    {
        if (rollAnimStartMs <= 0) return 0L;
        return Math.max(0L, System.currentTimeMillis() - rollAnimStartMs);
    }
}
