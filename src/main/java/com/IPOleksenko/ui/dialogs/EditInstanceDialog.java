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
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import com.IPOleksenko.ui.UIUtils;

public class EditInstanceDialog {

    public static void show(Stage parentStage, Instance instance, Runnable onSuccess) {
        if (instance == null) return;

        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Edit Instance & Update Version");
        stage.setResizable(false);
        UIUtils.applyWindowIcon(stage);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(540);
        root.setStyle("-fx-background-color: #161922;");

        Label title = new Label("Edit Instance & Update Version");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");

        // 1. Instance Name
        Label nameLabel = new Label("Instance Name / Название версии:");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField nameField = new TextField(instance.getName());

        // Custom Icon for Instance
        Label iconLabel = new Label("Instance Icon (Custom Icon / Своя иконка):");
        iconLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        final String[] customIconHolder = new String[]{instance.getIcon()};
        ImageView iconPreview = new ImageView();
        iconPreview.setFitWidth(36);
        iconPreview.setFitHeight(36);
        iconPreview.setPreserveRatio(false);
        Rectangle iconClip = new Rectangle(36, 36);
        iconClip.setArcWidth(8);
        iconClip.setArcHeight(8);
        iconPreview.setClip(iconClip);

        Image currentImg = UIUtils.loadImage(instance.getIcon());
        if (currentImg == null || currentImg.isError()) {
            currentImg = UIUtils.loadImage("/assets/icon.png");
        }
        if (currentImg != null) iconPreview.setImage(currentImg);

        Button chooseIconBtn = new Button("Browse Custom Icon...");
        chooseIconBtn.getStyleClass().add("btn-secondary");

        Button resetIconBtn = new Button("Reset to Default");
        resetIconBtn.getStyleClass().add("btn-secondary");

        chooseIconBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Instance Icon");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg")
            );
            File file = fc.showOpenDialog(stage);
            if (file != null && file.exists()) {
                customIconHolder[0] = file.getAbsolutePath();
                Image img = new Image(file.toURI().toString());
                if (!img.isError()) {
                    iconPreview.setImage(img);
                }
            }
        });

        resetIconBtn.setOnAction(e -> {
            customIconHolder[0] = "grass";
            Image img = UIUtils.loadImage("/assets/icon.png");
            if (img != null) iconPreview.setImage(img);
        });

        HBox iconBox = new HBox(10, iconPreview, chooseIconBtn, resetIconBtn);
        iconBox.setAlignment(Pos.CENTER_LEFT);

        // 2. Version Selection
        Label verLabel = new Label("Minecraft Version (Change/Update version):");
        verLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Region verSpacer = new Region();
        HBox.setHgrow(verSpacer, Priority.ALWAYS);

        Button refreshVerBtn = new Button("Refresh Versions");
        refreshVerBtn.getStyleClass().add("btn-secondary");

        HBox verHeader = new HBox(8, verLabel, verSpacer, refreshVerBtn);
        verHeader.setAlignment(Pos.CENTER_LEFT);

        // Filter Checkboxes - ALL SELECTED BY DEFAULT
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

        Label searchLabel = new Label("Search Version / Поиск версии:");
        searchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TextField searchField = new TextField();
        searchField.setPromptText("Type version to search...");

        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setVisibleRowCount(16);
        versionCombo.getItems().add(instance.getMinecraftVersion() + " [Current]");
        versionCombo.setValue(instance.getMinecraftVersion() + " [Current]");

        // 3. Java Runtime for Instance
        Label javaLabel = new Label("Java Runtime for Instance:");
        javaLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ComboBox<String> javaCombo = new ComboBox<>();
        javaCombo.setMaxWidth(Double.MAX_VALUE);
        javaCombo.setOnShowing(e -> JavaDetector.refreshNowAsync());

        String initialJava = instance.getCustomJavaPath();

        Consumer<List<JavaDetector.JavaInfo>> javaListener = detected -> {
            String currentSel = javaCombo.getValue();
            if (currentSel == null) {
                currentSel = initialJava;
            }

            List<String> items = new ArrayList<>();
            items.add("Auto (Recommended for version)");
            for (JavaDetector.JavaInfo info : detected) {
                items.add(info.getExecutablePath() + " [Java " + info.getMajorVersion() + " - " + info.getVendor() + "]");
            }

            if (currentSel != null && !currentSel.startsWith("Auto")) {
                String pathOnly = currentSel.contains("[") ? currentSel.substring(0, currentSel.indexOf('[')).trim() : currentSel.trim();
                boolean inList = false;
                for (String it : items) {
                    if (it.startsWith(pathOnly)) {
                        inList = true;
                        break;
                    }
                }
                if (!inList && !pathOnly.isEmpty()) {
                    items.add(pathOnly);
                }
            }

            javaCombo.getItems().setAll(items);

            boolean found = false;
            if (currentSel != null && !currentSel.startsWith("Auto")) {
                for (String item : items) {
                    if (item.equals(currentSel) || item.startsWith(currentSel.split(" \\[")[0])) {
                        javaCombo.setValue(item);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                javaCombo.setValue("Auto (Recommended for version)");
            }
        };

        JavaDetector.addListener(javaListener);
        stage.setOnHidden(e -> JavaDetector.removeListener(javaListener));

        // 4. Memory override
        Label ramLabel = new Label("Memory Allocation (RAM):");
        ramLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        ComboBox<String> ramCombo = new ComboBox<>();
        ramCombo.getItems().setAll(UIUtils.getRamPresets());
        ramCombo.setEditable(true);
        ramCombo.setMaxWidth(Double.MAX_VALUE);
        ramCombo.setPromptText("Select preset or type any RAM (e.g. 8192, 16 GB)...");

        if (instance.getCustomMemoryMb() != null && instance.getCustomMemoryMb() > 0) {
            int mem = instance.getCustomMemoryMb();
            String target = mem + " MB";
            boolean found = false;
            for (String r : ramCombo.getItems()) {
                if (r.startsWith(target)) {
                    ramCombo.setValue(r);
                    found = true;
                    break;
                }
            }
            if (!found) {
                double gb = mem / 1024.0;
                ramCombo.setValue(mem + " MB (" + String.format(java.util.Locale.ROOT, "%.1f", gb) + " GB)");
            }
        } else {
            ramCombo.setValue("Default (from launcher settings)");
        }

        // 5. JVM Arguments
        Label jvmLabel = new Label("Custom JVM Arguments:");
        jvmLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextArea jvmArgsArea = new TextArea(instance.getCustomJvmArgs() != null ? instance.getCustomJvmArgs() : "");
        jvmArgsArea.setPrefRowCount(3);
        jvmArgsArea.setWrapText(true);
        jvmArgsArea.setPromptText("-XX:+UseG1GC ...");

        CheckBox overrideJvmCheck = new CheckBox("Override launcher default JVM flags (completely replace instead of append)");
        overrideJvmCheck.setSelected(instance.isOverrideJvmArgs());
        overrideJvmCheck.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");

        Button copyDefaultJvmBtn = new Button("Load Default JVM Flags into Editor");
        copyDefaultJvmBtn.getStyleClass().add("btn-secondary");
        copyDefaultJvmBtn.setOnAction(e -> {
            jvmArgsArea.setText(com.IPOleksenko.config.ConfigManager.getInstance().getConfig().getJvmArgs());
            overrideJvmCheck.setSelected(true);
        });

        HBox jvmOptsBox = new HBox(12, overrideJvmCheck, copyDefaultJvmBtn);
        jvmOptsBox.setAlignment(Pos.CENTER_LEFT);

        // 6. Custom Game / Minecraft Arguments
        Label gameArgsLabel = new Label("Custom Game / Minecraft Arguments (optional):");
        gameArgsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField gameArgsField = new TextField(instance.getCustomGameArgs() != null ? instance.getCustomGameArgs() : "");
        gameArgsField.setPromptText("--quickPlayMultiplayer=... --demo");

        // 7. Preview Command Button
        Button previewCmdBtn = new Button("Preview Launch Command");
        previewCmdBtn.getStyleClass().add("btn-secondary");
        previewCmdBtn.setOnAction(e -> {
            try {
                Instance temp = new Instance(nameField.getText().trim(), CreateInstanceDialog.cleanVersionId(versionCombo.getValue()));
                temp.setId(instance.getId());
                temp.setCustomJavaPath(instance.getCustomJavaPath());
                String selJava = javaCombo.getValue();
                if (selJava != null && !selJava.startsWith("Auto")) {
                    int bracket = selJava.indexOf('[');
                    temp.setCustomJavaPath((bracket != -1 ? selJava.substring(0, bracket).trim() : selJava).trim());
                }
                String selRam = ramCombo.getValue();
                if (selRam != null && !selRam.startsWith("Default")) {
                    try {
                        temp.setCustomMemoryMb(Integer.parseInt(selRam.split(" ")[0]));
                    } catch (Exception ignored) {}
                }
                temp.setCustomJvmArgs(jvmArgsArea.getText());
                temp.setCustomGameArgs(gameArgsField.getText());
                temp.setOverrideJvmArgs(overrideJvmCheck.isSelected());

                List<String> cmd = com.IPOleksenko.launcher.MinecraftLauncher.previewLaunchCommand(temp, com.IPOleksenko.auth.AccountManager.getInstance().getActiveAccount());
                showPreviewDialog(stage, cmd);
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Failed to preview launch command:\n" + ex.getMessage(), ButtonType.OK);
                err.initOwner(stage);
                err.show();
            }
        });

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, previewCmdBtn, new Region(), cancelBtn, saveBtn);
        HBox.setHgrow(buttons.getChildren().get(1), Priority.ALWAYS);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
                title,
                nameLabel, nameField,
                iconLabel, iconBox,
                verHeader, filterBox, searchLabel, searchField, versionCombo,
                javaLabel, javaCombo,
                ramLabel, ramCombo,
                jvmLabel, jvmArgsArea, jvmOptsBox,
                gameArgsLabel, gameArgsField,
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
                boolean matched = false;
                if (previousSelection != null && versionCombo.getItems().contains(previousSelection)) {
                    versionCombo.setValue(previousSelection);
                    matched = true;
                }
                if (!matched) {
                    String instClean = instance.getMinecraftVersion();
                    for (String item : versionCombo.getItems()) {
                        if (CreateInstanceDialog.cleanVersionId(item).equalsIgnoreCase(instClean)) {
                            versionCombo.setValue(item);
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
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

            // Fetch official Mojang version manifest
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
            }, "Mojang-Manifest-Loader-Edit").start();
        };

        refreshVerBtn.setOnAction(e -> loadVersions.run());
        loadVersions.run();

        saveBtn.setOnAction(e -> {
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

            String verId = CreateInstanceDialog.cleanVersionId(rawVer);

            // Java selection
            String selJava = javaCombo.getValue();
            String customJava = "";
            if (selJava != null && !selJava.startsWith("Auto")) {
                int bracket = selJava.indexOf('[');
                customJava = (bracket != -1 ? selJava.substring(0, bracket).trim() : selJava).trim();
            }

            // RAM selection
            Integer customRam = UIUtils.parseRamMb(ramCombo.getValue());

            if (customIconHolder[0] != null) {
                instance.setIcon(customIconHolder[0]);
            }

            InstanceManager.getInstance().updateInstance(
                    instance, name, verId, customJava, customRam,
                    jvmArgsArea.getText(), gameArgsField.getText(), overrideJvmCheck.isSelected()
            );
            if (onSuccess != null) onSuccess.run();
            stage.close();
        });

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #161922; -fx-background: #161922;");

        Scene scene = new Scene(scrollPane, 590, 680);
        scene.getStylesheets().add(EditInstanceDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void showPreviewDialog(Stage parentStage, List<String> cmd) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Preview Launch Command");
        stage.setResizable(true);
        UIUtils.applyWindowIcon(stage);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #161922;");

        Label title = new Label("Full Launch Command Preview:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 14px;");

        TextArea cmdArea = new TextArea(String.join(" ", cmd));
        cmdArea.setWrapText(true);
        cmdArea.setEditable(false);
        cmdArea.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 12px;");
        VBox.setVgrow(cmdArea, Priority.ALWAYS);

        Button copyBtn = new Button("Copy to Clipboard");
        copyBtn.getStyleClass().add("btn-primary");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(String.join(" ", cmd));
            clipboard.setContent(content);
            copyBtn.setText("Copied!");
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(10, copyBtn, closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, cmdArea, btnBox);

        Scene scene = new Scene(root, 650, 420);
        scene.getStylesheets().add(EditInstanceDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}

