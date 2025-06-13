package meigo.north.commands;

import meigo.north.RamAllocator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class RamFreeCommand implements CommandExecutor {

    private final RamAllocator ramAllocator;

    public RamFreeCommand(RamAllocator ramAllocator) {
        this.ramAllocator = ramAllocator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        player.sendMessage(ChatColor.YELLOW + "Attempting to free all manually allocated RAM...");
        long previousAllocated = ramAllocator.getTotalAllocatedMegabytes();
        ramAllocator.freeAllocatedRam();

        if (previousAllocated > 0) {
            player.sendMessage(ChatColor.GREEN + "Successfully freed " + previousAllocated + " MB of RAM.");
            player.sendMessage(ChatColor.GREEN + "Current total allocated: " + ramAllocator.getTotalAllocatedMegabytes() + " MB.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "No RAM was manually allocated to free.");
        }

        return true;
    }
}