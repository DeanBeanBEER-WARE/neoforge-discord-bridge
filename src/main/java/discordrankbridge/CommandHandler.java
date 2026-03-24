package discordrankbridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, DiscordRankBridge mod) {
        // Register /verify command
        dispatcher.register(Commands.literal("verify")
            .then(Commands.argument("code", StringArgumentType.string())
                .executes(ctx -> {
                    String code = StringArgumentType.getString(ctx, "code");
                    String playerName = ctx.getSource().getPlayerOrException().getName().getString();
                    
                    VerificationService verificationService = mod.getVerificationService();
                    if (verificationService != null && verificationService.verify(playerName, code)) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aSuccessfully verified! Your Discord account is now linked."), false);
                        return 1;
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cInvalid verification code or no pending request found."));
                        return 0;
                    }
                })
            )
        );

        // Register /debugplaytime command
        dispatcher.register(Commands.literal("debugplaytime")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    try {
                        ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "player");
                        
                        PlayerStatsService playerStatsService = mod.getPlayerStatsService();
                        if (playerStatsService == null) {
                            ctx.getSource().sendFailure(Component.literal("§cPlayerStatsService not initialized."));
                            return 0;
                        }

                        String formattedPlaytime = playerStatsService.getFormattedPlaytime(targetPlayer);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e[Debug] §7Playtime for §a" + targetPlayer.getName().getString() + "§7: §b" + formattedPlaytime), false);
                        LOGGER.info("[DiscordRankBridge] /debugplaytime executed for player: {}, playtime: {}", targetPlayer.getName().getString(), formattedPlaytime);
                        return 1;
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
                        LOGGER.error("[DiscordRankBridge] Error executing /debugplaytime", e);
                        return 0;
                    }
                })
            )
        );
        
        LOGGER.info("[DiscordRankBridge] Registered /verify and /debugplaytime commands");
    }
}
