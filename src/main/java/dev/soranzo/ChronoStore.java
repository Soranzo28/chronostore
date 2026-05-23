package dev.soranzo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.UUID;

public class ChronoStore extends JavaPlugin {

    private static ChronoStore instance;
    private static final SessionManager sm = new SessionManager();
    private static Database db;

    @Override
    public void onEnable() {
        instance = this;
        try {
            db = Database.getInstance(getDataFolder());

            long lastReset = db.getLastReset();
            LocalDate lastResetDate = Instant.ofEpochSecond(lastReset)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if (!lastResetDate.equals(LocalDate.now())) {
                db.resetAllTimePlayed();
                db.setLastReset();
            }

            getLogger().info("ChronoStore iniciado!");
            getServer().getPluginManager().registerEvents(new PlayerListener(), this);

            var cmd = getCommand("chrono");
            if (cmd != null) cmd.setExecutor(new ChronoCommand());

            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {

                    UUID uuid = player.getUniqueId();

                    SessionData sd = sm.getSession(uuid);
                    if (sd == null) continue;

                    long timePlayed = sd.timePlayedToday() + (Instant.now().getEpochSecond() - sd.startTime());
                    if (timePlayed >= sd.timeLimit()) {
                        player.kick(Component.text("Tempo esgotado! Volte em " + sm.formatTimeUntilReset()).color(NamedTextColor.RED));
                    }
                }
            }, 0L, 20L);

            long seconds_until_reset = LocalDate.now().plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toEpochSecond() - Instant.now().getEpochSecond();

            long ticks_until_reset = seconds_until_reset * 20;

            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    db.resetAllTimePlayed();
                    db.setLastReset();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }, ticks_until_reset, 20L*60*60*24); //every 24h


        } catch (SQLException e) {
            getLogger().severe("Erro ao inicializar banco: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("ChronoStore desligado!");
    }


    public static ChronoStore getInstance(){
        return instance;
    }

    public SessionManager getSessionManager() {
        return sm;
    }
}