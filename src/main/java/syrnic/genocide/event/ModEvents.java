package syrnic.genocide.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import syrnic.genocide.command.GenocideCommands;
import syrnic.genocide.config.GenocideConfig;
import syrnic.genocide.logic.ExtinctionCheckManager;
import syrnic.genocide.logic.HazardTracker;
import syrnic.genocide.logic.JoinMessageManager;

import java.util.concurrent.CompletableFuture;

public final class ModEvents {
    @SubscribeEvent
    public void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (ExtinctionCheckManager.shouldBlockNaturalSpawn(
                event.getLevel(),
                event.getEntityType(),
                event.getSpawnType(),
                event.getPos()
        )) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (ExtinctionCheckManager.shouldBlockNaturalSpawn(
                event.getLevel(),
                mob.getType(),
                event.getSpawnType(),
                pos
        )) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (ExtinctionCheckManager.shouldBlockNaturalSpawn(
                event.getLevel(),
                mob.getType(),
                event.getSpawnType(),
                pos
        )) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        ExtinctionCheckManager.onEntityDeath(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        ExtinctionCheckManager.onBabySpawn(event.getParentA(), event.getParentB(), event.getChild());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (placed.is(BlockTags.FIRE) || placed.getFluidState().is(FluidTags.LAVA)) {
            HazardTracker.markChunkHazard(level, new ChunkPos(event.getPos()), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onVanillaGameEvent(VanillaGameEvent event) {
        if (!(event.getCause() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getEventPosition());
        if (event.getVanillaEvent().is(GameEvent.FLUID_PLACE.key())) {
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                HazardTracker.markChunkHazard(level, new ChunkPos(pos), player);
            }
        } else if (event.getVanillaEvent().is(GameEvent.BLOCK_PLACE.key())
                || event.getVanillaEvent().is(GameEvent.BLOCK_CHANGE.key())) {
            if (!isHoldingFireStarter(player)) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state)) {
                HazardTracker.markChunkHazard(level, new ChunkPos(pos), player);
            } else if (event.getVanillaEvent().is(GameEvent.BLOCK_PLACE.key())
                    && event.getContext().affectedState() == null
                    && hasAdjacentFire(level, pos)) {
                HazardTracker.markChunkHazard(level, new ChunkPos(pos), player);
            }
        }
    }

    private static boolean hasAdjacentFire(ServerLevel level, BlockPos pos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(BlockTags.FIRE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHoldingFireStarter(ServerPlayer player) {
        return player.getMainHandItem().is(Items.FLINT_AND_STEEL)
                || player.getMainHandItem().is(Items.FIRE_CHARGE)
                || player.getOffhandItem().is(Items.FLINT_AND_STEEL)
                || player.getOffhandItem().is(Items.FIRE_CHARGE);
    }

    @SubscribeEvent
    public void onEnteringSection(EntityEvent.EnteringSection event) {
        if (!event.didChunkChange()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            HazardTracker.removePlayerMarkFromChunk(
                    player.level().dimension(),
                    event.getOldPos().chunk(),
                    player.getUUID()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JoinMessageManager.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HazardTracker.clearAllPlayerMarks(player.getUUID());
            JoinMessageManager.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HazardTracker.clearAllPlayerMarks(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        JoinMessageManager.tickServer(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GenocideCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener((PreparableReloadListener) (
                preparationBarrier,
                resourceManager,
                preparationsProfiler,
                reloadProfiler,
                backgroundExecutor,
                gameExecutor
        ) -> CompletableFuture.supplyAsync(() -> null, backgroundExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenRunAsync(() -> {
                    GenocideConfig.reloadAll();
                    net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        syrnic.genocide.data.GenocideDataManager.enforceMaxRecords(server);
                    }
                }, gameExecutor));
    }
}
