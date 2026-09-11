package net.kingsmp.professions;

import net.kingsmp.KingSMPMod;
import net.kingsmp.crowns.CrownType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

/**
 * Interactive 3-row Chest GUI for Blacksmith Crown Upgrades & Masterwork Forge.
 * Implements strict atomic transaction validation.
 */
public class BlacksmithScreenHandler extends ChestMenu {

    private final ServerPlayer player;
    private final SimpleContainer container;

    public static void open(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(27);
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("⚒ Royal Forge & Crown Station").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }

            @Override
            public ChestMenu createMenu(int syncId, Inventory playerInventory, Player playerEntity) {
                return new BlacksmithScreenHandler(syncId, playerInventory, container, (ServerPlayer) playerEntity);
            }
        });
    }

    public BlacksmithScreenHandler(int syncId, Inventory playerInventory, SimpleContainer container, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, container, 3);
        this.player = player;
        this.container = container;
        renderGui();
    }

    private void renderGui() {
        ItemStack border = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        border.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, border.copy());
        }

        ItemStack crown = findCrown(player);
        if (crown == null || crown.isEmpty()) {
            ItemStack noCrown = new ItemStack(Items.BARRIER);
            noCrown.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("No Crown Detected!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("You must carry a Royal Crown in your").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("inventory to access forge upgrades.").withStyle(ChatFormatting.GRAY));
            noCrown.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));
            container.setItem(13, noCrown);
            return;
        }

        CrownType type = KingSMPMod.getCrownType(crown);
        int currentStep = KingSMPMod.getCrownStep(crown);

        // Display Crown in Center
        container.setItem(11, crown.copy());

        // Upgrade button in slot 15
        if (currentStep >= 3) {
            ItemStack max = new ItemStack(Items.NETHER_STAR);
            max.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Crown at Maximum Step! [III]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            container.setItem(15, max);
        } else {
            int nextStep = currentStep + 1;
            ItemStack upgradeBtn = new ItemStack(Items.ANVIL);
            upgradeBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Upgrade to Step " + (nextStep == 2 ? "II" : "III")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Required Materials:").withStyle(ChatFormatting.YELLOW));

            Map<Item, Integer> costs = getUpgradeCosts(nextStep);
            for (var entry : costs.entrySet()) {
                int owned = countItem(player, entry.getKey());
                boolean hasEnough = owned >= entry.getValue();
                lore.add(Component.literal("• " + entry.getValue() + "x " + getItemName(entry.getKey()))
                        .withStyle(hasEnough ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.literal(" (" + owned + "/" + entry.getValue() + ")").withStyle(ChatFormatting.DARK_GRAY)));
            }

            lore.add(Component.literal(""));
            lore.add(Component.literal("Click to forge upgrade atomically!").withStyle(ChatFormatting.GRAY));
            upgradeBtn.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));
            container.setItem(15, upgradeBtn);
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player playerEntity) {
        if (slotIndex < 0 || slotIndex >= 27) {
            super.clicked(slotIndex, button, actionType, playerEntity);
            return;
        }

        if (slotIndex == 15) {
            handleUpgrade();
            renderGui();
            this.broadcastFullState();
            return;
        }

        this.broadcastFullState();
    }

    private void handleUpgrade() {
        ItemStack crown = findCrown(player);
        if (crown == null || crown.isEmpty()) {
            player.sendOverlayMessage(Component.literal("No Crown found!").withStyle(ChatFormatting.RED));
            return;
        }

        int currentStep = KingSMPMod.getCrownStep(crown);
        if (currentStep >= 3) {
            player.sendOverlayMessage(Component.literal("Crown is already at Step III!").withStyle(ChatFormatting.GOLD));
            return;
        }

        int nextStep = currentStep + 1;
        Map<Item, Integer> costs = getUpgradeCosts(nextStep);

        // ── ATOMIC VALIDATION ─────────────────────────────────────────────────
        if (!hasAll(player, costs)) {
            player.sendOverlayMessage(Component.literal("Missing required forge materials!").withStyle(ChatFormatting.RED));
            KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // All items verified present: consume atomically
        for (var entry : costs.entrySet()) {
            consumeItem(player, entry.getKey(), entry.getValue());
        }

        // Grant upgrade
        KingSMPMod.setCrownStep(crown, nextStep);
        KingSMPMod.saveNow();

        // Grant Blacksmith profession XP
        ProfessionManager.addXp(player, ProfessionType.BLACKSMITH, 500);

        player.sendSystemMessage(Component.literal("⚒ CROWN UPGRADED! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Your Crown has ascended to Step " + (nextStep == 2 ? "II" : "III") + "!").withStyle(ChatFormatting.GREEN)));
        KingSMPMod.playSoundToPlayer(player, SoundEvents.ANVIL_USE, 1.0f, 1.0f);
        player.containerMenu.broadcastFullState();
    }

    private Map<Item, Integer> getUpgradeCosts(int nextStep) {
        Map<Item, Integer> costs = new LinkedHashMap<>();
        if (nextStep == 2) {
            costs.put(Items.DIAMOND, 16);
            costs.put(Items.GOLD_BLOCK, 8);
            costs.put(Items.ECHO_SHARD, 1);
        } else if (nextStep == 3) {
            costs.put(Items.NETHERITE_INGOT, 2);
            costs.put(Items.NETHER_STAR, 1);
            costs.put(Items.ECHO_SHARD, 4);
        }
        return costs;
    }

    // ── ATOMIC HELPERS ────────────────────────────────────────────────────

    public static boolean hasAll(ServerPlayer player, Map<Item, Integer> costs) {
        for (var entry : costs.entrySet()) {
            if (countItem(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void consumeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }

    private static ItemStack findCrown(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (KingSMPMod.isCrown(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static String getItemName(Item item) {
        return item.getName(new ItemStack(item)).getString();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
