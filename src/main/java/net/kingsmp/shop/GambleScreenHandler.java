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
        for (int i = 9; i <= 17; i++) {
            KingSMPMod.LOGGER.info("Final Slot " + i + " contains: " + guiInventory.getItem(i));
        }
        
        // Award middle item (slot 13)
        ItemStack reward = guiInventory.getItem(13).copy();
        ItemStack rewardCopy = reward.copy(); // Create a copy before player.getInventory().add(reward) consumes/shrinks it
        
        // Remove the spawner from their hand
        ItemStack heldItem = player.getItemInHand(hand);
        if (KingSMPMod.isGambleSpawner(heldItem)) {
            heldItem.shrink(1);
            
            // Deliver reward
            if (!player.getInventory().add(reward)) {
                player.drop(rewardCopy, false);
            }

            KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            KingSMPMod.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("🎉 You won: ")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(rewardCopy.getHoverName())
                        .append(" x" + rewardCopy.getCount()));
            }
        }

        discard();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.closeContainer();
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

    private static int getUncappedAmount(int min, double chanceToIncrease) {
        int amount = min;
        while (RANDOM.nextDouble() < chanceToIncrease) {
            amount++;
        }
        return amount;
    }
 
    private static net.minecraft.world.item.Item getRandomShulkerBoxItem() {
        List<net.minecraft.world.item.Item> boxes = new ArrayList<>();
        boxes.add(Items.SHULKER_BOX);
        boxes.addAll(Items.DYED_SHULKER_BOX.asList());
        return boxes.get(RANDOM.nextInt(boxes.size()));
    }

    private static ItemStack getRandomLootItem(String type) {
        List<ItemStack> pool = new ArrayList<>();
 
        switch (type.toLowerCase()) {
            case "overworld": {
                int roll = RANDOM.nextInt(1000);
                if (roll < 5) { // 0.5% Legendary
                    pool.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3));
                    pool.add(new ItemStack(Items.DIAMOND_BLOCK, 8));
                } else if (roll < 100) { // 9.5% Epic
                    pool.add(new ItemStack(Items.GOLDEN_APPLE, getUncappedAmount(8, 0.3)));
                    pool.add(new ItemStack(Items.OMINOUS_BOTTLE, getUncappedAmount(4, 0.3)));
                    pool.add(new ItemStack(Items.DIAMOND, getUncappedAmount(24, 0.3)));
                } else if (roll < 350) { // 25.0% Rare
                    pool.add(new ItemStack(Items.DIAMOND, getUncappedAmount(12, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(4, 0.3)));
                    pool.add(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                } else { // 65.0% Common
                    pool.add(new ItemStack(Items.DIAMOND, getUncappedAmount(6, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(2, 0.3)));
                }
                break;
            }
 
            case "nether": {
                int roll = RANDOM.nextInt(1000);
                if (roll < 5) { // 0.5% Legendary
                    pool.add(new ItemStack(Items.NETHERITE_INGOT, 4));
                    pool.add(new ItemStack(Items.WITHER_SKELETON_SKULL, 3));
                } else if (roll < 100) { // 9.5% Epic
                    pool.add(new ItemStack(Items.NETHERITE_INGOT, 1));
                    pool.add(new ItemStack(Items.NETHERITE_SCRAP, getUncappedAmount(8, 0.3)));
                    pool.add(new ItemStack(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                } else if (roll < 350) { // 25.0% Rare
                    pool.add(new ItemStack(Items.NETHERITE_SCRAP, getUncappedAmount(5, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(6, 0.3)));
                } else { // 65.0% Common
                    pool.add(new ItemStack(Items.NETHERITE_SCRAP, getUncappedAmount(2, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(3, 0.3)));
                }
                break;
            }
 
            case "end": {
                int roll = RANDOM.nextInt(1000);
                if (roll < 5) { // 0.5% Legendary
                    pool.add(new ItemStack(Items.ELYTRA, 1));
                } else if (roll < 100) { // 9.5% Epic
                    pool.add(new ItemStack(Items.NETHER_STAR, 1));
                    pool.add(new ItemStack(getRandomShulkerBoxItem(), 1));
                } else if (roll < 350) { // 25.0% Rare
                    pool.add(new ItemStack(Items.ECHO_SHARD, getUncappedAmount(4, 0.3)));
                    pool.add(new ItemStack(Items.SHULKER_SHELL, getUncappedAmount(10, 0.3)));
                } else { // 65.0% Common
                    pool.add(new ItemStack(Items.SHULKER_SHELL, getUncappedAmount(4, 0.3)));
                    pool.add(new ItemStack(Items.ECHO_SHARD, 1));
                }
                break;
            }
 
            case "ore": {
                int roll = RANDOM.nextInt(1000);
                if (roll < 5) { // 0.5% Legendary
                    pool.add(new ItemStack(Items.NETHERITE_INGOT, 3));
                    pool.add(new ItemStack(Items.DIAMOND_BLOCK, 8));
                } else if (roll < 100) { // 9.5% Epic
                    pool.add(new ItemStack(Items.DIAMOND_BLOCK, getUncappedAmount(4, 0.3)));
                    pool.add(new ItemStack(Items.ANCIENT_DEBRIS, getUncappedAmount(4, 0.3)));
                } else if (roll < 350) { // 25.0% Rare
                    pool.add(new ItemStack(Items.DIAMOND, getUncappedAmount(16, 0.3)));
                    pool.add(new ItemStack(Items.EMERALD_BLOCK, getUncappedAmount(6, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(6, 0.3)));
                } else { // 65.0% Common
                    pool.add(new ItemStack(Items.DIAMOND, getUncappedAmount(8, 0.3)));
                    pool.add(new ItemStack(Items.GOLD_BLOCK, getUncappedAmount(3, 0.3)));
                    pool.add(new ItemStack(Items.EMERALD_BLOCK, getUncappedAmount(3, 0.3)));
                }
                break;
            }
 
            case "trial": {
                int trialRoll = RANDOM.nextInt(1000);
                if (trialRoll < 5) { // 0.5% Legendary
                    pool.add(new ItemStack(Items.MACE, 1));
                } else if (trialRoll < 100) { // 9.5% Epic
                    pool.add(new ItemStack(Items.HEAVY_CORE, 1));
                    pool.add(new ItemStack(Items.OMINOUS_TRIAL_KEY, getUncappedAmount(6, 0.3)));
                } else if (trialRoll < 350) { // 25.0% Rare
                    pool.add(new ItemStack(Items.BREEZE_ROD, getUncappedAmount(12, 0.3)));
                    pool.add(new ItemStack(Items.TRIAL_KEY, getUncappedAmount(10, 0.3)));
                    pool.add(new ItemStack(Items.OMINOUS_TRIAL_KEY, getUncappedAmount(3, 0.3)));
                    pool.add(new ItemStack(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                    pool.add(new ItemStack(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
                } else { // 65.0% Common
                    pool.add(new ItemStack(Items.BREEZE_ROD, getUncappedAmount(6, 0.3)));
                    pool.add(new ItemStack(Items.TRIAL_KEY, getUncappedAmount(4, 0.3)));
                }
                break;
            }
        }

        if (pool.isEmpty()) {
            return new ItemStack(Items.COAL);
        }
        return pool.get(RANDOM.nextInt(pool.size())).copy();
    }
}
