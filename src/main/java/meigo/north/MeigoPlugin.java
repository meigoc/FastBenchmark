package meigo.north;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.management.OperatingSystemMXBean;

public class MeigoPlugin extends JavaPlugin {

    private Map<Player, BossBar> activeBars = new HashMap<>();
    private CpuMonitor cpuMonitor;

    @Override
    public void onEnable() {
        cpuMonitor = new CpuMonitor();
        cpuMonitor.start();
    }

    @Override
    public void onDisable() {
        if (cpuMonitor != null) {
            cpuMonitor.stop();
        }
        for (BossBar bar : activeBars.values()) {
            bar.removeAll();
        }
        activeBars.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cpubar")) {
            return false;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /cpubar <player>");
            return true;
        }

        String target = args[0];

        if (target.equals("@a")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                toggleCpuBar(player, sender);
            }
        } else if (target.equals("@r")) {
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            if (players.length > 0) {
                Player randomPlayer = players[(int) (Math.random() * players.length)];
                toggleCpuBar(randomPlayer, sender);
            }
        } else if (target.equals("@p")) {
            if (sender instanceof Player) {
                toggleCpuBar((Player) sender, sender);
            } else {
                sender.sendMessage("§cOnly players can use @p selector");
            }
        } else {
            Player player = Bukkit.getPlayer(target);
            if (player == null) {
                sender.sendMessage("§cPlayer not found: " + target);
                return true;
            }
            toggleCpuBar(player, sender);
        }

        return true;
    }

    private void toggleCpuBar(Player player, CommandSender sender) {
        if (activeBars.containsKey(player)) {
            BossBar bar = activeBars.remove(player);
            bar.removeAll();
            sender.sendMessage("§aCpubar toggled §cOFF §afor " + player.getName());
        } else {
            BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_20);
            bar.addPlayer(player);
            bar.setProgress(1.0);
            activeBars.put(player, bar);
            sender.sendMessage("§aCpubar toggled §aON §afor " + player.getName());
        }
    }

    private class CpuMonitor {
        private BukkitRunnable task;
        private OperatingSystemMXBean osBean;
        private ConcurrentLinkedQueue<Double> cpu1s = new ConcurrentLinkedQueue<>();
        private ConcurrentLinkedQueue<Double> cpu5s = new ConcurrentLinkedQueue<>();
        private ConcurrentLinkedQueue<Double> cpu60s = new ConcurrentLinkedQueue<>();

        public CpuMonitor() {
            osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        }

        public void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    double currentCpu = osBean.getProcessCpuLoad() * 100;
                    if (currentCpu < 0) currentCpu = 0;

                    cpu1s.offer(currentCpu);
                    cpu5s.offer(currentCpu);
                    cpu60s.offer(currentCpu);

                    while (cpu1s.size() > 20) cpu1s.poll();
                    while (cpu5s.size() > 100) cpu5s.poll();
                    while (cpu60s.size() > 1200) cpu60s.poll();

                    double avg1s = cpu1s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double avg5s = cpu5s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double avg60s = cpu60s.stream().mapToDouble(Double::doubleValue).average().orElse(0);

                    String text = String.format("§7CPU 1s: %s%.1f%% §7CPU 5s: %s%.1f%% §7CPU 60s: %s%.1f%%",
                            getColorCode(avg1s), avg1s,
                            getColorCode(avg5s), avg5s,
                            getColorCode(avg60s), avg60s);

                    double progress = Math.min(avg1s / 100.0, 1.0);
                    if (progress < 0) progress = 0;

                    for (BossBar bar : activeBars.values()) {
                        bar.setTitle(text);
                        bar.setProgress(progress);
                    }
                }
            };
            task.runTaskTimer(MeigoPlugin.this, 0L, 1L);
        }

        public void stop() {
            if (task != null) {
                task.cancel();
            }
        }

        private String getColorCode(double cpu) {
            if (cpu >= 90) return "§c";
            if (cpu >= 70) return "§e";
            return "§a";
        }
    }
}
