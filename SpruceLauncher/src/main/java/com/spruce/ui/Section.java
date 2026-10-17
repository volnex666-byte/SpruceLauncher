package com.spruce.ui;

public enum Section {
    HOME("Главное меню", Icons.HOME),
    CLIENTS("Клиенты", Icons.CLIENTS),
    SETTINGS("Настройки", Icons.SETTINGS);

    public final String title;
    public final String icon;

    Section(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
