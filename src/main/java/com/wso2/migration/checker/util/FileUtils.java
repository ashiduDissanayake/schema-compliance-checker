package com.wso2.migration.checker.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * File utility methods.
 */
public final class FileUtils {

    private FileUtils() {}

    /**
     * Calculates SHA-256 hash of a file for integrity verification.
     */
    public static String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file);
            byte[] hash = digest.digest(content);
            return HexFormat. of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Gets file size in human-readable format.
     */
    public static String humanReadableSize(Path file) throws IOException {
        long bytes = Files.size(file);
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%. 1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Ensures a directory exists, creating it if necessary.
     */
    public static Path ensureDirectory(Path dir) throws IOException {
        if (! Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * Reads file content as a string with proper encoding.
     */
    public static String readFileContent(Path file) throws IOException {
        return Files.readString(file);
    }
}