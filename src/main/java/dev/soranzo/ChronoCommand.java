package dev.soranzo;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ChronoCommand {

    public static void register(Commands commands) {
        commands.register(
            Commands.literal("chrono")
                .requires(source -> {
                    var sender = source.getSender();
                    return sender instanceof ConsoleCommandSender
                        || (sender instanceof Player p && p.isOp())
                        || sender.hasPermission("chronostore.admin");
                })
                .then(Commands.literal("add")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ChronoCommand::executeAdd)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ChronoCommand::executeRemove)))
                .then(Commands.literal("tbsp")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                            .executes(ChronoCommand::executeTbsp))))
                .then(Commands.literal("status")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ChronoCommand::executeStatus)))
                .then(Commands.literal("limit")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                            .executes(ChronoCommand::executeLimit))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ChronoCommand::executeReset)))
                .then(Commands.literal("pause").executes(ChronoCommand::executePause))
                .then(Commands.literal("resume").executes(ChronoCommand::executeResume))
                .build(),
            "Gerencia o tempo online"
        );
    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(source).getFirst();
        UUID uuid = target.getUniqueId();

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                Notifier.error(source.getSender(), "Player não registrado.");
                return Command.SINGLE_SUCCESS;
            }
            if (pd.monitored()) {
                Notifier.error(source.getSender(), "O jogador já está sendo monitorado.");
                return Command.SINGLE_SUCCESS;
            }
            db.monitorPlayer(uuid, true);
            PlayerData fresh = db.getPlayerData(uuid);
            if (fresh.timeTbsp() > 0) {
                long ticks = fresh.timeTbsp() * 20;
                ChronoStore.getInstance().getSessionManager().setTbspExpiry(uuid, Instant.now().getEpochSecond() + fresh.timeTbsp());
                Bukkit.getScheduler().runTaskLater(ChronoStore.getInstance(), () -> {
                    if (Bukkit.getPlayer(uuid) == null) return;
                    try {
                        long startTime = Instant.now().getEpochSecond();
                        PlayerData fp = db.getPlayerData(uuid);
                        if (fp == null || !fp.monitored()) return;
                        db.beginSession(uuid, startTime);
                        ChronoStore.getInstance().getSessionManager().startSession(uuid, startTime, fp.timePlayedToday(), fp.timeLimit());
                    } catch (SQLException ex) { ex.printStackTrace(); }
                }, ticks);
            } else {
                long now = Instant.now().getEpochSecond();
                db.beginSession(uuid, now);
                ChronoStore.getInstance().getSessionManager().startSession(uuid, now, fresh.timePlayedToday(), fresh.timeLimit());
            }
            Notifier.success(source.getSender(), "Jogador monitorado com sucesso.");
            Notifier.actionBar(target, Component.text("👁 Você está sendo monitorado!").color(NamedTextColor.YELLOW));
        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao monitorar jogador.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(source).getFirst();
        UUID uuid = target.getUniqueId();

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                Notifier.error(source.getSender(), "Player não registrado.");
                return Command.SINGLE_SUCCESS;
            }
            if (!pd.monitored()) {
                Notifier.error(source.getSender(), "O jogador não está sendo monitorado.");
                return Command.SINGLE_SUCCESS;
            }
            SessionData sd = ChronoStore.getInstance().getSessionManager().getSession(uuid);
            if (sd != null) {
                long ref = ChronoStore.isPaused()
                    ? Math.max(sd.startTime(), ChronoStore.getLastPauseTime())
                    : Instant.now().getEpochSecond();
                long elapsed = ref - sd.startTime();
                db.addTimePlayed(uuid, elapsed);
                db.endSession(uuid);
                ChronoStore.getInstance().getSessionManager().endSession(uuid);
            }
            db.monitorPlayer(uuid, false);
            Notifier.success(source.getSender(), "Monitoramento removido com sucesso.");
            Notifier.actionBar(target, Component.text("Monitoramento removido.").color(NamedTextColor.GRAY));
        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao remover monitoramento.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTbsp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(source).getFirst();
        UUID uuid = target.getUniqueId();
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                Notifier.error(source.getSender(), "Player não registrado.");
                return Command.SINGLE_SUCCESS;
            }
            db.setTbsp(uuid, seconds);
            Notifier.success(source.getSender(), "Tbsp definido para " + seconds + " segundos.");
            Notifier.actionBar(target, Component.text("⏳ tbsp: aguardar " + seconds + "s antes da sessão iniciar.").color(NamedTextColor.YELLOW));
        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao setar tbsp.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(source).getFirst();
        UUID uuid = target.getUniqueId();

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                Notifier.error(source.getSender(), "Player não registrado.");
                return Command.SINGLE_SUCCESS;
            }

            SessionData sd = ChronoStore.getInstance().getSessionManager().getSession(uuid);
            long sessionElapsed = 0;
            if (sd != null) {
                long ref = ChronoStore.isPaused()
                    ? Math.max(sd.startTime(), ChronoStore.getLastPauseTime())
                    : Instant.now().getEpochSecond();
                sessionElapsed = ref - sd.startTime();
            }
            long playedToday = (sd != null ? sd.timePlayedToday() : pd.timePlayedToday()) + sessionElapsed;
            long remaining = pd.timeLimit() - playedToday;

            source.getSender().sendMessage(Component.text()
                .append(Component.text("━━━ ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(target.getName()).color(NamedTextColor.YELLOW))
                .append(Component.text(" ━━━").color(NamedTextColor.DARK_GRAY))
                .appendNewline()
                .append(Component.text("Monitorado: ").color(NamedTextColor.GRAY))
                .append(pd.monitored()
                    ? Component.text("sim").color(NamedTextColor.GREEN)
                    : Component.text("não").color(NamedTextColor.RED))
                .appendNewline()
                .append(Component.text("Sessão ativa: ").color(NamedTextColor.GRAY))
                .append(sd != null
                    ? Component.text("sim").color(NamedTextColor.GREEN)
                    : Component.text("não").color(NamedTextColor.RED))
                .appendNewline()
                .append(Component.text("Jogado hoje: ").color(NamedTextColor.GRAY))
                .append(Component.text(Notifier.formatTime(playedToday)).color(NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Limite: ").color(NamedTextColor.GRAY))
                .append(Component.text(Notifier.formatTime(pd.timeLimit())).color(NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Restante: ").color(NamedTextColor.GRAY))
                .append(remaining > 0
                    ? Component.text(Notifier.formatTime(remaining)).color(NamedTextColor.GREEN)
                    : Component.text("esgotado").color(NamedTextColor.RED))
                .appendNewline()
                .append(Component.text("tbsp: ").color(NamedTextColor.GRAY))
                .append(Component.text(pd.timeTbsp() > 0 ? Notifier.formatTime(pd.timeTbsp()) : "nenhum").color(NamedTextColor.AQUA))
                .build());

        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao buscar dados.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executePause(CommandContext<CommandSourceStack> ctx) {
        try {
            if (ChronoStore.isPaused()) {
                Notifier.error(ctx.getSource().getSender(), "O monitoramento já está pausado.");
                return Command.SINGLE_SUCCESS;
            }
            ChronoStore.setPaused(true);
            Notifier.success(ctx.getSource().getSender(), "Monitoramento pausado.");
        } catch (SQLException ex) {
            Notifier.error(ctx.getSource().getSender(), "Erro ao pausar.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeResume(CommandContext<CommandSourceStack> ctx) {
        try {
            SessionManager sm = ChronoStore.getInstance().getSessionManager();
            Database db = Database.getInstance();
            if (!ChronoStore.isPaused()) {
                Notifier.error(ctx.getSource().getSender(), "O monitoramento não está pausado.");
                return Command.SINGLE_SUCCESS;
            }
            ChronoStore.setPaused(false);
            Map<UUID, SessionData> activeSessions = sm.getActiveSessions();
            long last_pause_epoch = db.getLastPause();

            for (Map.Entry<UUID, SessionData> entry : activeSessions.entrySet()) {
               UUID uuid = entry.getKey();
               SessionData sd = entry.getValue();
               long time_played = sd.timePlayedToday() + (last_pause_epoch - sd.startTime());
               sm.startSession(uuid, Instant.now().getEpochSecond(), time_played, sd.timeLimit());
            }

            Notifier.success(ctx.getSource().getSender(), "Monitoramento retomado.");
        } catch (SQLException ex) {
            Notifier.error(ctx.getSource().getSender(), "Erro ao retomar.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        String name = StringArgumentType.getString(ctx, "name");

        try {
            UUID uuid = db.findUUIDByName(name);
            if (uuid == null) {
                Notifier.error(source.getSender(), "Player \"" + name + "\" não encontrado.");
                return Command.SINGLE_SUCCESS;
            }

            db.resetOneTimePlayed(uuid);

            SessionData sd = ChronoStore.getInstance().getSessionManager().getSession(uuid);
            if (sd != null) {
                long now = Instant.now().getEpochSecond();
                ChronoStore.getInstance().getSessionManager().startSession(uuid, now, 0, sd.timeLimit());
            }

            Notifier.success(source.getSender(), "Tempo de " + name + " resetado.");

            Player online = Bukkit.getPlayer(uuid);
            if (online != null)
                Notifier.actionBar(online, Component.text("⏱ Seu tempo foi resetado!").color(NamedTextColor.GREEN));

        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao resetar tempo.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Database db = Database.getInstance();
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(source).getFirst();
        UUID uuid = target.getUniqueId();
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

        try {
            PlayerData pd = db.getPlayerData(uuid);
            if (pd == null) {
                Notifier.error(source.getSender(), "Player não registrado.");
                return Command.SINGLE_SUCCESS;
            }
            db.setTimeLimit(uuid, minutes * 60);
            SessionData sd = ChronoStore.getInstance().getSessionManager().getSession(uuid);
            if (sd != null) {
                ChronoStore.getInstance().getSessionManager().startSession(uuid, sd.startTime(), sd.timePlayedToday(), minutes * 60);
            }
            Notifier.success(source.getSender(), "Limite de " + target.getName() + " definido para " + minutes + " minutos.");
            Notifier.actionBar(target, Component.text("⏱ Novo limite: " + Notifier.formatTime((long) minutes * 60)).color(NamedTextColor.AQUA));
        } catch (SQLException ex) {
            Notifier.error(source.getSender(), "Erro ao definir limite.");
            ex.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }
}
