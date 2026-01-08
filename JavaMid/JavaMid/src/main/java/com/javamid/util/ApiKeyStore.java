package com.javamid.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Simple helper to read/write a single API key from a file named "Apikey.conf" placed in the
 * current working directory (or its nearest parent). The file format is plain text; the first
 * non-blank line is taken as the key.
 */
public final class ApiKeyStore {

    private static final String FILE_NAME = "Apikey.conf";

    private ApiKeyStore() {}

    public static String readKeyFromNearestParent() {
        File current;
        try {
            String userDir = System.getProperty("user.dir");
            current = (userDir == null || userDir.isBlank()) ? new File(".") : new File(userDir);
            current = current.getCanonicalFile();
        } catch (Exception e) {
            current = new File(".");
        }

        while (current != null) {
            File candidate = new File(current, FILE_NAME);
            if (candidate.isFile()) {
                String k = readKeyFromFile(candidate);
                if (k != null && !k.isBlank()) return k;
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Reads a labeled key from Apikey.conf (format: label=KEY or label: KEY). Case-insensitive label.
     * Searches nearest parent, then cwd.
     */
    public static String readLabeledKey(String label) {
        if (label == null || label.isBlank()) return null;
        String k = readLabeledKeyFromNearestParent(label);
        if (k != null && !k.isBlank()) return k;
        return readLabeledKeyFromCwd(label);
    }

    private static String readLabeledKeyFromNearestParent(String label) {
        File current;
        try {
            String userDir = System.getProperty("user.dir");
            current = (userDir == null || userDir.isBlank()) ? new File(".") : new File(userDir);
            current = current.getCanonicalFile();
        } catch (Exception e) {
            current = new File(".");
        }

        while (current != null) {
            File candidate = new File(current, FILE_NAME);
            if (candidate.isFile()) {
                String k = readLabeledKeyFromFile(candidate, label);
                if (k != null && !k.isBlank()) return k;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static String readLabeledKeyFromCwd(String label) {
        try {
            File f = new File(System.getProperty("user.dir"), FILE_NAME);
            if (f.isFile()) return readLabeledKeyFromFile(f, label);
        } catch (Exception ignored) {}
        return null;
    }

    public static String readKeyFromCwd() {
        try {
            File f = new File(System.getProperty("user.dir"), FILE_NAME);
            if (f.isFile()) return readKeyFromFile(f);
        } catch (Exception ignored) {}
        return null;
    }

    private static String readKeyFromFile(File file) {
        try (BufferedReader r = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) return line.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String readLabeledKeyFromFile(File file, String label) {
        String target = label.trim().toLowerCase();
        try (BufferedReader r = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (s.isBlank() || s.startsWith("#")) continue;
                int eq = s.indexOf('=');
                int colon = s.indexOf(':');
                int sep = (eq >= 0) ? eq : colon;
                if (sep > 0) {
                    String l = s.substring(0, sep).trim().toLowerCase();
                    if (l.equals(target)) {
                        return s.substring(sep+1).trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Save the API key into `Apikey.conf` in the current working directory. Overwrites existing file.
     */
    public static boolean saveKeyToCwd(String key) {
        if (key == null) return false;
        try {
            File f = new File(System.getProperty("user.dir"), FILE_NAME);
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, StandardCharsets.UTF_8))) {
                w.write(key.trim());
                w.newLine();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
