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

import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

public class PlayerBlockBreakHook {

    public static void register() {
        // Handled via BlockMixin to support veinminer mods
    }

    public static void handleBlockBreak(ServerPlayer serverPlayer, BlockState state) {
        handleBlockBreak(serverPlayer, serverPlayer.blockPosition(), state, serverPlayer.getMainHandItem());
    }

    public static void handleBlockBreak(ServerPlayer serverPlayer, BlockPos pos, BlockState state, ItemStack tool) {
        if (serverPlayer.isCreative()) {
            return;
        }

        UUID uuid = serverPlayer.getUUID();

        // ── 1. MINING QUESTS ──
        ActiveQuest quest = KingSMPMod.dataManager.getActiveQuest(uuid);
        if (quest != null && quest.type.equals("mining")) {
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            boolean matches = blockId.equals(quest.targetId) || 
                              (quest.targetId.equals("iron_ore") && blockId.equals("deepslate_iron_ore")) ||
                              (quest.targetId.equals("diamond_ore") && blockId.equals("deepslate_diamond_ore")) ||
                              (quest.targetId.equals("any_ore") && (blockId.contains("ore") || blockId.contains("debris")));
            
            if (matches) {
                synchronized (quest) {
                    if (KingSMPMod.dataManager.getActiveQuest(uuid) == null) {
                        return;
                    }
                    quest.progress++;
                    if (quest.progress >= quest.target) {
                        completeQuest(serverPlayer, quest);
                    } else {
                        serverPlayer.sendSystemMessage(Component.literal("⛏️ Quest Progress: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(quest.progress + "/" + quest.target).withStyle(ChatFormatting.YELLOW))
                                .append(" blocks mined."));
                    }
                }
            }
        }

        // ── 2. MINER PROFESSION XP & PERKS ──
        int minerXp = 0;
        ItemStack bonusOreDrop = ItemStack.EMPTY;

        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            minerXp = 5;
            bonusOreDrop = new ItemStack(Items.COAL);
        } else if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
            minerXp = 5;
            bonusOreDrop = new ItemStack(Items.RAW_COPPER);
        } else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            minerXp = 10;
            bonusOreDrop = new ItemStack(Items.RAW_IRON);
        } else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.NETHER_GOLD_ORE)) {
            minerXp = 15;
            bonusOreDrop = new ItemStack(Items.RAW_GOLD);
        } else if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            minerXp = 12;
            bonusOreDrop = new ItemStack(Items.REDSTONE);
        } else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            minerXp = 12;
            bonusOreDrop = new ItemStack(Items.LAPIS_LAZULI);
        } else if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
            minerXp = 10;
            bonusOreDrop = new ItemStack(Items.QUARTZ);
        } else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            minerXp = 35;
            bonusOreDrop = new ItemStack(Items.DIAMOND);
        } else if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            minerXp = 40;
            bonusOreDrop = new ItemStack(Items.EMERALD);
        } else if (state.is(Blocks.ANCIENT_DEBRIS)) {
            minerXp = 65;
            bonusOreDrop = new ItemStack(Items.ANCIENT_DEBRIS);
        }

        if (minerXp > 0) {
            ProfessionManager.addXp(serverPlayer, ProfessionType.MINER, minerXp);

            int minerLvl = ProfessionManager.getLevel(uuid, ProfessionType.MINER);
            if (minerLvl >= 2 && !bonusOreDrop.isEmpty()) {
                float bonusChance = (minerLvl >= 5) ? 0.25f : 0.05f * (minerLvl - 1);
                if (serverPlayer.getRandom().nextFloat() < bonusChance) {
                    Block.popResource(serverPlayer.level(), pos, bonusOreDrop.copy());
                    serverPlayer.sendOverlayMessage(
                            Component.literal("⛏ Bonus Ore Harvest!").withStyle(ChatFormatting.GOLD));
                }
            }
        }

        // ── 3. FARMER PROFESSION XP & PERKS ──
        int farmerXp = 0;
        ItemStack bonusCropDrop = ItemStack.EMPTY;

        if (state.getBlock() instanceof CropBlock cropBlock) {
            if (cropBlock.isMaxAge(state)) {
                farmerXp = 10;
                if (state.is(Blocks.WHEAT)) bonusCropDrop = new ItemStack(Items.WHEAT);
                else if (state.is(Blocks.CARROTS)) bonusCropDrop = new ItemStack(Items.CARROT);
                else if (state.is(Blocks.POTATOES)) bonusCropDrop = new ItemStack(Items.POTATO);
                else if (state.is(Blocks.BEETROOTS)) bonusCropDrop = new ItemStack(Items.BEETROOT);
            }
        } else if (state.getBlock() instanceof CocoaBlock) {
            if (state.getValue(CocoaBlock.AGE) == 2) {
                farmerXp = 10;
                bonusCropDrop = new ItemStack(Items.COCOA_BEANS);
            }
        } else if (state.getBlock() instanceof NetherWartBlock) {
            if (state.getValue(NetherWartBlock.AGE) == 3) {
                farmerXp = 10;
                bonusCropDrop = new ItemStack(Items.NETHER_WART);
            }
        } else if (state.is(Blocks.PUMPKIN)) {
            farmerXp = 10;
            bonusCropDrop = new ItemStack(Items.PUMPKIN);
        } else if (state.is(Blocks.MELON)) {
            farmerXp = 10;
            bonusCropDrop = new ItemStack(Items.MELON_SLICE, 3);
        } else if (state.is(Blocks.SUGAR_CANE)) {
            farmerXp = 5;
            bonusCropDrop = new ItemStack(Items.SUGAR_CANE);
        } else if (state.is(Blocks.CACTUS)) {
            farmerXp = 5;
            bonusCropDrop = new ItemStack(Items.CACTUS);
        } else if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
            farmerXp = 5;
            bonusCropDrop = new ItemStack(Items.KELP);
        } else if (state.getBlock() instanceof SweetBerryBushBlock) {
            if (state.getValue(SweetBerryBushBlock.AGE) >= 2) {
                farmerXp = 8;
                bonusCropDrop = new ItemStack(Items.SWEET_BERRIES, 2);
            }
        }

        if (farmerXp > 0) {
            ProfessionManager.addXp(serverPlayer, ProfessionType.FARMER, farmerXp);

            int farmerLvl = ProfessionManager.getLevel(uuid, ProfessionType.FARMER);
            if (farmerLvl >= 2 && !bonusCropDrop.isEmpty()) {
                float bonusChance = (farmerLvl >= 5) ? 0.25f : 0.05f * (farmerLvl - 1);
                if (serverPlayer.getRandom().nextFloat() < bonusChance) {
                    Block.popResource(serverPlayer.level(), pos, bonusCropDrop.copy());
                    serverPlayer.sendOverlayMessage(
                            Component.literal("🌾 Bountiful Soil! Extra crop harvested.").withStyle(ChatFormatting.GREEN));
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
