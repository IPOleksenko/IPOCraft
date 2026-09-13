package com.IPOleksenko.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Account {
    private String username;
    private String uuid;
    private String type; // "MICROSOFT" or "OFFLINE"
    private String accessToken;
    private String authSession;
    private String refreshToken;
    private long expiresAt;
    private String skinUrl;
    private String customIconPath;

    public Account() {
        this.type = "OFFLINE";
        this.accessToken = "0";
        this.authSession = "0";
    }

    public Account(String username, String uuid, String type, String accessToken, String authSession, String refreshToken, long expiresAt, String skinUrl) {
        this.username = username;
        this.uuid = uuid;
        this.type = type != null ? type : "OFFLINE";
        this.accessToken = accessToken != null ? accessToken : "0";
        this.authSession = authSession != null ? authSession : "0";
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.skinUrl = skinUrl;
    }

    public static Account createOffline(String username) {
        String clean = (username != null) ? username.trim() : "Player";
        if (clean.isEmpty()) clean = "Player";
        String offlineUuid = generateOfflineUuid(clean);
        return new Account(clean, offlineUuid, "OFFLINE", "0", "0", null, 0, null);
    }

    public static String generateOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String generateRandomUuid() {
        return UUID.randomUUID().toString();
    }

    public String getUsername() {
        return username != null ? username : "Player";
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid != null ? uuid : UUID.randomUUID().toString();
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getType() {
        return type != null ? type : "OFFLINE";
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMicrosoft() {
        return "MICROSOFT".equalsIgnoreCase(getType());
    }

    public String getAccessToken() {
        return accessToken != null ? accessToken : "0";
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAuthSession() {
        return authSession != null ? authSession : "0";
    }

    public void setAuthSession(String authSession) {
        this.authSession = authSession;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return isMicrosoft() && System.currentTimeMillis() >= expiresAt;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl;
    }

    public String getCustomIconPath() {
        return customIconPath;
    }

    public void setCustomIconPath(String customIconPath) {
        this.customIconPath = customIconPath;
    }

    @Override
    public String toString() {
        return getUsername() + " (" + (isMicrosoft() ? "Microsoft" : "Offline") + ")";
    }
}
