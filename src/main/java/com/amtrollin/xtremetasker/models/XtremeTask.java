package com.amtrollin.xtremetasker.models;

import com.amtrollin.xtremetasker.enums.*;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import lombok.Getter;

@Getter
public class XtremeTask {
    private final String id;
    private final String name;
    private final TaskSource source;
    private final TaskTier tier;

    private final Integer iconItemId; // nullable
    private final String iconKey;     // nullable

    // Optional enrichment fields (nullable)
    private final String description;
    private final String prereqs;
    private final String wikiUrl;
    private final String tip;
    private final TaskVerification verification;

    // Backwards-compatible constructor
    public XtremeTask(String id, String name, TaskSource source, TaskTier tier) {
        this(id, name, source, tier, null, null, null, null, null, null, null);
    }

    // Full constructor (used by JSON pack, now includes enrichment)
    public XtremeTask(
            String id,
            String name,
            TaskSource source,
            TaskTier tier,
            Integer iconItemId,
            String iconKey,
            String description,
            String prereqs,
            String wikiUrl,
            TaskVerification verification,
            String tip
    ) {
        this.name = safeTrimToNull(name);
        this.source = source;
        this.tier = tier;
        this.iconItemId = iconItemId;
        this.iconKey = safeTrimToNull(iconKey);
        this.description = safeTrimToNull(description);
        this.prereqs = safeTrimToNull(prereqs);
        this.wikiUrl = safeTrimToNull(wikiUrl);
        this.tip = safeTipTrimToNull(tip);
        this.verification = verification;

        // Critical: NEVER allow null/blank IDs (null IDs cause completion collisions)
        String trimmedId = safeTrimToNull(id);
        if (trimmedId == null) {
            trimmedId = generateStableId(this.name, this.source, this.tier, this.wikiUrl, this.iconKey, this.iconItemId);
        }
        this.id = trimmedId;
    }

    @Override
    public String toString() {
        return name;
    }

    private static String safeTrimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeTipTrimToNull(String s) {
        String t = safeTrimToNull(s);
        if (t == null || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("n/a") || t.equals("-")) {
            return null;
        }
        return t;
    }

    /**
     * Deterministic fallback id when pack has missing ids.
     * Keeps completion stable across restarts as long as these fields stay the same.
     */
    private static String generateStableId(
            String name,
            TaskSource source,
            TaskTier tier,
            String wikiUrl,
            String iconKey,
            Integer iconItemId
    ) {
        String payload =
                "name=" + Objects.toString(name, "") +
                        "|source=" + Objects.toString(source, "") +
                        "|tier=" + Objects.toString(tier, "") +
                        "|wiki=" + Objects.toString(wikiUrl, "") +
                        "|iconKey=" + Objects.toString(iconKey, "") +
                        "|iconItemId=" + Objects.toString(iconItemId, "");

        String hex = sha1Hex(payload);
        // keep it short but collision-resistant enough for our use
        return "gen_" + hex.substring(0, 16);
    }

    private static String sha1Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // ultra-safe fallback (still deterministic-ish)
            return Integer.toHexString(Objects.hashCode(s));
        }
    }
}
