package dev.IPOleksenko.data;

import javafx.scene.control.ListView;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class VersionManager {
    private static final String VERSIONS_FOLDER = System.getProperty("user.home") + "/.minecraft/IPOCraft";
    private final ListView<String> listView;

    public VersionManager(ListView<String> listView) {
        this.listView = listView;
        loadVersions();
    }

    public void loadVersions() {
        listView.getItems().clear();
        try {
            Path versionsPath = Paths.get(VERSIONS_FOLDER);
            if (Files.exists(versionsPath)) {
                List<String> versions = Files.list(versionsPath)
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
                listView.getItems().addAll(versions);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addVersion(String name) {
        try {
            Path newVersionPath = Paths.get(VERSIONS_FOLDER, name);
            if (!Files.exists(newVersionPath)) {
                Files.createDirectories(newVersionPath);
                loadVersions();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteSelectedVersion() {
        String selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                Path selectedPath = Paths.get(VERSIONS_FOLDER, selected);
                if (Files.exists(selectedPath)) {
                    deleteFolder(selectedPath);
                    loadVersions();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteFolder(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteFolder(entry);
                }
            }
        }
        Files.delete(path);
    }
}
