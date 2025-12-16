package com.wso2.migration.checker.util;

/**
 * Console output formatting utilities.
 */
public final class ConsoleFormatter {

    private ConsoleFormatter() {}

    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";

    /**
     * Prints the application banner.
     */
    public static void printBanner() {
        System.out.println();
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║" + RESET + BOLD + "                  SCHEMA COMPLIANCE CHECKER v1.0                       " + RESET + CYAN + "║" + RESET);
        System.out.println(CYAN + "║" + RESET + "           Migration Readiness & Schema Validation Tool                " + CYAN + "║" + RESET);
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    /**
     * Prints a section header.
     */
    public static void printSection(String title) {
        System.out.println();
        System.out.println(BOLD + "━". repeat(70) + RESET);
        System.out. println(BOLD + " " + title + RESET);
        System.out. println(BOLD + "━". repeat(70) + RESET);
    }

    /**
     * Prints a success message.
     */
    public static void printSuccess(String message) {
        System.out.println(GREEN + "✓ " + message + RESET);
    }

    /**
     * Prints an error message.
     */
    public static void printError(String message) {
        System.out.println(RED + "✗ " + message + RESET);
    }

    /**
     * Prints a warning message.
     */
    public static void printWarning(String message) {
        System.out. println(YELLOW + "⚠ " + message + RESET);
    }

    /**
     * Prints an info message.
     */
    public static void printInfo(String message) {
        System.out. println(BLUE + "ℹ " + message + RESET);
    }

    /**
     * Formats a progress indicator.
     */
    public static String progressBar(int current, int total, int width) {
        double progress = (double) current / total;
        int filled = (int) (progress * width);
        int empty = width - filled;

        return "[" + "█".repeat(filled) + "░".repeat(empty) + "] " +
                String.format("%d/%d (%.0f%%)", current, total, progress * 100);
    }

    /**
     * Clears the current line (for progress updates).
     */
    public static void clearLine() {
        System.out.print("\r" + " ".repeat(80) + "\r");
    }
}