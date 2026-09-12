package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.events.CombatTracker;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.text.ClickEvent;
//import net.minecraft.text.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
//import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaCommands {

    private static final long TPA_EXPIRY_MS = 60000; // 60 seconds
    private static final long TPA_COOLDOWN_MS = 60000; // 60 seconds
    // Target UUID -> (Sender UUID -> Expiry Timestamp)
    private static final Map<UUID, Map<UUID, Long>> tpaRequests = new HashMap<>();
    // Sender UUID -> Cooldown Expiry Timestamp
    public static final Map<UUID, Long> tpaCooldowns = new ConcurrentHashMap<>();
    // Sender UUID -> (Target UUID -> Cooldown Expiry Timestamp)
    public static final Map<UUID, Map<UUID, Long>> tpaTargetCooldowns = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, KingDataManager dataManager) {
        dispatcher.register(Commands.literal("tpa")
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Usage: /tpa <player>"));
                    return 0;
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cmdTpa(ctx, dataManager))));

        dispatcher.register(Commands.literal("tpaccept")
                .executes(ctx -> cmdTpAccept(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cmdTpAccept(ctx, EntityArgument.getPlayer(ctx, "player")))));

        dispatcher.register(Commands.literal("tpadeny")
                .executes(ctx -> cmdTpDeny(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cmdTpDeny(ctx, EntityArgument.getPlayer(ctx, "player")))));

        dispatcher.register(Commands.literal("tpdeny")
                .executes(ctx -> cmdTpDeny(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cmdTpDeny(ctx, EntityArgument.getPlayer(ctx, "player")))));

        dispatcher.register(Commands.literal("tpacancel")
                .executes(TpaCommands::cmdTpaCancel));

        dispatcher.register(Commands.literal("tpauto")
                .executes(ctx -> cmdTpAuto(ctx, dataManager)));
    }

    public static void clearCooldowns(UUID uuid) {
        if (uuid == null) return;
        tpaCooldowns.remove(uuid);
        tpaTargetCooldowns.remove(uuid);
    }

    private static int cmdTpa(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer sender = src.getPlayerOrException();
            if (CombatTracker.isInCombat(sender)) {
                src.sendFailure(Component.literal("You cannot send TPA requests while in combat! (" + CombatTracker.getRemainingSeconds(sender) + "s remaining)"));
                return 0;
            }

            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            if (sender.getUUID().equals(target.getUUID())) {
                src.sendFailure(Component.literal("You cannot send a teleport request to yourself."));
                return 0;
            }

            long now = System.currentTimeMillis();
            Long generalCooldownEnd = tpaCooldowns.get(sender.getUUID());
            if (generalCooldownEnd != null && now < generalCooldownEnd) {
                long remainingSec = (generalCooldownEnd - now + 999) / 1000;
                src.sendFailure(Component.literal("You must wait " + remainingSec + " seconds before sending another TPA request."));
                return 0;
            }

            Map<UUID, Long> targets = tpaTargetCooldowns.get(sender.getUUID());
            if (targets != null) {
                Long targetCooldownEnd = targets.get(target.getUUID());
                if (targetCooldownEnd != null && now < targetCooldownEnd) {
                    long remainingSec = (targetCooldownEnd - now + 999) / 1000;
                    src.sendFailure(Component.literal("You must wait " + remainingSec + " seconds before sending another TPA request to " + target.getScoreboardName() + "."));
                    return 0;
                }
            }

            if (dataManager.isTpAutoEnabled(target.getUUID())) {
                if (CombatTracker.isInCombat(target)) {
                    src.sendFailure(Component.literal("The target player is currently in combat!"));
                    return 0;
                }
                net.kingsmp.events.TeleportManager.startChannel(sender, target.getScoreboardName(), () -> {
                    sender.teleportTo((net.minecraft.server.level.ServerLevel) target.level(), target.getX(),
                            target.getY(), target.getZ(), java.util.Collections.emptySet(), target.getYRot(),
                            target.getXRot(), true);
                    sender.sendSystemMessage(Component.literal("✨ Teleported to ").withStyle(ChatFormatting.AQUA)
                            .append(target.getName().copy().withStyle(ChatFormatting.GOLD)));
                }, () -> {
                    clearCooldowns(sender.getUUID());
                    sender.sendSystemMessage(Component.literal("⏱️ TPA cooldown has been reset.").withStyle(ChatFormatting.GRAY));
                });
                
                tpaCooldowns.put(sender.getUUID(), now + 10000); // 10 seconds general spam protection
                tpaTargetCooldowns.computeIfAbsent(sender.getUUID(), k -> new ConcurrentHashMap<>()).put(target.getUUID(), now + TPA_COOLDOWN_MS); // 60 seconds per-player
                return 1;
            }

            tpaRequests.putIfAbsent(target.getUUID(), new HashMap<>());
            tpaRequests.get(target.getUUID()).put(sender.getUUID(), now + TPA_EXPIRY_MS);

            src.sendSuccess(
                    () -> Component.literal("✉️ Teleport request sent to ").withStyle(ChatFormatting.YELLOW)
                            .append(target.getName().copy().withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(". They have 60 seconds to accept.").withStyle(ChatFormatting.YELLOW)),
                    false);

            target.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .withStyle(ChatFormatting.GOLD));
            target.sendSystemMessage(sender.getName().copy().withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" has requested to teleport to you.").withStyle(ChatFormatting.YELLOW)));
            target.sendSystemMessage(Component.literal("Type ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/tpaccept " + sender.getScoreboardName()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" to accept, or ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("/tpdeny " + sender.getScoreboardName()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" to deny.").withStyle(ChatFormatting.GRAY)));
            target.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .withStyle(ChatFormatting.GOLD));

            tpaCooldowns.put(sender.getUUID(), now + 10000); // 10 seconds general spam protection
            tpaTargetCooldowns.computeIfAbsent(sender.getUUID(), k -> new ConcurrentHashMap<>()).put(target.getUUID(), now + TPA_COOLDOWN_MS); // 60 seconds per-player

        } catch (Exception e) {
            src.sendFailure(Component.literal("Error sending teleport request."));
        }
        return 1;
    }

    private static void cleanupExpiredRequests(UUID targetUuid) {
        if (!tpaRequests.containsKey(targetUuid))
            return;
        long now = System.currentTimeMillis();
        tpaRequests.get(targetUuid).entrySet().removeIf(entry -> entry.getValue() < now);
        if (tpaRequests.get(targetUuid).isEmpty()) {
            tpaRequests.remove(targetUuid);
        }
    }

    private static int cmdTpAccept(CommandContext<CommandSourceStack> ctx, ServerPlayer specificSender) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer target = src.getPlayerOrException();
            if (CombatTracker.isInCombat(target)) {
                src.sendFailure(Component.literal("You cannot accept TPA requests while in combat! (" + CombatTracker.getRemainingSeconds(target) + "s remaining)"));
                return 0;
            }

            cleanupExpiredRequests(target.getUUID());

            Map<UUID, Long> requests = tpaRequests.get(target.getUUID());
            if (requests == null || requests.isEmpty()) {
                src.sendFailure(Component.literal("You have no pending teleport requests."));
                return 0;
            }

            UUID senderUuidToAccept = null;

            if (specificSender != null) {
                if (requests.containsKey(specificSender.getUUID())) {
                    senderUuidToAccept = specificSender.getUUID();
                } else {
                    src.sendFailure(Component.literal("You have no pending request from that player."));
                    return 0;
                }
            } else {
                if (requests.size() == 1) {
                    senderUuidToAccept = requests.keySet().iterator().next();
                } else {
                    src.sendFailure(
                            Component.literal("You have multiple pending requests. Please specify who: /tpaccept <player>"));
                    return 0;
                }
            }

            ServerPlayer sender = src.getServer().getPlayerList().getPlayer(senderUuidToAccept);
            if (sender == null) {
                src.sendFailure(Component.literal("That player is no longer online."));
                return 0;
            }

            if (CombatTracker.isInCombat(sender)) {
                src.sendFailure(Component.literal(sender.getScoreboardName() + " is currently in combat and cannot teleport!"));
                sender.sendSystemMessage(Component.literal("Your teleport request was accepted but you cannot teleport while in combat! (" + CombatTracker.getRemainingSeconds(sender) + "s remaining)")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            requests.remove(senderUuidToAccept);
            if (requests.isEmpty()) {
                tpaRequests.remove(target.getUUID());
            }

            src.sendSuccess(() -> Component.literal("✅ Accepted teleport request from ").withStyle(ChatFormatting.GREEN)
                    .append(sender.getName().copy().withStyle(ChatFormatting.GOLD)), false);
            sender.sendSystemMessage(
                    Component.literal("✨ ").withStyle(ChatFormatting.AQUA)
                            .append(target.getName().copy().withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" accepted your teleport request!").withStyle(ChatFormatting.AQUA)));

            net.kingsmp.events.TeleportManager.startChannel(sender, target.getScoreboardName(), () -> {
                sender.teleportTo((net.minecraft.server.level.ServerLevel) target.level(), target.getX(),
                        target.getY(), target.getZ(), java.util.Collections.emptySet(), target.getYRot(), target.getXRot(),
                        true);
                sender.sendSystemMessage(Component.literal("✨ Arrived at ").withStyle(ChatFormatting.AQUA)
                        .append(target.getName().copy().withStyle(ChatFormatting.GOLD)));
            }, () -> {
                clearCooldowns(sender.getUUID());
                sender.sendSystemMessage(Component.literal("⏱️ TPA cooldown has been reset.").withStyle(ChatFormatting.GRAY));
            });

        } catch (Exception e) {
            src.sendFailure(Component.literal("Error accepting teleport request."));
        }
        return 1;
    }

    private static int cmdTpDeny(CommandContext<CommandSourceStack> ctx, ServerPlayer specificSender) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer target = src.getPlayerOrException();
            if (CombatTracker.isInCombat(target)) {
                src.sendFailure(Component.literal("You cannot deny TPA requests while in combat! (" + CombatTracker.getRemainingSeconds(target) + "s remaining)"));
                return 0;
            }

            cleanupExpiredRequests(target.getUUID());

            Map<UUID, Long> requests = tpaRequests.get(target.getUUID());
            if (requests == null || requests.isEmpty()) {
                src.sendFailure(Component.literal("You have no pending teleport requests."));
                return 0;
            }

            UUID senderUuidToDeny = null;

            if (specificSender != null) {
                if (requests.containsKey(specificSender.getUUID())) {
                    senderUuidToDeny = specificSender.getUUID();
                } else {
                    src.sendFailure(Component.literal("You have no pending request from that player."));
                    return 0;
                }
            } else {
                if (requests.size() == 1) {
                    senderUuidToDeny = requests.keySet().iterator().next();
                } else {
                    src.sendFailure(
                            Component.literal("You have multiple pending requests. Please specify who: /tpdeny <player>"));
                    return 0;
                }
            }

            requests.remove(senderUuidToDeny);
            if (requests.isEmpty()) {
                tpaRequests.remove(target.getUUID());
            }

            clearCooldowns(senderUuidToDeny);

            ServerPlayer sender = src.getServer().getPlayerList().getPlayer(senderUuidToDeny);

            src.sendSuccess(() -> Component.literal("❌ Denied teleport request.").withStyle(ChatFormatting.YELLOW), false);
            if (sender != null) {
                sender.sendSystemMessage(
                        Component.literal("❌ ").withStyle(ChatFormatting.RED)
                                .append(target.getName().copy().withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" denied your teleport request. Cooldown reset.").withStyle(ChatFormatting.RED)));
            }

        } catch (Exception e) {
            src.sendFailure(Component.literal("Error denying teleport request."));
        }
        return 1;
    }

    private static int cmdTpaCancel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer sender = src.getPlayerOrException();
            UUID senderUuid = sender.getUUID();

            boolean cancelled = false;
            for (Map<UUID, Long> requests : tpaRequests.values()) {
                if (requests.remove(senderUuid) != null) {
                    cancelled = true;
                }
            }

            if (net.kingsmp.events.TeleportManager.isChanneling(senderUuid)) {
                net.kingsmp.events.TeleportManager.cancelChannel(sender, "Cancelled by user");
                cancelled = true;
            }

            clearCooldowns(senderUuid);

            if (cancelled) {
                src.sendSuccess(() -> Component.literal("❌ Teleport request cancelled and cooldown reset.")
                        .withStyle(ChatFormatting.YELLOW), false);
            } else {
                src.sendSuccess(() -> Component.literal("⏱️ TPA cooldown has been reset.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error cancelling TPA request."));
        }
        return 1;
    }

    private static int cmdTpAuto(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (CombatTracker.isInCombat(player)) {
                src.sendFailure(Component.literal("You cannot toggle auto-accept while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
                return 0;
            }

            boolean isNowEnabled = dataManager.toggleTpAuto(player.getUUID());

            if (isNowEnabled) {
                src.sendSuccess(() -> Component.literal("✅ Auto-accept teleport requests is now ")
                        .withStyle(ChatFormatting.GREEN).append(Component.literal("ENABLED").withStyle(ChatFormatting.BOLD)), false);
            } else {
                src.sendSuccess(() -> Component.literal("❌ Auto-accept teleport requests is now ")
                        .withStyle(ChatFormatting.YELLOW).append(Component.literal("DISABLED").withStyle(ChatFormatting.BOLD)),
                        false);
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this command."));
        }
        return 1;
    }
}
