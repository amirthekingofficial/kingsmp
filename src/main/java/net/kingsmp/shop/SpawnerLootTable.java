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
        double rareChance = tier * 0.10; // Tier 1 = 10%, Tier 5 = 50%

        switch (mobType.toLowerCase()) {
            case "zombie":
                // Base drops: Rotten Flesh (4-8)
                loot.add(new ItemStack(Items.ROTTEN_FLESH, (4 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Iron Ingot, Carrot, Potato, Zombie Head
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.IRON_INGOT, multiplier));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.CARROT, multiplier));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.POTATO, multiplier));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.ZOMBIE_HEAD, 1));
                }
                break;

            case "skeleton":
                // Base drops: Bone (4-8), Arrow (4-8)
                loot.add(new ItemStack(Items.BONE, (4 + RANDOM.nextInt(5)) * multiplier));
                loot.add(new ItemStack(Items.ARROW, (4 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Bow, Skeleton Skull
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.BOW, 1));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SKELETON_SKULL, 1));
                }
                break;

            case "creeper":
                // Base drops: Gunpowder (4-8)
                loot.add(new ItemStack(Items.GUNPOWDER, (4 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Music Disc (random), Creeper Head
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.CREEPER_HEAD, 1));
                }
                if (RANDOM.nextDouble() < rareChance) {
                    // Random music disc
                    net.minecraft.world.item.Item[] discs = {
                        Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
                        Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
                        Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
                        Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
                        Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_PIGSTEP
                    };
                    loot.add(new ItemStack(discs[RANDOM.nextInt(discs.length)], 1));
                }
                break;

            case "spider":
                // Base drops: String (4-8), Spider Eye (2-4)
                loot.add(new ItemStack(Items.STRING, (4 + RANDOM.nextInt(5)) * multiplier));
                loot.add(new ItemStack(Items.SPIDER_EYE, (2 + RANDOM.nextInt(3)) * multiplier));
                // Rare drops: Cobweb
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.COBWEB, multiplier));
                }
                break;

            case "blaze":
                // Base drops: Blaze Rod (2-4)
                loot.add(new ItemStack(Items.BLAZE_ROD, (2 + RANDOM.nextInt(3)) * multiplier));
                // Rare drops: Fire Charge
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.FIRE_CHARGE, multiplier));
                }
                break;

            case "enderman":
                // Base drops: Ender Pearl (1-2)
                loot.add(new ItemStack(Items.ENDER_PEARL, (1 + RANDOM.nextInt(2)) * multiplier));
                // Rare drops: Eye of Ender
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.ENDER_EYE, multiplier));
                }
                break;

            case "slime":
                // Base drops: Slimeball (4-8)
                loot.add(new ItemStack(Items.SLIME_BALL, (4 + RANDOM.nextInt(5)) * multiplier));
                // Rare drops: Slime Block
                if (RANDOM.nextDouble() < rareChance) {
                    loot.add(new ItemStack(Items.SLIME_BLOCK, multiplier));
                }
                break;

            case "witch":
                // Base drops: Glowstone Dust (2-4), Redstone (2-4), Gunpowder (2-4)
                loot.add(new ItemStack(Items.GLOWSTONE_DUST, (2 + RANDOM.nextInt(3)) * multiplier));
                loot.add(new ItemStack(Items.REDSTONE, (2 + RANDOM.nextInt(3)) * multiplier));
                loot.add(new ItemStack(Items.GUNPOWDER, (2 + RANDOM.nextInt(3)) * multiplier));
                // Rare drops: Potion of Healing (Strong/standard)
                if (RANDOM.nextDouble() < rareChance) {
                    ItemStack healingPotion = new ItemStack(Items.POTION);
                    healingPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
                    loot.add(healingPotion);
                }
                break;
        }
        return loot;
    }
}
