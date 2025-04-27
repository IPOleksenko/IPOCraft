package dev.IPOleksenko;

import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.data.VersionManager;
import dev.IPOleksenko.window.RenderWindow;
import dev.IPOleksenko.window.UserWindow;
import dev.IPOleksenko.window.VersionWindow;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        RenderWindow renderWindow = new RenderWindow();
        renderWindow.show(primaryStage);

        ListView<String> userListView = new ListView<>();
        userListView.setPrefWidth(200);
        userListView.setPrefHeight(400);

        UserManager userManager = new UserManager(userListView);

        Button addUserButton = new Button("+");
        addUserButton.setOnAction(e -> UserWindow.open(userManager));

        Button deleteUserButton = new Button("-");
        deleteUserButton.setOnAction(e -> userManager.deleteSelectedUser());

        Button editUserButton = new Button("✏");
        editUserButton.setOnAction(e -> {
            int selectedIndex = userListView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                UserWindow.open(userManager, selectedIndex);
            }
        });

        VBox userBox = new VBox(10, new HBox(5, addUserButton, deleteUserButton, editUserButton), userListView);
        userBox.setPadding(new Insets(10));

        // Список версий + кнопки
        ListView<String> versionListView = new ListView<>();
        versionListView.setPrefWidth(200);
        versionListView.setPrefHeight(400);

        VersionManager versionManager = new VersionManager(versionListView);

        Button addVersionButton = new Button("+");
        addVersionButton.setOnAction(e -> VersionWindow.open(versionManager));

        Button deleteVersionButton = new Button("-");
        deleteVersionButton.setOnAction(e -> versionManager.deleteSelectedVersion());

        VBox versionBox = new VBox(10, new HBox(5, addVersionButton, deleteVersionButton), versionListView);
        versionBox.setPadding(new Insets(10));

        HBox mainBox = new HBox(50, userBox, versionBox);
        mainBox.setPadding(new Insets(10));

        renderWindow.getRoot().getChildren().add(mainBox);

        renderWindow.setMaximized(true);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
