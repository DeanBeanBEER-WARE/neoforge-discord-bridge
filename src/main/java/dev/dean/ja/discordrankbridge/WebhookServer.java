package dev.dean.ja.discordrankbridge;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class WebhookServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookServer.class);
    private final List<HttpServer> servers = new ArrayList<>();
    private final LuckPermsService luckPermsService;
    private final VerificationService verificationService;
    private final PlayerStatsService playerStatsService;
    private final MaintenanceService maintenanceService;
    private final MuteService muteService;

    public WebhookServer(LuckPermsService luckPermsService, VerificationService verificationService, PlayerStatsService playerStatsService, MaintenanceService maintenanceService, MuteService muteService) {
        this.luckPermsService = luckPermsService;
        this.verificationService = verificationService;
        this.playerStatsService = playerStatsService;
        this.maintenanceService = maintenanceService;
        this.muteService = muteService;
    }

    public void start() {
        if (!Config.COMMON.enabled.get()) {
            LOGGER.info("Webhook server is disabled in config.");
            return;
        }

        String bindAddress = Config.COMMON.bindAddress.get();
        List<? extends Integer> ports = Config.COMMON.ports.get();
        String path = Config.COMMON.path.get();

        LOGGER.info("[DiscordBridge-DEBUG] ===== WebhookServer Starting =====");
        LOGGER.info("[DiscordBridge-DEBUG] Bind Address: {}", bindAddress);
        LOGGER.info("[DiscordBridge-DEBUG] Ports: {}", ports);
        LOGGER.info("[DiscordBridge-DEBUG] Path: {}", path);
        LOGGER.info("[DiscordBridge-DEBUG] Auth Secret configured: {}", Config.COMMON.secret.get() != null && !Config.COMMON.secret.get().isEmpty());

        for (int port : ports) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
                server.createContext(path, new WebhookHandler(luckPermsService, verificationService, playerStatsService, maintenanceService, muteService));
                server.setExecutor(null);
                server.start();
                servers.add(server);
                LOGGER.info("Webhook server started on {}:{}{}", bindAddress, port, path);
            } catch (IOException e) {
                LOGGER.error("Failed to start webhook server on port {}", port, e);
            }
        }
    }

    public void stop() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
        LOGGER.info("All webhook servers stopped.");
    }
}
