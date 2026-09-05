package com.IPOleksenko.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class JavaDetector {

    public static class JavaInfo {
        private final String homePath;
        private final String executablePath;
        private final int majorVersion;
        private final String versionString;
        private final String vendor;

        public JavaInfo(String homePath, String executablePath, int majorVersion, String versionString, String vendor) {
            this.homePath = homePath;
            this.executablePath = executablePath;
            this.majorVersion = majorVersion;
            this.versionString = versionString;
            this.vendor = vendor;
        }

        public String getHomePath() {
            return homePath;
        }

        public String getExecutablePath() {
            return executablePath;
        }

        public int getMajorVersion() {
            return majorVersion;
        }

        public String getVersionString() {
            return versionString;
        }

        public String getVendor() {
            return vendor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavaInfo javaInfo = (JavaInfo) o;
            return majorVersion == javaInfo.majorVersion &&
                    Objects.equals(executablePath, javaInfo.executablePath) &&
                    Objects.equals(versionString, javaInfo.versionString) &&
                    Objects.equals(vendor, javaInfo.vendor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executablePath, majorVersion, versionString, vendor);
        }

        @Override
        public String toString() {
            return "Java " + majorVersion + " (" + versionString + " - " + vendor + ")";
        }
    }

    private static class CachedJava {
        final long lastModified;
        final long fileLength;
        final JavaInfo info;

        CachedJava(long lastModified, long fileLength, JavaInfo info) {
            this.lastModified = lastModified;
            this.fileLength = fileLength;
            this.info = info;
        }
    }

    private static final Map<String, CachedJava> infoCache = new ConcurrentHashMap<>();
    private static volatile List<JavaInfo> cachedDetected = Collections.emptyList();
    private static final Set<Consumer<List<JavaInfo>>> listeners = new CopyOnWriteArraySet<>();
    private static ScheduledExecutorService monitorScheduler = null;

    /**
     * Start background daemon scheduler checking for Java runtime installations/changes every 2 seconds.
     */
    public static synchronized void startMonitoring() {
        if (monitorScheduler == null || monitorScheduler.isShutdown()) {
            monitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "JavaDetector-RealtimeMonitor");
                t.setDaemon(true);
                return t;
            });
            // Initial scan in background
            monitorScheduler.execute(JavaDetector::scanAndNotifyIfChanged);
            // Periodic scan every 2 seconds
            monitorScheduler.scheduleWithFixedDelay(JavaDetector::scanAndNotifyIfChanged, 2, 2, TimeUnit.SECONDS);
        }
    }

    /**
     * Stop background monitoring scheduler.
     */
    public static synchronized void stopMonitoring() {
        if (monitorScheduler != null && !monitorScheduler.isShutdown()) {
            monitorScheduler.shutdownNow();
            monitorScheduler = null;
        }
    }

    /**
     * Trigger an asynchronous scan immediately and notify listeners if changes are detected.
     */
    public static void refreshNowAsync() {
        ScheduledExecutorService s = monitorScheduler;
        if (s != null && !s.isShutdown()) {
            s.execute(JavaDetector::scanAndNotifyIfChanged);
        } else {
            CompletableFuture.runAsync(JavaDetector::scanAndNotifyIfChanged);
        }
    }

    /**
     * Register a listener to receive real-time updates whenever Java installations change.
     * The listener is immediately called on the JavaFX thread with the current list if available.
     */
    public static void addListener(Consumer<List<JavaInfo>> listener) {
        listeners.add(listener);
        List<JavaInfo> current = cachedDetected;
        if (!current.isEmpty()) {
            runOnFxOrDirectly(() -> {
                try {
                    listener.accept(current);
                } catch (Throwable ignored) {}
            });
        } else {
            refreshNowAsync();
        }
    }

    /**
     * Remove an existing listener.
     */
    public static void removeListener(Consumer<List<JavaInfo>> listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the cached list of detected installations, running a fresh scan if cache is empty.
     */
    public static List<JavaInfo> getCachedInstallations() {
        if (cachedDetected.isEmpty()) {
            cachedDetected = Collections.unmodifiableList(detectAllInstallations());
        }
        return cachedDetected;
    }

    private static synchronized void scanAndNotifyIfChanged() {
        try {
            List<JavaInfo> fresh = detectAllInstallations();
            if (!fresh.equals(cachedDetected)) {
                cachedDetected = Collections.unmodifiableList(fresh);
                List<JavaInfo> dispatchList = cachedDetected;
                runOnFxOrDirectly(() -> {
                    for (Consumer<List<JavaInfo>> listener : listeners) {
                        try {
                            listener.accept(dispatchList);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private static void runOnFxOrDirectly(Runnable r) {
        try {
            javafx.application.Platform.runLater(r);
        } catch (IllegalStateException | NoClassDefFoundError e) {
            r.run();
        }
    }

    public static List<JavaInfo> detectAllInstallations() {
        Map<String, JavaInfo> results = new LinkedHashMap<>();
        Set<String> candidatePaths = new LinkedHashSet<>();

        // 1. Environment variables
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) candidatePaths.add(javaHome);

        String jdkHome = System.getenv("JDK_HOME");
        if (jdkHome != null) candidatePaths.add(jdkHome);

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                if (p.toLowerCase().contains("java") || p.toLowerCase().contains("jdk")) {
                    candidatePaths.add(p);
                }
            }
        }

        // 2. Windows Registry query (discovers JDKs installed on any drive/path)
        scanWindowsRegistry(candidatePaths);

        // 3. Scan all root drives (C:\, D:\, E:\, etc.) for common Java directories
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root == null || !root.exists()) continue;
                String r = root.getAbsolutePath();
                if (!r.endsWith(File.separator)) r += File.separator;

                String[] dirs = {
                        r + "Java",
                        r + "Program Files\\Microsoft",
                        r + "Program Files\\Java",
                        r + "Program Files\\Eclipse Adoptium",
                        r + "Program Files\\AdoptOpenJDK",
                        r + "Program Files\\BellSoft",
                        r + "Program Files\\Amazon Corretto",
                        r + "Program Files\\Zulu",
                        r + "Program Files\\JetBrains",
                        r + "Program Files (x86)\\Java",
                        r + "Program Files (x86)\\Microsoft",
                        r + "Program Files (x86)\\Eclipse Adoptium",
                        r + "Program Files (x86)\\BellSoft",
                        r + "Program Files (x86)\\Zulu"
                };

                for (String d : dirs) {
                    File dir = new File(d);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirForJavas(dir, candidatePaths, 2);
                    }
                }
            }
        }

        // 4. User profile directories
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String[] userDirs = {
                    userHome + "\\.jdks",
                    userHome + "\\.sdkman\\candidates\\java",
                    (localAppData != null ? localAppData + "\\Programs" : null),
                    (localAppData != null ? localAppData + "\\Programs\\Eclipse Adoptium" : null),
                    (localAppData != null ? localAppData + "\\Programs\\Common\\Java" : null)
            };

            for (String d : userDirs) {
                if (d == null) continue;
                File dir = new File(d);
                if (dir.exists() && dir.isDirectory()) {
                    scanDirForJavas(dir, candidatePaths, 2);
                }
            }
        }

        // Inspect candidates
        for (String candidate : candidatePaths) {
            Path exe = resolveJavaExecutable(candidate);
            if (exe != null && Files.isExecutable(exe)) {
                String exeStr = exe.toAbsolutePath().normalize().toString();
                if (!results.containsKey(exeStr)) {
                    JavaInfo info = inspectJava(exe);
                    if (info != null) {
                        results.put(exeStr, info);
                    }
                }
            }
        }

        List<JavaInfo> list = new ArrayList<>(results.values());
        // Sort descending by major version (e.g. 25, 21, 17, 11, 8), then by version string
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getMajorVersion(), a.getMajorVersion());
            if (cmp != 0) return cmp;
            return compareVersions(b.getVersionString(), a.getVersionString());
        });
        return list;
    }

    private static long lastRegScan = 0;
    private static final Set<String> cachedRegCandidates = new LinkedHashSet<>();

    private static synchronized void scanWindowsRegistry(Set<String> candidatePaths) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) return;

        long now = System.currentTimeMillis();
        if (now - lastRegScan < 2500 && !cachedRegCandidates.isEmpty()) {
            candidatePaths.addAll(cachedRegCandidates);
            return;
        }

        Set<String> freshReg = new LinkedHashSet<>();
        String[] regKeys = {
                "HKLM\\SOFTWARE\\JavaSoft",
                "HKLM\\SOFTWARE\\Microsoft\\JDK",
                "HKLM\\SOFTWARE\\Eclipse Adoptium",
                "HKLM\\SOFTWARE\\AdoptOpenJDK",
                "HKLM\\SOFTWARE\\Zulu",
                "HKLM\\SOFTWARE\\BellSoft",
                "HKCU\\SOFTWARE\\JavaSoft",
                "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft"
        };

        for (String key : regKeys) {
            try {
                Process p = new ProcessBuilder("reg", "query", key, "/s")
                        .redirectErrorStream(true)
                        .start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.contains("REG_SZ") || line.contains("REG_EXPAND_SZ")) {
                            String[] parts = line.split("REG_(?:EXPAND_)?SZ", 2);
                            if (parts.length > 1) {
                                String val = parts[1].trim();
                                if (!val.isEmpty() && (val.contains(":\\") || val.startsWith("\\\\"))) {
                                    File f = new File(val);
                                    if (f.exists()) {
                                        freshReg.add(f.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    }
                }
                p.waitFor(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        cachedRegCandidates.clear();
        cachedRegCandidates.addAll(freshReg);
        lastRegScan = now;
        candidatePaths.addAll(freshReg);
    }

    public static int getRecommendedJavaMajor(String minecraftVersion) {
        if (minecraftVersion == null) return 17;
        String v = minecraftVersion.toLowerCase().trim();
        if (v.startsWith("b") || v.startsWith("a") || v.startsWith("c") || v.startsWith("rd") || v.startsWith("inf")) {
            return 8;
        }

        // Snapshots (e.g. 24w14a, 17w45a)
        if (v.matches("^\\d{2}w\\d{2}[a-z]?$")) {
            try {
                int year = Integer.parseInt(v.substring(0, 2));
                if (year >= 24) return 21;
                if (year >= 21) return 17;
                return 8;
            } catch (Exception ignored) {}
        }

        if (v.startsWith("1.")) {
            if (isVersionLowerOrEqual(v, "1.16.5")) {
                return 8;
            } else if (isVersionGreaterOrEqual(v, "1.20.5")) {
                return 21;
            } else {
                return 17;
            }
        }

        // Modern versions like 26.2, 25.x, etc.
        try {
            int major = Integer.parseInt(v.split("[.-]")[0]);
            if (major >= 24) return 21;
        } catch (Exception ignored) {}

        return 17;
    }

    public static JavaInfo findRecommendedJava(String minecraftVersion, List<JavaInfo> available) {
        if (available == null || available.isEmpty()) {
            return null;
        }

        int targetMajor = getRecommendedJavaMajor(minecraftVersion);

        // 1. Exact major match
        for (JavaInfo info : available) {
            if (info.getMajorVersion() == targetMajor) {
                return info;
            }
        }

        // 2. If target is 21 and not found, try 21 or higher (e.g. Java 25)
        if (targetMajor >= 21) {
            for (JavaInfo info : available) {
                if (info.getMajorVersion() >= 21) {
                    return info;
                }
            }
        }

        // 3. If target is 17 or higher, try 17 or higher
        if (targetMajor >= 17) {
            for (JavaInfo info : available) {
                if (info.getMajorVersion() >= 17) {
                    return info;
                }
            }
        }

        // 4. Fallback to first available
        return available.get(0);
    }

    private static void scanDirForJavas(File dir, Set<String> candidates, int depth) {
        if (depth < 0 || dir == null || !dir.isDirectory()) return;
        File binJava = new File(dir, "bin" + File.separator + "java.exe");
        if (binJava.exists()) {
            candidates.add(dir.getAbsolutePath());
            return;
        }
        File directJava = new File(dir, "java.exe");
        if (directJava.exists()) {
            candidates.add(dir.getAbsolutePath());
            return;
        }

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    scanDirForJavas(child, candidates, depth - 1);
                }
            }
        }
    }

    private static Path resolveJavaExecutable(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        try {
            Path p = Paths.get(pathStr);
            if (Files.isRegularFile(p)) {
                String fn = p.getFileName().toString().toLowerCase();
                if (fn.equals("java.exe") || fn.equals("javaw.exe")) {
                    return p;
                }
            }
            Path inBin = p.resolve("bin").resolve("java.exe");
            if (Files.isRegularFile(inBin)) {
                return inBin;
            }
            Path inBinW = p.resolve("bin").resolve("javaw.exe");
            if (Files.isRegularFile(inBinW)) {
                return inBinW;
            }
            Path directJava = p.resolve("java.exe");
            if (Files.isRegularFile(directJava)) {
                return directJava;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static JavaInfo inspectJava(Path javaExe) {
        if (javaExe == null || !Files.isRegularFile(javaExe)) return null;
        try {
            File f = javaExe.toFile();
            long lastMod = f.lastModified();
            long len = f.length();
            String key = javaExe.toAbsolutePath().normalize().toString().toLowerCase();

            CachedJava cached = infoCache.get(key);
            if (cached != null && cached.lastModified == lastMod && cached.fileLength == len) {
                return cached.info;
            }

            JavaInfo fresh = runInspectJavaProcess(javaExe);
            if (fresh != null) {
                infoCache.put(key, new CachedJava(lastMod, len, fresh));
            }
            return fresh;
        } catch (Exception e) {
            return null;
        }
    }

    private static JavaInfo runInspectJavaProcess(Path javaExe) {
        try {
            Process process = new ProcessBuilder(javaExe.toAbsolutePath().toString(), "-version")
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String versionStr = "Unknown";
            int major = 8;
            String vendor = "Java";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("version \"") || line.contains("version ")) {
                    int quoteStart = line.indexOf('\"');
                    if (quoteStart != -1) {
                        int quoteEnd = line.indexOf('\"', quoteStart + 1);
                        if (quoteEnd != -1) {
                            versionStr = line.substring(quoteStart + 1, quoteEnd);
                        }
                    }
                }
                if (line.toLowerCase().contains("microsoft")) vendor = "Microsoft";
                else if (line.toLowerCase().contains("temurin") || line.toLowerCase().contains("adoptium")) vendor = "Eclipse Temurin";
                else if (line.toLowerCase().contains("corretto")) vendor = "Amazon Corretto";
                else if (line.toLowerCase().contains("zulu")) vendor = "Azul Zulu";
                else if (line.toLowerCase().contains("oracle") || line.toLowerCase().contains("hotspot")) vendor = "Oracle HotSpot";
                else if (line.toLowerCase().contains("jetbrains")) vendor = "JetBrains Runtime";
                else if (line.toLowerCase().contains("bellsoft") || line.toLowerCase().contains("liberica")) vendor = "BellSoft Liberica";
                else if (line.toLowerCase().contains("graalvm")) vendor = "GraalVM";
                else if (line.toLowerCase().contains("semeru") || line.toLowerCase().contains("ibm")) vendor = "IBM Semeru";
            }

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            major = parseMajorVersion(versionStr);
            Path home = javaExe.getParent() != null && javaExe.getParent().getFileName().toString().equalsIgnoreCase("bin")
                    ? javaExe.getParent().getParent() : javaExe.getParent();

            return new JavaInfo(home != null ? home.toAbsolutePath().toString() : "",
                    javaExe.toAbsolutePath().toString(), major, versionStr, vendor);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseMajorVersion(String version) {
        if (version == null) return 8;
        if (version.startsWith("1.")) {
            String[] parts = version.split("\\.");
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        int dot = version.indexOf('.');
        int dash = version.indexOf('-');
        int end = (dot != -1) ? dot : (dash != -1 ? dash : version.length());
        try {
            return Integer.parseInt(version.substring(0, end));
        } catch (Exception e) {
            return 8;
        }
    }

    private static boolean isVersionLowerOrEqual(String v1, String v2) {
        return compareVersions(v1, v2) <= 0;
    }

    private static boolean isVersionGreaterOrEqual(String v1, String v2) {
        return compareVersions(v1, v2) >= 0;
    }

    private static int compareVersions(String v1, String v2) {
        String[] p1 = v1.split("[.-]");
        String[] p2 = v2.split("[.-]");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int num1 = 0;
            int num2 = 0;
            if (i < p1.length) {
                try { num1 = Integer.parseInt(p1[i]); } catch (Exception ignored) {}
            }
            if (i < p2.length) {
                try { num2 = Integer.parseInt(p2[i]); } catch (Exception ignored) {}
            }
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
}

