package dev.soranzo;

import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public class ChronoStore extends JavaPlugin {

    @Override
    public void onEnable() {
        try {
            Database.getInstance(getDataFolder());
            getLogger().info("ChronoStore iniciado!");
        } catch (SQLException e) {
            getLogger().severe("Erro ao inicializar banco: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("ChronoStore desligado!");
    }
}