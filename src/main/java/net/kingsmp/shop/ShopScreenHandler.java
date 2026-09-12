package net.kingsmp.shop;

import net.kingsmp.KingSMPMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShopScreenHandler extends ChestMenu {

    // ── 4-Tab System ──────────────────────────────────────────────────────
    public enum Tab {
        SERVER_SHOP, SPAWNERS, GLOBAL_MARKET, MY_LISTINGS
    }

    private Tab currentTab = Tab.GLOBAL_MARKET;

    public enum SortMode {
        PRICE_HIGH_TO_LOW("Price: High ➔ Low"),
        PRICE_LOW_TO_HIGH("Price: Low ➔ High"),
        ITEM_NAME("Item Name"),
        YOUR_LISTINGS("Your Listings First"),
        ITEM_TYPE("Item Type");

        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private SortMode sortMode = SortMode.PRICE_HIGH_TO_LOW;

    private final net.minecraft.world.Container shopInventory;
    private final Player player;

    private int currentPage = 0;
    private String searchQuery = null;
    private static final int ITEMS_PER_PAGE = 36;

    public static class SearchSession {
        public final BlockPos pos;
        public final BlockState oldState;
        public final Tab returnTab;
        public final SortMode returnSortMode;

        public SearchSession(BlockPos pos, BlockState oldState, Tab returnTab, SortMode returnSortMode) {
            this.pos = pos;
            this.oldState = oldState;
            this.returnTab = returnTab;
            this.returnSortMode = returnSortMode;
        }
    }

    public static final Map<UUID, SearchSession> activeSearchSessions = new ConcurrentHashMap<>();

    public ShopScreenHandler(int syncId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, new SimpleContainer(54), 6);
        this.shopInventory = this.getContainer();
        this.player = playerInventory.player;
        refreshShop();
    }

    private void refreshShop() {
        shopInventory.clearContent();
        List<MarketListing> allListings = KingSMPMod.marketManager.getActiveListings();
        List<MarketListing> displayListings = new ArrayList<>();

        MarketListing[] tieredSpawners = new MarketListing[5];
        MarketListing endGamble = null;
        MarketListing overworldGamble = null;
        MarketListing netherGamble = null;
        MarketListing oreGamble = null;
        MarketListing trialGamble = null;

        for (MarketListing listing : allListings) {
            boolean isSpawner = listing.getItemToSell().is(Items.SPAWNER);

            if (currentTab == Tab.SERVER_SHOP && listing.isServerShop() && !isSpawner) {
                displayListings.add(listing);
            } else if (currentTab == Tab.SPAWNERS && listing.isServerShop() && isSpawner) {
                if (KingSMPMod.isGambleSpawner(listing.getItemToSell())) {
                    String gType = KingSMPMod.getGambleType(listing.getItemToSell());
                    if (gType.equals("end")) endGamble = listing;
                    else if (gType.equals("overworld")) overworldGamble = listing;
                    else if (gType.equals("nether")) netherGamble = listing;
                    else if (gType.equals("ore")) oreGamble = listing;
                    else if (gType.equals("trial")) trialGamble = listing;
                } else {
                    int tier = KingSMPMod.getSpawnerTier(listing.getItemToSell());
                    if (tier >= 1 && tier <= 5) {
                        tieredSpawners[tier - 1] = listing;
                    }
                }
            } else if (currentTab == Tab.GLOBAL_MARKET && !listing.isServerShop()) {
                displayListings.add(listing);
            } else if (currentTab == Tab.MY_LISTINGS && !listing.isServerShop()
                    && listing.getSellerId().equals(player.getUUID())) {
                displayListings.add(listing);
            }
        }

        if (currentTab == Tab.SPAWNERS) {
            // Fill background with grey glass panes
            ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
            filler.set(DataComponents.CUSTOM_NAME, Component.empty());
            for (int i = 0; i < 45; i++) {
                shopInventory.setItem(i, filler);
            }

            // Indicator items (Row 2: 9, 11, 13, 15, 17)
            ItemStack copperIngot = new ItemStack(Items.COPPER_INGOT);
            copperIngot.set(DataComponents.CUSTOM_NAME, Component.literal("Copper Tier - Tier 1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            shopInventory.setItem(9, copperIngot);

            ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
            ironIngot.set(DataComponents.CUSTOM_NAME, Component.literal("Iron Tier - Tier 2").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
            shopInventory.setItem(11, ironIngot);

            ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);
            goldIngot.set(DataComponents.CUSTOM_NAME, Component.literal("Gold Tier - Tier 3").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            shopInventory.setItem(13, goldIngot);

            ItemStack diamond = new ItemStack(Items.DIAMOND);
            diamond.set(DataComponents.CUSTOM_NAME, Component.literal("Diamond Tier - Tier 4").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            shopInventory.setItem(15, diamond);

            ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
            netherite.set(DataComponents.CUSTOM_NAME, Component.literal("Netherite Tier - Tier 5").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            shopInventory.setItem(17, netherite);

            // Spawners (Row 3: 18, 20, 22, 24, 26)
            int[] slots = {18, 20, 22, 24, 26};
            for (int j = 0; j < 5; j++) {
                MarketListing listing = tieredSpawners[j];
                if (listing != null) {
                    ItemStack displayItem = listing.getItemToSell().copy();
                    List<Component> loreLines = new ArrayList<>();
                    loreLines.add(Component.literal("Cost: " + listing.getSilverPrice() + " 🪙 Silver").withStyle(ChatFormatting.YELLOW));
                    loreLines.add(Component.literal("Seller: " + listing.getSellerName()).withStyle(ChatFormatting.GRAY));
                    loreLines.add(Component.empty());
                    loreLines.add(Component.literal("Infinite Stock").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    loreLines.add(Component.literal("Click to Buy").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    displayItem.set(DataComponents.LORE, new ItemLore(loreLines));
                    shopInventory.setItem(slots[j], displayItem);
                }
            }

            // Gamble Indicators (Row 4: 27, 29, 31, 33, 35)
            ItemStack endStone = new ItemStack(Items.END_STONE);
            endStone.set(DataComponents.CUSTOM_NAME, Component.literal("End Gamble").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            shopInventory.setItem(27, endStone);

            ItemStack grassBlock = new ItemStack(Items.GRASS_BLOCK);
            grassBlock.set(DataComponents.CUSTOM_NAME, Component.literal("Overworld Gamble").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            shopInventory.setItem(29, grassBlock);

            ItemStack netherrack = new ItemStack(Items.NETHERRACK);
            netherrack.set(DataComponents.CUSTOM_NAME, Component.literal("Nether Gamble").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            shopInventory.setItem(31, netherrack);

            ItemStack rawGold = new ItemStack(Items.RAW_GOLD_BLOCK);
            rawGold.set(DataComponents.CUSTOM_NAME, Component.literal("Ore & Ingot Gamble").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            shopInventory.setItem(33, rawGold);

            ItemStack trialKey = new ItemStack(Items.TRIAL_KEY);
            trialKey.set(DataComponents.CUSTOM_NAME, Component.literal("Trial Chamber Gamble").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
            shopInventory.setItem(35, trialKey);

            // Gamble Spawners (Row 5: 36, 38, 40, 42, 44)
            int[] gSlots = {36, 38, 40, 42, 44};
            MarketListing[] gListings = {endGamble, overworldGamble, netherGamble, oreGamble, trialGamble};
            for (int j = 0; j < 5; j++) {
                MarketListing listing = gListings[j];
                if (listing != null) {
                    ItemStack displayItem = listing.getItemToSell().copy();
                    List<Component> loreLines = new ArrayList<>();
                    loreLines.add(Component.literal("Cost: " + listing.getSilverPrice() + " 🪙 Silver").withStyle(ChatFormatting.YELLOW));
                    loreLines.add(Component.literal("Seller: " + listing.getSellerName()).withStyle(ChatFormatting.GRAY));
                    loreLines.add(Component.empty());
                    loreLines.add(Component.literal("Infinite Stock").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    loreLines.add(Component.literal("Left-Click to Buy").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    loreLines.add(Component.literal("Right-Click to Preview Loot").withStyle(ChatFormatting.GOLD));
                    displayItem.set(DataComponents.LORE, new ItemLore(loreLines));
                    shopInventory.setItem(gSlots[j], displayItem);
                }
            }
        } else {
            // Apply search query filter
            if (searchQuery != null && !searchQuery.isEmpty()) {
                displayListings.removeIf(listing -> !listing.getItemToSell().getHoverName().getString().toLowerCase().contains(searchQuery.toLowerCase()));
            }

            // Apply sorting to displayListings before rendering
            if (currentTab == Tab.GLOBAL_MARKET || currentTab == Tab.MY_LISTINGS) {
                displayListings.sort((a, b) -> {
                    switch (sortMode) {
                        case PRICE_HIGH_TO_LOW:
                            return Integer.compare(b.getSilverPrice(), a.getSilverPrice());
                        case PRICE_LOW_TO_HIGH:
                            return Integer.compare(a.getSilverPrice(), b.getSilverPrice());
                        case ITEM_NAME:
                            return a.getItemToSell().getHoverName().getString()
                                    .compareToIgnoreCase(b.getItemToSell().getHoverName().getString());
                        case YOUR_LISTINGS:
                            boolean aIsMine = a.getSellerId().equals(player.getUUID());
                            boolean bIsMine = b.getSellerId().equals(player.getUUID());
                            if (aIsMine && !bIsMine) return -1;
                            if (!aIsMine && bIsMine) return 1;
                            return Integer.compare(b.getSilverPrice(), a.getSilverPrice());
                        case ITEM_TYPE:
                            int typeComp = Integer.compare(getItemTypeScore(a.getItemToSell()), getItemTypeScore(b.getItemToSell()));
                            if (typeComp != 0) return typeComp;
                            return a.getItemToSell().getHoverName().getString()
                                    .compareToIgnoreCase(b.getItemToSell().getHoverName().getString());
                        default:
                            return 0;
                    }
                });
            }

            // Paginated item rendering
            int totalPages = (int) Math.ceil((double) displayListings.size() / ITEMS_PER_PAGE);
            if (totalPages <= 0) totalPages = 1;
            if (currentPage >= totalPages) currentPage = totalPages - 1;
            if (currentPage < 0) currentPage = 0;

            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayListings.size());

            int slotIdx = 0;
            for (int i = startIndex; i < endIndex; i++) {
                if (slotIdx >= 36) {
                    break;
                }
                MarketListing listing = displayListings.get(i);
                ItemStack displayItem = listing.getItemToSell().copy();

                Component priceText;
                if (listing.getSilverPrice() > 0) {
                    priceText = Component.literal("Cost: " + listing.getSilverPrice() + " 🪙 Silver")
                            .withStyle(ChatFormatting.YELLOW);
                } else {
                    priceText = Component.literal("Cost: FREE").withStyle(ChatFormatting.YELLOW);
                }

                Component sellerText = Component.literal("Seller: " + listing.getSellerName()).withStyle(ChatFormatting.GRAY);

                List<Component> loreLines = new ArrayList<>();
                loreLines.add(priceText);
                loreLines.add(sellerText);
                loreLines.add(Component.empty());

                if (currentTab == Tab.MY_LISTINGS) {
                    loreLines.add(Component.literal("Left-Click to Cancel Listing").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                } else if (currentTab == Tab.SERVER_SHOP) {
                    loreLines.add(Component.literal("Infinite Stock").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    loreLines.add(Component.literal("Click to Buy").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                } else {
                    if (player.getUUID().equals(listing.getSellerId())) {
                        loreLines.add(
                                Component.literal("Your Listing (Click to Cancel)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                    } else {
                        if (KingSMPMod.isGambleSpawner(listing.getItemToSell())) {
                            loreLines.add(Component.literal("Left-Click to Buy").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                            loreLines.add(Component.literal("Right-Click to Preview Loot").withStyle(ChatFormatting.GOLD));
                        } else {
                            loreLines.add(Component.literal("Click to Buy").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                        }
                    }
                }

                displayItem.set(DataComponents.LORE, new ItemLore(loreLines));
                shopInventory.setItem(slotIdx, displayItem);
                slotIdx++;
            }
        }

        if (currentTab != Tab.SPAWNERS) {
            // Fill control row (row 5) and tab row (row 6) with filler first
            ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
            filler.set(DataComponents.CUSTOM_NAME, Component.empty());
            for (int i = 36; i < 54; i++) {
                shopInventory.setItem(i, filler);
            }

            // Previous Page button in Slot 37
            if (currentPage > 0) {
                ItemStack prevPage = new ItemStack(Items.ARROW);
                prevPage.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                List<Component> prevLore = new ArrayList<>();
                prevLore.add(Component.literal("Go to page " + currentPage).withStyle(ChatFormatting.GRAY));
                prevPage.set(DataComponents.LORE, new ItemLore(prevLore));
                shopInventory.setItem(37, prevPage);
            }

            // Search Sign button in Slot 38
            ItemStack searchBtn = new ItemStack(Items.OAK_SIGN);
            searchBtn.set(DataComponents.CUSTOM_NAME, Component.literal("Search Market").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            List<Component> searchLore = new ArrayList<>();
            searchLore.add(Component.literal("Click to search items by name").withStyle(ChatFormatting.GRAY));
            if (searchQuery != null && !searchQuery.isEmpty()) {
                searchLore.add(Component.empty());
                searchLore.add(Component.literal("Current Filter: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(searchQuery).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            }
            searchBtn.set(DataComponents.LORE, new ItemLore(searchLore));
            shopInventory.setItem(38, searchBtn);

            // Clear Search button in Slot 39
            if (searchQuery != null && !searchQuery.isEmpty()) {
                ItemStack clearBtn = new ItemStack(Items.BARRIER);
                clearBtn.set(DataComponents.CUSTOM_NAME, Component.literal("Clear Search").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                List<Component> clearLore = new ArrayList<>();
                clearLore.add(Component.literal("Click to clear current filter:").withStyle(ChatFormatting.GRAY));
                clearLore.add(Component.literal("\"" + searchQuery + "\"").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
                clearBtn.set(DataComponents.LORE, new ItemLore(clearLore));
                shopInventory.setItem(39, clearBtn);
            }

            // Page Indicator in Slot 40
            int totalPages = (int) Math.ceil((double) displayListings.size() / ITEMS_PER_PAGE);
            if (totalPages <= 0) totalPages = 1;
            ItemStack pageInd = new ItemStack(Items.BOOK);
            pageInd.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (currentPage + 1) + " of " + totalPages).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            List<Component> pageLore = new ArrayList<>();
            pageLore.add(Component.literal("Total listings: " + displayListings.size()).withStyle(ChatFormatting.GRAY));
            pageInd.set(DataComponents.LORE, new ItemLore(pageLore));
            shopInventory.setItem(40, pageInd);

            // Sort Mode in Slot 41
            if (currentTab == Tab.GLOBAL_MARKET || currentTab == Tab.MY_LISTINGS) {
                ItemStack sortBtn = new ItemStack(Items.HOPPER);
                sortBtn.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Sort Mode").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                List<Component> sortLore = new ArrayList<>();
                sortLore.add(Component.literal("Current: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(sortMode.getDisplayName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
                sortLore.add(Component.literal(""));
                sortLore.add(Component.literal("Click to Cycle Sort Modes:").withStyle(ChatFormatting.GRAY));
                for (SortMode mode : SortMode.values()) {
                    if (mode == sortMode) {
                        sortLore.add(Component.literal(" ● " + mode.getDisplayName()).withStyle(ChatFormatting.GREEN));
                    } else {
                        sortLore.add(Component.literal(" ○ " + mode.getDisplayName()).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                sortBtn.set(DataComponents.LORE, new ItemLore(sortLore));
                shopInventory.setItem(41, sortBtn);
            }

            // Next Page button in Slot 42
            if (currentPage < totalPages - 1) {
                ItemStack nextPage = new ItemStack(Items.ARROW);
                nextPage.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                List<Component> nextLore = new ArrayList<>();
                nextLore.add(Component.literal("Go to page " + (currentPage + 2)).withStyle(ChatFormatting.GRAY));
                nextPage.set(DataComponents.LORE, new ItemLore(nextLore));
                shopInventory.setItem(42, nextPage);
            }
        } else {
            ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
            filler.set(DataComponents.CUSTOM_NAME, Component.empty());
            for (int i = 45; i < 54; i++) {
                shopInventory.setItem(i, filler);
            }
        }

        ItemStack serverBtn = new ItemStack(Items.NETHER_STAR);
        serverBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Resources").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (currentTab == Tab.SERVER_SHOP)
            serverBtn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        shopInventory.setItem(46, serverBtn);

        ItemStack spawnerBtn = new ItemStack(Items.SPAWNER);
        spawnerBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Spawners").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        if (currentTab == Tab.SPAWNERS)
            spawnerBtn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        shopInventory.setItem(48, spawnerBtn);

        ItemStack globalBtn = new ItemStack(Items.EMERALD);
        globalBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Player Market").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (currentTab == Tab.GLOBAL_MARKET)
            globalBtn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        shopInventory.setItem(50, globalBtn);

        ItemStack mineBtn = new ItemStack(Items.PLAYER_HEAD);
        mineBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("My Listings").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (currentTab == Tab.MY_LISTINGS)
            mineBtn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        shopInventory.setItem(52, mineBtn);
    }

    private void openSearchSignInput() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        serverPlayer.closeContainer();

        BlockPos pos = serverPlayer.blockPosition().above(5);
        ServerLevel level = (ServerLevel) serverPlayer.level();

        BlockState oldState = level.getBlockState(pos);

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState(), 3);

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            activeSearchSessions.put(serverPlayer.getUUID(), new SearchSession(pos, oldState, currentTab, sortMode));
            serverPlayer.openTextEdit(sign, true);
        } else {
            level.setBlock(pos, oldState, 3);
            reopenShop(serverPlayer, currentTab, sortMode, searchQuery);
        }
    }

    public static void reopenShop(ServerPlayer player, Tab tab, SortMode mode, String query) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (syncId, playerInv, p) -> {
                    ShopScreenHandler handler = new ShopScreenHandler(syncId, playerInv);
                    handler.currentTab = tab;
                    handler.sortMode = mode;
                    handler.searchQuery = query;
                    handler.currentPage = 0;
                    handler.refreshShop();
                    return handler;
                },
                Component.literal("Global Market").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
        ));
    }

    public static void cleanupSearchSession(UUID playerUuid, net.minecraft.server.MinecraftServer server) {
        SearchSession session = activeSearchSessions.remove(playerUuid);
        if (session != null && server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getBlockState(session.pos).is(net.minecraft.world.level.block.Blocks.OAK_SIGN)) {
                    level.setBlock(session.pos, session.oldState, 3);
                    break;
                }
            }
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            super.clicked(slotIndex, button, actionType, player);
            return;
        }

        if (slotIndex < 54) {
            if (currentTab != Tab.SPAWNERS && slotIndex >= 36 && slotIndex < 45) {
                // Row 5 Controls
                if (slotIndex == 37 && currentPage > 0) {
                    currentPage--;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 38) {
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    openSearchSignInput();
                } else if (slotIndex == 39 && searchQuery != null && !searchQuery.isEmpty()) {
                    searchQuery = null;
                    currentPage = 0;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 41 && (currentTab == Tab.GLOBAL_MARKET || currentTab == Tab.MY_LISTINGS)) {
                    int nextIndex = (sortMode.ordinal() + 1) % SortMode.values().length;
                    sortMode = SortMode.values()[nextIndex];
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 42) {
                    List<MarketListing> allListings = KingSMPMod.marketManager.getActiveListings();
                    List<MarketListing> displayListings = new ArrayList<>();
                    for (MarketListing listing : allListings) {
                        boolean isSpawner = listing.getItemToSell().is(Items.SPAWNER);
                        if (currentTab == Tab.SERVER_SHOP && listing.isServerShop() && !isSpawner) displayListings.add(listing);
                        else if (currentTab == Tab.GLOBAL_MARKET && !listing.isServerShop()) displayListings.add(listing);
                        else if (currentTab == Tab.MY_LISTINGS && !listing.isServerShop() && listing.getSellerId().equals(player.getUUID())) displayListings.add(listing);
                    }
                    if (searchQuery != null && !searchQuery.isEmpty()) {
                        displayListings.removeIf(listing -> !listing.getItemToSell().getHoverName().getString().toLowerCase().contains(searchQuery.toLowerCase()));
                    }
                    int totalPages = (int) Math.ceil((double) displayListings.size() / ITEMS_PER_PAGE);
                    if (totalPages <= 0) totalPages = 1;
                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                        refreshShop();
                    }
                }
                this.broadcastFullState();
                return;
            }

            if (slotIndex >= 45) {
                // Tab Selection in Row 6
                if (slotIndex == 46 && currentTab != Tab.SERVER_SHOP) {
                    currentTab = Tab.SERVER_SHOP;
                    currentPage = 0;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 48 && currentTab != Tab.SPAWNERS) {
                    currentTab = Tab.SPAWNERS;
                    currentPage = 0;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 50 && currentTab != Tab.GLOBAL_MARKET) {
                    currentTab = Tab.GLOBAL_MARKET;
                    currentPage = 0;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                } else if (slotIndex == 52 && currentTab != Tab.MY_LISTINGS) {
                    currentTab = Tab.MY_LISTINGS;
                    currentPage = 0;
                    KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    refreshShop();
                }
                this.broadcastFullState();
                return;
            }

            List<MarketListing> allListings = KingSMPMod.marketManager.getActiveListings();
            List<MarketListing> displayListings = new ArrayList<>();

            for (MarketListing listing : allListings) {
                boolean isSpawner = listing.getItemToSell().is(Items.SPAWNER);
                if (currentTab == Tab.SERVER_SHOP && listing.isServerShop() && !isSpawner)
                    displayListings.add(listing);
                else if (currentTab == Tab.SPAWNERS && listing.isServerShop() && isSpawner)
                    displayListings.add(listing);
                else if (currentTab == Tab.GLOBAL_MARKET && !listing.isServerShop())
                    displayListings.add(listing);
                else if (currentTab == Tab.MY_LISTINGS && !listing.isServerShop()
                        && listing.getSellerId().equals(player.getUUID()))
                    displayListings.add(listing);
            }

            if (searchQuery != null && !searchQuery.isEmpty()) {
                displayListings.removeIf(listing -> !listing.getItemToSell().getHoverName().getString().toLowerCase().contains(searchQuery.toLowerCase()));
            }

            if (currentTab == Tab.GLOBAL_MARKET || currentTab == Tab.MY_LISTINGS) {
                displayListings.sort((a, b) -> {
                    switch (sortMode) {
                        case PRICE_HIGH_TO_LOW:
                            return Integer.compare(b.getSilverPrice(), a.getSilverPrice());
                        case PRICE_LOW_TO_HIGH:
                            return Integer.compare(a.getSilverPrice(), b.getSilverPrice());
                        case ITEM_NAME:
                            return a.getItemToSell().getHoverName().getString()
                                    .compareToIgnoreCase(b.getItemToSell().getHoverName().getString());
                        case YOUR_LISTINGS:
                            boolean aIsMine = a.getSellerId().equals(player.getUUID());
                            boolean bIsMine = b.getSellerId().equals(player.getUUID());
                            if (aIsMine && !bIsMine) return -1;
                            if (!aIsMine && bIsMine) return 1;
                            return Integer.compare(b.getSilverPrice(), a.getSilverPrice());
                        case ITEM_TYPE:
                            int typeComp = Integer.compare(getItemTypeScore(a.getItemToSell()), getItemTypeScore(b.getItemToSell()));
                            if (typeComp != 0) return typeComp;
                            return a.getItemToSell().getHoverName().getString()
                                    .compareToIgnoreCase(b.getItemToSell().getHoverName().getString());
                        default:
                            return 0;
                    }
                });
            }

            MarketListing clickedListing = null;

            if (currentTab == Tab.SPAWNERS) {
                int tier = switch (slotIndex) {
                    case 18 -> 1;
                    case 20 -> 2;
                    case 22 -> 3;
                    case 24 -> 4;
                    case 26 -> 5;
                    default -> -1;
                };
                String gType = switch (slotIndex) {
                    case 36 -> "end";
                    case 38 -> "overworld";
                    case 40 -> "nether";
                    case 42 -> "ore";
                    case 44 -> "trial";
                    default -> "";
                };
                if (tier != -1) {
                    for (MarketListing listing : allListings) {
                        if (listing.isServerShop() && listing.getItemToSell().is(Items.SPAWNER) && !KingSMPMod.isGambleSpawner(listing.getItemToSell())) {
                            if (KingSMPMod.getSpawnerTier(listing.getItemToSell()) == tier) {
                                clickedListing = listing;
                                break;
                            }
                        }
                    }
                } else if (!gType.isEmpty()) {
                    for (MarketListing listing : allListings) {
                        if (listing.isServerShop() && listing.getItemToSell().is(Items.SPAWNER) && KingSMPMod.isGambleSpawner(listing.getItemToSell())) {
                            if (KingSMPMod.getGambleType(listing.getItemToSell()).equalsIgnoreCase(gType)) {
                                clickedListing = listing;
                                break;
                            }
                        }
                    }
                }
            } else {
                int clickedListIdx = -1;
                if (slotIndex < 36) {
                    clickedListIdx = (currentPage * ITEMS_PER_PAGE) + slotIndex;
                }

                if (clickedListIdx >= 0 && clickedListIdx < displayListings.size()) {
                    clickedListing = displayListings.get(clickedListIdx);
                }
            }

                if (clickedListing != null) {
                    if (currentTab == Tab.MY_LISTINGS) {
                        handleMyListingClick(clickedListing, button, actionType);
                    } else {
                        boolean isGamble = KingSMPMod.isGambleSpawner(clickedListing.getItemToSell());
                        if (isGamble && button == 1) { // Right-Click to preview
                            if (player instanceof ServerPlayer serverPlayer) {
                                String type = KingSMPMod.getGambleType(clickedListing.getItemToSell());
                                MarketListing finalListing = clickedListing;
                                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                        (syncId, playerInv, p) -> new net.kingsmp.shop.GamblePreviewScreenHandler(syncId, playerInv, type, finalListing),
                                        Component.literal("Preview - " + type.toUpperCase()).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                                ));
                                KingSMPMod.playSoundToPlayer(player, SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
                            }
                        } else {
                            processPurchase(clickedListing);
                        }
                    }
                }
            this.broadcastFullState();
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    private void handleMyListingClick(MarketListing listing, int button, ContainerInput actionType) {
        if (button == 0 && actionType != ContainerInput.QUICK_MOVE) {
            ItemStack returnedItem = listing.getItemToSell().copy();
            if (!player.getInventory().add(returnedItem))
                player.drop(returnedItem, false);
            KingSMPMod.marketManager.removeListing(listing);
            player.sendOverlayMessage(Component.literal("Listing cancelled! Item returned.").withStyle(ChatFormatting.YELLOW));
            KingSMPMod.playSoundToPlayer(player, SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
        }
        refreshShop();
    }

    private void processPurchase(MarketListing listing) {
        if (player.getUUID().equals(listing.getSellerId()) && !listing.isServerShop()) {
            ItemStack returnedItem = listing.getItemToSell().copy();
            if (!player.getInventory().add(returnedItem)) {
                player.drop(returnedItem, false);
            }
            KingSMPMod.marketManager.removeListing(listing);
            player.sendOverlayMessage(Component.literal("Listing cancelled! Item returned.").withStyle(ChatFormatting.YELLOW));
            KingSMPMod.playSoundToPlayer(player, SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
            refreshShop();
            return;
        }

        // ── VIRTUAL SILVER TRANSACTION ────────────────────────────────────────
        int basePrice = listing.getSilverPrice();
        if (basePrice > 0) {
            int finalPrice = basePrice;
            // Personal NPC shop discounts: 10% for Crown of Gold, 5% for Seneschal
            if (listing.isServerShop()) {
                ItemStack crown = net.kingsmp.crowns.CrownManager.getCrownInInventory(player);
                if (crown != null && KingSMPMod.getCrownType(crown) == net.kingsmp.crowns.CrownType.GOLD) {
                    finalPrice = (int) Math.round(basePrice * 0.90);
                } else if (net.kingsmp.factions.FactionManager.getPlayerRung(player.getUUID()) >= 4 &&
                           net.kingsmp.factions.FactionManager.getPlayerPath(player.getUUID()) == net.kingsmp.factions.FactionManager.RankPath.LOGISTICS) {
                    finalPrice = (int) Math.round(basePrice * 0.95);
                }
            }

            if (KingSMPMod.dataManager.removeSilver(player.getUUID(), finalPrice)) {
                ItemStack boughtItem = listing.getItemToSell().copy();
                if (!player.getInventory().add(boughtItem))
                    player.drop(boughtItem, false);

                player.sendOverlayMessage(Component.literal("Bought for " + finalPrice + " 🪙 Silver!").withStyle(ChatFormatting.GREEN));

                if (!listing.isServerShop()) {
                    KingSMPMod.marketManager.removeListing(listing);

                    // 1. Calculate market fee (5% default, 3% if seller is Master Merchant)
                    boolean isMasterMerchant = net.kingsmp.professions.ProfessionManager.isMaster(listing.getSellerId(), net.kingsmp.professions.ProfessionType.MERCHANT);
                    double feePercent = isMasterMerchant ? net.kingsmp.config.KingSMPConfig.masterMerchantMarketFeePercent : net.kingsmp.config.KingSMPConfig.marketFeePercent;
                    int marketFee = (int) Math.round(basePrice * (feePercent / 100.0));
                    int proceedsAfterFee = basePrice - marketFee;

                    // 2. Deduct Faction Tax from proceeds (if seller belongs to a taxed faction)
                    int factionTaxCut = 0;
                    String sellerFaction = net.kingsmp.factions.FactionManager.getPlayerFactionByUuid(listing.getSellerId());
                    if (sellerFaction != null) {
                        int taxRate = net.kingsmp.factions.FactionManager.getFactionTax(sellerFaction);
                        if (taxRate > 0) {
                            UUID factionKingUuid = net.kingsmp.factions.FactionManager.getFactionKing(sellerFaction);
                            if (factionKingUuid != null) {
                                factionTaxCut = (int) Math.round(proceedsAfterFee * (taxRate / 100.0));
                                if (factionTaxCut > 0) {
                                    KingSMPMod.dataManager.addSilver(factionKingUuid, factionTaxCut);
                                    proceedsAfterFee -= factionTaxCut;
                                }
                            }
                        }
                    }

                    // 3. Deposit net proceeds to seller's virtual bank
                    KingSMPMod.dataManager.addSilver(listing.getSellerId(), proceedsAfterFee);

                    // Grant Merchant XP to seller
                    net.minecraft.server.MinecraftServer server = player.level().getServer();
                    if (server != null) {
                        ServerPlayer seller = server.getPlayerList().getPlayer(listing.getSellerId());
                        if (seller != null) {
                            net.kingsmp.professions.ProfessionManager.addXp(seller, net.kingsmp.professions.ProfessionType.MERCHANT, Math.max(10, basePrice / 10));
                            seller.sendSystemMessage(
                                    Component.literal("💰 " + player.getScoreboardName() + " bought your item! +"
                                            + proceedsAfterFee + " Silver (Market Fee: -" + marketFee + " 🪙"
                                            + (factionTaxCut > 0 ? ", Faction Tax: -" + factionTaxCut + " 🪙" : "") + ")")
                                            .withStyle(ChatFormatting.GREEN));
                            KingSMPMod.playSoundToPlayer(seller, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                    }
                    // Grant Merchant XP to buyer
                    if (player instanceof ServerPlayer serverBuyer) {
                        net.kingsmp.professions.ProfessionManager.addXp(serverBuyer, net.kingsmp.professions.ProfessionType.MERCHANT, Math.max(5, finalPrice / 20));
                    }
                }
                refreshShop();
            } else {
                player.sendOverlayMessage(Component.literal("Not enough Silver! You need " + finalPrice + " 🪙.").withStyle(ChatFormatting.RED));
                KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    private int getItemTypeScore(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (stack.isDamageableItem()) {
            if (path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots")) {
                return 1; // Armor
            }
            if (path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_pickaxe") || path.endsWith("_shovel") || path.endsWith("_hoe") || path.equals("shield") || path.equals("trident") || path.equals("mace") || path.equals("bow") || path.equals("crossbow")) {
                return 2; // Tools & Weapons
            }
        }
        if (stack.has(DataComponents.FOOD)) {
            return 3; // Food
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
            return 4; // Blocks
        }
        return 5; // Misc
    }
}
