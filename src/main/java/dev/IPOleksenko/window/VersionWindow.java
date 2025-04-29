package dev.IPOleksenko.window;

import dev.IPOleksenko.data.VersionManager;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class VersionWindow {

    private static final String MINECRAFT_VERSIONS_PATH = System.getProperty("user.home") + "/IPOCraft/.minecraft/";

    public static void open(VersionManager versionManager) {
        Stage addVersionStage = new Stage();
        addVersionStage.initModality(Modality.APPLICATION_MODAL);
        addVersionStage.setTitle("Add Minecraft Version");

        addVersionStage.setResizable(false);

        InputStream iconStream = UserWindow.class.getResourceAsStream("/assets/icon.png");
        Image icon = new Image(iconStream);
        addVersionStage.getIcons().add(icon);

        VBox vbox = new VBox(10);
        vbox.setStyle("-fx-padding: 10;");

        ListView<String> versionList = new ListView<>();
        TextField customNameField = new TextField();
        customNameField.setPromptText("Enter custom version name...");

        CheckBox showRelease = new CheckBox("Show Release");
        showRelease.setSelected(true);

        CheckBox showSnapshot = new CheckBox("Show Snapshot");
        showSnapshot.setSelected(true);

        CheckBox showOther = new CheckBox("Show Other");
        showOther.setSelected(true);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("Save");

        List<VersionEntry> allVersions = fetchMinecraftVersions();
        updateList(versionList, allVersions, showRelease.isSelected(), showSnapshot.isSelected(), showOther.isSelected());

        versionList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                customNameField.setText(newValue);
            }
        });

        showRelease.setOnAction(e -> updateList(versionList, allVersions, showRelease.isSelected(), showSnapshot.isSelected(), showOther.isSelected()));
        showSnapshot.setOnAction(e -> updateList(versionList, allVersions, showRelease.isSelected(), showSnapshot.isSelected(), showOther.isSelected()));
        showOther.setOnAction(e -> updateList(versionList, allVersions, showRelease.isSelected(), showSnapshot.isSelected(), showOther.isSelected()));

        saveButton.setOnAction(e -> {
            String selectedVersion = versionList.getSelectionModel().getSelectedItem();
            String customName = customNameField.getText().trim();

            if (selectedVersion == null) {
                errorLabel.setText("Please select a Minecraft version.");
                return;
            }

            if (customName.isEmpty()) {
                errorLabel.setText("Custom version name cannot be empty.");
                return;
            }

            customName = customName.replaceAll("\\s+", "_");

            try {
                Path versionFolder = Paths.get(MINECRAFT_VERSIONS_PATH, customName);
                if (Files.exists(versionFolder)) {
                    errorLabel.setText("A version with this name already exists.");
                    return;
                }
                Files.createDirectories(versionFolder);

                VersionEntry versionEntry = findVersionEntry(allVersions, selectedVersion);
                if (versionEntry == null) {
                    errorLabel.setText("Version metadata not found.");
                    return;
                }

                JSONObject versionMeta = fetchVersionMeta(versionEntry.url);
                if (versionMeta == null) {
                    errorLabel.setText("Failed to fetch version metadata.");
                    return;
                }

                JSONObject downloads = versionMeta.getJSONObject("downloads");
                JSONObject client = downloads.getJSONObject("client");
                String clientUrl = client.getString("url");

                Path clientJar = versionFolder.resolve(customName + ".jar");
                downloadFile(clientUrl, clientJar);

                Path jsonFile = versionFolder.resolve(customName + ".json");
                saveJsonToFile(versionMeta, jsonFile);

                versionManager.loadVersions();
                addVersionStage.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
                errorLabel.setText("Failed to download or save version.");
            }
        });

        vbox.getChildren().addAll(
                showRelease, showSnapshot, showOther,
                versionList, customNameField, saveButton, errorLabel
        );

        Scene scene = new Scene(vbox, 400, 600);
        addVersionStage.setScene(scene);
        addVersionStage.showAndWait();
    }

    private static List<VersionEntry> fetchMinecraftVersions() {
        try {
            URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String response = reader.lines().collect(Collectors.joining());
            reader.close();

            JSONObject json = new JSONObject(response);
            JSONArray versions = json.getJSONArray("versions");

            return versions.toList().stream()
                    .map(obj -> {
                        Map<?, ?> map = (Map<?, ?>) obj;
                        return new VersionEntry(map.get("id").toString(), map.get("type").toString(), map.get("url").toString());
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private static VersionEntry findVersionEntry(List<VersionEntry> versions, String versionId) {
        return versions.stream().filter(v -> v.id.equals(versionId)).findFirst().orElse(null);
    }

    private static JSONObject fetchVersionMeta(String url) {
        try {
            URL metaUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) metaUrl.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String response = reader.lines().collect(Collectors.joining());
            reader.close();

            return new JSONObject(response);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void downloadFile(String urlStr, Path destination) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void saveJsonToFile(JSONObject json, Path destination) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            writer.write(json.toString(4));
        }
    }

    private static void updateList(ListView<String> listView, List<VersionEntry> versions, boolean showRelease, boolean showSnapshot, boolean showOther) {
        listView.getItems().clear();

        versions.stream()
                .filter(version -> {
                    String type = version.type.toLowerCase();
                    switch (type) {
                        case "release":
                            return showRelease;
                        case "snapshot":
                            return showSnapshot;
                        default:
                            return showOther;
                    }
                })
                .map(version -> version.id)
                .forEach(listView.getItems()::add);
    }

    private static class VersionEntry {
        String id;
        String type;
        String url;

        VersionEntry(String id, String type, String url) {
            this.id = id;
            this.type = type;
            this.url = url;
        }
    }
}
