package com.amtrollin.xtremetasker.models.verification;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class TaskVerification
{
    @SerializedName("method")
    private VerificationType type;

    @SerializedName("taskId")
    private Integer taskId;

    @SerializedName("itemIds")
    private int[] itemIds;

    @SerializedName("count")
    private Integer count;

    // achievement-diary fields
    @SerializedName("region")
    private String region;

    @SerializedName("difficulty")
    private String difficulty;

    // skill fields: map of skill name -> required XP (all entries are 13034431 = level 99)
    @SerializedName("experience")
    private Map<String, Long> experience;

    public VerificationType getType()
    {
        return type;
    }

    public Integer getTaskId()
    {
        return taskId;
    }

    public int[] getItemIds()
    {
        return itemIds;
    }

    public Integer getCount()
    {
        return count;
    }

    public String getRegion()
    {
        return region;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public Map<String, Long> getExperience()
    {
        return experience;
    }

    public enum VerificationType
    {
        @SerializedName("COMBAT_ACHIEVEMENT")
        COMBAT_ACHIEVEMENT,

        @SerializedName("collection-log")
        COLLECTION_LOG,

        @SerializedName("achievement-diary")
        ACHIEVEMENT_DIARY,

        @SerializedName("skill")
        SKILL
    }
}
