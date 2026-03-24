package dev.dean.ja.discordrankbridge;

import com.google.gson.JsonObject;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatListener.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final MuteService muteService;
    
    // Pattern for BetterForgeChat logs: [CHAT] [13:03] | [Rank] Name : Message
    private static final Pattern BFC_PATTERN = Pattern.compile("\\[CHAT\\]\\s+\\[(\\d{2}:\\d{2})\\]\\s+\\|\\s+\\[(.*?)\\]\\s+(.*?)\\s*:\\s*(.*)");
    
    private static boolean bfcDetected = false;

    public ChatListener(MuteService muteService) {
        this.muteService = muteService;
        setupLogInterception();
    }

    private void setupLogInterception() {
        try {
            org.apache.logging.log4j.core.LoggerContext ctx = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
            
            Appender appender = new AbstractAppender("DiscordRankBridgeAppender", null, null, true, null) {
                @Override
                public void append(LogEvent event) {
                    // CRITICAL: Ignore messages coming from our own mod's package
                    if (event.getLoggerName().startsWith("dev.dean.ja.discordrankbridge")) {
                        return;
                    }

                    String formattedMessage = event.getMessage().getFormattedMessage();
                    
                    // CRITICAL: Ignore messages that contain the '§' section sign.
                    // This is only used by the mod's bridge for stylized Discord messages.
                    // Minecraft chat naturally uses Components, so the raw log string for a player
                    // shouldn't contain '§' unless it's a mod-generated message like ours.
                    if (formattedMessage.contains("§")) {
                        return;
                    }

                    if (formattedMessage.contains("[CHAT]")) {
                        handleLogMessage(formattedMessage);
                    }
                }
            };
            
            appender.start();
            config.addAppender(appender);
            
            config.getRootLogger().addAppender(appender, null, null);
            for (org.apache.logging.log4j.core.config.LoggerConfig loggerConfig : config.getLoggers().values()) {
                loggerConfig.addAppender(appender, null, null);
            }
            
            ctx.updateLoggers();
            LOGGER.info("Multi-logger interception for BetterForgeChat initialized.");
        } catch (Exception e) {
            LOGGER.error("Failed to setup log interception", e);
        }
    }

    private void handleLogMessage(String logLine) {
        if (!Config.COMMON.chatEnabled.get()) return;

        Matcher matcher = BFC_PATTERN.matcher(logLine);
        if (matcher.find()) {
            if (!bfcDetected) {
                bfcDetected = true;
                LOGGER.info("[DiscordBridge] BetterForgeChat format detected. Disabling standard event fallback.");
                return; // Suppress the first detected message to avoid duplication with the standard listener
            }

            String timestamp = matcher.group(1).trim();
            String rank = matcher.group(2).trim();
            String playerName = matcher.group(3).trim();
            String message = matcher.group(4).trim();

            // Check if player is muted (BFC messages bypass ServerChatEvent)
            if (muteService != null) {
                net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
                    if (player != null && muteService.isMuted(player.getUUID())) {
                        // Send mute message to player
                        server.execute(() -> {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are muted!"));
                        });
                        LOGGER.info("[MuteService] Blocked BFC message from muted player: {}", playerName);
                        return; // Block message from being sent to Discord
                    }
                }
            }

            String webhookUrl = Config.COMMON.chatWebhookUrl.get();
            if (webhookUrl == null || webhookUrl.isEmpty()) return;

            // Log our own interception (will be ignored by appender due to package check)
            LOGGER.info("[DiscordBridge] Intercepted BFC chat from {}: {}", playerName, message);

            JsonObject json = new JsonObject();
            json.addProperty("username", playerName);
            json.addProperty("content", MessageConfig.get().formatDiscordChatBFC(timestamp, rank, message));
            json.addProperty("avatar_url", MessageConfig.get().getAvatarUrl(playerName));

            sendToDiscord(webhookUrl, json.toString());
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onServerChat(ServerChatEvent event) {
        if (!Config.COMMON.chatEnabled.get()) return;

        // Check if player is muted
        if (muteService != null && muteService.isMuted(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are muted!"));
            return;
        }

        // If BFC log interception is active, we silence the fallback listener
        if (bfcDetected) return;

        String username = event.getPlayer().getName().getString();
        String message = event.getRawText();
        String timestamp = LocalTime.now().format(TIME_FORMATTER);

        String webhookUrl = Config.COMMON.chatWebhookUrl.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        LOGGER.info("[DiscordBridge] Processed standard chat event from {}: {}", username, message);

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("content", MessageConfig.get().formatDiscordChatStandard(timestamp, message));
        json.addProperty("avatar_url", MessageConfig.get().getAvatarUrl(username));

        sendToDiscord(webhookUrl, json.toString());
    }

    /**
     * Sends a player join notification to the Discord chat webhook.
     */
    public void sendJoinMessage(String playerName) {
        if (!Config.COMMON.chatEnabled.get()) return;
        String webhookUrl = Config.COMMON.chatWebhookUrl.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        JsonObject json = new JsonObject();
        json.addProperty("username", playerName);
        json.addProperty("content", MessageConfig.get().formatJoinMessage(timestamp, playerName));
        json.addProperty("avatar_url", MessageConfig.get().getAvatarUrl(playerName));
        sendToDiscord(webhookUrl, json.toString());
    }

    /**
     * Sends a player leave notification to the Discord chat webhook.
     */
    public void sendLeaveMessage(String playerName) {
        if (!Config.COMMON.chatEnabled.get()) return;
        String webhookUrl = Config.COMMON.chatWebhookUrl.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        JsonObject json = new JsonObject();
        json.addProperty("username", playerName);
        json.addProperty("content", MessageConfig.get().formatLeaveMessage(timestamp, playerName));
        json.addProperty("avatar_url", MessageConfig.get().getAvatarUrl(playerName));
        sendToDiscord(webhookUrl, json.toString());
    }

    private void sendToDiscord(String webhookUrl, String jsonPayload) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 300) {
                                LOGGER.error("Failed to send chat to Discord. Status: {}, Body: {}", response.statusCode(), response.body());
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Error sending chat message to Discord", e);
            }
        });
    }
}
