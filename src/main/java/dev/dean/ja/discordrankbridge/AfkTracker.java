package dev.dean.ja.discordrankbridge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player activity and determines AFK status.
 * Players are marked AFK after configurable seconds of inactivity.
 * Tracks: movement, rotation, chat, commands, and block interactions.
 */
public class AfkTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AfkTracker.class);

    // Last activity timestamp per player UUID
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    // Current AFK state per player
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    // Last known position (x, y, z) per player for movement detection
    private final Map<UUID, double[]> lastPosition = new ConcurrentHashMap<>();
    // Last known rotation (yaw, pitch) per player
    private final Map<UUID, float[]> lastRotation = new ConcurrentHashMap<>();

    // Thresholds are loaded from MessageConfig

    public AfkTracker() {
        LOGGER.info("[DiscordRankBridge] AFK Tracker initialized.");
    }

    /**
     * Called on player join - start tracking.
     */
    public void onPlayerJoin(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        afkPlayers.remove(uuid);
        lastPosition.remove(uuid);
        lastRotation.remove(uuid);
    }

    /**
     * Called on player leave - stop tracking.
     */
    public void onPlayerLeave(UUID uuid) {
        lastActivity.remove(uuid);
        afkPlayers.remove(uuid);
        lastPosition.remove(uuid);
        lastRotation.remove(uuid);
    }

    /**
     * Mark a player as active (resets AFK timer).
     * Called on chat, commands, interactions.
     */
    public void markActive(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        if (afkPlayers.remove(uuid)) {
            LOGGER.debug("[AfkTracker] Player {} returned from AFK", uuid);
        }
    }

    /**
     * Called every server tick. Checks player positions/rotations and updates AFK state.
     * Only processes every 20 ticks (1 second) for performance.
     */
    private int tickCounter = 0;

    public void onServerTick() {
        tickCounter++;
        if (tickCounter < 20) return; // Only check every second
        tickCounter = 0;

        int timeoutSeconds = Config.COMMON.afkTimeout.get();
        if (timeoutSeconds <= 0) return; // AFK disabled

        long now = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();

            // Check movement and rotation
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            float yaw = player.getYRot();
            float pitch = player.getXRot();

            double[] prevPos = lastPosition.get(uuid);
            float[] prevRot = lastRotation.get(uuid);

            boolean moved = false;

            if (prevPos != null) {
                double moveTh = MessageConfig.get().afk.movementThreshold;
                double dx = Math.abs(x - prevPos[0]);
                double dy = Math.abs(y - prevPos[1]);
                double dz = Math.abs(z - prevPos[2]);
                if (dx > moveTh || dy > moveTh || dz > moveTh) {
                    moved = true;
                }
            }

            if (!moved && prevRot != null) {
                double rotTh = MessageConfig.get().afk.rotationThreshold;
                float dYaw = Math.abs(yaw - prevRot[0]);
                float dPitch = Math.abs(pitch - prevRot[1]);
                if (dYaw > rotTh || dPitch > rotTh) {
                    moved = true;
                }
            }

            // Update stored position/rotation
            lastPosition.put(uuid, new double[]{x, y, z});
            lastRotation.put(uuid, new float[]{yaw, pitch});

            if (moved) {
                markActive(uuid);
            }

            // Check AFK timeout
            Long lastAct = lastActivity.get(uuid);
            if (lastAct != null && (now - lastAct) > timeoutMs) {
                if (afkPlayers.add(uuid)) {
                    LOGGER.debug("[AfkTracker] Player {} is now AFK", player.getName().getString());
                }
            }
        }
    }

    /**
     * Returns whether a player is currently AFK.
     */
    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    /**
     * Returns the AFK display tag for a player.
     * @return "§7[AFK] " if AFK, empty string if not.
     */
    public String getAfkTag(UUID uuid) {
        return isAfk(uuid) ? MessageConfig.get().minecraft.afkTag : "";
    }
}
