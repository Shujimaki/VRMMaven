package com.example.vrminventory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class Logger {
    private static final String LOG_FILE = "application.log";

    public static void log(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(LocalDateTime.now() + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logError(String message, Exception e) {
        log("ERROR: " + message + " - " + e.getMessage());
    }
}