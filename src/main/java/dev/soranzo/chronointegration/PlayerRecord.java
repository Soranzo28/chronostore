package dev.soranzo.chronointegration;

public record PlayerRecord(
        String uuid,
        String name,
        int timeLimit,
        int graceTime,
        int timePlayedToday,
        boolean monitored
) {}