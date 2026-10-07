package syrnic.genocide.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import syrnic.genocide.config.GenocideConfig;

import java.util.UUID;

public record ExtinctionRecord(
        ChunkPos chunkPos,
        ResourceLocation species,
        ResourceLocation biome,
        BlockPos center,
        int radius,
        long createdAt,
        @Nullable UUID killerUuid,
        @Nullable String killerName
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ChunkX", chunkPos.x);
        tag.putInt("ChunkZ", chunkPos.z);
        tag.putString("Species", species.toString());
        tag.putString("Biome", biome.toString());
        tag.putInt("CenterX", center.getX());
        tag.putInt("CenterY", center.getY());
        tag.putInt("CenterZ", center.getZ());
        tag.putInt("Radius", radius);
        tag.putLong("CreatedAt", createdAt);
        if (killerUuid != null) {
            tag.putUUID("KillerUuid", killerUuid);
        }
        if (killerName != null && !killerName.isBlank()) {
            tag.putString("KillerName", killerName);
        }
        return tag;
    }

    @Nullable
    public static ExtinctionRecord fromTag(CompoundTag tag) {
        if (!tag.contains("Species") || !tag.contains("Biome")) {
            return null;
        }
        ResourceLocation species = ResourceLocation.tryParse(tag.getString("Species"));
        ResourceLocation biome = ResourceLocation.tryParse(tag.getString("Biome"));
        if (species == null || biome == null) {
            return null;
        }
        ChunkPos chunkPos = new ChunkPos(tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        BlockPos center = new BlockPos(tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"));
        int rawRadius = tag.getInt("Radius");
        int radius = GenocideConfig.isValidOddRadius(rawRadius) ? rawRadius : GenocideConfig.DEFAULT_RADIUS;
        long createdAt = tag.getLong("CreatedAt");
        UUID killerUuid = tag.hasUUID("KillerUuid") ? tag.getUUID("KillerUuid") : null;
        String killerName = tag.contains("KillerName") ? tag.getString("KillerName") : null;
        return new ExtinctionRecord(chunkPos, species, biome, center, radius, createdAt, killerUuid, killerName);
    }

    public boolean matchesSpeciesAndBiome(ResourceLocation otherSpecies, ResourceLocation otherBiome) {
        return this.species.equals(otherSpecies) && this.biome.equals(otherBiome);
    }
}
