package com.IPOleksenko.ui.dialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.auth.AccountManager;
import com.IPOleksenko.auth.MicrosoftAuthService;

public class MicrosoftLoginDialog {

    public static void show(Stage parentStage, Runnable onLoginSuccess) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Microsoft Account Sign-In");
        stage.setResizable(false);
        com.IPOleksenko.ui.UIUtils.applyWindowIcon(stage);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(480);
        root.setStyle("-fx-background-color: #161922;");

        Label titleLabel = new Label("Sign in with Microsoft");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label descLabel = new Label("Authenticate securely via Microsoft OAuth Device Code Flow.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #94a3b8; -fx-text-alignment: center; -fx-font-size: 13px;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);

        VBox codeBox = new VBox(12);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setVisible(false);
        codeBox.setManaged(false);
        codeBox.setPadding(new Insets(10));
        codeBox.setStyle("-fx-background-color: #1e222b; -fx-background-radius: 8px; -fx-border-color: #2e3646; -fx-border-radius: 8px;");

        Label step1 = new Label("1. Open the Microsoft verification page:");
        step1.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Button openBrowserBtn = new Button("Open Verification Page");
        openBrowserBtn.getStyleClass().add("btn-secondary");

        Label step2 = new Label("2. Enter the authorization code:");
        step2.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label codeLabel = new Label("----");
        codeLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #38bdf8; -fx-font-family: Consolas, monospace; -fx-padding: 4 12; -fx-background-color: #0f1117; -fx-background-radius: 6px;");

        Button copyCodeBtn = new Button("Copy Code to Clipboard");
        copyCodeBtn.getStyleClass().add("btn-primary");

        codeBox.getChildren().addAll(step1, openBrowserBtn, step2, codeLabel, copyCodeBtn);

        Label statusLabel = new Label("Requesting authentication code from Microsoft...");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");

        root.getChildren().addAll(titleLabel, descLabel, spinner, codeBox, statusLabel, cancelBtn);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(MicrosoftLoginDialog.class.getResource("/assets/style.css").toExternalForm());
        stage.setScene(scene);

        AtomicBoolean cancelFlag = new AtomicBoolean(false);

        cancelBtn.setOnAction(e -> {
            cancelFlag.set(true);
            stage.close();
        });

        stage.setOnCloseRequest(e -> cancelFlag.set(true));

        // Start OAuth thread
        new Thread(() -> {
            try {
                MicrosoftAuthService.DeviceCodeResponse dc = MicrosoftAuthService.requestDeviceCode();

                Platform.runLater(() -> {
                    codeLabel.setText(dc.userCode);
                    openBrowserBtn.setText("Open " + dc.verificationUri);
                    codeBox.setVisible(true);
                    codeBox.setManaged(true);
                    statusLabel.setText("Waiting for confirmation in your browser...");

                    openBrowserBtn.setOnAction(e -> {
                        try {
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                Desktop.getDesktop().browse(new URI(dc.verificationUri));
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });

                    copyCodeBtn.setOnAction(e -> {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(dc.userCode);
                        clipboard.setContent(content);
                        copyCodeBtn.setText("Code Copied!");
                    });
                });

                Account account = MicrosoftAuthService.pollAndAuthenticate(dc, cancelFlag, status -> {
                    Platform.runLater(() -> statusLabel.setText(status));
                });

                Platform.runLater(() -> {
                    AccountManager.getInstance().addAccount(account);
                    if (onLoginSuccess != null) onLoginSuccess.run();
                    stage.close();

                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION,
                            "Successfully signed in as " + account.getUsername() + "!\nUUID: " + account.getUuid(),
                            ButtonType.OK);
                    successAlert.initOwner(parentStage);
                    successAlert.setTitle("Account Connected");
                    successAlert.setHeaderText("Microsoft Official License Connected");
                    successAlert.show();
                });

            } catch (Exception ex) {
                if (!cancelFlag.get()) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        spinner.setVisible(false);
                        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        statusLabel.setText("Authentication failed: " + ex.getMessage());

                        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                        alert.initOwner(stage);
                        alert.setTitle("Sign-In Error");
                        alert.setHeaderText("Microsoft Sign-In Failed");
                        alert.show();
                    });
                }
            }
        }, "Microsoft-Auth-Thread").start();

        stage.showAndWait();
    }
}
