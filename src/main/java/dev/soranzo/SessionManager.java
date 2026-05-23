package dev.soranzo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final Map<UUID, SessionData> activeSessions = new HashMap<>();
    private final Map<UUID, Long> tbspExpiry = new HashMap<>();

    public void startSession(UUID uuid, long startTime, long playedTimeToday, int timeLimit) {
        tbspExpiry.remove(uuid);
        activeSessions.put(uuid, new SessionData(startTime, playedTimeToday, timeLimit));
    }

    public void endSession(UUID uuid) {
        activeSessions.remove(uuid);
    }

    public Map<UUID, SessionData> getActiveSessions() {
        return activeSessions;
    }

    public void setTbspExpiry(UUID uuid, long expiryEpoch) {
        tbspExpiry.put(uuid, expiryEpoch);
    }

    public long getTbspRemaining(UUID uuid) {
        Long expiry = tbspExpiry.get(uuid);
        if (expiry == null) return 0;
        return Math.max(0, expiry - Instant.now().getEpochSecond());
    }

    public void removeTbspExpiry(UUID uuid) {
        tbspExpiry.remove(uuid);
    }

    public SessionData getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public long secondsUntilReset() {
        LocalDate amanha = LocalDate.now().plusDays(1);
        long resetEpoch = amanha.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        return resetEpoch - Instant.now().getEpochSecond();
    }

    public String formatTimeUntilReset() {
        long segundos = secondsUntilReset();
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        return horas + "h" + minutos + "m";
    }
}