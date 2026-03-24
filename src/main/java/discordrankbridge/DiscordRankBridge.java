package discordrankbridge;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod(DiscordRankBridge.MODID)
public class DiscordRankBridge {
    public static final String MODID = "discordrankbridge";
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordRankBridge.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static long startTime;

    private WebhookServer webhookServer;
    private LuckPermsService luckPermsService;
    private VerificationService verificationService;
    private PlayerStatsService playerStatsService;
    private MaintenanceService maintenanceService;
    private MuteService muteService;
    private ChatListener chatListener;
    private AfkTracker afkTracker;
    private VerificationEnforcer verificationEnforcer;
    
    private static VerificationEnforcer staticVerificationEnforcer;

    public VerificationService getVerificationService() {
        return verificationService;
    }

    public VerificationEnforcer getVerificationEnforcer() {
        return verificationEnforcer;
    }
    
    public static VerificationEnforcer getStaticVerificationEnforcer() {
        return staticVerificationEnforcer;
    }

    public PlayerStatsService getPlayerStatsService() {
        return playerStatsService;
    }

    public DiscordRankBridge(IEventBus modEventBus, ModContainer modContainer) {
        // Register the setup method for modloading
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        
        // Register command handler EARLY - RegisterCommandsEvent fires BEFORE ServerStartingEvent
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("[DiscordRankBridge] RegisterCommandsEvent received, registering commands");
        CommandHandler.registerCommands(event.getDispatcher(), this);
        LOGGER.info("[DiscordRankBridge] Commands registered successfully");
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Discord Rank Bridge v1.0.2 starting...");
        startTime = System.currentTimeMillis();
        try {
            Path configDirPath = FMLPaths.CONFIGDIR.get().resolve(MODID);
            
            // Load JSON message config
            MessageConfig.load(configDirPath);
            
            luckPermsService = new LuckPermsService();
            verificationService = new VerificationService(configDirPath);
            playerStatsService = new PlayerStatsService();
            maintenanceService = new MaintenanceService();
            muteService = new MuteService(configDirPath);
            chatListener = new ChatListener(muteService);
            afkTracker = new AfkTracker();
            verificationEnforcer = new VerificationEnforcer(verificationService);
            staticVerificationEnforcer = verificationEnforcer;
            
            // Link services
            verificationService.setVerificationEnforcer(verificationEnforcer);
            
            // Register listeners
            NeoForge.EVENT_BUS.register(chatListener);
            NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
            NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
            NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
            
            // AFK tracking events
            NeoForge.EVENT_BUS.addListener(this::onServerTick);
            NeoForge.EVENT_BUS.addListener(this::onAfkChat);
            NeoForge.EVENT_BUS.addListener(this::onAfkRightClickBlock);
            NeoForge.EVENT_BUS.addListener(this::onAfkLeftClickBlock);
            
            webhookServer = new WebhookServer(luckPermsService, verificationService, playerStatsService, maintenanceService, muteService);
            webhookServer.start();
            
            LOGGER.info("Discord Rank Bridge services initialized.");
            
            sendStatusNotification(MessageConfig.get().discord.serverOnline);
        } catch (NoClassDefFoundError e) {
            LOGGER.error("LuckPerms API not found! Discord Rank Bridge will be disabled.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Discord Rank Bridge", e);
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        // Initialize TAB integration AFTER all mods/plugins are loaded
        // TAB API is not available during ServerStartingEvent
        if (playerStatsService != null) {
            TabPlaytimeIntegration.init(playerStatsService, afkTracker);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Discord Rank Bridge stopping...");
        
        // Send notification synchronously to ensure it goes out before JVM exit
        sendStatusNotificationSync(MessageConfig.get().discord.serverOffline);

        if (webhookServer != null) {
            webhookServer.stop();
        }
    }

    private void sendStatusNotification(String message) {
        String url = Config.COMMON.statusWebhookUrl.get();
        if (url == null || url.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("content", message);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 300) {
                                LOGGER.error("Failed to send status notification. Status: {}", response.statusCode());
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Error sending status notification", e);
            }
        });
    }

    private void sendStatusNotificationSync(String message) {
        String url = Config.COMMON.statusWebhookUrl.get();
        if (url == null || url.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("content", message);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                    .build();

            // Use join() to make it synchronous during shutdown
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(5, TimeUnit.SECONDS);
            LOGGER.info("Offline notification sent.");
        } catch (Exception e) {
            LOGGER.error("Error sending offline notification", e);
        }
    }

    public static long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    // --- AFK Tracking Event Handlers ---

    private void onServerTick(ServerTickEvent.Post event) {
        if (afkTracker != null) {
            afkTracker.onServerTick();
        }
        if (verificationEnforcer != null) {
            verificationEnforcer.onServerTick();
        }
        if (muteService != null) {
            muteService.onServerTick();
        }
    }

    private void onAfkChat(ServerChatEvent event) {
        if (afkTracker != null && event.getPlayer() != null) {
            afkTracker.markActive(event.getPlayer().getUUID());
        }
    }

    private void onAfkRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (afkTracker != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            afkTracker.markActive(player.getUUID());
        }
    }

    private void onAfkLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (afkTracker != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            afkTracker.markActive(player.getUUID());
        }
    }

    // --- Player Event Handlers ---

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            playerStatsService.updatePlayerStats(player);
            if (afkTracker != null) {
                afkTracker.onPlayerJoin(player.getUUID());
            }
            if (chatListener != null) {
                chatListener.sendJoinMessage(player.getName().getString());
            }
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            playerStatsService.updatePlayerStats(player);
            if (afkTracker != null) {
                afkTracker.onPlayerLeave(player.getUUID());
            }
            if (chatListener != null) {
                chatListener.sendLeaveMessage(player.getName().getString());
            }
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            if (maintenanceService.isMaintenanceMode()) {
                String playerName = player.getName().getString();
                
                // Check if player has staff rank (trialmod or higher)
                boolean hasStaffRank = luckPermsService.hasMinimumRank(playerName, "trialmod").join();
                
                if (!hasStaffRank && !maintenanceService.isPlayerAllowed(playerName)) {
                    player.connection.disconnect(Component.literal(MessageConfig.get().minecraft.maintenanceKickMessage));
                    LOGGER.info("[MaintenanceService] Player {} denied login during maintenance (rank insufficient).", playerName);
                } else if (hasStaffRank) {
                    LOGGER.info("[MaintenanceService] Player {} allowed during maintenance (staff rank).", playerName);
                }
            }
            
            // Verification enforcement
            if (verificationEnforcer != null) {
                verificationEnforcer.onPlayerJoin(player);
            }
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            if (verificationEnforcer != null) {
                verificationEnforcer.onPlayerRespawn(player);
            }
        }
    }
}
