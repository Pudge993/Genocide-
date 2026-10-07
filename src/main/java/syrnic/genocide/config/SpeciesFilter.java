package syrnic.genocide.config;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SpeciesFilter {
    private SpeciesFilter() {
    }

    public static boolean isDimensionAllowed(ServerLevel level) {
        String dimId = level.dimension().location().toString();
        return !GenocideConfig.dimensionBlacklist().contains(dimId);
    }

    /**
     * Finds the highest-priority biome rule matching the given biome holder.
     * Rules in {@link GenocideConfig#biomeRules()} are already sorted so that
     * exact biome ID rules precede tag rules, and ties are broken alphabetically by filename.
     */
    @Nullable
    public static BiomeRuleConfig findMatchingBiomeRule(Holder<Biome> biomeHolder) {
        for (BiomeRuleConfig rule : GenocideConfig.biomeRules()) {
            if (rule.matches(biomeHolder)) {
                return rule;
            }
        }
        return null;
    }

    public static boolean isBiomeEnabled(Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        if (rule != null && rule.enabled() != null) {
            return rule.enabled();
        }
        return true;
    }

    public static int getEffectiveExtinctionRadius(Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        if (rule != null && rule.extinctionRadius() != null) {
            return rule.extinctionRadius();
        }
        return GenocideConfig.extinctionRadius();
    }

    public static int getEffectiveReviveRadius(Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        if (rule != null && rule.reviveRadius() != null) {
            return rule.reviveRadius();
        }
        return GenocideConfig.reviveRadius();
    }

    public static int getEffectiveMinAdults(Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        if (rule != null && rule.minAdults() != null) {
            return Math.max(1, rule.minAdults());
        }
        return GenocideConfig.minAdults();
    }

    /**
     * Checks whether a given species is eligible for genocide in the given biome.
     * Order of evaluation (first match wins):
     * 1. Biome disabled -> false
     * 2. X in extinctionBlacklist (global or biome) -> false
     * 3. X in extinctionWhitelist (global or biome) -> true
     * 4. Category of X in categories -> true
     * 5. Otherwise -> false
     */
    public static boolean isSpeciesEligible(EntityType<?> entityType, Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        if (rule != null && rule.enabled() != null && !rule.enabled()) {
            return false;
        }

        // 1. Blacklist check (global or biome)
        if (matchesList(entityType, GenocideConfig.extinctionBlacklist())
                || (rule != null && matchesList(entityType, rule.extinctionBlacklist()))) {
            return false;
        }

        // 2. Whitelist check (global or biome)
        if (matchesList(entityType, GenocideConfig.extinctionWhitelist())
                || (rule != null && matchesList(entityType, rule.extinctionWhitelist()))) {
            return true;
        }

        // 3. MobCategory check
        return GenocideConfig.categories().contains(entityType.getCategory());
    }

    public static boolean isSpeciesBlacklisted(EntityType<?> entityType, Holder<Biome> biomeHolder) {
        BiomeRuleConfig rule = findMatchingBiomeRule(biomeHolder);
        return matchesList(entityType, GenocideConfig.extinctionBlacklist())
                || (rule != null && matchesList(entityType, rule.extinctionBlacklist()));
    }

    public static boolean matchesList(EntityType<?> entityType, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            if (entry.startsWith("#")) {
                ResourceLocation tagLoc = ResourceLocation.tryParse(entry.substring(1).trim());
                if (tagLoc != null) {
                    TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tagLoc);
                    if (entityType.is(tagKey)) {
                        return true;
                    }
                }
            } else {
                ResourceLocation targetId = ResourceLocation.tryParse(entry);
                if (targetId != null && targetId.equals(entityId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
