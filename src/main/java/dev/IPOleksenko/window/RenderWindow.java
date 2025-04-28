package dev.IPOleksenko.window;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;

import java.io.InputStream;

public class RenderWindow {

    private Stage primaryStage;
    private BorderPane root;
    private Canvas canvas;
    private ImageView logoView;
    private StackPane centerPane;

    private double logicalWidth = 1024;
    private double logicalHeight = 720;

    public RenderWindow() {
        root = new BorderPane();

        canvas = new Canvas(logicalWidth, logicalHeight);
        drawBackground();

        StackPane backgroundPane = new StackPane(canvas);
        backgroundPane.setPickOnBounds(false);

        logoView = new ImageView();
        logoView.setPreserveRatio(true);
        updateLogoImage();

        VBox topBox = new VBox(logoView);
        topBox.setStyle("-fx-alignment: center;");
        topBox.setMinHeight(100);
        topBox.setMaxHeight(200);

        centerPane = new StackPane();
        centerPane.setPadding(new Insets(20));

        StackPane stack = new StackPane(backgroundPane, root);
        root.setTop(topBox);
        root.setCenter(centerPane);
    }

    public void show(Stage primaryStage) {
        this.primaryStage = primaryStage;
        Scene scene = new Scene(new StackPane(canvas, root), logicalWidth, logicalHeight);

        scene.widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(newVal.doubleValue());
            drawBackground();
            updateLogoSize(newVal.doubleValue());
        });

        scene.heightProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setHeight(newVal.doubleValue());
            drawBackground();
        });

        primaryStage.setTitle("IPOCraft");
        addWindowIcons();
        primaryStage.setScene(scene);
        primaryStage.show();

        drawBackground();
        updateLogoSize(logicalWidth);
    }

    private void drawBackground() {
        InputStream imageStream = getClass().getResourceAsStream("/assets/background.png");
        if (imageStream != null) {
            Image bgImage = new Image(imageStream, 64, 64, false, true);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

            for (double y = 0; y < canvas.getHeight(); y += 64) {
                for (double x = 0; x < canvas.getWidth(); x += 64) {
                    gc.drawImage(bgImage, x, y);
                }
            }
        } else {
            System.out.println("Background image not found.");
        }
    }

    private void updateLogoImage() {
        InputStream logoStream = getClass().getResourceAsStream("/assets/logo.png");
        if (logoStream != null) {
            Image logoImage = new Image(logoStream);
            logoView.setImage(logoImage);
        } else {
            System.out.println("Logo not found.");
        }
    }

    private void updateLogoSize(double windowWidth) {
        if (logoView.getImage() != null) {
            logoView.setFitWidth(windowWidth * 0.4);
        }
    }

    private void addWindowIcons() {
        InputStream iconStream = getClass().getResourceAsStream("/assets/icon.png");
        if (iconStream != null) {
            Image icon = new Image(iconStream);
            primaryStage.getIcons().add(icon);
        } else {
            System.out.println("Icon not found in resources.");
        }
    }

    public Pane getContentPane() {
        return centerPane;
    }

    public void setMaximized(boolean maximized) {
        if (primaryStage != null) {
            primaryStage.setMaximized(maximized);
        }
    }

    public ImageView getLogoView() {
        return logoView;
    }
}
