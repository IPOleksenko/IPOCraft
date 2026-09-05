package com.IPOleksenko.launcher;

import org.json.JSONObject;

import com.IPOleksenko.config.ConfigManager;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class VersionScanner {

    public static class VersionItem {
        private final String id;
        private final String displayName;
        private final Path folder;
        private final boolean hasJson;
        private final boolean hasJar;

        public VersionItem(String id, String displayName, Path folder, boolean hasJson, boolean hasJar) {
            this.id = id;
            this.displayName = displayName;
            this.folder = folder;
            this.hasJson = hasJson;
            this.hasJar = hasJar;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Path getFolder() {
            return folder;
        }

        public boolean hasJson() {
            return hasJson;
        }

        public boolean hasJar() {
            return hasJar;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public static Path getSystemMinecraftDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null && !appdata.isEmpty()) {
                return Paths.get(appdata, ".minecraft");
            }
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    public static Path getSystemMinecraftVersionsDir() {
        Path mc = getSystemMinecraftDir();
        return mc != null ? mc.resolve("versions") : null;
    }

    public static Path getSystemMinecraftLibrariesDir() {
        Path mc = getSystemMinecraftDir();
        return mc != null ? mc.resolve("libraries") : null;
    }

    public static List<VersionItem> getInstalledVersions() {
        Map<String, VersionItem> items = new LinkedHashMap<>();
        Path versionsDir = ConfigManager.getInstance().getConfig().resolveVersionsPath();

        // 1. Scan configured versionsDir
        if (Files.exists(versionsDir)) {
            scanDirectory(versionsDir, items);
        }

        // 2. Also scan legacy ~/.IPOCraft/.minecraft/versions and ~/.IPOCraft/.minecraft/
        Path ipoVersions = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "versions");
        if (Files.exists(ipoVersions)) {
            scanDirectory(ipoVersions, items);
        }
        Path legacyDir = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft");
        if (Files.exists(legacyDir)) {
            scanDirectory(legacyDir, items);
        }

        // 3. Scan system .minecraft/versions (e.g. %APPDATA%/.minecraft/versions where Forge installer puts it)
        Path sysVersions = getSystemMinecraftVersionsDir();
        if (sysVersions != null && Files.exists(sysVersions)) {
            scanDirectory(sysVersions, items);
        }

        return new ArrayList<>(items.values());
    }

    private static void scanDirectory(Path parentDir, Map<String, VersionItem> out) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    if (name.equalsIgnoreCase("libraries") || name.equalsIgnoreCase("assets") || name.equalsIgnoreCase("natives")) {
                        continue;
                    }

                    Path json = entry.resolve(name + ".json");
                    Path jar = entry.resolve(name + ".jar");
                    boolean hasJson = Files.exists(json);
                    boolean hasJar = Files.exists(jar);

                    // Even if not matching exact name, check if any .json exists in folder
                    if (!hasJson) {
                        try (DirectoryStream<Path> sub = Files.newDirectoryStream(entry, "*.json")) {
                            for (Path p : sub) {
                                json = p;
                                hasJson = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (!hasJar) {
                        try (DirectoryStream<Path> sub = Files.newDirectoryStream(entry, "*.jar")) {
                            for (Path p : sub) {
                                jar = p;
                                hasJar = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }

                    if (hasJson || hasJar) {
                        if (!out.containsKey(name)) {
                            out.put(name, new VersionItem(name, name, entry, hasJson, hasJar));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed scanning versions: " + e.getMessage());
        }
    }

    public static void openVersionsFolder() {
        Path versionsDir = ConfigManager.getInstance().getConfig().resolveVersionsPath();
        try {
            Files.createDirectories(versionsDir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(versionsDir.toFile());
            } else {
                new ProcessBuilder("explorer.exe", versionsDir.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            System.err.println("Failed to open versions folder: " + e.getMessage());
        }
    }

    public static void openSystemMinecraftVersionsFolder() {
        Path versionsDir = getSystemMinecraftVersionsDir();
        if (versionsDir != null) {
            try {
                Files.createDirectories(versionsDir);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(versionsDir.toFile());
                } else {
                    new ProcessBuilder("explorer.exe", versionsDir.toAbsolutePath().toString()).start();
                }
            } catch (Exception e) {
                System.err.println("Failed to open system versions folder: " + e.getMessage());
            }
        }
    }
}
