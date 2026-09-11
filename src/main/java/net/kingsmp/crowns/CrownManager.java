package net.kingsmp.crowns;

import net.kingsmp.KingSMPMod;
import net.kingsmp.config.KingSMPConfig;
import net.kingsmp.events.CombatTracker;
import net.kingsmp.factions.FactionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Manages the five archetype Crowns:
 * - Crown of Skulls (War-King)
 * - Crown of Gold (Merchant-King)
 * - Crown of the End (Void-King)
 * - Crown of Lava (Pyromancer-King)
 * - Crown of Ice (Wraith-King)
 *
 * Enforces hard caps:
 * - Resistance <= I
 * - Strength <= II
 * - Speed <= II
 * - No permanent Regeneration II
 */
public class CrownManager {

    private static final Identifier SPEED_DEBUFF_ID = Identifier.fromNamespaceAndPath(KingSMPMod.MOD_ID, "crown_speed_debuff");
    private static final Identifier KNOCKBACK_RESIST_ID = Identifier.fromNamespaceAndPath(KingSMPMod.MOD_ID, "crown_knockback_resist");

    // Cooldown trackers (Player UUID -> epoch timestamp in ms when cooldown expires)
    private static final Map<UUID, Long> lastStandCooldowns = new HashMap<>();
    private static final Map<UUID, Long> emberBurstCooldowns = new HashMap<>();
    private static final Map<UUID, Long> vanishCooldowns = new HashMap<>();

    // Server-wide market tax rate (0-5%) controlled by the Merchant-King (Crown of Gold)
    private static int globalMarketTaxRate = 0;

    public static int getGlobalMarketTaxRate() {
        return globalMarketTaxRate;
    }

    public static void setGlobalMarketTaxRate(int rate) {
        globalMarketTaxRate = Math.max(0, Math.min(5, rate));
    }

    // ── TICK & PASSIVES ───────────────────────────────────────────────────

    public static void tickPlayer(ServerPlayer player, CrownType type, int step) {
        applyCrownPassives(player, type, step);
        applyHealthModifier(player, type);
        applyStep3Aura(player, type, step);
    }

    private static void applyCrownPassives(ServerPlayer player, CrownType type, int step) {
        // Enforce hard caps: Resistance I max (amp 0), Strength II max (amp 1), Speed II max (amp 1)
        switch (type) {
            case SKULLS -> {
                // Strength II, no ranged buffs, -10% speed debuff
                applySafeEffect(player, MobEffects.STRENGTH, 1);
                applySpeedDebuff(player, -0.10);
                applyKnockbackResist(player, 0.30);
                applySafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
            }
            case GOLD -> {
                // No combat buffs beyond vanilla! Resistance 0.
                removeSpeedDebuff(player);
                removeKnockbackResist(player);
                applySafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
            }
            case END -> {
                // Territory-based bonuses
                removeSpeedDebuff(player);
                removeKnockbackResist(player);
                boolean inTerritory = isInOwnedTerritory(player);
                if (inTerritory) {
                    applySafeEffect(player, MobEffects.HASTE, 0); // Haste I
                    applySafeEffect(player, MobEffects.RESISTANCE, 0); // Resistance I
                } else {
                    removeSafeEffect(player, MobEffects.HASTE);
                    removeSafeEffect(player, MobEffects.RESISTANCE);
                }
                applySafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
            }
            case LAVA -> {
                // Fire immunity, Nether Speed II / Normal Speed I
                removeSpeedDebuff(player);
                removeKnockbackResist(player);
                applySafeEffect(player, MobEffects.FIRE_RESISTANCE, 0);
                boolean inNether = player.level().dimension() == net.minecraft.world.level.Level.NETHER;
                int speedAmp = inNether ? 1 : 0; // Speed II in Nether, Speed I elsewhere
                applySafeEffect(player, MobEffects.SPEED, speedAmp);
                applySafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
            }
            case ICE -> {
                // Speed II, no fall damage
                removeSpeedDebuff(player);
                removeKnockbackResist(player);
                applySafeEffect(player, MobEffects.SPEED, 1); // Speed II (hard cap)
                applySafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
            }
        }
    }

    public static void clearPassives(ServerPlayer player) {
        removeSafeEffect(player, MobEffects.STRENGTH);
        removeSafeEffect(player, MobEffects.RESISTANCE);
        removeSafeEffect(player, MobEffects.SPEED);
        removeSafeEffect(player, MobEffects.FIRE_RESISTANCE);
        removeSafeEffect(player, MobEffects.HASTE);
        removeSafeEffect(player, MobEffects.HERO_OF_THE_VILLAGE);
        removeSafeEffect(player, MobEffects.REGENERATION);

        removeSpeedDebuff(player);
        removeKnockbackResist(player);
        removeHealthModifier(player);
    }

