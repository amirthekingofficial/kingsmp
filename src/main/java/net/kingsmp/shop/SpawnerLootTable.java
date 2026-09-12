package net.kingsmp.shop;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpawnerLootTable {

    private static final Random RANDOM = new Random();

    public static List<ItemStack> generateLoot(String mobType, int tier) {
        List<ItemStack> loot = new ArrayList<>();
        int multiplier = tier;
        double rareChance = Math.min(0.75, tier * 0.15); // Tier 1 = 15%, Tier 2 = 30%, Tier 3 = 45%, Tier 4 = 60%, Tier 5 = 75%

        switch (mobType.toLowerCase()) {
            case "zombie":
                // Base drops: Rotten Flesh (8-16 * tier)
                loot.add(new ItemStack(Items.ROTTEN_FLESH, (8 + RANDOM.nextInt(9)) * multiplier));
                // Rare drops: Iron Ingot, Carrot, Potato, Zombie Head
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.IRON_INGOT, multiplier * 2));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.CARROT, multiplier * 2));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.POTATO, multiplier * 2));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.ZOMBIE_HEAD, 1));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.IRON_BLOCK, 2));
                }
                break;

            case "skeleton":
                // Base drops: Bone (8-16 * tier), Arrow (8-16 * tier)
                loot.add(new ItemStack(Items.BONE, (8 + RANDOM.nextInt(9)) * multiplier));
                loot.add(new ItemStack(Items.ARROW, (8 + RANDOM.nextInt(9)) * multiplier));
                // Rare drops: Bow, Skeleton Skull
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.BOW, 1));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SKELETON_SKULL, 1));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SPECTRAL_ARROW, 16));
                }
                break;

            case "creeper":
                // Base drops: Gunpowder (8-16 * tier)
                loot.add(new ItemStack(Items.GUNPOWDER, (8 + RANDOM.nextInt(9)) * multiplier));
                // Rare drops: Music Disc (random, sells for 50 Silver), Creeper Head
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.CREEPER_HEAD, 1));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    net.minecraft.world.item.Item[] discs = {
                        Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
                        Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
                        Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
                        Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
                        Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_PIGSTEP
                    };
                    loot.add(new ItemStack(discs[RANDOM.nextInt(discs.length)], 1));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.TNT, 8));
                }
                break;

            case "spider":
                // Base drops: String (8-16 * tier), Spider Eye (5-9 * tier)
                loot.add(new ItemStack(Items.STRING, (8 + RANDOM.nextInt(9)) * multiplier));
                loot.add(new ItemStack(Items.SPIDER_EYE, (5 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Cobweb, Fermented Eye, Spider Skull/Web injection
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.COBWEB, multiplier * 2));
                }
                if (tier >= 3 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.FERMENTED_SPIDER_EYE, multiplier));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.COBWEB, 8));
                }
                break;

            case "blaze":
                // Base drops: Blaze Rod (5-9 * tier)
                loot.add(new ItemStack(Items.BLAZE_ROD, (5 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Fire Charge, Magma Cream, Gold Ingot
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.FIRE_CHARGE, multiplier * 2));
                }
                if (tier >= 4 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.MAGMA_CREAM, multiplier));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.GOLD_INGOT, 4 * multiplier));
                }
                break;

            case "enderman":
                // Base drops: Ender Pearl (4-8 * tier)
                loot.add(new ItemStack(Items.ENDER_PEARL, (4 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Eye of Ender, Echo Shards (7.5 Silver each!), Shulker Shell floor
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.ENDER_EYE, multiplier * 2));
                }
                if (tier >= 3 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.ECHO_SHARD, multiplier));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SHULKER_SHELL, 4));
                }
                break;

            case "slime":
                // Base drops: Slimeball (8-16 * tier)
                loot.add(new ItemStack(Items.SLIME_BALL, (8 + RANDOM.nextInt(9)) * multiplier));
                // Rare drops: Slime Block, Emerald floor drop for Slime deficit correction
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SLIME_BLOCK, multiplier));
                }
                if (tier >= 3 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SLIME_BLOCK, multiplier * 2));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.EMERALD, 8 * multiplier));
                }
                break;

            case "witch":
                // Base drops: Glowstone Dust (5-9 * tier), Redstone (5-9 * tier), Gunpowder (5-9 * tier)
                loot.add(new ItemStack(Items.GLOWSTONE_DUST, (5 + RANDOM.nextInt(5)) * multiplier));
                loot.add(new ItemStack(Items.REDSTONE, (5 + RANDOM.nextInt(5)) * multiplier));
                loot.add(new ItemStack(Items.GUNPOWDER, (5 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Potion of Healing, Sugar, Brewing Stand
                if (RANDOM.nextDouble() < rareChance) {
                    ItemStack healingPotion = new ItemStack(Items.POTION);
                    healingPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
                    loot.add(healingPotion);
                }
                if (tier >= 3 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SUGAR, multiplier * 2));
                }
                if (tier >= 5 && RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.BREWING_STAND, 1));
                }
                break;
        }
        return loot;
    }
}
