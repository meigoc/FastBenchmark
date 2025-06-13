package meigo.north;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class RamAllocator {

    private final Plugin plugin;
    private List<byte[]> allocatedMemory = new ArrayList<>();
    private long totalAllocatedBytes = 0;

    public RamAllocator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Allocates a specified amount of RAM in megabytes.
     * @param megabytes The amount of RAM to allocate in megabytes.
     * @return true if allocation was successful, false otherwise.
     */
    public boolean allocateRam(int megabytes) {
        if (megabytes <= 0) {
            plugin.getLogger().warning("Cannot allocate 0 or negative megabytes.");
            return false;
        }

        long bytesToAllocate = (long) megabytes * 1024 * 1024;

        try {
            int chunkSizeMB = 1;
            long bytesPerChunk = (long) chunkSizeMB * 1024 * 1024;

            for (long i = 0; i < bytesToAllocate; i += bytesPerChunk) {
                int currentChunkSize = (int) Math.min(bytesPerChunk, bytesToAllocate - i);
                byte[] chunk = new byte[currentChunkSize];
                allocatedMemory.add(chunk);
                totalAllocatedBytes += currentChunkSize;
            }
            plugin.getLogger().info("Successfully allocated " + megabytes + " MB of RAM. Total allocated: " + (totalAllocatedBytes / (1024 * 1024)) + " MB.");
            return true;
        } catch (OutOfMemoryError e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to allocate " + megabytes + " MB of RAM: Out of Memory!", e);
            freeAllocatedRam();
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred during RAM allocation for " + megabytes + " MB.", e);
            freeAllocatedRam();
            return false;
        }
    }

    public void freeAllocatedRam() {
        if (allocatedMemory.isEmpty()) {
            plugin.getLogger().info("No RAM to free.");
            return;
        }

        allocatedMemory.clear();
        totalAllocatedBytes = 0;
        System.gc();

        plugin.getLogger().info("Successfully freed all manually allocated RAM.");
    }

    public long getTotalAllocatedMegabytes() {
        return totalAllocatedBytes / (1024 * 1024);
    }
}