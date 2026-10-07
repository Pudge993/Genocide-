package syrnic.genocide.logic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import syrnic.genocide.config.GenocideConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HazardTracker {
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<UUID>>> DIMENSION_CHUNK_MARKS = new HashMap<>();

    private HazardTracker() {
    }

    /**
     * Marks the chunk where the player is located with the player's UUID when the player places lava or fire.
     * The mark is active only while the player remains in that chunk.
     * If multiple marks exist, the most recently recorded mark takes priority.
     */
    public static void markChunkHazard(ServerLevel level, ChunkPos placementChunk, ServerPlayer player) {
        if (!GenocideConfig.trackPlayerHazards() || !syrnic.genocide.config.SpeciesFilter.isDimensionAllowed(level)) {
            return;
        }
        ChunkPos playerChunk = player.chunkPosition();

        Long2ObjectOpenHashMap<List<UUID>> chunkMap = DIMENSION_CHUNK_MARKS.computeIfAbsent(
                level.dimension(),
                k -> new Long2ObjectOpenHashMap<>()
        );

        long key = playerChunk.toLong();
        List<UUID> marks = chunkMap.computeIfAbsent(key, k -> new ArrayList<>(2));
        UUID playerUuid = player.getUUID();
        marks.remove(playerUuid);
        marks.add(playerUuid);
    }

    /**
     * Removes the player's mark from a specific chunk (e.g. when leaving that chunk).
     */
    public static void removePlayerMarkFromChunk(ResourceKey<Level> dimension, ChunkPos chunkPos, UUID playerUuid) {
        Long2ObjectOpenHashMap<List<UUID>> chunkMap = DIMENSION_CHUNK_MARKS.get(dimension);
        if (chunkMap == null) {
            return;
        }
        long key = chunkPos.toLong();
        List<UUID> marks = chunkMap.get(key);
        if (marks != null) {
            marks.remove(playerUuid);
            if (marks.isEmpty()) {
                chunkMap.remove(key);
            }
        }
    }

    /**
     * Removes all marks belonging to the player across all dimensions and chunks
     * (used on logout, death, dimension change, or chunk change).
     */
    public static void clearAllPlayerMarks(UUID playerUuid) {
        for (Long2ObjectOpenHashMap<List<UUID>> chunkMap : DIMENSION_CHUNK_MARKS.values()) {
            Iterator<Long2ObjectMap.Entry<List<UUID>>> mapIt = chunkMap.long2ObjectEntrySet().iterator();
            while (mapIt.hasNext()) {
                Long2ObjectMap.Entry<List<UUID>> entry = mapIt.next();
                List<UUID> list = entry.getValue();
                list.remove(playerUuid);
                if (list.isEmpty()) {
                    mapIt.remove();
                }
            }
        }
    }

    /**
     * Returns the player whose active hazard mark was recorded last in the given chunk,
     * verifying that the player is still online, alive, in the same dimension, and in that chunk.
     */
    @Nullable
    public static ServerPlayer getActiveHazardKiller(ServerLevel level, ChunkPos chunkPos) {
        if (!GenocideConfig.trackPlayerHazards()) {
            return null;
        }
        Long2ObjectOpenHashMap<List<UUID>> chunkMap = DIMENSION_CHUNK_MARKS.get(level.dimension());
        if (chunkMap == null) {
            return null;
        }
        long key = chunkPos.toLong();
        List<UUID> marks = chunkMap.get(key);
        if (marks == null || marks.isEmpty()) {
            return null;
        }

        for (int i = marks.size() - 1; i >= 0; i--) {
            UUID playerUuid = marks.get(i);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null
                    && player.isAlive()
                    && player.serverLevel() == level
                    && player.chunkPosition().equals(chunkPos)) {
                return player;
            } else {
                marks.remove(i);
            }
        }

        if (marks.isEmpty()) {
            chunkMap.remove(key);
        }
        return null;
    }
}
