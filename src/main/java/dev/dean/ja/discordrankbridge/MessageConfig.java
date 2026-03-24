package dev.dean.ja.discordrankbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages a JSON-based message configuration file for all customizable
 * message formats and display strings used by the mod.
 * The file is created with defaults on first run and can be edited without rebuilding.
 */
public class MessageConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static MessageConfig INSTANCE;

    // --- Minecraft In-Game Formats ---
    public Minecraft minecraft = new Minecraft();
    // --- Discord Webhook Formats ---
    public Discord discord = new Discord();
    // --- AFK Settings ---
    public Afk afk = new Afk();

    public static class Minecraft {
        public String discordChatBadge = "§1[§l§oᴅɪѕᴄᴏʀᴅ§r§1]";
        public String discordChatFormat = "§f[{time}] | {badge}§7 {sender} : {message}";
        public String broadcastBadge = "§e[§l§oꜱᴇʀᴠᴇʀ§r§e]";
        public String broadcastFormat = "§f[{time}] | {badge} §l§c{message}";
        public String afkTag = "§7[§l§oᴀꜰᴋ§r§7]";
        public String maintenanceKickMessage = "§cServer is in maintenance mode.\n§7Only staff can join.";
        public String defaultKickReason = "Kicked by administrator via Discord";
    }

    public static class Discord {
        public String chatFormatBFC = "`[{timestamp}]` **[{rank}]** {message}";
        public String chatFormatStandard = "`[{timestamp}]` {message}";
        public String joinMessage = "`[{timestamp}]` ➡️ **{player}** joined the server";
        public String leaveMessage = "`[{timestamp}]` ⬅️ **{player}** left the server";
        public String serverOnline = "🟢 **Server is Online**";
        public String serverOffline = "🔴 **Server is Offline**";
        public String whitelistFormat = "**[Whitelist]** {message}";
        public String avatarUrl = "https://minotar.net/avatar/{player}";
    }

    public static class Afk {
        public int timeoutSeconds = 300;
        public double movementThreshold = 0.1;
        public double rotationThreshold = 5.0;
    }

    private MessageConfig() {}

    /**
     * Loads the config from disk, or creates it with defaults if missing.
     */
    public static MessageConfig load(Path configDir) {
        Path file = configDir.resolve("discordrankbridge-messages.json");
        try {
            Files.createDirectories(configDir);
            if (Files.exists(file)) {
                String json = Files.readString(file);
                INSTANCE = GSON.fromJson(json, MessageConfig.class);
                if (INSTANCE == null) INSTANCE = new MessageConfig();
                LOGGER.info("[DiscordRankBridge] Loaded message config from {}", file);
            } else {
                INSTANCE = new MessageConfig();
                save(configDir);
                LOGGER.info("[DiscordRankBridge] Created default message config at {}", file);
            }
        } catch (Exception e) {
            LOGGER.error("[DiscordRankBridge] Failed to load message config, using defaults", e);
            INSTANCE = new MessageConfig();
        }
        return INSTANCE;
    }

    /**
     * Saves the current config to disk.
     */
    public static void save(Path configDir) {
        Path file = configDir.resolve("discordrankbridge-messages.json");
        try {
            Files.createDirectories(configDir);
            Files.writeString(file, GSON.toJson(INSTANCE != null ? INSTANCE : new MessageConfig()));
        } catch (IOException e) {
            LOGGER.error("[DiscordRankBridge] Failed to save message config", e);
        }
    }

    /**
     * Returns the singleton instance. Must call load() first.
     */
    public static MessageConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new MessageConfig();
        }
        return INSTANCE;
    }

    // --- Helper methods for formatting ---

    public String formatDiscordChat(String time, String badge, String sender, String message) {
        return minecraft.discordChatFormat
                .replace("{time}", time)
                .replace("{badge}", badge != null ? badge : minecraft.discordChatBadge)
                .replace("{sender}", sender)
                .replace("{message}", message);
    }

    public String formatBroadcast(String time, String message) {
        return minecraft.broadcastFormat
                .replace("{time}", time)
                .replace("{badge}", minecraft.broadcastBadge)
                .replace("{message}", message);
    }

    public String formatDiscordChatBFC(String timestamp, String rank, String message) {
        return discord.chatFormatBFC
                .replace("{timestamp}", timestamp)
                .replace("{rank}", rank)
                .replace("{message}", message);
    }

    public String formatDiscordChatStandard(String timestamp, String message) {
        return discord.chatFormatStandard
                .replace("{timestamp}", timestamp)
                .replace("{message}", message);
    }

    public String formatJoinMessage(String timestamp, String player) {
        return discord.joinMessage
                .replace("{timestamp}", timestamp)
                .replace("{player}", player);
    }

    public String formatLeaveMessage(String timestamp, String player) {
        return discord.leaveMessage
                .replace("{timestamp}", timestamp)
                .replace("{player}", player);
    }

    public String formatWhitelist(String message) {
        return discord.whitelistFormat.replace("{message}", message);
    }

    public String getAvatarUrl(String player) {
        return discord.avatarUrl.replace("{player}", player);
    }
}
