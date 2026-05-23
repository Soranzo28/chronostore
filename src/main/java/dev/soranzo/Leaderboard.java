package dev.soranzo;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;

public class Leaderboard {

    private final Scoreboard board;
    private final Objective objective;

    public Leaderboard() {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective(
            "leaderboard",
            Criteria.DUMMY,
            Component.text("✦ LEADERBOARD ✦").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    @SuppressWarnings("deprecation")
    public void update() {
        for (String entry : new HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        try {
            Map<String, Long> totals = Database.getInstance().getAllTotalTimePlayed();
            int rank = totals.size();
            for (Map.Entry<String, Long> e : totals.entrySet()) {
                String line = "§f◆ §e" + e.getKey() + " §7- §a" + formatTime(e.getValue());
                objective.getScore(line).setScore(rank--);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void assign(Player player) {
        player.setScoreboard(board);
    }

    public void updateTabList(Player player, long remainingSeconds) {
        NamedTextColor color = remainingSeconds > 600 ? NamedTextColor.GREEN
            : remainingSeconds > 60 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        player.playerListName(Component.text()
            .append(Component.text(player.getName()))
            .append(Component.text(" [").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(formatTime(remainingSeconds)).color(color))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
            .build());
    }

    public void updateTabListTbsp(Player player, long tbspRemaining) {
        player.playerListName(Component.text()
            .append(Component.text(player.getName()))
            .append(Component.text(" [⏳ ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(formatTime(tbspRemaining)).color(NamedTextColor.AQUA))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
            .build());
    }

    public void resetTabList(Player player) {
        player.playerListName(Component.text(player.getName()));
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h" + m + "m";
        if (m > 0) return m + "m" + s + "s";
        return s + "s";
    }
}
