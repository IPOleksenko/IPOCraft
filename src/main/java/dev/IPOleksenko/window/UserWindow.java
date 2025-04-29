package dev.IPOleksenko.window;

import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.data.UserManager.UserEntry;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.UUID;

public class UserWindow {

    public static void open(UserManager userManager) {
        open(userManager, null);
    }

    public static void open(UserManager userManager, Integer editIndex) {
        Stage newUserStage = new Stage();
        newUserStage.initModality(Modality.APPLICATION_MODAL);
        newUserStage.setTitle(editIndex == null ? "Create New User" : "Edit User");

        newUserStage.setResizable(false);

        InputStream iconStream = UserWindow.class.getResourceAsStream("/assets/icon.png");
        Image icon = new Image(iconStream);
        newUserStage.getIcons().add(icon);

        VBox vbox = new VBox(10);
        vbox.setStyle("-fx-padding: 10;");

        // Main field for username
        Label nameLabel = new Label("Username:");
        TextField nameField = new TextField();
        nameField.setPromptText("Enter username");

        // Container for UUID, AccessToken, AuthSession
        VBox optionalFieldsBox = new VBox(5);
        Label uuidLabel = new Label("UUID (Optional):");
        TextField uuidField = new TextField();
        uuidField.setPromptText("Enter UUID if you want to specify (optional)");

        Label accessTokenLabel = new Label("Access Token (optional):");
        TextField accessTokenField = new TextField();
        accessTokenField.setPromptText("Enter access token if you want to specify (optional)");

        Label authSessionLabel = new Label("Auth Session (optional):");
        TextField authSessionField = new TextField();
        authSessionField.setPromptText("Enter auth session if you want to specify (optional)");

        optionalFieldsBox.getChildren().addAll(
                uuidLabel,
                uuidField,
                accessTokenLabel,
                accessTokenField,
                authSessionLabel,
                authSessionField
        );

        // TitledPane for hiding/showing optional fields
        TitledPane optionalFieldsPane = new TitledPane("Optional Fields", optionalFieldsBox);
        optionalFieldsPane.setCollapsible(true);
        optionalFieldsPane.setExpanded(false);

        // Save button
        Button createButton = new Button(editIndex == null ? "Create" : "Save");

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        // If editing a user, fill the fields
        if (editIndex != null) {
            UserEntry user = userManager.getUsers().get(editIndex);
            nameField.setText(user.username);
            uuidField.setText(user.uuid);
            accessTokenField.setText(user.accessToken);
            authSessionField.setText(user.authSession);
        }

        // Button click handler
        createButton.setOnAction(e -> {
            String username = nameField.getText().trim();
            String uuid = uuidField.getText().trim();
            String accessToken = accessTokenField.getText().trim();
            String authSession = authSessionField.getText().trim();

            if (username.isEmpty()) {
                errorLabel.setText("Username cannot be empty.");
                return;
            }

            boolean duplicate = userManager.getUsers().stream()
                    .anyMatch(u -> u.username != null
                            && u.username.equals(username)
                            && (editIndex == null || userManager.getUsers().indexOf(u) != editIndex));

            if (duplicate) {
                errorLabel.setText("A user with this username already exists.");
                return;
            }

            if (editIndex == null) {
                // If UUID is not entered, generate a new one
                if (uuid.isEmpty()) uuid = generateUuid();
                userManager.addUser(username, uuid, accessToken.isEmpty() ? "0" : accessToken, authSession.isEmpty() ? "0" : authSession);
            } else {
                UserEntry user = userManager.getUsers().get(editIndex);
                user.username = username;
                user.uuid = uuid.isEmpty() ? generateUuid() : uuid;
                user.accessToken = accessToken.isEmpty() ? "0" : accessToken;
                user.authSession = authSession.isEmpty() ? "0" : authSession;
                userManager.updateUser(editIndex, user);
            }

            newUserStage.close();
        });

        vbox.getChildren().addAll(
                nameLabel,
                nameField,
                optionalFieldsPane,
                createButton,
                errorLabel
        );

        Scene scene = new Scene(vbox, 400, 400);
        newUserStage.setScene(scene);
        newUserStage.showAndWait();
    }

    // Generate random UUID (if empty)
    private static String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
