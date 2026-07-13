package net.niwa.kinsaku.discordsystem.model;

import com.google.gson.annotations.SerializedName;

public class WhitelistRequest {
    @SerializedName("discord_id")
    private final String discordId;
    
    @SerializedName("university")
    private final String university;
    
    @SerializedName("minecraft_id")
    private final String minecraftId;
    
    @SerializedName("edition")
    private final String edition;

    @SerializedName("discord_username")
    private final String discordUsername;

    public WhitelistRequest(String discordId, String university, String minecraftId, String edition, String discordUsername) {
        this.discordId = discordId;
        this.university = university;
        this.minecraftId = minecraftId;
        this.edition = edition;
        this.discordUsername = discordUsername;
    }

    public String getDiscordId() { return discordId; }
    public String getUniversity() { return university; }
    public String getMinecraftId() { return minecraftId; }
    public String getEdition() { return edition; }
    public String getDiscordUsername() { return discordUsername; }
}
