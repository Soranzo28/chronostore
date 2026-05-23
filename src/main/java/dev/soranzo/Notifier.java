package dev.soranzo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Notifier {

    private static final Map<UUID, Long> actionBarPriority = new ConcurrentHashMap<>();
    private static final int PRIORITY_DURATION_MS = 4000;

    public static Component kickMessage(String timeUntilReset) {
        return Component.text()
            .appendNewline()
            .append(Component.text("⏱ TEMPO ESGOTADO").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
            .appendNewline()
            .appendNewline()
            .append(Component.text("Volte em ").color(NamedTextColor.GRAY))
            .append(Component.text(timeUntilReset).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
            .appendNewline()
            .build();
    }

    public static void warnTimeLow(Player player, long remainingSeconds) {
        long min = remainingSeconds / 60;
        long sec = remainingSeconds % 60;
        String timeStr = min > 0 ? min + "m" : sec + "s";
        NamedTextColor color = remainingSeconds <= 60 ? NamedTextColor.RED : NamedTextColor.YELLOW;

        player.showTitle(Title.title(
            Component.text("⚠ " + timeStr + " restantes").color(color).decorate(TextDecoration.BOLD),
            Component.text("Seu tempo está acabando!").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));

        float pitch = remainingSeconds <= 60 ? 0.6f : 1.0f;
        player.playSound(Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.MASTER, 1f, pitch));
    }

    public static void tickTbsp(Player player, long tbspRemaining) {
        Long expiry = actionBarPriority.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() < expiry) return;

        player.sendActionBar(Component.text("⏳ Sessão começa em: ").color(NamedTextColor.GRAY)
            .append(Component.text(formatTime(tbspRemaining)).color(NamedTextColor.AQUA)));
    }

    public static void tickRemaining(Player player, long remainingSeconds) {
        Long expiry = actionBarPriority.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() < expiry) return;

        NamedTextColor color = remainingSeconds > 600 ? NamedTextColor.GREEN
            : remainingSeconds > 60 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        player.sendActionBar(Component.text("⏱ Restante: ").color(NamedTextColor.GRAY)
            .append(Component.text(formatTime(remainingSeconds)).color(color)));
    }

    public static void actionBar(Player player, Component message) {
        actionBarPriority.put(player.getUniqueId(), System.currentTimeMillis() + PRIORITY_DURATION_MS);
        player.sendActionBar(message);
    }

    public static void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text("✔ " + message).color(NamedTextColor.GREEN));
        if (sender instanceof Player p)
            p.playSound(Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.MASTER, 0.8f, 1.2f));
    }

    public static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text("✘ " + message).color(NamedTextColor.RED));
        if (sender instanceof Player p)
            p.playSound(Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.MASTER, 0.8f, 1f));
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h" + m + "m";
        if (m > 0) return m + "m" + s + "s";
        return s + "s";
    }
}
