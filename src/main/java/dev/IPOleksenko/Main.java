package dev.IPOleksenko;

import dev.IPOleksenko.window.RenderElements;
import dev.IPOleksenko.window.RenderWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create the window
        RenderWindow renderWindow = new RenderWindow();

        // Show the window
        renderWindow.show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
