package net.kingsmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kingsmp.commands.KingCommands;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.election.ElectionManager;
import net.minecraft.server.level.ServerPlayer;
import net.kingsmp.events.PlayerEventHandler;
import net.kingsmp.shop.MarketListing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
// import java.util.Optional;
import net.minecraft.world.item.component.CustomModelData;

public class KingSMPMod implements ModInitializer {

    public static final String MOD_ID = "kingsmp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final int MAX_KINGS = 5;

    // Singleton managers
    public static KingDataManager dataManager;
    public static ElectionManager electionManager;
    public static net.kingsmp.shop.MarketManager marketManager;

    // Server reference (set from SERVER_STARTED event)
    public static net.minecraft.server.MinecraftServer server;

    private static long lastSaveTime = 0L;
    private static boolean savePending = false;

    /** Call after any important data mutation to persist safely. */
    public static void saveNow() {
        if (server == null)
            return;

        long now = System.currentTimeMillis();
        if (now - lastSaveTime > 15000L) {
            // Immediate save if it's been more than 15 seconds
            try {
                dataManager.saveToDisk(server);
                lastSaveTime = now;
                savePending = false;
            } catch (Exception e) {
                LOGGER.error("Failed to save data immediately", e);
            }
        } else if (!savePending) {
            savePending = true;
            // Schedule async deferred save and dispatch onto main server thread safely
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(Math.max(1000L, 15000L - (System.currentTimeMillis() - lastSaveTime)));
                    if (server != null) {
                        server.execute(() -> {
                            synchronized (KingSMPMod.class) {
                                if (savePending && server != null) {
                                    dataManager.saveToDisk(server);
                                    lastSaveTime = System.currentTimeMillis();
                                    savePending = false;
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to execute deferred save", e);
                }
            });
        }
    }

    // Memory tracker to prevent the election from triggering constantly during the
    // 250th day
    private static long lastElectionDay = -1;

    @Override
    public void onInitialize() {
        LOGGER.info("KingSMP initializing (Server-Side Mode)...");

        // Initialize managers
        dataManager = new KingDataManager();
        electionManager = new ElectionManager(dataManager);
        marketManager = new net.kingsmp.shop.MarketManager();

        // loadServerShop();
        // loadSpawnerShop();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            KingCommands.register(dispatcher, registryAccess, dataManager, electionManager);

            net.kingsmp.commands.FactionCommands.register(dispatcher);
            net.kingsmp.commands.EconomyCommands.register(dispatcher, registryAccess, dataManager);
            net.kingsmp.commands.HomeCommands.register(dispatcher, dataManager);
            net.kingsmp.commands.TpaCommands.register(dispatcher, dataManager);
            net.kingsmp.commands.EnderChestCommand.register(dispatcher, dataManager);
            net.kingsmp.commands.RtpCommand.register(dispatcher);
            net.kingsmp.commands.QuestCommand.register(dispatcher);
            net.kingsmp.commands.RankCommands.register(dispatcher);
            net.kingsmp.commands.ProfessionCommands.register(dispatcher);
            net.kingsmp.commands.WhoCommand.register(dispatcher, dataManager);
        });

        // Register block break event listener
        net.kingsmp.events.PlayerBlockBreakHook.register();

        // ── BOUNTY CLAIM & HUNTING QUESTS EVENT ──
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY
                .register((net.minecraft.server.level.ServerLevel world, net.minecraft.world.entity.Entity entity,
                        net.minecraft.world.entity.LivingEntity killedEntity,
                        net.minecraft.world.damagesource.DamageSource damageSource) -> {
                    if (entity instanceof net.minecraft.server.level.ServerPlayer killer) {

                        // 1. Bounty Claim Logic
                        if (killedEntity instanceof net.minecraft.server.level.ServerPlayer victim) {
                            int bounty = dataManager.getBounty(victim.getUUID());
                            if (bounty > 0) {
                                dataManager.addSilver(killer.getUUID(), bounty);
                                dataManager.clearBounty(victim.getUUID());

                                net.minecraft.server.MinecraftServer server = world.getServer();

                                if (server != null) {
                                    server.getPlayerList().broadcastSystemMessage(
                                            net.minecraft.network.chat.Component.literal("⚔️ ")
                                                    .append(killer.getName())
                                                    .append(net.minecraft.network.chat.Component
                                                            .literal(
                                                                    " has claimed the " + bounty + " Silver bounty on ")
                                                            .withStyle(net.minecraft.ChatFormatting.GOLD))
                                                    .append(victim.getName())
                                                    .append(net.minecraft.network.chat.Component.literal("!")
                                                            .withStyle(net.minecraft.ChatFormatting.GOLD))
                                                    .withStyle(net.minecraft.ChatFormatting.BOLD),
                                            false);
                                }

                                // Persist immediately — silver changed hands
                                saveNow();
                            }
                        }

                        // 2. Quest Hunting Progress
                        net.kingsmp.data.KingDataManager.ActiveQuest quest = dataManager
                                .getActiveQuest(killer.getUUID());
                        if (quest != null && quest.type.equals("hunting")) {
                            String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                    .getKey(killedEntity.getType()).getPath();
                            if (entityId.equals(quest.targetId)) {
                                synchronized (quest) {
                                    if (dataManager.getActiveQuest(killer.getUUID()) == null) {
                                        return;
                                    }
                                    quest.progress++;
                                    if (quest.progress >= quest.target) {
                                        net.kingsmp.events.PlayerBlockBreakHook.completeQuest(killer, quest);
                                    } else {
                                        killer.sendSystemMessage(
                                                net.minecraft.network.chat.Component.literal("🏹 Quest Progress: ")
                                                        .withStyle(net.minecraft.ChatFormatting.GRAY)
                                                        .append(net.minecraft.network.chat.Component
                                                                .literal(quest.progress + "/" + quest.target)
                                                                .withStyle(net.minecraft.ChatFormatting.YELLOW))
                                                        .append(" mob kills."));
                                        saveNow();
                                    }
                                }
                            }
                        }
                    }
                });

        // Prevent placing the Crown as a block
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isCrown(stack)) {
                return net.minecraft.world.InteractionResult.FAIL;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // ── ENDER CHEST PHYSICAL BLOCK INTERACTION ──
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                net.minecraft.world.level.block.state.BlockState state = world.getBlockState(hitResult.getBlockPos());
                if (state.is(net.minecraft.world.level.block.Blocks.ENDER_CHEST)) {
                    if (dataManager.hasUnlockedEnderChest(serverPlayer.getUUID())
                            && dataManager.hasUnlockedLargeEnderChest(serverPlayer.getUUID())) {
                        net.kingsmp.commands.EnderChestCommand.openLargeEnderChest(serverPlayer, dataManager);
                        return net.minecraft.world.InteractionResult.SUCCESS;
                    }
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // ── SPAWNER LOOT CACHE EVENTS ──
        // 1. Prevent placing the spawner on a block and open GUI instead
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isLootSpawner(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    int tier = getSpawnerTier(stack);
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (syncId, playerInv, p) -> new net.kingsmp.shop.SpawnerScreenHandler(syncId, playerInv, tier,
                                    hand),
                            Component.literal("Select Mob - Tier " + tier).withStyle(ChatFormatting.DARK_PURPLE,
                                    ChatFormatting.BOLD)));
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            if (isGambleSpawner(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    String type = getGambleType(stack);
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (syncId, playerInv, p) -> new net.kingsmp.shop.GambleScreenHandler(syncId, playerInv, type,
                                    hand),
                            Component.literal("Loot Gamble - " + type.toUpperCase()).withStyle(ChatFormatting.DARK_RED,
                                    ChatFormatting.BOLD)));
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 2. Handle using the spawner while clicking the air
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isLootSpawner(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    int tier = getSpawnerTier(stack);
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (syncId, playerInv, p) -> new net.kingsmp.shop.SpawnerScreenHandler(syncId, playerInv, tier,
                                    hand),
                            Component.literal("Select Mob - Tier " + tier).withStyle(ChatFormatting.DARK_PURPLE,
                                    ChatFormatting.BOLD)));
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            if (isGambleSpawner(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    String type = getGambleType(stack);
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (syncId, playerInv, p) -> new net.kingsmp.shop.GambleScreenHandler(syncId, playerInv, type,
                                    hand),
                            Component.literal("Loot Gamble - " + type.toUpperCase()).withStyle(ChatFormatting.DARK_RED,
                                    ChatFormatting.BOLD)));
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // Server lifecycle hooks
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            KingSMPMod.server = server;

