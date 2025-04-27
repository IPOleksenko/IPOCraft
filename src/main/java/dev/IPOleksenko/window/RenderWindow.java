package dev.IPOleksenko.window;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.InputStream;

public class RenderWindow {

    private Stage primaryStage;
    private Pane root;
    private Canvas canvas;
    private double logicalWidth = 1024;
    private double logicalHeight = 720;

    public RenderWindow() {
        root = new Pane();
        canvas = new Canvas(logicalWidth, logicalHeight);
        root.getChildren().add(canvas);
    }

    public void show(Stage primaryStage) {
        this.primaryStage = primaryStage;
        Scene scene = new Scene(root, logicalWidth, logicalHeight);

        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            canvas.setWidth(newValue.doubleValue());
            drawBackground();
            drawLogo(newValue.doubleValue(), scene.getHeight());
        });

        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            canvas.setHeight(newValue.doubleValue());
            drawBackground();
            drawLogo(scene.getWidth(), newValue.doubleValue());
        });

        primaryStage.setTitle("IPOCraft");
        addWindowIcons();
        primaryStage.setScene(scene);
        primaryStage.show();

        drawBackground();
        drawLogo(logicalWidth, logicalHeight);
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

    private void drawLogo(double windowWidth, double windowHeight) {
        InputStream logoStream = getClass().getResourceAsStream("/assets/logo.png");
        if (logoStream != null) {
            Image logoImage = new Image(logoStream);

            double logoWidth = windowWidth * 0.3;
            double logoHeight = logoImage.getHeight() * (logoWidth / logoImage.getWidth());

            double logoX = (windowWidth - logoWidth) * 0.5;
            double logoY = 10;

            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.drawImage(logoImage, logoX, logoY, logoWidth, logoHeight);
        } else {
            System.out.println("Logo not found.");
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

    public Pane getRoot() {
        return root;
    }
}
