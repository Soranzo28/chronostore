package dev.soranzo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final Database db = Database.getInstance();
    private final SessionManager sm = ChronoStore.getInstance().getSessionManager();

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        String name = e.getPlayer().getName();
        try {
            ChronoStore.getInstance().getLeaderboard().assign(e.getPlayer());

            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                db.addPlayer(uuid, name);
                db.beginSession(uuid, Instant.now().getEpochSecond());
            } else {
                long now = Instant.now().getEpochSecond();

                if (pd.monitored()) {
                    if (pd.timeTbsp() > 0) {
                        long ticks = pd.timeTbsp() * 20;
                        sm.setTbspExpiry(uuid, now + pd.timeTbsp());
                        Bukkit.getScheduler().runTaskLater(ChronoStore.getInstance(), () -> {
                            if (Bukkit.getPlayer(uuid) == null) return;
                            try {
                                long startTime = Instant.now().getEpochSecond();
                                PlayerData fresh = db.getPlayerData(uuid);
                                if (fresh == null || !fresh.monitored()) return;
                                db.beginSession(uuid, startTime);
                                sm.startSession(uuid, startTime, fresh.timePlayedToday(), fresh.timeLimit());
                            } catch (SQLException ex) {
                                ex.printStackTrace();
                            }
                        }, ticks);
                    } else {
                        db.beginSession(uuid, now);
                        sm.startSession(uuid, now, pd.timePlayedToday(), pd.timeLimit());
                    }
                } else {
                    db.beginSession(uuid, now);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) return;

            if (pd.monitored()) {
                sm.removeTbspExpiry(uuid);
                SessionData sd = sm.getSession(uuid);
                if (sd != null) {
                    long ref = ChronoStore.isPaused()
                        ? Math.max(sd.startTime(), ChronoStore.getLastPauseTime())
                        : Instant.now().getEpochSecond();
                    db.addTimePlayed(uuid, ref - sd.startTime());
                    sm.endSession(uuid);
                }
            }

            db.endSession(uuid);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}


