package dev.IPOleksenko;

import dev.IPOleksenko.data.JavaManager;
import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.data.UserManager.UserEntry;
import dev.IPOleksenko.data.VersionManager;
import dev.IPOleksenko.launcher.MinecraftLauncher;
import dev.IPOleksenko.window.RenderWindow;
import dev.IPOleksenko.window.UserWindow;
import dev.IPOleksenko.window.VersionWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setOnCloseRequest(e -> {
            System.exit(0);
        });

        RenderWindow renderWindow = new RenderWindow();

        // User List
        ListView<String> userListView = new ListView<>();
        userListView.setPlaceholder(new Label("No profiles"));
        UserManager userManager = new UserManager(userListView);

        // Version List
        ListView<String> versionListView = new ListView<>();
        versionListView.setPlaceholder(new Label("No versions"));
        VersionManager versionManager = new VersionManager(versionListView);

        // Java List
        ListView<String> javaListView = new ListView<>();
        javaListView.setPlaceholder(new Label("No Java versions found"));
        JavaManager javaManager = new JavaManager(javaListView);

        userManager.loadUsers();
        versionManager.loadVersions();
        javaManager.loadJavaVersions();

        userListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(userListView));
        versionListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(versionListView));
        javaListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(javaListView));

        if (!userListView.getItems().isEmpty()) userListView.getSelectionModel().select(0);
        if (!versionListView.getItems().isEmpty()) versionListView.getSelectionModel().select(0);
        if (!javaListView.getItems().isEmpty()) javaListView.getSelectionModel().select(0);

        // UI Labels
        Label userLabel = new Label("Profiles");
        Label versionLabel = new Label("Minecraft Versions");
        Label javaLabel = new Label("Java");

        styleLabel(userLabel);
        styleLabel(versionLabel);
        styleLabel(javaLabel);

        // User Buttons
        Button addUser = styledButton("+");
        Button deleteUser = styledButton("-");
        Button editUser = styledButton("✏");

        addUser.setOnAction(e -> UserWindow.open(userManager));
        deleteUser.setOnAction(e -> userManager.deleteSelectedUser());
        editUser.setOnAction(e -> {
            int index = userListView.getSelectionModel().getSelectedIndex();
            if (index >= 0) UserWindow.open(userManager, index);
        });

        HBox userButtons = new HBox(5, addUser, deleteUser, editUser);
        userButtons.setAlignment(Pos.CENTER_LEFT);
        VBox userBox = new VBox(5, new HBox(10, userButtons, userLabel), userListView);
        styleBox(userBox);
        VBox.setVgrow(userListView, Priority.ALWAYS);

        // Version Buttons
        Button addVersion = styledButton("+");
        Button deleteVersion = styledButton("-");

        addVersion.setOnAction(e -> VersionWindow.open(versionManager));
        deleteVersion.setOnAction(e -> versionManager.deleteSelectedVersion());

        HBox versionButtons = new HBox(5, addVersion, deleteVersion);
        versionButtons.setAlignment(Pos.CENTER_LEFT);
        VBox versionBox = new VBox(5, new HBox(10, versionButtons, versionLabel), versionListView);
        styleBox(versionBox);
        VBox.setVgrow(versionListView, Priority.ALWAYS);

        // Java Button
        Button reloadJava = styledButton("Refresh List");
        reloadJava.setOnAction(e -> javaManager.loadJavaVersions());

        VBox javaBox = new VBox(5, new HBox(10, reloadJava, javaLabel), javaListView);
        styleBox(javaBox);
        VBox.setVgrow(javaListView, Priority.ALWAYS);

        // Play button and progress
        Button playButton = new Button("▶");
        playButton.setStyle("-fx-font-size:16px; -fx-padding:10px; -fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        playButton.setMaxWidth(Double.MAX_VALUE);

        VBox playBox = new VBox(playButton);
        playBox.setPadding(new Insets(10));
        playBox.setStyle("-fx-alignment: center;");
        VBox.setVgrow(playBox, Priority.ALWAYS);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #3399FF;");

        Label progressLabel = new Label("0%");
        progressLabel.setVisible(false);
        progressLabel.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-effect: dropshadow(gaussian, black, 3, 0.5, 0, 0);"
        );

        StackPane progressStack = new StackPane(progressBar, progressLabel);
        StackPane.setAlignment(progressLabel, Pos.CENTER_RIGHT);
        StackPane.setMargin(progressLabel, new Insets(0, 10, 0, 0));

        // Main container that gets disabled during loading
        HBox mainBox = new HBox(20, userBox, versionBox, javaBox, playBox);
        mainBox.setPadding(new Insets(10));
        HBox.setHgrow(userBox, Priority.ALWAYS);
        HBox.setHgrow(versionBox, Priority.ALWAYS);
        HBox.setHgrow(javaBox, Priority.ALWAYS);
        HBox.setHgrow(playBox, Priority.NEVER);

        // Play button click handler
        playButton.setOnAction(e -> {
            int userIndex = userListView.getSelectionModel().getSelectedIndex();
            int versionIndex = versionListView.getSelectionModel().getSelectedIndex();
            int javaIndex = javaListView.getSelectionModel().getSelectedIndex();

            if (userIndex < 0 || versionIndex < 0 || javaIndex < 0) {
                new Alert(Alert.AlertType.WARNING, "Please select a user, a Minecraft version and a Java version.", ButtonType.OK).showAndWait();
                return;
            }

            // Disable all UI except progressStack
            mainBox.setDisable(true);

            UserEntry userEntry = userManager.getUsers().get(userIndex);
            String username = userEntry.minecraftAccount ? userEntry.login : userEntry.username;
            String userUuid = userEntry.uuid;
            String versionName = versionListView.getItems().get(versionIndex);
            String javaPath = javaManager.getSelectedJavaPath(javaIndex);

            progressBar.setProgress(0);
            progressBar.setVisible(true);
            progressLabel.setText("0%");
            progressLabel.setVisible(true);

            Consumer<Double> onProgress = progress -> Platform.runLater(() -> {
                progressBar.setProgress(progress);
                progressLabel.setText((int) (progress * 100) + "%");
            });

            new Thread(() -> {
                try {
                    MinecraftLauncher.launch(username, userUuid, versionName, javaPath, onProgress);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                        new Alert(Alert.AlertType.ERROR, "Launch failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
                    });
                } finally {
                    Platform.runLater(() -> {
                        // Hide progress and re-enable UI
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                        mainBox.setDisable(false);
                    });
                }
            }).start();
        });

        VBox root = new VBox(10, mainBox, progressStack);
        root.setPadding(new Insets(10));
        VBox.setVgrow(mainBox, Priority.ALWAYS);
        VBox.setVgrow(progressStack, Priority.NEVER);

        renderWindow.show(primaryStage);
        renderWindow.getContentPane().getChildren().add(root);
    }

    private Button styledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 14px; -fx-min-width: 32px; -fx-background-color: #3498db; -fx-text-fill: white;");
        return btn;
    }

    private void styleLabel(Label label) {
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
    }

    private void styleBox(VBox box) {
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #cccccc; -fx-border-radius: 6px; -fx-background-radius: 6px;");
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class AlwaysSelectedListCell<T> extends ListCell<T> {
        private final ListView<T> listView;

        public AlwaysSelectedListCell(ListView<T> listView) {
            this.listView = listView;
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText(item.toString());
                if (isSelected() || listView.getSelectionModel().getSelectedItem() == item) {
                    setStyle("-fx-background-color: #3399FF; -fx-text-fill: white;");
                } else {
                    setStyle("");
                }
            }
        }
    }
}
