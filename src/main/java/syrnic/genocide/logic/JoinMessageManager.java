package syrnic.genocide.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import syrnic.genocide.config.GenocideConfig;
import syrnic.genocide.config.JoinMessageMode;
import syrnic.genocide.data.GenocideDataManager;
import syrnic.genocide.data.GenocideSavedData;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JoinMessageManager {
    private static final Component WARNING_TEXT = Component.literal("Внимание, мобы не респавнятся!");
    private static final long DISPLAY_DURATION_MS = 60_000L;
    private static final int RESEND_INTERVAL_TICKS = 10;

    private static final class PendingJoinState {
        double baselineX;
        double baselineY;
        double baselineZ;
        boolean baselineInitialized;
        int settleTicks;
    }

    private static final class ActiveTimer {
        final long endTimeMs;
        int tickCounter;

        ActiveTimer(long endTimeMs) {
            this.endTimeMs = endTimeMs;
            this.tickCounter = 0;
        }
    }

    private static final Map<UUID, PendingJoinState> PENDING_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveTimer> ACTIVE_TIMERS = new ConcurrentHashMap<>();

    private JoinMessageManager() {
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        JoinMessageMode mode = GenocideConfig.joinMessageMode();
        if (mode == JoinMessageMode.OFF) {
            return;
        }

        UUID uuid = player.getUUID();
        if (mode == JoinMessageMode.FIRST_JOIN) {
            GenocideSavedData globalData = GenocideDataManager.getGlobalData(player.server);
            if (globalData.hasPlayerSeenJoinMessage(uuid)) {
                return;
            }
        }

        PendingJoinState state = new PendingJoinState();
        state.baselineX = player.getX();
        state.baselineY = player.getY();
        state.baselineZ = player.getZ();
        state.baselineInitialized = false;
        state.settleTicks = 3;
        PENDING_PLAYERS.put(uuid, state);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // If player leaves before first movement, mark is NOT set
        PENDING_PLAYERS.remove(uuid);
        ACTIVE_TIMERS.remove(uuid);
    }

    public static void onPlayerTeleported(ServerPlayer player, double x, double y, double z) {
        PendingJoinState state = PENDING_PLAYERS.get(player.getUUID());
        if (state != null) {
            state.baselineX = x;
            state.baselineY = y;
            state.baselineZ = z;
            state.baselineInitialized = true;
            state.settleTicks = 3;
        }
    }

    /**
     * Primary signal: ServerboundPlayerInputPacket (WASD, jump, sneak).
     */
    public static void onPlayerInputPacket(ServerPlayer player, float xxa, float zza, boolean jumping, boolean shiftKeyDown) {
        if (!PENDING_PLAYERS.containsKey(player.getUUID())) {
            return;
        }
        if (Math.abs(xxa) > 1.0E-4F || Math.abs(zza) > 1.0E-4F || jumping || shiftKeyDown) {
            startTimerForPlayer(player);
        }
    }

    /**
     * Additional signal: ServerboundPlayerCommandPacket (sneak / sprint key press on foot).
     */
    public static void onPlayerCommandKey(ServerPlayer player) {
        if (!PENDING_PLAYERS.containsKey(player.getUUID())) {
            return;
        }
        startTimerForPlayer(player);
    }

    /**
     * Fallback signal: ServerboundMovePlayerPacket not caused by teleport, knockback, or vehicle.
     */
    public static void onPlayerMovePacket(
            ServerPlayer player,
            double targetX,
            double targetY,
            double targetZ,
            boolean awaitingTeleport
    ) {
        PendingJoinState state = PENDING_PLAYERS.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (awaitingTeleport || player.isPassenger() || player.hurtTime > 0 || player.hurtMarked || player.isChangingDimension()) {
            state.baselineX = targetX;
            state.baselineY = targetY;
            state.baselineZ = targetZ;
            state.baselineInitialized = true;
            return;
        }

        if (!state.baselineInitialized || state.settleTicks > 0) {
            state.baselineX = targetX;
            state.baselineY = targetY;
            state.baselineZ = targetZ;
            state.baselineInitialized = true;
            if (state.settleTicks > 0) {
                state.settleTicks--;
            }
            return;
        }

        double dx = targetX - state.baselineX;
        double dy = targetY - state.baselineY;
        double dz = targetZ - state.baselineZ;

        state.baselineX = targetX;
        state.baselineY = targetY;
        state.baselineZ = targetZ;

        double horizontalSq = dx * dx + dz * dz;
        // Intentional horizontal movement (WASD) or upward jump (dy > 0.05)
        if (horizontalSq > 0.0004D || dy > 0.05D) {
            startTimerForPlayer(player);
        }
    }

    private static void startTimerForPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (PENDING_PLAYERS.remove(uuid) == null) {
            return;
        }

        if (GenocideConfig.joinMessageMode() == JoinMessageMode.OFF) {
            return;
        }

        // Immediately record that the player has seen the message in SavedData
        GenocideSavedData globalData = GenocideDataManager.getGlobalData(player.server);
        globalData.markPlayerSeenJoinMessage(uuid);

        long endTime = System.currentTimeMillis() + DISPLAY_DURATION_MS;
        ACTIVE_TIMERS.put(uuid, new ActiveTimer(endTime));
        sendActionBar(player, WARNING_TEXT);
    }

    /**
     * The only tick handler in the mod: sends actionbar packets strictly to players with an active timer.
     */
    public static void tickServer(MinecraftServer server) {
        if (ACTIVE_TIMERS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, ActiveTimer>> it = ACTIVE_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveTimer> entry = it.next();
            UUID uuid = entry.getKey();
            ActiveTimer timer = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                it.remove();
                continue;
            }

            if (now >= timer.endTimeMs) {
                sendActionBar(player, Component.empty());
                it.remove();
                continue;
            }

            timer.tickCounter++;
            if (timer.tickCounter >= RESEND_INTERVAL_TICKS) {
                timer.tickCounter = 0;
                sendActionBar(player, WARNING_TEXT);
            }
        }
    }

    private static void sendActionBar(ServerPlayer player, Component component) {
        player.connection.send(new ClientboundSetActionBarTextPacket(component));
    }
}
