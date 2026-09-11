package net.kingsmp.economy;

import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager.ActiveQuest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class QuestScreenHandler extends ChestMenu {

    public QuestScreenHandler(int syncId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, new SimpleContainer(27), 3);
        setupInitialSlots();
    }

    private void setupInitialSlots() {
        // Border slots (stained glass panes)
        ItemStack border = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));

        for (int i = 0; i < 27; i++) {
            if (i != 10 && i != 13 && i != 16) {
                this.getContainer().setItem(i, border);
            }
        }

        // Easy Quest Slot (10) - Lime Concrete / Wool
        QuestManager.QuestTemplate easyT = QuestManager.getDailyQuest("easy", 0);
        ItemStack easyItem = new ItemStack(Items.WOOL.lime());
        easyItem.set(DataComponents.CUSTOM_NAME,
                Component.literal("🟢 Easy Contract").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        List<Component> easyLore = new ArrayList<>();
        easyLore.add(Component.literal(""));
        easyLore.add(Component.literal("Task: " + easyT.description).withStyle(ChatFormatting.GRAY));
        easyLore.add(Component.literal("Reward: " + easyT.silverReward + " Silver").withStyle(ChatFormatting.GOLD));
        easyLore.add(Component.literal(""));
        easyLore.add(Component.literal("► Click to Accept").withStyle(ChatFormatting.GREEN));
        easyItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(easyLore));
        this.getContainer().setItem(10, easyItem);

        // Medium Quest Slot (13) - Yellow Concrete / Wool
        QuestManager.QuestTemplate mediumT = QuestManager.getDailyQuest("medium", 1);
        ItemStack mediumItem = new ItemStack(Items.WOOL.yellow());
        mediumItem.set(DataComponents.CUSTOM_NAME,
                Component.literal("🟡 Medium Contract").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        List<Component> mediumLore = new ArrayList<>();
        mediumLore.add(Component.literal(""));
        mediumLore.add(Component.literal("Task: " + mediumT.description).withStyle(ChatFormatting.GRAY));
        mediumLore.add(Component.literal("Reward: " + mediumT.silverReward + " Silver" + (mediumT.itemRewardCount > 0 ? " + " + mediumT.itemRewardCount + "x Diamond" : "")).withStyle(ChatFormatting.GOLD));
        mediumLore.add(Component.literal(""));
        mediumLore.add(Component.literal("► Click to Accept").withStyle(ChatFormatting.GREEN));
        mediumItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(mediumLore));
        this.getContainer().setItem(13, mediumItem);

        // Hard Quest Slot (16) - Red Concrete / Wool
        QuestManager.QuestTemplate hardT = QuestManager.getDailyQuest("hard", 2);
        ItemStack hardItem = new ItemStack(Items.WOOL.red());
        hardItem.set(DataComponents.CUSTOM_NAME,
                Component.literal("🔴 Hard Contract").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> hardLore = new ArrayList<>();
        hardLore.add(Component.literal(""));
        hardLore.add(Component.literal("Task: " + hardT.description).withStyle(ChatFormatting.GRAY));
        hardLore.add(Component.literal("Reward: " + hardT.silverReward + " Silver" + (hardT.itemRewardCount > 0 ? " + " + hardT.itemRewardCount + "x Diamond" : "")).withStyle(ChatFormatting.GOLD));
        hardLore.add(Component.literal(""));
        hardLore.add(Component.literal("► Click to Accept").withStyle(ChatFormatting.GREEN));
        hardItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(hardLore));
        this.getContainer().setItem(16, hardItem);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Only allow clicking the quest slots
        if (slotIndex == 10 || slotIndex == 13 || slotIndex == 16) {
            QuestManager.QuestTemplate template = null;

            if (slotIndex == 10) {
                template = QuestManager.getDailyQuest("easy", 0);
            } else if (slotIndex == 13) {
                template = QuestManager.getDailyQuest("medium", 1);
            } else if (slotIndex == 16) {
                template = QuestManager.getDailyQuest("hard", 2);
            }

            if (KingSMPMod.dataManager.getActiveQuest(serverPlayer.getUUID()) != null) {
                serverPlayer.sendSystemMessage(
                        Component.literal("❌ You already have an active quest! Use /quest cancel to abandon it first.")
                                .withStyle(ChatFormatting.RED));
            } else if (template != null) {
                ActiveQuest quest = new ActiveQuest(
                        template.type,
                        template.difficulty,
                        template.description,
                        0,
                        template.target,
                        template.targetId,
                        template.silverReward,
                        template.itemRewardId,
                        template.itemRewardCount
                );
                KingSMPMod.dataManager.setActiveQuest(serverPlayer.getUUID(), quest);
                KingSMPMod.saveNow();

                serverPlayer.sendSystemMessage(Component.literal("📜 Quest Accepted: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(quest.description).withStyle(ChatFormatting.YELLOW,
                                ChatFormatting.BOLD)));
                serverPlayer.closeContainer();
            }
        }

        this.broadcastFullState();
    }
}
