package com.IPOleksenko.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.IPOleksenko.auth.Account;
import com.IPOleksenko.auth.AccountManager;
import com.IPOleksenko.auth.MicrosoftAuthService;
import com.IPOleksenko.config.ConfigManager;
import com.IPOleksenko.config.LauncherConfig;
import com.IPOleksenko.instance.Instance;
import com.IPOleksenko.instance.InstanceManager;
import com.IPOleksenko.launcher.JavaDetector;
import com.IPOleksenko.launcher.MinecraftLauncher;
import com.IPOleksenko.ui.dialogs.CreateInstanceDialog;
import com.IPOleksenko.ui.dialogs.EditInstanceDialog;
import com.IPOleksenko.ui.dialogs.MicrosoftLoginDialog;
import com.IPOleksenko.ui.dialogs.RenameInstanceDialog;
import com.IPOleksenko.ui.dialogs.UserEditDialog;

public class MainWindow {

    private final Stage stage;
    private final ConfigManager configManager = ConfigManager.getInstance();
    private final InstanceManager instanceManager = InstanceManager.getInstance();
    private final AccountManager accountManager = AccountManager.getInstance();

    private StackPane rootStack;
    private BorderPane mainLayout;

    // Content Pages
    private StackPane contentArea;
    private VBox instancesPage;
    private VBox accountsPage;
    private VBox pathsPage;
    private VBox settingsPage;
    private VBox consolePage;

    // Hero Play Bar components
    private Label heroInstanceName;
    private Label heroInstanceVersion;
    private ComboBox<String> heroJavaSelector;
    private Label heroStatusLabel;
    private ProgressBar heroProgressBar;
    private Button heroPlayButton;

    // User Badge in sidebar
    private Label sidebarUsername;
    private Label sidebarAccountType;
    private ImageView sidebarAvatar;

    // Navigation buttons
    private Button btnNavInstances;
    private Button btnNavAccounts;
    private Button btnNavPaths;
    private Button btnNavSettings;
    private Button btnNavConsole;

    // In-App Terminal & logs
    private TextArea consoleTextArea;
    private final StringBuilder logBuffer = new StringBuilder();
    private Label terminalStatusBadge;
    private TextField terminalInputField;

    public MainWindow(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("IPOCraft");
        stage.setMinWidth(1000);
        stage.setMinHeight(660);
        stage.setWidth(1120);
        stage.setHeight(740);

        InputStream iconStream = getClass().getResourceAsStream("/assets/icon.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        buildUI();
        setupSystemStreamsRedirection();

        Scene scene = new Scene(rootStack, 1120, 740);
        scene.getStylesheets().add(getClass().getResource("/assets/style.css").toExternalForm());

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            JavaDetector.stopMonitoring();
            System.exit(0);
        });

        JavaDetector.startMonitoring();
        JavaDetector.addListener(this::onJavaInstallationsChanged);
        stage.focusedProperty().addListener((obs, oldV, focused) -> {
            if (focused) {
                JavaDetector.refreshNowAsync();
            }
        });

        stage.show();

