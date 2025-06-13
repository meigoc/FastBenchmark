package meigo.north.tests;

import meigo.north.MeigoPlugin;
import meigo.north.utils.TpsUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EntityTest implements Listener {

    private final MeigoPlugin plugin;
    private boolean isRunning = false;

    private Player testPlayer;
    private UUID testPlayerId;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final List<Double> tpsSamples = new ArrayList<>();
    private final List<Integer> pingSamples = new ArrayList<>();

    private BukkitTask mainTask;
    private BukkitTask spawnerTask;
    private BukkitTask monitorTask;

    private static final int TEST_DURATION_SECONDS = 30;
    private static final EntityType[] MOB_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN
    };

    public EntityTest(MeigoPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTest(Player player, int maxEntities) {
        if (isRunning) {
            player.sendMessage(ChatColor.RED + "An entity benchmark is already in progress.");
            return;
        }

        this.isRunning = true;
        this.testPlayer = player;
        this.testPlayerId = player.getUniqueId();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        player.sendMessage(ChatColor.GOLD + "Starting entity benchmark for " + TEST_DURATION_SECONDS + " seconds...");

        spawnerTask = new BukkitRunnable() {
            final int spawnRadius = 160;
            @Override
            public void run() {
                if (spawnedMobs.size() >= maxEntities) {
                    this.cancel();
                    return;
                }

                World world = player.getWorld();
                Random random = ThreadLocalRandom.current();

                for (int i = 0; i < 20; i++) {
                    if (spawnedMobs.size() >= maxEntities) break;

                    Location loc = player.getLocation().add(random.nextInt(spawnRadius*2) - spawnRadius, 0, random.nextInt(spawnRadius*2) - spawnRadius);
                    Location spawnLoc = world.getHighestBlockAt(loc).getLocation().add(0, 1, 0);

                    if (!spawnLoc.getChunk().isLoaded()) continue;

                    EntityType type = MOB_TYPES[random.nextInt(MOB_TYPES.length)];
                    Entity spawned = world.spawnEntity(spawnLoc, type);

                    spawned.setInvulnerable(true);
                    spawned.setPersistent(false);
                    if (spawned instanceof Ageable) {
                        ((Ageable) spawned).setAdult();
                    }
                    spawnedMobs.add(spawned);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        monitorTask = new BukkitRunnable() {
            int secondsLeft = TEST_DURATION_SECONDS;
            @Override
            public void run() {
                double currentTps = TpsUtils.getTps()[0];
                tpsSamples.add(currentTps);

                int avgPing = (int) Bukkit.getOnlinePlayers().stream().mapToInt(Player::getPing).average().orElse(0);
                pingSamples.add(avgPing);

                String tpsColor = currentTps > 18 ? "§a" : (currentTps > 15 ? "§e" : "§c");
                String message = String.format("§eTime: §f%ds §8| §eTPS: %s%.2f §8| §eAvg Ping: §f%dms",
                        secondsLeft, tpsColor, currentTps, avgPing);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));

                if (--secondsLeft < 0) {
                    stopTest(true);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isRunning) stopTest(true);
            }
        }.runTaskLater(plugin, TEST_DURATION_SECONDS * 20L);
    }

    public void stopTest(boolean graceful) {
        if (!isRunning) return;

        if (mainTask != null) mainTask.cancel();
        if (spawnerTask != null) spawnerTask.cancel();
        if (monitorTask != null) monitorTask.cancel();

        spawnedMobs.forEach(Entity::remove);
        spawnedMobs.clear();

        HandlerList.unregisterAll(this);

        if (graceful && testPlayer != null && testPlayer.isOnline()) {
            double avgTps = tpsSamples.stream().mapToDouble(d -> d).average().orElse(0);
            double avgPing = pingSamples.stream().mapToInt(i -> i).average().orElse(0);

            testPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§aTest finished!"));
            testPlayer.sendMessage(ChatColor.GREEN + "===== Entity Benchmark Results =====");
            testPlayer.sendMessage(String.format(ChatColor.YELLOW + "Average TPS: %.2f", avgTps));
            testPlayer.sendMessage(String.format(ChatColor.YELLOW + "Average Ping (all players): %.0fms", avgPing));
            testPlayer.sendMessage(ChatColor.GREEN + "================================");
            testPlayer.playSound(testPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        } else if (testPlayer != null && testPlayer.isOnline()) {
            testPlayer.sendMessage(ChatColor.RED + "Entity benchmark was stopped prematurely.");
        }

        tpsSamples.clear();
        pingSamples.clear();
        this.isRunning = false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(testPlayerId)) {
            stopTest(false);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (spawnedMobs.contains(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN && spawnedMobs.contains(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}