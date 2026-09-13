package com.IPOleksenko.ui.dialogs;

import com.IPOleksenko.instance.Instance;
import com.IPOleksenko.instance.InstanceManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RenameInstanceDialog {

    public static void show(Stage parentStage, Instance instance, Runnable onSuccess) {
        if (instance == null) return;

        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Rename Instance");
        stage.setResizable(false);
        com.IPOleksenko.ui.UIUtils.applyWindowIcon(stage);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(400);
        root.setStyle("-fx-background-color: #161922;");

        Label title = new Label("Rename Instance");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TextField nameField = new TextField(instance.getName());
        nameField.setPromptText("Enter new instance name");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("btn-primary");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");

        HBox buttons = new HBox(10, cancelBtn, saveBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, nameField, errorLabel, buttons);

        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                errorLabel.setText("Instance name cannot be empty.");
                return;
            }

            InstanceManager.getInstance().renameInstance(instance, newName);
            if (onSuccess != null) onSuccess.run();
            stage.close();
        });

        Scene scene = new Scene(root);
        scene.getStylesheets().add(RenameInstanceDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}

