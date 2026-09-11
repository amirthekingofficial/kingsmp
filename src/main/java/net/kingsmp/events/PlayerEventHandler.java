package net.kingsmp.events;

import net.kingsmp.KingSMPMod;
import net.kingsmp.factions.FactionManager;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.commands.TpaCommands;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
//import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
// import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
// import java.util.UUID;
// import java.util.Optional;

/**
 * Handles all player-related logic:
 * - Applying/removing Crown status effects
 * - Checking Crown item presence using Server-Sided NBT tags
 * - Welcome messages on join
 */
public class PlayerEventHandler {

    // Duration for re-applied effects (3 seconds, refreshed every 2 seconds)
    // private static final int EFFECT_DURATION = 60;

    // 4.0 equals exactly 2 extra hearts. A nice, balanced advantage!
    public static final double HEALTH_BONUS = 4.0;

    /**
     * Called every 40 ticks. Applies or refreshes Crown effects for Kings who
     * carry their Crown, and removes them from non-kings / kings without crown.
     */
    public static void applyKingEffects(MinecraftServer server, KingDataManager data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isKing = data.isKing(player.getUUID());
            ItemStack crownStack = net.kingsmp.crowns.CrownManager.getCrownInInventory(player);
            boolean hasCrown = crownStack != null && !crownStack.isEmpty();

            // ── USURPER LOGIC ─────────────────────────────────────────────────
            if (hasCrown && !isKing) {
                if (data.getKingCount() < KingSMPMod.MAX_KINGS) {
                    data.addKing(player.getUUID(), player.getScoreboardName());
                    isKing = true;

                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal(
                                    "👑 " + player.getScoreboardName()
                                            + " has claimed the Crown and is now the King!")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                            false);

                    KingSMPMod.playSoundToPlayer(player, net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
            // ──────────────────────────────────────────────────────────────────

            if (isKing && hasCrown) {
                net.kingsmp.crowns.CrownType crownType = KingSMPMod.getCrownType(crownStack);
                int crownStep = KingSMPMod.getCrownStep(crownStack);
                net.kingsmp.crowns.CrownManager.tickPlayer(player, crownType, crownStep);
                data.addReignTick(player.getUUID());
            } else {
                net.kingsmp.crowns.CrownManager.clearPassives(player);
            }

            enforceEnderChestRules(player);

            // ── FACTION BRANCHING PERKS ──
            FactionManager.RankPath path = FactionManager.getPlayerPath(player);
            int rung = FactionManager.getPlayerRung(player);

            if (path != null) {
                switch (path) {
                    case SCOUT -> {
                        // R1: Speed I, R3: Night Vision
                        if (rung >= 1 && (!isKing || !hasCrown)) {
                            applySafeEffect(player, MobEffects.SPEED, 0);
                        }
                        if (rung >= 3) {
                            applySafeEffect(player, MobEffects.NIGHT_VISION, 0);
                        }
                    }
                    case OCCULT -> {
                        // R1: Fire Resistance
                        if (rung >= 1) {
                            applySafeEffect(player, MobEffects.FIRE_RESISTANCE, 0);
                        }
                    }
                    case MILITARY -> {
                        // R2: Resistance I in claims, R3: Strength I in claims
                        if (rung >= 2 && FactionManager.isInFactionTerritory(player)) {
                            applySafeEffect(player, MobEffects.RESISTANCE, 0);
                        }
                        if (rung >= 3 && FactionManager.isInFactionTerritory(player)) {
                            applySafeEffect(player, MobEffects.STRENGTH, 0);
                        }
                    }
                    case LOGISTICS -> {
                        // Logistics has market perks & extra outposts (handled in events/shop)
                    }
                }
            }
        }
    }

    private static void applySafeEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || (current.isAmbient() && !current.isVisible()) || current.getAmplifier() < amp) {
            player.addEffect(new MobEffectInstance(effect, 100, amp, true, false, true));
        }
    }

    // ── ENDER CHEST RESTRICTIONS ──────────────────────────────────────────────
    public static void enforceEnderChestRules(ServerPlayer player) {
        net.minecraft.world.inventory.PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
        boolean forcedOut = false;

        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);

            if (stack.is(net.minecraft.world.item.Items.DRAGON_EGG) || KingSMPMod.isCrown(stack)) {
                ItemStack itemToReturn = stack.copy();
                enderChest.setItem(i, ItemStack.EMPTY);

                if (!player.getInventory().add(itemToReturn)) {
                    player.drop(itemToReturn, false);
                }
                forcedOut = true;
            }
        }

        if (KingSMPMod.dataManager.hasUnlockedLargeEnderChest(player.getUUID())) {
            java.util.List<ItemStack> items = KingSMPMod.dataManager.getExpandedEnderChest(player.getUUID());
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);

                if (stack.is(net.minecraft.world.item.Items.DRAGON_EGG) || KingSMPMod.isCrown(stack)) {
                    ItemStack itemToReturn = stack.copy();
                    items.set(i, ItemStack.EMPTY);

                    if (!player.getInventory().add(itemToReturn)) {
                        player.drop(itemToReturn, false);
                    }
                    forcedOut = true;
                }
            }
        }

        if (forcedOut) {
            KingSMPMod.saveNow();
            player.sendSystemMessage(Component.literal("An artifact of immense power violently escapes your Ender Chest!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            KingSMPMod.playSoundToPlayer(player, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.0f);
        }
    }

    // ── Join / Leave ──────────────────────────────────────────────────────────

    public static void onPlayerJoin(ServerPlayer player, KingDataManager data) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(
                Component.literal("  Welcome to ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("KingSMP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal("!").withStyle(ChatFormatting.GRAY)));

        // ── MAILBOX CHECK ─────────────────────────────────────────────────────
        java.util.List<ItemStack> pendingMail = KingSMPMod.marketManager.collectMail(player.getUUID());

        if (pendingMail != null && !pendingMail.isEmpty()) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("📬 You have unread mail from the Global Market!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (ItemStack paymentItem : pendingMail) {
                int amount = paymentItem.getCount();
                String name = paymentItem.getHoverName().getString();

                player.getInventory().placeItemBackInInventory(paymentItem);

                player.sendSystemMessage(Component.literal(" + Received " + amount + "x " + name).withStyle(ChatFormatting.GREEN));
            }

            KingSMPMod.playSoundToPlayer(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendSystemMessage(Component.literal(""));
        }

        if (data.getKingCount() > 0) {
            StringBuilder kings = new StringBuilder();
            data.getCurrentKings().values().forEach(name -> {
                if (kings.length() > 0)
                    kings.append(", ");
                kings.append(name);
            });
            player.sendSystemMessage(
                    Component.literal("  👑 Current Kings: ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(kings.toString()).withStyle(ChatFormatting.GOLD)));
        } else {
            player.sendSystemMessage(
                    Component.literal("  No King is currently elected. Watch for an election!")
                            .withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(
                Component.literal("  💬 Join our Discord: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("discord.gg/mckingsmp")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.WHITE)
                                        .withUnderlined(true)
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                                                java.net.URI.create("https://discord.gg/mckingsmp")
                                        ))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                Component.literal("Click to join our Discord server!")
                                        )))));
        net.minecraft.network.chat.Component fullTitle = net.kingsmp.util.TitleFormatter.formatFullTitle(player);
        player.sendSystemMessage(Component.literal("  🎖 Citizen Standing: ").withStyle(ChatFormatting.YELLOW).append(fullTitle));

        player.sendSystemMessage(
                Component.literal("  📖 Use ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("/king help").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                        .append(Component.literal(" to learn how to use every command!").withStyle(ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal(""));

        if (data.isKing(player.getUUID())) {
            ItemStack crown = net.kingsmp.crowns.CrownManager.getCrownInInventory(player);
            if (crown == null || crown.isEmpty()) {
                net.kingsmp.crowns.CrownType type = net.kingsmp.crowns.CrownType.SKULLS;
                if (net.kingsmp.factions.FactionManager.isFactionLeader(player)) {
                    type = net.kingsmp.crowns.CrownType.END;
                } else {
                    var path = net.kingsmp.factions.FactionManager.getPlayerPath(player);
                    if (path != null) {
                        type = switch (path) {
                            case MILITARY -> net.kingsmp.crowns.CrownType.SKULLS;
                            case LOGISTICS -> net.kingsmp.crowns.CrownType.GOLD;
                            case OCCULT -> net.kingsmp.crowns.CrownType.LAVA;
                            case SCOUT -> net.kingsmp.crowns.CrownType.ICE;
                        };
                    }
                }
                crown = KingSMPMod.createCrown(type, 1);
                if (!player.getInventory().add(crown)) {
                    player.drop(crown, false);
                }
                player.sendSystemMessage(
                        Component.literal("👑 Your " + type.getDisplayName() + " has been restored!")
                                .withStyle(ChatFormatting.GOLD));
            }

            net.kingsmp.crowns.CrownManager.tickPlayer(player, KingSMPMod.getCrownType(crown), KingSMPMod.getCrownStep(crown));
        }
    }

    public static void onPlayerLeave(ServerPlayer player, KingDataManager data) {
        net.kingsmp.shop.ShopScreenHandler.cleanupSearchSession(player.getUUID(), player.level().getServer());
        net.kingsmp.crowns.CrownManager.clearPassives(player);

        if (CombatTracker.isInCombat(player)) {
            // Dethrone if King
            if (data.isKing(player.getUUID())) {
                data.removeKing(player.getUUID());
                net.minecraft.server.MinecraftServer server = player.level().getServer();
                if (server != null) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("☠ " + player.getScoreboardName() + " was dethroned for combat logging!")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            false);
                }
            }

            // Drop all inventory items
            player.getInventory().dropAll();
            KingSMPMod.saveNow();
        }

        CombatTracker.removePlayer(player.getUUID());
        TpaCommands.tpaCooldowns.remove(player.getUUID());
        TpaCommands.tpaTargetCooldowns.remove(player.getUUID());
    }
}
