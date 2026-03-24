package discordrankbridge.mixin;

import discordrankbridge.VerificationEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent unverified players from picking up items.
 */
@Mixin(ItemEntity.class)
public class PlayerPickupMixin {
    
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Get the VerificationEnforcer from the static accessor
            try {
                VerificationEnforcer enforcer = discordrankbridge.DiscordRankBridge.getStaticVerificationEnforcer();
                if (enforcer != null && !enforcer.canPickupItems(serverPlayer.getUUID())) {
                    // Cancel the pickup
                    ci.cancel();
                }
            } catch (Exception e) {
                // Silently fail - don't break the game if something goes wrong
            }
        }
    }
}
