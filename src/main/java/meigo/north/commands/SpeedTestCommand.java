package meigo.north.commands;

import meigo.north.MeigoPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DecimalFormat;

public class SpeedTestCommand implements CommandExecutor {

    private final MeigoPlugin plugin;
    private final FileConfiguration speedTestConfig;
    private final String downloadUrl;
    private final String tempFolderName;
    private static final DecimalFormat df = new DecimalFormat("0.00");

    public SpeedTestCommand(MeigoPlugin plugin) {
        this.plugin = plugin;
        File configFile = new File(plugin.getDataFolder(), "speedtest.yml");
        this.speedTestConfig = YamlConfiguration.loadConfiguration(configFile);
        this.downloadUrl = speedTestConfig.getString("download-url", "https://speedtest.selectel.ru/10GB");
        this.tempFolderName = speedTestConfig.getString("temp-folder", "tmp");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("fastbenchmark.speedtest")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Starting network speed test... This may take a few seconds.");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(downloadUrl);
                long ping = getPing(url.getHost());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(formatMessage("&ePing to " + url.getHost() + ": &a" + ping + " ms")));

                performDownloadTest(player, url);

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "Error during speed test: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        return true;
    }

    private long getPing(String host) {
        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 80), 1000); // 1 second timeout
            return System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            return -1; // -1 means error
        }
    }

    private void performDownloadTest(Player player, URL url) {
        File tempDir = new File(plugin.getDataFolder(), tempFolderName);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempFile = new File(tempDir, "speedtest_temp_file.tmp");
        final long startTime = System.currentTimeMillis();
        final long timeLimit = 5000; // 5 seconds

        final long[] bytesDownloaded = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > timeLimit + 500) { // +500ms for closing
                    this.cancel();
                    return;
                }

                double seconds = elapsedTime / 1000.0;
                if (seconds > 0) {
                    double speed = (bytesDownloaded[0] / 1024.0 / 1024.0) / seconds; // MB/s
                    String actionBarMessage = formatMessage(
                            "&fDownloading: &b" + df.format(speed) + " MB/s" +
                                    " &7| &fTime: &e" + (int)(seconds) + "/5s"
                    );
                    sendActionBar(player, actionBarMessage);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 5L);

        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    bytesDownloaded[0] += bytesRead;

                    if (System.currentTimeMillis() - startTime > timeLimit) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "Failed to complete the download test: " + e.getMessage()));
            e.printStackTrace();
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = Math.min(totalTime, timeLimit) / 1000.0;
            double finalSpeed = (bytesDownloaded[0] / 1024.0 / 1024.0) / totalSeconds;

            Bukkit.getScheduler().runTask(plugin, () -> {
                sendActionBar(player, formatMessage("&a✔ Test complete!"));
                player.sendMessage(formatMessage("&e--- SpeedTest Results ---"));
                player.sendMessage(formatMessage("&fDownload speed: &a" + df.format(finalSpeed) + " &2MB/s"));
                player.sendMessage(formatMessage("&fUpload speed: &7not tested"));
                player.sendMessage(formatMessage("&e--------------------------"));
            });

            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Exception e) {
            // nothing :3
        }
    }

    private String formatMessage(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}