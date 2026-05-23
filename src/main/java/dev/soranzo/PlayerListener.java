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
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                db.addPlayer(uuid, name);
            } else if (pd.monitored()) {

                long now = Instant.now().getEpochSecond();

                if (pd.timeTbsp() > 0) {
                    long ticks = pd.timeTbsp() * 20 * 60;
                    Bukkit.getScheduler().runTaskLater(ChronoStore.getInstance(), () -> {

                        if (Bukkit.getPlayer(uuid) == null) return;
                        try {
                            db.beginSession(uuid, now);
                            db.getPlayerData(uuid);
                            sm.startSession(uuid, now, pd.timePlayedToday(), pd.timeLimit());
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }, ticks);
                } else {
                    sm.startSession(uuid, now, pd.timePlayedToday(), pd.timeLimit());
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
            if (pd != null && pd.monitored()) {
                long time_played = sm.getSession(uuid).timePlayedToday();
                db.addTimePlayed(uuid, time_played);
                sm.endSession(uuid);
                db.endSession(uuid);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}


