package discordrankbridge;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Integrates with the TAB plugin to provide a per-player playtime placeholder.
 * Registers %drb_playtime% which returns the formatted playtime string for each player.
 */
public final class TabPlaytimeIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TabPlaytimeIntegration.class);

    private static PlayerStatsService playerStatsService;
    private static AfkTracker afkTracker;

    private TabPlaytimeIntegration() {}

    /**
     * Initializes the TAB integration by registering placeholders.
     * Should be called after services are initialized AND after all mods are loaded (ServerStartedEvent).
     *
     * @param statsService The PlayerStatsService instance for playtime lookups.
     * @param tracker The AfkTracker instance for AFK status lookups.
     */
    public static void init(PlayerStatsService statsService, AfkTracker tracker) {
        playerStatsService = statsService;
        afkTracker = tracker;
        registerPlaceholders();
    }

    private static void registerPlaceholders() {
        TabAPI api;
        try {
            api = TabAPI.getInstance();
        } catch (Throwable t) {
            LOGGER.error("[TAB_DEBUG] TAB API failed: {} - {}", t.getClass().getName(), t.getMessage());
            LOGGER.error("[TAB_DEBUG] Full stacktrace:", t);
            return;
        }

        if (api == null || api.getPlaceholderManager() == null) {
            LOGGER.info("[DiscordRankBridge] TAB PlaceholderManager not available, skipping placeholders.");
            return;
        }

        // Register %drb_playtime% - formatted playtime per player
        api.getPlaceholderManager().registerPlayerPlaceholder(
            "%drb_playtime%",
            1000,
            (TabPlayer tabPlayer) -> {
                if (playerStatsService == null) return "0m";
                return playerStatsService.getFormattedPlaytime(tabPlayer.getUniqueId());
            }
        );

        // Register %drb_afk% - AFK tag per player (empty if not AFK)
        api.getPlaceholderManager().registerPlayerPlaceholder(
            "%drb_afk%",
            500,
            (TabPlayer tabPlayer) -> {
                if (afkTracker == null) return "";
                return afkTracker.getAfkTag(tabPlayer.getUniqueId());
            }
        );

        LOGGER.info("[DiscordRankBridge] Registered TAB placeholders: %drb_playtime%, %drb_afk%");
    }
}
