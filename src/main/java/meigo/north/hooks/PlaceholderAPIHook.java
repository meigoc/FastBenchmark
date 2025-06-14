package meigo.north.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import meigo.north.MeigoPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final MeigoPlugin plugin;

    public PlaceholderAPIHook(MeigoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fastbenchmark";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "cpu_load":
                return String.format("%.1f", plugin.getCpuMonitor().getCpuLoad());

            case "cpu_maxload":
                return String.format("%.1f", plugin.getCpuMonitor().getMaxCpuLoad());

            case "cpu_threads":
                return String.valueOf(plugin.getCpuMonitor().getCpuThreads());

            case "noderam_used":
                if (plugin.isOshiAvailable() && plugin.getNodeRamMonitor() != null) {
                    return String.valueOf(plugin.getNodeRamMonitor().getUsedMemory());
                }
                return "N/A";

            case "noderam_max":
                if (plugin.isOshiAvailable() && plugin.getNodeRamMonitor() != null) {
                    return String.valueOf(plugin.getNodeRamMonitor().getTotalMemory());
                }
                return "N/A";

            case "realram_used":
                return String.valueOf(plugin.getRamMonitor().getUsedMemory());

            case "realram_max":
                return String.valueOf(plugin.getRamMonitor().getMaxMemory());

            case "cpu_name":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null) {
                    return plugin.getSystemInfoManager().getCpuName();
                }
                return "N/A";

            case "cpu_manufacturer":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null) {
                    return plugin.getSystemInfoManager().getCpuManufacturer();
                }
                return "N/A";

            case "cpu_frequency":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null) {
                    return plugin.getSystemInfoManager().getCpuFrequency();
                }
                return "N/A";

            case "cpu_swap_enabled":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null) {
                    return String.valueOf(plugin.getSystemInfoManager().isSwapEnabled());
                }
                return "false";

            case "cpu_swap_used":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null &&
                        plugin.getSystemInfoManager().isSwapEnabled()) {
                    return String.valueOf(plugin.getSystemInfoManager().getSwapUsed());
                }
                return "0";

            case "cpu_swap_max":
                if (plugin.isOshiAvailable() && plugin.getSystemInfoManager() != null &&
                        plugin.getSystemInfoManager().isSwapEnabled()) {
                    return String.valueOf(plugin.getSystemInfoManager().getSwapTotal());
                }
                return "0";

            default:
                return null;
        }
    }
}
