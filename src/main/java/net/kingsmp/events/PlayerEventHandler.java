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
            boolean hasCrown = hasCrownInInventory(player);

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

                    applyHealthBonus(player, true);
                    player.heal((float) HEALTH_BONUS);
                }
            }
            // ──────────────────────────────────────────────────────────────────

            if (isKing && hasCrown) {
                applyEffects(player);
                applyHealthBonus(player, true);
                data.addReignTick(player.getUUID());
            } else {
                removeEffects(player);
                applyHealthBonus(player, false);
            }

            enforceEnderChestRules(player);

            // ── FACTION RANKS & BUFFS ──
            // FIX: Use getPlayerRank() which returns FactionRank enum, NOT
            // getPlayerFaction() which returns a String!
            FactionManager.FactionRank rank = FactionManager.getPlayerRank(player);

            if (rank != null) {
                // 👑 KINGS: Haste I, Fire Resistance, Hero of the Village.
                if (rank == FactionManager.FactionRank.KING) {
                    applyCrownEffect(player, MobEffects.HASTE, 0);
                    applyCrownEffect(player, MobEffects.FIRE_RESISTANCE, 0);
                    applyCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
                    
                    // Remove commander/knight effects they don't have
                    if (!isKing || !hasCrown) {
                        removeCrownEffect(player, MobEffects.SPEED);
                    }
                }
                // ⚡ COMMANDERS: Speed I, Haste I, Fire Resistance, and Hero of the Village.
                else if (rank == FactionManager.FactionRank.COMMANDER) {
                    applyCrownEffect(player, MobEffects.SPEED, 0);
                    applyCrownEffect(player, MobEffects.HASTE, 0);
                    applyCrownEffect(player, MobEffects.FIRE_RESISTANCE, 0);
                    applyCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
                }
                // 🛡️ KNIGHTS: Resistance I.
                else if (rank == FactionManager.FactionRank.KNIGHT) {
                    applyCrownEffect(player, MobEffects.RESISTANCE, 0);
                    
                    // Remove king/commander effects they don't have
                    removeCrownEffect(player, MobEffects.HASTE);
                    if (!isKing || !hasCrown) {
                        removeCrownEffect(player, MobEffects.SPEED);
                        removeCrownEffect(player, MobEffects.FIRE_RESISTANCE);
                        removeCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE);
                    }
                }
                // (RECRUITS get no buffs until they earn them!)
                else if (rank == FactionManager.FactionRank.RECRUIT) {
                    removeCrownEffect(player, MobEffects.HASTE);
                    removeCrownEffect(player, MobEffects.RESISTANCE);
                    if (!isKing || !hasCrown) {
                        removeCrownEffect(player, MobEffects.SPEED);
                        removeCrownEffect(player, MobEffects.FIRE_RESISTANCE);
                        removeCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE);
                    }
                }
            } else {
                // If the player lost their rank, we need to remove the faction buffs
                removeCrownEffect(player, MobEffects.HASTE);
                removeCrownEffect(player, MobEffects.RESISTANCE);
                if (!isKing || !hasCrown) {
                    removeCrownEffect(player, MobEffects.SPEED);
                    removeCrownEffect(player, MobEffects.FIRE_RESISTANCE);
                    removeCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE);
                }
            }
        }
    }

    private static boolean hasCrownInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (KingSMPMod.isCrown(stack))
                return true;
        }
        return false;
    }

    private static int getPlayerCrownLevel(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (KingSMPMod.isCrown(stack)) {
                return KingSMPMod.getCrownLevel(stack);
            }
        }
        return 1;
    }

    private static void applyCrownEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
        MobEffectInstance current = player.getEffect(effect);
        // Only apply if the player doesn't have the effect, OR if the current effect is from the Crown (ambient, no particles)
        // or if it's a weaker effect.
        if (current == null || (current.isAmbient() && !current.isVisible()) || current.getAmplifier() < amp) {
            player.addEffect(new MobEffectInstance(effect, 100, amp, true, false, true));
        }
    }

    private static void applyEffects(ServerPlayer player) {
        int level = getPlayerCrownLevel(player);

        // SPEED: Tier 1 → Speed I, Tier 2-4 → Speed II, Tier 5 → Speed III
        int speedAmp = (level == 5) ? 2 : (level >= 2) ? 1 : 0;
        applyCrownEffect(player, MobEffects.SPEED, speedAmp);

        // STRENGTH: Tier 1-2 → Strength I, Tier 3-4 → Strength II, Tier 5 → Strength III
        int strengthAmp = (level == 5) ? 2 : (level >= 3) ? 1 : 0;
        applyCrownEffect(player, MobEffects.STRENGTH, strengthAmp);

        // RESISTANCE: Tier 1-3 → Resistance I, Tier 4-5 → Resistance II
        int resAmp = (level >= 4) ? 1 : 0;
        applyCrownEffect(player, MobEffects.RESISTANCE, resAmp);

        // FIRE RESISTANCE: Unlocked at Tier 2+
        if (level >= 2) {
            applyCrownEffect(player, MobEffects.FIRE_RESISTANCE, 0);
        }

        // REGENERATION: Tier 4 → Regen I, Tier 5 → Regen II
        if (level >= 4) {
            int regenAmp = (level == 5) ? 1 : 0;
            applyCrownEffect(player, MobEffects.REGENERATION, regenAmp);
        }

        // HERO OF THE VILLAGE: All levels
        applyCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
    }

    private static void removeCrownEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        // Only remove the effect if it was applied by the crown (ambient, no particles)
        if (current != null && current.isAmbient() && !current.isVisible()) {
            player.removeEffect(effect);
        }
    }

    private static void removeEffects(ServerPlayer player) {
        removeCrownEffect(player, MobEffects.SPEED);
        removeCrownEffect(player, MobEffects.STRENGTH);
        removeCrownEffect(player, MobEffects.RESISTANCE);
        removeCrownEffect(player, MobEffects.REGENERATION);
        removeCrownEffect(player, MobEffects.NIGHT_VISION);
        removeCrownEffect(player, MobEffects.FIRE_RESISTANCE);
        removeCrownEffect(player, MobEffects.HERO_OF_THE_VILLAGE);
    }

    private static void applyHealthBonus(ServerPlayer player, boolean apply) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attr = player
                .getAttribute(Attributes.MAX_HEALTH);
        if (attr == null)
            return;

        boolean hadModifier = attr.hasModifier(KingSMPMod.HEALTH_MODIFIER_ID);
        attr.removeModifier(KingSMPMod.HEALTH_MODIFIER_ID);

        if (apply) {
            int level = getPlayerCrownLevel(player);

            double bonusHearts = switch (level) {
                case 2 -> 12.0; // +6 Hearts
                case 3 -> 20.0; // +10 Hearts
                case 4 -> 28.0; // +14 Hearts
                case 5 -> 40.0; // +20 Hearts
                default -> 8.0; // +4 Hearts (Tier 1)
            };

            attr.addPermanentModifier(new AttributeModifier(
                    KingSMPMod.HEALTH_MODIFIER_ID,
                    bonusHearts,
                    AttributeModifier.Operation.ADD_VALUE));

            if (!hadModifier)
                player.heal((float) bonusHearts);
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
            boolean alreadyHasCrown = hasCrownInInventory(player);
            if (!alreadyHasCrown) {
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
                ItemStack crown = KingSMPMod.createCrown(type, 1);
                if (!player.getInventory().add(crown)) {
                    player.drop(crown, false);
                }
                player.sendSystemMessage(
                        Component.literal("👑 Your " + type.getDisplayName() + " has been restored!")
                                .withStyle(ChatFormatting.GOLD));
            }

            applyEffects(player);
            applyHealthBonus(player, true);
        }
    }

    public static void onPlayerLeave(ServerPlayer player, KingDataManager data) {
        net.kingsmp.shop.ShopScreenHandler.cleanupSearchSession(player.getUUID(), player.level().getServer());
        removeEffects(player);
        applyHealthBonus(player, false);

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
