package net.niwa.kinsaku.discordsystem.model;

import com.google.gson.annotations.SerializedName;

public class WhitelistResponse {
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("minecraft_uuid")
    private String minecraftUuid;
    
    @SerializedName("error")
    private String error;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getMinecraftUuid() { return minecraftUuid; }
    public String getError() { return error; }
}
