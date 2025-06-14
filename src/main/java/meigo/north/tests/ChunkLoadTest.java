package meigo.north.tests;

import meigo.north.MeigoPlugin;
import meigo.north.utils.TpsUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ChunkLoadTest {

    private final MeigoPlugin plugin;
    private boolean isRunning = false;
    private final List<Location> simulatedPlayers = new ArrayList<>();
    private final List<BukkitTask> movementTasks = new ArrayList<>();
    private BukkitTask monitorTask;
    private final List<Double> tpsSamples = new ArrayList<>();
    private int chunksLoaded = 0;
    private final Set<Chunk> loadedChunks = new HashSet<>();

    public ChunkLoadTest(MeigoPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTest(Player player, int durationSeconds) {
        if (isRunning) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000A chunk load test is already running!"));
            return;
        }

        isRunning = true;
        simulatedPlayers.clear();
        movementTasks.clear();
        tpsSamples.clear();
        loadedChunks.clear();
        chunksLoaded = 0;

        player.sendMessage(MeigoPlugin.formatHexColor("&#FFA500Starting chunk load test for &#FFFF00" + durationSeconds + " seconds&#FFA500..."));
        player.sendMessage(MeigoPlugin.formatHexColor("&#FFD700Simulating 4 players running in different directions"));

        World world = player.getWorld();
        Location center = player.getLocation();

        // Create 4 simulated player positions
        simulatedPlayers.add(center.clone()); // North
        simulatedPlayers.add(center.clone()); // South
        simulatedPlayers.add(center.clone()); // East
        simulatedPlayers.add(center.clone()); // West

        // Movement directions
        double[][] directions = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        String[] directionNames = {"North", "South", "East", "West"};

        // Start movement tasks for each simulated player
        for (int i = 0; i < 4; i++) {
            final int index = i;
            final double[] direction = directions[i];
            final String dirName = directionNames[i];

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Location loc = simulatedPlayers.get(index);
                loc.add(direction[0] * 3, 0, direction[1] * 3); // Move 3 blocks per tick

                // Load chunk at new position
                Chunk chunk = loc.getChunk();
                if (!chunk.isLoaded()) {
                    chunk.load();
                }

                if (loadedChunks.add(chunk)) {
                    chunksLoaded++;
                }

                // Keep chunks loaded in a radius
                int radius = plugin.getTestsConfig().getChunkLoadRadius();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        Chunk nearbyChunk = world.getChunkAt(chunk.getX() + x, chunk.getZ() + z);
                        if (!nearbyChunk.isLoaded()) {
                            nearbyChunk.load();
                        }
                        if (loadedChunks.add(nearbyChunk)) {
                            chunksLoaded++;
                        }
                    }
                }
            }, 0L, 2L);

            movementTasks.add(task);
        }

        // Monitor task
        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double currentTps = TpsUtils.getTps()[0];
            tpsSamples.add(currentTps);

            String tpsColor = currentTps > 18 ? "&#00FF00" : (currentTps > 15 ? "&#FFFF00" : "&#FF0000");
            String message = MeigoPlugin.formatHexColor("&#FFD700Chunks Loaded: &#FFFFFF" + chunksLoaded +
                    " &#808080| &#FFD700TPS: " + tpsColor + String.format("%.2f", currentTps));

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        }, 0L, 20L);

        // Stop after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> stopTest(player, true), durationSeconds * 20L);
    }

    private void stopTest(Player player, boolean success) {
        if (!isRunning) return;

        isRunning = false;

        movementTasks.forEach(BukkitTask::cancel);
        movementTasks.clear();

        if (monitorTask != null) monitorTask.cancel();

        if (success) {
            double avgTps = tpsSamples.stream().mapToDouble(d -> d).average().orElse(20.0);

            player.sendMessage(" ");
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00===== Chunk Load Test Results ====="));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Total chunks loaded: &#FFFFFF" + chunksLoaded));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Average TPS: &#FFFFFF" + String.format("%.2f", avgTps)));
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00==================================="));

            // Play completion sound
            String soundName = plugin.getTestsConfig().getChunkTestSound();
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException e) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }
        }

        // Unload chunks after a delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            loadedChunks.forEach(chunk -> chunk.unload(true));
            loadedChunks.clear();
        }, 100L);
    }
}
