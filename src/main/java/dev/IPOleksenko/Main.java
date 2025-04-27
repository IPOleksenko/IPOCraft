package dev.IPOleksenko;

import dev.IPOleksenko.window.UserWindow;
import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.window.RenderWindow;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        RenderWindow renderWindow = new RenderWindow();
        renderWindow.show(primaryStage);

        ListView<String> listView = new ListView<>();
        listView.setLayoutX(10);
        listView.setLayoutY(50);
        listView.setPrefWidth(200);
        listView.setPrefHeight(400);

        UserManager userManager = new UserManager(listView);

        Button addButton = new Button("+");
        addButton.setLayoutX(10);
        addButton.setLayoutY(10);
        addButton.setOnAction(e -> UserWindow.open(userManager));

        Button deleteButton = new Button("-");
        deleteButton.setLayoutX(50);
        deleteButton.setLayoutY(10);
        deleteButton.setOnAction(e -> userManager.deleteSelectedUser());

        Button editButton = new Button("✏");
        editButton.setLayoutX(90);
        editButton.setLayoutY(10);
        editButton.setOnAction(e -> {
            int selectedIndex = listView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                UserWindow.open(userManager, selectedIndex);
            }
        });

        renderWindow.getRoot().getChildren().addAll(addButton, deleteButton, editButton, listView);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
