package com.IPOleksenko.ui.dialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import com.IPOleksenko.instance.Instance;
import com.IPOleksenko.instance.InstanceManager;
import com.IPOleksenko.launcher.JavaDetector;
import com.IPOleksenko.launcher.VersionScanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

public class CreateInstanceDialog {

    public static void show(Stage parentStage, Runnable onSuccess) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Create Instance");
        stage.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(500);
        root.setStyle("-fx-background-color: #161922;");

        Label title = new Label("Create New Minecraft Instance");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");

        // 1. Instance Name (DO NOT auto-fill version or modloader)
        Label nameLabel = new Label("Instance Name:");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Survival, Hardcore, Creative");

        // 2. Version Selection
        Label verLabel = new Label("Minecraft Version:");
        verLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Region verSpacer = new Region();
        HBox.setHgrow(verSpacer, Priority.ALWAYS);

        Button refreshVerBtn = new Button("Refresh Versions");
        refreshVerBtn.getStyleClass().add("btn-secondary");

        HBox verHeader = new HBox(8, verLabel, verSpacer, refreshVerBtn);
        verHeader.setAlignment(Pos.CENTER_LEFT);

        // Filter Checkboxes - ALL SELECTED BY DEFAULT as requested
        CheckBox cbReleases = new CheckBox("Releases");
        cbReleases.setSelected(true);
        cbReleases.setStyle("-fx-text-fill: #cbd5e1;");

        CheckBox cbSnapshots = new CheckBox("Snapshots");
        cbSnapshots.setSelected(true);
        cbSnapshots.setStyle("-fx-text-fill: #cbd5e1;");

        CheckBox cbOldBeta = new CheckBox("Old Beta");
        cbOldBeta.setSelected(true);
        cbOldBeta.setStyle("-fx-text-fill: #cbd5e1;");

        CheckBox cbOldAlpha = new CheckBox("Old Alpha");
        cbOldAlpha.setSelected(true);
        cbOldAlpha.setStyle("-fx-text-fill: #cbd5e1;");

        CheckBox cbCustom = new CheckBox("Installed");
        cbCustom.setSelected(true);
        cbCustom.setStyle("-fx-text-fill: #cbd5e1;");

        FlowPane filterBox = new FlowPane(10, 8, cbReleases, cbSnapshots, cbOldBeta, cbOldAlpha, cbCustom);

        TextField searchField = new TextField();

        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setVisibleRowCount(16);
        versionCombo.setPromptText("Loading official versions...");

        // 3. Java Runtime for Instance
        Label javaLabel = new Label("Java Runtime for Instance:");
        javaLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ComboBox<String> javaCombo = new ComboBox<>();
        javaCombo.setMaxWidth(Double.MAX_VALUE);
        javaCombo.setOnShowing(e -> JavaDetector.refreshNowAsync());

        Consumer<List<JavaDetector.JavaInfo>> javaListener = detected -> {
            String currentSel = javaCombo.getValue();
            List<String> items = new ArrayList<>();
            items.add("Auto (Recommended for version)");
            for (JavaDetector.JavaInfo info : detected) {
                items.add(info.getExecutablePath() + " [Java " + info.getMajorVersion() + " - " + info.getVendor() + "]");
            }
            javaCombo.getItems().setAll(items);
            if (currentSel != null && !currentSel.startsWith("Auto")) {
                boolean found = false;
                for (String item : items) {
                    if (item.equals(currentSel) || item.startsWith(currentSel.split(" \\[")[0])) {
                        javaCombo.setValue(item);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    javaCombo.setValue("Auto (Recommended for version)");
                }
            } else {
                javaCombo.setValue("Auto (Recommended for version)");
            }
        };

        JavaDetector.addListener(javaListener);
        stage.setOnHidden(e -> JavaDetector.removeListener(javaListener));

        // 4. Memory override
        Label ramLabel = new Label("Memory Allocation (RAM):");
        ramLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        ComboBox<String> ramCombo = new ComboBox<>();
        ramCombo.getItems().addAll(
                "Default (from launcher settings)",
                "2048 MB (2 GB)",
                "3072 MB (3 GB)",
                "4096 MB (4 GB)",
                "6144 MB (6 GB)",
                "8192 MB (8 GB)",
                "12288 MB (12 GB)"
        );
        ramCombo.setValue("Default (from launcher settings)");
        ramCombo.setMaxWidth(Double.MAX_VALUE);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");

        Button createBtn = new Button("Create Instance");
        createBtn.getStyleClass().add("btn-primary");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, cancelBtn, createBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
                title,
                nameLabel, nameField,
                verHeader, filterBox, searchField, versionCombo,
                javaLabel, javaCombo,
                ramLabel, ramCombo,
                statusLabel,
                buttons
        );

        List<VersionScanner.VersionItem> installed = new ArrayList<>();
        List<String> vanillaReleases = new ArrayList<>();
        List<String> vanillaSnapshots = new ArrayList<>();
        List<String> vanillaOldBetas = new ArrayList<>();
        List<String> vanillaOldAlphas = new ArrayList<>();

