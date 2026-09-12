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
        ItemStack stack = new ItemStack(item, Math.min(count, 64));
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
        
        if (count > 64) {
            lore.add(Component.literal("Total Amount: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(count + "x").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        }

        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private static ItemStack createPreviewBundle(net.minecraft.world.item.Item displayItem, String title, List<String> contents, String rarity, double chance) {
        ItemStack stack = new ItemStack(displayItem, 1);
        ChatFormatting rarityColor = switch (rarity.toLowerCase()) {
            case "legendary" -> ChatFormatting.GOLD;
            case "epic" -> ChatFormatting.LIGHT_PURPLE;
            case "rare" -> ChatFormatting.AQUA;
            case "common" -> ChatFormatting.GRAY;
            default -> ChatFormatting.WHITE;
        };
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("★ " + title + " ★").withStyle(rarityColor, ChatFormatting.BOLD));

        List<Component> lore = new ArrayList<>();
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

        lore.add(Component.literal("Bundle Contents:").withStyle(ChatFormatting.YELLOW));
        for (String line : contents) {
            lore.add(Component.literal(" • " + line).withStyle(ChatFormatting.WHITE));
        }

        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private List<ItemStack> getRewardsForPreview(String type) {
        List<ItemStack> list = new ArrayList<>();
        switch (type.toLowerCase()) {
            case "overworld":
                list.add(createPreviewBundle(Items.ENCHANTED_GOLDEN_APPLE, "The Golden Hoard", List.of("5x Enchanted Golden Apple", "100x Diamond Block", "50x Gold Block", "5x Totem of Undying"), "Legendary", 0.5));
                list.add(createPreviewBundle(Items.GOLDEN_APPLE, "Overworld Vault", List.of("75x Diamond Block", "10x Golden Apple", "8x Ominous Bottle"), "Epic", 9.5));
                list.add(createPreviewBundle(Items.DIAMOND_BLOCK, "30x Diamond Block & Trim", List.of("30x Diamond Block", "1x Overworld Armor Trim"), "Rare", 12.5));
                list.add(createPreviewBundle(Items.EMERALD_BLOCK, "60x Emerald Block & Trim", List.of("60x Emerald Block", "1x Overworld Armor Trim"), "Rare", 12.5));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 13, "Common", 32.5));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 27, "Common", 32.5));
                break;
            case "nether":
                list.add(createPreviewBundle(Items.NETHERITE_INGOT, "Nether Overlord", List.of("32x Netherite Ingot", "16x Wither Skeleton Skull", "3x Netherite Upgrade Template"), "Legendary", 0.5));
                list.add(createPreviewBundle(Items.NETHERITE_INGOT, "Netherite Cache & Trim", List.of("20x Netherite Ingot", "1x Nether Armor Trim"), "Epic", 9.5));
                list.add(createPreviewItem(Items.NETHERITE_SCRAP, 29, "Rare", 12.5));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 80, "Rare", 12.5));
                list.add(createPreviewItem(Items.NETHERITE_SCRAP, 13, "Common", 32.5));
                list.add(createPreviewItem(Items.GOLD_BLOCK, 36, "Common", 32.5));
                break;
            case "end":
                list.add(createPreviewBundle(Items.ELYTRA, "Cosmic Monarch", List.of("1x Elytra", "25x Nether Star", "2x Shulker Box"), "Legendary", 0.5));
                list.add(createPreviewItem(Items.NETHER_STAR, 15, "Epic", 9.5));
                list.add(createPreviewItem(Items.SHULKER_SHELL, 135, "Rare", 12.5));
                list.add(createPreviewItem(Items.ECHO_SHARD, 180, "Rare", 12.5));
                list.add(createPreviewItem(Items.SHULKER_SHELL, 60, "Common", 32.5));
                list.add(createPreviewItem(Items.ECHO_SHARD, 80, "Common", 32.5));
                break;
            case "ore":
                list.add(createPreviewBundle(Items.NETHERITE_INGOT, "Deepslate Sovereign", List.of("40x Netherite Ingot", "80x Diamond Block", "20x Ancient Debris"), "Legendary", 0.5));
                list.add(createPreviewBundle(Items.ANCIENT_DEBRIS, "Ancient Treasury", List.of("10x Diamond Block", "40x Ancient Debris", "18x Netherite Ingot"), "Epic", 9.5));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 60, "Rare", 12.5));
                list.add(createPreviewItem(Items.EMERALD_BLOCK, 120, "Rare", 12.5));
                list.add(createPreviewItem(Items.DIAMOND_BLOCK, 27, "Common", 32.5));
                list.add(createPreviewItem(Items.EMERALD_BLOCK, 53, "Common", 32.5));
                break;
            case "trial":
                list.add(createPreviewBundle(Items.MACE, "Trial Champion", List.of("1x Mace", "8x Heavy Core", "4x Netherite Ingot"), "Legendary", 0.5));
                list.add(createPreviewItem(Items.HEAVY_CORE, 5, "Epic", 9.5));
                list.add(createPreviewBundle(Items.OMINOUS_TRIAL_KEY, "60x Ominous Key & Trim", List.of("60x Ominous Trial Key", "1x Trial Armor Trim"), "Rare", 12.5));
                list.add(createPreviewBundle(Items.TRIAL_KEY, "180x Trial Key & Trim", List.of("180x Trial Key", "1x Trial Armor Trim"), "Rare", 12.5));
                list.add(createPreviewItem(Items.TRIAL_KEY, 80, "Common", 32.5));
                list.add(createPreviewItem(Items.BREEZE_ROD, 133, "Common", 32.5));
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
