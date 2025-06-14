package meigo.north.commands;

import meigo.north.MeigoPlugin;
import meigo.north.SystemInfoManager;
import meigo.north.tests.*;
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
import java.util.Locale;
import java.util.UUID;

public class FastBenchmarkCommand implements CommandExecutor {

    private final MeigoPlugin plugin;
    private boolean isWorldGenRunning = false;
    private final EntityTest entityTest;
    private final ParticleTest particleTest;
    private final ChunkLoadTest chunkLoadTest;
    private final PlayersTest playersTest;

    public FastBenchmarkCommand(MeigoPlugin plugin) {
        this.plugin = plugin;
        this.entityTest = new EntityTest(plugin);
        this.particleTest = new ParticleTest(plugin);
        this.chunkLoadTest = new ChunkLoadTest(plugin);
        this.playersTest = new PlayersTest(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MeigoPlugin.formatHexColor("&#FF0000This command can only be used by a player."));
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("test")) {
                handleTestCommand(player, args);
                return true;
            } else if (args[0].equalsIgnoreCase("stats")) {
                handleStatsCommand(player);
                return true;
            } else if (args[0].equalsIgnoreCase("help")) {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Invalid page number."));
                        return true;
                    }
                }
                sendHelpMessage(player, label, page);
                return true;
            }
        }

        sendHelpMessage(player, label, 1);
        return true;
    }

    private void handleTestCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendHelpMessage(player, "fb", 1);
            return;
        }

        String testType = args[1];

        try {
            switch (testType.toLowerCase()) {
                case "worldgen":
                    runWorldGenTest(player);
                    break;

                case "entity":
                    if (args.length < 3) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Usage: /fb test entity <max_entities>"));
                        return;
                    }
                    int maxEntities = Integer.parseInt(args[2]);
                    if (maxEntities <= 0) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000The number of entities must be greater than 0."));
                        return;
                    }
                    entityTest.startTest(player, maxEntities);
                    break;

                case "particles":
                    if (args.length < 3) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Usage: /fb test particles <amount>"));
                        return;
                    }
                    int particleAmount = Integer.parseInt(args[2]);
                    if (particleAmount <= 0) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000The number of particles must be greater than 0."));
                        return;
                    }
                    particleTest.startTest(player, particleAmount);
                    break;

                case "chunksload":
                    if (args.length < 3) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Usage: /fb test chunksload <seconds>"));
                        return;
                    }
                    int seconds = Integer.parseInt(args[2]);
                    if (seconds <= 0) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Duration must be greater than 0 seconds."));
                        return;
                    }
                    chunkLoadTest.startTest(player, seconds);
                    break;

                case "players":
                    if (args.length < 4) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Usage: /fb test players <amount> <duration>"));
                        return;
                    }
                    int playerAmount = Integer.parseInt(args[2]);
                    int duration = Integer.parseInt(args[3]);
                    if (playerAmount <= 0 || duration <= 0) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Amount and duration must be greater than 0."));
                        return;
                    }
                    playersTest.startTest(player, playerAmount, duration);
                    break;

                default:
                    player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Unknown test type: " + testType));
                    sendHelpMessage(player, "fb", 2);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Invalid number format."));
        }
    }

    private void handleStatsCommand(Player player) {
        if (!plugin.isOshiAvailable()) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000OSHI-core library not found for /fb stats."));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000&lPossible reasons:"));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF00001. Your server core doesn't include oshi-core as a dependency."));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF00002. You're using an old Minecraft version without oshi-core."));
            return;
        }

        player.sendMessage(" ");
        player.spigot().sendMessage(createGradient("System Information", new Color(0x00BFFF), new Color(0x1E90FF)));
        player.sendMessage(" ");

        SystemInfoManager sysInfo = plugin.getSystemInfoManager();

        player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ CPU: &#FFFFFF" + sysInfo.getCpuName()));
        player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ Manufacturer: &#FFFFFF" + sysInfo.getCpuManufacturer()));
        player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ Frequency: &#FFFFFF" + sysInfo.getCpuFrequency()));
        player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ Threads: &#FFFFFF" + Runtime.getRuntime().availableProcessors()));

        if (sysInfo.isSwapEnabled()) {
            String swapUsed = formatBytes(sysInfo.getSwapUsed());
            String swapTotal = formatBytes(sysInfo.getSwapTotal());
            player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ SWAP: &#00FF00Enabled &#FFFFFF(" + swapUsed + " / " + swapTotal + ")"));
        } else {
            player.sendMessage(MeigoPlugin.formatHexColor("&#87CEEB▸ SWAP: &#FF0000Disabled"));
        }

        player.sendMessage(" ");
    }

    private void sendHelpMessage(Player player, String label, int page) {
        if (page < 1 || page > 3) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Invalid page number. Available pages: 1, 2, 3."));
            return;
        }

        player.sendMessage(" ");
        player.spigot().sendMessage(createGradient("FastBenchmark Help", new Color(0xFF8C00), new Color(0xFF4500)));
        player.sendMessage(" ");

        if (page == 1) {
            player.spigot().sendMessage(new ComponentBuilder("Main Commands (Page 1/3)")
                    .color(ChatColor.of("#e57373")).bold(true).create());
            player.spigot().sendMessage(createCommandHelp("/" + label + " help [page]", "Shows this help message"));
            player.spigot().sendMessage(createCommandHelp("/" + label + " stats", "Shows system information"));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test worldgen", "Runs world generation speed test"));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test entity <amount>", "Spawns entities to test server TPS"));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test particles <amount>", "Spawns particles to test performance"));
        } else if (page == 2) {
            player.spigot().sendMessage(new ComponentBuilder("Test Commands (Page 2/3)")
                    .color(ChatColor.of("#e57373")).bold(true).create());
            player.spigot().sendMessage(createCommandHelp("/" + label + " test chunksload <seconds>", "Simulates 4 players loading chunks"));
            player.spigot().sendMessage(createCommandHelp("/" + label + " test players <amount> <duration>", "Simulates player connections"));
            player.spigot().sendMessage(createCommandHelp("/speedtest", "Runs a network speed test"));
        } else if (page == 3) {
            player.spigot().sendMessage(new ComponentBuilder("Debug Tools (Page 3/3)")
                    .color(ChatColor.of("#e57373")).bold(true).create());
            player.spigot().sendMessage(createCommandHelp("/cpubar <player>", "Toggle CPU monitoring boss bar"));
            player.spigot().sendMessage(createCommandHelp("/rambar <player>", "Toggle RAM monitoring boss bar"));
            player.spigot().sendMessage(createCommandHelp("/noderambar <player>", "Toggle System RAM monitoring boss bar"));
            player.spigot().sendMessage(createCommandHelp("/addram <mb>", "Allocates specified amount of RAM"));
            player.spigot().sendMessage(createCommandHelp("/ramfree", "Frees the RAM allocated by /addram"));
        }
        player.sendMessage(" ");
    }

    private void runWorldGenTest(Player player) {
        if (isWorldGenRunning) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000A world generation benchmark is already in progress."));
            return;
        }
        isWorldGenRunning = true;

        player.sendMessage(MeigoPlugin.formatHexColor("&#FFA500Starting world generation benchmark..."));
        player.sendMessage(MeigoPlugin.formatHexColor("&#FFD700The server may freeze for a moment. This is expected."));

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
                    player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Error: World creation failed."));
                    isWorldGenRunning = false;
                    return;
                }

                player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00Benchmark finished!"));
                player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00World '&#FFFF00" + worldName +
                        "&#00FF00' generated in &#FFFF00" + resultMessage));

                // Play sound from config
                String soundName = plugin.getTestsConfig().getWorldGenSound();
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
                } catch (IllegalArgumentException e) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                        MeigoPlugin.formatHexColor("&#00FF00Test finished in &#FFFF00" + resultMessage)
                ));

                player.sendMessage(MeigoPlugin.formatHexColor("&#808080Now unloading and deleting the world..."));

                if (Bukkit.unloadWorld(benchmarkWorld, false)) {
                    player.sendMessage(MeigoPlugin.formatHexColor("&#808080World unloaded successfully. Deleting files..."));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                Path worldPath = benchmarkWorld.getWorldFolder().toPath();
                                Files.walk(worldPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00Temporary world files have been deleted."));
                                        isWorldGenRunning = false;
                                    }
                                }.runTask(plugin);
                            } catch (Exception e) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Error deleting world files: " + e.getMessage()));
                                        isWorldGenRunning = false;
                                    }
                                }.runTask(plugin);
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000Error: Could not unload benchmark world."));
                    isWorldGenRunning = false;
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private BaseComponent[] createCommandHelp(String command, String description) {
        return new ComponentBuilder(" ").append(command).color(ChatColor.of("#ffcc80"))
                .append(" - " + description).color(ChatColor.of("#ffab91")).create();
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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
