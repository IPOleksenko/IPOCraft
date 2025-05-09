package dev.IPOleksenko.launcher;

import dev.IPOleksenko.data.DownloadManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MinecraftLauncher {

    private static final String VERSIONS_ROOT =
            System.getProperty("user.home") + "/IPOCraft/.minecraft";

    public static void launch(String userName, String userUuid, String accessToken, String authSession, String versionName, String javaPath, Consumer<Double> onProgress) throws Exception {        if (!userName.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("Invalid username: " + userName);

        Path verDir = Paths.get(VERSIONS_ROOT, versionName);
        JSONObject versionJson = new JSONObject(Files.readString(verDir.resolve(versionName + ".json")));

        Path libsDir = verDir.resolve("libraries");
        Path assetsDir = verDir.resolve("assets");

        List<DownloadManager.DownloadTask> tasks = DownloadManager.collectDownloadTasks(versionJson, libsDir, assetsDir);
        int total = tasks.size();
        int[] done = {0};

        DownloadManager.executeTasks(tasks, ignored -> {
            done[0]++;
            onProgress.accept(done[0] / (double) total);
        });

        Path nativesDir = verDir.resolve("natives");
        Files.createDirectories(nativesDir);
        extractNatives(versionJson, libsDir, nativesDir);

        List<String> classpathList = new ArrayList<>();
        Files.walk(libsDir)
                .filter(p -> p.toString().endsWith(".jar"))
                .forEach(p -> classpathList.add(p.toAbsolutePath().toString()));
        classpathList.add(verDir.resolve(versionName + ".jar").toAbsolutePath().toString());
        String classpath = String.join(File.pathSeparator, classpathList);

        String mainClass = versionJson.optString("mainClass", "net.minecraft.client.main.Main");

        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(javaPath, "bin", "java.exe").toString());
        cmd.add("-Xmx2G");
        cmd.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        if (versionJson.has("minecraftArguments"))
        {
            cmd.add(userName);
            cmd.add(authSession);
        } else if (versionJson.has("arguments")) {
            JSONObject argumentsJson = versionJson.getJSONObject("arguments");
            if (argumentsJson.has("game")){
                cmd.add("--username");   cmd.add(userName);
            }
        }

        cmd.add("--uuid");           cmd.add(userUuid);
        cmd.add("--version");        cmd.add(versionName);
        cmd.add("--gameDir");        cmd.add(verDir.toString());
        cmd.add("--assetsDir");      cmd.add(assetsDir.toString());
        cmd.add("--assetIndex");     cmd.add(versionJson.getJSONObject("assetIndex").getString("id"));
        cmd.add("--accessToken");    cmd.add(accessToken);
        cmd.add("--userProperties"); cmd.add("{}");

        System.out.println("Launching with:\n  " + String.join(" \\\n  ", cmd));

        new ProcessBuilder(cmd)
                .directory(new File(verDir.toString()))
                .inheritIO()
                .start();
    }

    private static void extractNatives(JSONObject versionJson, Path libsFolder, Path nativesDir) throws IOException {
        String osKey = detectOS();
        JSONArray libraries = versionJson.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.getJSONObject(i);
            if (!lib.has("natives")) continue;
            JSONObject natives = lib.getJSONObject("natives");
            if (!natives.has(osKey)) continue;

            String classifier = natives.getString(osKey);
            JSONObject downloads = lib.getJSONObject("downloads");
            if (!downloads.has("classifiers")) continue;
            JSONObject classifiers = downloads.getJSONObject("classifiers");
            if (!classifiers.has(classifier)) continue;

            String url = classifiers.getJSONObject(classifier).getString("url");
            String path = classifiers.getJSONObject(classifier).getString("path");
            Path jarPath = libsFolder.resolve(path);

            if (!Files.exists(jarPath)) {
                Files.createDirectories(jarPath.getParent());
                DownloadManager.downloadFile(url, jarPath);
            }

            try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName().toLowerCase();
                    if (entry.isDirectory()) continue;
                    if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                        Path output = nativesDir.resolve(Paths.get(entry.getName()).getFileName().toString());
                        try (InputStream in = zipFile.getInputStream(entry)) {
                            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
    }

    private static String detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }
}
