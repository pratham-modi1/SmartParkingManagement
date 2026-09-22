package com.pratham.smartparkingmanagement.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseProperties {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = DatabaseProperties.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (input == null) {
                throw new RuntimeException("application.properties not found.");
            }

            properties.load(input);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public static String getUrl() {
        return properties.getProperty("db.url");
    }

    public static String getUsername() {
        return properties.getProperty("db.username");
    }

    public static String getPassword() {
        return properties.getProperty("db.password");
    }

    public static int getPoolSize() {
        return Integer.parseInt(properties.getProperty("db.pool.size"));
    }

    public static String getJwtSecret() {
    return properties.getProperty("jwt.secret");
    }

    public static int getJwtExpiryMinutes() {
        return Integer.parseInt(properties.getProperty("jwt.expiryMinutes"));
    }

}