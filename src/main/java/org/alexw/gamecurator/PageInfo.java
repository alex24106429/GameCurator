package org.alexw.gamecurator;

public class PageInfo {
    final String title;
    final String id;
    final String iconName; 

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