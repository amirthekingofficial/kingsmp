package net.kingsmp.economy;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;

public class HelpScreenHandler extends ChestMenu {

    public HelpScreenHandler(int syncId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, new SimpleContainer(54), 6);
        refreshHelp();
    }

    private void refreshHelp() {
        net.minecraft.world.Container container = this.getContainer();
        container.clearContent();

        // Filler glass pane
        ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        filler.set(DataComponents.CUSTOM_NAME, Component.empty());

        ItemStack border = new ItemStack(Items.STAINED_GLASS_PANE.orange());
        border.set(DataComponents.CUSTOM_NAME, Component.empty());

        // Fill background with grey glass panes
        for (int i = 0; i < 54; i++) {
            container.setItem(i, filler);
        }

        // Orange border for row 5 (slots 36-44)
        for (int i = 36; i < 45; i++) {
            container.setItem(i, border);
        }

        // ── CATEGORY 1: ECONOMY & MARKET (Row 1: 0-8) ──
        ItemStack cat1 = new ItemStack(Items.BOOK);
        cat1.set(DataComponents.CUSTOM_NAME, Component.literal("Economy & Shop").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        container.setItem(0, cat1);

        container.setItem(1, createCommandItem(Items.GOLD_INGOT, "/bal", "View your current virtual Silver balance.", "Example: /bal"));
        container.setItem(2, createCommandItem(Items.GOLD_BLOCK, "/pay <player> <amount>", "Transfer Silver to another player.", "Example: /pay Notch 50"));
        container.setItem(3, createCommandItem(Items.CHEST, "/sell", "Open the sell chest GUI to sell items for Silver.", "Example: /sell"));
        container.setItem(4, createCommandItem(Items.PAPER, "/price", "Check the bank sell value of your held item.", "Example: /price"));
        container.setItem(5, createCommandItem(Items.EMERALD, "/shop", "Open the Global Market (Resources, Spawners, Market).", "Example: /shop"));
        container.setItem(6, createCommandItem(Items.DIAMOND, "/shop sell <price>", "List your held item on the Player Market.", "Example: /shop sell 200"));
        container.setItem(7, createCommandItem(Items.REDSTONE, "/market cancel <id>", "Cancel one of your active market listings.", "Example: /market cancel 171800000"));
        container.setItem(8, filler);

        // ── CATEGORY 2: KING & POLITICS (Row 2: 9-17) ──
        ItemStack cat2 = new ItemStack(Items.BOOK);
        cat2.set(DataComponents.CUSTOM_NAME, Component.literal("Crowns & Politics").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        container.setItem(9, cat2);

        container.setItem(10, createCommandItem(Items.ANVIL, "/king upgrade", "Open Blacksmith Crown Upgrade station.", "Example: /king upgrade"));
        container.setItem(11, createCommandItem(Items.BLAZE_POWDER, "/king ability", "Trigger your Crown Archetype active ability.", "Example: /king ability"));
        container.setItem(12, createCommandItem(Items.BEACON, "/king status", "Check the 5 Archetype Thrones and active Kings.", "Example: /king status"));
        container.setItem(13, createCommandItem(Items.WRITABLE_BOOK, "/king vote <player>", "Cast your vote for a player to become King.", "Example: /king vote Notch"));
        container.setItem(14, createCommandItem(Items.GOLDEN_CARROT, "/king leaderboard", "View the top players by reign time.", "Example: /king leaderboard"));
        container.setItem(15, createCommandItem(Items.SUNFLOWER, "/king myvote", "Check who you voted for in the active election.", "Example: /king myvote"));
        container.setItem(16, createCommandItem(Items.PLAYER_HEAD, "/who [player]", "Inspect citizen standing, rank, professions, and dossier.", "Example: /who Notch"));
        container.setItem(17, filler);

        // ── CATEGORY 3: SOCIAL & TRAVEL (Row 3: 18-26) ──
        ItemStack cat3 = new ItemStack(Items.BOOK);
        cat3.set(DataComponents.CUSTOM_NAME, Component.literal("Travel & Homes").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        container.setItem(18, cat3);

        container.setItem(19, createCommandItem(Items.ENDER_PEARL, "/tpa <player>", "Request to teleport to a player.", "Example: /tpa Notch"));
        container.setItem(20, createCommandItem(Items.SLIME_BALL, "/tpaccept", "Accept a pending teleport request.", "Example: /tpaccept"));
        container.setItem(21, createCommandItem(Items.BARRIER, "/tpdeny", "Deny a pending teleport request.", "Example: /tpdeny"));
        container.setItem(22, createCommandItem(Items.BED.red(), "/home", "Teleport to your home location.", "Example: /home"));
        container.setItem(23, createCommandItem(Items.COMPASS, "/sethome", "Set your home location at your current position.", "Example: /sethome"));
        container.setItem(24, createCommandItem(Items.FEATHER, "/rtp", "Teleport to a random safe location.", "Example: /rtp"));
        container.setItem(25, createCommandItem(Items.ENDER_CHEST, "/enderchest", "Open your virtual Ender Chest.", "Example: /enderchest"));
        container.setItem(26, filler);

        // ── CATEGORY 4: FACTIONS & TRADES (Row 4: 27-35) ──
        ItemStack cat4 = new ItemStack(Items.BOOK);
        cat4.set(DataComponents.CUSTOM_NAME, Component.literal("Factions & Trades").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        container.setItem(27, cat4);

        container.setItem(28, createCommandItem(Items.SHIELD, "/rank path <name>", "Choose Military, Logistics, Scout, or Occult.", "Example: /rank path military"));
        container.setItem(29, createCommandItem(Items.BOOK, "/rank info", "View your rank standing and cumulative perks.", "Example: /rank info"));
        container.setItem(30, createCommandItem(Items.GOLD_INGOT, "/rank promote <player>", "Promote a faction member to the next rung.", "Example: /rank promote Notch"));
        container.setItem(31, createCommandItem(Items.EXPERIENCE_BOTTLE, "/rank buff", "Cast Occult Chanter faction blessing.", "Example: /rank buff"));
        container.setItem(32, createCommandItem(Items.CRAFTING_TABLE, "/trade board", "Open the 7-trade Profession Progression Board.", "Example: /trade board"));
        container.setItem(33, createCommandItem(Items.DIAMOND_PICKAXE, "/trade pulse", "Trigger Miner Vein Sense (Master Miner).", "Example: /trade pulse"));
        container.setItem(34, createCommandItem(Items.BOW, "/quest", "Open the Daily Quests GUI.", "Example: /quest"));
        container.setItem(35, createCommandItem(Items.SKELETON_SKULL, "/bounty add <p> <amt>", "Place a Silver bounty on a player.", "Example: /bounty add Notch 100"));

        // ── ROW 6: DISCORD (Slot 49) ──
        ItemStack discordItem = new ItemStack(Items.COMPASS);
        discordItem.set(DataComponents.CUSTOM_NAME, Component.literal("💬 Join our Discord!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        List<Component> discordLore = new ArrayList<>();
        discordLore.add(Component.literal("Link: .gg/mckingsmp").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE));
        discordLore.add(Component.empty());
        discordLore.add(Component.literal("Check discord for community updates,").withStyle(ChatFormatting.GRAY));
        discordLore.add(Component.literal("rules, trading, and announcements!").withStyle(ChatFormatting.GRAY));
        discordItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(discordLore));
        container.setItem(49, discordItem);
    }

    private ItemStack createCommandItem(net.minecraft.world.item.Item item, String command, String description, String example) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(command).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(description).withStyle(ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(Component.literal(example).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        // Cancel all click actions
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
