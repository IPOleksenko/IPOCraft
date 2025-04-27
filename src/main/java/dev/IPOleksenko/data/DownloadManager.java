package dev.IPOleksenko.data;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Iterator;

public class DownloadManager {

    private static final String ASSETS_BASE_URL = "https://resources.download.minecraft.net/";
    private static final int BUFFER_SIZE = 8192;

    public static void downloadLibraries(JSONObject versionJson, Path libsFolder) throws IOException {
        versionJson.getJSONArray("libraries").forEach(obj -> {
            JSONObject lib = (JSONObject) obj;
            if (lib.has("downloads") && lib.getJSONObject("downloads").has("artifact")) {
                JSONObject artifact = lib.getJSONObject("downloads").getJSONObject("artifact");
                String url = artifact.getString("url");
                String path = artifact.getString("path");
                Path filePath = libsFolder.resolve(path);
                try {
                    if (!Files.exists(filePath)) {
                        Files.createDirectories(filePath.getParent());
                        downloadFile(url, filePath);
                        System.out.println("Downloaded library: " + path);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    public static void downloadAssets(JSONObject versionJson, Path assetsFolder) throws IOException {
        JSONObject assetIndexInfo = versionJson.getJSONObject("assetIndex");
        String indexUrl = assetIndexInfo.getString("url");

        JSONObject indexJson;
        try (InputStream in = new URL(indexUrl).openStream()) {
            indexJson = new JSONObject(new String(in.readAllBytes()));
        }

        JSONObject objects = indexJson.getJSONObject("objects");
        Iterator<String> keys = objects.keys();
        while (keys.hasNext()) {
            String assetName = keys.next();
            JSONObject assetInfo = objects.getJSONObject(assetName);
            String hash = assetInfo.getString("hash");
            String subDir = hash.substring(0, 2);

            Path target = assetsFolder.resolve("objects").resolve(subDir).resolve(hash);
            if (Files.exists(target)) continue;

            Files.createDirectories(target.getParent());
            String downloadUrl = ASSETS_BASE_URL + subDir + "/" + hash;
            System.out.println("Downloading asset: " + assetName);

            downloadFile(downloadUrl, target);
        }

        Path indexDest = assetsFolder.resolve("indexes")
                .resolve(assetIndexInfo.getString("id") + ".json");
        Files.createDirectories(indexDest.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(indexDest)) {
            writer.write(indexJson.toString(4));
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
