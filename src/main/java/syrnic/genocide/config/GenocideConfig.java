package syrnic.genocide.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;
import syrnic.genocide.GenocideMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class GenocideConfig {
    public static final int DEFAULT_RADIUS = 3;
    public static final int MAX_ODD_RADIUS = 63;

    private static final Predicate<Object> ODD_RADIUS_VALIDATOR = o ->
            o instanceof Number n && isValidOddRadius(n.intValue());

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> CATEGORIES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTINCTION_WHITELIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTINCTION_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<Integer> EXTINCTION_RADIUS;
    private static final ModConfigSpec.ConfigValue<Integer> REVIVE_RADIUS;
    private static final ModConfigSpec.IntValue MIN_ADULTS;
    private static final ModConfigSpec.IntValue SURVIVAL_CHANCE_WITH_YOUNG;
    private static final ModConfigSpec.IntValue REVIVE_MIN_ADULTS;
    private static final ModConfigSpec.IntValue REVIVE_BASE_CHANCE;
    private static final ModConfigSpec.IntValue REVIVE_STEP;
    private static final ModConfigSpec.BooleanValue COUNT_NON_PLAYER_DEATHS;
    private static final ModConfigSpec.BooleanValue TRACK_PLAYER_HAZARDS;
    private static final ModConfigSpec.IntValue MAX_RECORDS;
    private static final ModConfigSpec.EnumValue<JoinMessageMode> JOIN_MESSAGE_MODE;

    private static volatile Set<MobCategory> cachedCategories = EnumSet.of(MobCategory.CREATURE, MobCategory.AXOLOTLS);
    private static volatile List<String> cachedWhitelist = Collections.emptyList();
    private static volatile List<String> cachedBlacklist = Collections.emptyList();
    private static volatile Set<String> cachedDimensionBlacklist = Collections.emptySet();
    private static volatile int cachedExtinctionRadius = DEFAULT_RADIUS;
    private static volatile int cachedReviveRadius = DEFAULT_RADIUS;
    private static volatile int cachedMinAdults = 2;
    private static volatile int cachedSurvivalChanceWithYoung = 50;
    private static volatile int cachedReviveMinAdults = 2;
    private static volatile int cachedReviveBaseChance = 30;
    private static volatile int cachedReviveStep = 10;
    private static volatile boolean cachedCountNonPlayerDeaths = false;
    private static volatile boolean cachedTrackPlayerHazards = true;
    private static volatile int cachedMaxRecords = 10000;
    private static volatile JoinMessageMode cachedJoinMessageMode = JoinMessageMode.FIRST_JOIN;
    private static volatile List<BiomeRuleConfig> cachedBiomeRules = Collections.emptyList();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        CATEGORIES = builder
                .comment("Категории, на которые действует геноцид")
                .defineListAllowEmpty("categories", List.of("CREATURE", "AXOLOTLS"), () -> "CREATURE", o -> o instanceof String);

        EXTINCTION_WHITELIST = builder
                .comment("Виды или теги, на которые геноцид действует независимо от категории")
                .defineListAllowEmpty("extinctionWhitelist", Collections.emptyList(), () -> "", o -> o instanceof String);

        EXTINCTION_BLACKLIST = builder
                .comment("Виды или теги, на которые геноцид не действует независимо от категории")
                .defineListAllowEmpty("extinctionBlacklist", Collections.emptyList(), () -> "", o -> o instanceof String);

        DIMENSION_BLACKLIST = builder
                .comment("Измерения, в которых геноцид отключён (например, [\"minecraft:the_end\"])")
                .defineListAllowEmpty("dimensionBlacklist", Collections.emptyList(), () -> "", o -> o instanceof String);

        EXTINCTION_RADIUS = builder
                .comment("Радиус проверки мобов на создание записи в чанках (только нечётные числа: 1=1x1, 3=3x3, 5=5x5...)")
                .define("extinctionRadius", DEFAULT_RADIUS, ODD_RADIUS_VALIDATOR);

        REVIVE_RADIUS = builder
                .comment("Радиус проверки мобов на удаление записи в чанках (только нечётные числа: 1=1x1, 3=3x3, 5=5x5...)")
                .define("reviveRadius", DEFAULT_RADIUS, ODD_RADIUS_VALIDATOR);

        MIN_ADULTS = builder
                .comment("Порог: сколько взрослых сохраняет вид")
                .defineInRange("minAdults", 2, 1, 1000);

        SURVIVAL_CHANCE_WITH_YOUNG = builder
                .comment("Шанс сохранения вида (%), если взрослых меньше порога, а особей с детёнышами достаточно")
                .defineInRange("survivalChanceWithYoung", 50, 0, 100);

        REVIVE_MIN_ADULTS = builder
                .comment("Минимум взрослых для возвращения вида")
                .defineInRange("reviveMinAdults", 2, 1, 1000);

        REVIVE_BASE_CHANCE = builder
                .comment("Вероятность возвращения (%) при reviveMinAdults взрослых")
                .defineInRange("reviveBaseChance", 30, 0, 100);

        REVIVE_STEP = builder
                .comment("Прибавка вероятности (%) за каждого следующего взрослого")
                .defineInRange("reviveStep", 10, 0, 100);

        COUNT_NON_PLAYER_DEATHS = builder
                .comment("Запускать проверку для гибели не от игрока")
                .define("countNonPlayerDeaths", false);

        TRACK_PLAYER_HAZARDS = builder
                .comment("Засчитывать игроку гибель от поставленных им лавы и огня")
                .define("trackPlayerHazards", true);

        MAX_RECORDS = builder
                .comment("Максимум записей на весь сервер. При превышении удаляется самая старая")
                .defineInRange("maxRecords", 10000, 1, 10000000);

        JOIN_MESSAGE_MODE = builder
                .comment("Режим сообщения при входе: OFF, FIRST_JOIN, EVERY_JOIN")
                .defineEnum("joinMessageMode", JoinMessageMode.FIRST_JOIN);

        SPEC = builder.build();
    }

    private GenocideConfig() {
    }

    public static boolean isValidOddRadius(int value) {
        return value >= 1 && value <= MAX_ODD_RADIUS && (value % 2 != 0);
    }

    public static int sanitizeOddRadius(int value, int fallback, String contextName) {
        if (isValidOddRadius(value)) {
            return value;
        }
        GenocideMod.LOGGER.warn(
                "Invalid radius value {} for {} (must be an odd integer: 1=1x1, 3=3x3, 5=5x5...). Using fallback {}.",
                value, contextName, fallback
        );
        return fallback;
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "genocide.toml");
    }

    public static synchronized int reloadAll() {
        reloadMainConfigFromDisk();
        loadBiomeConfigsFromDisk();
        return cachedBiomeRules.size();
    }

    public static synchronized void bakeFromSpec() {
        if (!SPEC.isLoaded()) {
            return;
        }
        cachedCategories = parseCategories(CATEGORIES.get());
        cachedWhitelist = copyStringList(EXTINCTION_WHITELIST.get());
        cachedBlacklist = copyStringList(EXTINCTION_BLACKLIST.get());
        cachedDimensionBlacklist = parseDimensionBlacklist(DIMENSION_BLACKLIST.get());
        cachedExtinctionRadius = sanitizeOddRadius(EXTINCTION_RADIUS.get(), DEFAULT_RADIUS, "extinctionRadius");
        cachedReviveRadius = sanitizeOddRadius(REVIVE_RADIUS.get(), DEFAULT_RADIUS, "reviveRadius");
        cachedMinAdults = Math.max(1, MIN_ADULTS.get());
        cachedSurvivalChanceWithYoung = clampPercent(SURVIVAL_CHANCE_WITH_YOUNG.get());
        cachedReviveMinAdults = Math.max(1, REVIVE_MIN_ADULTS.get());
        cachedReviveBaseChance = clampPercent(REVIVE_BASE_CHANCE.get());
        cachedReviveStep = clampPercent(REVIVE_STEP.get());
        cachedCountNonPlayerDeaths = COUNT_NON_PLAYER_DEATHS.get();
        cachedTrackPlayerHazards = TRACK_PLAYER_HAZARDS.get();
        cachedMaxRecords = Math.max(1, MAX_RECORDS.get());
        cachedJoinMessageMode = JOIN_MESSAGE_MODE.get();
    }

    private static void reloadMainConfigFromDisk() {
        bakeFromSpec();
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("genocide.toml");
        if (!Files.exists(configPath)) {
            return;
        }
        try (FileConfig fileConfig = FileConfig.of(configPath)) {
            fileConfig.load();
            if (fileConfig.get("categories") instanceof List<?> list) {
                cachedCategories = parseCategories(extractStringList(list));
            }
            if (fileConfig.get("extinctionWhitelist") instanceof List<?> list) {
                cachedWhitelist = Collections.unmodifiableList(extractStringList(list));
            }
            if (fileConfig.get("extinctionBlacklist") instanceof List<?> list) {
                cachedBlacklist = Collections.unmodifiableList(extractStringList(list));
            }
            if (fileConfig.get("dimensionBlacklist") instanceof List<?> list) {
                cachedDimensionBlacklist = parseDimensionBlacklist(extractStringList(list));
            }
            if (fileConfig.get("extinctionRadius") instanceof Number n) {
                cachedExtinctionRadius = sanitizeOddRadius(n.intValue(), DEFAULT_RADIUS, "extinctionRadius");
            } else if (fileConfig.get("radius") instanceof Number n) {
                cachedExtinctionRadius = sanitizeOddRadius(n.intValue(), DEFAULT_RADIUS, "radius");
            }
            if (fileConfig.get("reviveRadius") instanceof Number n) {
                cachedReviveRadius = sanitizeOddRadius(n.intValue(), DEFAULT_RADIUS, "reviveRadius");
            }
            if (fileConfig.get("minAdults") instanceof Number n) {
                cachedMinAdults = Math.max(1, n.intValue());
            }
            if (fileConfig.get("survivalChanceWithYoung") instanceof Number n) {
                cachedSurvivalChanceWithYoung = clampPercent(n.intValue());
            }
            if (fileConfig.get("reviveMinAdults") instanceof Number n) {
                cachedReviveMinAdults = Math.max(1, n.intValue());
            }
            if (fileConfig.get("reviveBaseChance") instanceof Number n) {
                cachedReviveBaseChance = clampPercent(n.intValue());
            }
            if (fileConfig.get("reviveStep") instanceof Number n) {
                cachedReviveStep = clampPercent(n.intValue());
            }
            if (fileConfig.get("countNonPlayerDeaths") instanceof Boolean b) {
                cachedCountNonPlayerDeaths = b;
            }
            if (fileConfig.get("trackPlayerHazards") instanceof Boolean b) {
                cachedTrackPlayerHazards = b;
            }
            if (fileConfig.get("maxRecords") instanceof Number n) {
                cachedMaxRecords = Math.max(1, n.intValue());
            }
            if (fileConfig.get("joinMessageMode") instanceof String s) {
                cachedJoinMessageMode = JoinMessageMode.fromString(s);
            }
        } catch (Exception ex) {
            GenocideMod.LOGGER.error("Failed to reload config/genocide.toml from disk", ex);
        }
    }

    public static synchronized void loadBiomeConfigsFromDisk() {
        Path biomesDir = FMLPaths.CONFIGDIR.get().resolve("genocide").resolve("biomes");
        try {
            Files.createDirectories(biomesDir);
        } catch (IOException ex) {
            GenocideMod.LOGGER.error("Failed to create config/genocide/biomes directory", ex);
            cachedBiomeRules = Collections.emptyList();
            return;
        }

        List<BiomeRuleConfig> loadedRules = new ArrayList<>();
        try (Stream<Path> paths = Files.list(biomesDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                    .forEach(path -> {
                        BiomeRuleConfig rule = parseBiomeFile(path);
                        if (rule != null && rule.isValidTarget()) {
                            loadedRules.add(rule);
                        }
                    });
        } catch (IOException ex) {
            GenocideMod.LOGGER.error("Failed to scan config/genocide/biomes directory", ex);
        }

        loadedRules.sort(BiomeRuleConfig.PRIORITY_COMPARATOR);
        cachedBiomeRules = Collections.unmodifiableList(loadedRules);
    }

    @Nullable
    private static BiomeRuleConfig parseBiomeFile(Path path) {
        String fileName = path.getFileName().toString();
        try (FileConfig config = FileConfig.of(path)) {
            config.load();
            Object biomeObj = config.get("biome");
            if (!(biomeObj instanceof String biomeStr) || biomeStr.isBlank()) {
                GenocideMod.LOGGER.warn("Skipping biome config {} because 'biome' field is missing or empty", fileName);
                return null;
            }

            Boolean enabled = config.get("enabled") instanceof Boolean b ? b : null;

            Integer extinctionRadius = null;
            if (config.get("extinctionRadius") instanceof Number n) {
                extinctionRadius = parseOptionalOddRadius(n.intValue(), fileName + ":extinctionRadius");
            } else if (config.get("radius") instanceof Number n) {
                extinctionRadius = parseOptionalOddRadius(n.intValue(), fileName + ":radius");
            }

            Integer reviveRadius = null;
            if (config.get("reviveRadius") instanceof Number n) {
                reviveRadius = parseOptionalOddRadius(n.intValue(), fileName + ":reviveRadius");
            }

            Integer minAdults = config.get("minAdults") instanceof Number n ? n.intValue() : null;
            List<String> whitelist = extractStringList(config.get("extinctionWhitelist"));
            List<String> blacklist = extractStringList(config.get("extinctionBlacklist"));

            return new BiomeRuleConfig(
                    fileName,
                    biomeStr,
                    enabled,
                    extinctionRadius,
                    reviveRadius,
                    minAdults,
                    whitelist,
                    blacklist
            );
        } catch (Exception ex) {
            GenocideMod.LOGGER.error("Failed to parse biome config file {}", fileName, ex);
            return null;
        }
    }

    @Nullable
    private static Integer parseOptionalOddRadius(int value, String source) {
        if (isValidOddRadius(value)) {
            return value;
        }
        GenocideMod.LOGGER.warn(
                "Ignoring invalid radius {} in {} (must be an odd integer: 1=1x1, 3=3x3, 5=5x5...). Falling back to main config.",
                value, source
        );
        return null;
    }

    private static List<String> extractStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String s && !s.isBlank()) {
                result.add(s.trim());
            }
        }
        return result;
    }

    private static List<String> copyStringList(List<? extends String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                result.add(s.trim());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<MobCategory> parseCategories(List<? extends String> names) {
        EnumSet<MobCategory> set = EnumSet.noneOf(MobCategory.class);
        if (names == null) {
            return set;
        }
        for (String rawName : names) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            String trimmed = rawName.trim();
            for (MobCategory cat : MobCategory.values()) {
                if (cat.name().equalsIgnoreCase(trimmed) || cat.getName().equalsIgnoreCase(trimmed)) {
                    set.add(cat);
                    break;
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static Set<String> parseDimensionBlacklist(List<? extends String> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>(rawList.size());
        for (String raw : rawList) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(trimmed);
            set.add(loc != null ? loc.toString() : trimmed);
        }
        return Collections.unmodifiableSet(set);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static Set<MobCategory> categories() {
        return cachedCategories;
    }

    public static List<String> extinctionWhitelist() {
        return cachedWhitelist;
    }

    public static List<String> extinctionBlacklist() {
        return cachedBlacklist;
    }

    public static Set<String> dimensionBlacklist() {
        return cachedDimensionBlacklist;
    }

    public static int extinctionRadius() {
        return cachedExtinctionRadius;
    }

    public static int reviveRadius() {
        return cachedReviveRadius;
    }

    public static int minAdults() {
        return cachedMinAdults;
    }

    public static int survivalChanceWithYoung() {
        return cachedSurvivalChanceWithYoung;
    }

    public static int reviveMinAdults() {
        return cachedReviveMinAdults;
    }

    public static int reviveBaseChance() {
        return cachedReviveBaseChance;
    }

    public static int reviveStep() {
        return cachedReviveStep;
    }

    public static boolean countNonPlayerDeaths() {
        return cachedCountNonPlayerDeaths;
    }

    public static boolean trackPlayerHazards() {
        return cachedTrackPlayerHazards;
    }

    public static int maxRecords() {
        return cachedMaxRecords;
    }

    public static JoinMessageMode joinMessageMode() {
        return cachedJoinMessageMode;
    }

    public static List<BiomeRuleConfig> biomeRules() {
        return cachedBiomeRules;
    }
}
