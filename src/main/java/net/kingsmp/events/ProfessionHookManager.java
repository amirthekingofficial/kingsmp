package net.kingsmp.events;

import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public class ProfessionHookManager {

    public static void handleSlotTake(ServerPlayer player, Slot slot, ItemStack stack) {
        if (stack.isEmpty() || player.isCreative()) {
            return;
        }

        // ── 1. FURNACE / SMELTING (Blacksmith & Farmer/Cook) ──
        if (slot instanceof FurnaceResultSlot) {
            Item item = stack.getItem();
            int count = stack.getCount();

            if (item == Items.NETHERITE_SCRAP || item == Items.NETHERITE_INGOT) {
                ProfessionManager.addXp(player, ProfessionType.BLACKSMITH, Math.min(100, 40 * count));
            } else if (item == Items.IRON_INGOT || item == Items.GOLD_INGOT) {
                ProfessionManager.addXp(player, ProfessionType.BLACKSMITH, Math.min(60, 10 * count));
            } else if (item == Items.COPPER_INGOT) {
                ProfessionManager.addXp(player, ProfessionType.BLACKSMITH, Math.min(40, 5 * count));
            } else if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_CHICKEN
                    || item == Items.COOKED_MUTTON || item == Items.COOKED_SALMON || item == Items.COOKED_COD
                    || item == Items.BAKED_POTATO) {
                ProfessionManager.addXp(player, ProfessionType.FARMER, Math.min(40, 5 * count));
            }
            return;
        }

        // ── 2. CRAFTING TABLE / 2x2 (Blacksmith, Alchemist, Farmer) ──
        if (slot instanceof ResultSlot) {
            Item item = stack.getItem();

            // Blacksmith: Armor, Tools, Weapons, Shields, Anvil
            boolean isSmithItem = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                    || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                    || stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
                    || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)
                    || stack.is(Items.SHIELD) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)
                    || stack.is(Items.ANVIL) || stack.is(Items.CHIPPED_ANVIL) || stack.is(Items.DAMAGED_ANVIL);

            if (isSmithItem) {
                int xp = 25;
                if (stack.is(Items.NETHERITE_SWORD) || stack.is(Items.NETHERITE_PICKAXE) || stack.is(Items.NETHERITE_AXE)
                        || stack.is(Items.NETHERITE_CHESTPLATE) || stack.is(Items.NETHERITE_HELMET)
                        || stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS)
                        || stack.is(Items.DIAMOND_SWORD) || stack.is(Items.DIAMOND_PICKAXE)
                        || stack.is(Items.DIAMOND_CHESTPLATE)) {
                    xp = 45;
                }
                ProfessionManager.addXp(player, ProfessionType.BLACKSMITH, xp);

                // Blacksmith Masterwork perk: Level 2+ has a chance for bonus announcement / quality
                int smithLvl = ProfessionManager.getLevel(player.getUUID(), ProfessionType.BLACKSMITH);
                if (smithLvl >= 2) {
                    float chance = (smithLvl >= 5) ? 0.25f : 0.05f * (smithLvl - 1);
                    if (player.getRandom().nextFloat() < chance) {
                        player.sendOverlayMessage(
                                Component.literal("🔨 Masterwork Craft! Exceptional forging precision.").withStyle(ChatFormatting.GOLD));
                    }
                }
                return;
            }

            // Alchemist: Potions, Splash/Lingering, Tipped Arrows, Golden Apples, Alchemy Reagents
            if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                    || stack.is(Items.TIPPED_ARROW) || stack.is(Items.GOLDEN_APPLE)
                    || stack.is(Items.FERMENTED_SPIDER_EYE) || stack.is(Items.GLISTERING_MELON_SLICE)
                    || stack.is(Items.MAGMA_CREAM)) {
                ProfessionManager.addXp(player, ProfessionType.ALCHEMIST, 20);
                return;
            }

            // Farmer: Bread, Cake, Golden Carrot, Pumpkin Pie, Rabbit Stew
            if (stack.is(Items.BREAD) || stack.is(Items.CAKE) || stack.is(Items.GOLDEN_CARROT)
                    || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.RABBIT_STEW) || stack.is(Items.COOKIE)) {
                ProfessionManager.addXp(player, ProfessionType.FARMER, 10);
                return;
            }
        }

        // ── 3. BREWING STAND (Alchemist) ──
        if (slot.container instanceof BrewingStandBlockEntity) {
            if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
                ProfessionManager.addXp(player, ProfessionType.ALCHEMIST, 25);
            }
        }
    }
}
