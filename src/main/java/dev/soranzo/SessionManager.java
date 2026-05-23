package dev.soranzo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final Map<UUID, SessionData> activeSessions = new HashMap<>();
    private final Database db = Database.getInstance();

    public void startSession(UUID uuid, long startTime, long playedTimeToday, int timeLimit) {
        SessionData sd = new SessionData(startTime, playedTimeToday, timeLimit);
        activeSessions.put(uuid, sd);
    }

    public void endSession(UUID uuid) {
        activeSessions.remove(uuid);
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