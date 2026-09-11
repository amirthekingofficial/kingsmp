package net.kingsmp.shop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.kingsmp.KingSMPMod;

import java.util.ArrayList;
import java.util.List;

public class GamblePreviewScreenHandler extends ChestMenu {

    private final String gambleType;
    private final InteractionHand hand;
    private final MarketListing listing;
    private final net.minecraft.world.Container guiInventory;

    public GamblePreviewScreenHandler(int syncId, Inventory playerInventory, String gambleType, InteractionHand hand) {
        super(MenuType.GENERIC_9x5, syncId, playerInventory, new SimpleContainer(45), 5);
        this.gambleType = gambleType;
        this.hand = hand;
        this.listing = null;
        this.guiInventory = this.getContainer();

        setupPreviewItems();
    }

    public GamblePreviewScreenHandler(int syncId, Inventory playerInventory, String gambleType, MarketListing listing) {
        super(MenuType.GENERIC_9x5, syncId, playerInventory, new SimpleContainer(45), 5);
        this.gambleType = gambleType;
        this.hand = null;
        this.listing = listing;
        this.guiInventory = this.getContainer();

        setupPreviewItems();
    }

    private void setupPreviewItems() {
        guiInventory.clearContent();

        // 1. Fill bottom row with border panes, except center slot 40
        ItemStack border = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        border.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = 36; i < 45; i++) {
            if (i != 40) {
                guiInventory.setItem(i, border.copy());
            }
        }

