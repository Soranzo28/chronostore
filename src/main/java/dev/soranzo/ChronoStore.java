package dev.soranzo;

import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public class ChronoStore extends JavaPlugin {

    private static ChronoStore instance;

    @Override
    public void onEnable() {
        instance = this;
        try {
            Database.getInstance(getDataFolder());
            getLogger().info("ChronoStore iniciado!");
            getServer().getPluginManager().registerEvents(new PlayerListener(), this);

            var cmd = getCommand("chrono");
            if (cmd != null) cmd.setExecutor(new ChronoCommand());

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
}