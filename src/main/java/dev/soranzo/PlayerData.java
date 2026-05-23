package dev.soranzo;

import java.util.UUID;

public record PlayerData(
        UUID uuid,
        String name,
        int timeLimit,
        long timeTbsp,
        int timePlayedToday,
        boolean monitored
) {}
