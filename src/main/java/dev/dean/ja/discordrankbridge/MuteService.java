package dev.dean.ja.discordrankbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage player mutes (temporary and permanent).
 */
public class MuteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MuteService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path configDir;
    private final Path muteFile;
    private final Map<UUID, MuteData> mutedPlayers = new ConcurrentHashMap<>();
    
    private int tickCounter = 0;

    public MuteService(Path configDir) {
        this.configDir = configDir;
        this.muteFile = configDir.resolve("muted_players.json");
        loadMutes();
    }

    /**
     * Mutes a player.
     * 
     * @param minecraftName Player's username
     * @param durationMinutes Duration in minutes (null = permanent)
     * @param reason Mute reason
     * @return true if successful
     */
    public boolean mutePlayer(String minecraftName, Integer durationMinutes, String reason) {
        if (minecraftName == null || minecraftName.isEmpty()) {
            LOGGER.warn("Cannot mute player: minecraftName is null or empty");
            return false;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("Cannot mute player: server not available");
            return false;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(minecraftName);
        UUID uuid;
        
        if (player != null) {
            uuid = player.getUUID();
        } else {
            // Player offline, try to get UUID from user cache
            var optUuid = server.getProfileCache().get(minecraftName);
            if (optUuid.isEmpty()) {
                LOGGER.warn("Cannot mute player {}: UUID not found", minecraftName);
                return false;
            }
            uuid = optUuid.get().getId();
        }

        long muteTime = System.currentTimeMillis();
        Long expiryTime = null;
        boolean permanent = true;

        if (durationMinutes != null && durationMinutes > 0) {
            expiryTime = muteTime + (durationMinutes * 60L * 1000L);
            permanent = false;
        }

        MuteData muteData = new MuteData(minecraftName, reason, muteTime, expiryTime, permanent);
        mutedPlayers.put(uuid, muteData);
        saveMutes();

        LOGGER.info("[MuteService] Muted player {} (UUID: {}) - Reason: {} - Duration: {}", 
                minecraftName, uuid, reason, permanent ? "Permanent" : durationMinutes + " minutes");
        return true;
    }

    /**
     * Unmutes a player.
     * 
     * @param minecraftName Player's username
     * @return true if player was muted and is now unmuted
     */
    public boolean unmutePlayer(String minecraftName) {
        if (minecraftName == null || minecraftName.isEmpty()) {
            LOGGER.warn("Cannot unmute player: minecraftName is null or empty");
            return false;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("Cannot unmute player: server not available");
            return false;
        }

        // Find UUID by name
        UUID targetUuid = null;
        for (Map.Entry<UUID, MuteData> entry : mutedPlayers.entrySet()) {
            if (entry.getValue().minecraftName.equalsIgnoreCase(minecraftName)) {
                targetUuid = entry.getKey();
                break;
            }
        }

        if (targetUuid == null) {
            LOGGER.info("[MuteService] Player {} is not muted", minecraftName);
            return false;
        }

        mutedPlayers.remove(targetUuid);
        saveMutes();
        LOGGER.info("[MuteService] Unmuted player {} (UUID: {})", minecraftName, targetUuid);
        return true;
    }

    /**
     * Checks if a player is currently muted.
     * 
     * @param uuid Player's UUID
     * @return MuteData if muted, null otherwise
     */
    public MuteData getMuteData(UUID uuid) {
        if (uuid == null) return null;
        return mutedPlayers.get(uuid);
    }

    /**
     * Checks if a player is currently muted.
     * 
     * @param uuid Player's UUID
     * @return true if muted
     */
    public boolean isMuted(UUID uuid) {
        return mutedPlayers.containsKey(uuid);
    }

    /**
     * Called every server tick to check for expired mutes.
     * Checks every 20 seconds (400 ticks).
     */
    public void onServerTick() {
        tickCounter++;
        if (tickCounter >= 400) { // Every 20 seconds
            tickCounter = 0;
            checkExpiredMutes();
        }
    }

    /**
     * Checks and removes expired temporary mutes.
     */
    private void checkExpiredMutes() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Map.Entry<UUID, MuteData> entry : mutedPlayers.entrySet()) {
            MuteData data = entry.getValue();
            if (!data.permanent && data.expiryTime != null && now >= data.expiryTime) {
                LOGGER.info("[MuteService] Mute expired for player {} (UUID: {})", data.minecraftName, entry.getKey());
                mutedPlayers.remove(entry.getKey());
                changed = true;
            }
        }

        if (changed) {
            saveMutes();
        }
    }

    /**
     * Loads mutes from JSON file.
     */
    private void loadMutes() {
        try {
            Files.createDirectories(configDir);
            if (Files.exists(muteFile)) {
                String json = Files.readString(muteFile);
                Map<String, MuteData> loaded = GSON.fromJson(json, new TypeToken<Map<String, MuteData>>(){}.getType());
                
                if (loaded != null) {
                    // Convert String UUIDs to UUID objects
                    for (Map.Entry<String, MuteData> entry : loaded.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            mutedPlayers.put(uuid, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("Invalid UUID in muted_players.json: {}", entry.getKey());
                        }
                    }
                    LOGGER.info("[MuteService] Loaded {} muted player(s)", mutedPlayers.size());
                }
            } else {
                LOGGER.info("[MuteService] No existing mutes file found, starting fresh");
            }
        } catch (Exception e) {
            LOGGER.error("[MuteService] Failed to load mutes", e);
        }
    }

    /**
     * Saves mutes to JSON file.
     */
    private void saveMutes() {
        try {
            Files.createDirectories(configDir);
            
            // Convert UUID keys to String for JSON
            Map<String, MuteData> toSave = new HashMap<>();
            for (Map.Entry<UUID, MuteData> entry : mutedPlayers.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            
            String json = GSON.toJson(toSave);
            Files.writeString(muteFile, json);
            LOGGER.debug("[MuteService] Saved {} muted player(s)", mutedPlayers.size());
        } catch (IOException e) {
            LOGGER.error("[MuteService] Failed to save mutes", e);
        }
    }

    /**
     * Data class for mute information.
     */
    public static class MuteData {
        public String minecraftName;
        public String reason;
        public long muteTime;
        public Long expiryTime; // null if permanent
        public boolean permanent;

        public MuteData(String minecraftName, String reason, long muteTime, Long expiryTime, boolean permanent) {
            this.minecraftName = minecraftName;
            this.reason = reason;
            this.muteTime = muteTime;
            this.expiryTime = expiryTime;
            this.permanent = permanent;
        }
    }
}
