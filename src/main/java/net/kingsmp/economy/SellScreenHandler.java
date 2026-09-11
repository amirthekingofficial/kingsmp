package net.kingsmp.economy;

import net.kingsmp.KingSMPMod;
import net.kingsmp.commands.EconomyCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

public class SellScreenHandler extends ChestMenu {

    private final Container container;

    public SellScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(27));
    }

    public SellScreenHandler(int syncId, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, container, 3);
        this.container = container;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        double accumulatedDoubleValue = 0.0;
        int soldCount = 0;
        java.util.List<ItemStack> unsellableReturned = new java.util.ArrayList<>();
        
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            
            if (KingSMPMod.isCrown(stack) || stack.is(Items.DRAGON_EGG)) {
                unsellableReturned.add(stack);
                continue;
            }
            
            double singleValue = EconomyCommands.calculateItemValue(stack);
            double value = singleValue * stack.getCount();
            if (value >= 0.01) {
                accumulatedDoubleValue += value;
                soldCount += stack.getCount();
            } else {
                unsellableReturned.add(stack);
            }
        }

        int finalEarnings = (int) Math.floor(accumulatedDoubleValue);
        
        // Return unsellable/returned items
        for (ItemStack returnStack : unsellableReturned) {
            if (!serverPlayer.getInventory().add(returnStack)) {
                serverPlayer.drop(returnStack, false);
            }
        }

        if (finalEarnings > 0) {
            KingSMPMod.dataManager.addSilver(serverPlayer.getUUID(), finalEarnings);
            KingSMPMod.saveNow();
            serverPlayer.sendSystemMessage(Component.literal("💰 Sold ")
                    .append(Component.literal(String.valueOf(soldCount)).withStyle(ChatFormatting.YELLOW))
                    .append(" items for ")
                    .append(Component.literal(finalEarnings + " Silver").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    .append("!"));
        } else if (soldCount > 0) {
            serverPlayer.sendSystemMessage(Component.literal("❌ The items placed are not worth enough to equal 1 Silver in bulk.")
                    .withStyle(ChatFormatting.RED));
            // Return everything back to the player if nothing was sold
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && !KingSMPMod.isCrown(stack) && !stack.is(Items.DRAGON_EGG)) {
                    if (!serverPlayer.getInventory().add(stack)) {
                        serverPlayer.drop(stack, false);
                    }
                }
            }
        }
    }
}
