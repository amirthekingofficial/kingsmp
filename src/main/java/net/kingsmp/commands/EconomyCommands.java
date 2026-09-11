package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.kingsmp.events.CombatTracker;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.shop.ShopScreenHandler;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;

import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandBuildContext;




public class EconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
            KingDataManager dataManager) {

        // ── 1. /shop — Opens the Global Market GUI ──
        dispatcher.register(Commands.literal("shop")
                .executes(ctx -> cmdShopView(ctx))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdShopSellMarket(ctx)))));

        // ── 2. /market cancel <id> ──
        dispatcher.register(Commands.literal("market")
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (CombatTracker.isInCombat(player)) {
                                        player.sendSystemMessage(
                                                Component.literal("You cannot cancel listings while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)")
                                                        .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    long id = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id");

                                    net.kingsmp.shop.MarketListing listing = null;
                                    for (net.kingsmp.shop.MarketListing ml : KingSMPMod.marketManager.getActiveListings()) {
                                        if (ml.getId() == id) {
                                            listing = ml;
                                            break;
                                        }
                                    }

                                    if (listing == null) {
                                        player.sendSystemMessage(
                                                Component.literal("Listing not found!").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    if (!listing.getSellerId().equals(player.getUUID())) {
                                        player.sendSystemMessage(
                                                Component.literal("You can only cancel your own listings!")
                                                        .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    if (!player.getInventory().add(listing.getItemToSell().copy())) {
                                        player.sendSystemMessage(Component
                                                .literal("Your inventory is full! Clear space to reclaim your item.")
                                                .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    KingSMPMod.marketManager.removeListing(listing);
                                    KingSMPMod.saveNow();
                                    player.sendSystemMessage(
                                            Component.literal("📦 Listing cancelled. Item returned to your inventory.")
                                                    .withStyle(ChatFormatting.YELLOW));
                                    return 1;
                                }))));

        // ── 3. /sell — Sells held item for Silver ──
        dispatcher.register(Commands.literal("sell")
                .executes(ctx -> cmdBankSell(ctx, dataManager)));

        // ── 4. /price — Check what the Bank will pay for your item ──
        dispatcher.register(Commands.literal("price")
                .executes(ctx -> cmdShopPrice(ctx)));

        // ── 5. THE BOUNTY BOARD (/bounty) ──
        dispatcher.register(Commands.literal("bounty")
                .then(Commands.literal("add")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(50))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            if (CombatTracker.isInCombat(player)) {
                                                player.sendSystemMessage(
                                                        Component.literal("You cannot place bounties while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)")
                                                                .withStyle(ChatFormatting.RED));
                                                return 0;
                                            }
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                            if (dataManager.getSilver(player.getUUID()) < amount) {
                                                player.sendSystemMessage(Component
                                                        .literal("You don't have enough Silver to place this bounty!")
                                                        .withStyle(ChatFormatting.RED));
                                                return 0;
                                            }

                                            dataManager.removeSilver(player.getUUID(), amount);
                                            dataManager.addBounty(target.getUUID(), amount);

                                            KingSMPMod.saveNow();

                                            int newTotalBounty = dataManager.getBounty(target.getUUID());

                                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                                    Component.literal("☠️ " + player.getScoreboardName()
                                                            + " placed a bounty! " + target.getScoreboardName()
                                                            + " is now wanted for " + newTotalBounty + " Silver!")
                                                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                                                    false);
                                            return 1;
                                        }))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            try {
                                ServerPlayer player = src.getPlayerOrException();
                                if (CombatTracker.isInCombat(player)) {
                                    src.sendFailure(Component.literal("You cannot view active bounties while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
                                    return 0;
                                }
                            } catch (Exception e) {}
                            Map<UUID, Integer> bounties = dataManager.getAllBounties();

                            if (bounties.isEmpty()) {
                                src.sendSuccess(() -> Component.literal("There are currently no active bounties.")
                                        .withStyle(ChatFormatting.GRAY), false);
                                return 1;
                            }

                            src.sendSuccess(() -> Component.literal("━━━ ☠️ WANTED POSTERS ☠️ ━━━")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);

                            bounties.forEach((uuid, amount) -> {
                                ServerPlayer wantedPlayer = src.getServer().getPlayerList().getPlayer(uuid);
                                String name = wantedPlayer != null ? wantedPlayer.getScoreboardName()
                                        : "Offline Player";

                                src.sendSuccess(
                                        () -> Component.literal("➤ " + name + " — ").withStyle(ChatFormatting.RED)
                                                .append(Component.literal(amount + " 🪙 Silver")
                                                        .withStyle(ChatFormatting.GOLD)),
                                        false);
                            });

                            return 1;
                        })));

        // ── 7. /pay <player> <amount> — Gives/transfers Silver ──
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                    ServerPlayer receiver = EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                    if (sender.getUUID().equals(receiver.getUUID())) {
                                        sender.sendSystemMessage(Component.literal("You cannot pay yourself!").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    int senderBal = dataManager.getSilver(sender.getUUID());
                                    if (senderBal < amount) {
                                        sender.sendSystemMessage(Component.literal("You do not have enough Silver!").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    dataManager.removeSilver(sender.getUUID(), amount, false);
                                    dataManager.addSilver(receiver.getUUID(), amount);
                                    KingSMPMod.saveNow();

                                    sender.sendSystemMessage(Component.literal("Sent " + amount + " 🪙 Silver to " + receiver.getScoreboardName()).withStyle(ChatFormatting.GREEN));
                                    receiver.sendSystemMessage(Component.literal("Received " + amount + " 🪙 Silver from " + sender.getScoreboardName()).withStyle(ChatFormatting.GREEN));

                                    return 1;
                                }))));
    }

    // ── SHOP COMMAND LOGIC ──────────────────────────────────────────────────

    private static int cmdShopView(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        if (CombatTracker.isInCombat(player)) {
            ctx.getSource().sendFailure(Component.literal("You cannot open the shop while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
            return 0;
        }

        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, p) -> new ShopScreenHandler(syncId, playerInv),
                Component.literal("Global Market").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)));
        return 1;
    }

    private static int cmdShopSellMarket(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        if (CombatTracker.isInCombat(player)) {
            player.sendSystemMessage(
                    Component.literal("You cannot sell items on the market while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
        int price = IntegerArgumentType.getInteger(ctx, "price");
        ItemStack handItem = player.getMainHandItem();

        if (handItem.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("You must be holding an item to sell it!")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (KingSMPMod.isCrown(handItem)) {
            player.sendSystemMessage(
                    Component.literal("The Crown is sacred and cannot be sold!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            return 0;
        }

        long listingId = System.currentTimeMillis();
        net.kingsmp.shop.MarketListing listing = new net.kingsmp.shop.MarketListing(
                listingId, player.getUUID(), player.getScoreboardName(), handItem.copy(),
                price, false);

        KingSMPMod.marketManager.addListing(listing);

        player.sendSystemMessage(
                Component.literal("⚖️ Successfully listed your item for " + price + " Silver!")
                        .withStyle(ChatFormatting.GREEN));

        player.sendSystemMessage(
                Component
                        .literal("To cancel this listing, type: /market cancel "
                                + listingId)
                        .withStyle(ChatFormatting.GRAY));

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        KingSMPMod.saveNow();
        return 1;
    }

    private static int cmdBankSell(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (CombatTracker.isInCombat(p)) {
            p.sendSystemMessage(Component.literal("You cannot sell items while in combat! (" + CombatTracker.getRemainingSeconds(p) + "s remaining)").withStyle(ChatFormatting.RED));
            return 0;
        }

        p.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, player) -> new net.kingsmp.economy.SellScreenHandler(syncId, playerInv),
                Component.literal("Drop items here to sell!").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
        ));

        return 1;
    }

    private static int cmdShopPrice(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (CombatTracker.isInCombat(p)) {
            p.sendSystemMessage(Component.literal("You cannot check prices while in combat! (" + CombatTracker.getRemainingSeconds(p) + "s remaining)").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack hand = p.getMainHandItem();

        if (hand.isEmpty()) {
            p.sendSystemMessage(
                    Component.literal("You must hold an item to check its price!").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (KingSMPMod.isCrown(hand)) {
            p.sendSystemMessage(Component.literal("👑 The Crown is priceless!").withStyle(ChatFormatting.GOLD,
                    ChatFormatting.BOLD));
            return 0;
        }

        if (hand.is(net.minecraft.world.item.Items.DRAGON_EGG)) {
            p.sendSystemMessage(Component.literal("🥚 The Dragon Egg is priceless!").withStyle(ChatFormatting.DARK_PURPLE,
                    ChatFormatting.BOLD));
            return 0;
        }

        double pricePerItem = calculateItemValue(hand);
        Component rarityText = getRarityText(hand);

        p.sendSystemMessage(Component.literal("━━━ 🔍 Item Appraisal ━━━").withStyle(ChatFormatting.DARK_AQUA,
                ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Item: ").withStyle(ChatFormatting.GRAY).append(hand.getHoverName()));
        p.sendSystemMessage(Component.literal("Rarity: ").withStyle(ChatFormatting.GRAY).append(rarityText));

        if (pricePerItem >= 1.0) {
            p.sendSystemMessage(Component.literal("Bank Value: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal((int) pricePerItem + " 🪙 Silver each").withStyle(ChatFormatting.GREEN)));
        } else {
            int bulkAmount = (int) Math.ceil(1.0 / pricePerItem);
            p.sendSystemMessage(Component.literal("Bank Value: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("1 🪙 Silver per " + bulkAmount + " items")
                            .withStyle(ChatFormatting.YELLOW)));
        }

        return 1;
    }
    // ── HELPERS & PRICING ─────────────────────────────────────────────────────

    private static final java.util.Map<net.minecraft.world.item.Item, Double> CUSTOM_PRICES = new java.util.HashMap<>();

    static {
        // Blocks of resources
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.NETHERITE_BLOCK, 900.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.NETHERITE_INGOT, 100.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.ANCIENT_DEBRIS, 25.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.DIAMOND_BLOCK, 18.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.DIAMOND, 2.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.EMERALD_BLOCK, 9.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.EMERALD, 1.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.GOLD_BLOCK, 9.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.GOLD_INGOT, 1.0);
        
        // Rare & Unique drops
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.DRAGON_EGG, 0.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.ELYTRA, 600.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.MACE, 800.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.HEAVY_CORE, 500.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.BEACON, 350.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.NETHER_STAR, 250.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.DRAGON_HEAD, 100.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.CONDUIT, 200.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.HEART_OF_THE_SEA, 150.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 150.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 100.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.TRIDENT, 100.0);
        
        // Trial Chamber items
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.OMINOUS_BOTTLE, 15.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.OMINOUS_TRIAL_KEY, 15.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.TRIAL_KEY, 5.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.BREEZE_ROD, 3.0);
        
        // Consumables & Mob Drops
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 60.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.GOLDEN_APPLE, 4.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL, 20.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.SHULKER_SHELL, 10.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.GHAST_TEAR, 5.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.PHANTOM_MEMBRANE, 2.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.SLIME_BALL, 1.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.SPONGE, 5.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.WET_SPONGE, 5.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.OBSIDIAN, 1.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 0.3);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.ECHO_SHARD, 7.5);
        
        // Shulker Boxes (all colors)
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.SHULKER_BOX, 25.0);
        for (net.minecraft.world.item.Item box : net.minecraft.world.item.Items.DYED_SHULKER_BOX.asList()) {
            CUSTOM_PRICES.put(box, 25.0);
        }
        
        // Amethyst blocks & shards
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.BUDDING_AMETHYST, 15.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.AMETHYST_BLOCK, 2.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.AMETHYST_SHARD, 0.5);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.PRISMARINE_SHARD, 0.5);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.PRISMARINE_CRYSTALS, 0.5);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.SPYGLASS, 10.0);
        CUSTOM_PRICES.put(net.minecraft.world.item.Items.GOAT_HORN, 10.0);
    }

    // ── PRICING LOGIC (Using Doubles for Bulk Pricing) ──
    private static final java.util.Map<net.minecraft.world.item.Item, java.lang.Double> ITEM_VALUE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static double getServerShopBuyPrice(net.minecraft.world.item.Item item) {
        double minPrice = Double.MAX_VALUE;
        for (net.kingsmp.shop.MarketListing listing : net.kingsmp.KingSMPMod.marketManager.getActiveListings()) {
            if (listing.isServerShop() && listing.getItemToSell().is(item)) {
                double pricePerItem = (double) listing.getSilverPrice() / listing.getItemToSell().getCount();
                if (pricePerItem < minPrice) {
                    minPrice = pricePerItem;
                }
            }
        }
        return minPrice;
    }

    public static double calculateItemValue(ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        double baseValue = 0.0;

        // 1. Check Custom Prices Map
        if (CUSTOM_PRICES.containsKey(item)) {
            baseValue = CUSTOM_PRICES.get(item);
        } else if (ITEM_VALUE_CACHE.containsKey(item)) {
            // 2. Check value cache
            double cachedBase = ITEM_VALUE_CACHE.get(item);
            baseValue = applyDynamicModifiers(stack, cachedBase);
        } else {
            // Check if item is a tool, weapon, or armor dynamically...
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            boolean isToolOrArmor = false;
            double tierBaseValue = 0.0;

            if (itemName.endsWith("_sword") || itemName.endsWith("_pickaxe") || itemName.endsWith("_axe") ||
                itemName.endsWith("_shovel") || itemName.endsWith("_hoe") || itemName.endsWith("_helmet") ||
                itemName.endsWith("_chestplate") || itemName.endsWith("_leggings") || itemName.endsWith("_boots") ||
                itemName.equals("shield")) {
                
                isToolOrArmor = true;
                if (itemName.startsWith("wooden_") || itemName.startsWith("leather_") || itemName.startsWith("golden_")) {
                    tierBaseValue = 5.0;
                } else if (itemName.startsWith("stone_")) {
                    tierBaseValue = 10.0;
                } else if (itemName.startsWith("chainmail_")) {
                    tierBaseValue = 12.0;
                } else if (itemName.startsWith("iron_") || itemName.equals("shield")) {
                    tierBaseValue = 20.0;
                } else if (itemName.startsWith("diamond_")) {
                    tierBaseValue = 100.0;
                } else if (itemName.startsWith("netherite_")) {
                    tierBaseValue = 300.0;
                } else {
                    tierBaseValue = 10.0; // fallback tool/armor base
                }
            }

            if (isToolOrArmor) {
                ITEM_VALUE_CACHE.put(item, tierBaseValue);
                baseValue = applyDynamicModifiers(stack, tierBaseValue);
            } else if (itemName.contains("music_disc")) {
                ITEM_VALUE_CACHE.put(item, 50.0);
                baseValue = 50.0;
            } else if (itemName.contains("trim_smithing_template")) {
                ITEM_VALUE_CACHE.put(item, 30.0);
                baseValue = 30.0;
            } else if (itemName.contains("log") || itemName.contains("wood") || itemName.contains("quartz")) {
                double v = 1.0 / 16.0;
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            } else if (itemName.contains("iron_ingot") || itemName.contains("copper_ingot") || itemName.contains("coal")) {
                double v = 1.0 / 32.0;
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            } else if (itemName.contains("glass") || itemName.contains("terracotta") || itemName.contains("brick")) {
                double v = 1.0 / 32.0;
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            } else if (itemName.contains("leaves") || itemName.contains("sapling") || itemName.contains("seed")) {
                double v = 1.0 / 32.0;
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            } else if (itemName.contains("dirt") || itemName.contains("sand") || itemName.contains("gravel") ||
                    itemName.contains("cobblestone") || itemName.contains("stone") || itemName.contains("planks") ||
                    itemName.contains("netherrack") || itemName.contains("end_stone") || itemName.contains("basalt") ||
                    itemName.contains("deepslate") || itemName.contains("andesite") || itemName.contains("diorite") ||
                    itemName.contains("granite") || itemName.contains("tuff") || itemName.contains("ice")) {
                double v = 1.0 / 64.0;
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            } else {
                net.minecraft.world.item.Rarity rarity = stack.getOrDefault(net.minecraft.core.component.DataComponents.RARITY,
                        net.minecraft.world.item.Rarity.COMMON);
                double v = switch (rarity) {
                    case EPIC -> 500.0;
                    case RARE -> 50.0;
                    case UNCOMMON -> 5.0;
                    case COMMON -> 0.1; // 1 Silver per 10 items for anything else in the game!
                };
                ITEM_VALUE_CACHE.put(item, v);
                baseValue = v;
            }
        }

        // Apply fail-safe cap: Sell price must never exceed 50% of the Server Shop's buy price.
        double buyPrice = getServerShopBuyPrice(item);
        if (buyPrice != Double.MAX_VALUE) {
            double maxAllowedSell = buyPrice * 0.5;
            if (baseValue > maxAllowedSell) {
                baseValue = maxAllowedSell;
            }
        }

        return Math.max(0.0, baseValue);
    }


    private static double applyDynamicModifiers(ItemStack stack, double baseValue) {
        double value = baseValue;
        
        // Add $15.0 per enchantment level
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        int totalEnchantmentLevels = 0;
        for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, java.lang.Integer> entry : enchantments.entrySet()) {
            totalEnchantmentLevels += entry.getValue();
        }
        value += totalEnchantmentLevels * 15.0;
        
        // Scale by durability ratio
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            double durabilityRatio = (double) (maxDamage - currentDamage) / maxDamage;
            value *= durabilityRatio;
        }
        
        return Math.max(0.0, value);
    }

    public static Component getRarityText(ItemStack stack) {
        net.minecraft.world.item.Rarity rarity = stack.getOrDefault(net.minecraft.core.component.DataComponents.RARITY,
                net.minecraft.world.item.Rarity.COMMON);
        return switch (rarity) {
            case EPIC -> Component.literal("Epic").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            case RARE -> Component.literal("Rare").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case UNCOMMON -> Component.literal("Uncommon").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            case COMMON -> Component.literal("Common").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        };
    }


}