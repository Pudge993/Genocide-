package syrnic.genocide.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GenocideSavedData extends SavedData {
    public static final String DATA_NAME = "genocide_data";

    public static final SavedData.Factory<GenocideSavedData> FACTORY = new SavedData.Factory<>(
            GenocideSavedData::new,
            GenocideSavedData::load
    );

    private final Long2ObjectOpenHashMap<List<ExtinctionRecord>> chunkIndex = new Long2ObjectOpenHashMap<>();
    private final Set<UUID> seenJoinMessagePlayers = new HashSet<>();
    private int totalRecords = 0;

    public GenocideSavedData() {
    }

    public static GenocideSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GenocideSavedData data = new GenocideSavedData();

        if (tag.contains("Records", Tag.TAG_LIST)) {
            ListTag recordsTag = tag.getList("Records", Tag.TAG_COMPOUND);
            for (int i = 0; i < recordsTag.size(); i++) {
                ExtinctionRecord record = ExtinctionRecord.fromTag(recordsTag.getCompound(i));
                if (record != null) {
                    data.addRecordInternal(record);
                }
            }
        }

        if (tag.contains("SeenJoinPlayers", Tag.TAG_LIST)) {
            ListTag seenList = tag.getList("SeenJoinPlayers", Tag.TAG_STRING);
            for (int i = 0; i < seenList.size(); i++) {
                try {
                    data.seenJoinMessagePlayers.add(UUID.fromString(seenList.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag recordsTag = new ListTag();
        for (Long2ObjectMap.Entry<List<ExtinctionRecord>> entry : chunkIndex.long2ObjectEntrySet()) {
            for (ExtinctionRecord record : entry.getValue()) {
                recordsTag.add(record.toTag());
            }
        }
        tag.put("Records", recordsTag);

        if (!seenJoinMessagePlayers.isEmpty()) {
            ListTag seenList = new ListTag();
            for (UUID uuid : seenJoinMessagePlayers) {
                seenList.add(StringTag.valueOf(uuid.toString()));
            }
            tag.put("SeenJoinPlayers", seenList);
        }

        return tag;
    }

    private boolean addRecordInternal(ExtinctionRecord record) {
        long key = record.chunkPos().toLong();
        List<ExtinctionRecord> list = chunkIndex.get(key);
        if (list == null) {
            list = new ArrayList<>(2);
            chunkIndex.put(key, list);
        } else {
            for (ExtinctionRecord existing : list) {
                if (existing.matchesSpeciesAndBiome(record.species(), record.biome())) {
                    return false;
                }
            }
        }
        list.add(record);
        totalRecords++;
        return true;
    }

    /**
     * Adds a record to the chunk if no record with the same species and biome already exists in that chunk.
     *
     * @return true if the record was added, false if a duplicate already existed.
     */
    public boolean addRecordIfAbsent(ExtinctionRecord record) {
        boolean added = addRecordInternal(record);
        if (added) {
            setDirty();
        }
        return added;
    }

    /**
     * Fast O(1) hash-table check whether the chunk has any record for the given species.
     */
    public boolean hasSpeciesInChunk(long chunkKey, ResourceLocation species) {
        List<ExtinctionRecord> list = chunkIndex.get(chunkKey);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).species().equals(species)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fast O(1) hash-table check whether the chunk has a record matching both species and biome.
     */
    public boolean hasRecord(long chunkKey, ResourceLocation species, ResourceLocation biome) {
        List<ExtinctionRecord> list = chunkIndex.get(chunkKey);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (int i = 0, size = list.size(); i < size; i++) {
            ExtinctionRecord rec = list.get(i);
            if (rec.species().equals(species) && rec.biome().equals(biome)) {
                return true;
            }
        }
        return false;
    }

    public List<ExtinctionRecord> getRecordsInChunk(ChunkPos chunkPos) {
        List<ExtinctionRecord> list = chunkIndex.get(chunkPos.toLong());
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /**
     * Removes records in the given chunk matching optional species and optional biome.
     * If species is null, removes all records in the chunk.
     *
     * @return number of removed records.
     */
    public int removeRecords(ChunkPos chunkPos, @Nullable ResourceLocation species, @Nullable ResourceLocation biome) {
        long key = chunkPos.toLong();
        List<ExtinctionRecord> list = chunkIndex.get(key);
        if (list == null || list.isEmpty()) {
            return 0;
        }

        if (species == null && biome == null) {
            int count = list.size();
            chunkIndex.remove(key);
            totalRecords -= count;
            setDirty();
            return count;
        }

        int removed = 0;
        Iterator<ExtinctionRecord> it = list.iterator();
        while (it.hasNext()) {
            ExtinctionRecord rec = it.next();
            boolean speciesMatch = (species == null || rec.species().equals(species));
            boolean biomeMatch = (biome == null || rec.biome().equals(biome));
            if (speciesMatch && biomeMatch) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            if (list.isEmpty()) {
                chunkIndex.remove(key);
            }
            totalRecords -= removed;
            setDirty();
        }
        return removed;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    @Nullable
    public ExtinctionRecord findOldestRecord() {
        ExtinctionRecord oldest = null;
        for (Long2ObjectMap.Entry<List<ExtinctionRecord>> entry : chunkIndex.long2ObjectEntrySet()) {
            for (ExtinctionRecord rec : entry.getValue()) {
                if (oldest == null || rec.createdAt() < oldest.createdAt()) {
                    oldest = rec;
                }
            }
        }
        return oldest;
    }

    public boolean removeSpecificRecord(ExtinctionRecord target) {
        long key = target.chunkPos().toLong();
        List<ExtinctionRecord> list = chunkIndex.get(key);
        if (list == null) {
            return false;
        }
        if (list.remove(target)) {
            if (list.isEmpty()) {
                chunkIndex.remove(key);
            }
            totalRecords--;
            setDirty();
            return true;
        }
        return false;
    }

    public Set<ResourceLocation> getAllRecordedSpecies() {
        Set<ResourceLocation> speciesSet = new HashSet<>();
        for (Long2ObjectMap.Entry<List<ExtinctionRecord>> entry : chunkIndex.long2ObjectEntrySet()) {
            for (ExtinctionRecord rec : entry.getValue()) {
                speciesSet.add(rec.species());
            }
        }
        return speciesSet;
    }

    public boolean hasPlayerSeenJoinMessage(UUID playerUuid) {
        return seenJoinMessagePlayers.contains(playerUuid);
    }

    public void markPlayerSeenJoinMessage(UUID playerUuid) {
        if (seenJoinMessagePlayers.add(playerUuid)) {
            setDirty();
        }
    }
}
