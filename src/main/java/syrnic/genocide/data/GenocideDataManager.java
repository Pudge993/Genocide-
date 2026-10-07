package syrnic.genocide.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import syrnic.genocide.config.GenocideConfig;

public final class GenocideDataManager {
    private GenocideDataManager() {
    }

    public static GenocideSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GenocideSavedData.FACTORY, GenocideSavedData.DATA_NAME);
    }

    public static GenocideSavedData getGlobalData(MinecraftServer server) {
        return get(server.overworld());
    }

    public static int getTotalRecordsAcrossServer(MinecraftServer server) {
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += get(level).getTotalRecords();
        }
        return total;
    }

    public static void enforceMaxRecords(MinecraftServer server) {
        int maxRecords = GenocideConfig.maxRecords();
        int total = getTotalRecordsAcrossServer(server);
        while (total > maxRecords) {
            GenocideSavedData oldestData = null;
            ExtinctionRecord oldestRecord = null;

            for (ServerLevel level : server.getAllLevels()) {
                GenocideSavedData data = get(level);
                ExtinctionRecord candidate = data.findOldestRecord();
                if (candidate != null && (oldestRecord == null || candidate.createdAt() < oldestRecord.createdAt())) {
                    oldestRecord = candidate;
                    oldestData = data;
                }
            }

            if (oldestData != null && oldestRecord != null && oldestData.removeSpecificRecord(oldestRecord)) {
                total--;
            } else {
                break;
            }
        }
    }
}
