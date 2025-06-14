package meigo.north;

import com.sun.management.OperatingSystemMXBean;
import meigo.north.commands.*;
import meigo.north.config.TestsConfig;
import meigo.north.hooks.PlaceholderAPIHook;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.*;
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
    private TestsConfig testsConfig;
    private SystemInfoManager systemInfoManager;

    // Feature availability flags
    private boolean oshiAvailable = false;
    private boolean placeholderAPIAvailable = false;

    @Override
    public void onEnable() {
        // Check dependencies
        checkDependencies();

        loadConfig();
        setupMonitors();
        setupCommands();

        Bukkit.getPluginManager().registerEvents(this, this);

        ramAllocator = new RamAllocator(this);
        testsConfig = new TestsConfig(this);

        if (oshiAvailable) {
            systemInfoManager = new SystemInfoManager(this);
        }

        // Create config files
        saveDefaultConfigs();

        // Register PlaceholderAPI expansion if available
        if (placeholderAPIAvailable) {
            new PlaceholderAPIHook(this).register();
        }

        getLogger().info(formatHexColor("&#00FF00FastBenchmark v1.3 has been enabled!"));

        // Log feature availability
        if (!oshiAvailable) {
            getLogger().warning(formatHexColor("&#FF0000OSHI-core not found. /fb stats and /noderambar disabled."));
        }
        if (!placeholderAPIAvailable) {
            getLogger().info(formatHexColor("&#FFFF00PlaceholderAPI not found. Placeholders disabled."));
        }
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

        getLogger().info(formatHexColor("&#FF0000FastBenchmark has been disabled."));
    }

    private void checkDependencies() {
        // Check for OSHI
        try {
            Class.forName("oshi.SystemInfo");
            oshiAvailable = true;
        } catch (ClassNotFoundException e) {
            oshiAvailable = false;
        }

        // Check for PlaceholderAPI
        placeholderAPIAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private void saveDefaultConfigs() {
        File speedTestConfigFile = new File(getDataFolder(), "speedtest.yml");
        if (!speedTestConfigFile.exists()) {
            saveResource("speedtest.yml", false);
        }

        File testsConfigFile = new File(getDataFolder(), "tests.yml");
        if (!testsConfigFile.exists()) {
            saveResource("tests.yml", false);
        }
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
        cpuMonitor.start();
        ramMonitor.start();

        if (oshiAvailable) {
            nodeRamMonitor = new NodeRamMonitor();
            nodeRamMonitor.start();
        }
    }

    private void setupCommands() {
        registerCommand("cpubar", new BarCommandExecutor(activeCpuBars, "cpubar"));

        if (oshiAvailable) {
            registerCommand("noderambar", new BarCommandExecutor(activeNodeRamBars, "noderambar"));
        } else {
            registerCommand("noderambar", new DisabledCommandExecutor("noderambar"));
        }

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

        getCommand("addram").setExecutor(new AddRamCommand(ramAllocator));
        getCommand("ramfree").setExecutor(new RamFreeCommand(ramAllocator));
        getCommand("speedtest").setExecutor(new SpeedTestCommand(this));
        getCommand("fastbenchmark").setExecutor(new FastBenchmarkCommand(this));
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
        } else {
            getLogger().severe("Command '" + name + "' is not registered in plugin.yml! Please add it.");
        }
    }

    // Getters for other classes
    public boolean isOshiAvailable() {
        return oshiAvailable;
    }

    public boolean isPlaceholderAPIAvailable() {
        return placeholderAPIAvailable;
    }

    public TestsConfig getTestsConfig() {
        return testsConfig;
    }

    public SystemInfoManager getSystemInfoManager() {
        return systemInfoManager;
    }

    public Map<UUID, BossBar> getActiveCpuBars() {
        return activeCpuBars;
    }

    public Map<UUID, BossBar> getActiveRamBars() {
        return activeRamBars;
    }

    public Map<UUID, BossBar> getActiveNodeRamBars() {
        return activeNodeRamBars;
    }

    public static String formatHexColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text
                .replaceAll("&#([A-Fa-f0-9]{6})", "§x§$1")
                .replaceAll("§x§([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])",
                        "§x§$1§$2§$3§$4§$5§$6"));
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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // Command executor for disabled features
    private class DisabledCommandExecutor implements CommandExecutor {
        private final String feature;

        public DisabledCommandExecutor(String feature) {
            this.feature = feature;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            sender.sendMessage(formatHexColor("&#FF0000OSHI-core library not found for /" + feature + "."));
            sender.sendMessage(formatHexColor("&#FF0000&lPossible reasons:"));
            sender.sendMessage(formatHexColor("&#FF00001. Your server core doesn't include oshi-core as a dependency."));
            sender.sendMessage(formatHexColor("&#FF00002. You're using an old Minecraft version without oshi-core."));
            return true;
        }
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
                sender.sendMessage(formatHexColor("&#FF0000You do not have permission to use this command."));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(formatHexColor("&#FF0000Usage: /" + label + " <player|@a|@p|@r>"));
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
                    sender.sendMessage(formatHexColor("&#FF0000Player not found: " + targetArg));
                    return true;
                }
                targets.add(player);
            }

            if (targets.isEmpty()) {
                sender.sendMessage(formatHexColor("&#FF0000No players matched the selector."));
                return true;
            }

            targets.forEach(p -> toggleBar(p, sender, command.getName()));
            return true;
        }

        private void toggleBar(Player player, CommandSender sender, String commandName) {
            UUID uuid = player.getUniqueId();
            if (activeBars.containsKey(uuid)) {
                Optional.ofNullable(activeBars.remove(uuid)).ifPresent(BossBar::removeAll);
                sender.sendMessage(formatHexColor("&#00FF00" + barType + " toggled &#FF0000OFF &#00FF00for " + player.getName()));
            } else {
                BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
                bar.addPlayer(player);
                activeBars.put(uuid, bar);
                sender.sendMessage(formatHexColor("&#00FF00" + barType + " toggled &#00FF00ON &#00FF00for " + player.getName()));
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
                colorCode = barConfig.getString(path + "high", "&#FF0000");
            } else if (percentage >= barConfig.getDouble(type + ".thresholds.yellow", 70.0)) {
                colorCode = barConfig.getString(path + "medium", "&#FFFF00");
            } else {
                colorCode = barConfig.getString(path + "default", "&#00FF00");
            }
            return formatHexColor(colorCode);
        }
    }

    public class CpuMonitor extends Monitor {
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

                    String title = String.format(Locale.US, formatHexColor("&#808080CPU: %s%.1f%% &#808080/ %.1f%%"),
                            numberColor, totalCpu, maxCpu);
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

        public double getCpuLoad() {
            return osBean.getProcessCpuLoad() * 100.0 * coreCount;
        }

        public double getMaxCpuLoad() {
            return maxCpu;
        }

        public int getCpuThreads() {
            return coreCount;
        }
    }

    public class RamMonitor extends Monitor {
        private final boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
        private final long pid = ProcessHandle.current().pid();
        private long lastUsedMemory = 0;
        private long lastMaxMemory = 0;

        @Override
        public void start() {
            long interval = barConfig.getLong("update-intervals.rambar", 3000) / 50;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeRamBars.isEmpty() && !placeholderAPIAvailable) return;

                    if (!isLinux) {
                        lastUsedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                        lastMaxMemory = Runtime.getRuntime().maxMemory();

                        if (!activeRamBars.isEmpty()) {
                            activeRamBars.values().forEach(bar -> {
                                bar.setTitle(formatHexColor("&#FF0000Process RAM monitor is only supported on Linux"));
                                bar.setProgress(1.0);
                                bar.setColor(BarColor.RED);
                            });
                        }
                        return;
                    }

                    try {
                        lastUsedMemory = getProcessRssBytes();
                        lastMaxMemory = Runtime.getRuntime().maxMemory();
                    } catch (IOException | InterruptedException e) {
                        getLogger().warning("Failed to get process RAM: " + e.getMessage());
                        if (!activeRamBars.isEmpty()) {
                            activeRamBars.values().forEach(bar -> {
                                bar.setTitle(formatHexColor("&#FF0000Error getting process RAM usage"));
                                bar.setProgress(1.0);
                                bar.setColor(BarColor.RED);
                            });
                        }
                        return;
                    }

                    double percentage = lastMaxMemory > 0 ? ((double) lastUsedMemory / lastMaxMemory) * 100.0 : 0.0;
                    String numberColor = getNumberColor(percentage, "rambar");

                    String usedStr = formatBytes(lastUsedMemory);
                    String maxStr = formatBytes(lastMaxMemory);
                    String percStr = String.format(Locale.US, "%.1f", percentage);

                    String title = formatHexColor("&#808080Process RAM: " + numberColor + usedStr +
                            " &#808080/ " + maxStr + " (" + numberColor + percStr + "%&#808080)");
                    double progress = Math.min(1.0, (double) lastUsedMemory / lastMaxMemory);
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

        public long getUsedMemory() {
            return lastUsedMemory;
        }

        public long getMaxMemory() {
            return lastMaxMemory;
        }
    }

    public class NodeRamMonitor extends Monitor {
        private oshi.SystemInfo systemInfo;
        private long lastUsedMemory = 0;
        private long lastTotalMemory = 0;

        public NodeRamMonitor() {
            if (oshiAvailable) {
                try {
                    systemInfo = new oshi.SystemInfo();
                } catch (Exception e) {
                    getLogger().severe("Failed to initialize OSHI SystemInfo: " + e.getMessage());
                }
            }
        }

        @Override
        public void start() {
            if (!oshiAvailable || systemInfo == null) return;

            long interval = barConfig.getLong("update-intervals.noderambar", 5000) / 50;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeNodeRamBars.isEmpty() && !placeholderAPIAvailable) return;

                    try {
                        oshi.hardware.GlobalMemory memory = systemInfo.getHardware().getMemory();
                        lastTotalMemory = memory.getTotal();
                        lastUsedMemory = lastTotalMemory - memory.getAvailable();
                        double percentage = ((double) lastUsedMemory / lastTotalMemory) * 100.0;
                        String numberColor = getNumberColor(percentage, "noderambar");

                        String usedStr = formatBytes(lastUsedMemory);
                        String totalStr = formatBytes(lastTotalMemory);
                        String percStr = String.format(Locale.US, "%.1f", percentage);

                        String title = formatHexColor("&#808080System RAM: " + numberColor + usedStr +
                                " &#808080/ " + totalStr + " (" + numberColor + percStr + "%&#808080)");
                        double progress = Math.min(1.0, percentage / 100.0);
                        BarColor color = getBarColor(percentage, "noderambar");

                        activeNodeRamBars.values().forEach(bar -> {
                            bar.setTitle(title);
                            bar.setProgress(progress);
                            bar.setColor(color);
                        });
                    } catch (Exception e) {
                        getLogger().warning("Error updating node RAM monitor: " + e.getMessage());
                    }
                }
            };
            task.runTaskTimerAsynchronously(MeigoPlugin.this, 0L, interval);
        }

        public long getUsedMemory() {
            return lastUsedMemory;
        }

        public long getTotalMemory() {
            return lastTotalMemory;
        }
    }

    // Getters for monitors
    public CpuMonitor getCpuMonitor() {
        return cpuMonitor;
    }

    public RamMonitor getRamMonitor() {
        return ramMonitor;
    }

    public NodeRamMonitor getNodeRamMonitor() {
        return nodeRamMonitor;
    }
}
