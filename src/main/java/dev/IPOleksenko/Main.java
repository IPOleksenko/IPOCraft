package dev.IPOleksenko;

import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.data.UserManager.UserEntry;
import dev.IPOleksenko.data.VersionManager;
import dev.IPOleksenko.launcher.MinecraftLauncher;
import dev.IPOleksenko.window.RenderWindow;
import dev.IPOleksenko.window.UserWindow;
import dev.IPOleksenko.window.VersionWindow;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
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
        UserManager userManager = new UserManager(userListView);

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

        ListView<String> versionListView = new ListView<>();
        versionListView.setPrefSize(200, 400);
        VersionManager versionManager = new VersionManager(versionListView);

        Button addVersion = new Button("+");
        addVersion.setOnAction(e -> VersionWindow.open(versionManager));
        Button deleteVersion = new Button("-");
        deleteVersion.setOnAction(e -> versionManager.deleteSelectedVersion());
        VBox versionBox = new VBox(5, new HBox(5, addVersion, deleteVersion), versionListView);
        versionBox.setPadding(new Insets(10));

        Button playButton = new Button("Play");
        playButton.setStyle("-fx-font-size:16px; -fx-padding:10px;");
        playButton.setOnAction(e -> {
            int userIndex = userListView.getSelectionModel().getSelectedIndex();
            int versionIndex = versionListView.getSelectionModel().getSelectedIndex();
            if (userIndex < 0 || versionIndex < 0) {
                System.out.println("Please select a user and a version.");
                return;
            }

            UserEntry userEntry = userManager.getUsers().get(userIndex);
            String username = userEntry.minecraftAccount ? userEntry.login : userEntry.username;
            String userUuid = userEntry.uuid;
            String versionName = versionListView.getItems().get(versionIndex);

            try {
                MinecraftLauncher.launch(username, userUuid, versionName);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        HBox mainBox = new HBox(50, userBox, versionBox);
        mainBox.setPadding(new Insets(10));
        VBox root = new VBox(20, mainBox, playButton);
        root.setPadding(new Insets(10));

        renderWindow.getRoot().getChildren().add(root);
        renderWindow.setMaximized(true);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
