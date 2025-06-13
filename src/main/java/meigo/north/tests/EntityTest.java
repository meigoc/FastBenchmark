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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EntityTest implements Listener {

    private final MeigoPlugin plugin;
    private boolean isRunning = false;

    private UUID testPlayerId;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final List<Double> tpsSamples = new ArrayList<>();
    private final List<Integer> pingSamples = new ArrayList<>();

    private BukkitTask mainTask;
    private BukkitTask spawnerTask;
    private BukkitTask monitorTask;

    private static final int TEST_DURATION_SECONDS = 30;
    private static final int SPAWN_CHUNK_RADIUS = 5;
    private static final int MAX_SPAWNS_PER_TICK = 250;

    private static final EntityType[] MOB_TYPES = {
            // Hostile Mobs
            EntityType.WARDEN, EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.CREEPER, EntityType.SPIDER, EntityType.ENDERMAN, EntityType.SLIME, EntityType.WITCH,
            EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.STRAY, EntityType.DROWNED, EntityType.CAVE_SPIDER,
            EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN,
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.RAVAGER, EntityType.VEX,
            EntityType.PHANTOM, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.WITHER_SKELETON, EntityType.SHULKER,

            // Neutral Mobs
            EntityType.IRON_GOLEM, EntityType.WOLF, EntityType.POLAR_BEAR, EntityType.PANDA, EntityType.GOAT,
            EntityType.BEE, EntityType.DOLPHIN, EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.PUFFERFISH,

            // Passive Mobs
            EntityType.ALLAY, EntityType.SNIFFER, EntityType.CAMEL, EntityType.FROG,
            EntityType.TADPOLE, EntityType.AXOLOTL, EntityType.GLOW_SQUID, EntityType.PIG, EntityType.COW,
            EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.RABBIT, EntityType.BAT,
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.CAT, EntityType.OCELOT, EntityType.FOX,
            EntityType.TURTLE, EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.SQUID,
            EntityType.STRIDER
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
        this.testPlayerId = player.getUniqueId();
        clearPreviousData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.sendMessage(ChatColor.GOLD + "Starting entity benchmark with a target of " + maxEntities + " entities (AI Enabled).");

        int ticks = TEST_DURATION_SECONDS * 20;
        int entitiesPerTick = (int) Math.ceil((double) maxEntities / ticks);
        final int spawnRate = Math.min(MAX_SPAWNS_PER_TICK, Math.max(1, entitiesPerTick));

        spawnerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (spawnedMobs.size() >= maxEntities) {
                return;
            }

            Player p = Bukkit.getPlayer(testPlayerId);
            if (p == null) return;

            World world = p.getWorld();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            final int spawnRadius = SPAWN_CHUNK_RADIUS * 16;

            for (int i = 0; i < spawnRate; i++) {
                if (spawnedMobs.size() >= maxEntities) break;

                Location center = p.getLocation();
                double randomX = random.nextInt(spawnRadius * 2) - spawnRadius;
                double randomZ = random.nextInt(spawnRadius * 2) - spawnRadius;
                Location loc = center.clone().add(randomX, 0, randomZ);

                Location spawnLoc = world.getHighestBlockAt(loc).getLocation().add(0, 1, 0);
                if (!spawnLoc.getChunk().isLoaded()) continue;

                EntityType type = MOB_TYPES[random.nextInt(MOB_TYPES.length)];
                Entity spawned = world.spawnEntity(spawnLoc, type);
                configureSpawnedEntity(spawned);
                spawnedMobs.add(spawned);
            }
        }, 0L, 1L);

        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(testPlayerId);
            if (p == null || !p.isOnline()) {
                stopTest(false);
                return;
            }

            double currentTps = TpsUtils.getTps()[0];
            tpsSamples.add(currentTps);

            int currentPing = p.getPing();
            pingSamples.add(currentPing);

            String tpsColor = currentTps > 18 ? "§a" : (currentTps > 15 ? "§e" : "§c");
            String message = String.format("§eEntities: §f%d/%d §8| §eTPS: %s%.2f §8| §ePing: §f%dms",
                    spawnedMobs.size(), maxEntities, tpsColor, currentTps, currentPing);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));

        }, 0L, 20L);

        mainTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> stopTest(true), ticks);
    }

    public void stopTest(boolean graceful) {
        if (!isRunning) return;

        cancelTasks();
        spawnedMobs.forEach(Entity::remove);
        HandlerList.unregisterAll(this);

        Player player = Bukkit.getPlayer(testPlayerId);
        if (player != null && player.isOnline()) {
            if (graceful) {
                sendResults(player);
            } else {
                player.sendMessage(ChatColor.RED + "Entity benchmark was stopped prematurely.");
            }
        }

        clearPreviousData();
        isRunning = false;
    }

    private void configureSpawnedEntity(Entity entity) {
        entity.setInvulnerable(true);
        entity.setPersistent(false);
        if (entity instanceof Ageable) {
            ((Ageable) entity).setAdult();
        }
        // AI is now enabled by default, no need to call setAware(false)
    }

    private void sendResults(Player player) {
        double avgTps = tpsSamples.stream().mapToDouble(d -> d).average().orElse(0);
        double avgPing = pingSamples.stream().mapToInt(i -> i).average().orElse(0);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§aBenchmark Finished!"));
        player.sendMessage(ChatColor.GREEN + "===== Entity Benchmark Results (AI Enabled) =====");
        player.sendMessage(String.format(ChatColor.YELLOW + "Total entities spawned: %d", spawnedMobs.size()));
        player.sendMessage(String.format(ChatColor.YELLOW + "Average TPS: %.2f", avgTps));
        player.sendMessage(String.format(ChatColor.YELLOW + "Your Average Ping: %.0fms", avgPing));
        player.sendMessage(ChatColor.GREEN + "=============================================");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    private void cancelTasks() {
        if (mainTask != null) mainTask.cancel();
        if (spawnerTask != null) spawnerTask.cancel();
        if (monitorTask != null) monitorTask.cancel();
    }

    private void clearPreviousData() {
        spawnedMobs.clear();
        tpsSamples.clear();
        pingSamples.clear();
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