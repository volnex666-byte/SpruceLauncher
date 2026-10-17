package com.spruce.core;

public class LaunchResult {
    public final boolean started;
    public final String error;
    public final Process process;

    private LaunchResult(boolean started, String error, Process process) {
        this.started = started;
        this.error = error;
        this.process = process;
    }

    public static LaunchResult running(Process process) {
        return new LaunchResult(true, null, process);
    }

    public static LaunchResult failure(String error) {
        return new LaunchResult(false, error, null);
    }
}
