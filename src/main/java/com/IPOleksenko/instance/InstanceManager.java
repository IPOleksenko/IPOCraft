package com.IPOleksenko.instance;

import com.IPOleksenko.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class InstanceManager {
    private static final Path INSTANCES_JSON = Paths.get(System.getProperty("user.home"), "IPOCraft", "instances.json");
    private static final Path LEGACY_MINECRAFT = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft");
    private static InstanceManager instance;

    private final List<Instance> instances = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private InstanceManager() {
        loadInstances();
        migrateLegacyVersions();
    }

    public static synchronized InstanceManager getInstance() {
        if (instance == null) {
            instance = new InstanceManager();
        }
        return instance;
    }

    public List<Instance> getInstances() {
        return instances;
    }

    public Instance getInstanceById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Instance inst : instances) {
            if (id.equals(inst.getId())) {
                return inst;
            }
        }
        return null;
    }

    public Instance getActiveInstance() {
        String activeId = ConfigManager.getInstance().getConfig().getActiveInstanceId();
        Instance found = getInstanceById(activeId);
        if (found != null) return found;

        if (!instances.isEmpty()) {
            Instance first = instances.get(0);
            setActiveInstance(first);
            return first;
        }
        return null;
    }

    public void setActiveInstance(Instance instance) {
        if (instance != null) {
            ConfigManager.getInstance().getConfig().setActiveInstanceId(instance.getId());
            ConfigManager.getInstance().saveConfig();
        }
    }

    public Instance createInstance(String name, String minecraftVersion) {
        Instance inst = new Instance(name, minecraftVersion);
        try {
            Files.createDirectories(inst.getGameDir());
        } catch (IOException e) {
            e.printStackTrace();
        }

        instances.add(inst);
        saveInstances();
        setActiveInstance(inst);
        return inst;
    }

    public Instance createInstance(String name, String minecraftVersion, String modloader, String modloaderVersion) {
        return createInstance(name, minecraftVersion);
    }

    public void renameInstance(Instance inst, String newName) {
        if (inst != null && newName != null && !newName.trim().isEmpty()) {
            inst.setName(newName.trim());
            saveInstances();
        }
    }

    public Instance cloneInstance(Instance source, String newName) {
        if (source == null) return null;
        String name = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : source.getName() + " (Copy)";
        Instance copy = new Instance(name, source.getMinecraftVersion());
        copy.setIcon(source.getIcon());
        copy.setCustomMemoryMb(source.getCustomMemoryMb());
        copy.setCustomJvmArgs(source.getCustomJvmArgs());
        copy.setCustomJavaPath(source.getCustomJavaPath());

        try {
            Files.createDirectories(copy.getGameDir());
            if (Files.exists(source.getGameDir())) {
                copyDirectory(source.getGameDir(), copy.getGameDir());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        instances.add(copy);
        saveInstances();
        return copy;
    }

    public boolean deleteInstance(Instance inst) {
        if (inst == null) return false;
        instances.remove(inst);
        saveInstances();

        try {
            Path baseDir = inst.getBaseDirectory();
            if (Files.exists(baseDir)) {
                deleteRecursively(baseDir);
            }
        } catch (IOException e) {
            System.err.println("Could not completely delete directory for instance: " + e.getMessage());
        }

        if (ConfigManager.getInstance().getConfig().getActiveInstanceId().equals(inst.getId())) {
            if (!instances.isEmpty()) {
                setActiveInstance(instances.get(0));
            } else {
                ConfigManager.getInstance().getConfig().setActiveInstanceId("");
                ConfigManager.getInstance().saveConfig();
            }
        }
        return true;
    }

    public void loadInstances() {
        instances.clear();
        try {
            if (Files.exists(INSTANCES_JSON)) {
                String json = Files.readString(INSTANCES_JSON);
                Type type = new TypeToken<List<Instance>>() {}.getType();
                List<Instance> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    instances.addAll(loaded);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load instances: " + e.getMessage());
        }
    }

    public void saveInstances() {
        try {
            Files.createDirectories(INSTANCES_JSON.getParent());
            String json = gson.toJson(instances);
            Files.writeString(INSTANCES_JSON, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save instances: " + e.getMessage());
        }
    }

    private void migrateLegacyVersions() {
        try {
            if (!Files.exists(LEGACY_MINECRAFT)) return;
            // Legacy versions were in ~/IPOCraft/.minecraft/<versionName>
            Files.list(LEGACY_MINECRAFT)
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("libraries") &&
                            !p.getFileName().toString().equals("assets") &&
                            !p.getFileName().toString().equals("natives"))
                    .forEach(dir -> {
                        String verName = dir.getFileName().toString();
                        // Check if an instance already exists for this
                        boolean exists = instances.stream().anyMatch(i -> i.getName().equalsIgnoreCase(verName));
                        if (!exists) {
                            Instance inst = new Instance(verName, verName);
                            instances.add(inst);
                        }
                    });
            saveInstances();
        } catch (Exception ignored) {}
    }

    public void updateInstance(Instance inst, String newName, String newVersion, String javaPath, Integer ramMb) {
        updateInstance(inst, newName, newVersion, javaPath, ramMb, null, null, null);
    }

    public void updateInstance(Instance inst, String newName, String newVersion, String javaPath, Integer ramMb, String jvmArgs, String gameArgs, Boolean overrideJvmArgs) {
        if (inst == null) return;
        if (newName != null && !newName.trim().isEmpty()) {
            inst.setName(newName.trim());
        }
        if (newVersion != null && !newVersion.trim().isEmpty()) {
            inst.setMinecraftVersion(newVersion.trim());
        }
        inst.setCustomJavaPath(javaPath != null ? javaPath.trim() : "");
        inst.setCustomMemoryMb(ramMb);
        if (jvmArgs != null) {
            inst.setCustomJvmArgs(jvmArgs.trim());
        }
        if (gameArgs != null) {
            inst.setCustomGameArgs(gameArgs.trim());
        }
        if (overrideJvmArgs != null) {
            inst.setOverrideJvmArgs(overrideJvmArgs);
        }
        saveInstances();
    }

    public void updateInstance(Instance inst, String newName, String newVersion, String newModloader, String javaPath, Integer ramMb) {
        updateInstance(inst, newName, newVersion, javaPath, ramMb);
        if (newModloader != null && !newModloader.trim().isEmpty()) {
            inst.setModloader(newModloader.trim());
            saveInstances();
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                Path dest = target.resolve(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    copyDirectory(entry, dest);
                } else {
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}

