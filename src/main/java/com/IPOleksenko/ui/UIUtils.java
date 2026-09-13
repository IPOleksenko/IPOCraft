package com.IPOleksenko.ui;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.instance.Instance;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;

public class UIUtils {

    public static void applyWindowIcon(Stage stage) {
        if (stage == null) return;
        try {
            InputStream is = UIUtils.class.getResourceAsStream("/assets/icon.png");
            if (is != null) {
                stage.getIcons().setAll(new Image(is));
            }
        } catch (Exception ignored) {}
    }

    public static Image loadImage(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return null;
        try {
            File file = new File(pathOrUrl);
            if (file.exists() && file.isFile()) {
                return new Image(file.toURI().toString());
            }
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                return new Image(pathOrUrl, true);
            }
            InputStream is = UIUtils.class.getResourceAsStream(pathOrUrl);
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static Node createAccountIcon(Account acc, double size) {
        if (acc != null && acc.getCustomIconPath() != null && !acc.getCustomIconPath().trim().isEmpty()) {
            Image img = loadImage(acc.getCustomIconPath());
            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(size);
                iv.setFitHeight(size);
                iv.setPreserveRatio(false);
                Circle clip = new Circle(size / 2, size / 2, size / 2);
                iv.setClip(clip);
                return iv;
            }
        }
        if (acc != null && acc.getSkinUrl() != null && !acc.getSkinUrl().trim().isEmpty()) {
            Image img = loadImage(acc.getSkinUrl());
            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(size);
                iv.setFitHeight(size);
                Circle clip = new Circle(size / 2, size / 2, size / 2);
                iv.setClip(clip);
                return iv;
            }
        }
        Label lbl = new Label(acc != null && acc.isMicrosoft() ? "MS" : "OFF");
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (acc != null && acc.isMicrosoft() ? "#4ade80" : "#94a3b8")
                + "; -fx-background-color: #14171f; -fx-padding: 4 8; -fx-background-radius: 6px;");
        return lbl;
    }

    public static Node createInstanceIcon(Instance inst, double size) {
        if (inst != null && inst.getIcon() != null && !inst.getIcon().trim().isEmpty() && !inst.getIcon().equalsIgnoreCase("grass")) {
            Image img = loadImage(inst.getIcon());
            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(size);
                iv.setFitHeight(size);
                iv.setPreserveRatio(false);
                Rectangle clip = new Rectangle(size, size);
                clip.setArcWidth(8);
                clip.setArcHeight(8);
                iv.setClip(clip);
                return iv;
            }
        }
        Label iconLbl = new Label("MC");
        iconLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3b82f6; -fx-background-color: #14171f; -fx-padding: 4 8; -fx-background-radius: 6px;");
        return iconLbl;
    }

    public static int getTotalSystemMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                long totalBytes = ((com.sun.management.OperatingSystemMXBean) osBean).getTotalMemorySize();
                if (totalBytes > 0) {
                    return (int) (totalBytes / (1024 * 1024));
                }
            }
        } catch (Throwable ignored) {}
        return 16384;
    }

    public static java.util.List<String> getRamPresets() {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("Default (from launcher settings)");

        int totalMb = getTotalSystemMemoryMb();
        int totalGb = Math.max(2, (int) Math.round(totalMb / 1024.0));

        int[] standardSteps = {
                1024, 2048, 3072, 4096, 5120, 6144, 8192, 10240, 12288, 14336, 16384,
                20480, 24576, 28672, 32768, 40960, 49152, 65536, 98304, 131072
        };

        for (int step : standardSteps) {
            if (step <= totalMb) {
                int gb = step / 1024;
                if (step == totalMb || (gb == totalGb && Math.abs(step - totalMb) < 1024)) {
                    list.add(step + " MB (" + gb + " GB - Max OS RAM)");
                } else {
                    list.add(step + " MB (" + gb + " GB)");
                }
            }
        }

        boolean hasMax = false;
        for (String s : list) {
            if (s.contains("Max OS RAM")) {
                hasMax = true;
                break;
            }
        }
        if (!hasMax && totalMb > 1024) {
            list.add(totalMb + " MB (" + totalGb + " GB - Max OS RAM)");
        }

        return list;
    }

    public static Integer parseRamMb(String input) {
        if (input == null || input.trim().isEmpty() || input.startsWith("Default")) {
            return null;
        }
        String clean = input.trim();
        if (clean.toLowerCase().endsWith("gb") || clean.toLowerCase().endsWith("g")) {
            String num = clean.replaceAll("[^0-9.]", "");
            try {
                double gb = Double.parseDouble(num);
                int mb = (int) Math.round(gb * 1024.0);
                if (mb > 0) return mb;
            } catch (Exception ignored) {}
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*(\\d+)").matcher(clean);
        if (matcher.find()) {
            try {
                int mb = Integer.parseInt(matcher.group(1));
                if (mb > 0) return mb;
            } catch (Exception ignored) {}
        }
        String digits = clean.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                int mb = Integer.parseInt(digits);
                if (mb > 0) return mb;
            } catch (Exception ignored) {}
        }
        return null;
    }
}

