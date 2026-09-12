package net.kingsmp.mixin;

import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @Shadow
    public abstract Player getPlayerOwner();

    @Shadow
    private Entity hookedIn;

    @Shadow
    private int nibble;

    @Inject(method = "retrieve", at = @At("HEAD"))
    private void kingsmp$onFishCaught(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        // Only trigger on an actual successful bite/catch or entity snag
        if (this.hookedIn != null || this.nibble > 0) {
            Player owner = this.getPlayerOwner();
            if (owner instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
                int xp = (this.hookedIn != null) ? 40 : 25;
                ProfessionManager.addXp(serverPlayer, ProfessionType.FISHER, xp);

                // Lucky Line perk for level 2+ fishers
                int lvl = ProfessionManager.getLevel(serverPlayer.getUUID(), ProfessionType.FISHER);
                if (lvl >= 2) {
                    float chance = (lvl >= 5) ? 0.25f : 0.05f * (lvl - 1);
                    if (serverPlayer.getRandom().nextFloat() < chance) {
                        serverPlayer.sendOverlayMessage(
                                Component.literal("🎣 Lucky Line! Bonus catch retrieved.").withStyle(ChatFormatting.AQUA));
                        ItemStack bonus = switch (serverPlayer.getRandom().nextInt(4)) {
                            case 0 -> new ItemStack(Items.SALMON);
                            case 1 -> new ItemStack(Items.TROPICAL_FISH);
                            case 2 -> new ItemStack(Items.PUFFERFISH);
                            default -> new ItemStack(Items.COD);
                        };
                        if (!serverPlayer.getInventory().add(bonus)) {
                            serverPlayer.drop(bonus, false);
                        }
                    }
                }
            }
        }
    }
}
