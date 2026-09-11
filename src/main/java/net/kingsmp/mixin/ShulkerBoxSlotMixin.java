package net.kingsmp.mixin;

import net.kingsmp.KingSMPMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxSlot.class)
public class ShulkerBoxSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventItemsInShulker(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // If the item is the Crown OR the Dragon Egg, cancel the insertion!
        if (KingSMPMod.isCrown(stack) || stack.is(Items.DRAGON_EGG)) {
            cir.setReturnValue(false);
        }
    }
}