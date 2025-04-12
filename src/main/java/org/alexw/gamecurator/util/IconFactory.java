package org.alexw.gamecurator.util;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class IconFactory {

    private static final Color DEFAULT_ICON_COLOR = Color.BLACK;

    // --- Icon Sizes (Consider moving sizes here or keeping them context-specific) ---
    public static final double NAV_ICON_SIZE = 28.0;
    public static final double BUTTON_ICON_SIZE = 18.0;
    public static final double SETTINGS_ICON_SIZE = 20.0;
    public static final double ASSISTANT_ICON_SIZE = 24.0;

    public static Node createIcon(String iconIdentifier, double size) {
        return createIcon(iconIdentifier, size, DEFAULT_ICON_COLOR);
    }

    public static Node createIcon(String iconIdentifier, double size, Color color) {
        FontIcon icon = new FontIcon();
        // Map abstract identifier to specific Ikonli code
        switch (iconIdentifier.toUpperCase()) {
            // Navigation & Page Icons
            case "STAR":          icon.setIconCode(MaterialDesign.MDI_STAR); break;
            case "CALENDAR":      icon.setIconCode(MaterialDesign.MDI_CALENDAR); break;
            case "SEARCH":        icon.setIconCode(MaterialDesign.MDI_MAGNIFY); break;
            case "BOOKMARK":      icon.setIconCode(MaterialDesign.MDI_BOOKMARK); break;
            case "SETTINGS":      icon.setIconCode(MaterialDesign.MDI_SETTINGS); break;
            case "AUTO_FIX":      icon.setIconCode(MaterialDesign.MDI_AUTO_FIX); break; // For Assistant / AI Toggle

            // Action Icons
            case "ADD":           icon.setIconCode(MaterialDesign.MDI_PLUS_BOX); break;
            case "REMOVE":        icon.setIconCode(MaterialDesign.MDI_MINUS_BOX); break;
            case "SHARE":         icon.setIconCode(MaterialDesign.MDI_SHARE_VARIANT); break;
            case "REFRESH":       icon.setIconCode(MaterialDesign.MDI_REFRESH); break; // For Get Recommendations btn
            case "ROBOT":         icon.setIconCode(MaterialDesign.MDI_ROBOT); break; // Another option for AI

            // Settings Icons
            case "DELETE":        icon.setIconCode(MaterialDesign.MDI_DELETE); break;
            case "RESTORE":       icon.setIconCode(MaterialDesign.MDI_RESTORE); break;
            // case "AI":         icon.setIconCode(MaterialDesign.MDI_AUTO_FIX); break; // Replaced by AUTO_FIX

            // Default / Fallback
            default:
                System.err.println("Warning: Unknown icon identifier: " + iconIdentifier);
                icon.setIconCode(MaterialDesign.MDI_HELP_CIRCLE); // Fallback icon
                break;
        }
        icon.setIconSize((int) size);
        icon.setIconColor(color != null ? color : DEFAULT_ICON_COLOR);
        return icon;
    }
}
