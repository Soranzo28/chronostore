package dev.soranzo;

import dev.soranzo.chronointegration.*;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class ChronoStore extends JavaPlugin implements ChronoStoreAPI {

    private static ChronoStore instance;
    private static final SessionManager sm = new SessionManager();
    private static Database db;
    private static boolean paused = false;
    private static long lastPauseTime = 0;
    private Leaderboard leaderboard;

    @Override
    public List<PlayerRecord> getPlayers() {
        return db.getAllPlayers();
    }

    @Override
    public List<SessionRecord> getSessions() {
    return db.getAllSessions();
    }

    @Override
    public List<PlayerRanking> getTopPlayers(int limit) {
        return db.getTopPlayers(limit);
    }

    @Override
    public ConfigRecord getChronoConfig() {
        return db.getConfig();
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            db = Database.getInstance(getDataFolder());

            db.closeOrphanedSessions();

            long lastReset = db.getLastReset();
            LocalDate lastResetDate = Instant.ofEpochSecond(lastReset)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if (!lastResetDate.equals(LocalDate.now())) {
                db.resetAllTimePlayed();
                db.setLastReset();
            }

            paused = db.isPaused() || isWeekend();
            if (paused != db.isPaused()) db.setPaused(paused);
            if (paused) lastPauseTime = db.getLastPause();

            leaderboard = new Leaderboard();
            leaderboard.update();

            getLogger().info("ChronoStore iniciado! Pausado: " + paused);
            getServer().getPluginManager().registerEvents(new PlayerListener(), this);

            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                ChronoCommand.register(event.registrar())
            );

            Bukkit.getScheduler().runTaskTimer(this, () -> {
                long now = Instant.now().getEpochSecond();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    SessionData sd = sm.getSession(uuid);
                    if (sd == null) {
                        long tbspRemaining = sm.getTbspRemaining(uuid);
                        if (tbspRemaining > 0) {
                            leaderboard.updateTabListTbsp(player, tbspRemaining);
                            Notifier.tickTbsp(player, tbspRemaining);
                        } else {
                            leaderboard.resetTabList(player);
                        }
                        continue;
                    }

                    if (!paused) {
                        long timePlayed = sd.timePlayedToday() + (now - sd.startTime());
                        long remaining = sd.timeLimit() - timePlayed;
                        if (remaining <= 0) {
                            player.kick(Notifier.kickMessage(sm.formatTimeUntilReset()));
                            continue;
                        }
                        if (remaining == 600 || remaining == 300 || remaining == 60) {
                            Notifier.warnTimeLow(player, remaining);
                        }
                        leaderboard.updateTabList(player, remaining);
                        Notifier.tickRemaining(player, remaining);
                    } else {
                        long frozenPlayed = sd.timePlayedToday() + Math.max(0, lastPauseTime - sd.startTime());
                        long frozenRemaining = Math.max(0, sd.timeLimit() - frozenPlayed);
                        leaderboard.updateTabList(player, frozenRemaining);
                    }
                }
            }, 0L, 20L);

            Bukkit.getScheduler().runTaskTimer(this, leaderboard::update, 20L * 60 * 5, 20L * 60 * 5);

            scheduleNextReset();

        } catch (SQLException e) {
            getLogger().severe("Erro ao inicializar banco: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SessionData sd = sm.getSession(player.getUniqueId());
            if (sd == null) continue;
            try {
                long ref = paused
                    ? Math.max(sd.startTime(), lastPauseTime)
                    : Instant.now().getEpochSecond();
                db.addTimePlayed(player.getUniqueId(), ref - sd.startTime());
                db.endSession(player.getUniqueId());
            } catch (SQLException e) {
                getLogger().warning("Falha ao salvar sessão de " + player.getName() + ": " + e.getMessage());
            }
        }
        getLogger().info("ChronoStore desligado!");
    }

    public static long getLastPauseTime() {
        return lastPauseTime;
    }

    public static void setPaused(boolean value) throws SQLException {
        if (value) {
            lastPauseTime = Instant.now().getEpochSecond();
            db.setLastPause(lastPauseTime);
        }
        paused = value;
        db.setPaused(value);
        Component msg = value
            ? Component.text("⏸ Monitoramento pausado. Bom jogo!").color(NamedTextColor.YELLOW)
            : Component.text("▶ Monitoramento retomado.").color(NamedTextColor.GREEN);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Notifier.actionBar(p, msg);
        }
    }

    public static boolean isPaused() {
        return paused;
    }

    private void scheduleNextReset() {
        long now = Instant.now().getEpochSecond();
        long nextMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond();
        long ticksUntilReset = (nextMidnight - now) * 20L;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                db.resetAllTimePlayed();
                db.setLastReset();
                boolean weekend = isWeekend();
                if (paused != weekend) setPaused(weekend);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            scheduleNextReset();
        }, ticksUntilReset);
    }

    private static boolean isWeekend() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static ChronoStore getInstance() {
        return instance;
    }

    public SessionManager getSessionManager() {
        return sm;
    }

    public Leaderboard getLeaderboard() {
        return leaderboard;
    }
}
