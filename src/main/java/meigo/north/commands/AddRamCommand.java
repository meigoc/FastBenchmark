package meigo.north.commands;

import meigo.north.RamAllocator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class AddRamCommand implements CommandExecutor {

    private final RamAllocator ramAllocator;

    public AddRamCommand(RamAllocator ramAllocator) {
        this.ramAllocator = ramAllocator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /addram <megabytes>");
            return true;
        }

        int megabytes;
        try {
            megabytes = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number for megabytes. Please enter a whole number.");
            return true;
        }

        if (megabytes <= 0) {
            player.sendMessage(ChatColor.RED + "Megabytes must be a positive number.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Attempting to allocate " + megabytes + " MB of RAM...");
        boolean success = ramAllocator.allocateRam(megabytes);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Successfully allocated " + megabytes + " MB. Total allocated: " + ramAllocator.getTotalAllocatedMegabytes() + " MB.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to allocate " + megabytes + " MB. Check server console for errors.");
        }

        return true;
    }
}