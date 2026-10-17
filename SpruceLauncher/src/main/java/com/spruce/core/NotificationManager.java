package com.spruce.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class NotificationManager {
    private NotificationManager() {
    }

    private static final int MAX_HISTORY = 24;

    public enum Level {
        INFO, SUCCESS, WARNING, ERROR
    }

    public static final class Notification {
        public final Level level;
        public final String title;
        public final String message;

        public Notification(Level level, String title, String message) {
            this.level = level;
            this.title = title;
            this.message = message;
        }
    }

    private static Consumer<Notification> sink;
    private static Runnable listener;
    private static final Deque<Notification> history = new ArrayDeque<>();

    public static void setSink(Consumer<Notification> consumer) {
        sink = consumer;
    }

    public static void setListener(Runnable runnable) {
        listener = runnable;
    }

    public static List<Notification> history() {
        return new ArrayList<>(history);
    }

    public static void clearHistory() {
        history.clear();
    }

    public static void info(String title, String message) {
        emit(new Notification(Level.INFO, title, message));
    }

    public static void success(String title, String message) {
        emit(new Notification(Level.SUCCESS, title, message));
    }

    public static void warning(String title, String message) {
        emit(new Notification(Level.WARNING, title, message));
    }

    public static void error(String title, String message) {
        emit(new Notification(Level.ERROR, title, message));
    }

    private static void emit(Notification notification) {
        history.addFirst(notification);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        if (sink != null) {
            sink.accept(notification);
        }
        if (listener != null) {
            listener.run();
        }
    }
}