        Runnable updateCombo = () -> {
            String previousSelection = versionCombo.getValue();
            versionCombo.getItems().clear();
            String query = searchField.getText().trim().toLowerCase();

            // 1. Local / Custom
            if (cbCustom.isSelected()) {
                for (VersionScanner.VersionItem v : installed) {
                    String tag = v.getId() + " [Installed]";
                    if (query.isEmpty() || tag.toLowerCase().contains(query)) {
                        versionCombo.getItems().add(tag);
                    }
                }
            }

            // 2. Official Releases
            if (cbReleases.isSelected()) {
                for (String v : vanillaReleases) {
                    String tag = v + " [Release]";
                    if (query.isEmpty() || tag.toLowerCase().contains(query) || v.toLowerCase().contains(query)) {
                        versionCombo.getItems().add(tag);
                    }
                }
            }

            // 3. Official Snapshots
            if (cbSnapshots.isSelected()) {
                for (String v : vanillaSnapshots) {
                    String tag = v + " [Snapshot]";
                    if (query.isEmpty() || tag.toLowerCase().contains(query) || v.toLowerCase().contains(query)) {
                        versionCombo.getItems().add(tag);
                    }
                }
            }

            // 4. Old Beta
            if (cbOldBeta.isSelected()) {
                for (String v : vanillaOldBetas) {
                    String tag = v + " [Old Beta]";
                    if (query.isEmpty() || tag.toLowerCase().contains(query) || v.toLowerCase().contains(query)) {
                        versionCombo.getItems().add(tag);
                    }
                }
            }

            // 5. Old Alpha
            if (cbOldAlpha.isSelected()) {
                for (String v : vanillaOldAlphas) {
                    String tag = v + " [Old Alpha]";
                    if (query.isEmpty() || tag.toLowerCase().contains(query) || v.toLowerCase().contains(query)) {
                        versionCombo.getItems().add(tag);
                    }
                }
            }

            if (!versionCombo.getItems().isEmpty()) {
                if (previousSelection != null && versionCombo.getItems().contains(previousSelection)) {
                    versionCombo.setValue(previousSelection);
                } else {
                    versionCombo.setValue(versionCombo.getItems().get(0));
                }
            }
        };

        cbReleases.setOnAction(e -> updateCombo.run());
        cbSnapshots.setOnAction(e -> updateCombo.run());
        cbOldBeta.setOnAction(e -> updateCombo.run());
        cbOldAlpha.setOnAction(e -> updateCombo.run());
        cbCustom.setOnAction(e -> updateCombo.run());
        searchField.textProperty().addListener((obs, o, n) -> updateCombo.run());

        Runnable loadVersions = () -> {
            refreshVerBtn.setDisable(true);
            statusLabel.setText("");
            versionCombo.setPromptText("Loading official versions...");
            installed.clear();
            installed.addAll(VersionScanner.getInstalledVersions());
            vanillaReleases.clear();
            vanillaSnapshots.clear();
            vanillaOldBetas.clear();
            vanillaOldAlphas.clear();
            updateCombo.run();

            // Fetch official Mojang versions
            new Thread(() -> {
                try {
                    URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "IPOCraft/2.0");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    int respCode = conn.getResponseCode();
                    if (respCode < 200 || respCode >= 300) {
                        throw new IOException("HTTP " + respCode + " (" + conn.getResponseMessage() + ")");
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray arr = json.getJSONArray("versions");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String id = item.getString("id");
                        String type = item.getString("type");
                        if ("release".equalsIgnoreCase(type)) {
                            vanillaReleases.add(id);
                        } else if ("snapshot".equalsIgnoreCase(type)) {
                            vanillaSnapshots.add(id);
                        } else if ("old_beta".equalsIgnoreCase(type)) {
                            vanillaOldBetas.add(id);
                        } else if ("old_alpha".equalsIgnoreCase(type)) {
                            vanillaOldAlphas.add(id);
                        }
                    }

                    Platform.runLater(() -> {
                        refreshVerBtn.setDisable(false);
                        updateCombo.run();
                    });
                } catch (Exception e) {
                    String errorMsg;
                    if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof java.net.NoRouteToHostException) {
                        errorMsg = "No internet connection: unable to load remote versions.";
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        errorMsg = "Connection timed out: unable to load remote versions.";
                    } else {
                        errorMsg = "Failed to load versions: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    }

                    Platform.runLater(() -> {
                        refreshVerBtn.setDisable(false);
                        statusLabel.setText(errorMsg);
                        versionCombo.setPromptText(errorMsg);
                        updateCombo.run();
                    });
                }
            }, "Mojang-Manifest-Loader").start();
        };

        refreshVerBtn.setOnAction(e -> loadVersions.run());
        loadVersions.run();

        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String rawVer = versionCombo.getValue();

            if (name.isEmpty()) {
                statusLabel.setText("Please enter an instance name.");
                return;
            }
            if (rawVer == null || rawVer.isEmpty()) {
                statusLabel.setText("Please select a Minecraft version.");
                return;
            }

            String verId = cleanVersionId(rawVer);
            Instance inst = InstanceManager.getInstance().createInstance(name, verId);

            // Java selection
            String selJava = javaCombo.getValue();
            if (selJava != null && !selJava.startsWith("Auto")) {
                int bracket = selJava.indexOf('[');
                inst.setCustomJavaPath((bracket != -1 ? selJava.substring(0, bracket).trim() : selJava).trim());
            }

            // RAM selection
            String selRam = ramCombo.getValue();
            if (selRam != null && !selRam.startsWith("Default")) {
                int mb = Integer.parseInt(selRam.split(" ")[0]);
                inst.setCustomMemoryMb(mb);
            }

            InstanceManager.getInstance().saveInstances();
            if (onSuccess != null) onSuccess.run();
            stage.close();
        });

        Scene scene = new Scene(root);
        scene.getStylesheets().add(CreateInstanceDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    public static String cleanVersionId(String raw) {
        if (raw == null) return "";
        int bracket = raw.indexOf('[');
        if (bracket != -1) {
            return raw.substring(0, bracket).trim();
        }
        return raw.trim();
    }
}
