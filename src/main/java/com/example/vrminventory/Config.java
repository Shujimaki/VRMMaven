package com.example.vrminventory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getResourceAsStream("/config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            Logger.logError("Failed to load configuration", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}