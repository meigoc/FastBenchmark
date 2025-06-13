package meigo.north.commands;

import meigo.north.MeigoPlugin; // Замени на твой главный класс плагина
import meigo.north.tests.EntityTest; // Импортируем новый класс для теста
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

public class FastBenchmarkCommand implements CommandExecutor {

    private final MeigoPlugin plugin;
    private boolean isWorldGenRunning = false;
    private final EntityTest entityTest; // Экземпляр теста сущностей

    public FastBenchmarkCommand(MeigoPlugin plugin) {
        this.plugin = plugin;
        this.entityTest = new EntityTest(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            handleTestCommand(player, args);
            return true;
        }

        // Обработка команды /fb help с пагинацией
        int page = 1;
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid page number.");
                    return true;
                }
            }
        }
        sendHelpMessage(player, label, page);
        return true;
    }

    private void handleTestCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendHelpMessage(player, "fb", 1);
            return;
        }

        String testType = args[1];
        if (testType.equalsIgnoreCase("worldgen")) {
            runWorldGenTest(player);
        } else if (testType.equalsIgnoreCase("entity")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /fb test entity <max_entities>");
                return;
            }
            try {
                int maxEntities = Integer.parseInt(args[2]);
                if (maxEntities <= 0) {
                    player.sendMessage(ChatColor.RED + "The number of entities must be greater than 0.");
                    return;
                }
                entityTest.startTest(player, maxEntities);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number of entities.");
            }
        }
    }

    private void sendHelpMessage(Player player, String label, int page) {
        if (page < 1 || page > 2) {
            player.sendMessage(ChatColor.RED + "Invalid page number. Available pages: 1, 2.");
            return;
        }

        player.sendMessage(" ");
        player.spigot().sendMessage(createGradient("FastBenchmark Help", new Color(0xFF8C00), new Color(0xFF4500)));
        player.sendMessage(" ");

        if (page == 1) {
            player.spigot().sendMessage(new ComponentBuilder("Main Commands (Page 1/2)")
                    .color(ChatColor.of("#e57373")).bold(true).create());
            player.spigot().sendMessage(createCommandHelp("/" + label + " help [page]", "- Shows this help message."));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test worldgen", "- Runs a world generation speed test."));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test entity <amount>", "- Spawns entities to test server TPS."));
        } else if (page == 2) {
            player.spigot().sendMessage(new ComponentBuilder("Debug Tools (Page 2/2)")
                    .color(ChatColor.of("#e57373")).bold(true).create());
            player.spigot().sendMessage(createCommandHelp("/speedtest", "- Runs a network speed test."));
            player.spigot().sendMessage(createCommandHelp("/cpubar <player>", "- Toggle CPU monitoring boss bar."));
            player.spigot().sendMessage(createCommandHelp("/rambar <player>", "- Toggle RAM monitoring boss bar."));
            player.spigot().sendMessage(createCommandHelp("/noderambar <player>", "- Toggle System RAM monitoring boss bar."));
            player.spigot().sendMessage(createCommandHelp("/addram <mb>", "- Allocates a specified amount of RAM."));
            player.spigot().sendMessage(createCommandHelp("/ramfree", "- Frees the RAM allocated by /addram."));
        }
        player.sendMessage(" ");
    }

    private void runWorldGenTest(Player player) {
        if (isWorldGenRunning) {
            player.sendMessage(ChatColor.RED + "A world generation benchmark is already in progress.");
            return;
        }
        isWorldGenRunning = true;

        player.sendMessage(ChatColor.of("#ffab91") + "Starting world generation benchmark...");
        player.sendMessage(ChatColor.GOLD + "The server may freeze for a moment. This is expected.");

        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                String worldName = "benchmark-" + UUID.randomUUID().toString().substring(0, 12);

                WorldCreator wc = new WorldCreator(worldName).environment(World.Environment.NORMAL).type(WorldType.NORMAL);
                World benchmarkWorld = Bukkit.createWorld(wc);

                long duration = System.currentTimeMillis() - startTime;
                String resultMessage = String.format("%.3f seconds", duration / 1000.0);

                if (benchmarkWorld == null) {
                    player.sendMessage(ChatColor.RED + "Error: World creation failed.");
                    isWorldGenRunning = false;
                    return;
                }

                player.sendMessage(ChatColor.of("#90EE90") + "Benchmark finished!");
                player.sendMessage(ChatColor.GREEN + "World '" + worldName + "' generated in " + ChatColor.YELLOW + resultMessage);

                // ИЗМЕНЕНО: Добавляем звук и ActionBar
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                        ChatColor.GREEN + "Test finished in " + ChatColor.YELLOW + resultMessage
                ));

                player.sendMessage(ChatColor.GRAY + "Now unloading and deleting the world...");

                // Процесс выгрузки и удаления
                if (Bukkit.unloadWorld(benchmarkWorld, false)) {
                    player.sendMessage(ChatColor.GRAY + "World unloaded successfully. Deleting files...");
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                Path worldPath = benchmarkWorld.getWorldFolder().toPath();
                                Files.walk(worldPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.sendMessage(ChatColor.GREEN + "Temporary world files have been deleted.");
                                        isWorldGenRunning = false;
                                    }
                                }.runTask(plugin);
                            } catch (Exception e) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.sendMessage(ChatColor.RED + "Error deleting world files: " + e.getMessage());
                                        isWorldGenRunning = false;
                                    }
                                }.runTask(plugin);
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    player.sendMessage(ChatColor.RED + "Error: Could not unload benchmark world.");
                    isWorldGenRunning = false;
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private BaseComponent[] createCommandHelp(String command, String description) {
        return new ComponentBuilder(" ").append(command).color(ChatColor.of("#ffcc80"))
                .append(" " + description).color(ChatColor.of("#ffab91")).create();
    }

    private BaseComponent[] createGradient(String text, Color start, Color end) {
        ComponentBuilder builder = new ComponentBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            double ratio = (length == 1) ? 0.5 : (double) i / (length - 1);
            int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
            int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
            int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
            builder.append(String.valueOf(text.charAt(i)))
                    .color(ChatColor.of(new Color(red, green, blue)));
        }
        return builder.create();
    }
}