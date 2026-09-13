package com.IPOleksenko.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.UUID;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.auth.AccountManager;
import com.IPOleksenko.ui.UIUtils;

public class UserEditDialog {

    public static void show(Stage parentStage, Account accountToEdit, Runnable onSuccess) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        boolean isEdit = accountToEdit != null;
        stage.setTitle(isEdit ? "Edit Player Profile" : "Create Player Profile");
        stage.setResizable(false);
        UIUtils.applyWindowIcon(stage);

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

        // 2. Custom Avatar / Icon
        Label avatarLabel = new Label("Profile Icon / Avatar (Кастомная иконка игрока):");
        avatarLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        final String[] customIconHolder = new String[]{isEdit ? accountToEdit.getCustomIconPath() : null};
        ImageView avatarPreview = new ImageView();
        avatarPreview.setFitWidth(40);
        avatarPreview.setFitHeight(40);
        avatarPreview.setPreserveRatio(false);
        Circle avatarClip = new Circle(20, 20, 20);
        avatarPreview.setClip(avatarClip);

        Image currentAvatar = isEdit ? UIUtils.loadImage(accountToEdit.getCustomIconPath()) : null;
        if (currentAvatar == null || currentAvatar.isError()) {
            currentAvatar = UIUtils.loadImage("/assets/icon.png");
        }
        if (currentAvatar != null) avatarPreview.setImage(currentAvatar);

        Button chooseAvatarBtn = new Button("Browse Custom Avatar...");
        chooseAvatarBtn.getStyleClass().add("btn-secondary");

        Button clearAvatarBtn = new Button("Clear Avatar");
        clearAvatarBtn.getStyleClass().add("btn-secondary");

        chooseAvatarBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Player Profile Avatar / Icon");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg")
            );
            File file = fc.showOpenDialog(stage);
            if (file != null && file.exists()) {
                customIconHolder[0] = file.getAbsolutePath();
                Image img = new Image(file.toURI().toString());
                if (!img.isError()) {
                    avatarPreview.setImage(img);
                }
            }
        });

        clearAvatarBtn.setOnAction(e -> {
            customIconHolder[0] = null;
            Image defImg = UIUtils.loadImage("/assets/icon.png");
            if (defImg != null) avatarPreview.setImage(defImg);
        });

        HBox avatarBox = new HBox(10, avatarPreview, chooseAvatarBtn, clearAvatarBtn);
        avatarBox.setAlignment(Pos.CENTER_LEFT);

        // 3. UUID
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

        // 4. Access Token
        Label tokenLabel = new Label("Access Token (Optional):");
        tokenLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        TextField tokenField = new TextField(isEdit ? accountToEdit.getAccessToken() : "0");
        tokenField.setPromptText("0 or your custom token");

        // 5. Auth Session
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
                accountToEdit.setCustomIconPath(customIconHolder[0]);
                AccountManager.getInstance().saveAccounts();
            } else {
                Account newAcc = new Account(name, uuid, "OFFLINE", token, session, null, 0, null);
                newAcc.setCustomIconPath(customIconHolder[0]);
                AccountManager.getInstance().addAccount(newAcc);
            }

            if (onSuccess != null) onSuccess.run();
            stage.close();
        });

        root.getChildren().addAll(
                title,
                nameLabel, nameField,
                avatarLabel, avatarBox,
                uuidLabel, uuidButtons,
                tokenLabel, tokenField,
                sessionLabel, sessionField,
                errorLabel,
                buttons
        );

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #161922; -fx-background: #161922;");

        Scene scene = new Scene(scroll, 500, 640);
        scene.getStylesheets().add(UserEditDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
