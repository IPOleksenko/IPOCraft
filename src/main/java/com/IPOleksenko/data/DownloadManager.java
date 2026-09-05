package com.IPOleksenko.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DownloadManager {

    private static final String ASSETS_BASE_URL = "https://resources.download.minecraft.net/";
    private static final int BUFFER_SIZE = 8192;

    public static class DownloadTask {
        private final String url;
        private final Path target;

        public DownloadTask(String url, Path target) {
            this.url = url;
            this.target = target;
        }

        public String getUrl() {
            return url;
        }

        public Path getTarget() {
            return target;
        }

        public String url() {
            return url;
        }

        public Path target() {
            return target;
        }
    }

    public static boolean isLibraryAllowed(JSONObject lib) {
        if (!lib.has("rules")) return true;
        JSONArray rules = lib.getJSONArray("rules");
        String currentOs = getOsKey();
        boolean allowed = false;

        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            String action = rule.getString("action");
            boolean matches = true;

            if (rule.has("os")) {
                JSONObject os = rule.getJSONObject("os");
                if (os.has("name")) {
                    String ruleOs = os.getString("name");
                    matches = ruleOs.equalsIgnoreCase(currentOs);
                }
            }

            if (matches) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }

    public static List<DownloadTask> collectDownloadTasks(JSONObject versionJson, Path libsFolder, Path assetsFolder) throws IOException {
        List<DownloadTask> tasks = new ArrayList<>();

        if (versionJson.has("libraries")) {
            versionJson.getJSONArray("libraries").forEach(obj -> {
                JSONObject lib = (JSONObject) obj;
                if (!isLibraryAllowed(lib)) return;

                // Download native libraries (LWJGL, OpenAL, etc.) for older and legacy versions
                if (lib.has("natives")) {
                    JSONObject natives = lib.getJSONObject("natives");
                    String osKey = getOsKey();
                    if (natives.has(osKey)) {
                        String classifier = natives.getString(osKey);
                        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
                        classifier = classifier.replace("${arch}", arch);

                        if (lib.has("downloads") && lib.getJSONObject("downloads").has("classifiers")) {
                            JSONObject classifiers = lib.getJSONObject("downloads").getJSONObject("classifiers");
                            if (classifiers.has(classifier)) {
                                JSONObject cArt = classifiers.getJSONObject(classifier);
                                String cUrl = cArt.getString("url");
                                String cPath = cArt.getString("path");
                                Path cFilePath = libsFolder.resolve(cPath);
                                if (!tryCopyFromExisting(cPath, cFilePath)) {
                                    tasks.add(new DownloadTask(cUrl, cFilePath));
                                }
                            }
                        } else if (lib.has("name")) {
                            String baseName = lib.getString("name");
                            String relPath = mavenNameToPath(baseName + ":" + classifier);
                            Path cFilePath = libsFolder.resolve(relPath);
                            if (!tryCopyFromExisting(relPath, cFilePath)) {
                                String baseUrl = lib.optString("url", "https://libraries.minecraft.net/");
                                if (!baseUrl.endsWith("/")) baseUrl += "/";
                                tasks.add(new DownloadTask(baseUrl + relPath, cFilePath));
                            }
                        }
                    }
                    return; // Native library handled; do not process as normal jar
                }

                if (lib.has("downloads") && lib.getJSONObject("downloads").has("artifact")) {
                    JSONObject artifact = lib.getJSONObject("downloads").getJSONObject("artifact");
                    String url = artifact.getString("url");
                    String path = artifact.getString("path");
                    Path filePath = libsFolder.resolve(path);
                    if (!tryCopyFromExisting(path, filePath)) {
                        tasks.add(new DownloadTask(url, filePath));
                    }
                } else if (lib.has("name")) {
                    String relPath = mavenNameToPath(lib.getString("name"));
                    Path filePath = libsFolder.resolve(relPath);
                    if (!tryCopyFromExisting(relPath, filePath)) {
                        String baseUrl = lib.optString("url", "https://libraries.minecraft.net/");
                        if (!baseUrl.endsWith("/")) baseUrl += "/";
                        tasks.add(new DownloadTask(baseUrl + relPath, filePath));
                    }
                }
            });
        }

        if (versionJson.has("assetIndex")) {
            JSONObject assetIndexInfo = versionJson.getJSONObject("assetIndex");
            String indexUrl = assetIndexInfo.getString("url");

            JSONObject indexJson;
            try (InputStream in = new URL(indexUrl).openStream()) {
                indexJson = new JSONObject(new String(in.readAllBytes()));
            }

            JSONObject objects = indexJson.getJSONObject("objects");
            for (String assetName : objects.keySet()) {
                JSONObject assetInfo = objects.getJSONObject(assetName);
                String hash = assetInfo.getString("hash");
                String subDir = hash.substring(0, 2);
                Path target = assetsFolder.resolve("objects").resolve(subDir).resolve(hash);
                if (!Files.exists(target)) {
                    String downloadUrl = ASSETS_BASE_URL + subDir + "/" + hash;
                    tasks.add(new DownloadTask(downloadUrl, target));
                }
            }

            Path indexDest = assetsFolder.resolve("indexes")
                    .resolve(assetIndexInfo.getString("id") + ".json");
            Files.createDirectories(indexDest.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(indexDest)) {
                writer.write(indexJson.toString(4));
            }
        }

        return tasks;
    }

    public static void reconstructLegacyAssets(JSONObject versionJson, Path assetsFolder, Path gameDir) {
        if (versionJson == null || !versionJson.has("assetIndex")) return;
        JSONObject assetIndexInfo = versionJson.getJSONObject("assetIndex");
        String indexId = assetIndexInfo.optString("id", "");

        Path indexFile = assetsFolder.resolve("indexes").resolve(indexId + ".json");
        if (!Files.exists(indexFile)) return;

        try {
            String jsonStr = Files.readString(indexFile);
            JSONObject indexJson = new JSONObject(jsonStr);
            boolean isVirtual = indexJson.optBoolean("virtual", false);
            boolean mapToResources = indexJson.optBoolean("map_to_resources", false);

            if (!isVirtual && !mapToResources && !indexId.startsWith("pre-1.6") && !indexId.equals("legacy")) {
                return;
            }

            Path virtualRoot = assetsFolder.resolve("virtual").resolve(indexId);
            Path resourcesRoot = gameDir != null ? gameDir.resolve("resources") : null;

            JSONObject objects = indexJson.optJSONObject("objects");
            if (objects == null) return;

            for (String assetName : objects.keySet()) {
                JSONObject obj = objects.getJSONObject(assetName);
                String hash = obj.getString("hash");
                String sub = hash.substring(0, 2);
                Path source = assetsFolder.resolve("objects").resolve(sub).resolve(hash);
                if (!Files.exists(source)) continue;

                Path vDest = virtualRoot.resolve(assetName);
                if (!Files.exists(vDest)) {
                    Files.createDirectories(vDest.getParent());
                    try {
                        Files.copy(source, vDest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {}
                }

                if (mapToResources && resourcesRoot != null) {
                    Path rDest = resourcesRoot.resolve(assetName);
                    if (!Files.exists(rDest)) {
                        Files.createDirectories(rDest.getParent());
                        try {
                            Files.copy(source, rDest, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not reconstruct legacy assets: " + e.getMessage());
        }
    }

    public static String mavenNameToPath(String mavenName) {
        String[] parts = mavenName.split(":");
        if (parts.length < 3) return mavenName.replace(':', '/') + ".jar";
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    public static void executeTasks(List<DownloadTask> tasks, Consumer<Double> progressCallback) throws IOException {
        if (tasks == null || tasks.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(1.0);
            return;
        }

        int total = tasks.size();
        AtomicInteger done = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();

        for (DownloadTask task : tasks) {
            futures.add(executor.submit(() -> {
                try {
                    Files.createDirectories(task.target.getParent());
                    downloadFile(task.url, task.target);
                } catch (Exception e) {
                    System.err.println("Failed to download: " + task.url + " (" + e.getMessage() + ")");
                } finally {
                    int currentDone = done.incrementAndGet();
                    if (progressCallback != null) {
                        progressCallback.accept(currentDone / (double) total);
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {}
        }

        executor.shutdown();
    }

    public static void downloadFile(String urlStr, Path dest) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestProperty("User-Agent", "IPOCraft/2.0");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        if (code != 200) {
            throw new IOException("Failed to download " + urlStr + " -> HTTP " + code);
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    public static String getOsKey() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private static boolean tryCopyFromExisting(String relPath, Path targetPath) {
        if (Files.exists(targetPath)) return true;
        try {
            Path sysLibs = com.IPOleksenko.launcher.VersionScanner.getSystemMinecraftLibrariesDir();
            Path legacyLibs = Paths.get(System.getProperty("user.home"), "IPOCraft", ".minecraft", "libraries");
            if (sysLibs != null && Files.exists(sysLibs.resolve(relPath))) {
                Files.createDirectories(targetPath.getParent());
                Files.copy(sysLibs.resolve(relPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            if (Files.exists(legacyLibs.resolve(relPath))) {
                Files.createDirectories(targetPath.getParent());
                Files.copy(legacyLibs.resolve(relPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
