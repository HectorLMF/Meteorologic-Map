package com.javamid.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public final class Config {

    private static final Properties PROPS = new Properties();

    static {
        // Classpath defaults
        loadFromClasspath("application.properties");

        // Local (workspace) overrides: don't commit these files.
        // We search upwards from the current working directory so running from a subfolder still works.
        loadFromNearestParent("application-local.properties");
        loadFromNearestParent("javamid.local.properties");

        // User-home overrides
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            loadFromFile(new File(userHome, ".javamid.properties"));
        }
    }

    private Config() {
    }

    public static String get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String fromSystem = System.getProperty(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem;
        }
        String fromProps = PROPS.getProperty(key);
        if (fromProps == null || fromProps.isBlank()) {
            return null;
        }
        return fromProps;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void loadFromClasspath(String resourceName) {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                return;
            }
            PROPS.load(in);
        } catch (Exception ignored) {
            // Best-effort; keep running without config
        }
    }

    private static void loadFromFile(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            PROPS.load(in);
        } catch (Exception ignored) {
            // Best-effort; keep running without config
        }
    }

    private static void loadFromNearestParent(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        File current;
        try {
            String userDir = System.getProperty("user.dir");
            current = (userDir == null || userDir.isBlank()) ? new File(".") : new File(userDir);
            current = current.getCanonicalFile();
        } catch (Exception e) {
            current = new File(".");
        }

        while (current != null) {
            File candidate = new File(current, fileName);
            if (candidate.isFile()) {
                loadFromFile(candidate);
                return;
            }
            current = current.getParentFile();
        }
    }
}
