package net.kingsmp.shop;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.kingsmp.KingSMPMod;

import java.util.*;

public class GambleScreenHandler extends ChestMenu {

    private final String gambleType;
    private final InteractionHand hand;
    private final Player player;
    private final net.minecraft.world.Container guiInventory;

    // Track state of the spin
    private int currentDelay = 2; // Ticks between shifts
    private int ticksUntilNextShift = 2;
    private int totalShifts = 0;
    private final int maxShifts = 25; // Spin length
    private boolean toggleBorder = false;

    // Registry of active rolling screens
    private static final List<GambleScreenHandler> activeGambles = new ArrayList<>();

    public GambleScreenHandler(int syncId, Inventory playerInventory, String gambleType, InteractionHand hand) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, new SimpleContainer(27), 3);
        this.gambleType = gambleType;
        this.hand = hand;
        this.player = playerInventory.player;
        this.guiInventory = this.getContainer();

        KingSMPMod.LOGGER.info("GambleScreenHandler initialized. Type: '" + gambleType + "'");

        setupInitialSlots();
        synchronized (activeGambles) {
            activeGambles.add(this);
        }
    }

    private void setupInitialSlots() {
        // Borders (alternating colors)
        updateBorders();

        // Row 2 (Slots 9-17) - Fill with random items from loot pool
        for (int i = 9; i <= 17; i++) {
            ItemStack stack = getRandomLootItem(gambleType);
            KingSMPMod.LOGGER.info("Initial slot " + i + ": " + stack);
            guiInventory.setItem(i, stack);
        }
    }

    private void updateBorders() {
        ItemStack pane1 = new ItemStack(toggleBorder ? Items.STAINED_GLASS_PANE.red() : Items.STAINED_GLASS_PANE.lime());
        pane1.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("★ SPINNING ★").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        ItemStack pane2 = new ItemStack(toggleBorder ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.red());
        pane2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("★ SPINNING ★").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

        // Row 1 (0-8)
        for (int i = 0; i <= 8; i++) {
            guiInventory.setItem(i, (i % 2 == 0) ? pane1.copy() : pane2.copy());
        }
        // Row 3 (18-26)
        for (int i = 18; i <= 26; i++) {
            guiInventory.setItem(i, (i % 2 == 0) ? pane2.copy() : pane1.copy());
        }

        // Accentuate the pointer/middle selectors
        ItemStack pointer = new ItemStack(Items.STAINED_GLASS_PANE.yellow());
        pointer.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("▼ TARGET ▼").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        guiInventory.setItem(4, pointer); // Top center
        guiInventory.setItem(22, pointer.copy()); // Bottom center

        // Skip Roll button in Slot 8
        ItemStack skipBtn = new ItemStack(Items.FEATHER);
        skipBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Skip Roll").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> skipLore = new ArrayList<>();
        skipLore.add(Component.literal("Click to instantly receive reward").withStyle(ChatFormatting.GRAY));
        skipBtn.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(skipLore));
        guiInventory.setItem(8, skipBtn);
    }

    private void shiftItemsLeft() {
        // Shift slot 10->9, 11->10...
        for (int i = 9; i < 17; i++) {
            guiInventory.setItem(i, guiInventory.getItem(i + 1));
        }
        // Insert a new random item on the right (slot 17)
        ItemStack stack = getRandomLootItem(gambleType);
        guiInventory.setItem(17, stack);
    }

    public void tick() {
        if (player.containerMenu != this) {
            // Player closed the GUI early, remove from active tickers
            discard();
            return;
        }

        ticksUntilNextShift--;

        if (ticksUntilNextShift <= 0) {
            totalShifts++;
            toggleBorder = !toggleBorder;
            updateBorders();

            if (totalShifts >= maxShifts) {
                finishSpin();
                return;
            }

            shiftItemsLeft();

            // Play sound for tick effect
            KingSMPMod.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);

            // Adjust slowdown speed dynamically
            if (totalShifts < 10) {
                currentDelay = 2;
            } else if (totalShifts < 16) {
                currentDelay = 3;
            } else if (totalShifts < 21) {
                currentDelay = 5;
            } else if (totalShifts < 24) {
                currentDelay = 8;
            } else {
                currentDelay = 12;
            }

            ticksUntilNextShift = currentDelay;
        }
    }

    private void finishSpin() {
        KingSMPMod.LOGGER.info("Spin finished! gambleType='" + gambleType + "' totalShifts=" + totalShifts);

        // Security check: Verify the player is still holding the matching crate ticket to prevent ticket-swap exploit
        ItemStack heldItem = player.getItemInHand(hand);
        if (!KingSMPMod.isGambleSpawner(heldItem) || !KingSMPMod.getGambleType(heldItem).equalsIgnoreCase(this.gambleType)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("❌ Spin canceled: You swapped or removed your " + this.gambleType.toUpperCase() + " Gamble Crate!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            discard();
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return;
        }

        // Consume 1 matching crate ticket
        heldItem.shrink(1);

        // Award middle item (slot 13) and unpack bundles safely
        ItemStack rewardDisplay = guiInventory.getItem(13).copy();
        List<ItemStack> deliveredItems = unpackReward(rewardDisplay, gambleType);

        for (ItemStack item : deliveredItems) {
            deliverStackSafely(player, item);
        }

        KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        KingSMPMod.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("🎉 You won: ")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(rewardDisplay.getHoverName()));
            for (ItemStack item : deliveredItems) {
                serverPlayer.sendSystemMessage(Component.literal("  + ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(item.getHoverName())
                        .append(" x" + item.getCount()));
            }
        }

        discard();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.closeContainer();
        }
    }

    private static void deliverStackSafely(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        int remaining = stack.getCount();
        int maxStack = stack.getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack chunk = stack.copyWithCount(give);
            if (!player.getInventory().add(chunk)) {
                player.drop(chunk, false);
            }
            remaining -= give;
        }
    }

    private void discard() {
        synchronized (activeGambles) {
            activeGambles.remove(this);
        }
    }

    public static void tickActiveGambles(MinecraftServer server) {
        synchronized (activeGambles) {
            for (int i = activeGambles.size() - 1; i >= 0; i--) {
                activeGambles.get(i).tick();
            }
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex == 8) {
            KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
            finishSpin();
            return;
        }
        // Prevent player from taking items out of the spinning container
        this.broadcastFullState();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static final Random RANDOM = new Random();

    private static final Item[] OVERWORLD_TRIMS = {
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE
    };

    private static final Item[] NETHER_TRIMS = {
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE
    };

    private static final Item[] TRIAL_TRIMS = {
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    };

    public static Item getRandomOverworldTrim() {
        return OVERWORLD_TRIMS[RANDOM.nextInt(OVERWORLD_TRIMS.length)];
    }

    public static Item getRandomNetherTrim() {
        return NETHER_TRIMS[RANDOM.nextInt(NETHER_TRIMS.length)];
    }

    public static Item getRandomTrialTrim() {
        return TRIAL_TRIMS[RANDOM.nextInt(TRIAL_TRIMS.length)];
    }

    public static Item getRandomShulkerBoxItem() {
        List<Item> boxes = new ArrayList<>();
        boxes.add(Items.SHULKER_BOX);
        boxes.addAll(Items.DYED_SHULKER_BOX.asList());
        return boxes.get(RANDOM.nextInt(boxes.size()));
    }

    public static ItemStack createRewardDisplay(Item icon, int displayCount, String title, ChatFormatting color, String bundleId, int trueCount, List<String> bundleLines) {
        ItemStack stack = new ItemStack(icon, Math.min(displayCount, 64));
        if (title != null && !title.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(color, ChatFormatting.BOLD));
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (bundleId != null && !bundleId.isEmpty()) {
            tag.putString("RewardBundleId", bundleId);
        }
        if (trueCount > 0) {
            tag.putInt("TrueCount", trueCount);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        List<Component> lore = new ArrayList<>();
        if (bundleLines != null && !bundleLines.isEmpty()) {
            lore.add(Component.literal("Includes:").withStyle(ChatFormatting.YELLOW));
            for (String line : bundleLines) {
                lore.add(Component.literal(" • " + line).withStyle(ChatFormatting.WHITE));
            }
        } else if (trueCount > 64) {
            lore.add(Component.literal("Total Amount: " + trueCount + "x").withStyle(ChatFormatting.GOLD));
        }
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        }
        return stack;
    }

    public static List<ItemStack> unpackReward(ItemStack display, String crateType) {
        List<ItemStack> items = new ArrayList<>();
        CustomData customData = display.get(DataComponents.CUSTOM_DATA);
        String bundleId = "";
        if (customData != null && customData.copyTag().contains("RewardBundleId")) {
            bundleId = customData.copyTag().getString("RewardBundleId").orElse("");
        }

        switch (bundleId) {
            case "overworld_legendary":
                items.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 5));
                items.add(new ItemStack(Items.DIAMOND_BLOCK, 100));
                items.add(new ItemStack(Items.GOLD_BLOCK, 50));
                items.add(new ItemStack(Items.TOTEM_OF_UNDYING, 5));
                return items;
            case "overworld_epic":
                items.add(new ItemStack(Items.DIAMOND_BLOCK, 75));
                items.add(new ItemStack(Items.GOLDEN_APPLE, 10));
                items.add(new ItemStack(Items.OMINOUS_BOTTLE, 8));
                return items;
            case "overworld_rare_diamond":
                items.add(new ItemStack(Items.DIAMOND_BLOCK, 30));
                items.add(new ItemStack(getRandomOverworldTrim(), 1));
                return items;
            case "overworld_rare_emerald":
                items.add(new ItemStack(Items.EMERALD_BLOCK, 60));
                items.add(new ItemStack(getRandomOverworldTrim(), 1));
                return items;

            case "nether_legendary":
                items.add(new ItemStack(Items.NETHERITE_INGOT, 32));
                items.add(new ItemStack(Items.WITHER_SKELETON_SKULL, 16));
                items.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3));
                return items;
            case "nether_epic":
                items.add(new ItemStack(Items.NETHERITE_INGOT, 20));
                items.add(new ItemStack(getRandomNetherTrim(), 1));
                return items;
            case "nether_rare_gold":
                items.add(new ItemStack(Items.GOLD_BLOCK, 80));
                return items;

            case "end_legendary":
                items.add(new ItemStack(Items.ELYTRA, 1));
                items.add(new ItemStack(Items.NETHER_STAR, 25));
                items.add(new ItemStack(getRandomShulkerBoxItem(), 2));
                return items;
            case "end_epic":
                items.add(new ItemStack(Items.NETHER_STAR, 15));
                return items;
            case "end_rare_shell":
                items.add(new ItemStack(Items.SHULKER_SHELL, 135));
                return items;
            case "end_rare_echo":
                items.add(new ItemStack(Items.ECHO_SHARD, 180));
                return items;
            case "end_common_echo":
                items.add(new ItemStack(Items.ECHO_SHARD, 80));
                return items;

            case "ore_legendary":
                items.add(new ItemStack(Items.NETHERITE_INGOT, 40));
                items.add(new ItemStack(Items.DIAMOND_BLOCK, 80));
                items.add(new ItemStack(Items.ANCIENT_DEBRIS, 20));
                return items;
            case "ore_epic":
                items.add(new ItemStack(Items.DIAMOND_BLOCK, 10));
                items.add(new ItemStack(Items.ANCIENT_DEBRIS, 40));
                items.add(new ItemStack(Items.NETHERITE_INGOT, 18));
                return items;
            case "ore_rare_emerald":
                items.add(new ItemStack(Items.EMERALD_BLOCK, 120));
                return items;

            case "trial_legendary":
                items.add(new ItemStack(Items.MACE, 1));
                items.add(new ItemStack(Items.HEAVY_CORE, 8));
                items.add(new ItemStack(Items.NETHERITE_INGOT, 4));
                return items;
            case "trial_epic":
                items.add(new ItemStack(Items.HEAVY_CORE, 5));
                return items;
            case "trial_rare_ominous":
                items.add(new ItemStack(Items.OMINOUS_TRIAL_KEY, 60));
                items.add(new ItemStack(getRandomTrialTrim(), 1));
                return items;
            case "trial_rare_standard":
                items.add(new ItemStack(Items.TRIAL_KEY, 180));
                items.add(new ItemStack(getRandomTrialTrim(), 1));
                return items;
            case "trial_common_key":
                items.add(new ItemStack(Items.TRIAL_KEY, 80));
                return items;
            case "trial_common_breeze":
                items.add(new ItemStack(Items.BREEZE_ROD, 133));
                return items;

            default:
                if (customData != null && customData.copyTag().contains("TrueCount")) {
                    int trueCount = customData.copyTag().getInt("TrueCount").orElse(display.getCount());
                    items.add(display.copyWithCount(trueCount));
                } else {
                    items.add(display.copy());
                }
                return items;
        }
    }

    public static ItemStack getRandomLootItem(String type) {
        int roll = RANDOM.nextInt(1000);

        switch (type.toLowerCase()) {
            case "overworld": {
                if (roll < 5) { // 0.5% Legendary
                    return createRewardDisplay(
                            Items.ENCHANTED_GOLDEN_APPLE, 1,
                            "★ The Golden Hoard ★", ChatFormatting.GOLD,
                            "overworld_legendary", 1,
                            List.of("5x Enchanted Golden Apple", "100x Diamond Block", "50x Gold Block", "5x Totem of Undying")
                    );
                } else if (roll < 100) { // 9.5% Epic
                    return createRewardDisplay(
                            Items.GOLDEN_APPLE, 10,
                            "★ Overworld Vault ★", ChatFormatting.LIGHT_PURPLE,
                            "overworld_epic", 1,
                            List.of("75x Diamond Block", "10x Golden Apple", "8x Ominous Bottle")
                    );
                } else if (roll < 350) { // 25.0% Rare
                    if (RANDOM.nextBoolean()) {
                        return createRewardDisplay(
                                Items.DIAMOND_BLOCK, 30,
                                "★ 30x Diamond Block & Trim ★", ChatFormatting.AQUA,
                                "overworld_rare_diamond", 30,
                                List.of("30x Diamond Block", "1x Overworld Armor Trim")
                        );
                    } else {
                        return createRewardDisplay(
                                Items.EMERALD_BLOCK, 60,
                                "★ 60x Emerald Block & Trim ★", ChatFormatting.AQUA,
                                "overworld_rare_emerald", 60,
                                List.of("60x Emerald Block", "1x Overworld Armor Trim")
                        );
                    }
                } else { // 65.0% Common
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.DIAMOND_BLOCK, 13);
                    } else {
                        return new ItemStack(Items.GOLD_BLOCK, 27);
                    }
                }
            }

            case "nether": {
                if (roll < 5) { // 0.5% Legendary
                    return createRewardDisplay(
                            Items.NETHERITE_INGOT, 32,
                            "★ Nether Overlord ★", ChatFormatting.GOLD,
                            "nether_legendary", 32,
                            List.of("32x Netherite Ingot", "16x Wither Skeleton Skull", "3x Netherite Upgrade Template")
                    );
                } else if (roll < 100) { // 9.5% Epic
                    return createRewardDisplay(
                            Items.NETHERITE_INGOT, 20,
                            "★ Netherite Cache & Trim ★", ChatFormatting.LIGHT_PURPLE,
                            "nether_epic", 20,
                            List.of("20x Netherite Ingot", "1x Nether Armor Trim")
                    );
                } else if (roll < 350) { // 25.0% Rare
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.NETHERITE_SCRAP, 29);
                    } else {
                        return createRewardDisplay(
                                Items.GOLD_BLOCK, 64,
                                "★ 80x Gold Block ★", ChatFormatting.AQUA,
                                "nether_rare_gold", 80,
                                List.of("80x Gold Block")
                        );
                    }
                } else { // 65.0% Common
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.NETHERITE_SCRAP, 13);
                    } else {
                        return new ItemStack(Items.GOLD_BLOCK, 36);
                    }
                }
            }

            case "end": {
                if (roll < 5) { // 0.5% Legendary
                    return createRewardDisplay(
                            Items.ELYTRA, 1,
                            "★ Cosmic Monarch ★", ChatFormatting.GOLD,
                            "end_legendary", 1,
                            List.of("1x Elytra", "25x Nether Star", "2x Shulker Box")
                    );
                } else if (roll < 100) { // 9.5% Epic
                    return createRewardDisplay(
                            Items.NETHER_STAR, 15,
                            "★ Star Cluster ★", ChatFormatting.LIGHT_PURPLE,
                            "end_epic", 15,
                            List.of("15x Nether Star")
                    );
                } else if (roll < 350) { // 25.0% Rare
                    if (RANDOM.nextBoolean()) {
                        return createRewardDisplay(
                                Items.SHULKER_SHELL, 64,
                                "★ 135x Shulker Shell ★", ChatFormatting.AQUA,
                                "end_rare_shell", 135,
                                List.of("135x Shulker Shell")
                        );
                    } else {
                        return createRewardDisplay(
                                Items.ECHO_SHARD, 64,
                                "★ 180x Echo Shard ★", ChatFormatting.AQUA,
                                "end_rare_echo", 180,
                                List.of("180x Echo Shard")
                        );
                    }
                } else { // 65.0% Common
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.SHULKER_SHELL, 60);
                    } else {
                        return createRewardDisplay(
                                Items.ECHO_SHARD, 64,
                                "★ 80x Echo Shard ★", ChatFormatting.GRAY,
                                "end_common_echo", 80,
                                List.of("80x Echo Shard")
                        );
                    }
                }
            }

            case "ore": {
                if (roll < 5) { // 0.5% Legendary
                    return createRewardDisplay(
                            Items.NETHERITE_INGOT, 40,
                            "★ Deepslate Sovereign ★", ChatFormatting.GOLD,
                            "ore_legendary", 40,
                            List.of("40x Netherite Ingot", "80x Diamond Block", "20x Ancient Debris")
                    );
                } else if (roll < 100) { // 9.5% Epic
                    return createRewardDisplay(
                            Items.ANCIENT_DEBRIS, 40,
                            "★ Ancient Treasury ★", ChatFormatting.LIGHT_PURPLE,
                            "ore_epic", 40,
                            List.of("10x Diamond Block", "40x Ancient Debris", "18x Netherite Ingot")
                    );
                } else if (roll < 350) { // 25.0% Rare
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.DIAMOND_BLOCK, 60);
                    } else {
                        return createRewardDisplay(
                                Items.EMERALD_BLOCK, 64,
                                "★ 120x Emerald Block ★", ChatFormatting.AQUA,
                                "ore_rare_emerald", 120,
                                List.of("120x Emerald Block")
                        );
                    }
                } else { // 65.0% Common
                    if (RANDOM.nextBoolean()) {
                        return new ItemStack(Items.DIAMOND_BLOCK, 27);
                    } else {
                        return new ItemStack(Items.EMERALD_BLOCK, 53);
                    }
                }
            }

            case "trial": {
                if (roll < 5) { // 0.5% Legendary
                    return createRewardDisplay(
                            Items.MACE, 1,
                            "★ Trial Champion ★", ChatFormatting.GOLD,
                            "trial_legendary", 1,
                            List.of("1x Mace", "8x Heavy Core", "4x Netherite Ingot")
                    );
                } else if (roll < 100) { // 9.5% Epic
                    return createRewardDisplay(
                            Items.HEAVY_CORE, 5,
                            "★ Heavy Core Cache ★", ChatFormatting.LIGHT_PURPLE,
                            "trial_epic", 5,
                            List.of("5x Heavy Core")
                    );
                } else if (roll < 350) { // 25.0% Rare
                    if (RANDOM.nextBoolean()) {
                        return createRewardDisplay(
                                Items.OMINOUS_TRIAL_KEY, 60,
                                "★ 60x Ominous Key & Trim ★", ChatFormatting.AQUA,
                                "trial_rare_ominous", 60,
                                List.of("60x Ominous Trial Key", "1x Trial Armor Trim")
                        );
                    } else {
                        return createRewardDisplay(
                                Items.TRIAL_KEY, 64,
                                "★ 180x Trial Key & Trim ★", ChatFormatting.AQUA,
                                "trial_rare_standard", 180,
                                List.of("180x Trial Key", "1x Trial Armor Trim")
                        );
                    }
                } else { // 65.0% Common
                    if (RANDOM.nextBoolean()) {
                        return createRewardDisplay(
                                Items.TRIAL_KEY, 64,
                                "★ 80x Trial Key ★", ChatFormatting.GRAY,
                                "trial_common_key", 80,
                                List.of("80x Trial Key")
                        );
                    } else {
                        return createRewardDisplay(
                                Items.BREEZE_ROD, 64,
                                "★ 133x Breeze Rod ★", ChatFormatting.GRAY,
                                "trial_common_breeze", 133,
                                List.of("133x Breeze Rod")
                        );
                    }
                }
            }
        }

        return new ItemStack(Items.COAL, 1);
    }
}
