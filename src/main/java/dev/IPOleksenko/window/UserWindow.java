package dev.IPOleksenko.window;

import dev.IPOleksenko.data.UserManager;
import dev.IPOleksenko.data.UserManager.UserEntry;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class UserWindow {

    public static void open(UserManager userManager) {
        open(userManager, null);
    }

    public static void open(UserManager userManager, Integer editIndex) {
        Stage newUserStage = new Stage();
        newUserStage.initModality(Modality.APPLICATION_MODAL);
        newUserStage.setTitle(editIndex == null ? "Create New User" : "Edit User");

        VBox vbox = new VBox(10);
        vbox.setStyle("-fx-padding: 10;");

        Label nameLabel = new Label("Username:");
        TextField nameField = new TextField();
        nameField.setPromptText("Enter username");

        Label loginLabel = new Label("Login:");
        TextField loginField = new TextField();
        loginField.setPromptText("Enter login");

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");

        loginLabel.setVisible(false);
        loginField.setVisible(false);
        passwordLabel.setVisible(false);
        passwordField.setVisible(false);

        CheckBox minecraftCheckBox = new CheckBox("Minecraft Account");

        Button createButton = new Button(editIndex == null ? "Create" : "Save");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        if (editIndex != null) {
            UserEntry user = userManager.getUsers().get(editIndex);
            minecraftCheckBox.setSelected(user.minecraftAccount);

            if (user.minecraftAccount) {
                loginField.setText(user.login);
                passwordField.setText(user.password);
                loginLabel.setVisible(true);
                loginField.setVisible(true);
                passwordLabel.setVisible(true);
                passwordField.setVisible(true);
                nameLabel.setVisible(false);
                nameField.setVisible(false);
            } else {
                nameField.setText(user.username);
                nameLabel.setVisible(true);
                nameField.setVisible(true);
                loginLabel.setVisible(false);
                loginField.setVisible(false);
                passwordLabel.setVisible(false);
                passwordField.setVisible(false);
            }
        }

        minecraftCheckBox.setOnAction(e -> {
            boolean selected = minecraftCheckBox.isSelected();
            nameLabel.setVisible(!selected);
            nameField.setVisible(!selected);
            loginLabel.setVisible(selected);
            loginField.setVisible(selected);
            passwordLabel.setVisible(selected);
            passwordField.setVisible(selected);
        });

        createButton.setOnAction(e -> {
            boolean isMinecraftAccount = minecraftCheckBox.isSelected();

            if (isMinecraftAccount) {
                String login = loginField.getText().trim();
                String password = passwordField.getText().trim();

                if (login.isEmpty() || password.isEmpty()) {
                    errorLabel.setText("Login and password cannot be empty.");
                    return;
                }

                boolean duplicate = userManager.getUsers().stream()
                        .anyMatch(u -> u.minecraftAccount
                                && u.login != null
                                && u.login.equals(login)
                                && (editIndex == null || userManager.getUsers().indexOf(u) != editIndex));

                if (duplicate) {
                    errorLabel.setText("A user with this login already exists.");
                    return;
                }

                if (editIndex == null) {
                    userManager.addUser(login, true, login, password);
                } else {
                    UserEntry user = userManager.getUsers().get(editIndex);
                    user.username = null;
                    user.login = login;
                    user.password = password;
                    user.minecraftAccount = true;
                    userManager.updateUser(editIndex, user);
                }
            } else {
                String username = nameField.getText().trim();

                if (username.isEmpty()) {
                    errorLabel.setText("Username cannot be empty.");
                    return;
                }

                boolean duplicate = userManager.getUsers().stream()
                        .anyMatch(u -> !u.minecraftAccount
                                && u.username != null
                                && u.username.equals(username)
                                && (editIndex == null || userManager.getUsers().indexOf(u) != editIndex));

                if (duplicate) {
                    errorLabel.setText("A user with this username already exists.");
                    return;
                }

                if (editIndex == null) {
                    userManager.addUser(username, false, null, null);
                } else {
                    UserEntry user = userManager.getUsers().get(editIndex);
                    user.username = username;
                    user.login = null;
                    user.password = null;
                    user.minecraftAccount = false;
                    userManager.updateUser(editIndex, user);
                }
            }

            newUserStage.close();
        });

        vbox.getChildren().addAll(
                minecraftCheckBox,
                nameLabel,
                nameField,
                loginLabel,
                loginField,
                passwordLabel,
                passwordField,
                createButton,
                errorLabel
        );

        Scene scene = new Scene(vbox, 300, 300);
        newUserStage.setScene(scene);
        newUserStage.showAndWait();
    }
}