        // 2. Add Buy/Spin Crate button in slot 40
        ItemStack controlButton = new ItemStack(listing != null ? Items.EMERALD : Items.CHEST);
        if (listing != null) {
            controlButton.set(DataComponents.CUSTOM_NAME, 
                Component.literal("▶ BUY CRATE ◀").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            List<Component> buyLore = new ArrayList<>();
            buyLore.add(Component.literal("Cost: " + listing.getSilverPrice() + " 🪙 Silver").withStyle(ChatFormatting.YELLOW));
            buyLore.add(Component.literal("Click to purchase this crate!").withStyle(ChatFormatting.GREEN));
            controlButton.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(buyLore));
        } else {
            controlButton.set(DataComponents.CUSTOM_NAME, 
                Component.literal("▶ SPIN CRATE ◀").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            List<Component> spinLore = new ArrayList<>();
            spinLore.add(Component.literal("Click here to start rolling!").withStyle(ChatFormatting.YELLOW));
            spinLore.add(Component.literal("This will consume 1 Crate.").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            controlButton.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(spinLore));
        }
        guiInventory.setItem(40, controlButton);

        // 3. Add rewards based on type
        List<ItemStack> rewards = getRewardsForPreview(gambleType);
        int slot = 0;
        for (ItemStack reward : rewards) {
            if (slot >= 36) break; // Don't overflow preview space
            guiInventory.setItem(slot, reward);
            slot++;
        }

        // 4. Fill empty slots in the preview area with black stained glass panes
        ItemStack emptyFiller = new ItemStack(Items.STAINED_GLASS_PANE.black());
        emptyFiller.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = slot; i < 36; i++) {
            guiInventory.setItem(i, emptyFiller.copy());
        }
    }

    private static ItemStack createPreviewItem(net.minecraft.world.item.Item item, int count, String rarity, double chance) {
        ItemStack stack = new ItemStack(item, count);
        List<Component> lore = new ArrayList<>();
        ChatFormatting rarityColor = switch (rarity.toLowerCase()) {
            case "legendary" -> ChatFormatting.GOLD;
            case "epic" -> ChatFormatting.LIGHT_PURPLE;
            case "rare" -> ChatFormatting.AQUA;
            case "common" -> ChatFormatting.GRAY;
            default -> ChatFormatting.WHITE;
        };
        lore.add(Component.literal("Rarity: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rarity).withStyle(rarityColor, ChatFormatting.BOLD)));
        
        String chanceStr = String.format(java.util.Locale.ROOT, "%.2f%%", chance);
        if (chanceStr.endsWith(".00%")) {
            chanceStr = String.format(java.util.Locale.ROOT, "%.0f%%", chance);
        } else if (chanceStr.endsWith("0%")) {
            chanceStr = String.format(java.util.Locale.ROOT, "%.1f%%", chance);
        }
        lore.add(Component.literal("Drop Chance: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(chanceStr).withStyle(ChatFormatting.GREEN)));
        
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private List<ItemStack> getRewardsForPreview(String type) {
        List<ItemStack> list = new ArrayList<>();
        switch (type.toLowerCase()) {
            case "overworld":
                list.add(createPreviewItem(Items.ENCHANTED_GOLDEN_APPLE, 3, "Legendary", 0.25));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 8, "Legendary", 0.25));
                list.add(createPreviewItem(Items.GOLDEN_APPLE, 8, "Epic", 9.5 / 3.0));
                list.add(createPreviewItem(Items.OMINOUS_BOTTLE, 4, "Epic", 9.5 / 3.0));
                list.add(createPreviewItem(Items.DIAMOND, 24, "Epic", 9.5 / 3.0));
                list.add(createPreviewItem(Items.DIAMOND, 12, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 4, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 12.0));
                list.add(createPreviewItem(Items.DIAMOND, 6, "Common", 65.0 / 2.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 2, "Common", 65.0 / 2.0));
                break;
            case "nether":
                list.add(createPreviewItem(Items.NETHERITE_INGOT, 4, "Legendary", 0.25));
                list.add(createPreviewItem(Items.WITHER_SKELETON_SKULL, 3, "Legendary", 0.25));
                list.add(createPreviewItem(Items.NETHERITE_INGOT, 1, "Epic", 9.5 / 5.0));
                list.add(createPreviewItem(Items.NETHERITE_SCRAP, 8, "Epic", 9.5 / 5.0));
                list.add(createPreviewItem(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Epic", 9.5 / 5.0));
                list.add(createPreviewItem(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Epic", 9.5 / 5.0));
                list.add(createPreviewItem(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Epic", 9.5 / 5.0));
                list.add(createPreviewItem(Items.NETHERITE_SCRAP, 5, "Rare", 25.0 / 2.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 6, "Rare", 25.0 / 2.0));
                list.add(createPreviewItem(Items.NETHERITE_SCRAP, 2, "Common", 65.0 / 2.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 3, "Common", 65.0 / 2.0));
                break;
            case "end":
                list.add(createPreviewItem(Items.ELYTRA, 1, "Legendary", 0.5));
                list.add(createPreviewItem(Items.NETHER_STAR, 1, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.SHULKER_BOX, 1, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.ECHO_SHARD, 4, "Rare", 25.0 / 2.0));
                list.add(createPreviewItem(Items.SHULKER_SHELL, 10, "Rare", 25.0 / 2.0));
                list.add(createPreviewItem(Items.SHULKER_SHELL, 4, "Common", 65.0 / 2.0));
                list.add(createPreviewItem(Items.ECHO_SHARD, 1, "Common", 65.0 / 2.0));
                break;
            case "ore":
                list.add(createPreviewItem(Items.NETHERITE_INGOT, 3, "Legendary", 0.25));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 8, "Legendary", 0.25));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 4, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.ANCIENT_DEBRIS, 4, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.DIAMOND, 16, "Rare", 25.0 / 3.0));
                list.add(createPreviewItem(Items.EMERALD_BLOCK, 6, "Rare", 25.0 / 3.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 6, "Rare", 25.0 / 3.0));
                list.add(createPreviewItem(Items.DIAMOND, 8, "Common", 65.0 / 3.0));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 3, "Common", 65.0 / 3.0));
                list.add(createPreviewItem(Items.EMERALD_BLOCK, 3, "Common", 65.0 / 3.0));
                break;
            case "trial":
                list.add(createPreviewItem(Items.MACE, 1, "Legendary", 0.5));
                list.add(createPreviewItem(Items.HEAVY_CORE, 1, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.OMINOUS_TRIAL_KEY, 6, "Epic", 9.5 / 2.0));
                list.add(createPreviewItem(Items.BREEZE_ROD, 12, "Rare", 25.0 / 5.0));
                list.add(createPreviewItem(Items.TRIAL_KEY, 10, "Rare", 25.0 / 5.0));
                list.add(createPreviewItem(Items.OMINOUS_TRIAL_KEY, 3, "Rare", 25.0 / 5.0));
                list.add(createPreviewItem(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 5.0));
                list.add(createPreviewItem(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1, "Rare", 25.0 / 5.0));
                list.add(createPreviewItem(Items.BREEZE_ROD, 6, "Common", 65.0 / 2.0));
                list.add(createPreviewItem(Items.TRIAL_KEY, 4, "Common", 65.0 / 2.0));
                break;
        }
        return list;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex == 40) {
            if (listing != null) {
                // BUY MODE
                if (listing.getSilverPrice() > 0) {
                    if (KingSMPMod.dataManager.removeSilver(player.getUUID(), listing.getSilverPrice())) {
                        ItemStack boughtItem = listing.getItemToSell().copy();
                        if (!player.getInventory().add(boughtItem)) {
                            player.drop(boughtItem, false);
                        }
                        player.sendOverlayMessage(Component.literal("Bought for " + listing.getSilverPrice() + " 🪙 Silver!").withStyle(ChatFormatting.GREEN));
                        KingSMPMod.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.closeContainer();
                        }
                    } else {
                        player.sendOverlayMessage(Component.literal("Not enough Silver! You need " + listing.getSilverPrice() + " 🪙.").withStyle(ChatFormatting.RED));
                        KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                    }
                }
            } else if (hand != null) {
                // SPIN MODE
                ItemStack heldItem = player.getItemInHand(hand);
                if (KingSMPMod.isGambleSpawner(heldItem)) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                (syncId, playerInv, p) -> new GambleScreenHandler(syncId, playerInv, gambleType, hand),
                                Component.literal("Loot Gamble - " + gambleType.toUpperCase()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        ));
                        KingSMPMod.playSoundToPlayer(player, SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
                    }
                } else {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.closeContainer();
                    }
                }
            }
            return;
        }

        // Prevent taking items out of the preview container
        this.broadcastFullState();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
