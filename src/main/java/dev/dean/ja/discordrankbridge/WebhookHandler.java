package dev.dean.ja.discordrankbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.stats.Stats;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WebhookHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookHandler.class);
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LuckPermsService luckPermsService;
    private final VerificationService verificationService;
    private final PlayerStatsService playerStatsService;
    private final MaintenanceService maintenanceService;
    private final MuteService muteService;
    
    // Simple deduplication cache: Message Content -> Timestamp
    private static final Map<String, Long> lastMessages = new ConcurrentHashMap<>();

    public WebhookHandler(LuckPermsService luckPermsService, VerificationService verificationService, PlayerStatsService playerStatsService, MaintenanceService maintenanceService, MuteService muteService) {
        this.luckPermsService = luckPermsService;
        this.verificationService = verificationService;
        this.playerStatsService = playerStatsService;
        this.maintenanceService = maintenanceService;
        this.muteService = muteService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String authToken = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (authToken == null || !authToken.equals(Config.COMMON.secret.get())) {
            sendResponse(exchange, 401, "Unauthorized");
            return;
        }

        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        try {
            WebhookPayload payload = GSON.fromJson(body, WebhookPayload.class);
            if (payload == null || payload.action == null) {
                LOGGER.warn("[DiscordBridge] Bad Request: Missing action in payload");
                sendResponse(exchange, 400, "Bad Request: Missing required action");
                return;
            }

            String action = payload.action.trim().toLowerCase();
            
            // Only log actions that are NOT "get_online_players" and NOT "get_tps" and NOT "get_uptime" to reduce spam
            if (!action.equals("get_online_players") && !action.equals("get_tps") && !action.equals("get_uptime")) {
                LOGGER.info("[DiscordBridge] Incoming request from: {}", exchange.getRemoteAddress());
                LOGGER.info("[DiscordBridge] Raw Request Body: [{}]", body);
                LOGGER.info("[DiscordBridge] Processing action: [{}]", action);
            }

            switch (action) {
                case "chat":
                    if (isDuplicate(payload)) {
                        LOGGER.info("[DiscordBridge] Ignored duplicate chat message");
                    } else {
                        handleChat(payload);
                    }
                    sendResponse(exchange, 200, "OK");
                    break;
                case "verify":
                    handleVerify(payload);
                    sendResponse(exchange, 200, "OK");
                    break;
                case "unlink":
                    handleUnlink(exchange, payload);
                    break;
                case "get_verified_user":
                    handleGetVerifiedUser(exchange, payload);
                    break;
                case "get_all_verified_users":
                    handleGetAllVerifiedUsers(exchange);
                    break;
                case "add":
                case "remove":
                    handleRankUpdate(exchange, payload);
                    break;
                case "whitelist_add":
                    handleWhitelistAdd(payload);
                    sendResponse(exchange, 200, "OK");
                    break;
                case "whitelist_remove":
                    handleWhitelistRemove(payload);
                    sendResponse(exchange, 200, "OK");
                    break;
                case "get_online_players":
                    handleGetOnlinePlayers(exchange);
                    break;
                case "get_tps":
                    handleGetTps(exchange);
                    break;
                case "get_player_stats":
                    handleGetPlayerStats(exchange, payload);
                    break;
                case "kick_player":
                    handleKickPlayer(exchange, payload);
                    break;
                case "broadcast_message":
                    handleBroadcastMessage(exchange, payload);
                    break;
                case "get_uptime":
                    handleGetUptime(exchange);
                    break;
                case "get_top_players":
                    handleGetTopPlayers(exchange, payload);
                    break;
                case "stop_server":
                    handleStopServer(exchange);
                    break;
                case "restart_server":
                    handleRestartServer(exchange);
                    break;
                case "execute_command":
                    handleExecuteCommand(exchange, payload);
                    break;
                case "set_maintenance_mode":
                    handleSetMaintenanceMode(exchange, payload);
                    break;
                case "update_maintenance_whitelist":
                    handleUpdateMaintenanceWhitelist(exchange, payload);
                    break;
                case "mute_player":
                    handleMutePlayer(exchange, payload);
                    break;
                case "unmute_player":
                    handleUnmutePlayer(exchange, payload);
                    break;
                default:
                    LOGGER.warn("[DiscordBridge] Unknown action: [{}]", action);
                    sendResponse(exchange, 400, "Bad Request: Unknown action [" + action + "]");
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("[DiscordBridge] Error processing payload", e);
            sendResponse(exchange, 400, "Bad Request: Error processing payload");
        }
    }

    private boolean isDuplicate(WebhookPayload payload) {
        if (payload.message == null) return false;
        
        String key = payload.discordUsername + ":" + payload.message;
        long now = System.currentTimeMillis();
        Long last = lastMessages.put(key, now);
        
        // Clean up old entries occasionally
        if (lastMessages.size() > 100) {
            lastMessages.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
        }

        // If message was seen in last 2 seconds, it's a duplicate
        return last != null && (now - last < 2000);
    }

    private void handleChat(WebhookPayload payload) {
        if (!Config.COMMON.chatEnabled.get()) return;

        String time = payload.timestamp != null ? payload.timestamp : LocalTime.now().format(TIME_FORMATTER);
        String sender = payload.minecraftName != null ? payload.minecraftName : payload.discordUsername;

        // If rank is missing from payload, look it up in LuckPerms
        if (payload.rank == null && payload.minecraftName != null) {
            luckPermsService.getHighestPriorityGroup(payload.minecraftName).thenAccept(group -> {
                broadcastChatMessage(time, group, sender, payload.message);
            });
        } else {
            broadcastChatMessage(time, payload.rank, sender, payload.message);
        }
    }

    private void broadcastChatMessage(String time, String rank, String sender, String message) {
        // We add §f[HH:mm] to explicitly identify this as a mod-generated system message
        // Our ChatListener Log4j appender will ignore any message containing '§'
        // Format: &f[hh:mm] | &1[&l&oᴅɪѕᴄᴏʀᴅ&r&1]&7 <sender> : <message>
        String formattedMessage = MessageConfig.get().formatDiscordChat(time, null, sender, message);
        
        ServerLifecycleHooks.getCurrentServer().execute(() -> {
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(formattedMessage),
                    false
            );
        });
    }

    private void handleVerify(WebhookPayload payload) {
        String discId = payload.discordId != null ? payload.discordId : payload.discordUserId;
        if (payload.minecraftName == null || payload.code == null || discId == null) {
            LOGGER.error("Missing fields for verify action: {}", payload);
            return;
        }
        verificationService.addPendingVerification(payload.minecraftName, payload.code, discId);
    }

    private void handleUnlink(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        boolean success = verificationService.unlink(payload.minecraftName);

        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        response.addProperty("message", success ? "Player unlinked successfully." : "Player was not linked.");
        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleGetVerifiedUser(HttpExchange exchange, WebhookPayload payload) throws IOException {
        LOGGER.info("[DiscordBridge-DEBUG] === handleGetVerifiedUser START ===");
        LOGGER.info("[DiscordBridge-DEBUG] Payload minecraftName: [{}]", payload.minecraftName);
        
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            LOGGER.warn("[DiscordBridge-DEBUG] minecraftName is null or empty!");
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        LOGGER.info("[DiscordBridge-DEBUG] Querying VerificationService for: {}", payload.minecraftName);
        String discordId = verificationService.getVerifiedDiscordId(payload.minecraftName);
        LOGGER.info("[DiscordBridge-DEBUG] Retrieved Discord ID: [{}]", discordId);
        
        boolean isVerified = (discordId != null);
        LOGGER.info("[DiscordBridge-DEBUG] Is verified: {}", isVerified);

        JsonObject response = new JsonObject();
        response.addProperty("minecraftName", payload.minecraftName);
        response.addProperty("discordId", discordId);
        response.addProperty("isVerified", isVerified);

        LOGGER.info("[DiscordBridge-DEBUG] Response JSON: {}", response.toString());
        LOGGER.info("[DiscordBridge-DEBUG] Sending 200 OK response");
        sendJsonResponse(exchange, 200, response.toString());
        LOGGER.info("[DiscordBridge-DEBUG] === handleGetVerifiedUser END ===");
    }

    private void handleGetAllVerifiedUsers(HttpExchange exchange) throws IOException {
        LOGGER.info("[DiscordBridge-DEBUG] === handleGetAllVerifiedUsers START ===");
        
        Map<String, String> verifiedUsers = verificationService.getAllVerifiedUsers();
        
        JsonObject response = new JsonObject();
        com.google.gson.JsonArray usersArray = new com.google.gson.JsonArray();
        
        for (Map.Entry<String, String> entry : verifiedUsers.entrySet()) {
            JsonObject userObj = new JsonObject();
            userObj.addProperty("minecraftName", entry.getKey());
            userObj.addProperty("discordId", entry.getValue());
            usersArray.add(userObj);
        }
        
        response.add("users", usersArray);
        response.addProperty("count", verifiedUsers.size());
        
        LOGGER.info("[DiscordBridge-DEBUG] Sending {} verified users to Discord Bot", verifiedUsers.size());
        sendJsonResponse(exchange, 200, response.toString());
        LOGGER.info("[DiscordBridge-DEBUG] === handleGetAllVerifiedUsers END ===");
    }

    private void handleRankUpdate(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty() || payload.rank == null) {
            sendResponse(exchange, 400, "Bad Request: minecraftName and rank are required");
            return;
        }

        String mappedGroup = Config.COMMON.getMappedGroup(payload.rank);
        if (mappedGroup == null) {
            LOGGER.error("No mapping found for rank: {}", payload.rank);
            sendResponse(exchange, 400, "Bad Request: Unsupported rank");
            return;
        }

        luckPermsService.updatePlayerGroup(payload.minecraftName, mappedGroup, payload.action)
                .thenAccept(success -> {
                    try {
                        if (success) {
                            sendResponse(exchange, 200, "OK");
                        } else {
                            sendResponse(exchange, 500, "LuckPerms update failed");
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed to send response", e);
                    }
                });
    }

    private void handleWhitelistAdd(WebhookPayload payload) {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        server.execute(() -> {
            UserWhiteList whitelist = server.getPlayerList().getWhiteList();
            boolean isWhitelisted = false;
            
            for (String name : whitelist.getUserList()) {
                if (name.equalsIgnoreCase(payload.minecraftName)) {
                    isWhitelisted = true;
                    break;
                }
            }

            String message;
            if (isWhitelisted) {
                message = "Player " + payload.minecraftName + " is already whitelisted.";
                LOGGER.info("[DiscordBridge] Whitelist check: {}", message);
            } else {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "whitelist add " + payload.minecraftName);
                message = "Player " + payload.minecraftName + " has been added to the whitelist.";
                LOGGER.info("[DiscordBridge] Whitelist action: {}", message);
            }
            sendFeedbackToDiscord(message);
        });
    }

    private void handleWhitelistRemove(WebhookPayload payload) {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        server.execute(() -> {
            UserWhiteList whitelist = server.getPlayerList().getWhiteList();
            boolean isWhitelisted = false;

            for (String name : whitelist.getUserList()) {
                if (name.equalsIgnoreCase(payload.minecraftName)) {
                    isWhitelisted = true;
                    break;
                }
            }

            String message;
            if (isWhitelisted) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "whitelist remove " + payload.minecraftName);
                message = "Player " + payload.minecraftName + " has been removed from the whitelist.";
                LOGGER.info("[DiscordBridge] Whitelist action: {}", message);
            } else {
                message = "Player " + payload.minecraftName + " was not on the whitelist.";
                LOGGER.info("[DiscordBridge] Whitelist check: {}", message);
            }
            sendFeedbackToDiscord(message);
        });
    }

    private void sendFeedbackToDiscord(String message) {
        String webhookUrl = Config.COMMON.chatWebhookUrl.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("username", "Server");
        json.addProperty("content", MessageConfig.get().formatWhitelist(message));

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 300) {
                                LOGGER.error("Failed to send whitelist feedback to Discord. Status: {}", response.statusCode());
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Error sending whitelist feedback to Discord", e);
            }
        });
    }

    private void handleGetOnlinePlayers(HttpExchange exchange) throws IOException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("onlineCount", server.getPlayerList().getPlayerCount());
        response.addProperty("maxCount", server.getPlayerList().getMaxPlayers());
        
        com.google.gson.JsonArray players = new com.google.gson.JsonArray();
        server.getPlayerList().getPlayers().forEach(p -> players.add(p.getName().getString()));
        response.add("players", players);

        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleGetTps(HttpExchange exchange) throws IOException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        // Calculate average MSPT (Milliseconds Per Tick)
        // In 1.21.1, getAverageTickTimeNanos() returns the average in nanoseconds
        double averageMspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        
        // TPS = 1000 / MSPT (capped at 20.0)
        double tps = Math.min(20.0, 1000.0 / Math.max(1.0, averageMspt));

        JsonObject response = new JsonObject();
        response.addProperty("tps", Math.round(tps * 10.0) / 10.0);
        response.addProperty("mspt", Math.round(averageMspt * 10.0) / 10.0);

        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleGetPlayerStats(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(payload.minecraftName);
        if (player == null) {
            sendResponse(exchange, 404, "Player not found or offline");
            return;
        }

        int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        int kills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)) + 
                    player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        int playtimeTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        
        // Convert ticks to hours (20 ticks/sec, 3600 sec/hour)
        double playtimeHours = playtimeTicks / 72000.0;

        JsonObject response = new JsonObject();
        response.addProperty("minecraftName", payload.minecraftName);
        response.addProperty("health", Math.round(player.getHealth() * 10.0) / 10.0);
        response.addProperty("deaths", deaths);
        response.addProperty("kills", kills);
        response.addProperty("playtime", Math.round(playtimeHours * 10.0) / 10.0);

        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleKickPlayer(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        String kickReason = (payload.reason != null && !payload.reason.isEmpty()) 
                ? payload.reason 
                : MessageConfig.get().minecraft.defaultKickReason;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(payload.minecraftName);
            JsonObject response = new JsonObject();

            try {
                if (player != null) {
                    player.connection.disconnect(Component.literal(kickReason));
                    response.addProperty("success", true);
                    response.addProperty("message", "Player [" + payload.minecraftName + "] has been kicked.");
                    sendJsonResponse(exchange, 200, response.toString());
                } else {
                    response.addProperty("success", false);
                    response.addProperty("message", "Player [" + payload.minecraftName + "] not found or offline.");
                    sendJsonResponse(exchange, 200, response.toString());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to send kick response", e);
            }
        });
    }

    private void handleBroadcastMessage(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.message == null || payload.message.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: message is required");
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        String time = LocalTime.now().format(TIME_FORMATTER);
        // Format: §f[hh:mm] | §e[§l§oꜱᴇʀᴠᴇʀ§r§e] §l§c<message>
        String broadcastText = MessageConfig.get().formatBroadcast(time, payload.message);
        
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(broadcastText),
                    false
            );
        });

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleGetUptime(HttpExchange exchange) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("uptimeSeconds", DiscordRankBridge.getUptimeSeconds());
        sendJsonResponse(exchange, 200, response.toString());
    }

    /**
     * Handles the get_top_players action.
     * Fetches and sorts players based on the requested metric (kda or playtime).
     *
     * @param exchange The HttpExchange object.
     * @param payload  The WebhookPayload containing sorting criteria.
     * @throws IOException If an I/O error occurs.
     */
    private void handleGetTopPlayers(HttpExchange exchange, WebhookPayload payload) throws IOException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        String sortBy = (payload.sortBy != null) ? payload.sortBy.toLowerCase() : "playtime";

        server.execute(() -> {
            try {
                // Update stats for all currently online players before generating the top list
                server.getPlayerList().getPlayers().forEach(playerStatsService::updatePlayerStats);

                // Get all persisted player statistics
                java.util.List<JsonObject> playerList = playerStatsService.getAllPlayerStats().stream().map(data -> {
                    JsonObject playerJson = new JsonObject();
                    playerJson.addProperty("minecraftName", data.minecraftName);
                    playerJson.addProperty("kda", data.kda);
                    playerJson.addProperty("playtime", data.playtime);
                    return playerJson;
                }).sorted((a, b) -> {
                    if ("kda".equals(sortBy)) {
                        return Double.compare(b.get("kda").getAsDouble(), a.get("kda").getAsDouble());
                    } else {
                        return Double.compare(b.get("playtime").getAsDouble(), a.get("playtime").getAsDouble());
                    }
                }).limit(10).collect(Collectors.toList());

                JsonObject response = new JsonObject();
                com.google.gson.JsonArray playersArray = new com.google.gson.JsonArray();
                playerList.forEach(playersArray::add);
                response.add("players", playersArray);

                sendJsonResponse(exchange, 200, response.toString());
            } catch (IOException e) {
                LOGGER.error("Failed to send top players response", e);
            }
        });
    }

    /**
     * Handles the stop_server action.
     * Initiates a graceful shutdown of the Minecraft server.
     *
     * @param exchange The HttpExchange object.
     * @throws IOException If an I/O error occurs.
     */
    private void handleStopServer(HttpExchange exchange) throws IOException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Server shutdown initiated.");
        
        // Send response first before halting
        sendJsonResponse(exchange, 200, response.toString());

        LOGGER.info("[DiscordBridge] Shutdown initiated via Webhook.");
        server.execute(() -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop");
        });
    }

    /**
     * Handles the restart_server action.
     * Initiates a graceful restart of the Minecraft server.
     *
     * @param exchange The HttpExchange object.
     * @throws IOException If an I/O error occurs.
     */
    private void handleRestartServer(HttpExchange exchange) throws IOException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Server restart initiated.");

        // Send response first before halting
        sendJsonResponse(exchange, 200, response.toString());

        LOGGER.info("[DiscordBridge] Restart initiated via Webhook.");
        server.execute(() -> {
            // In vanilla/modded without a dedicated restart command, stop is the safest way to shut down.
            // Actual restart depends on the server wrapper script detecting the stop.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop");
        });
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleExecuteCommand(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.command == null || payload.command.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: command is required");
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendResponse(exchange, 503, "Server not available");
            return;
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        server.execute(() -> {
            try {
                StringBuilder output = new StringBuilder();
                CommandSourceStack sourceStack = server.createCommandSourceStack()
                    .withSource(new CommandSource() {
                        @Override
                        public void sendSystemMessage(Component component) {
                            output.append(component.getString()).append("\n");
                        }

                        @Override
                        public boolean acceptsSuccess() {
                            return true;
                        }

                        @Override
                        public boolean acceptsFailure() {
                            return true;
                        }

                        @Override
                        public boolean shouldInformAdmins() {
                            return false;
                        }
                    })
                    .withPermission(4); // Operator level 4

                server.getCommands().performPrefixedCommand(sourceStack, payload.command);
                future.complete(output.toString().trim());
            } catch (Exception e) {
                LOGGER.error("Error executing command via webhook", e);
                future.completeExceptionally(e);
            }
        });

        try {
            String commandOutput = future.join();
            JsonObject response = new JsonObject();
            response.addProperty("output", commandOutput.isEmpty() ? "Command executed (no output)" : commandOutput);
            sendJsonResponse(exchange, 200, response.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to execute command", e);
            sendResponse(exchange, 500, "Internal Server Error executing command");
        }
    }

    private void handleSetMaintenanceMode(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.mode == null) {
            sendResponse(exchange, 400, "Bad Request: mode is required");
            return;
        }

        LOGGER.info("[MaintenanceService] Received set_maintenance_mode request. New state: {}", payload.mode);
        maintenanceService.setMaintenanceMode(payload.mode);

        // If enabling maintenance mode, kick unauthorized players
        if (payload.mode) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> {
                    int kickedCount = 0;
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        String playerName = player.getName().getString();
                        
                        // Check if player has staff rank (trialmod or higher)
                        boolean hasStaffRank = luckPermsService.hasMinimumRank(playerName, "trialmod").join();
                        
                        // Kick if player has no staff rank AND is not on manual whitelist
                        if (!hasStaffRank && !maintenanceService.isPlayerAllowed(playerName)) {
                            player.connection.disconnect(Component.literal(
                                MessageConfig.get().minecraft.maintenanceKickMessage
                            ));
                            kickedCount++;
                            LOGGER.info("[MaintenanceService] Kicked {} (maintenance mode activated)", playerName);
                        } else {
                            LOGGER.info("[MaintenanceService] Player {} allowed to stay (staff rank or whitelisted)", playerName);
                        }
                    }
                    LOGGER.info("[MaintenanceService] Maintenance mode activated. {} player(s) kicked.", kickedCount);
                });
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Maintenance mode set to " + (payload.mode ? "on" : "off") + ".");
        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleUpdateMaintenanceWhitelist(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.allowedPlayers == null) {
            sendResponse(exchange, 400, "Bad Request: allowedPlayers is required");
            return;
        }

        maintenanceService.updateWhitelist(payload.allowedPlayers);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Maintenance whitelist updated.");
        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleMutePlayer(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        if (payload.reason == null || payload.reason.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: reason is required");
            return;
        }

        boolean success = muteService.mutePlayer(payload.minecraftName, payload.duration, payload.reason);

        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        if (success) {
            String durationText = (payload.duration == null) ? "permanently" : "for " + payload.duration + " minutes";
            response.addProperty("message", "Player " + payload.minecraftName + " muted " + durationText + ".");
        } else {
            response.addProperty("message", "Failed to mute player " + payload.minecraftName + ".");
        }
        sendJsonResponse(exchange, 200, response.toString());
    }

    private void handleUnmutePlayer(HttpExchange exchange, WebhookPayload payload) throws IOException {
        if (payload.minecraftName == null || payload.minecraftName.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: minecraftName is required");
            return;
        }

        boolean success = muteService.unmutePlayer(payload.minecraftName);

        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        if (success) {
            response.addProperty("message", "Player " + payload.minecraftName + " unmuted successfully.");
        } else {
            response.addProperty("message", "Player " + payload.minecraftName + " was not muted.");
        }
        sendJsonResponse(exchange, 200, response.toString());
    }
}
