package meigo.north;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.VirtualMemory;

public class SystemInfoManager {
    private final MeigoPlugin plugin;
    private SystemInfo systemInfo;

    // Cached values
    private String cpuName = "Unknown";
    private String cpuManufacturer = "Unknown";
    private String cpuFrequency = "Unknown";
    private boolean swapEnabled = false;
    private long swapUsed = 0;
    private long swapTotal = 0;

    public SystemInfoManager(MeigoPlugin plugin) {
        this.plugin = plugin;
        loadSystemInfo();
    }

    private void loadSystemInfo() {
        try {
            systemInfo = new SystemInfo();
            CentralProcessor processor = systemInfo.getHardware().getProcessor();

            // CPU Info
            cpuName = processor.getProcessorIdentifier().getName();
            cpuManufacturer = processor.getProcessorIdentifier().getVendor();

            // Frequency in GHz
            long maxFreq = processor.getMaxFreq();
            if (maxFreq > 0) {
                cpuFrequency = String.format("%.2f GHz", maxFreq / 1_000_000_000.0);
            }

            // Swap info
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            VirtualMemory virtualMemory = memory.getVirtualMemory();
            swapTotal = virtualMemory.getSwapTotal();
            swapUsed = virtualMemory.getSwapUsed();
            swapEnabled = swapTotal > 0;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load system information: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateSwapInfo() {
        if (!swapEnabled) return;

        try {
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            VirtualMemory virtualMemory = memory.getVirtualMemory();
            swapUsed = virtualMemory.getSwapUsed();
            swapTotal = virtualMemory.getSwapTotal();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update swap info: " + e.getMessage());
        }
    }

    // Getters
    public String getCpuName() {
        return cpuName;
    }

    public String getCpuManufacturer() {
        return cpuManufacturer;
    }

    public String getCpuFrequency() {
        return cpuFrequency;
    }

    public boolean isSwapEnabled() {
        return swapEnabled;
    }

    public long getSwapUsed() {
        updateSwapInfo();
        return swapUsed;
    }

    public long getSwapTotal() {
        return swapTotal;
    }
}
