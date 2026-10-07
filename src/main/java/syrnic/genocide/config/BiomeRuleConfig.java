package syrnic.genocide.config;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class BiomeRuleConfig {
    public static final Comparator<BiomeRuleConfig> PRIORITY_COMPARATOR = (a, b) -> {
        if (a.isTag != b.isTag) {
            return a.isTag ? 1 : -1;
        }
        int cmp = a.fileName.compareToIgnoreCase(b.fileName);
        return cmp != 0 ? cmp : a.fileName.compareTo(b.fileName);
    };

    private final String fileName;
    private final String rawBiomeTarget;
    private final boolean isTag;
    @Nullable
    private final ResourceLocation biomeId;
    @Nullable
    private final TagKey<Biome> biomeTag;
    @Nullable
    private final Boolean enabled;
    @Nullable
    private final Integer extinctionRadius;
    @Nullable
    private final Integer reviveRadius;
    @Nullable
    private final Integer minAdults;
    private final List<String> extinctionWhitelist;
    private final List<String> extinctionBlacklist;

    public BiomeRuleConfig(
            String fileName,
            String rawBiomeTarget,
            @Nullable Boolean enabled,
            @Nullable Integer extinctionRadius,
            @Nullable Integer reviveRadius,
            @Nullable Integer minAdults,
            @Nullable List<String> extinctionWhitelist,
            @Nullable List<String> extinctionBlacklist
    ) {
        this.fileName = fileName;
        this.rawBiomeTarget = rawBiomeTarget.trim();
        this.isTag = this.rawBiomeTarget.startsWith("#");
        if (this.isTag) {
            ResourceLocation loc = ResourceLocation.tryParse(this.rawBiomeTarget.substring(1).trim());
            this.biomeTag = loc != null ? TagKey.create(Registries.BIOME, loc) : null;
            this.biomeId = null;
        } else {
            this.biomeId = ResourceLocation.tryParse(this.rawBiomeTarget);
            this.biomeTag = null;
        }
        this.enabled = enabled;
        this.extinctionRadius = extinctionRadius;
        this.reviveRadius = reviveRadius;
        this.minAdults = minAdults != null ? Math.max(1, minAdults) : null;
        this.extinctionWhitelist = extinctionWhitelist != null ? List.copyOf(extinctionWhitelist) : Collections.emptyList();
        this.extinctionBlacklist = extinctionBlacklist != null ? List.copyOf(extinctionBlacklist) : Collections.emptyList();
    }

    public boolean isValidTarget() {
        return isTag ? biomeTag != null : biomeId != null;
    }

    public boolean matches(Holder<Biome> biomeHolder) {
        if (isTag) {
            return biomeTag != null && biomeHolder.is(biomeTag);
        } else {
            return biomeId != null && biomeHolder.is(biomeId);
        }
    }

    public String fileName() {
        return fileName;
    }

    public String rawBiomeTarget() {
        return rawBiomeTarget;
    }

    public boolean isTag() {
        return isTag;
    }

    @Nullable
    public Boolean enabled() {
        return enabled;
    }

    @Nullable
    public Integer extinctionRadius() {
        return extinctionRadius;
    }

    @Nullable
    public Integer reviveRadius() {
        return reviveRadius;
    }

    @Nullable
    public Integer minAdults() {
        return minAdults;
    }

    public List<String> extinctionWhitelist() {
        return extinctionWhitelist;
    }

    public List<String> extinctionBlacklist() {
        return extinctionBlacklist;
    }
}
