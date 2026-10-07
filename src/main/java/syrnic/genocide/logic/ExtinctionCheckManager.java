package syrnic.genocide.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import syrnic.genocide.config.GenocideConfig;
import syrnic.genocide.config.SpeciesFilter;
import syrnic.genocide.data.ExtinctionRecord;
import syrnic.genocide.data.GenocideDataManager;
import syrnic.genocide.data.GenocideSavedData;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ExtinctionCheckManager {
    private static final Set<LivingEntity> PROCESSED_DEATHS = Collections.newSetFromMap(new WeakHashMap<>());

    private ExtinctionCheckManager() {
    }

    public static void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (entity instanceof ServerPlayer player) {
            HazardTracker.clearAllPlayerMarks(player.getUUID());
            return;
        }

        if (!(entity instanceof Mob)) {
            return;
        }

        // Ensure check runs strictly once per death event
        if (!PROCESSED_DEATHS.add(entity)) {
            return;
        }

        ServerPlayer killer = resolveKillerPlayer(level, entity, source);
        if (killer == null && !GenocideConfig.countNonPlayerDeaths()) {
            return;
        }

        if (!SpeciesFilter.isDimensionAllowed(level)) {
            return;
        }

        BlockPos deathPos = entity.blockPosition();
        ChunkPos deathChunk = entity.chunkPosition();
        if (!level.hasChunk(deathChunk.x, deathChunk.z)) {
            return;
        }

        Holder<Biome> biomeHolder = level.getBiome(deathPos);
        EntityType<?> entityType = entity.getType();
        if (!SpeciesFilter.isSpeciesEligible(entityType, biomeHolder)) {
            return;
        }

        int extinctionRadius = SpeciesFilter.getEffectiveExtinctionRadius(biomeHolder);
        int minAdults = SpeciesFilter.getEffectiveMinAdults(biomeHolder);
        ResourceLocation biomeId = ChunkRadiusHelper.getBiomeId(biomeHolder);

        ChunkRadiusHelper.PopulationCount count = ChunkRadiusHelper.countPopulation(
                level,
                entityType,
                biomeId,
                deathChunk,
                extinctionRadius,
                entity
        );

        // 1. Check fails if there are at least minAdults adult individuals
        if (count.adults() >= minAdults) {
            return;
        }

        // 2. If adults < minAdults, but total (adults + babies) >= minAdults, check fails with survivalChanceWithYoung %
        if (count.total() >= minAdults) {
            int survivalChance = GenocideConfig.survivalChanceWithYoung();
            if (survivalChance >= 100 || (survivalChance > 0 && level.random.nextInt(100) < survivalChance)) {
                return;
            }
        }

        // Check succeeded: create records for all chunks in radius (even unloaded ones)
        ResourceLocation speciesId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        long now = System.currentTimeMillis();
        UUID killerUuid = killer != null ? killer.getUUID() : null;
        String killerName = killer != null ? killer.getScoreboardName() : null;

        GenocideSavedData savedData = GenocideDataManager.get(level);
        List<ChunkPos> affectedChunks = ChunkRadiusHelper.getChunksForOddRadius(deathChunk, extinctionRadius);
        boolean anyAdded = false;

        for (ChunkPos chunkPos : affectedChunks) {
            ExtinctionRecord record = new ExtinctionRecord(
                    chunkPos,
                    speciesId,
                    biomeId,
                    deathPos,
                    extinctionRadius,
                    now,
                    killerUuid,
                    killerName
            );
            if (savedData.addRecordIfAbsent(record)) {
                anyAdded = true;
            }
        }

        if (anyAdded) {
            GenocideDataManager.enforceMaxRecords(level.getServer());
        }
    }

    @Nullable
    private static ServerPlayer resolveKillerPlayer(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (victim.getKillCredit() instanceof ServerPlayer sp) {
            return sp;
        }
        if (source.getEntity() instanceof ServerPlayer sp) {
            return sp;
        }
        if (victim.getKillCredit() instanceof TamableAnimal tamable
                && tamable.isTame()
                && tamable.getOwner() instanceof ServerPlayer sp) {
            return sp;
        }
        if (GenocideConfig.trackPlayerHazards()
                && source.is(DamageTypeTags.IS_FIRE)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)) {
            return HazardTracker.getActiveHazardKiller(level, victim.chunkPosition());
        }
        return null;
    }

    public static void onBabySpawn(Mob parentA, Mob parentB, @Nullable AgeableMob child) {
        if (child == null || !(parentA.level() instanceof ServerLevel level)) {
            return;
        }

        if (!SpeciesFilter.isDimensionAllowed(level)) {
            return;
        }

        BlockPos birthPos = parentA.blockPosition();
        ChunkPos birthChunk = new ChunkPos(birthPos);
        if (!level.hasChunk(birthChunk.x, birthChunk.z)) {
            return;
        }

        Holder<Biome> biomeHolder = level.getBiome(birthPos);
        EntityType<?> speciesType = child.getType();
        if (!SpeciesFilter.isSpeciesEligible(speciesType, biomeHolder)) {
            return;
        }

        int reviveRadius = SpeciesFilter.getEffectiveReviveRadius(biomeHolder);
        ResourceLocation speciesId = BuiltInRegistries.ENTITY_TYPE.getKey(speciesType);
        ResourceLocation biomeId = ChunkRadiusHelper.getBiomeId(biomeHolder);

        GenocideSavedData savedData = GenocideDataManager.get(level);
        List<ChunkPos> chunksInRadius = ChunkRadiusHelper.getChunksForOddRadius(birthChunk, reviveRadius);

        boolean hasMatchingRecord = false;
        for (ChunkPos chunkPos : chunksInRadius) {
            if (savedData.hasRecord(chunkPos.toLong(), speciesId, biomeId)) {
                hasMatchingRecord = true;
                break;
            }
        }
        if (!hasMatchingRecord) {
            return;
        }

        ChunkRadiusHelper.PopulationCount count = ChunkRadiusHelper.countPopulation(
                level,
                speciesType,
                biomeId,
                birthChunk,
                reviveRadius,
                child
        );

        int adults = count.adults();
        int reviveMinAdults = GenocideConfig.reviveMinAdults();
        if (adults < reviveMinAdults) {
            return;
        }

        int chance = Math.min(100, GenocideConfig.reviveBaseChance() + GenocideConfig.reviveStep() * (adults - reviveMinAdults));
        if (chance <= 0) {
            return;
        }
        if (chance < 100 && level.random.nextInt(100) >= chance) {
            return;
        }

        // Revival check succeeded: remove matching records in the radius chunks
        for (ChunkPos chunkPos : chunksInRadius) {
            savedData.removeRecords(chunkPos, speciesId, biomeId);
        }
    }

    public static boolean shouldBlockNaturalSpawn(
            ServerLevelAccessor levelAccessor,
            EntityType<?> entityType,
            MobSpawnType spawnType,
            BlockPos pos
    ) {
        if (spawnType != MobSpawnType.NATURAL) {
            return false;
        }

        ServerLevel level = levelAccessor.getLevel();
        if (!SpeciesFilter.isDimensionAllowed(level)) {
            return false;
        }

        GenocideSavedData savedData = GenocideDataManager.get(level);
        long chunkKey = ChunkPos.asLong(pos);
        ResourceLocation speciesId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        // Fast O(1) hash-table check before querying biome
        if (!savedData.hasSpeciesInChunk(chunkKey, speciesId)) {
            return false;
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (!SpeciesFilter.isSpeciesEligible(entityType, biomeHolder)) {
            return false;
        }

        ResourceLocation biomeId = ChunkRadiusHelper.getBiomeId(biomeHolder);
        return savedData.hasRecord(chunkKey, speciesId, biomeId);
    }
}
