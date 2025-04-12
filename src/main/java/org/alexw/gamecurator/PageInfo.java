package org.alexw.gamecurator;

// Simple data class for page definition
public class PageInfo {
    final String title;
    final String id;
    final String iconName; // e.g., "STAR", "CALENDAR", "SEARCH", "LIBRARY", "SETTINGS", "AUTO_FIX"

    public PageInfo(String title, String id, String iconName) {
        this.title = title;
        this.id = id;
        this.iconName = iconName;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public String getIconName() {
        return iconName;
    }
}
