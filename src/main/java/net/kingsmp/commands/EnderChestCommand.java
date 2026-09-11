package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

public class EnderChestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, KingDataManager dataManager) {
        dispatcher.register(Commands.literal("enderchest")
                .executes(ctx -> openEnderChest(ctx, dataManager))
                .then(Commands.literal("upgrade")
                        .executes(ctx -> upgradeEnderChest(ctx, dataManager))));

        dispatcher.register(Commands.literal("ec")
                .executes(ctx -> openEnderChest(ctx, dataManager))
                .then(Commands.literal("upgrade")
                        .executes(ctx -> upgradeEnderChest(ctx, dataManager))));
    }

    private static int openEnderChest(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.closeContainer();
            if (dataManager.hasUnlockedEnderChest(player.getUUID())) {
                if (dataManager.hasUnlockedLargeEnderChest(player.getUUID())) {
                    openLargeEnderChest(player, dataManager);
                } else {
                    player.openMenu(new SimpleMenuProvider(
                            (syncId, inventory, p) -> ChestMenu.threeRows(syncId, inventory, p.getEnderChestInventory()),
                            Component.translatable("container.enderchest")
                    ));
                }
                return 1;
            }

            // Not unlocked yet, check for 32 diamonds
            int countNeeded = 32;
            int countFound = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.DIAMOND)) {
                    countFound += stack.getCount();
                }
            }

            if (countFound >= countNeeded) {
                int remainingToTake = countNeeded;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(Items.DIAMOND)) {
                        int take = Math.min(remainingToTake, stack.getCount());
                        stack.shrink(take);
                        remainingToTake -= take;
                        if (remainingToTake <= 0) {
                            break;
                        }
                    }
                }

                dataManager.unlockEnderChest(player.getUUID());
                KingSMPMod.saveNow();

                player.sendSystemMessage(Component.literal("💎 Remote Ender Chest unlocked successfully!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

                // Open it immediately
                player.openMenu(new SimpleMenuProvider(
                        (syncId, inventory, p) -> ChestMenu.threeRows(syncId, inventory, p.getEnderChestInventory()),
                        Component.translatable("container.enderchest")
                ));
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("❌ You must unlock the Remote Ender Chest first!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("Cost: 32x Diamonds. You currently have " + countFound + "/32.")
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    public static void openLargeEnderChest(ServerPlayer player, KingDataManager dataManager) {
        player.closeContainer();
        java.util.List<ItemStack> items = dataManager.getExpandedEnderChest(player.getUUID());
        LargeEnderChestContainer container = new LargeEnderChestContainer(items);

        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> ChestMenu.sixRows(syncId, inventory, container),
                Component.translatable("container.enderchest")
        ));
    }

    private static int upgradeEnderChest(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.closeContainer();
            if (!dataManager.hasUnlockedEnderChest(player.getUUID())) {
                player.sendSystemMessage(Component.literal("❌ You must unlock the Remote Ender Chest first using /ec (costs 32 Diamonds).")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            if (dataManager.hasUnlockedLargeEnderChest(player.getUUID())) {
                player.sendSystemMessage(Component.literal("❌ Your Ender Chest is already fully expanded to a Large Chest!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            int countNeeded = 2;
            int countFound = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.NETHERITE_INGOT)) {
                    countFound += stack.getCount();
                }
            }

            if (countFound >= countNeeded) {
                int remainingToTake = countNeeded;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(Items.NETHERITE_INGOT)) {
                        int take = Math.min(remainingToTake, stack.getCount());
                        stack.shrink(take);
                        remainingToTake -= take;
                        if (remainingToTake <= 0) {
                            break;
                        }
                    }
                }

                dataManager.unlockLargeEnderChest(player.getUUID());
                
                // Copy existing items from player's normal Ender Chest to the first 27 slots of the expanded one
                net.minecraft.world.inventory.PlayerEnderChestContainer oldChest = player.getEnderChestInventory();
                java.util.List<ItemStack> newChest = dataManager.getExpandedEnderChest(player.getUUID());
                for (int i = 0; i < oldChest.getContainerSize(); i++) {
                    newChest.set(i, oldChest.getItem(i).copy());
                }
                oldChest.clearContent();
                
                KingSMPMod.saveNow();

                player.sendSystemMessage(Component.literal("🔥 Remote Ender Chest expanded successfully to a Large Chest (54 slots)!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                
                openLargeEnderChest(player, dataManager);
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("❌ You must have 2x Netherite Ingots to expand the Ender Chest!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("You currently have " + countFound + "/2 Netherite Ingots.")
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static class LargeEnderChestContainer extends net.minecraft.world.SimpleContainer {
        private boolean initializing = true;
        private final java.util.List<ItemStack> items;

        public LargeEnderChestContainer(java.util.List<ItemStack> items) {
            super(54);
            this.items = items;
            for (int i = 0; i < 54; i++) {
                this.setItem(i, items.get(i).copy());
            }
            this.initializing = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!initializing) {
                for (int i = 0; i < 54; i++) {
                    items.set(i, this.getItem(i).copy());
                }
                KingSMPMod.saveNow();
            }
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            super.stopOpen(user);
            for (int i = 0; i < 54; i++) {
                items.set(i, this.getItem(i).copy());
            }
            KingSMPMod.saveNow();
        }
    }
}
