package com.IPOleksenko.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.config.ConfigManager;
import com.IPOleksenko.config.LauncherConfig;
import com.IPOleksenko.data.DownloadManager;
import com.IPOleksenko.instance.Instance;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MinecraftLauncher {

    public static void launch(Instance instance, Account account,
                              Consumer<Double> progressCallback,
                              Consumer<String> statusCallback,
                              Consumer<String> logCallback,
                              Runnable onGameExit) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException("No instance selected to launch. Please select or create an instance.");
        }
        if (account == null) {
            throw new IllegalArgumentException("No player profile selected. Please add or select an account first.");
        }

        LauncherConfig config = ConfigManager.getInstance().getConfig();
        Path sharedLibs = config.resolveLibrariesPath();
        Path sharedAssets = config.resolveAssetsPath();
        Path sharedVersions = config.resolveVersionsPath();

        if (statusCallback != null) statusCallback.accept("Preparing launch for " + instance.getName() + "...");

        // 1. Resolve version metadata
        JSONObject versionJson = resolveVersionJson(instance, sharedVersions);
        if (versionJson == null) {
            throw new FileNotFoundException("Could not find or download version JSON for " + instance.getMinecraftVersion());
        }

        // 2. Download libraries and assets
        if (statusCallback != null) statusCallback.accept("Checking libraries and assets...");
        Files.createDirectories(sharedLibs);
        Files.createDirectories(sharedAssets);

        // Synchronize Forge processor libraries (client-srg.jar, forge-client.jar, etc.) from system repo if missing
        syncForgeAndSystemLibraries(sharedLibs, logCallback);
        verifyForgeLibraries(versionJson, sharedLibs, logCallback);

        List<DownloadManager.DownloadTask> tasks = DownloadManager.collectDownloadTasks(versionJson, sharedLibs, sharedAssets);

        // Also ensure client jar is downloaded
        Path clientJar = ensureClientJar(instance.getMinecraftVersion(), versionJson, sharedVersions);

        if (!tasks.isEmpty()) {
            if (statusCallback != null) statusCallback.accept("Downloading " + tasks.size() + " files...");
            DownloadManager.executeTasks(tasks, progressCallback);
        }

        if (progressCallback != null) progressCallback.accept(1.0);

        // 3. Extract natives & reconstruct legacy assets
        if (statusCallback != null) statusCallback.accept("Extracting native libraries...");
        Path nativesDir = instance.getBaseDirectory().resolve("natives");
        Files.createDirectories(nativesDir);
        extractNatives(versionJson, sharedLibs, nativesDir);
        DownloadManager.reconstructLegacyAssets(versionJson, sharedAssets, instance.getGameDir());

        // 4. Build classpath
        Set<String> classpathEntries = new LinkedHashSet<>();
        collectClasspathLibraries(versionJson, sharedLibs, classpathEntries);
        if (clientJar != null && Files.exists(clientJar)) {
            classpathEntries.add(clientJar.toAbsolutePath().toString());
        }

        Set<String> safeClasspathEntries = new LinkedHashSet<>();
        for (String entry : classpathEntries) {
            safeClasspathEntries.add(toSafeClasspathEntry(entry));
        }

        String classpath = String.join(File.pathSeparator, safeClasspathEntries);

        // 5. Determine Java Path and validate version compatibility
        String javaExe = resolveJavaPath(instance, config);
        JavaDetector.JavaInfo detectedJava = JavaDetector.inspectJava(Paths.get(javaExe));
        int detectedMajor = detectedJava != null ? detectedJava.getMajorVersion() : 0;

        int recommendedMajor = 0;
        if (versionJson.has("javaVersion")) {
            recommendedMajor = versionJson.getJSONObject("javaVersion").optInt("majorVersion", 0);
        }
        if (recommendedMajor <= 0) {
            recommendedMajor = JavaDetector.getRecommendedJavaMajor(instance.getMinecraftVersion());
        }

        if (detectedMajor > 0 && recommendedMajor > detectedMajor) {
            if (logCallback != null) {
                logCallback.accept("[WARN] Minecraft " + instance.getMinecraftVersion() +
                        " requires Java " + recommendedMajor + "+, but Java " + detectedMajor +
                        " is currently configured. Launching with Java " + detectedMajor + "...");
            }
        }

        if (logCallback != null) {
            logCallback.accept("[IPOCraft] Using Java: " + javaExe + (detectedJava != null ? " (" + detectedJava.getVersionString() + ")" : ""));
            logCallback.accept("[IPOCraft] Instance: " + instance.getName() + " (" + instance.getMinecraftVersion() + ")");
            logCallback.accept("[IPOCraft] Account: " + account.getUsername() + " (" + account.getType() + ") UUID: " + account.getUuid());
        }

        // 6. Memory settings
        int maxMemory = (instance.getCustomMemoryMb() != null && instance.getCustomMemoryMb() > 0)
                ? instance.getCustomMemoryMb()
                : config.getMaxMemoryMb();
        int minMemory = config.getMinMemoryMb();
        if (minMemory > maxMemory) minMemory = maxMemory;

        // 7. Assemble JVM & Game arguments
        List<String> finalCmd = buildLaunchCommand(instance, account, config, versionJson,
                sharedLibs, sharedAssets, nativesDir, classpath, javaExe, detectedMajor);

        if (statusCallback != null) statusCallback.accept("Starting Minecraft process...");
        if (logCallback != null) {
            logCallback.accept("[IPOCraft] Executing: " + String.join(" ", finalCmd));
        }

        Files.createDirectories(instance.getGameDir());

        ProcessBuilder pb = new ProcessBuilder(finalCmd);
        pb.directory(instance.getGameDir().toFile());
        pb.redirectErrorStream(true);

        // Apply Custom Environment Variables if specified
        if (config.getCustomEnvVars() != null && !config.getCustomEnvVars().trim().isEmpty()) {
            for (String line : config.getCustomEnvVars().split("\n")) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    pb.environment().put(k, v);
                }
            }
        }

        Process process = pb.start();
        currentRunningProcess = process;
        currentProcessStdin = process.getOutputStream();

        // Stream process output to logs
        int finalRecommendedMajor = recommendedMajor;
        new Thread(() -> {
            boolean hadClassVersionError = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("UnsupportedClassVersionError") || line.contains("has been compiled by a more recent version of the Java Runtime")) {
                        hadClassVersionError = true;
                    }
                    if (logCallback != null) {
                        logCallback.accept(line);
                    }
                }
            } catch (IOException ignored) {}

            try {
                int exitCode = process.waitFor();
                if (logCallback != null) {
                    logCallback.accept("[IPOCraft] Minecraft process exited with code " + exitCode);
                    if (exitCode != 0 && hadClassVersionError) {
                        logCallback.accept("[ERROR] This Minecraft version requires a newer Java runtime! Please install Java " +
                                (finalRecommendedMajor > 0 ? finalRecommendedMajor : "21") + "+ and select it in Instance Settings -> Java Runtime.");
                    }
                }
            } catch (InterruptedException ignored) {}

            currentRunningProcess = null;
            currentProcessStdin = null;

            if (onGameExit != null) {
                onGameExit.run();
            }
        }, "Minecraft-Log-Reader").start();
    }

    private static volatile Process currentRunningProcess = null;
    private static volatile OutputStream currentProcessStdin = null;

    public static boolean isGameRunning() {
        return currentRunningProcess != null && currentRunningProcess.isAlive();
    }

    public static boolean sendCommand(String command) {
        if (isGameRunning() && currentProcessStdin != null) {
            try {
                currentProcessStdin.write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                currentProcessStdin.flush();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public static String resolveJavaPath(Instance instance, LauncherConfig config) {
        String resolved = null;
        if (instance != null && instance.getCustomJavaPath() != null && !instance.getCustomJavaPath().isEmpty()) {
            Path p = Paths.get(instance.getCustomJavaPath());
            if (Files.isRegularFile(p)) resolved = p.toAbsolutePath().toString();
            else {
                Path inBin = p.resolve("bin").resolve("java.exe");
                if (Files.isRegularFile(inBin)) resolved = inBin.toAbsolutePath().toString();
            }
        }

        if (resolved == null && config != null && config.getJavaPath() != null && !config.getJavaPath().equalsIgnoreCase("auto") && !config.getJavaPath().isEmpty()) {
            Path p = Paths.get(config.getJavaPath());
            if (Files.isRegularFile(p)) resolved = p.toAbsolutePath().toString();
            else {
                Path inBin = p.resolve("bin").resolve("java.exe");
                if (Files.isRegularFile(inBin)) resolved = inBin.toAbsolutePath().toString();
            }
        }

        if (resolved == null) {
            // Auto detection
            List<JavaDetector.JavaInfo> javas = JavaDetector.detectAllInstallations();
            String mcVer = (instance != null) ? instance.getMinecraftVersion() : "";
            JavaDetector.JavaInfo recommended = JavaDetector.findRecommendedJava(mcVer, javas);
            if (recommended != null) {
                resolved = recommended.getExecutablePath();
            } else {
                resolved = "java";
            }
        }

        return preferJavaw(resolved);
    }

    public static String preferJavaw(String javaPath) {
        if (javaPath == null || javaPath.isEmpty()) return "javaw";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            if (javaPath.equalsIgnoreCase("java")) {
                return "javaw";
            }
            if (javaPath.toLowerCase().endsWith("java.exe")) {
                String javaw = javaPath.substring(0, javaPath.length() - 8) + "javaw.exe";
                if (Files.exists(Paths.get(javaw))) {
                    return javaw;
                }
            }
        }
        return javaPath;
    }

    private static JSONObject resolveVersionJson(Instance instance, Path sharedVersions) throws IOException {
        String baseVersion = instance.getMinecraftVersion();

        // 1. Check in shared versions
        Path verFolder = sharedVersions.resolve(baseVersion);
        Path jsonPath = verFolder.resolve(baseVersion + ".json");
        if (Files.exists(jsonPath)) {
            JSONObject customJson = new JSONObject(Files.readString(jsonPath));
            if (customJson.has("inheritsFrom")) {
                JSONObject baseJson = getOrFetchBaseVersionJson(customJson.getString("inheritsFrom"), sharedVersions);
                if (baseJson != null) {
                    return mergeJsons(baseJson, customJson);
                }
            }
            return customJson;
        }

        // 1b. Check any .json in shared versions folder
        if (Files.isDirectory(verFolder)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(verFolder, "*.json")) {
                for (Path p : ds) {
                    JSONObject customJson = new JSONObject(Files.readString(p));
                    if (customJson.has("inheritsFrom")) {
                        JSONObject baseJson = getOrFetchBaseVersionJson(customJson.getString("inheritsFrom"), sharedVersions);
                        if (baseJson != null) {
                            return mergeJsons(baseJson, customJson);
                        }
                    }
                    return customJson;
                }
            } catch (Exception ignored) {}
        }

        // 2. Check system %APPDATA%/.minecraft/versions/<version>/<version>.json (standard Forge install)
        Path sysVersions = VersionScanner.getSystemMinecraftVersionsDir();
        if (sysVersions != null) {
            Path sysJson = sysVersions.resolve(baseVersion).resolve(baseVersion + ".json");
            if (Files.exists(sysJson)) {
                try {
                    Path localTarget = sharedVersions.resolve(baseVersion).resolve(baseVersion + ".json");
                    if (!Files.exists(localTarget)) {
                        Files.createDirectories(localTarget.getParent());
                        Files.copy(sysJson, localTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {}
                JSONObject customJson = new JSONObject(Files.readString(sysJson));
                if (customJson.has("inheritsFrom")) {
                    JSONObject baseJson = getOrFetchBaseVersionJson(customJson.getString("inheritsFrom"), sharedVersions);
                    if (baseJson != null) {
                        return mergeJsons(baseJson, customJson);
                    }
                }
                return customJson;
            }
            Path sysFolder = sysVersions.resolve(baseVersion);
            if (Files.isDirectory(sysFolder)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(sysFolder, "*.json")) {
                    for (Path p : ds) {
                        try {
                            Path localTarget = sharedVersions.resolve(baseVersion).resolve(baseVersion + ".json");
                            if (!Files.exists(localTarget)) {
                                Files.createDirectories(localTarget.getParent());
                                Files.copy(p, localTarget, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (Exception ignored) {}
                        JSONObject customJson = new JSONObject(Files.readString(p));
                        if (customJson.has("inheritsFrom")) {
                            JSONObject baseJson = getOrFetchBaseVersionJson(customJson.getString("inheritsFrom"), sharedVersions);
                            if (baseJson != null) {
                                return mergeJsons(baseJson, customJson);
                            }
                        }
                        return customJson;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Check legacy folder ~/.IPOCraft/.minecraft/versions/<version>/<version>.json
        Path legacyVersions = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "versions", baseVersion, baseVersion + ".json");
        if (Files.exists(legacyVersions)) {
            JSONObject customJson = new JSONObject(Files.readString(legacyVersions));
            if (customJson.has("inheritsFrom")) {
                JSONObject baseJson = getOrFetchBaseVersionJson(customJson.getString("inheritsFrom"), sharedVersions);
                if (baseJson != null) {
                    return mergeJsons(baseJson, customJson);
                }
            }
            return customJson;
        }

        // 4. Check legacy folder ~/.IPOCraft/.minecraft/<version>/<version>.json
        Path legacyJson = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", baseVersion, baseVersion + ".json");
        if (Files.exists(legacyJson)) {
            return new JSONObject(Files.readString(legacyJson));
        }

        return getOrFetchBaseVersionJson(baseVersion, sharedVersions);
    }

    private static JSONObject getOrFetchBaseVersionJson(String versionId, Path sharedVersions) throws IOException {
        Path jsonPath = sharedVersions.resolve(versionId).resolve(versionId + ".json");
        if (Files.exists(jsonPath)) {
            return new JSONObject(Files.readString(jsonPath));
        }

        // Check system .minecraft/versions/<versionId>/<versionId>.json
        Path sysVersions = VersionScanner.getSystemMinecraftVersionsDir();
        if (sysVersions != null) {
            Path sysJson = sysVersions.resolve(versionId).resolve(versionId + ".json");
            if (Files.exists(sysJson)) {
                return new JSONObject(Files.readString(sysJson));
            }
        }

        // Fetch Mojang manifest
        String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        String manifestStr = fetchString(manifestUrl);
        JSONObject manifest = new JSONObject(manifestStr);
        JSONArray versions = manifest.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject ver = versions.getJSONObject(i);
            if (ver.getString("id").equals(versionId)) {
                String metaUrl = ver.getString("url");
                String metaStr = fetchString(metaUrl);
                JSONObject metaJson = new JSONObject(metaStr);
                Files.createDirectories(jsonPath.getParent());
                Files.writeString(jsonPath, metaJson.toString(4));
                return metaJson;
            }
        }

        return null;
    }

    private static Path ensureClientJar(String versionId, JSONObject versionJson, Path sharedVersions) throws IOException {
        String targetVer = (versionJson != null && versionJson.has("jar"))
                ? versionJson.getString("jar")
                : versionId;

        Path targetClientJar = sharedVersions.resolve(targetVer).resolve(targetVer + ".jar");
        if (Files.exists(targetClientJar)) {
            return targetClientJar;
        }
        Path clientJar = sharedVersions.resolve(versionId).resolve(versionId + ".jar");
        if (Files.exists(clientJar)) {
            return clientJar;
        }

        // Check system .minecraft/versions
        Path sysVersions = VersionScanner.getSystemMinecraftVersionsDir();
        if (sysVersions != null) {
            Path sysTargetJar = sysVersions.resolve(targetVer).resolve(targetVer + ".jar");
            if (Files.exists(sysTargetJar)) {
                return sysTargetJar;
            }
            Path sysJar = sysVersions.resolve(versionId).resolve(versionId + ".jar");
            if (Files.exists(sysJar)) {
                return sysJar;
            }
        }

        // Check legacy folder
        Path legacyJar = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", versionId, versionId + ".jar");
        if (Files.exists(legacyJar)) {
            return legacyJar;
        }
        Path legacyVerJar = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "versions", versionId, versionId + ".jar");
        if (Files.exists(legacyVerJar)) {
            return legacyVerJar;
        }

        // If version inherits from another version (e.g. Forge 1.20.1 inherits from 1.20.1)
        if (versionJson != null && versionJson.has("inheritsFrom")) {
            String baseVer = versionJson.getString("inheritsFrom");
            Path baseJar = sharedVersions.resolve(baseVer).resolve(baseVer + ".jar");
            if (Files.exists(baseJar)) {
                return baseJar;
            }
            if (sysVersions != null) {
                Path sysBaseJar = sysVersions.resolve(baseVer).resolve(baseVer + ".jar");
                if (Files.exists(sysBaseJar)) {
                    return sysBaseJar;
                }
            }
            JSONObject baseJson = getOrFetchBaseVersionJson(baseVer, sharedVersions);
            if (baseJson != null && baseJson.has("downloads") && baseJson.getJSONObject("downloads").has("client")) {
                String clientUrl = baseJson.getJSONObject("downloads").getJSONObject("client").getString("url");
                Files.createDirectories(baseJar.getParent());
                DownloadManager.downloadFile(clientUrl, baseJar);
                return baseJar;
            }
        }

        if (versionJson != null && versionJson.has("downloads") && versionJson.getJSONObject("downloads").has("client")) {
            String clientUrl = versionJson.getJSONObject("downloads").getJSONObject("client").getString("url");
            Files.createDirectories(clientJar.getParent());
            DownloadManager.downloadFile(clientUrl, clientJar);
            return clientJar;
        }

        return null;
    }

    static JSONObject mergeJsons(JSONObject base, JSONObject child) {
        JSONObject merged = new JSONObject(base.toString());

        if (child.has("mainClass")) {
            merged.put("mainClass", child.getString("mainClass"));
        }

        if (child.has("minecraftArguments")) {
            merged.put("minecraftArguments", child.getString("minecraftArguments"));
        }

        if (child.has("jar")) {
            merged.put("jar", child.getString("jar"));
        }

        if (child.has("assets")) {
            merged.put("assets", child.getString("assets"));
        }

        if (child.has("assetIndex")) {
            merged.put("assetIndex", child.getJSONObject("assetIndex"));
        }

        if (child.has("id")) {
            merged.put("id", child.getString("id"));
        }

        if (child.has("libraries")) {
            Map<String, JSONObject> libMap = new LinkedHashMap<>();
            JSONArray childLibs = child.getJSONArray("libraries");
            for (int i = 0; i < childLibs.length(); i++) {
                JSONObject lib = childLibs.getJSONObject(i);
                String key = getLibraryKey(lib);
                if (key != null) {
                    libMap.put(key, lib);
                } else {
                    libMap.put("__child_lib_" + i, lib);
                }
            }

            JSONArray baseLibs = merged.optJSONArray("libraries");
            if (baseLibs != null) {
                for (int i = 0; i < baseLibs.length(); i++) {
                    JSONObject lib = baseLibs.getJSONObject(i);
                    String key = getLibraryKey(lib);
                    if (key != null) {
                        if (!libMap.containsKey(key)) {
                            libMap.put(key, lib);
                        }
                    } else {
                        libMap.put("__base_lib_" + i, lib);
                    }
                }
            }

            JSONArray mergedLibs = new JSONArray();
            for (JSONObject lib : libMap.values()) {
                mergedLibs.put(lib);
            }
            merged.put("libraries", mergedLibs);
        }

        if (child.has("arguments")) {
            JSONObject childArgs = child.getJSONObject("arguments");
            JSONObject baseArgs = merged.optJSONObject("arguments");
            if (baseArgs == null) baseArgs = new JSONObject();

            if (childArgs.has("jvm")) {
                JSONArray baseJvm = baseArgs.optJSONArray("jvm");
                if (baseJvm == null) baseJvm = new JSONArray();
                JSONArray childJvm = childArgs.getJSONArray("jvm");
                for (int i = 0; i < childJvm.length(); i++) {
                    baseJvm.put(childJvm.get(i));
                }
                baseArgs.put("jvm", baseJvm);
            }

            if (childArgs.has("game")) {
                JSONArray baseGame = baseArgs.optJSONArray("game");
                if (baseGame == null) baseGame = new JSONArray();
                JSONArray childGame = childArgs.getJSONArray("game");
                for (int i = 0; i < childGame.length(); i++) {
                    baseGame.put(childGame.get(i));
                }
                baseArgs.put("game", baseGame);
            }

            merged.put("arguments", baseArgs);
        }

        if (child.has("logging")) {
            merged.put("logging", child.getJSONObject("logging"));
        }

        return merged;
    }

    static String getLibraryKey(JSONObject lib) {
        if (lib == null) return null;
        String name = lib.optString("name", null);
        if (name != null && !name.isEmpty()) {
            return getArtifactKey(name);
        }
        if (lib.has("downloads") && lib.getJSONObject("downloads").has("artifact")) {
            return lib.getJSONObject("downloads").getJSONObject("artifact").optString("path", null);
        }
        return null;
    }

    static String getArtifactKey(String mavenName) {
        String[] parts = mavenName.split(":");
        if (parts.length < 2) return mavenName;
        String groupId = parts[0];
        String artifactId = parts[1];
        String classifier = null;
        if (parts.length >= 4) {
            classifier = parts[3];
            int atIdx = classifier.indexOf('@');
            if (atIdx >= 0) classifier = classifier.substring(0, atIdx);
        }
        return groupId + ":" + artifactId + (classifier != null ? ":" + classifier : "");
    }

    private static void collectClasspathLibraries(JSONObject versionJson, Path libsFolder, Set<String> classpath) {
        if (!versionJson.has("libraries")) return;
        JSONArray libraries = versionJson.getJSONArray("libraries");

        Path sysLibs = VersionScanner.getSystemMinecraftLibrariesDir();
        Path legacyLibs = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "libraries");
        Set<String> seenLibKeys = new HashSet<>();

        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.getJSONObject(i);
            if (!DownloadManager.isLibraryAllowed(lib)) continue;
            // Native libraries are extracted to natives directory, NOT placed on java classpath
            if (lib.has("natives")) continue;

            String key = getLibraryKey(lib);
            if (key != null && !seenLibKeys.add(key)) {
                continue;
            }

            String path = null;
            if (lib.has("downloads") && lib.getJSONObject("downloads").has("artifact")) {
                path = lib.getJSONObject("downloads").getJSONObject("artifact").optString("path", null);
            }
            if (path == null && lib.has("name")) {
                path = DownloadManager.mavenNameToPath(lib.getString("name"));
            }

            if (path != null) {
                Path jar = libsFolder.resolve(path);
                if (Files.exists(jar)) {
                    classpath.add(jar.toAbsolutePath().toString());
                    continue;
                }
                if (sysLibs != null) {
                    Path sysJar = sysLibs.resolve(path);
                    if (Files.exists(sysJar)) {
                        classpath.add(sysJar.toAbsolutePath().toString());
                        continue;
                    }
                }
                if (Files.exists(legacyLibs)) {
                    Path legJar = legacyLibs.resolve(path);
                    if (Files.exists(legJar)) {
                        classpath.add(legJar.toAbsolutePath().toString());
                    }
                }
            }
        }
    }

    public static List<String> buildLaunchCommand(Instance instance, Account account, LauncherConfig config,
                                                  JSONObject versionJson, Path sharedLibs, Path sharedAssets,
                                                  Path nativesDir, String classpath, String javaExe, int detectedMajor) {

        int maxMemory = (instance.getCustomMemoryMb() != null && instance.getCustomMemoryMb() > 0)
                ? instance.getCustomMemoryMb()
                : config.getMaxMemoryMb();
        int minMemory = config.getMinMemoryMb();
        if (minMemory > maxMemory) minMemory = maxMemory;

        String safeNatives = toSafeClasspathEntry(nativesDir.toAbsolutePath().toString());
        List<String> rawCmd = new ArrayList<>();
        rawCmd.add(javaExe);

        String customJvm = (instance.getCustomJvmArgs() != null) ? instance.getCustomJvmArgs().trim() : "";
        boolean overrideJvm = instance.isOverrideJvmArgs() && !customJvm.isEmpty();

        // Check if custom JVM arguments already specify -Xms, -Xmx, or -Djava.library.path
        boolean hasCustomXms = overrideJvm && (customJvm.contains("-Xms") || customJvm.matches(".*-Xms\\S+.*"));
        boolean hasCustomXmx = overrideJvm && (customJvm.contains("-Xmx") || customJvm.matches(".*-Xmx\\S+.*"));
        boolean hasCustomLibPath = overrideJvm && customJvm.contains("-Djava.library.path=");

        if (!hasCustomXms) rawCmd.add("-Xms" + minMemory + "M");
        if (!hasCustomXmx) rawCmd.add("-Xmx" + maxMemory + "M");
        if (!hasCustomLibPath) rawCmd.add("-Djava.library.path=" + safeNatives);

        if (overrideJvm) {
            // User chose to override base launcher JVM flags completely
            for (String arg : customJvm.split("\\s+")) {
                if (!arg.isEmpty()) rawCmd.add(arg);
            }
        } else {
            // Launcher global default JVM args (e.g. G1GC flags)
            String defaultJvm = (config != null && config.getJvmArgs() != null) ? config.getJvmArgs().trim() : "";
            if (!defaultJvm.isEmpty()) {
                for (String arg : defaultJvm.split("\\s+")) {
                    if (!arg.isEmpty()) rawCmd.add(arg);
                }
            }
            // Instance-specific custom JVM args (appended)
            if (!customJvm.isEmpty()) {
                for (String arg : customJvm.split("\\s+")) {
                    if (!arg.isEmpty()) rawCmd.add(arg);
                }
            }
        }

        // For modern Java (16+), ensure reflective access to unnamed modules if not already added
        if (detectedMajor >= 16) {
            boolean hasInvokeOpen = false;
            boolean hasJarOpen = false;
            for (String c : rawCmd) {
                if (c.contains("java.base/java.lang.invoke=ALL-UNNAMED")) hasInvokeOpen = true;
                if (c.contains("java.base/java.util.jar=ALL-UNNAMED")) hasJarOpen = true;
            }
            if (!hasInvokeOpen) {
                rawCmd.add("--add-opens");
                rawCmd.add("java.base/java.lang.invoke=ALL-UNNAMED");
            }
            if (!hasJarOpen) {
                rawCmd.add("--add-opens");
                rawCmd.add("java.base/java.util.jar=ALL-UNNAMED");
            }
        }

        Path assetsPath = sharedAssets;
        if (versionJson.has("assetIndex")) {
            JSONObject aIndex = versionJson.getJSONObject("assetIndex");
            String idxId = aIndex.optString("id", "");
            Path virt = sharedAssets.resolve("virtual").resolve(idxId);
            if (Files.exists(virt)) {
                assetsPath = virt;
            }
        }

        Map<String, String> tokens = new HashMap<>();
        tokens.put("natives_directory", safeNatives);
        tokens.put("launcher_name", "IPOCraft");
        tokens.put("launcher_version", "2.0");
        tokens.put("classpath", classpath);
        tokens.put("classpath_separator", File.pathSeparator);
        tokens.put("library_directory", sharedLibs.toAbsolutePath().toString());
        tokens.put("libraries_directory", sharedLibs.toAbsolutePath().toString());
        tokens.put("auth_player_name", account.getUsername());
        tokens.put("version_name", instance.getMinecraftVersion());
        tokens.put("game_directory", instance.getGameDir().toAbsolutePath().toString());
        tokens.put("assets_root", sharedAssets.toAbsolutePath().toString());
        tokens.put("game_assets", assetsPath.toAbsolutePath().toString());
        tokens.put("assets_index_name", versionJson.optJSONObject("assetIndex") != null
                ? versionJson.getJSONObject("assetIndex").optString("id", instance.getMinecraftVersion())
                : instance.getMinecraftVersion());
        tokens.put("auth_uuid", account.getUuid().replace("-", ""));
        tokens.put("auth_access_token", account.getAccessToken());
        tokens.put("auth_session", account.getAuthSession());
        tokens.put("user_type", account.isMicrosoft() ? "msa" : "mojang");
        tokens.put("user_properties", "{}");
        tokens.put("user_property_map", "{}");
        tokens.put("profile_name", account.getUsername());
        tokens.put("version_type", "release");
        tokens.put("resolution_width", String.valueOf(config.getGameWidth()));
        tokens.put("resolution_height", String.valueOf(config.getGameHeight()));
        tokens.put("clientid", config.getMicrosoftClientId() != null ? config.getMicrosoftClientId() : "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb");
        tokens.put("auth_xuid", account.getUuid().replace("-", ""));

        // Modern arguments support (1.13+)
        if (versionJson.has("arguments")) {
            JSONObject argsObj = versionJson.getJSONObject("arguments");
            if (argsObj.has("jvm")) {
                parseArgumentsArray(argsObj.getJSONArray("jvm"), tokens, rawCmd, true, detectedMajor);
            } else {
                rawCmd.add("-cp");
                rawCmd.add(classpath);
            }

            String mainClass = versionJson.optString("mainClass", "net.minecraft.client.main.Main");
            rawCmd.add(mainClass);

            if (argsObj.has("game")) {
                parseArgumentsArray(argsObj.getJSONArray("game"), tokens, rawCmd, false, detectedMajor);
            }
        } else {
            // Legacy arguments (1.12.2 and older)
            rawCmd.add("-cp");
            rawCmd.add(classpath);

            String mainClass = versionJson.optString("mainClass", "net.minecraft.client.main.Main");
            rawCmd.add(mainClass);

            if (versionJson.has("minecraftArguments")) {
                String mcArgs = versionJson.getString("minecraftArguments");
                for (String part : mcArgs.split("\\s+")) {
                    rawCmd.add(substitute(part, tokens));
                }
            }
        }

        // Custom game arguments specified for the instance
        if (instance.getCustomGameArgs() != null && !instance.getCustomGameArgs().trim().isEmpty()) {
            for (String gArg : instance.getCustomGameArgs().trim().split("\\s+")) {
                if (!gArg.isEmpty()) {
                    rawCmd.add(substitute(gArg, tokens));
                }
            }
        }

        // Window size arguments
        if (config.getGameWidth() > 0 && config.getGameHeight() > 0) {
            if (!rawCmd.contains("--width")) {
                rawCmd.add("--width");
                rawCmd.add(String.valueOf(config.getGameWidth()));
            }
            if (!rawCmd.contains("--height")) {
                rawCmd.add("--height");
                rawCmd.add(String.valueOf(config.getGameHeight()));
            }
        }
        if (config.isFullscreen() && !rawCmd.contains("--fullscreen")) {
            rawCmd.add("--fullscreen");
        }

        // Apply Launch Wrapper if specified (e.g. gamemoderun, mangohud, or custom script)
        List<String> finalCmd = new ArrayList<>();
        if (config.getLaunchWrapper() != null && !config.getLaunchWrapper().trim().isEmpty()) {
            for (String w : config.getLaunchWrapper().trim().split("\\s+")) {
                if (!w.isEmpty()) finalCmd.add(w);
            }
        }
        finalCmd.addAll(rawCmd);
        return finalCmd;
    }

    public static List<String> previewLaunchCommand(Instance instance, Account account) throws Exception {
        if (instance == null) return Collections.emptyList();
        LauncherConfig config = ConfigManager.getInstance().getConfig();
        Path sharedLibs = config.resolveLibrariesPath();
        Path sharedAssets = config.resolveAssetsPath();
        Path sharedVersions = config.resolveVersionsPath();
        JSONObject versionJson = resolveVersionJson(instance, sharedVersions);
        if (versionJson == null) {
            return Collections.singletonList("Version JSON not found for " + instance.getMinecraftVersion());
        }
        Path nativesDir = instance.getBaseDirectory().resolve("natives");
        Set<String> classpathEntries = new LinkedHashSet<>();
        collectClasspathLibraries(versionJson, sharedLibs, classpathEntries);
        Path clientJar = ensureClientJar(instance.getMinecraftVersion(), versionJson, sharedVersions);
        if (clientJar != null && Files.exists(clientJar)) {
            classpathEntries.add(clientJar.toAbsolutePath().toString());
        }
        Set<String> safeClasspathEntries = new LinkedHashSet<>();
        for (String entry : classpathEntries) {
            safeClasspathEntries.add(toSafeClasspathEntry(entry));
        }
        String classpath = String.join(File.pathSeparator, safeClasspathEntries);
        String javaExe = resolveJavaPath(instance, config);
        JavaDetector.JavaInfo detectedJava = JavaDetector.inspectJava(Paths.get(javaExe));
        int detectedMajor = detectedJava != null ? detectedJava.getMajorVersion() : 0;
        Account acc = (account != null) ? account : Account.createOffline("Player");
        return buildLaunchCommand(instance, acc, config, versionJson, sharedLibs, sharedAssets, nativesDir, classpath, javaExe, detectedMajor);
    }

    private static void parseArgumentsArray(JSONArray array, Map<String, String> tokens, List<String> cmd, boolean isJvm, int javaMajor) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (item instanceof String) {
                String val = substitute((String) item, tokens);
                if (!val.isEmpty() && !val.matches("^\\$\\{[a-zA-Z0-9_]+\\}$")) {
                    if (isJvm && val.contains(File.pathSeparator)) {
                        String[] parts = val.split(java.util.regex.Pattern.quote(File.pathSeparator));
                        List<String> safeParts = new ArrayList<>();
                        for (String p : parts) {
                            safeParts.add(toSafeClasspathEntry(p));
                        }
                        val = String.join(File.pathSeparator, safeParts);
                    }
                    if (!isJvm || isJvmArgCompatibleWithJava(val, javaMajor)) {
                        cmd.add(val);
                    }
                }
            } else if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                boolean allowed = true;
                if (obj.has("rules")) {
                    allowed = isRuleAllowed(obj.getJSONArray("rules"));
                }
                if (allowed && obj.has("value")) {
                    Object valObj = obj.get("value");
                    if (valObj instanceof String) {
                        String val = substitute((String) valObj, tokens);
                        if (!val.isEmpty() && !val.matches("^\\$\\{[a-zA-Z0-9_]+\\}$")) {
                            if (isJvm && val.contains(File.pathSeparator)) {
                                String[] parts = val.split(java.util.regex.Pattern.quote(File.pathSeparator));
                                List<String> safeParts = new ArrayList<>();
                                for (String p : parts) {
                                    safeParts.add(toSafeClasspathEntry(p));
                                }
                                val = String.join(File.pathSeparator, safeParts);
                            }
                            if (!isJvm || isJvmArgCompatibleWithJava(val, javaMajor)) {
                                cmd.add(val);
                            }
                        }
                    } else if (valObj instanceof JSONArray) {
                        JSONArray valArr = (JSONArray) valObj;
                        for (int j = 0; j < valArr.length(); j++) {
                            String val = substitute(valArr.getString(j), tokens);
                            if (!val.isEmpty() && !val.matches("^\\$\\{[a-zA-Z0-9_]+\\}$")) {
                                if (isJvm && val.contains(File.pathSeparator)) {
                                    String[] parts = val.split(java.util.regex.Pattern.quote(File.pathSeparator));
                                    List<String> safeParts = new ArrayList<>();
                                    for (String p : parts) {
                                        safeParts.add(toSafeClasspathEntry(p));
                                    }
                                    val = String.join(File.pathSeparator, safeParts);
                                }
                                if (!isJvm || isJvmArgCompatibleWithJava(val, javaMajor)) {
                                    cmd.add(val);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isJvmArgCompatibleWithJava(String arg, int javaMajor) {
        if (arg == null || javaMajor <= 0) return true;
        String trimmed = arg.trim();
        // --sun-misc-unsafe-memory-access was added in Java 24 (JEP 471)
        if (trimmed.startsWith("--sun-misc-unsafe-memory-access") && javaMajor < 24) {
            return false;
        }
        // --enable-native-access was added in Java 17 (preview/incubator) and finalized in Java 22
        if (trimmed.startsWith("--enable-native-access") && javaMajor < 17) {
            return false;
        }
        // Modular arguments only exist in Java 9+
        if ((trimmed.startsWith("--add-opens") || trimmed.startsWith("--add-exports") ||
                trimmed.startsWith("--add-modules") || trimmed.startsWith("--illegal-access")) && javaMajor < 9) {
            return false;
        }
        return true;
    }

    private static boolean isRuleAllowed(JSONArray rules) {
        String osName = getOsName();
        boolean allowed = false;

        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            String action = rule.getString("action");
            boolean matches = true;

            if (rule.has("os")) {
                JSONObject os = rule.getJSONObject("os");
                if (os.has("name")) {
                    String ruleOs = os.getString("name");
                    matches = ruleOs.equalsIgnoreCase(osName);
                }
            }

            if (rule.has("features")) {
                // Features like is_demo_user, has_quick_plays_support, etc.
                // We do not enable demo mode or quick play by default.
                matches = false;
            }

            if (matches) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }

    private static String substitute(String template, Map<String, String> tokens) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        if (result.contains("${user_properties}") || result.contains("${user_property_map}")) {
            result = result.replace("${user_properties}", "{}").replace("${user_property_map}", "{}");
        }
        return result;
    }

    private static void extractNatives(JSONObject versionJson, Path libsFolder, Path nativesDir) throws IOException {
        String osKey = getOsName();
        if (!versionJson.has("libraries")) return;
        JSONArray libraries = versionJson.getJSONArray("libraries");

        Path sysLibs = VersionScanner.getSystemMinecraftLibrariesDir();
        Path legacyLibs = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "libraries");

        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.getJSONObject(i);
            if (!DownloadManager.isLibraryAllowed(lib)) continue;
            if (!lib.has("natives")) continue;
            JSONObject natives = lib.getJSONObject("natives");
            if (!natives.has(osKey)) continue;

            String classifier = natives.getString(osKey);
            String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
            classifier = classifier.replace("${arch}", arch);

            Path jarPath = null;
            JSONObject downloads = lib.optJSONObject("downloads");
            if (downloads != null && downloads.has("classifiers")) {
                JSONObject classifiers = downloads.getJSONObject("classifiers");
                if (classifiers.has(classifier)) {
                    String path = classifiers.getJSONObject(classifier).getString("path");
                    jarPath = libsFolder.resolve(path);
                    if (!Files.exists(jarPath)) {
                        if (sysLibs != null && Files.exists(sysLibs.resolve(path))) {
                            jarPath = sysLibs.resolve(path);
                        } else if (Files.exists(legacyLibs.resolve(path))) {
                            jarPath = legacyLibs.resolve(path);
                        }
                    }
                }
            } else if (lib.has("name")) {
                String baseName = lib.getString("name");
                String relPath = DownloadManager.mavenNameToPath(baseName + ":" + classifier);
                jarPath = libsFolder.resolve(relPath);
                if (!Files.exists(jarPath)) {
                    if (sysLibs != null && Files.exists(sysLibs.resolve(relPath))) {
                        jarPath = sysLibs.resolve(relPath);
                    } else if (Files.exists(legacyLibs.resolve(relPath))) {
                        jarPath = legacyLibs.resolve(relPath);
                    }
                }
            }

            if (jarPath != null && Files.exists(jarPath)) {
                try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName().toLowerCase();
                        if (entry.isDirectory() || name.startsWith("meta-inf/")) continue;
                        if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                            Path output = nativesDir.resolve(Paths.get(entry.getName()).getFileName().toString());
                            Files.createDirectories(output.getParent());
                            try (InputStream in = zipFile.getInputStream(entry)) {
                                Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed extracting natives from " + jarPath + ": " + e.getMessage());
                }
            }
        }
    }

    private static String getOsName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private static String fetchString(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "IPOCraft/2.0");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    private static final Map<String, String> SHORT_PATH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static String toSafeClasspathEntry(String pathStr) {
        if (!"windows".equals(getOsName()) || pathStr == null || pathStr.isEmpty()) {
            return pathStr;
        }

        boolean hasNonAscii = false;
        for (int i = 0; i < pathStr.length(); i++) {
            if (pathStr.charAt(i) > 127) {
                hasNonAscii = true;
                break;
            }
        }
        if (!hasNonAscii) return pathStr;

        if (SHORT_PATH_CACHE.containsKey(pathStr)) {
            return SHORT_PATH_CACHE.get(pathStr);
        }

        // 1. Try Windows 8.3 short name via cmd /c for %I in ("path") do @echo %~sI
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "for %I in (\"" + pathStr + "\") do @echo %~sI");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    line = line.trim();
                    boolean shortHasNonAscii = false;
                    for (int i = 0; i < line.length(); i++) {
                        if (line.charAt(i) > 127) {
                            shortHasNonAscii = true;
                            break;
                        }
                    }
                    if (!shortHasNonAscii && Files.exists(Paths.get(line))) {
                        SHORT_PATH_CACHE.put(pathStr, line);
                        return line;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Fallback: Copy jar to an ASCII-safe cache directory
        try {
            Path src = Paths.get(pathStr);
            if (Files.exists(src)) {
                Path cacheDir = Paths.get(System.getProperty("user.home"), "IPOCraft", "cache", "ascii_jars");
                boolean homeHasNonAscii = false;
                for (int i = 0; i < cacheDir.toString().length(); i++) {
                    if (cacheDir.toString().charAt(i) > 127) {
                        homeHasNonAscii = true;
                        break;
                    }
                }
                if (homeHasNonAscii) {
                    String sysDrive = System.getenv("SystemDrive");
                    if (sysDrive == null) sysDrive = "C:";
                    cacheDir = Paths.get(sysDrive, "ProgramData", "IPOCraft", "ascii_jars");
                }
                Files.createDirectories(cacheDir);

                String hash = Integer.toHexString(pathStr.hashCode());
                String safeName = "jar_" + hash + "_" + src.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
                Path target = cacheDir.resolve(safeName);

                if (!Files.exists(target) || Files.size(target) != Files.size(src)) {
                    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                }
                String res = target.toAbsolutePath().toString();
                SHORT_PATH_CACHE.put(pathStr, res);
                return res;
            }
        } catch (Exception e) {
            System.err.println("Failed creating safe ASCII jar entry: " + e.getMessage());
        }

        return pathStr;
    }

    public static void syncForgeAndSystemLibraries(Path sharedLibs, Consumer<String> logCallback) {
        try {
            Path sysLibs = VersionScanner.getSystemMinecraftLibrariesDir();
            Path legacyLibs = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "libraries");

            List<Path> sources = new ArrayList<>();
            if (sysLibs != null && Files.exists(sysLibs) && !sysLibs.toAbsolutePath().normalize().equals(sharedLibs.toAbsolutePath().normalize())) {
                sources.add(sysLibs);
            }
            if (Files.exists(legacyLibs) && !legacyLibs.toAbsolutePath().normalize().equals(sharedLibs.toAbsolutePath().normalize())) {
                sources.add(legacyLibs);
            }

            for (Path src : sources) {
                // 1. Sync net/minecraft/client (Forge remapped/extra/srg jars)
                Path srcClient = src.resolve("net").resolve("minecraft").resolve("client");
                if (Files.exists(srcClient)) {
                    copyDirectoryTreeIfNotExists(srcClient, sharedLibs.resolve("net").resolve("minecraft").resolve("client"));
                }

                // 2. Sync net/minecraftforge (Forge client patches, fmlloader, universal jars)
                Path srcForge = src.resolve("net").resolve("minecraftforge");
                if (Files.exists(srcForge)) {
                    copyDirectoryTreeIfNotExists(srcForge, sharedLibs.resolve("net").resolve("minecraftforge"));
                }
            }
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("[WARN] Error syncing system libraries: " + e.getMessage());
            }
        }
    }

    private static void copyDirectoryTreeIfNotExists(Path source, Path target) {
        try {
            if (!Files.exists(source)) return;
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path rel = source.relativize(dir);
                    Path destDir = target.resolve(rel);
                    if (!Files.exists(destDir)) {
                        Files.createDirectories(destDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = source.relativize(file);
                    Path destFile = target.resolve(rel);
                    if (!Files.exists(destFile) || Files.size(destFile) != Files.size(file)) {
                        Files.createDirectories(destFile.getParent());
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
    }

    private static void verifyForgeLibraries(JSONObject versionJson, Path sharedLibs, Consumer<String> logCallback) {
        try {
            String mcVer = null;
            String mcpVer = null;
            String forgeVer = null;

            if (versionJson != null && versionJson.has("arguments") && versionJson.getJSONObject("arguments").has("game")) {
                JSONArray gameArgs = versionJson.getJSONObject("arguments").getJSONArray("game");
                for (int i = 0; i < gameArgs.length() - 1; i++) {
                    Object item = gameArgs.get(i);
                    if (item instanceof String) {
                        String s = (String) item;
                        if (s.equals("--fml.mcVersion")) mcVer = gameArgs.getString(i + 1);
                        else if (s.equals("--fml.mcpVersion")) mcpVer = gameArgs.getString(i + 1);
                        else if (s.equals("--fml.forgeVersion")) forgeVer = gameArgs.getString(i + 1);
                    }
                }
            }

            if (mcVer != null && mcpVer != null) {
                Path srgJar = sharedLibs.resolve("net/minecraft/client/" + mcVer + "-" + mcpVer + "/client-" + mcVer + "-" + mcpVer + "-srg.jar");
                if (!Files.exists(srgJar)) {
                    if (logCallback != null) {
                        logCallback.accept("[ERROR] Missing required Forge library: " + srgJar.getFileName() +
                                ". Please make sure Forge " + (forgeVer != null ? forgeVer : "") + " is installed via the official Forge installer.");
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