            // ✅ Load shops here, AFTER registries and components are fully bound!
            loadServerShop();
            loadSpawnerShop();

            dataManager.loadFromDisk(server);
            electionManager.setServer(server);
            LOGGER.info("KingSMP data loaded. Current kings: {}", dataManager.getKingCount());

            // ── DIAGNOSTIC PRINTS FOR GAMBLE SPAWNERS ──
            LOGGER.info("── GAMBLE SPAWNER DIAGNOSTICS START ──");
            String[] types = { "overworld", "nether", "end", "ore", "trial" };
            for (String t : types) {
                ItemStack spawner = createGambleSpawner(t);
                boolean isGamble = isGambleSpawner(spawner);
                String readType = getGambleType(spawner);
                LOGGER.info("GambleType: input='" + t + "', isGamble=" + isGamble + ", readType='" + readType + "'");
            }
            LOGGER.info("── GAMBLE SPAWNER DIAGNOSTICS END ──");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            dataManager.saveToDisk(server);
            KingSMPMod.server = null;
            LOGGER.info("KingSMP data saved.");
        });

        // Tick handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            net.kingsmp.shop.GambleScreenHandler.tickActiveGambles(server);

            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (net.kingsmp.events.CombatTracker.isInCombat(player)) {
                        long remainingSeconds = net.kingsmp.events.CombatTracker.getRemainingSeconds(player);
                        player.sendSystemMessage(
                                Component.literal("⚔️ IN COMBAT: " + remainingSeconds + "s remaining ⚔️")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                                true);
                    }
                }
            }

            if (server.getTickCount() % 40 == 0) {
                PlayerEventHandler.applyKingEffects(server, dataManager);
            }

            // Auto-save every 5 minutes (6000 ticks) to prevent data loss on crash
            if (server.getTickCount() % 6000 == 0 && server.getTickCount() > 0) {
                dataManager.saveToDisk(server);
                LOGGER.info("KingSMP auto-save complete.");
            }

            electionManager.tick(server);

            net.minecraft.server.level.ServerLevel overworld = server
                    .getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld != null) {
                long currentDay = overworld.getOverworldClockTime() / 24000;

                if (currentDay > 0 && currentDay % 250 == 0) {
                    if (lastElectionDay != currentDay) {
                        lastElectionDay = currentDay;

                        if (electionManager.getPhase() == ElectionManager.Phase.IDLE) {
                            electionManager.startElection(ElectionManager.DEFAULT_VOTE_DURATION_TICKS);
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal(
                                            "🌍 250 Minecraft Days have passed! A new Election has automatically begun!")
                                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                                    false);
                        }
                    }
                }
            }

            // Tick teleport channels
            net.kingsmp.events.TeleportManager.tick(server);
        });

        // Player join/leave
        ServerPlayConnectionEvents.JOIN
                .register((handler, sender, server) -> PlayerEventHandler.onPlayerJoin(handler.player, dataManager));

        ServerPlayConnectionEvents.DISCONNECT
                .register((handler, server) -> {
                    net.kingsmp.events.TeleportManager.cancelChannel(handler.player.getUUID(), "Disconnected");
                    PlayerEventHandler.onPlayerLeave(handler.player, dataManager);
                });

        // PvP Combat Tagging & Teleport Interruption Event
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE
                .register((entity, source, amount) -> {
                    if (entity instanceof ServerPlayer victim) {
                        net.kingsmp.events.TeleportManager.cancelChannel(victim, "Damage taken!");
                        if (source.getEntity() instanceof ServerPlayer attacker) {
                            if (!victim.getUUID().equals(attacker.getUUID())) {
                                net.kingsmp.events.CombatTracker.tag(victim, attacker);
                                net.kingsmp.events.TeleportManager.cancelChannel(attacker, "Entered combat!");
                            }
                        }
                    }
                    return true;
                });

        LOGGER.info("KingSMP initialized successfully!");
    }

    // ── SERVER-SIDED CROWN LOGIC ────────────────────────────────────────────

    public static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "crown_health");

    public static ItemStack createCrown() {
        return createCrown(net.kingsmp.crowns.CrownType.GOLD, 1);
    }

    public static ItemStack createCrown(net.kingsmp.crowns.CrownType type, int step) {
        ItemStack crown = new ItemStack(Items.CARVED_PUMPKIN);
        crown.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        crown.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);

        setCrownData(crown, type, step);
        return crown;
    }

    public static boolean isCrown(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains("IsKingSMPCrown");
    }

    public static net.kingsmp.crowns.CrownType getCrownType(ItemStack crown) {
        if (!isCrown(crown))
            return net.kingsmp.crowns.CrownType.GOLD;
        CustomData customData = crown.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("CrownType")) {
            return net.kingsmp.crowns.CrownType.fromString(customData.copyTag().getString("CrownType").orElse("GOLD"));
        }
        return net.kingsmp.crowns.CrownType.GOLD;
    }

    public static int getCrownStep(ItemStack crown) {
        if (!isCrown(crown))
            return 1;
        CustomData customData = crown.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("CrownStep")) {
                return Math.max(1, Math.min(3, tag.getInt("CrownStep").orElse(1)));
            } else if (tag.contains("CrownLevel")) { // backward compatibility
                int oldLevel = tag.getInt("CrownLevel").orElse(1);
                return Math.max(1, Math.min(3, (oldLevel + 1) / 2));
            }
        }
        return 1;
    }

    // Backward-compatible alias for getCrownStep
    public static int getCrownLevel(ItemStack crown) {
        return getCrownStep(crown);
    }

    public static void setCrownStep(ItemStack crown, int step) {
        setCrownData(crown, getCrownType(crown), step);
    }

    public static void setCrownLevel(ItemStack crown, int level) {
        setCrownStep(crown, Math.max(1, Math.min(3, level)));
    }

    public static void setCrownData(ItemStack crown, net.kingsmp.crowns.CrownType type, int step) {
        step = Math.max(1, Math.min(3, step));

        CustomData customData = crown.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();
        nbt.putBoolean("IsKingSMPCrown", true);
        nbt.putString("CrownType", type.name());
        nbt.putInt("CrownStep", step);
        nbt.putInt("CrownLevel", step); // backward compat
        crown.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        // 1. Custom Name and Lore
        String stepRoman = switch (step) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
        crown.set(DataComponents.CUSTOM_NAME,
                Component.literal("👑 " + type.getDisplayName() + " [" + stepRoman + "]")
                        .withStyle(type.getColor(), ChatFormatting.BOLD));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("Throne: " + type.getTitle()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Upgrade Step: " + step + "/3").withStyle(ChatFormatting.DARK_GRAY));
        if (step >= 2) {
            lore.add(Component.literal("✦ Signature Ability Unlocked").withStyle(ChatFormatting.YELLOW));
        }
        if (step >= 3) {
            lore.add(Component.literal("✦ 30-Block Faction Aura Active").withStyle(ChatFormatting.GOLD));
        }
        crown.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        // 2. Map CMD values to match carved_pumpkin.json
        float cmdValue = type.getCustomModelData();
        crown.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                java.util.List.of(cmdValue), java.util.List.of(), java.util.List.of(), java.util.List.of()));

        // 3. Remove pumpkin blur effect
        Equippable customEquip = Equippable.builder(EquipmentSlot.HEAD)
                .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GOLD)
                .build();
        crown.set(DataComponents.EQUIPPABLE, customEquip);

        // 4. Balanced base armor attributes (Step 1 -> 2.0 armor, Step 2 -> 3.0 armor, Step 3 -> 4.0 armor)
        ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
        double armor = 2.0 + (step - 1) * 1.0;
        double toughness = (step >= 2) ? 1.0 : 0.0;

        modifiers.add(Attributes.ARMOR,
                new AttributeModifier(Identifier.fromNamespaceAndPath(MOD_ID, "crown_armor"), armor,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.HEAD);

        if (toughness > 0) {
            modifiers.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(Identifier.fromNamespaceAndPath(MOD_ID, "crown_toughness"), toughness,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.HEAD);
        }

        crown.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
    }

    // ── PREMADE SERVER SHOP ─────────────────────────────────────────────────

    public static void loadServerShop() {
        java.util.UUID serverId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");

        // --- CATEGORY 1: ARTIFACTS / HIGH-TIER ---
        marketManager.addListing(
                new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.TOTEM_OF_UNDYING), 250, true));
        marketManager.addListing(
                new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), 120, true));
        marketManager
                .addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.GOLDEN_APPLE, 8), 64, true));

        // --- CATEGORY 2: MATERIALS ---
        marketManager
                .addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.ECHO_SHARD, 4), 60, true));
        marketManager.addListing(
                new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.OMINOUS_BOTTLE, 1), 40, true));

        // --- CATEGORY 3: PVP & MOVEMENT ---
        marketManager
                .addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.WIND_CHARGE, 16), 16, true));
        marketManager
                .addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.ENDER_PEARL, 16), 16, true));
        marketManager.addListing(
                new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.FIREWORK_ROCKET, 64), 12, true));
        marketManager.addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.COBWEB, 16), 10, true));
        marketManager.addListing(
                new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.EXPERIENCE_BOTTLE, 64), 40, true));
        marketManager
                .addListing(new MarketListing(serverId, "SERVER SHOP", new ItemStack(Items.SHULKER_BOX, 1), 60, true));
    }

    // ── PREMADE SPAWNER SHOP ────────────────────────────────────────────────

    public static void loadSpawnerShop() {
        java.util.UUID serverId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        String shopCategory = "SPAWNER SHOP";

        MarketListing tier1 = new MarketListing(serverId, shopCategory, createLootSpawner(1), 100, true);
        MarketListing tier2 = new MarketListing(serverId, shopCategory, createLootSpawner(2), 250, true);
        MarketListing tier3 = new MarketListing(serverId, shopCategory, createLootSpawner(3), 500, true);
        MarketListing tier4 = new MarketListing(serverId, shopCategory, createLootSpawner(4), 750, true);
        MarketListing tier5 = new MarketListing(serverId, shopCategory, createLootSpawner(5), 1000, true);

        // Gamble Spawners (with progressive pricing)
        MarketListing endGamble = new MarketListing(serverId, shopCategory, createGambleSpawner("end"), 1500, true);
        MarketListing overworldGamble = new MarketListing(serverId, shopCategory, createGambleSpawner("overworld"), 600,
                true);
        MarketListing netherGamble = new MarketListing(serverId, shopCategory, createGambleSpawner("nether"), 800,
                true);
        MarketListing oreGamble = new MarketListing(serverId, shopCategory, createGambleSpawner("ore"), 1200, true);
        MarketListing trialGamble = new MarketListing(serverId, shopCategory, createGambleSpawner("trial"), 1000, true);

        marketManager.addListing(tier1);
        marketManager.addListing(tier2);
        marketManager.addListing(tier3);
        marketManager.addListing(tier4);
        marketManager.addListing(tier5);

        marketManager.addListing(endGamble);
        marketManager.addListing(overworldGamble);
        marketManager.addListing(netherGamble);
        marketManager.addListing(oreGamble);
        marketManager.addListing(trialGamble);
    }

    public static ItemStack createLootSpawner(int tier) {
        ItemStack spawner = new ItemStack(Items.SPAWNER);
        spawner.set(DataComponents.CUSTOM_NAME,
                Component.literal("Tier " + tier + " Spawner Loot Cache")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        spawner.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        CustomData customData = spawner.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();
        nbt.putInt("SpawnerTier", tier);
        nbt.putBoolean("IsLootSpawner", true);
        spawner.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("Right-click to open Mob Loot Selection GUI").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Tier " + tier + " gives " + tier + "x loot amount.")
                .withStyle(ChatFormatting.YELLOW));
        lore.add(Component.literal("Also increases luck for rare drops.").withStyle(ChatFormatting.YELLOW));
        spawner.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        return spawner;
    }

    public static boolean isLootSpawner(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains("IsLootSpawner");
    }

    public static int getSpawnerTier(ItemStack stack) {
        if (!isLootSpawner(stack))
            return 1;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("SpawnerTier")) {
            return customData.copyTag().getInt("SpawnerTier").orElse(1);
        }
        return 1;
    }

    public static ItemStack createGambleSpawner(String type) {
        ItemStack spawner = new ItemStack(Items.SPAWNER);
        String title = switch (type.toLowerCase()) {
            case "overworld" -> "Overworld Gamble Spawner";
            case "nether" -> "Nether Gamble Spawner";
            case "end" -> "End Gamble Spawner";
            case "ore" -> "Ore & Ingot Gamble Spawner";
            case "trial" -> "Trial Chamber Gamble Spawner";
            default -> "Gamble Spawner";
        };
        ChatFormatting color = switch (type.toLowerCase()) {
            case "overworld" -> ChatFormatting.GREEN;
            case "nether" -> ChatFormatting.RED;
            case "end" -> ChatFormatting.LIGHT_PURPLE;
            case "ore" -> ChatFormatting.GOLD;
            case "trial" -> ChatFormatting.DARK_AQUA;
            default -> ChatFormatting.YELLOW;
        };
        spawner.set(DataComponents.CUSTOM_NAME,
                Component.literal(title).withStyle(color, ChatFormatting.BOLD));
        spawner.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        CustomData customData = spawner.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();
        nbt.putString("GambleType", type);
        nbt.putBoolean("IsGambleSpawner", true);
        spawner.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        int cost = switch (type.toLowerCase()) {
            case "end" -> 1500;
            case "ore" -> 1200;
            case "trial" -> 1000;
            case "nether" -> 800;
            case "overworld" -> 600;
            default -> 900;
        };

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("Right-click to Roll for Rare Rewards!").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Cost: " + cost + " Silver").withStyle(ChatFormatting.YELLOW));
        spawner.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        return spawner;
    }

    public static boolean isGambleSpawner(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains("IsGambleSpawner");
    }

    public static String getGambleType(ItemStack stack) {
        if (!isGambleSpawner(stack))
            return "";
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("GambleType")) {
            return customData.copyTag().getString("GambleType").orElse("");
        }
        return "";
    }

    public static void playSoundToPlayer(net.minecraft.world.entity.player.Player player,
            net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
    }

    public static void playSoundToPlayer(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume,
            float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
    }

}
