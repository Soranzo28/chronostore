package dev.soranzo.chronointegration;

public record SessionRecord(
        int id,
        String playerUuid,
        long dateIn,
        Long dateOut
) {}