package discordrankbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for persisting and managing player statistics.
 * Stores data in a JSON file to allow access to statistics of offline players.
 */
public class PlayerStatsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStatsService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STATS_FILE = Paths.get("world", "discordrankbridge", "player_stats.json");
    
    private final Map<String, PlayerData> playerStats = new ConcurrentHashMap<>();

    public PlayerStatsService() {
        loadStats();
    }

    /**
     * Updates the statistics for a specific player.
     *
     * @param player The ServerPlayer instance.
     */
    public void updatePlayerStats(ServerPlayer player) {
        String name = player.getName().getString();
        
        int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        int mobKills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        int playerKills = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        int playtimeTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));

        double kda = (double) (mobKills + playerKills) / Math.max(1, deaths);
        double playtimeHours = playtimeTicks / 72000.0;

        PlayerData data = new PlayerData(
                name,
                Math.round(kda * 100.0) / 100.0,
                Math.round(playtimeHours * 10.0) / 10.0
        );

        playerStats.put(name, data);
        saveStats();
    }

    /**
     * Returns a list of all stored player statistics.
     *
     * @return List of PlayerData.
     */
    public List<PlayerData> getAllPlayerStats() {
        return new ArrayList<>(playerStats.values());
    }

    /**
     * Returns the formatted playtime string for a given player.
     * Format: "Xd Yh Zm" (e.g., "1d 02h 05m")
     *
     * @param player The ServerPlayer instance.
     * @return Formatted playtime string.
     */
    public String getFormattedPlaytime(ServerPlayer player) {
        int playtimeTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        return formatPlaytimeFromTicks(playtimeTicks);
    }

    /**
     * Returns the formatted playtime string for a player identified by UUID.
     * Looks up the ServerPlayer from the current server instance.
     *
     * @param uuid The player's UUID.
     * @return Formatted playtime string, or "0m" if the player is not online.
     */
    public String getFormattedPlaytime(UUID uuid) {
        try {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return "0m";
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return "0m";
            return getFormattedPlaytime(player);
        } catch (Exception e) {
            LOGGER.error("Error getting playtime for UUID {}", uuid, e);
            return "0m";
        }
    }

    /**
     * Converts playtime in ticks to a formatted string.
     * Format: "Xd Yh Zm" (e.g., "1d 02h 05m")
     *
     * @param ticks The playtime in ticks.
     * @return Formatted playtime string.
     */
    private String formatPlaytimeFromTicks(int ticks) {
        // Convert ticks to seconds (20 ticks = 1 second)
        long totalSeconds = ticks / 20L;
        
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        
        if (days > 0) {
            return String.format("%dd %02dh %02dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private void loadStats() {
        try {
            if (Files.exists(STATS_FILE)) {
                try (Reader reader = Files.newBufferedReader(STATS_FILE, StandardCharsets.UTF_8)) {
                    Map<String, PlayerData> loaded = GSON.fromJson(reader, new TypeToken<Map<String, PlayerData>>() {}.getType());
                    if (loaded != null) {
                        playerStats.putAll(loaded);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load player stats", e);
        }
    }

    private void saveStats() {
        try {
            Files.createDirectories(STATS_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(STATS_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(playerStats, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player stats", e);
        }
    }

    /**
     * Data class representing a player's statistics.
     */
    public static class PlayerData {
        public String minecraftName;
        public double kda;
        public double playtime;

        public PlayerData(String minecraftName, double kda, double playtime) {
            this.minecraftName = minecraftName;
            this.kda = kda;
            this.playtime = playtime;
        }
    }
}
