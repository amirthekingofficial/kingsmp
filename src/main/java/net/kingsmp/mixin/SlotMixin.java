package net.kingsmp.mixin;

import net.kingsmp.KingSMPMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {

    // Change "inventory" to "container" here to match Mojang mappings
    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventCrownInContainers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {

        if (KingSMPMod.isCrown(stack) || stack.is(net.minecraft.world.item.Items.DRAGON_EGG)) {
            // Update the check here to use "this.container" instead of "this.inventory"
            if (!(this.container instanceof Inventory)) {
                // Return false to tell the game "No, this item is not allowed in here!"
                cir.setReturnValue(false);
            }
        }
    }
}