package com.IPOleksenko.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherConfig {
    // Memory settings (in MB)
    private int minMemoryMb = 1024;
    private int maxMemoryMb = 4096;

    // Java settings
    private String javaPath = "auto";
    private String jvmArgs = "-XX:+UseG1GC -Dsun.rmi.dgc.server.gcInterval=2147483646 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M";

    // Window settings
    private int gameWidth = 1280;
    private int gameHeight = 720;
    private boolean fullscreen = false;

    // Launcher behavior on game launch: "KEEP", "HIDE", "CLOSE"
    private String launcherAction = "KEEP";

    // Directory Customization (where to save files)
    private String instancesDir = "";
    private String versionsDir = "";
    private String assetsDir = "";
    private String librariesDir = "";

    // Launch System Customization (how to launch the game process)
    private String launchWrapper = "";
    private String customEnvVars = "";
    private String processPriority = "NORMAL";

    // Active state
    private String activeInstanceId = "";
    private String activeUserUuid = "";

    // Microsoft Azure Application ID for OAuth2 Device Flow
    private String microsoftClientId = "43b56eb6-cbec-4278-9c39-d70c21aa6d49";

    public LauncherConfig() {}

    public int getMinMemoryMb() {
        return minMemoryMb;
    }

    public void setMinMemoryMb(int minMemoryMb) {
        this.minMemoryMb = minMemoryMb;
    }

    public int getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(int maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public String getJavaPath() {
        return javaPath != null ? javaPath : "auto";
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getJvmArgs() {
        return jvmArgs != null ? jvmArgs : "";
    }

    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public int getGameWidth() {
        return gameWidth > 0 ? gameWidth : 1280;
    }

    public void setGameWidth(int gameWidth) {
        this.gameWidth = gameWidth;
    }

    public int getGameHeight() {
        return gameHeight > 0 ? gameHeight : 720;
    }

    public void setGameHeight(int gameHeight) {
        this.gameHeight = gameHeight;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public String getLauncherAction() {
        return launcherAction != null ? launcherAction : "KEEP";
    }

    public void setLauncherAction(String launcherAction) {
        this.launcherAction = launcherAction;
    }

    // Directory Customization Getters/Setters
    public String getInstancesDir() {
        if (instancesDir == null || instancesDir.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), "IPOCraft", "instances").toString();
        }
        return instancesDir;
    }

    public void setInstancesDir(String instancesDir) {
        this.instancesDir = instancesDir;
    }

    public String getVersionsDir() {
        if (versionsDir == null || versionsDir.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), "IPOCraft", "versions").toString();
        }
        return versionsDir;
    }

    public void setVersionsDir(String versionsDir) {
        this.versionsDir = versionsDir;
    }

    public String getAssetsDir() {
        if (assetsDir == null || assetsDir.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), "IPOCraft", "assets").toString();
        }
        return assetsDir;
    }

    public void setAssetsDir(String assetsDir) {
        this.assetsDir = assetsDir;
    }

    public String getLibrariesDir() {
        if (librariesDir == null || librariesDir.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), "IPOCraft", "libraries").toString();
        }
        return librariesDir;
    }

    public void setLibrariesDir(String librariesDir) {
        this.librariesDir = librariesDir;
    }

    public Path resolveInstancesPath() {
        return Paths.get(getInstancesDir());
    }

    public Path resolveVersionsPath() {
        return Paths.get(getVersionsDir());
    }

    public Path resolveAssetsPath() {
        return Paths.get(getAssetsDir());
    }

    public Path resolveLibrariesPath() {
        return Paths.get(getLibrariesDir());
    }

    // Launch System Customization
    public String getLaunchWrapper() {
        return launchWrapper != null ? launchWrapper : "";
    }

    public void setLaunchWrapper(String launchWrapper) {
        this.launchWrapper = launchWrapper;
    }

    public String getCustomEnvVars() {
        return customEnvVars != null ? customEnvVars : "";
    }

    public void setCustomEnvVars(String customEnvVars) {
        this.customEnvVars = customEnvVars;
    }

    public String getProcessPriority() {
        return processPriority != null ? processPriority : "NORMAL";
    }

    public void setProcessPriority(String processPriority) {
        this.processPriority = processPriority;
    }

    public String getActiveInstanceId() {
        return activeInstanceId != null ? activeInstanceId : "";
    }

    public void setActiveInstanceId(String activeInstanceId) {
        this.activeInstanceId = activeInstanceId;
    }

    public String getActiveUserUuid() {
        return activeUserUuid != null ? activeUserUuid : "";
    }

    public void setActiveUserUuid(String activeUserUuid) {
        this.activeUserUuid = activeUserUuid;
    }

    public String getMicrosoftClientId() {
        if (microsoftClientId == null || microsoftClientId.trim().isEmpty()) {
            return "43b56eb6-cbec-4278-9c39-d70c21aa6d49";
        }
        return microsoftClientId.trim();
    }

    public void setMicrosoftClientId(String microsoftClientId) {
        this.microsoftClientId = microsoftClientId;
    }
}