    // ── ATTRIBUTE MODIFIERS ───────────────────────────────────────────────

    private static void applyHealthModifier(ServerPlayer player, CrownType type) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        double bonus = (type == CrownType.SKULLS)
                ? KingSMPConfig.crownSkullsHealthBonus  // +12.0 HP (+6 Hearts)
                : KingSMPConfig.crownBaseHealthBonus;   // +4.0 HP (+2 Hearts)

        boolean hadModifier = attr.hasModifier(KingSMPMod.HEALTH_MODIFIER_ID);
        attr.removeModifier(KingSMPMod.HEALTH_MODIFIER_ID);

        attr.addPermanentModifier(new AttributeModifier(
                KingSMPMod.HEALTH_MODIFIER_ID,
                bonus,
                AttributeModifier.Operation.ADD_VALUE));

        if (!hadModifier) {
            player.heal((float) bonus);
        }
    }

    public static void removeHealthModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(KingSMPMod.HEALTH_MODIFIER_ID);
        }
    }

    private static void applySpeedDebuff(ServerPlayer player, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && !attr.hasModifier(SPEED_DEBUFF_ID)) {
            attr.addTransientModifier(new AttributeModifier(SPEED_DEBUFF_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void removeSpeedDebuff(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SPEED_DEBUFF_ID);
        }
    }

    private static void applyKnockbackResist(ServerPlayer player, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attr != null && !attr.hasModifier(KNOCKBACK_RESIST_ID)) {
            attr.addTransientModifier(new AttributeModifier(KNOCKBACK_RESIST_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeKnockbackResist(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attr != null) {
            attr.removeModifier(KNOCKBACK_RESIST_ID);
        }
    }

    // ── STEP 3 FACTION AURAS ──────────────────────────────────────────────

    private static void applyStep3Aura(ServerPlayer king, CrownType type, int step) {
        if (step < 3) return;

        String faction = FactionManager.getPlayerFaction(king);
        if (faction == null) return;

        double radiusSq = KingSMPConfig.crownAuraRadiusBlocks * KingSMPConfig.crownAuraRadiusBlocks;

        for (ServerPlayer other : king.level().getServer().getPlayerList().getPlayers()) {
            if (other.getUUID().equals(king.getUUID())) continue;
            if (faction.equalsIgnoreCase(FactionManager.getPlayerFaction(other))) {
                if (other.level() == king.level() && other.distanceToSqr(king) <= radiusSq) {
                    switch (type) {
                        case SKULLS -> applySafeEffect(other, MobEffects.RESISTANCE, 0); // Resistance I
                        case LAVA -> applySafeEffect(other, MobEffects.FIRE_RESISTANCE, 0);
                        case ICE -> applySafeEffect(other, MobEffects.SPEED, 0); // Speed I
                        case END -> {
                            if (isInOwnedTerritory(other)) {
                                applySafeEffect(other, MobEffects.HASTE, 0); // Haste I
                                applySafeEffect(other, MobEffects.RESISTANCE, 0);
                            }
                        }
                        case GOLD -> {
                            // Faction shop discount checked dynamically in ShopScreenHandler
                        }
                    }
                }
            }
        }
    }

    // ── CLIMAX & ACTIVATED ABILITIES ─────────────────────────────────────

    /**
     * Crown of Skulls "Last Stand" triggered when HP drops below 30%.
     */
    public static void checkLastStand(ServerPlayer player, CrownType type, int step) {
        if (type != CrownType.SKULLS || step < 2) return;

        float hp = player.getHealth();
        float maxHp = player.getMaxHealth();

        if (hp <= maxHp * 0.30f) {
            long now = System.currentTimeMillis();
            long cd = lastStandCooldowns.getOrDefault(player.getUUID(), 0L);

            if (now >= cd) {
                lastStandCooldowns.put(player.getUUID(), now + (KingSMPConfig.crownLastStandCooldownSeconds * 1000L));

                // Gain Resistance I + Regeneration I for 8 seconds
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0, true, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0, true, true, true));

                player.sendSystemMessage(Component.literal("💀 LAST STAND ACTIVATED! (Resistance I + Regen I for 8s)")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                KingSMPMod.playSoundToPlayer(player, SoundEvents.WITHER_SPAWN, 1.0f, 1.0f);

                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }
    }

    /**
     * Activated ability triggered via /king ability
     */
    public static boolean triggerActiveAbility(ServerPlayer player) {
        ItemStack crown = getCrownInInventory(player);
        if (crown == null || crown.isEmpty()) {
            player.sendSystemMessage(Component.literal("You must have a Crown in your inventory to use an ability!").withStyle(ChatFormatting.RED));
            return false;
        }

        CrownType type = KingSMPMod.getCrownType(crown);
        int step = KingSMPMod.getCrownStep(crown);

        if (step < 2) {
            player.sendSystemMessage(Component.literal("Your Crown must be at least Upgrade Step 2 to use its active ability!").withStyle(ChatFormatting.RED));
            return false;
        }

        long now = System.currentTimeMillis();

        switch (type) {
            case LAVA -> {
                // "Ember Burst": AoE fire pulse (deal 6 magic damage + 5s ignite) on 90s CD
                long cd = emberBurstCooldowns.getOrDefault(player.getUUID(), 0L);
                if (now < cd) {
                    long remainingSec = (cd - now) / 1000L;
                    player.sendSystemMessage(Component.literal("Ember Burst is on cooldown! " + remainingSec + "s remaining.").withStyle(ChatFormatting.RED));
                    return false;
                }

                emberBurstCooldowns.put(player.getUUID(), now + (KingSMPConfig.crownEmberBurstCooldownSeconds * 1000L));

                ServerLevel sl = (ServerLevel) player.level();
                sl.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 100, 2.5, 1.0, 2.5, 0.15);
                KingSMPMod.playSoundToPlayer(player, SoundEvents.BLAZE_SHOOT, 1.0f, 1.0f);

                // Affect nearby entities (radius 5 blocks)
                sl.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(5.0), target -> !target.getUUID().equals(player.getUUID())).forEach(target -> {
                    target.igniteForSeconds(5.0f);
                    target.hurtServer(sl, player.damageSources().magic(), 6.0f);
                    target.sendSystemMessage(Component.literal("🔥 Burned by " + player.getScoreboardName() + "'s Ember Burst!").withStyle(ChatFormatting.GOLD));
                });

                player.sendSystemMessage(Component.literal("🌋 EMBER BURST RELEASED!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                return true;
            }

            case ICE -> {
                // "Vanish": Break combat tag + 3s Invisibility + Speed burst (24h CD)
                long cd = vanishCooldowns.getOrDefault(player.getUUID(), 0L);
                if (now < cd) {
                    long remainingMin = (cd - now) / 60000L;
                    player.sendSystemMessage(Component.literal("Vanish is on cooldown! " + (remainingMin / 60) + "h " + (remainingMin % 60) + "m remaining.").withStyle(ChatFormatting.RED));
                    return false;
                }

                vanishCooldowns.put(player.getUUID(), now + (KingSMPConfig.crownVanishCooldownHours * 3600000L));

                CombatTracker.removePlayer(player.getUUID());
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false, true));

                ServerLevel sl = (ServerLevel) player.level();
                sl.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 1.0, 0.5, 0.1);
                KingSMPMod.playSoundToPlayer(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);

                player.sendSystemMessage(Component.literal("❄️ VANISH ACTIVATED! Combat tag cleared, 3s Invisibility.").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                return true;
            }

            default -> {
                player.sendSystemMessage(Component.literal("Your Crown's abilities are passive or territory-bound.").withStyle(ChatFormatting.GRAY));
                return false;
            }
        }
    }

    public static ItemStack getCrownInInventory(net.minecraft.world.entity.player.Player player) {
        if (player == null) return null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (KingSMPMod.isCrown(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean isInOwnedTerritory(ServerPlayer player) {
        String faction = FactionManager.getPlayerFaction(player);
        if (faction == null) return false;
        // Check outpost radius (e.g. within 64 blocks of any faction outpost)
        var outposts = FactionManager.getOutposts(faction);
        for (var outpost : outposts.values()) {
            if (outpost.dimension.equalsIgnoreCase(player.level().dimension().identifier().toString())) {
                double dx = outpost.x - player.getX();
                double dy = outpost.y - player.getY();
                double dz = outpost.z - player.getZ();
                if ((dx * dx + dy * dy + dz * dz) <= (64.0 * 64.0)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── EFFECT HELPERS (Respecting Ambient & Visibility) ──────────────────

    private static void applySafeEffect(ServerPlayer player, Holder<MobEffect> effect, int amp) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || (current.isAmbient() && !current.isVisible()) || current.getAmplifier() < amp) {
            player.addEffect(new MobEffectInstance(effect, 100, amp, true, false, true));
        }
    }

    private static void removeSafeEffect(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.isAmbient() && !current.isVisible()) {
            player.removeEffect(effect);
        }
    }
}
