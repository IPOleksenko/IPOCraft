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
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        RenderWindow renderWindow = new RenderWindow();
        renderWindow.show(primaryStage);

        ListView<String> userListView = new ListView<>();
        userListView.setPrefSize(200, 400);
        Label userPlaceholder = new Label("No profiles");
        userPlaceholder.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        userListView.setPlaceholder(userPlaceholder);
        UserManager userManager = new UserManager(userListView);

        ListView<String> versionListView = new ListView<>();
        versionListView.setPrefSize(200, 400);
        Label versionPlaceholder = new Label("No versions");
        versionPlaceholder.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        versionListView.setPlaceholder(versionPlaceholder);
        VersionManager versionManager = new VersionManager(versionListView);

        ListView<String> javaListView = new ListView<>();
        javaListView.setPrefSize(200, 400);
        Label javaPlaceholder = new Label("No Java versions found");
        javaPlaceholder.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        javaListView.setPlaceholder(javaPlaceholder);
        JavaManager javaManager = new JavaManager(javaListView);

        userManager.loadUsers();
        versionManager.loadVersions();
        javaManager.loadJavaVersions();

        userListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(userListView));
        versionListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(versionListView));
        javaListView.setCellFactory(lv -> new AlwaysSelectedListCell<>(javaListView));

        if (!userListView.getItems().isEmpty()) {
            userListView.getSelectionModel().select(0);
        }
        if (!versionListView.getItems().isEmpty()) {
            versionListView.getSelectionModel().select(0);
        }
        if (!javaListView.getItems().isEmpty()) {
            javaListView.getSelectionModel().select(0);
        }

        Button addUser = new Button("+");
        addUser.setOnAction(e -> UserWindow.open(userManager));
        Button deleteUser = new Button("-");
        deleteUser.setOnAction(e -> userManager.deleteSelectedUser());
        Button editUser = new Button("✏");
        editUser.setOnAction(e -> {
            int index = userListView.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                UserWindow.open(userManager, index);
            }
        });
        VBox userBox = new VBox(5, new HBox(5, addUser, deleteUser, editUser), userListView);
        userBox.setPadding(new Insets(10));

        Button addVersion = new Button("+");
        addVersion.setOnAction(e -> VersionWindow.open(versionManager));
        Button deleteVersion = new Button("-");
        deleteVersion.setOnAction(e -> versionManager.deleteSelectedVersion());
        VBox versionBox = new VBox(5, new HBox(5, addVersion, deleteVersion), versionListView);
        versionBox.setPadding(new Insets(10));

        Button reloadJava = new Button("↻");
        reloadJava.setOnAction(e -> javaManager.loadJavaVersions());
        VBox javaBox = new VBox(5, reloadJava, javaListView);
        javaBox.setPadding(new Insets(10));

        Button playButton = new Button("Play");
        playButton.setStyle("-fx-font-size:16px; -fx-padding:10px;");
        playButton.setOnAction(e -> {
            int userIndex = userListView.getSelectionModel().getSelectedIndex();
            int versionIndex = versionListView.getSelectionModel().getSelectedIndex();
            int javaIndex = javaListView.getSelectionModel().getSelectedIndex();

            if (userIndex < 0 || versionIndex < 0 || javaIndex < 0) {
                new Alert(
                        Alert.AlertType.WARNING,
                        "Please select a user, a version, and a Java version.",
                        ButtonType.OK
                ).showAndWait();
                return;
            }

            UserEntry userEntry = userManager.getUsers().get(userIndex);
            String username = userEntry.minecraftAccount ? userEntry.login : userEntry.username;
            String userUuid = userEntry.uuid;
            String versionName = versionListView.getItems().get(versionIndex);
            String javaPath = javaManager.getSelectedJavaPath(javaIndex);

            try {
                MinecraftLauncher.launch(username, userUuid, versionName, javaPath);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox playBox = new VBox(playButton);
        playBox.setPadding(new Insets(10));
        playBox.setStyle("-fx-alignment: center;");

        HBox mainBox = new HBox(50, userBox, versionBox, javaBox, playBox);
        mainBox.setPadding(new Insets(10));

        VBox root = new VBox(mainBox);
        root.setPadding(new Insets(10));

        renderWindow.getContentPane().getChildren().add(root);
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
