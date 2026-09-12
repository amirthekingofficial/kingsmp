package net.kingsmp.events;

import net.kingsmp.KingSMPMod;
import net.kingsmp.config.KingSMPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages stationary teleportation channeling (e.g. 3 seconds).
 * Cancels if:
 * - Player moves > 0.5 blocks from starting location
 * - Player takes damage or enters combat
 * - Player disconnects
 */
public class TeleportManager {

    private static class Channel {
        final UUID uuid;
        final double startX, startY, startZ;
        final String destinationName;
        final Runnable onComplete;
        final Runnable onCancel;
        int ticksRemaining;

        Channel(ServerPlayer player, String destinationName, Runnable onComplete, Runnable onCancel, int ticks) {
            this.uuid = player.getUUID();
            this.startX = player.getX();
            this.startY = player.getY();
            this.startZ = player.getZ();
            this.destinationName = destinationName;
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            this.ticksRemaining = ticks;
        }
    }

    private static final Map<UUID, Channel> activeChannels = new ConcurrentHashMap<>();

    public static boolean isChanneling(UUID uuid) {
        return activeChannels.containsKey(uuid);
    }

    public static void startChannel(ServerPlayer player, String destinationName, Runnable onComplete) {
        startChannel(player, destinationName, onComplete, null);
    }

    public static void startChannel(ServerPlayer player, String destinationName, Runnable onComplete, Runnable onCancel) {
        if (CombatTracker.isInCombat(player)) {
            player.sendSystemMessage(Component.literal("❌ Cannot teleport while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int warmupSeconds = KingSMPConfig.teleportChannelSeconds;
        if (warmupSeconds <= 0) {
            onComplete.run();
            return;
        }

        int ticks = warmupSeconds * 20;
        activeChannels.put(player.getUUID(), new Channel(player, destinationName, onComplete, onCancel, ticks));

        player.sendSystemMessage(Component.literal("⏳ Teleporting to " + destinationName + " in " + warmupSeconds + "s... Stand still!")
                .withStyle(ChatFormatting.YELLOW));
    }

    public static void cancelChannel(UUID uuid, String reason) {
        Channel channel = activeChannels.remove(uuid);
        if (channel != null && channel.onCancel != null) {
            try {
                channel.onCancel.run();
            } catch (Exception ignored) {}
        }
    }

    public static void cancelChannel(ServerPlayer player, String reason) {
        Channel channel = activeChannels.remove(player.getUUID());
        if (channel != null) {
            player.sendSystemMessage(Component.literal("❌ Teleportation cancelled: " + reason)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            if (channel.onCancel != null) {
                try {
                    channel.onCancel.run();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (activeChannels.isEmpty()) return;

        var iterator = activeChannels.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            Channel channel = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || player.isRemoved()) {
                iterator.remove();
                if (channel.onCancel != null) {
                    try {
                        channel.onCancel.run();
                    } catch (Exception ignored) {}
                }
                continue;
            }

            if (CombatTracker.isInCombat(player)) {
                iterator.remove();
                player.sendSystemMessage(Component.literal("❌ Teleportation cancelled: Combat tag initiated!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                if (channel.onCancel != null) {
                    try {
                        channel.onCancel.run();
                    } catch (Exception ignored) {}
                }
                continue;
            }

            // Movement check: > 0.5 blocks (squared > 0.25)
            double dx = player.getX() - channel.startX;
            double dy = player.getY() - channel.startY;
            double dz = player.getZ() - channel.startZ;
            if ((dx * dx + dy * dy + dz * dz) > 0.25) {
                iterator.remove();
                player.sendSystemMessage(Component.literal("❌ Teleportation cancelled: You moved!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                KingSMPMod.playSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                if (channel.onCancel != null) {
                    try {
                        channel.onCancel.run();
                    } catch (Exception ignored) {}
                }
                continue;
            }

            channel.ticksRemaining--;

            if (channel.ticksRemaining % 20 == 0 && channel.ticksRemaining > 0) {
                int sec = channel.ticksRemaining / 20;
                player.sendSystemMessage(Component.literal("⏳ Teleporting to " + channel.destinationName + " in " + sec + "s...")
                        .withStyle(ChatFormatting.YELLOW));
            }

            if (channel.ticksRemaining <= 0) {
                iterator.remove();
                try {
                    channel.onComplete.run();
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("❌ Teleportation failed unexpectedly.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }
}
