package discordrankbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataDirectory;
    private final File verifiedFile;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<String, VerificationRequest> pendingVerifications = new ConcurrentHashMap<>();
    private Map<String, String> verifiedUsers = new HashMap<>(); // Minecraft Name -> Discord ID
    private VerificationEnforcer verificationEnforcer;

    public VerificationService(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.verifiedFile = dataDirectory.resolve("verified_users.json").toFile();
        loadVerifiedUsers();
    }

    public void setVerificationEnforcer(VerificationEnforcer enforcer) {
        this.verificationEnforcer = enforcer;
    }

    public void addPendingVerification(String minecraftName, String code, String discordId) {
        pendingVerifications.put(minecraftName.toLowerCase(), new VerificationRequest(code, discordId));
        LOGGER.info("Added pending verification for {}: code {}", minecraftName, code);
    }

    public boolean verify(String minecraftName, String code) {
        VerificationRequest request = pendingVerifications.get(minecraftName.toLowerCase());
        if (request != null && request.code.equals(code)) {
            verifiedUsers.put(minecraftName.toLowerCase(), request.discordId);
            pendingVerifications.remove(minecraftName.toLowerCase());
            saveVerifiedUsers();
            
            // Notify the enforcer to remove restrictions
            if (verificationEnforcer != null) {
                // Get the player's UUID from the server
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(minecraftName);
                    if (player != null) {
                        verificationEnforcer.onPlayerVerified(player.getUUID());
                    }
                }
            }
            
            sendSuccessToDiscord(minecraftName, request.discordId);
            return true;
        }
        return false;
    }

    private void sendSuccessToDiscord(String minecraftName, String discordId) {
        String webhookUrl = Config.COMMON.chatWebhookUrl.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("action", "verify_success");
        json.addProperty("minecraftName", minecraftName);
        json.addProperty("discordId", discordId);
        json.addProperty("username", "Server");
        json.addProperty("content", "Player " + minecraftName + " has successfully verified their account (Discord ID: " + discordId + ")!");

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                        .build();

                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 300) {
                                LOGGER.error("Failed to send verification success to Discord. Status: {}, Body: {}", response.statusCode(), response.body());
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Error sending verification success to Discord", e);
            }
        });
    }

    public boolean isVerified(String minecraftName) {
        return verifiedUsers.containsKey(minecraftName.toLowerCase());
    }

    public String getVerifiedDiscordId(String minecraftName) {
        return verifiedUsers.get(minecraftName.toLowerCase());
    }

    /**
     * Gets a copy of all verified users.
     * 
     * @return Map of Discord IDs to Minecraft names
     */
    public Map<String, String> getAllVerifiedUsers() {
        return new ConcurrentHashMap<>(verifiedUsers);
    }

    public boolean unlink(String minecraftName) {
        String removed = verifiedUsers.remove(minecraftName.toLowerCase());
        if (removed != null) {
            saveVerifiedUsers();
            
            // Notify enforcer to reapply debuffs if player is online
            if (verificationEnforcer != null) {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(minecraftName);
                    if (player != null) {
                        verificationEnforcer.onPlayerUnlinked(player);
                    }
                }
            }
            
            LOGGER.info("Unlinked player: {} (Discord ID: {})", minecraftName, removed);
            return true;
        }
        return false;
    }

    private void loadVerifiedUsers() {
        if (!verifiedFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(verifiedFile)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                verifiedUsers = loaded;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load verified users", e);
        }
    }

    private void saveVerifiedUsers() {
        if (!verifiedFile.getParentFile().exists()) {
            verifiedFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(verifiedFile)) {
            GSON.toJson(verifiedUsers, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save verified users", e);
        }
    }

    private static class VerificationRequest {
        final String code;
        final String discordId;

        VerificationRequest(String code, String discordId) {
            this.code = code;
            this.discordId = discordId;
        }
    }
}
