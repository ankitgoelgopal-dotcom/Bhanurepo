package com.example.browser;

/** Launcher that does NOT extend Application, so JavaFX works from a plain classpath jar. */
public class Main {
    public static void main(String[] args) {
        BrowserApp.begin(args);
    }
}
