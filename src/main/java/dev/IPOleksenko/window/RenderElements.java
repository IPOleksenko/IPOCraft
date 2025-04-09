package dev.IPOleksenko.window;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class RenderElements {

    public static void addText(RenderWindow window, String textContent) {
        Text text = new Text(textContent);
        text.setFill(Color.BLACK);  // Text color
        window.getRoot().getChildren().add(text); // Add text to the window
    }
}
