package dev.soranzo;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class ChronoCommand implements CommandExecutor {
    private final Database db = Database.getInstance();


    private PlayerData getData(UUID uuid) {
        PlayerData pd = null;
        try {
           pd = db.getPlayerData(uuid);
        } catch (SQLException ex){
            ex.printStackTrace();
        }

        return pd;
    }

    private Player getTarget(CommandSender cs, String[] args, int index) {
        if (args.length <= index) {
            cs.sendMessage("§cUso incorreto do comando.");
            return null;
        }

        Player target = Bukkit.getPlayer(args[index]);
        if (target == null){
            cs.sendMessage("§cPlayer não encontrado ou offline.");
            return null;
        }
        return target;
    }


    @Override
    public boolean onCommand(CommandSender cs, Command c, String a1, String[] ap) {
       if (ap.length == 0) return false;

       String action = ap[0].toLowerCase();
       Player target;
       UUID target_uuid;
       PlayerData target_data;

       switch(action) {
           case "add":
               target = getTarget(cs, ap, 1);
               if (target == null) return true;

               target_uuid = target.getUniqueId();
               target_data = getData(target_uuid);

               if (target_data == null) {
                   cs.sendMessage("§cPlayer não registrado.");
                   return true;
               }

               if (target_data.monitored()) {
                   cs.sendMessage("§cO jogador já está sendo monitorado");
                   return true;
               }

               try {
                   db.monitorPlayer(target_uuid, true);
               } catch (SQLException ex) {
                   cs.sendMessage("§cErro ao monitorar jogador");
               }
               return true;

           case "remove":
               target = getTarget(cs, ap, 1);
               if (target == null) return true;

               target_uuid = target.getUniqueId();
               target_data = getData(target_uuid);

               if (target_data == null) {
                   cs.sendMessage("§cPlayer não registrado.");
                   return true;
               }

               if (!target_data.monitored()) {
                   cs.sendMessage("§cO jogador não está sendo monitorado");
                   return true;
               }

               try {
                   db.monitorPlayer(target_uuid, false);
               } catch (SQLException ex) {
                   cs.sendMessage("§cErro ao remover monitoramento do jogador");
               }
               return true;

           case "tbsp":
               if (ap.length < 3) {
                   cs.sendMessage("§cUso: /chrono tbsp <nick> <minutos>");
                   return true;
               }
               target = getTarget(cs, ap, 1);
               if (target == null) return true;

               target_uuid = target.getUniqueId();
               target_data = getData(target_uuid);

               if (target_data == null) {
                   cs.sendMessage("§cPlayer não registrado.");
                   return true;
               }


               try {
                   long tbsp = Long.parseLong(ap[2]) * 60;
                   db.setTbsp(target_uuid, tbsp);
               } catch (SQLException ex){
                   cs.sendMessage("§cErro ao setar tbsp.");
               } catch (NumberFormatException ex) {
                   cs.sendMessage("§cUso: /chrono tbsp <nick> <minutos>");
               }
               return true;

           default:
               return false;
       }
    }
}