        refreshInstancesList();
        refreshHeroBar();
        refreshUserBadge();
    }

    private void buildUI() {
        rootStack = new StackPane();
        rootStack.setStyle("-fx-background-color: transparent;");

        // 1. Background layer using /assets/background.png (Minecraft dirt texture)
        Region bgLayer = new Region();
        bgLayer.getStyleClass().add("launcher-background");
        bgLayer.setMouseTransparent(true);
        try {
            InputStream bgStream = getClass().getResourceAsStream("/assets/background.png");
            if (bgStream != null) {
                Image bgImg = new Image(bgStream, 64, 64, false, true);
                bgLayer.setBackground(new Background(new BackgroundImage(
                        bgImg,
                        BackgroundRepeat.REPEAT,
                        BackgroundRepeat.REPEAT,
                        BackgroundPosition.DEFAULT,
                        new BackgroundSize(64, 64, false, false, false, false)
                )));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Dark tint overlay to keep text legible while showing the dirt texture
        Region overlay = new Region();
        overlay.getStyleClass().add("launcher-overlay");
        overlay.setMouseTransparent(true);

        mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: transparent;");

        // Sidebar
        VBox sidebar = buildSidebar();
        mainLayout.setLeft(sidebar);

        // Center Content Area
        contentArea = new StackPane();
        contentArea.setPadding(new Insets(16));

        instancesPage = buildInstancesPage();
        accountsPage = buildAccountsPage();
        pathsPage = buildPathsPage();
        settingsPage = buildSettingsPage();
        consolePage = buildConsolePage();

        contentArea.getChildren().addAll(
                instancesPage,
                accountsPage,
                pathsPage,
                settingsPage,
                consolePage
        );
        showPage(instancesPage, btnNavInstances);

        mainLayout.setCenter(contentArea);

        // Bottom Hero Play Bar
        HBox heroBar = buildHeroPlayBar();
        mainLayout.setBottom(heroBar);

        rootStack.getChildren().addAll(bgLayer, overlay, mainLayout);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setPadding(new Insets(14));

        // Logo
        ImageView logoView = new ImageView();
        logoView.setPreserveRatio(true);
        logoView.setFitWidth(180);
        InputStream logoStream = getClass().getResourceAsStream("/assets/logo.png");
        if (logoStream != null) {
            logoView.setImage(new Image(logoStream));
        }

        VBox brandBox = new VBox(6, logoView);
        brandBox.setAlignment(Pos.CENTER);
        brandBox.setPadding(new Insets(0, 0, 10, 0));

        // Navigation Items (Clean English labels, no fragile emojis)
        btnNavInstances = createNavButton("Instances", e -> showPage(instancesPage, btnNavInstances));
        btnNavAccounts = createNavButton("Player Profiles", e -> {
            refreshAccountsPage();
            showPage(accountsPage, btnNavAccounts);
        });
        btnNavPaths = createNavButton("Paths & Launch", e -> showPage(pathsPage, btnNavPaths));
        btnNavSettings = createNavButton("Game Settings", e -> showPage(settingsPage, btnNavSettings));
        btnNavConsole = createNavButton("Terminal & Logs", e -> showPage(consolePage, btnNavConsole));

        VBox navBox = new VBox(4,
                btnNavInstances,
                btnNavAccounts,
                btnNavPaths,
                btnNavSettings,
                btnNavConsole
        );
        VBox.setVgrow(navBox, Priority.ALWAYS);

        HBox userWidget = buildUserWidget();

        sidebar.getChildren().addAll(brandBox, navBox, userWidget);
        return sidebar;
    }

    private Button createNavButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(handler);
        return btn;
    }

    private void showPage(VBox page, Button navBtn) {
        instancesPage.setVisible(false);
        instancesPage.setManaged(false);
        accountsPage.setVisible(false);
        accountsPage.setManaged(false);
        pathsPage.setVisible(false);
        pathsPage.setManaged(false);
        settingsPage.setVisible(false);
        settingsPage.setManaged(false);
        consolePage.setVisible(false);
        consolePage.setManaged(false);

        page.setVisible(true);
        page.setManaged(true);

        btnNavInstances.getStyleClass().remove("active");
        btnNavAccounts.getStyleClass().remove("active");
        btnNavPaths.getStyleClass().remove("active");
        btnNavSettings.getStyleClass().remove("active");
        btnNavConsole.getStyleClass().remove("active");

        if (navBtn != null) {
            navBtn.getStyleClass().add("active");
        }
    }

    private HBox buildUserWidget() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #1e222b; -fx-background-radius: 8px; -fx-border-color: #2e3646; -fx-border-radius: 8px; -fx-cursor: hand;");

        sidebarAvatar = new ImageView();
        sidebarAvatar.setFitWidth(32);
        sidebarAvatar.setFitHeight(32);
        InputStream iconStream = getClass().getResourceAsStream("/assets/icon.png");
        if (iconStream != null) sidebarAvatar.setImage(new Image(iconStream));

        Circle clip = new Circle(16, 16, 16);
        sidebarAvatar.setClip(clip);

        sidebarUsername = new Label("No Account");
        sidebarUsername.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        sidebarAccountType = new Label("Click to add");
        sidebarAccountType.getStyleClass().add("badge-offline");

        VBox textBox = new VBox(2, sidebarUsername, sidebarAccountType);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        box.getChildren().addAll(sidebarAvatar, textBox);
        box.setOnMouseClicked(e -> {
            refreshAccountsPage();
            showPage(accountsPage, btnNavAccounts);
        });

        return box;
    }

    private void refreshUserBadge() {
        Account active = accountManager.getActiveAccount();
        if (active != null) {
            sidebarUsername.setText(active.getUsername());
            if (active.isMicrosoft()) {
                sidebarAccountType.setText("Official License");
                sidebarAccountType.getStyleClass().setAll("badge-license");
            } else {
                sidebarAccountType.setText("Offline");
                sidebarAccountType.getStyleClass().setAll("badge-offline");
            }
        } else {
            sidebarUsername.setText("No Account");
            sidebarAccountType.setText("Click to add");
            sidebarAccountType.getStyleClass().setAll("badge-offline");
        }
    }

    // ==========================================
    // Page 1: Instances
    // ==========================================
    private VBox instancesContainer;

    private VBox buildInstancesPage() {
        VBox page = new VBox(14);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Installed Instances");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newInstBtn = new Button("+ New Instance");
        newInstBtn.getStyleClass().add("btn-primary");
        newInstBtn.setOnAction(e -> CreateInstanceDialog.show(stage, this::onInstanceCreated));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> {
            instanceManager.loadInstances();
            refreshInstancesList();
            refreshHeroBar();
        });

        header.getChildren().addAll(title, spacer, newInstBtn, refreshBtn);

        instancesContainer = new VBox(10);
        ScrollPane scroll = new ScrollPane(instancesContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        page.getChildren().addAll(header, scroll);
        return page;
    }

    private void refreshInstancesList() {
        instancesContainer.getChildren().clear();
        List<Instance> instances = instanceManager.getInstances();
        Instance active = instanceManager.getActiveInstance();

        if (instances.isEmpty()) {
            Label empty = new Label("No instances created yet. Click '+ New Instance' above to get started.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-padding: 24;");
            instancesContainer.getChildren().add(empty);
            return;
        }

        for (Instance inst : instances) {
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("instance-card");
            if (active != null && inst.getId().equals(active.getId())) {
                card.getStyleClass().add("selected");
            }

            Label iconLbl = new Label("MC");
            iconLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #3b82f6; -fx-background-color: #14171f; -fx-padding: 6 10; -fx-background-radius: 6px;");

            VBox info = new VBox(4);
            Label nameLbl = new Label(inst.getName());
            nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

            String javaInfo = inst.getCustomJavaPath() != null && !inst.getCustomJavaPath().isEmpty()
                    ? " | Java: Custom" : "";
            Label verLbl = new Label("Version: " + inst.getMinecraftVersion() + javaInfo);
            verLbl.setStyle("-fx-text-fill: #94a3b8;");

            info.getChildren().addAll(nameLbl, verLbl);
            HBox.setHgrow(info, Priority.ALWAYS);

            boolean isSelected = active != null && inst.getId().equals(active.getId());
            Button selectBtn = new Button(isSelected ? "Active" : "Select");
            selectBtn.getStyleClass().add("btn-secondary");
            if (isSelected) {
                selectBtn.setDisable(true);
            } else {
                selectBtn.setOnAction(e -> {
                    instanceManager.setActiveInstance(inst);
                    refreshInstancesList();
                    refreshHeroBar();
                });
            }

            Button editBtn = new Button("Edit / Version");
            editBtn.getStyleClass().add("btn-primary");
            editBtn.setOnAction(e -> EditInstanceDialog.show(stage, inst, () -> {
                refreshInstancesList();
                refreshHeroBar();
            }));

            Button renameBtn = new Button("Rename");
            renameBtn.getStyleClass().add("btn-secondary");
            renameBtn.setOnAction(e -> RenameInstanceDialog.show(stage, inst, () -> {
                refreshInstancesList();
                refreshHeroBar();
            }));

            Button cloneBtn = new Button("Clone");
            cloneBtn.getStyleClass().add("btn-secondary");
            cloneBtn.setOnAction(e -> {
                instanceManager.cloneInstance(inst, inst.getName() + " (Copy)");
                refreshInstancesList();
            });

            Button folderBtn = new Button("Folder");
            folderBtn.getStyleClass().add("btn-secondary");
            folderBtn.setOnAction(e -> openFolder(inst.getGameDir()));

            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().add("btn-danger");
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete instance \"" + inst.getName() + "\" and all its data?", ButtonType.YES, ButtonType.NO);
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.YES) {
                        instanceManager.deleteInstance(inst);
                        refreshInstancesList();
                        refreshHeroBar();
                    }
                });
            });

            card.getChildren().addAll(iconLbl, info, selectBtn, editBtn, renameBtn, cloneBtn, folderBtn, deleteBtn);
            instancesContainer.getChildren().add(card);
        }
    }

    private void onInstanceCreated() {
        refreshInstancesList();
        refreshHeroBar();
        showPage(instancesPage, btnNavInstances);
    }

    // ==========================================
    // Page 2: Accounts & Profiles
    // ==========================================
    private VBox accountsContainer;

    private VBox buildAccountsPage() {
        VBox page = new VBox(14);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Player Profiles & Accounts");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshAccountsBtn = new Button("Refresh");
        refreshAccountsBtn.getStyleClass().add("btn-secondary");
        refreshAccountsBtn.setOnAction(e -> {
            refreshAccountsBtn.setDisable(true);
            refreshAccountsBtn.setText("Refreshing...");
            new Thread(() -> {
                try {
                    accountManager.refreshAllAccounts();
                } finally {
                    Platform.runLater(() -> {
                        refreshAccountsBtn.setDisable(false);
                        refreshAccountsBtn.setText("Refresh");
                        refreshAccountsPage();
                        refreshUserBadge();
                        refreshHeroBar();
                    });
                }
            }, "Accounts-Refresher").start();
        });

        Button addMsBtn = new Button("+ Microsoft Account");
        addMsBtn.getStyleClass().add("btn-primary");
        addMsBtn.setOnAction(e -> MicrosoftLoginDialog.show(stage, () -> {
            refreshAccountsPage();
            refreshUserBadge();
        }));

        Button addCustomPlayerBtn = new Button("+ Add Offline Player");
        addCustomPlayerBtn.getStyleClass().add("btn-secondary");
        addCustomPlayerBtn.setOnAction(e -> UserEditDialog.show(stage, null, () -> {
            refreshAccountsPage();
            refreshUserBadge();
        }));

        header.getChildren().addAll(title, spacer, refreshAccountsBtn, addMsBtn, addCustomPlayerBtn);

        accountsContainer = new VBox(10);
        ScrollPane scroll = new ScrollPane(accountsContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        page.getChildren().addAll(header, scroll);
        return page;
    }

    private void refreshAccountsPage() {
        accountsContainer.getChildren().clear();
        List<Account> accounts = accountManager.getAccounts();
        Account active = accountManager.getActiveAccount();

        if (accounts.isEmpty()) {
            Label empty = new Label("No player profiles created yet. Click '+ Microsoft Account' or '+ Add Offline Player' above to get started.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-padding: 24;");
            accountsContainer.getChildren().add(empty);
            return;
        }

        for (Account acc : accounts) {
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("card");
            if (active != null && acc.getUuid().equalsIgnoreCase(active.getUuid())) {
                card.setStyle("-fx-border-color: #2563eb; -fx-border-width: 2px; -fx-background-color: #222a38;");
            }

            Label avatar = new Label(acc.isMicrosoft() ? "MS" : "OFF");
            avatar.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                    + (acc.isMicrosoft() ? "#4ade80" : "#94a3b8")
                    + "; -fx-background-color: #14171f; -fx-padding: 6 10; -fx-background-radius: 6px;");

            VBox info = new VBox(3);
            Label name = new Label(acc.getUsername());
            name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

            Label uuidLbl = new Label("UUID: " + acc.getUuid());
            uuidLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-family: monospace; -fx-font-size: 11px;");

            Label type = new Label(acc.isMicrosoft() ? "Microsoft Official License" : "Offline Account");
            type.getStyleClass().add(acc.isMicrosoft() ? "badge-license" : "badge-offline");

            info.getChildren().addAll(name, uuidLbl, type);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().add("btn-secondary");
            editBtn.setOnAction(e -> UserEditDialog.show(stage, acc, () -> {
                refreshAccountsPage();
                refreshUserBadge();
            }));

            boolean isSelected = active != null && acc.getUuid().equalsIgnoreCase(active.getUuid());
            Button selectBtn = new Button(isSelected ? "Active" : "Select");
            selectBtn.getStyleClass().add("btn-secondary");
            if (isSelected) {
                selectBtn.setDisable(true);
            } else {
                selectBtn.setOnAction(e -> {
                    accountManager.setActiveAccount(acc);
                    refreshAccountsPage();
                    refreshUserBadge();
                });
            }

            Button delBtn = new Button("Delete");
            delBtn.getStyleClass().add("btn-danger");
            delBtn.setOnAction(e -> {
                accountManager.removeAccount(acc);
                refreshAccountsPage();
                refreshUserBadge();
            });

            if (acc.isMicrosoft()) {
                Button refreshCardBtn = new Button("Refresh");
                refreshCardBtn.getStyleClass().add("btn-secondary");
                refreshCardBtn.setOnAction(e -> {
                    refreshCardBtn.setDisable(true);
                    refreshCardBtn.setText("...");
                    new Thread(() -> {
                        try {
                            Account refreshed = MicrosoftAuthService.refreshAccount(acc);
                            accountManager.addAccount(refreshed);
                        } catch (Exception ex) {
                            System.err.println("Failed to refresh token: " + ex.getMessage());
                        } finally {
                            Platform.runLater(() -> {
                                refreshAccountsPage();
                                refreshUserBadge();
                                refreshHeroBar();
                            });
                        }
                    }, "Refresh-Single-Account").start();
                });
                card.getChildren().addAll(avatar, info, refreshCardBtn, editBtn, selectBtn, delBtn);
            } else {
                card.getChildren().addAll(avatar, info, editBtn, selectBtn, delBtn);
            }
            accountsContainer.getChildren().add(card);
        }
    }

    // ==========================================
    // Page 3: Paths & Launch System Customization
    // ==========================================
    private TextField instancesDirField;
    private TextField versionsDirField;
    private TextField assetsDirField;
    private TextField librariesDirField;
    private TextField launchWrapperField;
    private TextArea customEnvVarsArea;
    private ComboBox<String> priorityCombo;

    private VBox buildPathsPage() {
        VBox page = new VBox(14);

        Label title = new Label("Paths & Launch System Customization");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox form = new VBox(16);
        form.setPadding(new Insets(8));

        LauncherConfig config = configManager.getConfig();

        // 1. Directories
        VBox dirCard = new VBox(10);
        dirCard.getStyleClass().add("card");
        Label dirTitle = new Label("Storage Directories");
        dirTitle.getStyleClass().add("card-title");

        instancesDirField = new TextField(config.getInstancesDir());
        versionsDirField = new TextField(config.getVersionsDir());
        assetsDirField = new TextField(config.getAssetsDir());
        librariesDirField = new TextField(config.getLibrariesDir());

        dirCard.getChildren().addAll(
                dirTitle,
                createDirRow("Instances Directory:", instancesDirField),
                createDirRow("Versions Directory:", versionsDirField),
                createDirRow("Assets Directory:", assetsDirField),
                createDirRow("Libraries Directory:", librariesDirField)
        );

        // 2. Launch System
        VBox launchCard = new VBox(10);
        launchCard.getStyleClass().add("card");
        Label launchTitle = new Label("Process Configuration");
        launchTitle.getStyleClass().add("card-title");

        Label wrapperLbl = new Label("Launch Wrapper Command:");
        wrapperLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        launchWrapperField = new TextField(config.getLaunchWrapper());
        launchWrapperField.setPromptText("e.g. gamemoderun or custom command");

        Label envLbl = new Label("Custom Environment Variables (KEY=VALUE):");
        envLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        customEnvVarsArea = new TextArea(config.getCustomEnvVars());
        customEnvVarsArea.setPrefRowCount(3);
        customEnvVarsArea.setPromptText("MESA_GL_VERSION_OVERRIDE=4.5\n__GL_THREADED_OPTIMIZATIONS=1");

        Label priorityLbl = new Label("Game Process Priority:");
        priorityLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        priorityCombo = new ComboBox<>();
        priorityCombo.getItems().addAll("Normal", "High", "Realtime");
        priorityCombo.setValue("HIGH".equalsIgnoreCase(config.getProcessPriority()) ? "High" : "Normal");

        launchCard.getChildren().addAll(launchTitle, wrapperLbl, launchWrapperField, envLbl, customEnvVarsArea, priorityLbl, priorityCombo);

        Button saveBtn = new Button("Save Paths & Launch Options");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            config.setInstancesDir(instancesDirField.getText().trim());
            config.setVersionsDir(versionsDirField.getText().trim());
            config.setAssetsDir(assetsDirField.getText().trim());
            config.setLibrariesDir(librariesDirField.getText().trim());
            config.setLaunchWrapper(launchWrapperField.getText().trim());
            config.setCustomEnvVars(customEnvVarsArea.getText().trim());

            String p = priorityCombo.getValue();
            config.setProcessPriority(p != null && (p.equalsIgnoreCase("High") || p.contains("HIGH")) ? "HIGH" : "NORMAL");

            configManager.saveConfig();
            new Alert(Alert.AlertType.INFORMATION, "Paths and launch options saved successfully!", ButtonType.OK).showAndWait();
        });

        form.getChildren().addAll(dirCard, launchCard, saveBtn);
        scroll.setContent(form);

        page.getChildren().addAll(title, scroll);
        return page;
    }

    private VBox createDirRow(String labelText, TextField field) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Button browseBtn = new Button("Browse...");
        browseBtn.getStyleClass().add("btn-secondary");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(stage);
            if (f != null) field.setText(f.getAbsolutePath());
        });

        Button openBtn = new Button("Open Folder");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(e -> {
            String path = field.getText().trim();
            if (!path.isEmpty()) openFolder(Paths.get(path));
        });

        HBox h = new HBox(8, field, browseBtn, openBtn);
        HBox.setHgrow(field, Priority.ALWAYS);

        return new VBox(4, lbl, h);
    }

    public static void openFolder(Path folder) {
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(folder.toFile());
            } else {
                new ProcessBuilder("explorer.exe", folder.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            System.err.println("Failed opening folder: " + e.getMessage());
        }
    }

    // ==========================================
    // Page 5: Settings
    // ==========================================
    private Slider ramSlider;
    private Label ramValueLabel;
    private ComboBox<String> javaPathCombo;
    private TextArea jvmArgsArea;
    private TextField widthField;
    private TextField heightField;
    private CheckBox fullscreenCheck;
    private ComboBox<String> launcherActionCombo;
    private TextField msClientIdField;

    private VBox buildSettingsPage() {
        VBox page = new VBox(14);

        Label title = new Label("Game Settings & Memory Allocation");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox form = new VBox(16);
        form.setPadding(new Insets(8));

        LauncherConfig config = configManager.getConfig();

        // 1. RAM Card
        VBox memoryCard = new VBox(8);
        memoryCard.getStyleClass().add("card");
        Label memTitle = new Label("Memory Allocation (RAM)");
        memTitle.getStyleClass().add("card-title");

        ramSlider = new Slider(1024, 16384, config.getMaxMemoryMb());
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setMinorTickCount(1);
        ramSlider.setSnapToTicks(true);

        ramValueLabel = new Label(formatRam(config.getMaxMemoryMb()));
        ramValueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        ramSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int mb = (int) Math.round(newV.doubleValue() / 512.0) * 512;
            ramValueLabel.setText(formatRam(mb));
        });

        memoryCard.getChildren().addAll(memTitle, ramSlider, ramValueLabel);

        // 2. Default Java Runtime
        VBox javaCard = new VBox(8);
        javaCard.getStyleClass().add("card");
        Label javaTitle = new Label("Default Java Runtime");
        javaTitle.getStyleClass().add("card-title");

        javaPathCombo = new ComboBox<>();
        javaPathCombo.setMaxWidth(Double.MAX_VALUE);
        javaPathCombo.setOnShowing(e -> JavaDetector.refreshNowAsync());
        populateJavaCombo(config.getJavaPath());

        Button browseJavaBtn = new Button("Browse...");
        browseJavaBtn.getStyleClass().add("btn-secondary");
        browseJavaBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select java.exe");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Executable", "java.exe", "javaw.exe"));
            File file = fc.showOpenDialog(stage);
            if (file != null) {
                javaPathCombo.getItems().add(file.getAbsolutePath());
                javaPathCombo.setValue(file.getAbsolutePath());
            }
        });

        HBox javaBox = new HBox(8, javaPathCombo, browseJavaBtn);
        HBox.setHgrow(javaPathCombo, Priority.ALWAYS);
        javaCard.getChildren().addAll(javaTitle, javaBox);

        // 3. JVM Arguments Card
        VBox jvmCard = new VBox(8);
        jvmCard.getStyleClass().add("card");
        Label jvmTitle = new Label("JVM Arguments");
        jvmTitle.getStyleClass().add("card-title");

        jvmArgsArea = new TextArea(config.getJvmArgs());
        jvmArgsArea.setPrefRowCount(3);
        jvmArgsArea.setWrapText(true);

        Button resetJvmBtn = new Button("Reset to optimized G1GC flags");
        resetJvmBtn.getStyleClass().add("btn-secondary");
        resetJvmBtn.setOnAction(e -> jvmArgsArea.setText("-XX:+UseG1GC -Dsun.rmi.dgc.server.gcInterval=2147483646 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"));

        jvmCard.getChildren().addAll(jvmTitle, jvmArgsArea, resetJvmBtn);

        // 4. Resolution Card
        VBox resCard = new VBox(8);
        resCard.getStyleClass().add("card");
        Label resTitle = new Label("Game Window Resolution");
        resTitle.getStyleClass().add("card-title");

        widthField = new TextField(String.valueOf(config.getGameWidth()));
        heightField = new TextField(String.valueOf(config.getGameHeight()));
        fullscreenCheck = new CheckBox("Start in fullscreen");
        fullscreenCheck.setSelected(config.isFullscreen());

        HBox resBox = new HBox(12,
                new Label("Width:"), widthField,
                new Label("Height:"), heightField,
                fullscreenCheck);
        resBox.setAlignment(Pos.CENTER_LEFT);
        resCard.getChildren().addAll(resTitle, resBox);

        // 5. Launcher Behavior
        VBox actionCard = new VBox(8);
        actionCard.getStyleClass().add("card");
        Label actionTitle = new Label("Launcher Behavior on Launch");
        actionTitle.getStyleClass().add("card-title");

        launcherActionCombo = new ComboBox<>();
        launcherActionCombo.getItems().addAll("Keep launcher open (KEEP)", "Hide launcher during game (HIDE)", "Close launcher (CLOSE)");
        if ("HIDE".equalsIgnoreCase(config.getLauncherAction())) {
            launcherActionCombo.setValue("Hide launcher during game (HIDE)");
        } else if ("CLOSE".equalsIgnoreCase(config.getLauncherAction())) {
            launcherActionCombo.setValue("Close launcher (CLOSE)");
        } else {
            launcherActionCombo.setValue("Keep launcher open (KEEP)");
        }

        actionCard.getChildren().addAll(actionTitle, launcherActionCombo);

        // 6. Microsoft Azure Client ID
        VBox msCard = new VBox(8);
        msCard.getStyleClass().add("card");
        Label msTitle = new Label("Microsoft Azure Client ID");
        msTitle.getStyleClass().add("card-title");

        msClientIdField = new TextField(config.getMicrosoftClientId() != null ? config.getMicrosoftClientId() : "43b56eb6-cbec-4278-9c39-d70c21aa6d49");
        msClientIdField.setPromptText("43b56eb6-cbec-4278-9c39-d70c21aa6d49");

        Button resetMsIdBtn = new Button("Reset to Default");
        resetMsIdBtn.getStyleClass().add("btn-secondary");
        resetMsIdBtn.setOnAction(e -> msClientIdField.setText("43b56eb6-cbec-4278-9c39-d70c21aa6d49"));

        HBox msBox = new HBox(8, msClientIdField, resetMsIdBtn);
        HBox.setHgrow(msClientIdField, Priority.ALWAYS);

        msCard.getChildren().addAll(msTitle, msBox);

        Button saveBtn = new Button("Save Settings");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> saveSettings());

        form.getChildren().addAll(memoryCard, javaCard, jvmCard, resCard, actionCard, msCard, saveBtn);
        scroll.setContent(form);

        page.getChildren().addAll(title, scroll);
        return page;
    }

    private void populateJavaCombo(String current) {
        updateSettingsJavaCombo(JavaDetector.getCachedInstallations());
    }

    private void updateSettingsJavaCombo(List<JavaDetector.JavaInfo> detected) {
        if (javaPathCombo == null) return;
        String currentVal = javaPathCombo.getValue();
        if (currentVal == null) {
            currentVal = configManager.getConfig().getJavaPath();
        }

        List<String> items = new ArrayList<>();
        items.add("Auto (Recommended)");
        for (JavaDetector.JavaInfo info : detected) {
            items.add(info.getExecutablePath() + " [Java " + info.getMajorVersion() + " - " + info.getVendor() + "]");
        }

        if (currentVal != null && !currentVal.startsWith("Auto")) {
            String pathOnly = currentVal.contains("[") ? currentVal.substring(0, currentVal.indexOf('[')).trim() : currentVal.trim();
            boolean inList = false;
            for (String it : items) {
                if (it.startsWith(pathOnly)) {
                    inList = true;
                    break;
                }
            }
            if (!inList && !pathOnly.isEmpty()) {
                items.add(pathOnly);
            }
        }

        javaPathCombo.getItems().setAll(items);

        boolean found = false;
        if (currentVal != null && !currentVal.equalsIgnoreCase("auto")) {
            for (String item : items) {
                if (item.startsWith(currentVal) || item.equalsIgnoreCase(currentVal)) {
                    javaPathCombo.setValue(item);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            javaPathCombo.setValue("Auto (Recommended)");
        }
    }

    private void saveSettings() {
        LauncherConfig config = configManager.getConfig();

        int mb = (int) Math.round(ramSlider.getValue() / 512.0) * 512;
        config.setMaxMemoryMb(mb);

        String selJava = javaPathCombo.getValue();
        if (selJava != null && selJava.startsWith("Auto")) {
            config.setJavaPath("auto");
        } else if (selJava != null) {
            int bracket = selJava.indexOf('[');
            config.setJavaPath((bracket != -1 ? selJava.substring(0, bracket).trim() : selJava).trim());
        }

        config.setJvmArgs(jvmArgsArea.getText().trim());

        try {
            config.setGameWidth(Integer.parseInt(widthField.getText().trim()));
            config.setGameHeight(Integer.parseInt(heightField.getText().trim()));
        } catch (NumberFormatException ignored) {}

        config.setFullscreen(fullscreenCheck.isSelected());

        String action = launcherActionCombo.getValue();
        if (action != null && action.contains("HIDE")) {
            config.setLauncherAction("HIDE");
        } else if (action != null && action.contains("CLOSE")) {
            config.setLauncherAction("CLOSE");
        } else {
            config.setLauncherAction("KEEP");
        }

        String clientId = msClientIdField.getText().trim();
        if (!clientId.isEmpty()) {
            config.setMicrosoftClientId(clientId);
        } else {
            config.setMicrosoftClientId("43b56eb6-cbec-4278-9c39-d70c21aa6d49");
        }

        configManager.saveConfig();
        new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully!", ButtonType.OK).showAndWait();
    }

    private String formatRam(int mb) {
        double gb = mb / 1024.0;
        return String.format("%d MB (%.1f GB)", mb, gb);
    }

    // ==========================================
    // Page 6: In-App Terminal & Logs
    // ==========================================
    private VBox buildConsolePage() {
        VBox page = new VBox(12);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Launcher & Game Terminal");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");

        terminalStatusBadge = new Label("[ Ready ]");
        terminalStatusBadge.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyBtn = new Button("Copy Logs");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setOnAction(e -> {
            Clipboard cb = Clipboard.getSystemClipboard();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(consoleTextArea.getText());
            cb.setContent(cc);
            copyBtn.setText("Logs Copied!");
        });

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            logBuffer.setLength(0);
            if (consoleTextArea != null) consoleTextArea.clear();
        });

        header.getChildren().addAll(title, terminalStatusBadge, spacer, copyBtn, clearBtn);

        consoleTextArea = new TextArea();
        consoleTextArea.getStyleClass().add("console-area");
        consoleTextArea.setEditable(false);
        consoleTextArea.setWrapText(false);
        VBox.setVgrow(consoleTextArea, Priority.ALWAYS);

        // Interactive Terminal Command Bar
        terminalInputField = new TextField();
        terminalInputField.getStyleClass().add("terminal-input");
        terminalInputField.setPromptText("Enter command (help, clear, status, info, instances, gc)...");
        HBox.setHgrow(terminalInputField, Priority.ALWAYS);

        Button sendBtn = new Button("Send");
        sendBtn.getStyleClass().add("btn-primary");

        Runnable executeCommand = () -> {
            String cmd = terminalInputField.getText().trim();
            if (cmd.isEmpty()) return;
            terminalInputField.clear();
            handleTerminalCommand(cmd);
        };

        terminalInputField.setOnAction(e -> executeCommand.run());
        sendBtn.setOnAction(e -> executeCommand.run());

        HBox commandBar = new HBox(8, terminalInputField, sendBtn);
        commandBar.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().addAll(header, consoleTextArea, commandBar);
        return page;
    }

    private void handleTerminalCommand(String cmd) {
        appendLog("> " + cmd);
        String lower = cmd.toLowerCase().trim();

        if (lower.equals("help")) {
            appendLog("--- IPOCraft Terminal Help ---");
            appendLog("  help      - Show this help message");
            appendLog("  clear     - Clear terminal screen");
            appendLog("  status    - Current status of launcher and game");
            appendLog("  info      - System, memory, and Java environment details");
            appendLog("  instances - List all installed instances");
            appendLog("  gc        - Run garbage collector and free memory");
            appendLog("  <command> - If Minecraft is running, sends command to game process");
            appendLog("------------------------------");
            return;
        }

        if (lower.equals("clear")) {
            logBuffer.setLength(0);
            if (consoleTextArea != null) consoleTextArea.clear();
            return;
        }

        if (lower.equals("status")) {
            boolean running = MinecraftLauncher.isGameRunning();
            Instance active = instanceManager.getActiveInstance();
            Account acc = accountManager.getActiveAccount();
            appendLog("[Status] Game: " + (running ? "Running (PID active)" : "Stopped (idle)"));
            appendLog("[Status] Active instance: " + (active != null ? active.getName() + " (" + active.getMinecraftVersion() + ")" : "None selected"));
            appendLog("[Status] Active player: " + (acc != null ? acc.getUsername() + " [" + acc.getType() + "] UUID: " + acc.getUuid() : "None selected"));
            return;
        }

        if (lower.equals("info")) {
            long totalRam = Runtime.getRuntime().totalMemory() / (1024 * 1024);
            long freeRam = Runtime.getRuntime().freeMemory() / (1024 * 1024);
            long usedRam = totalRam - freeRam;
            appendLog("[Info] IPOCraft Launcher v2.0");
            appendLog("[Info] OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            appendLog("[Info] Java runtime: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
            appendLog("[Info] Launcher memory: " + usedRam + " MB used / " + totalRam + " MB allocated");
            appendLog("[Info] Instances directory: " + configManager.getConfig().resolveInstancesPath());
            return;
        }

        if (lower.equals("instances")) {
            List<Instance> list = instanceManager.getInstances();
            appendLog("[Instances] Total instances: " + list.size());
            for (int i = 0; i < list.size(); i++) {
                Instance inst = list.get(i);
                appendLog(String.format("  [%d] %s | Version: %s | ID: %s",
                        i + 1, inst.getName(), inst.getMinecraftVersion(), inst.getId()));
            }
            return;
        }

        if (lower.equals("gc")) {
            long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            System.gc();
            long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long freed = (before - after) / (1024 * 1024);
            appendLog("[GC] Garbage collection completed. Freed memory: " + Math.max(0, freed) + " MB");
            return;
        }

        // If game is running, send command to game process
        if (MinecraftLauncher.isGameRunning()) {
            boolean sent = MinecraftLauncher.sendCommand(cmd);
            if (sent) {
                appendLog("[Game] Command sent to Minecraft: " + cmd);
            } else {
                appendLog("[Error] Failed to send command to Minecraft process");
            }
        } else {
            appendLog("[Terminal] Unknown command: '" + cmd + "'. Type 'help' for available commands.");
        }
    }

    private void setupSystemStreamsRedirection() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        OutputStream outStream = new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();
                    appendLog(line);
                    originalOut.println(line);
                } else if (b != '\r') {
                    buffer.write(b);
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                for (int i = off; i < off + len; i++) {
                    write(b[i]);
                }
            }
        };

        OutputStream errStream = new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();
                    appendLog("[ERR] " + line);
                    originalErr.println(line);
                } else if (b != '\r') {
                    buffer.write(b);
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                for (int i = off; i < off + len; i++) {
                    write(b[i]);
                }
            }
        };

        System.setOut(new PrintStream(outStream, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errStream, true, StandardCharsets.UTF_8));

        appendLog("[IPOCraft] Internal terminal ready.");
        appendLog("[IPOCraft] Launcher running in GUI mode (no external console window).");
        appendLog("[IPOCraft] Type 'help' in the prompt below for available commands.");
    }

    public void appendLog(String line) {
        Platform.runLater(() -> {
            logBuffer.append(line).append('\n');
            if (consoleTextArea != null) {
                consoleTextArea.appendText(line + "\n");
            }
        });
    }

    // ==========================================
    // Hero Play Bar (Bottom)
    // ==========================================
    private HBox buildHeroPlayBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("hero-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        heroInstanceName = new Label("Instance");
        heroInstanceName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        heroInstanceVersion = new Label("Version");
        heroInstanceVersion.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        VBox instanceInfo = new VBox(2, heroInstanceName, heroInstanceVersion);
        instanceInfo.setMinWidth(160);

        // Java runtime quick selector right in the play bar
        VBox javaBox = new VBox(2);
        Label javaLbl = new Label("Java Runtime:");
        javaLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px; -fx-font-weight: bold;");

        heroJavaSelector = new ComboBox<>();
        heroJavaSelector.setPrefWidth(220);
        heroJavaSelector.setOnShowing(e -> JavaDetector.refreshNowAsync());
        populateHeroJavaSelector();

        heroJavaSelector.setOnAction(e -> {
            Instance active = instanceManager.getActiveInstance();
            if (active != null) {
                String val = heroJavaSelector.getValue();
                if (val != null && val.startsWith("Auto")) {
                    active.setCustomJavaPath("");
                } else if (val != null) {
                    int b = val.indexOf('[');
                    active.setCustomJavaPath((b != -1 ? val.substring(0, b).trim() : val).trim());
                }
                instanceManager.saveInstances();
            }
        });

        javaBox.getChildren().addAll(javaLbl, heroJavaSelector);

        // Progress & Status
        heroStatusLabel = new Label("Ready to launch");
        heroStatusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        heroProgressBar = new ProgressBar(0);
        heroProgressBar.setMaxWidth(Double.MAX_VALUE);
        heroProgressBar.setVisible(false);

        VBox progressBox = new VBox(4, heroStatusLabel, heroProgressBar);
        progressBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(progressBox, Priority.ALWAYS);

        // Big PLAY Button
        heroPlayButton = new Button("PLAY");
        heroPlayButton.getStyleClass().add("play-button");
        heroPlayButton.setOnAction(e -> launchActiveInstance());

        bar.getChildren().addAll(instanceInfo, javaBox, progressBox, heroPlayButton);
        return bar;
    }

    private void onJavaInstallationsChanged(List<JavaDetector.JavaInfo> detected) {
        updateHeroJavaSelector(detected);
        updateSettingsJavaCombo(detected);
    }

    private void populateHeroJavaSelector() {
        updateHeroJavaSelector(JavaDetector.getCachedInstallations());
    }

    private void updateHeroJavaSelector(List<JavaDetector.JavaInfo> detected) {
        if (heroJavaSelector == null) return;
        String currentVal = heroJavaSelector.getValue();
        Instance active = instanceManager.getActiveInstance();
        String customPath = (active != null) ? active.getCustomJavaPath() : null;

        List<String> items = new ArrayList<>();
        items.add("Auto (Recommended)");
        for (JavaDetector.JavaInfo info : detected) {
            items.add(info.getExecutablePath() + " [Java " + info.getMajorVersion() + " - " + info.getVendor() + "]");
        }

        heroJavaSelector.getItems().setAll(items);

        if (customPath != null && !customPath.isEmpty()) {
            for (String item : items) {
                if (item.startsWith(customPath)) {
                    heroJavaSelector.setValue(item);
                    return;
                }
            }
        }

        if (currentVal != null && !currentVal.startsWith("Auto")) {
            for (String item : items) {
                if (item.equals(currentVal) || item.startsWith(currentVal.split(" \\[")[0])) {
                    heroJavaSelector.setValue(item);
                    return;
                }
            }
        }

        heroJavaSelector.setValue("Auto (Recommended)");
    }

    private void refreshHeroBar() {
        Instance active = instanceManager.getActiveInstance();
        if (active != null) {
            heroInstanceName.setText(active.getName());
            heroInstanceVersion.setText("Version: " + active.getMinecraftVersion());
            heroPlayButton.setDisable(false);

            // Sync java selector
            if (active.getCustomJavaPath() != null && !active.getCustomJavaPath().isEmpty()) {
                boolean found = false;
                for (String item : heroJavaSelector.getItems()) {
                    if (item.startsWith(active.getCustomJavaPath())) {
                        heroJavaSelector.setValue(item);
                        found = true;
                        break;
                    }
                }
                if (!found) heroJavaSelector.setValue("Auto (Recommended)");
            } else {
                heroJavaSelector.setValue("Auto (Recommended)");
            }
        } else {
            heroInstanceName.setText("No instance selected");
            heroInstanceVersion.setText("-");
            heroPlayButton.setDisable(true);
        }
    }

    private void launchActiveInstance() {
        Instance inst = instanceManager.getActiveInstance();
        Account acc = accountManager.getActiveAccount();

        if (inst == null) {
            new Alert(Alert.AlertType.WARNING, "Please select or create an instance!", ButtonType.OK).showAndWait();
            return;
        }
        if (acc == null) {
            new Alert(Alert.AlertType.WARNING, "Please add or select a player profile!", ButtonType.OK).showAndWait();
            return;
        }

        heroPlayButton.setDisable(true);
        heroProgressBar.setVisible(true);
        heroProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        heroStatusLabel.setText("Preparing to launch...");

        new Thread(() -> {
            try {
                Account validAcc = acc;
                if (acc.isMicrosoft() && acc.isExpired()) {
                    Platform.runLater(() -> heroStatusLabel.setText("Refreshing Microsoft token..."));
                    validAcc = accountManager.refreshActiveAccountIfNeeded();
                }

                final Account launchAcc = validAcc;

                MinecraftLauncher.launch(
                        inst,
                        launchAcc,
                        progress -> Platform.runLater(() -> {
                            heroProgressBar.setProgress(progress);
                            heroStatusLabel.setText(String.format("Downloading: %d%%", (int) (progress * 100)));
                        }),
                        status -> Platform.runLater(() -> heroStatusLabel.setText(status)),
                        this::appendLog,
                        () -> Platform.runLater(() -> {
                            heroPlayButton.setDisable(false);
                            heroProgressBar.setVisible(false);
                            heroStatusLabel.setText("Game finished.");
                            if (terminalStatusBadge != null) {
                                terminalStatusBadge.setText("[ Ready ]");
                                terminalStatusBadge.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
                            }

                            String action = configManager.getConfig().getLauncherAction();
                            if ("HIDE".equalsIgnoreCase(action)) {
                                stage.show();
                            }
                        })
                );

                Platform.runLater(() -> {
                    heroStatusLabel.setText("Minecraft is running!");
                    if (terminalStatusBadge != null) {
                        terminalStatusBadge.setText("[ Game Running ]");
                        terminalStatusBadge.setStyle("-fx-background-color: #14532d; -fx-text-fill: #4ade80; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                    String action = configManager.getConfig().getLauncherAction();
                    if ("HIDE".equalsIgnoreCase(action)) {
                        stage.hide();
                    } else if ("CLOSE".equalsIgnoreCase(action)) {
                        stage.close();
                        System.exit(0);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                appendLog("[ERROR] " + ex.getMessage());
                Platform.runLater(() -> {
                    heroPlayButton.setDisable(false);
                    heroProgressBar.setVisible(false);
                    heroStatusLabel.setText("Launch error");
                    new Alert(Alert.AlertType.ERROR, "Failed to launch Minecraft:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
                });
            }
        }, "Minecraft-Launcher-Thread").start();
    }
}
