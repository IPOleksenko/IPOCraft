package com.IPOleksenko.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ConfigManager {
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), "IPOCraft", "config.json");
    private static ConfigManager instance;

    private LauncherConfig config;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public LauncherConfig getConfig() {
        return config;
    }

    public void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                config = gson.fromJson(json, LauncherConfig.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to read config, using defaults: " + e.getMessage());
        }

        if (config == null) {
            config = new LauncherConfig();
            saveConfig();
        } else if ("c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb".equalsIgnoreCase(config.getMicrosoftClientId())) {
            config.setMicrosoftClientId("43b56eb6-cbec-4278-9c39-d70c21aa6d49");
            saveConfig();
        }
        ensureLauncherProfiles();
    }

    public void saveConfig() {
        try {
            if (config == null) return;
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = gson.toJson(config);
            Files.writeString(CONFIG_PATH, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ensureLauncherProfiles();
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public void ensureLauncherProfiles() {
        try {
            java.util.List<Path> targets = new java.util.ArrayList<>();
            if (config != null) {
                Path versionsParent = config.resolveVersionsPath().getParent();
                if (versionsParent != null) {
                    targets.add(versionsParent.resolve("launcher_profiles.json"));
                }
            }
            targets.add(Paths.get(System.getProperty("user.home"), "IPOCraft", "launcher_profiles.json"));

            String appdata = System.getenv("APPDATA");
            if (appdata != null && !appdata.isEmpty()) {
                targets.add(Paths.get(appdata, ".minecraft", "launcher_profiles.json"));
            }

            String defaultContent = "{\n" +
                    "  \"profiles\": {\n" +
                    "    \"(Default)\": {\n" +
                    "      \"name\": \"(Default)\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"settings\": {\n" +
                    "    \"crashAssistance\": true,\n" +
                    "    \"enableAdvanced\": true,\n" +
                    "    \"enableReleases\": true,\n" +
                    "    \"enableSnapshots\": true,\n" +
                    "    \"enableHistorical\": true\n" +
                    "  },\n" +
                    "  \"version\": 3\n" +
                    "}\n";

            for (Path p : targets) {
                if (!Files.exists(p)) {
                    if (p.getParent() != null) {
                        Files.createDirectories(p.getParent());
                    }
                    Files.writeString(p, defaultContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to ensure launcher_profiles.json: " + e.getMessage());
        }
    }
}

