package discordrankbridge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces verification requirements by applying debuffs and restrictions
 * to players who have not linked their Discord account.
 */
public class VerificationEnforcer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationEnforcer.class);
    
    // Track unverified players
    private final Set<UUID> unverifiedPlayers = ConcurrentHashMap.newKeySet();
    
    private final VerificationService verificationService;
    
    // Tick counters for periodic actions
    private int debuffTickCounter = 0;
    private int titleTickCounter = 0;
    private int radiusCheckCounter = 0;

    public VerificationEnforcer(VerificationService verificationService) {
        this.verificationService = verificationService;
        LOGGER.info("[DiscordRankBridge] VerificationEnforcer initialized.");
    }

    /**
     * Called when a player joins the server.
     * Checks verification status and starts enforcement if needed.
     */
    public void onPlayerJoin(ServerPlayer player) {
        if (!Config.COMMON.verificationEnabled.get()) return;
        
        String playerName = player.getName().getString();
        UUID uuid = player.getUUID();
        
        if (!verificationService.isVerified(playerName)) {
            unverifiedPlayers.add(uuid);
            LOGGER.info("[VerificationEnforcer] Player {} is not verified - applying restrictions", playerName);
            
            // Teleport to jail if enabled
            if (Config.COMMON.jailEnabled.get()) {
                teleportToJail(player);
            }
            
            // Apply debuffs immediately
            applyDebuffs(player);
            
            // Show verification title immediately
            showVerificationTitle(player);
        } else {
            LOGGER.info("[VerificationEnforcer] Player {} is verified - no restrictions applied", playerName);
        }
    }

    /**
     * Called when a player leaves the server.
     * Stops tracking the player.
     */
    public void onPlayerLeave(UUID uuid) {
        unverifiedPlayers.remove(uuid);
    }

    /**
     * Called when a player respawns.
     * Re-applies debuffs immediately if player is unverified.
     */
    public void onPlayerRespawn(ServerPlayer player) {
        if (!Config.COMMON.verificationEnabled.get()) return;
        
        UUID uuid = player.getUUID();
        if (unverifiedPlayers.contains(uuid)) {
            LOGGER.debug("[VerificationEnforcer] Player {} respawned - reapplying debuffs", player.getName().getString());
            applyDebuffs(player);
        }
    }

    /**
     * Called when a player successfully verifies their account.
     * Removes all restrictions and cleans up tracking.
     */
    public void onPlayerVerified(UUID uuid) {
        if (unverifiedPlayers.remove(uuid)) {
            LOGGER.info("[VerificationEnforcer] Player {} verified - removing restrictions", uuid);
            
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    removeDebuffs(player);
                    clearTitle(player);
                    
                    // Teleport to spawn if jail is enabled
                    if (Config.COMMON.jailEnabled.get()) {
                        teleportToSpawn(player);
                    }
                }
            }
        }
    }

    /**
     * Called when a player unlinks their Discord account.
     * Reapplies all restrictions and starts tracking.
     */
    public void onPlayerUnlinked(ServerPlayer player) {
        if (!Config.COMMON.verificationEnabled.get()) return;
        
        UUID uuid = player.getUUID();
        if (!unverifiedPlayers.contains(uuid)) {
            unverifiedPlayers.add(uuid);
            LOGGER.info("[VerificationEnforcer] Player {} unlinked - applying restrictions", player.getName().getString());
            
            // Apply debuffs immediately
            applyDebuffs(player);
            
            // Show verification title immediately
            showVerificationTitle(player);
        }
    }

    /**
     * Checks if a player is allowed to pick up items.
     * Unverified players cannot pick up items.
     */
    public boolean canPickupItems(UUID uuid) {
        if (!Config.COMMON.verificationEnabled.get()) return true;
        return !unverifiedPlayers.contains(uuid);
    }

    /**
     * Called every server tick.
     * Handles periodic debuff and title reapplication.
     */
    public void onServerTick() {
        if (!Config.COMMON.verificationEnabled.get()) return;
        if (unverifiedPlayers.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Reapply debuffs every N seconds
        debuffTickCounter++;
        int debuffInterval = Config.COMMON.debuffReapplyInterval.get() * 20; // Convert seconds to ticks
        if (debuffTickCounter >= debuffInterval) {
            debuffTickCounter = 0;
            reapplyDebuffs(server);
        }

        // Re-show title every N seconds
        titleTickCounter++;
        int titleInterval = Config.COMMON.titleInterval.get() * 20; // Convert seconds to ticks
        if (titleTickCounter >= titleInterval) {
            titleTickCounter = 0;
            reapplyTitles(server);
        }

        // Check jail radius every 5 seconds (100 ticks)
        if (Config.COMMON.jailEnabled.get()) {
            radiusCheckCounter++;
            if (radiusCheckCounter >= 100) {
                radiusCheckCounter = 0;
                checkAllJailRadii(server);
            }
        }
    }

    /**
     * Applies blindness and slowness effects to a player.
     */
    private void applyDebuffs(ServerPlayer player) {
        // Blindness Level 255 (infinite duration)
        player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                MobEffectInstance.INFINITE_DURATION,
                254, // Level 255 (0-based index, so 254)
                false,
                false,
                true
        ));
        
        // Slowness Level 255 (infinite duration)
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                MobEffectInstance.INFINITE_DURATION,
                254, // Level 255 (0-based index, so 254)
                false,
                false,
                true
        ));
    }

    /**
     * Removes verification debuffs from a player.
     */
    private void removeDebuffs(ServerPlayer player) {
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        LOGGER.info("[VerificationEnforcer] Removed debuffs from player {}", player.getName().getString());
    }

    /**
     * Shows the verification title to a player.
     */
    private void showVerificationTitle(ServerPlayer player) {
        String title = Config.COMMON.verifyTitle.get();
        String subtitle = Config.COMMON.verifySubtitle.get();
        
        player.displayClientMessage(Component.literal(title), false);
        player.sendSystemMessage(Component.literal("§c" + title));
        player.sendSystemMessage(Component.literal("§7" + subtitle));
        
        // Send actual title packet
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                10, // fadeIn (ticks)
                70, // stay (ticks)
                20  // fadeOut (ticks)
        ));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal("§c§l" + title)
        ));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal("§7" + subtitle)
        ));
    }

    /**
     * Clears the title display for a player.
     */
    private void clearTitle(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
    }

    /**
     * Reapplies debuffs to all unverified players currently online.
     */
    private void reapplyDebuffs(net.minecraft.server.MinecraftServer server) {
        for (UUID uuid : unverifiedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                applyDebuffs(player);
            }
        }
    }

    /**
     * Re-shows verification title to all unverified players currently online.
     */
    private void reapplyTitles(net.minecraft.server.MinecraftServer server) {
        for (UUID uuid : unverifiedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                showVerificationTitle(player);
            }
        }
    }

    /**
     * Teleports a player to the jail location.
     */
    private void teleportToJail(ServerPlayer player) {
        double x = Config.COMMON.jailX.get();
        double y = Config.COMMON.jailY.get();
        double z = Config.COMMON.jailZ.get();
        
        player.teleportTo(x, y, z);
        LOGGER.info("[VerificationEnforcer] Teleported {} to jail ({}, {}, {})", 
                player.getName().getString(), x, y, z);
    }

    /**
     * Teleports a player to the spawn location.
     */
    private void teleportToSpawn(ServerPlayer player) {
        double x = Config.COMMON.spawnX.get();
        double y = Config.COMMON.spawnY.get();
        double z = Config.COMMON.spawnZ.get();
        
        player.teleportTo(x, y, z);
        player.sendSystemMessage(Component.literal("§a§lVerification successful! Welcome to the server!"));
        LOGGER.info("[VerificationEnforcer] Teleported {} to spawn ({}, {}, {})", 
                player.getName().getString(), x, y, z);
    }

    /**
     * Checks if a player is within the jail radius.
     * Returns true if player is within radius or jail is disabled.
     */
    private boolean isInJailRadius(ServerPlayer player) {
        double jailX = Config.COMMON.jailX.get();
        double jailY = Config.COMMON.jailY.get();
        double jailZ = Config.COMMON.jailZ.get();
        int radius = Config.COMMON.jailRadius.get();
        
        double dx = player.getX() - jailX;
        double dy = player.getY() - jailY;
        double dz = player.getZ() - jailZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double radiusSquared = radius * radius;
        
        return distanceSquared <= radiusSquared;
    }

    /**
     * Checks all unverified players and teleports them back to jail if they left the radius.
     */
    private void checkAllJailRadii(net.minecraft.server.MinecraftServer server) {
        for (UUID uuid : unverifiedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && !isInJailRadius(player)) {
                LOGGER.debug("[VerificationEnforcer] Player {} left jail radius - teleporting back", 
                        player.getName().getString());
                teleportToJail(player);
                player.sendSystemMessage(Component.literal("§c§lYou must verify your account before leaving this area!"));
            }
        }
    }
}
