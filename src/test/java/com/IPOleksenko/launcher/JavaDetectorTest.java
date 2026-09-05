package com.IPOleksenko.launcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class JavaDetectorTest {

    @Test
    public void testDetectInstallationsFindsJava() {
        List<JavaDetector.JavaInfo> javas = JavaDetector.detectAllInstallations();
        assertNotNull(javas, "Java detection should not return null");
        assertFalse(javas.isEmpty(), "Should detect at least one Java installation on system");

        System.out.println("=== Detected Java Installations (" + javas.size() + ") ===");
        boolean foundJava25 = false;
        boolean foundJava11 = false;
        boolean foundJava8 = false;

        for (JavaDetector.JavaInfo info : javas) {
            System.out.println(" - Major: " + info.getMajorVersion() +
                    " | Version: " + info.getVersionString() +
                    " | Vendor: " + info.getVendor() +
                    " | Exe: " + info.getExecutablePath());

            if (info.getMajorVersion() == 25) foundJava25 = true;
            if (info.getMajorVersion() == 11) foundJava11 = true;
            if (info.getMajorVersion() == 8) foundJava8 = true;
        }

        assertTrue(foundJava11 || foundJava8 || foundJava25, "At least one known JDK should be detected");
        if (new File("D:\\Java\\jdk-25.0.4\\bin\\java.exe").exists()) {
            assertTrue(foundJava25, "D:\\Java\\jdk-25.0.4 should be detected via registry or drive scanning!");
        }
    }

    @Test
    public void testCachingPerformance() {
        long start1 = System.currentTimeMillis();
        List<JavaDetector.JavaInfo> list1 = JavaDetector.detectAllInstallations();
        long dur1 = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        List<JavaDetector.JavaInfo> list2 = JavaDetector.detectAllInstallations();
        long dur2 = System.currentTimeMillis() - start2;

        assertEquals(list1.size(), list2.size(), "Cached run should return identical count");
        System.out.println("First scan duration: " + dur1 + " ms, Second cached scan: " + dur2 + " ms");
        assertTrue(dur2 < 200, "Cached detection should be fast (took " + dur2 + " ms)");
    }

    @Test
    public void testRecommendedJavaMajor() {
        assertEquals(8, JavaDetector.getRecommendedJavaMajor("b1.7.3"));
        assertEquals(8, JavaDetector.getRecommendedJavaMajor("a1.2.6"));
        assertEquals(8, JavaDetector.getRecommendedJavaMajor("1.12.2"));
        assertEquals(8, JavaDetector.getRecommendedJavaMajor("1.16.5"));
        assertEquals(17, JavaDetector.getRecommendedJavaMajor("1.18.2"));
        assertEquals(17, JavaDetector.getRecommendedJavaMajor("1.20.4"));
        assertEquals(21, JavaDetector.getRecommendedJavaMajor("1.20.5"));
        assertEquals(21, JavaDetector.getRecommendedJavaMajor("1.21.1"));
        assertEquals(21, JavaDetector.getRecommendedJavaMajor("26.2"));
    }

    @Test
    public void testFindRecommendedJava() {
        List<JavaDetector.JavaInfo> mockList = new ArrayList<>();
        mockList.add(new JavaDetector.JavaInfo("C:\\j25", "C:\\j25\\bin\\java.exe", 25, "25.0.4", "Oracle"));
        mockList.add(new JavaDetector.JavaInfo("C:\\j17", "C:\\j17\\bin\\java.exe", 17, "17.0.8", "Microsoft"));
        mockList.add(new JavaDetector.JavaInfo("C:\\j11", "C:\\j11\\bin\\java.exe", 11, "11.0.16", "Microsoft"));
        mockList.add(new JavaDetector.JavaInfo("C:\\j8", "C:\\j8\\bin\\java.exe", 8, "1.8.0_503", "Oracle"));

        // 1.16.5 should pick Java 8
        JavaDetector.JavaInfo rec8 = JavaDetector.findRecommendedJava("1.16.5", mockList);
        assertNotNull(rec8);
        assertEquals(8, rec8.getMajorVersion());

        // 1.18.2 should pick Java 17
        JavaDetector.JavaInfo rec17 = JavaDetector.findRecommendedJava("1.18.2", mockList);
        assertNotNull(rec17);
        assertEquals(17, rec17.getMajorVersion());

        // 1.20.5 requires 21+, should pick Java 25
        JavaDetector.JavaInfo rec25 = JavaDetector.findRecommendedJava("1.20.5", mockList);
        assertNotNull(rec25);
        assertEquals(25, rec25.getMajorVersion());
    }

    @Test
    public void testRealtimeListenerRegistration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<JavaDetector.JavaInfo>> received = new AtomicReference<>();

        java.util.function.Consumer<List<JavaDetector.JavaInfo>> listener = list -> {
            received.set(list);
            latch.countDown();
        };

        JavaDetector.addListener(listener);
        boolean ok = latch.await(2, TimeUnit.SECONDS);
        JavaDetector.removeListener(listener);

        assertTrue(ok, "Listener should receive update upon registration");
        assertNotNull(received.get(), "Received list should not be null");
    }

    @Test
    public void testRealtimeAdditionDetection() throws Exception {
        List<JavaDetector.JavaInfo> initial = JavaDetector.detectAllInstallations();
        assertFalse(initial.isEmpty());
        Path sourceExe = Paths.get(initial.get(0).getExecutablePath());

        // Create a mock JDK in user home .jdks which is scanned by JavaDetector
        Path testJdkDir = Paths.get(System.getProperty("user.home"), ".jdks", "realtime-test-jdk", "bin");
        Files.createDirectories(testJdkDir);
        Path testExe = testJdkDir.resolve("java.exe");

        CountDownLatch addLatch = new CountDownLatch(1);
        AtomicReference<List<JavaDetector.JavaInfo>> addedList = new AtomicReference<>();

        java.util.function.Consumer<List<JavaDetector.JavaInfo>> listener = list -> {
            for (JavaDetector.JavaInfo info : list) {
                if (info.getExecutablePath().equalsIgnoreCase(testExe.toAbsolutePath().normalize().toString())) {
                    addedList.set(list);
                    addLatch.countDown();
                }
            }
        };

        try {
            Files.deleteIfExists(testExe);
            JavaDetector.addListener(listener);
            Files.copy(sourceExe, testExe, StandardCopyOption.REPLACE_EXISTING);

            JavaDetector.refreshNowAsync();

            boolean detected = addLatch.await(5, TimeUnit.SECONDS);
            assertTrue(detected, "JavaDetector should detect newly placed Java in real time!");
            assertNotNull(addedList.get());
        } finally {
            JavaDetector.removeListener(listener);
            try {
                Files.deleteIfExists(testExe);
                Files.deleteIfExists(testJdkDir);
                Files.deleteIfExists(testJdkDir.getParent());
            } catch (Exception ignored) {}
        }
    }
}
