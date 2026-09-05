package com.IPOleksenko;

/**
 * Standard launcher entrypoint for JavaFX fat jar execution.
 * By not extending Application itself, this avoids the JavaFX runtime component
 * restriction when launching a shaded fat-jar directly with java -jar or double-click.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}

