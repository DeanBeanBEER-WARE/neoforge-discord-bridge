package dev.dean.ja.discordrankbridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaintenanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceService.class);
    
    private boolean maintenanceMode = false;
    private Set<String> allowedPlayers = new HashSet<>();

    /**
     * Sets the maintenance mode state.
     * 
     * @param enabled true to enable maintenance mode, false to disable
     */
    public void setMaintenanceMode(boolean enabled) {
        this.maintenanceMode = enabled;
        LOGGER.info("[MaintenanceService] Maintenance mode set to: {}", enabled ? "ON" : "OFF");
    }

    /**
     * Gets the current maintenance mode state.
     * 
     * @return true if maintenance mode is active, false otherwise
     */
    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    /**
     * Updates the maintenance whitelist with a new list of allowed players.
     * 
     * @param playerNames List of Minecraft player names allowed during maintenance
     */
    public void updateWhitelist(List<String> playerNames) {
        allowedPlayers.clear();
        if (playerNames != null) {
            allowedPlayers.addAll(playerNames);
        }
        LOGGER.info("[MaintenanceService] Maintenance whitelist updated. {} player(s) allowed.", allowedPlayers.size());
    }

    /**
     * Checks if a player is allowed to join during maintenance mode.
     * 
     * @param playerName The Minecraft player name to check
     * @return true if the player is on the whitelist, false otherwise
     */
    public boolean isPlayerAllowed(String playerName) {
        return allowedPlayers.contains(playerName);
    }

    /**
     * Gets the count of players on the maintenance whitelist.
     * 
     * @return the number of allowed players
     */
    public int getWhitelistSize() {
        return allowedPlayers.size();
    }
}
