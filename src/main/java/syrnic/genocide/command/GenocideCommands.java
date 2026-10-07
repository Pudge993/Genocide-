package syrnic.genocide.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import syrnic.genocide.config.GenocideConfig;
import syrnic.genocide.data.ExtinctionRecord;
import syrnic.genocide.data.GenocideDataManager;
import syrnic.genocide.data.GenocideSavedData;
import syrnic.genocide.logic.ChunkRadiusHelper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GenocideCommands {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final SuggestionProvider<CommandSourceStack> SPECIES_SUGGESTIONS = (context, builder) -> {
        Set<ResourceLocation> suggestions = new HashSet<>(BuiltInRegistries.ENTITY_TYPE.keySet());
        ServerLevel level = context.getSource().getLevel();
        suggestions.addAll(GenocideDataManager.get(level).getAllRecordedSpecies());
        return SharedSuggestionProvider.suggestResource(suggestions, builder);
    };

    private GenocideCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("genocide")
                .requires(source -> source.hasPermission(2));

        // /genocide status [<pos>]
        root.then(Commands.literal("status")
                .executes(ctx -> executeStatus(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> executeStatus(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))));

        // /genocide reset ...
        LiteralArgumentBuilder<CommandSourceStack> resetNode = Commands.literal("reset");

        // /genocide reset at <pos> <species>
        // /genocide reset at radius <radius> <pos> <species>
        LiteralArgumentBuilder<CommandSourceStack> resetAtNode = Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("species", ResourceLocationArgument.id())
                                .suggests(SPECIES_SUGGESTIONS)
                                .executes(ctx -> executeResetChunk(
                                        ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                        ResourceLocationArgument.getId(ctx, "species")
                                ))))
                .then(buildRadiusBranch("radius", false));

        // /genocide reset all at <pos>
        // /genocide reset all at radius <radius> <pos>
        LiteralArgumentBuilder<CommandSourceStack> resetAllNode = Commands.literal("all")
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> executeResetChunk(
                                         ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                        null
                                )))
                        .then(buildRadiusBranch("radius", true)));

        resetNode.then(resetAtNode);
        resetNode.then(resetAllNode);
        root.then(resetNode);

        // /genocide reload
        root.then(Commands.literal("reload")
                .executes(GenocideCommands::executeReload));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRadiusBranch(String literalName, boolean allOnly) {
        var radiusArg = Commands.argument("radius", IntegerArgumentType.integer(1, GenocideConfig.MAX_ODD_RADIUS));
        var posArg = Commands.argument("pos", BlockPosArgument.blockPos());

        if (allOnly) {
            posArg.executes(ctx -> executeResetRadius(
                    ctx.getSource(),
                    IntegerArgumentType.getInteger(ctx, "radius"),
                    BlockPosArgument.getBlockPos(ctx, "pos"),
                    null
            ));
        } else {
            posArg.then(Commands.argument("species", ResourceLocationArgument.id())
                    .suggests(SPECIES_SUGGESTIONS)
                    .executes(ctx -> executeResetRadius(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            BlockPosArgument.getBlockPos(ctx, "pos"),
                            ResourceLocationArgument.getId(ctx, "species")
                    )));
        }

        return Commands.literal(literalName).then(radiusArg.then(posArg));
    }

    private static int executeStatus(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        ChunkPos chunkPos = new ChunkPos(pos);
        GenocideSavedData savedData = GenocideDataManager.get(level);
        List<ExtinctionRecord> records = savedData.getRecordsInChunk(chunkPos);

        if (records.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "В чанке [%d, %d] (точка %d, %d, %d) нет записей о геноциде.",
                            chunkPos.x, chunkPos.z, pos.getX(), pos.getY(), pos.getZ()
                    )),
                    false
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(String.format(
                        "Записи о геноциде в чанке [%d, %d] (%d шт.):",
                        chunkPos.x, chunkPos.z, records.size()
                )),
                false
        );

        for (int i = 0; i < records.size(); i++) {
            ExtinctionRecord rec = records.get(i);
            String timeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(rec.createdAt()));
            String killerStr;
            if (rec.killerName() != null && rec.killerUuid() != null) {
                killerStr = rec.killerName() + " (" + rec.killerUuid() + ")";
            } else if (rec.killerUuid() != null) {
                killerStr = rec.killerUuid().toString();
            } else {
                killerStr = "отсутствует";
            }

            BlockPos center = rec.center();
            int r = rec.radius();
            int index = i + 1;
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "%d) Вид: %s | Центр: (%d, %d, %d) | Радиус: %d (%dx%d чанков) | Биом: %s | Время: %s | Игрок: %s",
                            index,
                            rec.species(),
                            center.getX(), center.getY(), center.getZ(),
                            r, r, r,
                            rec.biome(),
                            timeStr,
                            killerStr
                    )),
                    false
            );
        }

        return records.size();
    }

    private static int executeResetChunk(CommandSourceStack source, BlockPos pos, @Nullable ResourceLocation species) {
        ServerLevel level = source.getLevel();
        ChunkPos chunkPos = new ChunkPos(pos);
        GenocideSavedData savedData = GenocideDataManager.get(level);
        int removed = savedData.removeRecords(chunkPos, species, null);

        if (species != null) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Удалено записей для вида %s в чанке [%d, %d]: %d.",
                            species, chunkPos.x, chunkPos.z, removed
                    )),
                    true
            );
        } else {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Удалено всех записей в чанке [%d, %d]: %d.",
                            chunkPos.x, chunkPos.z, removed
                    )),
                    true
            );
        }
        return removed;
    }

    private static int executeResetRadius(
            CommandSourceStack source,
            int oddRadius,
            BlockPos pos,
            @Nullable ResourceLocation species
    ) {
        if (!GenocideConfig.isValidOddRadius(oddRadius)) {
            source.sendFailure(Component.literal(
                    "Радиус должен быть нечётным числом (1=1x1, 3=3x3, 5=5x5...)."
            ));
            return 0;
        }

        ServerLevel level = source.getLevel();
        ChunkPos centerChunk = new ChunkPos(pos);
        List<ChunkPos> chunks = ChunkRadiusHelper.getChunksForOddRadius(centerChunk, oddRadius);

        GenocideSavedData savedData = GenocideDataManager.get(level);
        int totalRemoved = 0;
        for (ChunkPos chunkPos : chunks) {
            totalRemoved += savedData.removeRecords(chunkPos, species, null);
        }

        int finalRemoved = totalRemoved;
        if (species != null) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Удалено записей для вида %s в радиусе %d (%dx%d чанков вокруг [%d, %d]): %d.",
                            species, oddRadius, oddRadius, oddRadius, centerChunk.x, centerChunk.z, finalRemoved
                    )),
                    true
            );
        } else {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Удалено всех записей в радиусе %d (%dx%d чанков вокруг [%d, %d]): %d.",
                            oddRadius, oddRadius, oddRadius, centerChunk.x, centerChunk.z, finalRemoved
                    )),
                    true
            );
        }
        return totalRemoved;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        int biomeCount = GenocideConfig.reloadAll();
        GenocideDataManager.enforceMaxRecords(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Конфигурация Genocide перезагружена (правил биомов: %d).",
                        biomeCount
                )),
                true
        );
        return 1;
    }
}
