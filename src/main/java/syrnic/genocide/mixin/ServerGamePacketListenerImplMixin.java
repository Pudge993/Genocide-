package syrnic.genocide.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import syrnic.genocide.logic.JoinMessageManager;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    @Nullable
    private Vec3 awaitingPositionFromClient;

    @Shadow
    private int tickCount;

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void genocide$onHandlePlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (this.player != null && this.player.server.isSameThread()) {
            JoinMessageManager.onPlayerInputPacket(
                    this.player,
                    packet.getXxa(),
                    packet.getZza(),
                    packet.isJumping(),
                    packet.isShiftKeyDown()
            );
        }
    }

    @Inject(method = "handlePlayerCommand", at = @At("TAIL"))
    private void genocide$onHandlePlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (this.player != null && this.player.server.isSameThread()) {
            ServerboundPlayerCommandPacket.Action action = packet.getAction();
            if (action == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                    || action == ServerboundPlayerCommandPacket.Action.START_SPRINTING
                    || action == ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP) {
                JoinMessageManager.onPlayerCommandKey(this.player);
            }
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void genocide$onHandleMovePlayerHead(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (this.player == null || !this.player.server.isSameThread()) {
            return;
        }
        if (packet.hasPosition()) {
            double targetX = packet.getX(this.player.getX());
            double targetY = packet.getY(this.player.getY());
            double targetZ = packet.getZ(this.player.getZ());
            boolean awaitingTeleport = this.awaitingPositionFromClient != null || this.tickCount == 0;
            JoinMessageManager.onPlayerMovePacket(this.player, targetX, targetY, targetZ, awaitingTeleport);
        }
    }

    @Inject(
            method = "teleport(DDDFFLjava/util/Set;)V",
            at = @At("TAIL")
    )
    private void genocide$onTeleport(
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Set<RelativeMovement> relativeSet,
            CallbackInfo ci
    ) {
        if (this.player != null && this.player.server.isSameThread()) {
            JoinMessageManager.onPlayerTeleported(this.player, this.player.getX(), this.player.getY(), this.player.getZ());
        }
    }
}
