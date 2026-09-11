package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kingsmp.KingSMPMod;
import net.kingsmp.config.KingSMPConfig;
import net.kingsmp.factions.FactionManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public class RankCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rank")

                // ── /rank help ──
                .then(Commands.literal("help").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    src.sendSuccess(() -> Component.literal("━━━━━━━━ 🎖 FACTION RANKS HELP ━━━━━━━━").withStyle(ChatFormatting.GOLD), false);
                    src.sendSuccess(() -> Component.literal("/rank path <military|logistics|scout|occult> - Choose your rank path").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/rank info [player] - View your or another member's rank standing").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/rank promote <player> - Promote a member in their path (Leader/Commander)").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/rank buff - Cast Occult Chanter strength blessing").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("Switching paths costs " + KingSMPConfig.rankPathSwitchFee + " 🪙 Silver and resets to Rung 1.").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))

                // ── /rank path <name> ──
                .then(Commands.literal("path")
                        .then(Commands.argument("path", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("military");
                                    builder.suggest("logistics");
                                    builder.suggest("scout");
                                    builder.suggest("occult");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String pathArg = StringArgumentType.getString(ctx, "path");
                                    FactionManager.RankPath chosen = FactionManager.RankPath.fromString(pathArg);

                                    if (chosen == null) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid path! Choose from: military, logistics, scout, occult."));
                                        return 0;
                                    }

                                    return FactionManager.selectPath(player, chosen) ? 1 : 0;
                                })))

                // ── /rank info [player] ──
                .then(Commands.literal("info")
                        .executes(ctx -> showRankInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> showRankInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))

                // ── /rank promote <player> ──
                .then(Commands.literal("promote")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer leader = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    return FactionManager.promotePlayerRung(leader, target) ? 1 : 0;
                                })))

                // ── /rank buff ──
                .then(Commands.literal("buff").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return FactionManager.triggerChanterBuff(player) ? 1 : 0;
                }))
        );
    }

    private static int showRankInfo(CommandSourceStack src, ServerPlayer target) {
        String faction = FactionManager.getPlayerFaction(target);
        FactionManager.RankPath path = FactionManager.getPlayerPath(target.getUUID());
        int rung = FactionManager.getPlayerRung(target.getUUID());
        String title = FactionManager.getPlayerRankTitle(target.getUUID());

        src.sendSuccess(() -> Component.literal("━━━━━━━━ 🎖 RANK STATUS: " + target.getScoreboardName() + " ━━━━━━━━").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Faction: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(faction != null ? faction.toUpperCase() : "None").withStyle(ChatFormatting.WHITE)), false);
        src.sendSuccess(() -> Component.literal("Current Title: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(title).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);

        if (path != null) {
            src.sendSuccess(() -> Component.literal("Path: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(path.getDisplayName()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" [Rung " + rung + "/4]").withStyle(ChatFormatting.DARK_GRAY)), false);

            src.sendSuccess(() -> Component.literal("Crown Eligibility: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(path.getCrownEligibility().getDisplayName())
                            .withStyle(rung >= 4 ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal(rung >= 4 ? " (Eligible!)" : " (Requires Rung 4: " + path.getRungTitle(4) + ")").withStyle(ChatFormatting.GRAY)), false);
        } else {
            src.sendSuccess(() -> Component.literal("Path: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("None chosen. Use /rank path <name>").withStyle(ChatFormatting.RED)), false);
        }

        return 1;
    }
}
