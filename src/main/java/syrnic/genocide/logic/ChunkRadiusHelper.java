package syrnic.genocide.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ChunkRadiusHelper {
    private static final ResourceLocation FALLBACK_BIOME = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

    private ChunkRadiusHelper() {
    }

    public record PopulationCount(int adults, int babies) {
        public int total() {
            return adults + babies;
        }
    }

    /**
     * Converts an odd grid diameter in chunks (1=1x1, 3=3x3, 5=5x5...) into half-width chunk offset around the center chunk.
     * 1 -> 0 (1x1 chunk)
     * 3 -> 1 (3x3 chunks)
     * 5 -> 2 (5x5 chunks)
     */
    public static int oddDiameterToChunkOffset(int oddChunkDiameter) {
        int clamped = Math.max(1, oddChunkDiameter);
        return (clamped - 1) / 2;
    }

    public static List<ChunkPos> getChunksForOddRadius(ChunkPos centerChunk, int oddChunkDiameter) {
        int offset = oddDiameterToChunkOffset(oddChunkDiameter);
        int side = offset * 2 + 1;
        List<ChunkPos> chunks = new ArrayList<>(side * side);
        for (int dx = -offset; dx <= offset; dx++) {
            for (int dz = -offset; dz <= offset; dz++) {
                chunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
            }
        }
        return chunks;
    }

    public static ResourceLocation getBiomeId(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(FALLBACK_BIOME);
    }

    public static ResourceLocation getBiomeId(ServerLevel level, BlockPos pos) {
        return getBiomeId(level.getBiome(pos));
    }

    /**
     * Counts loaded alive entities of the given type in the same biome within the odd chunk grid (1=1x1, 3=3x3, 5=5x5...),
     * strictly without loading any unloaded chunks.
     */
    public static PopulationCount countPopulation(
            ServerLevel level,
            EntityType<?> targetType,
            ResourceLocation targetBiome,
            ChunkPos centerChunk,
            int oddChunkDiameter,
            @Nullable Entity excludedEntity
    ) {
        int chunkOffset = oddDiameterToChunkOffset(oddChunkDiameter);
        int minChunkX = centerChunk.x - chunkOffset;
        int maxChunkX = centerChunk.x + chunkOffset;
        int minChunkZ = centerChunk.z - chunkOffset;
        int maxChunkZ = centerChunk.z + chunkOffset;

        double minX = minChunkX << 4;
        double minZ = minChunkZ << 4;
        double maxX = (maxChunkX + 1) << 4;
        double maxZ = (maxChunkZ + 1) << 4;

        AABB searchBox = new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                maxX,
                level.getMaxBuildHeight(),
                maxZ
        );

        List<? extends Entity> entities = level.getEntities(targetType, searchBox, entity -> {
            if (entity == excludedEntity || !entity.isAlive() || entity.isRemoved()) {
                return false;
            }
            ChunkPos ep = entity.chunkPosition();
            if (ep.x < minChunkX || ep.x > maxChunkX || ep.z < minChunkZ || ep.z > maxChunkZ) {
                return false;
            }
            // Do not trigger chunk loading
            if (!level.hasChunk(ep.x, ep.z)) {
                return false;
            }
            ResourceLocation entityBiome = getBiomeId(level, entity.blockPosition());
            return targetBiome.equals(entityBiome);
        });

        int adults = 0;
        int babies = 0;
        for (Entity entity : entities) {
            boolean isBaby = (entity instanceof LivingEntity living) && living.isBaby();
            if (isBaby) {
                babies++;
            } else {
                adults++;
            }
        }
        return new PopulationCount(adults, babies);
    }
}
