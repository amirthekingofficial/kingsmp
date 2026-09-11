package net.kingsmp.mixin;

import net.kingsmp.commands.EconomyCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void kingsmp$appendWorthTooltip(Item.TooltipContext context, net.minecraft.world.entity.player.Player player, net.minecraft.world.item.TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        ItemStack self = (ItemStack) (Object) this;

        if (player != null && player.containerMenu != null) {
            String menuClassName = player.containerMenu.getClass().getName();
            if (menuClassName.contains("ShopScreenHandler") || menuClassName.contains("GambleScreenHandler")) {
                cir.setReturnValue(tooltip);
                return;
            }
        }

        if (!self.isEmpty()) {
            double singleWorth = EconomyCommands.calculateItemValue(self);
            int count = self.getCount();
            double totalWorth = singleWorth * count;
            
            if (singleWorth > 0.0) {
                if (count > 1) {
                    if (totalWorth >= 1.0) {
                        tooltip.add(Component.literal("Worth: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal((int) totalWorth + " Silver").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" (" + (singleWorth >= 1.0 ? (int) singleWorth + " Silver" : String.format("%.2f", singleWorth)) + " each)").withStyle(ChatFormatting.DARK_GRAY)));
                    } else {
                        int bulkAmount = (int) Math.ceil(1.0 / singleWorth);
                        tooltip.add(Component.literal("Worth: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("1 Silver per " + bulkAmount + " items").withStyle(ChatFormatting.YELLOW)));
                    }
                } else {
                    if (singleWorth >= 1.0) {
                        tooltip.add(Component.literal("Worth: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal((int) singleWorth + " Silver").withStyle(ChatFormatting.GREEN)));
                    } else {
                        int bulkAmount = (int) Math.ceil(1.0 / singleWorth);
                        tooltip.add(Component.literal("Worth: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("1 Silver per " + bulkAmount + " items").withStyle(ChatFormatting.YELLOW)));
                    }
                }
            }
        }

        cir.setReturnValue(tooltip);
    }
}
