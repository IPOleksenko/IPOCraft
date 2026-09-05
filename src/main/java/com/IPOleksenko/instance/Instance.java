package com.IPOleksenko.instance;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class Instance {
    private String id;
    private String name;
    private String minecraftVersion;
    private String modloader;
    private String modloaderVersion;
    private String icon;
    private long createdTimestamp;
    private long lastPlayedTimestamp;
    private Integer customMemoryMb;
    private String customJvmArgs;
    private String customJavaPath;

    public Instance() {
        this.id = UUID.randomUUID().toString();
        this.createdTimestamp = System.currentTimeMillis();
        this.modloader = "";
        this.modloaderVersion = "";
        this.icon = "grass";
    }

    public Instance(String name, String minecraftVersion) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.minecraftVersion = minecraftVersion;
        this.modloader = "";
        this.modloaderVersion = "";
        this.icon = "grass";
        this.createdTimestamp = System.currentTimeMillis();
    }

    public Instance(String name, String minecraftVersion, String modloader, String modloaderVersion) {
        this(name, minecraftVersion);
        if (modloader != null) this.modloader = modloader;
        if (modloaderVersion != null) this.modloaderVersion = modloaderVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Minecraft " + (minecraftVersion != null ? minecraftVersion : "");
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMinecraftVersion() {
        return minecraftVersion != null ? minecraftVersion : "";
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getModloader() {
        return modloader != null ? modloader : "";
    }

    public void setModloader(String modloader) {
        this.modloader = modloader;
    }

    public String getModloaderVersion() {
        return modloaderVersion != null ? modloaderVersion : "";
    }

    public void setModloaderVersion(String modloaderVersion) {
        this.modloaderVersion = modloaderVersion;
    }

    public String getIcon() {
        return icon != null ? icon : "grass";
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getLastPlayedTimestamp() {
        return lastPlayedTimestamp;
    }

    public void setLastPlayedTimestamp(long lastPlayedTimestamp) {
        this.lastPlayedTimestamp = lastPlayedTimestamp;
    }

    public Integer getCustomMemoryMb() {
        return customMemoryMb;
    }

    public void setCustomMemoryMb(Integer customMemoryMb) {
        this.customMemoryMb = customMemoryMb;
    }

    public String getCustomJvmArgs() {
        return customJvmArgs;
    }

    public void setCustomJvmArgs(String customJvmArgs) {
        this.customJvmArgs = customJvmArgs;
    }

    public String getCustomJavaPath() {
        return customJavaPath;
    }

    public void setCustomJavaPath(String customJavaPath) {
        this.customJavaPath = customJavaPath;
    }

    public Path getBaseDirectory() {
        return com.IPOleksenko.config.ConfigManager.getInstance().getConfig().resolveInstancesPath().resolve(getId());
    }

    public Path getGameDir() {
        return getBaseDirectory().resolve(".minecraft");
    }


    @Override
    public String toString() {
        return getName() + " (" + getMinecraftVersion() + ")";
    }
}

