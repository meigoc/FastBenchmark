package meigo.north.tests;

import meigo.north.MeigoPlugin;
import meigo.north.utils.TpsUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleTest {

    private final MeigoPlugin plugin;
    private boolean isRunning = false;
    private BukkitTask particleTask;
    private BukkitTask monitorTask;
    private final List<Double> tpsSamples = new ArrayList<>();
    private int particlesSpawned = 0;
    private long startTime;

    public ParticleTest(MeigoPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTest(Player player, int targetParticles) {
        if (isRunning) {
            player.sendMessage(MeigoPlugin.formatHexColor("&#FF0000A particle test is already running!"));
            return;
        }

        isRunning = true;
        particlesSpawned = 0;
        tpsSamples.clear();
        startTime = System.currentTimeMillis();

        player.sendMessage(MeigoPlugin.formatHexColor("&#FFA500Starting particle benchmark with &#FFFF00" + targetParticles + " &#FFA500particles..."));

        int maxParticlesPerTick = plugin.getTestsConfig().getMaxParticlesPerTick();
        int particlesPerTick = Math.min(maxParticlesPerTick, Math.max(1, targetParticles / 100));

        Random random = new Random();
        Location center = player.getLocation();

        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (particlesSpawned >= targetParticles) {
                stopTest(player, true);
                return;
            }

            for (int i = 0; i < particlesPerTick && particlesSpawned < targetParticles; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 20;
                double offsetY = random.nextDouble() * 10;
                double offsetZ = (random.nextDouble() - 0.5) * 20;

                Location particleLoc = center.clone().add(offsetX, offsetY, offsetZ);

                // Spawn different particle types
                Particle particleType = getRandomParticle(random);
                player.getWorld().spawnParticle(particleType, particleLoc, 1, 0, 0, 0, 0);

                particlesSpawned++;
            }
        }, 0L, 1L);

        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double currentTps = TpsUtils.getTps()[0];
            tpsSamples.add(currentTps);

            String tpsColor = currentTps > 18 ? "&#00FF00" : (currentTps > 15 ? "&#FFFF00" : "&#FF0000");
            String message = MeigoPlugin.formatHexColor("&#FFD700Particles: &#FFFFFF" + particlesSpawned + "/" + targetParticles +
                    " &#808080| &#FFD700TPS: " + tpsColor + String.format("%.2f", currentTps));

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        }, 0L, 20L);

        // Auto-stop after 30 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isRunning) {
                stopTest(player, true);
            }
        }, 600L);
    }

    private void stopTest(Player player, boolean success) {
        if (!isRunning) return;

        isRunning = false;

        if (particleTask != null) particleTask.cancel();
        if (monitorTask != null) monitorTask.cancel();

        if (success) {
            long duration = System.currentTimeMillis() - startTime;
            double avgTps = tpsSamples.stream().mapToDouble(d -> d).average().orElse(20.0);

            player.sendMessage(" ");
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00===== Particle Test Results ====="));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Total particles spawned: &#FFFFFF" + particlesSpawned));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Test duration: &#FFFFFF" + String.format("%.2f seconds", duration / 1000.0)));
            player.sendMessage(MeigoPlugin.formatHexColor("&#FFFF00Average TPS: &#FFFFFF" + String.format("%.2f", avgTps)));
            player.sendMessage(MeigoPlugin.formatHexColor("&#00FF00================================"));

            // Play completion sound
            String soundName = plugin.getTestsConfig().getParticleTestSound();
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException e) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }
        }
    }

    private Particle getRandomParticle(Random random) {
        Particle[] particles = {
                Particle.FLAME, Particle.SOUL_FIRE_FLAME, Particle.CAMPFIRE_COSY_SMOKE,
                Particle.REDSTONE, Particle.SPELL_WITCH, Particle.SPELL_MOB,
                Particle.VILLAGER_HAPPY, Particle.CRIT, Particle.CRIT_MAGIC,
                Particle.SMOKE_NORMAL, Particle.SMOKE_LARGE, Particle.LAVA,
                Particle.WATER_SPLASH, Particle.WATER_BUBBLE, Particle.CLOUD,
                Particle.SNOWBALL, Particle.DRIP_WATER, Particle.DRIP_LAVA,
                Particle.ENCHANTMENT_TABLE, Particle.PORTAL, Particle.END_ROD
        };
        return particles[random.nextInt(particles.length)];
    }
}
