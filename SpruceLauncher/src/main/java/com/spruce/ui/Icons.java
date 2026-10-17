package com.spruce.ui;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public final class Icons {
    private Icons() {
    }

    public static final String SPRUCE =
            "M12 1.5 L5.2 12.2 H9.1 L3.4 22 H20.6 L14.9 12.2 H18.8 Z";
    public static final String HOME =
            "M12 3 L3 11 H6 V20 H10 V14 H14 V20 H18 V11 H21 Z";
    public static final String CLIENTS =
            "M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z";
    public static final String SETTINGS =
            "M12 8.2 A3.8 3.8 0 1 0 12 15.8 A3.8 3.8 0 0 0 12 8.2 Z "
            + "M12 2 L13.2 4.6 L16 4.2 L15.4 7 L17.8 8.4 L15.6 10 "
            + "L16.8 12.6 L14 12.6 L13.6 15.4 L12 15.4 L10.4 15.4 "
            + "L10 12.6 L7.2 12.6 L8.4 10 L6.2 8.4 L8.6 7 L8 4.2 L10.8 4.6 Z";
    public static final String PLAY =
            "M8 5 V19 L19 12 Z";
    public static final String FOLDER =
            "M3 6 A2 2 0 0 1 5 4 H9 L11 6 H19 A2 2 0 0 1 21 8 V17 A2 2 0 0 1 19 19 H5 A2 2 0 0 1 3 17 Z";
    public static final String REFRESH =
            "M12 4 A8 8 0 1 0 20 12 H17 A5 5 0 1 1 12 7 V10 L17 5.5 L12 1 Z";
    public static final String CHECK =
            "M9 16.2 L5.5 12.7 L4.1 14.1 L9 19 L20 8 L18.6 6.6 Z";
    public static final String PLUS =
            "M11 5 H13 V11 H19 V13 H13 V19 H11 V13 H5 V11 H11 Z";
    public static final String LAYERS =
            "M12 3 L2 8 L12 13 L22 8 Z M2 12 L12 17 L22 12 M2 16 L12 21 L22 16";

    public static final String NOVA =
            "M12 2 L14.4 9 L21.5 9 L15.8 13.4 L18.1 20.5 L12 16 L5.9 20.5 L8.2 13.4 L2.5 9 L9.6 9 Z";
    public static final String VERTEX =
            "M12 2.5 L21.5 12 L12 21.5 L2.5 12 Z";
    public static final String BELL =
            "M12 2.2 A6.2 6.2 0 0 1 18.2 8.4 V13 L20.2 16.4 H3.8 L5.8 13 V8.4 "
            + "A6.2 6.2 0 0 1 12 2.2 Z M9.9 18.4 H14.1 A2.1 2.1 0 0 1 9.9 18.4 Z";
    public static final String SUN =
            "M12 7 A5 5 0 1 1 12 17 A5 5 0 0 1 12 7 Z "
            + "M11.1 1 H12.9 V4.2 H11.1 Z M11.1 19.8 H12.9 V23 H11.1 Z "
            + "M1 11.1 H4.2 V12.9 H1 Z M19.8 11.1 H23 V12.9 H19.8 Z "
            + "M4.1 5.5 L5.5 4.1 L7.2 5.8 L5.8 7.2 Z M16.8 18.2 L18.2 16.8 L19.9 18.5 L18.5 19.9 Z "
            + "M4.1 18.5 L5.8 16.8 L7.2 18.2 L5.5 19.9 Z M16.8 5.8 L18.5 4.1 L19.9 5.5 L18.2 7.2 Z";
    public static final String MOON =
            "M20.4 14.6 A8.6 8.6 0 1 1 9.4 3.6 A7 7 0 0 0 20.4 14.6 Z";
    public static final String CHEVRON_DOWN =
            "M6.4 9.4 H10 L12 11.6 L14 9.4 H17.6 L12 15.2 Z";

    public static String byKey(String key) {
        if ("nova".equals(key)) {
            return NOVA;
        }
        if ("vertex".equals(key)) {
            return VERTEX;
        }
        if ("diamond".equals(key) || "folder".equals(key)) {
            return VERTEX;
        }
        return SPRUCE;
    }

    public static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(Color.web("#7C8AA5"));
        return icon;
    }

    public static SVGPath icon(String path, double scale, Color color) {
        SVGPath icon = icon(path);
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        icon.setFill(color);
        return icon;
    }
}
