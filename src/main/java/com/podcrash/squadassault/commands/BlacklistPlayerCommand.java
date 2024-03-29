package com.podcrash.squadassault.commands;

import com.podcrash.squadassault.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class BlacklistPlayerCommand extends HostCommand {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if(!checkPermission(commandSender,command, s, args)) {
            return true;
        }

        Main.getSAConfig().getBlacklistedPlayers().add(args[0].toLowerCase());
        Main.getSAConfig().getConfig().set("BannedPlayers", Main.getSAConfig().getBlacklistedPlayers());
        Bukkit.broadcastMessage(ChatColor.YELLOW + args[0] + ChatColor.AQUA + " has been added as a blacklisted player.");
        return true;
    }
}
