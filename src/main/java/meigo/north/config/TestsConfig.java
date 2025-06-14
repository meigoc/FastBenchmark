package meigo.north.config;

import meigo.north.MeigoPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class TestsConfig {

    private final MeigoPlugin plugin;
    private FileConfiguration config;

    public TestsConfig(MeigoPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "tests.yml");
        if (!configFile.exists()) {
            plugin.saveResource("tests.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        loadConfig();
    }

    // World Generation Test
    public String getWorldGenSound() {
        return config.getString("worldgen.completion-sound", "ENTITY_PLAYER_LEVELUP");
    }

    // Entity Test
    public String getEntityTestSound() {
        return config.getString("entity.completion-sound", "ENTITY_PLAYER_LEVELUP");
    }

    public int getMaxEntitiesPerTick() {
        return config.getInt("entity.max-spawns-per-tick", 250);
    }

    public int getEntityTestDuration() {
        return config.getInt("entity.test-duration-seconds", 30);
    }

    // Particle Test
    public String getParticleTestSound() {
        return config.getString("particles.completion-sound", "ENTITY_PLAYER_LEVELUP");
    }

    public int getMaxParticlesPerTick() {
        return config.getInt("particles.max-particles-per-tick", 100);
    }

    // Chunk Load Test
    public String getChunkTestSound() {
        return config.getString("chunksload.completion-sound", "ENTITY_PLAYER_LEVELUP");
    }

    public int getChunkLoadRadius() {
        return config.getInt("chunksload.chunk-radius", 5);
    }

    // Players Test
    public String getPlayersTestSound() {
        return config.getString("players.completion-sound", "ENTITY_PLAYER_LEVELUP");
    }

    public int getPlayersPerSecond() {
        return config.getInt("players.players-per-second", 5);
    }
}
