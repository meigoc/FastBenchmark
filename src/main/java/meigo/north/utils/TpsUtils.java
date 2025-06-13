package meigo.north.utils;

import org.bukkit.Bukkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TpsUtils {

    private static Object minecraftServer;
    private static Field tpsField;

    static {
        try {
            Object craftServer = Bukkit.getServer();
            Method getServerMethod = craftServer.getClass().getMethod("getServer");
            minecraftServer = getServerMethod.invoke(craftServer);
            tpsField = minecraftServer.getClass().getField("recentTps");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[FastBenchmark] Cannot initialize TPS Checker. Test may be incorrect.");
            e.printStackTrace();
            minecraftServer = null;
            tpsField = null;
        }
    }

    public static double[] getTps() {
        if (minecraftServer == null || tpsField == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        try {
            return (double[]) tpsField.get(minecraftServer);
        } catch (IllegalAccessException e) {
            return new double[]{0.0, 0.0, 0.0};
        }
    }
}