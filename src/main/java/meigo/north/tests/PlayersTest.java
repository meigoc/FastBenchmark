package meigo.north.tests;

import meigo.north.MeigoPlugin;
import meigo.north.utils.TpsUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PlayersTest {

    private final MeigoPlugin plugin;
    private boolean isRunning = false;
    private final List<Player> fakePlayers = new ArrayList<>();
    private BukkitTask spawnTask;
    private BukkitTask movementTask;
    private BukkitTask monitorTask;
    private final List<Double> tpsSamples = new ArrayList<>();
    private int targetPlayers;
    private int currentPlayers = 0;

    public PlayersTest(MeigoPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTest(Player player, int playerAmount, int duration) {
        if (isRunning) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000A players test is already running!"));
            return;
        }

        isRunning = true;
        targetPlayers = playerAmount;
        currentPlayers = 0;
        fakePlayers.clear();
        tpsSamples.clear();

        player.sendMessage(MeigoPlugin.formatHexColor("&#FFA500Starting players test with &#FFFF00" + playerAmount +
                " &#FFA500players for &#FFFF00" + duration + " seconds&#FFA500..."));

        World world = player.getWorld();
        Location spawnLocation = player.getLocation();

        // Spawn players gradually
        int playersPerSecond = Math.max(1, playerAmount / 10);

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentPlayers >= targetPlayers) {
                if (spawnTask != null) spawnTask.cancel();
                return;
            }

            for (int i = 0; i < playersPerSecond && currentPlayers < targetPlayers; i++) {
                try {
                    // Create fake player using NMS
                    String playerName = "TestPlayer" + currentPlayers;
                    Location loc = getRandomLocation(spawnLocation, 50);

                    // Note: This is a simplified version. In reality, creating fake players
                    // requires NMS and is version-specific. For now, we'll simulate with messages
                    currentPlayers++;

                    // Simulate player join message
                    if (currentPlayers % 10 == 0) {
                        player.sendMessage(MeigoPlugin.formatHexColor("&#808080Simulated " + currentPlayers + " players joined..."));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to create fake player: " + e.getMessage());
                }
            }
        }, 0L, 20L);

        // Simulate player movement
        movementTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Simulate players moving around
            Random random = new Random();

            // Create particle effects to visualize simulated players
            for (int i = 0; i < Math.min(currentPlayers, 50); i++) {
                Location particleLoc = getRandomLocation(spawnLocation, 100);
                world.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1);
            }
        }, 0L, 10L);

        // Monitor task
        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double currentTps = TpsUtils.getTps()[0];
            tpsSamples.add(currentTps);

            String tpsColor = currentTps > 18 ? "&#00FF00" : (currentTps > 15 ? "&#FFFF00" : "&#FF0000");
            String message = MeigoPlugin.formatHexColor("&#FFD700Simulated Players: &#FFFFFF" + currentPlayers + "/" + targetPlayers +
                    " &#808080| &#FFD700TPS: " + tpsColor + String.format("%.2f", currentTps));

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        }, 0L, 20L);

        // Stop after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> stopTest(player, true), duration * 20L);
    }

    private void stopTest(Player player, boolean success) {
        if (!isRunning) return;

        isRunning = false;

        if (spawnTask != null) spawnTask.cancel();
        if (movementTask != null) movementTask.cancel();
        if (monitorTask != null) monitorTask.cancel();

        // Clean up fake players
        fakePlayers.forEach(fakePlayer -> {
            try {
                // Remove fake player
            } catch (Exception e) {
                // Ignore
            }
        });
        fakePlayers.clear();

        if (success) {
            double avgTps = tpsSamples.stream().mapToDouble(d -> d).average().orElse(20.0);

            player.sendMessage(" ");
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00===== Players Test Results ====="));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Simulated players: &#FFFFFF" + currentPlayers));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Average TPS: &#FFFFFF" + String.format("%.2f", avgTps)));
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00================================"));

            // Play completion sound
            String soundName = plugin.getTestsConfig().getPlayersTestSound();
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException e) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }
        }

        currentPlayers = 0;
    }

    private Location getRandomLocation(Location center, int radius) {
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * radius;

        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(center.getWorld(), x, y, z);
    }
}
