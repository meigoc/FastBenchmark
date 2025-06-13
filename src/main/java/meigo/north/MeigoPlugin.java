package meigo.north;

import com.sun.management.OperatingSystemMXBean;
import meigo.north.commands.AddRamCommand;
import meigo.north.commands.FastBenchmarkCommand;
import meigo.north.commands.RamFreeCommand;
import meigo.north.commands.SpeedTestCommand;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessHandle;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MeigoPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, BossBar> activeCpuBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeRamBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeNodeRamBars = new ConcurrentHashMap<>();

    private CpuMonitor cpuMonitor;
    private RamMonitor ramMonitor;
    private NodeRamMonitor nodeRamMonitor;
    private FileConfiguration barConfig;
    private boolean overrideRamCommand;

    private RamAllocator ramAllocator;

    @Override
    public void onEnable() {
        loadConfig();
        setupMonitors();
        setupCommands();
        Bukkit.getPluginManager().registerEvents(this, this);

        ramAllocator = new RamAllocator(this);

        File speedTestConfigFile = new File(getDataFolder(), "speedtest.yml");
        if (!speedTestConfigFile.exists()) {
            getConfig().options().copyDefaults(true);
            saveResource("speedtest.yml", false);
        }

        getCommand("addram").setExecutor(new AddRamCommand(ramAllocator));
        getCommand("ramfree").setExecutor(new RamFreeCommand(ramAllocator));
        getCommand("speedtest").setExecutor(new SpeedTestCommand(this));

        getCommand("fastbenchmark").setExecutor(new FastBenchmarkCommand(this));


        getLogger().info("FastBenchmark has been enabled!");
        String message = "§6§lJust a simple test message!";
        Player player = Bukkit.getPlayer("ExamplePlayer");


    }

    @Override
    public void onDisable() {
        if (cpuMonitor != null) cpuMonitor.stop();
        if (ramMonitor != null) ramMonitor.stop();
        if (nodeRamMonitor != null) nodeRamMonitor.stop();

        activeCpuBars.values().forEach(BossBar::removeAll);
        activeRamBars.values().forEach(BossBar::removeAll);
        activeNodeRamBars.values().forEach(BossBar::removeAll);

        activeCpuBars.clear();
        activeRamBars.clear();
        activeNodeRamBars.clear();

        if (ramAllocator != null) {
            ramAllocator.freeAllocatedRam();
        }

        getLogger().info("FastBenchmark has been disabled.");
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "bars.yml");
        if (!configFile.exists()) {
            saveResource("bars.yml", false);
        }
        barConfig = YamlConfiguration.loadConfiguration(configFile);
        overrideRamCommand = barConfig.getBoolean("rambar-override-command", true);
    }

    private void setupMonitors() {
        cpuMonitor = new CpuMonitor();
        ramMonitor = new RamMonitor();
        nodeRamMonitor = new NodeRamMonitor();
        cpuMonitor.start();
        ramMonitor.start();
        nodeRamMonitor.start();
    }

    private void setupCommands() {
        registerCommand("cpubar", new BarCommandExecutor(activeCpuBars, "cpubar"));
        registerCommand("noderambar", new BarCommandExecutor(activeNodeRamBars, "noderambar"));


        PluginCommand rambarPluginCommand = Bukkit.getPluginCommand("rambar");
        boolean isRamBarRegisteredByOther = rambarPluginCommand != null && rambarPluginCommand.getPlugin() != this;

        if (overrideRamCommand) {
            registerCommand("rambar", new BarCommandExecutor(activeRamBars, "rambar"));
            if (isRamBarRegisteredByOther) {
                getLogger().info("'/rambar' is registered by another plugin. Using high-priority event listener to handle it.");
            }
        } else {
            if (isRamBarRegisteredByOther) {
                registerCommand("realrambar", new BarCommandExecutor(activeRamBars, "rambar"));
                getLogger().info("'/rambar' is registered by another plugin. Registered fallback command '/realrambar'.");
            } else {
                registerCommand("rambar", new BarCommandExecutor(activeRamBars, "rambar"));
            }
        }
    }

    private void registerCommand(String name, BarCommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command '" + name + "' is not registered in plugin.yml! Please add it.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!overrideRamCommand) return;

        String[] parts = event.getMessage().substring(1).split(" ");
        if (parts.length > 0 && parts[0].equalsIgnoreCase("rambar")) {
            PluginCommand rambarCmd = Bukkit.getPluginCommand("rambar");
            if (rambarCmd != null && rambarCmd.getPlugin() != this) {
                event.setCancelled(true);
                String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                new BarCommandExecutor(activeRamBars, "rambar").onCommand(event.getPlayer(), getCommand("rambar"), "rambar", args);
            }
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (activeCpuBars.containsKey(uuid)) activeCpuBars.get(uuid).addPlayer(player);
        if (activeRamBars.containsKey(uuid)) activeRamBars.get(uuid).addPlayer(player);
        if (activeNodeRamBars.containsKey(uuid)) activeNodeRamBars.get(uuid).addPlayer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (activeCpuBars.containsKey(uuid)) activeCpuBars.get(uuid).removePlayer(player);
        if (activeRamBars.containsKey(uuid)) activeRamBars.get(uuid).removePlayer(player);
        if (activeNodeRamBars.containsKey(uuid)) activeNodeRamBars.get(uuid).removePlayer(player);
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private class BarCommandExecutor implements CommandExecutor, TabCompleter {
        private final Map<UUID, BossBar> activeBars;
        private final String barType;

        public BarCommandExecutor(Map<UUID, BossBar> activeBars, String barType) {
            this.activeBars = activeBars;
            this.barType = barType;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player|@a|@p|@r>");
                return true;
            }

            String targetArg = args[0];
            List<Player> targets = new ArrayList<>();
            if (targetArg.equalsIgnoreCase("@a")) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else if (targetArg.equalsIgnoreCase("@r")) {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!players.isEmpty()) targets.add(players.get(new Random().nextInt(players.size())));
            } else if (targetArg.equalsIgnoreCase("@p") && sender instanceof Player) {
                targets.add((Player) sender);
            } else {
                Player player = Bukkit.getPlayer(targetArg);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + targetArg);
                    return true;
                }
                targets.add(player);
            }

            if (targets.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No players matched the selector.");
                return true;
            }

            targets.forEach(p -> toggleBar(p, sender, command.getName()));
            return true;
        }

        private void toggleBar(Player player, CommandSender sender, String commandName) {
            UUID uuid = player.getUniqueId();
            if (activeBars.containsKey(uuid)) {
                Optional.ofNullable(activeBars.remove(uuid)).ifPresent(BossBar::removeAll);
                sender.sendMessage(color("&a" + barType + "bar toggled &cOFF &afor " + player.getName()));
            } else {
                BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
                bar.addPlayer(player);
                activeBars.put(uuid, bar);
                sender.sendMessage(color("&a" + barType + "bar toggled &aON &afor " + player.getName()));
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>(Arrays.asList("@a", "@p", "@r"));
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            }
            return Collections.emptyList();
        }
    }

    private abstract class Monitor {
        protected BukkitRunnable task;
        public abstract void start();
        public void stop() {
            if (task != null && !task.isCancelled()) task.cancel();
        }

        protected BarColor getBarColor(double percentage, String type) {
            String colorName;
            if (percentage >= barConfig.getDouble(type + ".thresholds.red", 90.0)) {
                colorName = barConfig.getString(type + ".colors.red", "RED");
            } else if (percentage >= barConfig.getDouble(type + ".thresholds.yellow", 70.0)) {
                colorName = barConfig.getString(type + ".colors.yellow", "YELLOW");
            } else {
                colorName = barConfig.getString(type + ".colors.default", "GREEN");
            }
            try {
                return BarColor.valueOf(colorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                return BarColor.GREEN;
            }
        }

        protected String getNumberColor(double percentage, String type) {
            String path = type + ".number-color-codes.";
            String colorCode;
            if (percentage >= barConfig.getDouble(type + ".thresholds.red", 90.0)) {
                colorCode = barConfig.getString(path + "high", "&c");
            } else if (percentage >= barConfig.getDouble(type + ".thresholds.yellow", 70.0)) {
                colorCode = barConfig.getString(path + "medium", "&e");
            } else {
                colorCode = barConfig.getString(path + "default", "&a");
            }
            return color(colorCode);
        }
    }

    private class CpuMonitor extends Monitor {
        private final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        private final int coreCount = Runtime.getRuntime().availableProcessors();
        private final double maxCpu = 100.0 * coreCount;

        @Override
        public void start() {
            long interval = barConfig.getLong("update-intervals.cpubar", 1000) / 50;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeCpuBars.isEmpty()) return;
                    double processCpuLoad = osBean.getProcessCpuLoad();
                    if (processCpuLoad < 0) processCpuLoad = 0;

                    double totalCpu = processCpuLoad * 100.0 * coreCount;
                    double percentageForColor = totalCpu / coreCount;
                    String numberColor = getNumberColor(percentageForColor, "cpubar");

                    String title = String.format(Locale.US, "§7CPU: %s%.1f%% §7/ %.1f%%", numberColor, totalCpu, maxCpu);
                    double progress = Math.min(1.0, totalCpu / maxCpu);
                    BarColor color = getBarColor(percentageForColor, "cpubar");

                    activeCpuBars.values().forEach(bar -> {
                        bar.setTitle(title);
                        bar.setProgress(progress);
                        bar.setColor(color);
                    });
                }
            };
            task.runTaskTimerAsynchronously(MeigoPlugin.this, 0L, interval);
        }
    }

    private class RamMonitor extends Monitor {
        private final boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
        private final long pid = ProcessHandle.current().pid();

        @Override
        public void start() {
            long interval = barConfig.getLong("update-intervals.rambar", 3000) / 50;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeRamBars.isEmpty()) return;

                    if (!isLinux) {
                        activeRamBars.values().forEach(bar -> {
                            bar.setTitle("§cProcess RAM monitor is only supported on Linux");
                            bar.setProgress(1.0);
                            bar.setColor(BarColor.RED);
                        });
                        return;
                    }

                    long usedMemory;
                    try {
                        usedMemory = getProcessRssBytes();
                    } catch (IOException | InterruptedException e) {
                        getLogger().warning("Failed to get process RAM: " + e.getMessage());
                        activeRamBars.values().forEach(bar -> {
                            bar.setTitle("§cError getting process RAM usage");
                            bar.setProgress(1.0);
                            bar.setColor(BarColor.RED);
                        });
                        return;
                    }

                    long maxMemory = Runtime.getRuntime().maxMemory();
                    double percentage = maxMemory > 0 ? ((double) usedMemory / maxMemory) * 100.0 : 0.0;
                    String numberColor = getNumberColor(percentage, "rambar");

                    String usedStr = formatBytes(usedMemory);
                    String maxStr = formatBytes(maxMemory);
                    String percStr = String.format(Locale.US, "%.1f", percentage);

                    String title = "§7Process RAM: " + numberColor + usedStr + " §7/ " + maxStr + " (" + numberColor + percStr + "%§7)";
                    double progress = Math.min(1.0, (double) usedMemory / maxMemory);
                    BarColor color = getBarColor(percentage, "rambar");

                    activeRamBars.values().forEach(bar -> {
                        bar.setTitle(title);
                        bar.setProgress(progress);
                        bar.setColor(color);
                    });
                }
            };
            task.runTaskTimerAsynchronously(MeigoPlugin.this, 0L, interval);
        }

        private long getProcessRssBytes() throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "rss", "--no-headers");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (process.waitFor() == 0 && line != null && !line.trim().isEmpty()) {
                    return Long.parseLong(line.trim()) * 1024;
                } else {
                    throw new IOException("ps command failed or returned empty output. Exit code: " + process.exitValue());
                }
            }
        }
    }

    private class NodeRamMonitor extends Monitor {
        private final SystemInfo systemInfo = new SystemInfo();

        @Override
        public void start() {
            long interval = barConfig.getLong("update-intervals.noderambar", 5000) / 50;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeNodeRamBars.isEmpty()) return;

                    GlobalMemory memory = systemInfo.getHardware().getMemory();
                    long total = memory.getTotal();
                    long used = total - memory.getAvailable();
                    double percentage = ((double) used / total) * 100.0;
                    String numberColor = getNumberColor(percentage, "noderambar");

                    String usedStr = formatBytes(used);
                    String totalStr = formatBytes(total);
                    String percStr = String.format(Locale.US, "%.1f", percentage);

                    String title = "§7System RAM: " + numberColor + usedStr + " §7/ " + totalStr + " (" + numberColor + percStr + "%§7)";
                    double progress = Math.min(1.0, percentage / 100.0);
                    BarColor color = getBarColor(percentage, "noderambar");

                    activeNodeRamBars.values().forEach(bar -> {
                        bar.setTitle(title);
                        bar.setProgress(progress);
                        bar.setColor(color);
                    });
                }
            };
            task.runTaskTimerAsynchronously(MeigoPlugin.this, 0L, interval);
        }
    }
}