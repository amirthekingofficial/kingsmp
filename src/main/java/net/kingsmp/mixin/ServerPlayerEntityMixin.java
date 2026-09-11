package net.kingsmp.mixin;

import net.kingsmp.KingSMPMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.kingsmp.events.CombatTracker;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventCrownQKey(boolean dropAll, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ItemStack stack = player.getMainHandItem();

        if (KingSMPMod.isCrown(stack)) {
            player.sendSystemMessage(Component.literal("👑 You cannot drop the Crown!").withStyle(ChatFormatting.RED),
                    true);

            // GHOST ITEM FIX: Force the server to sync the inventory back to the client!
            int slot = player.getInventory().getSelectedSlot();
            player.getInventory().setItem(slot, stack.copy());

            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void kingsmp$dethroneOnDeath(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Clear combat tag on death
        CombatTracker.removePlayer(player.getUUID());

        if (KingSMPMod.dataManager.isKing(player.getUUID())) {

            // DEGRADATION MECHANIC: Drops one upgrade step on EVERY death (Step 3 -> Step 2 -> Step 1)
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (KingSMPMod.isCrown(stack)) {
                    int step = KingSMPMod.getCrownStep(stack);
                    if (step > 1) {
                        KingSMPMod.setCrownStep(stack, step - 1);
                    }
                    break;
                }
            }

            KingSMPMod.dataManager.removeKing(player.getUUID());

            net.minecraft.server.MinecraftServer server = player.level().getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("☠ " + player.getScoreboardName() + " was slain and dropped their Crown!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        false);
            }
        }
    }

}