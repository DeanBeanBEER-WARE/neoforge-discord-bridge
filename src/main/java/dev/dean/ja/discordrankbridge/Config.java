package dev.dean.ja.discordrankbridge;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;

public class Config {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.ConfigValue<String> bindAddress;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> ports;
        public final ModConfigSpec.ConfigValue<String> path;
        public final ModConfigSpec.ConfigValue<String> secret;
        public final ModConfigSpec.ConfigValue<List<? extends String>> rankMappings;
        public final ModConfigSpec.BooleanValue chatEnabled;
        public final ModConfigSpec.ConfigValue<String> chatWebhookUrl;
        public final ModConfigSpec.ConfigValue<String> statusWebhookUrl;
        public final ModConfigSpec.ConfigValue<List<? extends String>> rankAliases;
        public final ModConfigSpec.IntValue afkTimeout;
        public final ModConfigSpec.BooleanValue verificationEnabled;
        public final ModConfigSpec.ConfigValue<String> verifyTitle;
        public final ModConfigSpec.ConfigValue<String> verifySubtitle;
        public final ModConfigSpec.IntValue debuffReapplyInterval;
        public final ModConfigSpec.IntValue titleInterval;
        public final ModConfigSpec.BooleanValue jailEnabled;
        public final ModConfigSpec.ConfigValue<Double> jailX;
        public final ModConfigSpec.ConfigValue<Double> jailY;
        public final ModConfigSpec.ConfigValue<Double> jailZ;
        public final ModConfigSpec.ConfigValue<Double> spawnX;
        public final ModConfigSpec.ConfigValue<Double> spawnY;
        public final ModConfigSpec.ConfigValue<Double> spawnZ;
        public final ModConfigSpec.IntValue jailRadius;

        public Common(ModConfigSpec.Builder builder) {
            builder.push("http");
            enabled = builder
                    .comment("Whether the webhook server is enabled")
                    .define("enabled", true);
            bindAddress = builder
                    .comment("The address to bind the server to")
                    .define("bindAddress", "0.0.0.0");
            ports = builder
                    .comment("The ports to listen on")
                    .defineList("ports", Collections.singletonList(3000), o -> o instanceof Integer);
            path = builder
                    .comment("The path to listen on")
                    .define("path", "/webhook");
            secret = builder
                    .comment("The shared secret for authentication (X-Auth-Token header)")
                    .define("secret", "JLs%&#9§yv+WSW.zJNnt6fx5sU_X_WXw");
            builder.pop();

            builder.push("rankMapping");
            rankMappings = builder
                    .comment("Discord rank key to LuckPerms group name mappings.",
                            "The order of this list determines the priority! The first entry is the highest rank.",
                            "Format: discord_rank:luckperms_group",
                            "Example: admin:admin")
                    .defineList("mappings", Collections.singletonList("test:test"), o -> o instanceof String && ((String) o).contains(":"));
            builder.pop();

            builder.push("chat");
            chatEnabled = builder
                    .comment("Whether to synchronize chat between Minecraft and Discord")
                    .define("enabled", true);
            chatWebhookUrl = builder
                    .comment("The Discord Webhook URL to send Minecraft chat messages to")
                    .define("webhookUrl", "");
            statusWebhookUrl = builder
                    .comment("The Discord Webhook URL to send server online/offline status to")
                    .define("statusWebhookUrl", "https://discord.com/api/webhooks/1470748494917795925/Xhx2Aw9eM4y3Msz2SFDcgr7NXRfdLQ0kZOQNyRWf6vdKzGG6Clfwluxotkx4j2Jk-Xtn");
            rankAliases = builder
                    .comment("Display name aliases for ranks (including format codes).",
                            "Format: group_name:display_name",
                            "Example: admin:&1[&l&oᴅɪѕᴄᴏʀᴅ&r&1]&7")
                    .defineList("rankAliases", Collections.emptyList(), o -> o instanceof String && ((String) o).contains(":"));
            builder.pop();

            builder.push("afk");
            afkTimeout = builder
                    .comment("Seconds of inactivity before a player is marked as AFK. Set to 0 to disable.")
                    .defineInRange("timeout", 300, 0, 3600);
            builder.pop();

            builder.push("verification");
            verificationEnabled = builder
                    .comment("Whether to enforce verification requirements for players")
                    .define("enabled", true);
            verifyTitle = builder
                    .comment("Title shown to unverified players")
                    .define("title", "Verify yourself");
            verifySubtitle = builder
                    .comment("Subtitle shown to unverified players")
                    .define("subtitle", "go to #how-to-verify in discord (https://discord.gg/6uatzv8hWm) for more information");
            debuffReapplyInterval = builder
                    .comment("Seconds between debuff reapplication for unverified players")
                    .defineInRange("debuffReapplyInterval", 5, 1, 60);
            titleInterval = builder
                    .comment("Seconds between title redisplay for unverified players")
                    .defineInRange("titleInterval", 30, 5, 300);
            builder.pop();

            builder.push("jail");
            jailEnabled = builder
                    .comment("Whether to enforce jail for unverified players")
                    .define("enabled", true);
            jailX = builder
                    .comment("Jail X coordinate (for unverified players)")
                    .define("jailX", 4805.0);
            jailY = builder
                    .comment("Jail Y coordinate")
                    .define("jailY", 509.0);
            jailZ = builder
                    .comment("Jail Z coordinate")
                    .define("jailZ", 2349.0);
            spawnX = builder
                    .comment("Spawn X coordinate (for verified players)")
                    .define("spawnX", 4805.0);
            spawnY = builder
                    .comment("Spawn Y coordinate")
                    .define("spawnY", 180.0);
            spawnZ = builder
                    .comment("Spawn Z coordinate")
                    .define("spawnZ", 2349.0);
            jailRadius = builder
                    .comment("Jail radius in blocks - players will be teleported back if they go beyond this distance")
                    .defineInRange("jailRadius", 10, 5, 100);
            builder.pop();
        }

        public String getMappedGroup(String discordRank) {
            if (discordRank == null) return null;
            List<? extends String> mappings = rankMappings.get();
            for (String mapping : mappings) {
                String[] parts = mapping.split(":", 2);
                if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(discordRank.trim())) {
                    return parts[1].trim();
                }
            }
            return null;
        }

        /**
         * Returns the priority of a LuckPerms group.
         * Lower number means higher priority (0 is highest).
         * Returns Integer.MAX_VALUE if group is not in the mapping.
         */
        public int getGroupPriority(String groupName) {
            if (groupName == null) return Integer.MAX_VALUE;
            List<? extends String> mappings = rankMappings.get();
            for (int i = 0; i < mappings.size(); i++) {
                String[] parts = mappings.get(i).split(":", 2);
                if (parts.length == 2 && parts[1].trim().equalsIgnoreCase(groupName.trim())) {
                    return i;
                }
            }
            return Integer.MAX_VALUE;
        }

        /**
         * Checks if a group is managed by this mod.
         */
        public boolean isManagedGroup(String groupName) {
            if (groupName == null) return false;
            List<? extends String> mappings = rankMappings.get();
            for (String mapping : mappings) {
                String[] parts = mapping.split(":", 2);
                if (parts.length == 2 && parts[1].trim().equalsIgnoreCase(groupName.trim())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the alias for a group, or the group name itself if no alias is defined.
         */
        public String getRankAlias(String groupName) {
            if (groupName == null) return "Player";
            List<? extends String> aliases = rankAliases.get();
            for (String alias : aliases) {
                String[] parts = alias.split(":", 2);
                if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(groupName.trim())) {
                    return parts[1].trim();
                }
            }
            return groupName;
        }
    }
}
