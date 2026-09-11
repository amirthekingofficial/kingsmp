package net.kingsmp.mixin;

import net.kingsmp.KingSMPMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventCrownDrop(ItemStack stack, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir) {

        if (KingSMPMod.isCrown(stack)) {
            Player player = (Player) (Object) this;

            if (!player.isAlive()) {
                return;
            }

            if (player.getInventory().add(stack)) {
                player.sendOverlayMessage(
                        Component.literal("👑 You cannot drop the Crown!").withStyle(ChatFormatting.RED));

                // GHOST ITEM FIX: Force the UI to refresh so the item doesn't visually
                // disappear
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.containerMenu.broadcastFullState();
                }

                cir.setReturnValue(null);
            }
        }
    }
}