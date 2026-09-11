package net.kingsmp.events;

import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager.ActiveQuest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class PlayerBlockBreakHook {

    public static void register() {
        // Handled via BlockMixin to support veinminer mods
    }

    public static void handleBlockBreak(ServerPlayer serverPlayer, BlockState state) {
        UUID uuid = serverPlayer.getUUID();
        ActiveQuest quest = KingSMPMod.dataManager.getActiveQuest(uuid);

        if (quest != null && quest.type.equals("mining")) {
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            boolean matches = blockId.equals(quest.targetId) || 
                              (quest.targetId.equals("iron_ore") && blockId.equals("deepslate_iron_ore")) ||
                              (quest.targetId.equals("diamond_ore") && blockId.equals("deepslate_diamond_ore")) ||
                              (quest.targetId.equals("any_ore") && (blockId.contains("ore") || blockId.contains("debris")));
            
            if (matches) {
                synchronized (quest) {
                    // Check again in case it completed on another thread concurrently
                    if (KingSMPMod.dataManager.getActiveQuest(uuid) == null) {
                        return;
                    }
                    quest.progress++;
                    if (quest.progress >= quest.target) {
                        // Quest complete!
                        completeQuest(serverPlayer, quest);
                    } else {
                        serverPlayer.sendSystemMessage(Component.literal("⛏️ Quest Progress: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(quest.progress + "/" + quest.target).withStyle(ChatFormatting.YELLOW))
                                .append(" blocks mined."));
                        KingSMPMod.saveNow();
                    }
                }
            }
        }
    }

    public static void completeQuest(ServerPlayer player, ActiveQuest quest) {
        KingSMPMod.dataManager.setActiveQuest(player.getUUID(), null);
        
        // Award Silver
        KingSMPMod.dataManager.addSilver(player.getUUID(), quest.silverReward);
        
        player.sendSystemMessage(Component.literal("🎉 Quest Completed! ")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(quest.description).withStyle(ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("💰 Reward: " + quest.silverReward + " Silver").withStyle(ChatFormatting.GOLD));

        // Award Item Reward (must be rare items only, e.g. diamonds)
        if (quest.itemRewardId != null && !quest.itemRewardId.isEmpty()) {
            ItemStack reward = ItemStack.EMPTY;
            if (quest.itemRewardId.equals("diamond")) {
                reward = new ItemStack(Items.DIAMOND, quest.itemRewardCount);
            }
            
            if (!reward.isEmpty()) {
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                player.sendSystemMessage(Component.literal("🎁 Extra Reward: " + quest.itemRewardCount + "x " + reward.getHoverName().getString())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        
        KingSMPMod.playSoundToPlayer(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        KingSMPMod.saveNow();
    }
}
