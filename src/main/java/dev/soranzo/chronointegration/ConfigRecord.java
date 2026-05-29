package dev.soranzo.chronointegration;

public record ConfigRecord(
        int id,
        long lastReset,
        boolean paused,
        Long lastPause
) {}
