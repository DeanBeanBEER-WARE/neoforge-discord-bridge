package dev.dean.ja.discordrankbridge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to suppress AFK broadcast messages from NeoEssentials (or similar mods)
 * that use broadcastSystemMessage to announce AFK status changes.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void discordrankbridge$onBroadcastSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        String text = message.getString();
        if (text.contains("is now AFK") || text.contains("is no longer AFK")) {
            ci.cancel();
        }
    }
}
