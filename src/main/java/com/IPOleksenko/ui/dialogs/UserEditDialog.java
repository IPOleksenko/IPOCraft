package com.IPOleksenko.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.UUID;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.auth.AccountManager;

public class UserEditDialog {

    public static void show(Stage parentStage, Account accountToEdit, Runnable onSuccess) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        boolean isEdit = accountToEdit != null;
        stage.setTitle(isEdit ? "Edit Player Profile" : "Create Player Profile");
        stage.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(480);
        root.setStyle("-fx-background-color: #161922;");

        Label title = new Label(isEdit ? "Edit Player Profile" : "Add Player Profile");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");

        // 1. Username
        Label nameLabel = new Label("Player Username:");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField nameField = new TextField(isEdit ? accountToEdit.getUsername() : "");
        nameField.setPromptText("Enter username");

        // 2. UUID
        Label uuidLabel = new Label("Player UUID:");
        uuidLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField uuidField = new TextField(isEdit ? accountToEdit.getUuid() : UUID.randomUUID().toString());
        uuidField.setPromptText("XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX");

        Button btnRandomUuid = new Button("🎲 Random UUID");
        btnRandomUuid.getStyleClass().add("btn-secondary");
        btnRandomUuid.setOnAction(e -> uuidField.setText(UUID.randomUUID().toString()));

        Button btnOfflineUuid = new Button("⚙ Offline UUID");
        btnOfflineUuid.getStyleClass().add("btn-secondary");
        btnOfflineUuid.setOnAction(e -> {
            String nick = nameField.getText().trim();
            if (!nick.isEmpty()) {
                uuidField.setText(Account.generateOfflineUuid(nick));
            }
        });

        HBox uuidButtons = new HBox(8, uuidField, btnRandomUuid, btnOfflineUuid);
        HBox.setHgrow(uuidField, Priority.ALWAYS);

        // 3. Access Token
        Label tokenLabel = new Label("Access Token (Optional):");
        tokenLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField tokenField = new TextField(isEdit ? accountToEdit.getAccessToken() : "0");
        tokenField.setPromptText("0 or your custom token");

        // 4. Auth Session
        Label sessionLabel = new Label("Auth Session (Optional):");
        sessionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField sessionField = new TextField(isEdit ? accountToEdit.getAuthSession() : "0");
        sessionField.setPromptText("0 or session identifier");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");

        // Action buttons
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button(isEdit ? "Save Profile" : "Create Player");
        saveBtn.getStyleClass().add("btn-primary");

        HBox buttons = new HBox(12, cancelBtn, saveBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String uuid = uuidField.getText().trim();
            String token = tokenField.getText().trim();
            String session = sessionField.getText().trim();

            if (name.isEmpty()) {
                errorLabel.setText("Username cannot be empty.");
                return;
            }

            if (uuid.isEmpty()) {
                uuid = Account.generateOfflineUuid(name);
            }

            if (token.isEmpty()) token = "0";
            if (session.isEmpty()) session = "0";

            if (isEdit) {
                accountToEdit.setUsername(name);
                accountToEdit.setUuid(uuid);
                accountToEdit.setAccessToken(token);
                accountToEdit.setAuthSession(session);
                AccountManager.getInstance().saveAccounts();
            } else {
                Account newAcc = new Account(name, uuid, "OFFLINE", token, session, null, 0, null);
                AccountManager.getInstance().addAccount(newAcc);
            }

            if (onSuccess != null) onSuccess.run();
            stage.close();
        });

        root.getChildren().addAll(
                title,
                nameLabel, nameField,
                uuidLabel, uuidButtons,
                tokenLabel, tokenField,
                sessionLabel, sessionField,
                errorLabel,
                buttons
        );

        Scene scene = new Scene(root);
        scene.getStylesheets().add(UserEditDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}

