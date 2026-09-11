package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kingsmp.KingSMPMod;
import net.kingsmp.factions.FactionManager;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.kingsmp.factions.FactionScreenHandler;
import net.kingsmp.events.CombatTracker;

public class FactionCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faction")

                // ── /faction create <name> ──
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");

                                    int cost = 5000;
                                    if (KingSMPMod.dataManager.getSilver(player.getUUID()) < cost) {
                                        src.sendFailure(Component.literal("Creating a faction costs 5000 Silver! You currently have " 
                                                + KingSMPMod.dataManager.getSilver(player.getUUID()) + " Silver."));
                                        return 0;
                                    }

                                    if (FactionManager.createFaction(player, name)) {
                                        KingSMPMod.dataManager.removeSilver(player.getUUID(), cost);
                                        KingSMPMod.saveNow();
                                        src.getServer().getPlayerList()
                                                .broadcastSystemMessage(Component
                                                        .literal("🏰 King " + player.getScoreboardName()
                                                                + " has founded " + name.toUpperCase() + "!")
                                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                                    } else
                                        src.sendFailure(Component.literal(
                                                "Failed to create faction. Name taken or you are already in one."));
                                    return 1;
                                })))

                // ── /faction invite <player> ──
                .then(Commands.literal("invite")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer inviter = src.getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

                                    if (FactionManager.invitePlayer(inviter, target)) {
                                        String fName = FactionManager
                                                .getFactionNameForDisplay(FactionManager.getPlayerFaction(inviter));
                                        src.sendSuccess(() -> Component
                                                .literal("📨 Invited " + target.getScoreboardName() + " to " + fName)
                                                .withStyle(ChatFormatting.GREEN), false);
                                        target.sendSystemMessage(Component
                                                .literal("📨 You have been invited to join " + fName
                                                        + "! Type /faction accept")
                                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                                    }
                                    return 1;
                                })))

                // ── /faction accept ──
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (FactionManager.acceptInvite(player)) {
                                KingSMPMod.saveNow();
                                String fName = FactionManager
                                        .getFactionNameForDisplay(FactionManager.getPlayerFaction(player));
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("⚔️ You have joined " + fName + " as a RECRUIT!")
                                                .withStyle(ChatFormatting.GREEN),
                                        false);
                            }
                            return 1;
                        }))

                // ── /faction promote <player> <rank> ──
                .then(Commands.literal("promote")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("COMMANDER");
                                            builder.suggest("KNIGHT");
                                            builder.suggest("RECRUIT");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            ServerPlayer king = src.getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            String rankInput = StringArgumentType.getString(ctx, "rank").toUpperCase();

                                            try {
                                                FactionManager.FactionRank newRank = FactionManager.FactionRank
                                                        .valueOf(rankInput);
                                                if (newRank == FactionManager.FactionRank.KING)
                                                    throw new Exception();

                                                if (FactionManager.promotePlayer(king, target, newRank)) {
                                                    KingSMPMod.saveNow();
                                                    src.sendSuccess(
                                                            () -> Component
                                                                    .literal("Promoted " + target.getScoreboardName()
                                                                            + " to " + newRank.name())
                                                                    .withStyle(ChatFormatting.GREEN),
                                                            false);
                                                    target.sendSystemMessage(Component
                                                            .literal("🛡️ You were promoted to " + newRank.name() + "!")
                                                            .withStyle(ChatFormatting.GOLD));
                                                } else
                                                    src.sendFailure(Component.literal(
                                                            "Could not promote player. Are they in your faction?"));
                                            } catch (Exception e) {
                                                src.sendFailure(Component
                                                        .literal("Invalid rank! Use COMMANDER, KNIGHT, or RECRUIT."));
                                            }
                                            return 1;
                                        }))))

                // ── /faction kick <player> ──
                .then(Commands.literal("kick")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer kicker = src.getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

                                    if (FactionManager.kickPlayer(kicker, target)) {
                                        KingSMPMod.saveNow();
                                        String fName = FactionManager
                                                .getFactionNameForDisplay(FactionManager.getPlayerFaction(kicker));
                                        src.sendSuccess(() -> Component
                                                .literal(
                                                        "Booted " + target.getScoreboardName() + " from the faction.")
                                                .withStyle(ChatFormatting.GREEN), false);
                                        target.sendSystemMessage(Component.literal("💀 You have been kicked from " + fName + "!")
                                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                                    } else
                                        src.sendFailure(Component.literal(
                                                "Failed to kick player. You must be a King/Commander, and they must be in your faction."));
                                    return 1;
                                })))

                // ── /faction leave ──
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (FactionManager.leaveFaction(player)) {
                                KingSMPMod.saveNow();
                                ctx.getSource().sendSuccess(() -> Component.literal("You have abandoned your faction.")
                                        .withStyle(ChatFormatting.YELLOW), false);
                            }
                            return 1;
                        }))

                // ── /faction view ──
                .then(Commands.literal("view")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String fName = FactionManager.getPlayerFaction(player);

                            if (fName == null) {
                                ctx.getSource().sendFailure(Component.literal("You are not in a faction!"));
                                return 0;
                            }

                            java.util.List<java.util.UUID> members = FactionManager.getFactionMembers(fName);
                            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                    (syncId, playerInv, p) -> new FactionScreenHandler(syncId, playerInv, fName,
                                            members),
                                    Component.literal("Faction: " + FactionManager.getFactionNameForDisplay(fName))
                                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)));

                            return 1;
                        }))

                // ── /faction disband ──
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ServerPlayer king = ctx.getSource().getPlayerOrException();
                            String fName = FactionManager
                                    .getFactionNameForDisplay(FactionManager.getPlayerFaction(king));

                            if (FactionManager.disbandFaction(king)) {
                                KingSMPMod.saveNow();
                                ctx.getSource().getServer().getPlayerList()
                                        .broadcastSystemMessage(Component.literal("🔥 The King has disbanded " + fName + "!")
                                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
                            } else
                                ctx.getSource().sendFailure(Component.literal("Only the King can disband the faction."));
                            return 1;
                        }))

                // ── /faction setoutpost <name> ──
                .then(Commands.literal("setoutpost")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        String fName = FactionManager.getPlayerFaction(player);
                                        if (fName != null) {
                                            java.util.Map<String, FactionManager.OutpostLocation> outposts = FactionManager.getOutposts(fName);
                                            if (outposts != null) {
                                                for (String name : outposts.keySet()) {
                                                    builder.suggest(name);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {}
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayerOrException();
                                    String fName = FactionManager.getPlayerFaction(player);
                                    FactionManager.FactionRank rank = FactionManager.getPlayerRank(player);

                                    if (fName == null || (rank != FactionManager.FactionRank.KING && rank != FactionManager.FactionRank.COMMANDER)) {
                                        src.sendFailure(Component.literal("Only Kings and Commanders can set outposts!"));
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    int count = FactionManager.getOutpostCount(fName);
                                    
                                    // Check if updating an existing outpost. If they already have it, it's just an update (no extra cost).
                                    boolean isUpdate = FactionManager.getOutpost(fName, name) != null;
                                    int cost = 0;

                                    if (count >= 1 && !isUpdate) {
                                        cost = 1000;
                                    }

                                    if (cost > 0) {
                                        if (KingSMPMod.dataManager.getSilver(player.getUUID()) < cost) {
                                            src.sendFailure(Component.literal("Setting additional outposts costs " + cost + " Silver! You have " + KingSMPMod.dataManager.getSilver(player.getUUID()) + "."));
                                            return 0;
                                        }
                                    }

                                     String dim = player.level().dimension().identifier().toString();
                                     FactionManager.OutpostLocation loc = new FactionManager.OutpostLocation(
                                             dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
                                     );

                                    FactionManager.setOutpost(fName, name, loc);
                                    
                                    if (cost > 0) {
                                        KingSMPMod.dataManager.removeSilver(player.getUUID(), cost);
                                    }
                                    KingSMPMod.saveNow();

                                    int finalCost = cost;
                                    src.sendSuccess(() -> Component.literal("🚩 Faction outpost '" + name.toUpperCase() + "' set successfully!" + (finalCost > 0 ? " (Paid " + finalCost + " Silver)" : " (Free)")).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))

                // ── /faction outpost <name> ──
                .then(Commands.literal("outpost")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        String fName = FactionManager.getPlayerFaction(player);
                                        if (fName != null) {
                                            java.util.Map<String, FactionManager.OutpostLocation> outposts = FactionManager.getOutposts(fName);
                                            if (outposts != null) {
                                                for (String name : outposts.keySet()) {
                                                    builder.suggest(name);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {}
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayerOrException();
                                    
                                    if (CombatTracker.isInCombat(player)) {
                                        src.sendFailure(Component.literal("You cannot teleport to an outpost while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
                                        return 0;
                                    }

                                    String fName = FactionManager.getPlayerFaction(player);
                                    if (fName == null) {
                                        src.sendFailure(Component.literal("You are not in a faction!"));
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    FactionManager.OutpostLocation loc = FactionManager.getOutpost(fName, name);

                                    if (loc == null) {
                                        src.sendFailure(Component.literal("Outpost '" + name.toUpperCase() + "' not found!"));
                                        return 0;
                                    }

                                    net.minecraft.server.level.ServerLevel world = src.getServer().getLevel(
                                            net.minecraft.resources.ResourceKey.create(
                                                    net.minecraft.core.registries.Registries.DIMENSION,
                                                    net.minecraft.resources.Identifier.parse(loc.dimension)
                                            )
                                    );

                                    if (world == null) {
                                        src.sendFailure(Component.literal("The dimension the outpost is in could not be found!"));
                                        return 0;
                                    }

                                    player.teleportTo(world, loc.x, loc.y, loc.z, java.util.Collections.emptySet(), loc.yaw, loc.pitch, true);
                                    src.sendSuccess(() -> Component.literal("✨ Teleported to faction outpost '" + name.toUpperCase() + "'!").withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                })))

                // ── /faction deleteoutpost <name> ──
                .then(Commands.literal("deleteoutpost")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        String fName = FactionManager.getPlayerFaction(player);
                                        if (fName != null) {
                                            java.util.Map<String, FactionManager.OutpostLocation> outposts = FactionManager.getOutposts(fName);
                                            if (outposts != null) {
                                                for (String name : outposts.keySet()) {
                                                    builder.suggest(name);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {}
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayerOrException();
                                    String fName = FactionManager.getPlayerFaction(player);
                                    FactionManager.FactionRank rank = FactionManager.getPlayerRank(player);

                                    if (fName == null || (rank != FactionManager.FactionRank.KING && rank != FactionManager.FactionRank.COMMANDER)) {
                                        src.sendFailure(Component.literal("Only Kings and Commanders can delete outposts!"));
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    if (FactionManager.deleteOutpost(fName, name)) {
                                        KingSMPMod.saveNow();
                                        src.sendSuccess(() -> Component.literal("🗑️ Outpost '" + name.toUpperCase() + "' deleted successfully.").withStyle(ChatFormatting.YELLOW), false);
                                        return 1;
                                    } else {
                                        src.sendFailure(Component.literal("Outpost '" + name.toUpperCase() + "' not found!"));
                                        return 0;
                                    }
                                })))

                // ── /faction listoutposts ──
                .then(Commands.literal("listoutposts")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer player = src.getPlayerOrException();
                            String fName = FactionManager.getPlayerFaction(player);

                            if (fName == null) {
                                src.sendFailure(Component.literal("You are not in a faction!"));
                                return 0;
                            }

                            java.util.Map<String, FactionManager.OutpostLocation> outposts = FactionManager.getOutposts(fName);
                            if (outposts.isEmpty()) {
                                src.sendSuccess(() -> Component.literal("No outposts set for your faction. set one with /faction setoutpost <name>").withStyle(ChatFormatting.GRAY), false);
                                return 1;
                            }

                            src.sendSuccess(() -> Component.literal("━━━ 🚩 Faction Outposts ━━━").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);
                            outposts.forEach((name, loc) -> {
                                src.sendSuccess(() -> Component.literal("➤ " + name.toUpperCase()).withStyle(ChatFormatting.YELLOW), false);
                            });

                            return 1;
                        }))
                
                // ── /faction tax [rate] ──
                .then(Commands.literal("tax")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer player = src.getPlayerOrException();
                            String fName = FactionManager.getPlayerFaction(player);

                            if (fName == null) {
                                src.sendFailure(Component.literal("You are not in a faction!"));
                                return 0;
                            }

                            int rate = FactionManager.getFactionTax(fName);
                            src.sendSuccess(() -> Component.literal("💸 Current Faction Tax Rate: " + rate + "%")
                                    .withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        })
                        .then(Commands.argument("rate", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayerOrException();
                                    String fName = FactionManager.getPlayerFaction(player);
                                    FactionManager.FactionRank rank = FactionManager.getPlayerRank(player);

                                    if (fName == null || rank != FactionManager.FactionRank.KING) {
                                        src.sendFailure(Component.literal("Only the Faction King can set the tax rate!"));
                                        return 0;
                                    }

                                    int rate = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rate");
                                    FactionManager.setFactionTax(fName, rate);
                                    KingSMPMod.saveNow();

                                    src.sendSuccess(() -> Component.literal("💸 Faction tax rate set to " + rate + "% for " + fName.toUpperCase() + "!")
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                }))
                        )
                );
    }
}