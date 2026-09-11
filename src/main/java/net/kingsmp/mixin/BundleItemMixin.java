package net.kingsmp.mixin;

import net.kingsmp.KingSMPMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleItem.class)
public class BundleItemMixin {

    // ── SCENARIO 1: Holding a Bundle and right-clicking the Crown/Egg in your
    // inventory ──
    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventInsertWithBundle(ItemStack stack, Slot slot, ClickAction clickType, Player player,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemInSlot = slot.getItem();

        if (clickType == ClickAction.SECONDARY && (KingSMPMod.isCrown(itemInSlot) || itemInSlot.is(Items.DRAGON_EGG))) {
            cir.setReturnValue(false); // Bounces it back!
        }
    }

    // ── SCENARIO 2: Holding the Crown/Egg and right-clicking a Bundle in your
    // inventory ──
    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void kingsmp$preventInsertIntoBundle(ItemStack stack, ItemStack otherStack, Slot slot,
            ClickAction clickType, Player player, SlotAccess cursorStackReference,
            CallbackInfoReturnable<Boolean> cir) {

        if (clickType == ClickAction.SECONDARY && (KingSMPMod.isCrown(otherStack) || otherStack.is(Items.DRAGON_EGG))) {
            cir.setReturnValue(false); // Bounces it back!
        }
    }
}