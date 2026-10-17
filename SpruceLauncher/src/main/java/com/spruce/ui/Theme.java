package com.spruce.ui;

import javafx.scene.paint.Color;

public final class Theme {
    private Theme() {
    }

    // Green-Black premium palette
    public static final Color ACCENT    = Color.web("#39D353");   // Main green
    public static final Color ACCENT_2  = Color.web("#1E8F42");   // Secondary green
    public static final Color ACCENT_SOFT = Color.web("#2AB847"); // Soft green
    public static final Color TEXT      = Color.web("#DCE8DE");   // Light elements
    public static final Color MUTED     = Color.web("#849589");   // Secondary text

    // Mode values stored in config.json
    public static final String MODE_GRAPHITE = "graphite";
    public static final String MODE_OBSIDIAN = "obsidian";
    public static final String MODE_LIGHT = "light";

    // CSS root style classes
    public static final String GRAPHITE = "theme-graphite";
    public static final String OBSIDIAN = "theme-obsidian";
    public static final String LIGHT = "theme-light";

    public static boolean isLight(String theme) {
        return MODE_LIGHT.equals(theme);
    }

    public static boolean isDark(String theme) {
        return !isLight(theme);
    }

    public static String classFor(String theme) {
        if (MODE_OBSIDIAN.equals(theme)) {
            return OBSIDIAN;
        }
        if (MODE_LIGHT.equals(theme)) {
            return LIGHT;
        }
        return GRAPHITE;
    }

    public static Color background(String theme) {
        if (isLight(theme)) {
            return Color.web("#EFF5F0");
        }
        return MODE_OBSIDIAN.equals(theme) ? Color.web("#060B07") : Color.web("#080D09");
    }

    public static Color glow(String theme) {
        if (isLight(theme)) {
            return Color.web("#D8EEDD");
        }
        return MODE_OBSIDIAN.equals(theme) ? Color.web("#0D2212") : Color.web("#0F2416");
    }
}
