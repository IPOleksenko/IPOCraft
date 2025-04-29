package dev.IPOleksenko.data;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class DownloadManager {

    private static final String ASSETS_BASE_URL = "https://resources.download.minecraft.net/";
    private static final int BUFFER_SIZE = 8192;

    public record DownloadTask(String url, Path target) {}

    public static List<DownloadTask> collectDownloadTasks(JSONObject versionJson, Path libsFolder, Path assetsFolder) throws IOException {
        List<DownloadTask> tasks = new ArrayList<>();

        versionJson.getJSONArray("libraries").forEach(obj -> {
            JSONObject lib = (JSONObject) obj;
            if (lib.has("downloads") && lib.getJSONObject("downloads").has("artifact")) {
                JSONObject artifact = lib.getJSONObject("downloads").getJSONObject("artifact");
                String url = artifact.getString("url");
                String path = artifact.getString("path");
                Path filePath = libsFolder.resolve(path);
                if (!Files.exists(filePath)) {
                    tasks.add(new DownloadTask(url, filePath));
                }
            }
        });

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

        return tasks;
    }

    public static void executeTasks(List<DownloadTask> tasks, Consumer<Double> progressCallback) throws IOException {
        int total = tasks.size();
        int done = 0;
        for (DownloadTask task : tasks) {
            Files.createDirectories(task.target().getParent());
            System.out.println("Downloading: " + task.url());
            downloadFile(task.url(), task.target());
            done++;
            if (progressCallback != null) {
                progressCallback.accept(done / (double) total);
            }
        }
    }

    public static void downloadFile(String urlStr, Path dest) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestProperty("User-Agent", "Java MinecraftLauncher");
        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to download " + urlStr + " -> HTTP " + connection.getResponseCode());
        }
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }
}
