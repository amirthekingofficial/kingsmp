package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ProfessionCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trade")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return showProfessionBoard(ctx.getSource(), player);
                })

                // ── /trade help ──
                .then(Commands.literal("help").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    src.sendSuccess(() -> Component.literal("━━━━━━━━ 🛠 PROFESSIONS & TRADES HELP ━━━━━━━━").withStyle(ChatFormatting.GOLD), false);
                    src.sendSuccess(() -> Component.literal("/trade board - View your progress across all 7 trades").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/trade info [player] - Inspect a player's profession levels").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/trade recipes <trade> - View perks and unlocks for a trade").withStyle(ChatFormatting.YELLOW), false);
                    src.sendSuccess(() -> Component.literal("/trade pulse - Trigger Miner Vein Sense (Master Miner)").withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }))

                // ── /trade board ──
                .then(Commands.literal("board").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return showProfessionBoard(ctx.getSource(), player);
                }))

                // ── /trade info [player] ──
                .then(Commands.literal("info")
                        .executes(ctx -> showProfessionBoard(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> showProfessionBoard(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))

                // ── /trade recipes <trade> ──
                .then(Commands.literal("recipes")
                        .then(Commands.argument("trade", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ProfessionType pt : ProfessionType.values()) {
                                        builder.suggest(pt.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "trade");
                                    ProfessionType type = ProfessionType.fromString(name);
                                    if (type == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown trade! Choose from: fisher, farmer, miner, blacksmith, alchemist, hunter, merchant."));
                                        return 0;
                                    }
                                    showTradeDetails(ctx.getSource(), type);
                                    return 1;
                                })))

                // ── /trade pulse ──
                .then(Commands.literal("pulse").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return ProfessionManager.triggerVeinSense(player) ? 1 : 0;
                }))
        );

        dispatcher.register(Commands.literal("trades")
                .executes(ctx -> showProfessionBoard(ctx.getSource(), ctx.getSource().getPlayerOrException())));
    }

    private static int showProfessionBoard(CommandSourceStack src, ServerPlayer target) {
        src.sendSuccess(() -> Component.literal("━━━━━━━━ 🛠 TRADES: " + target.getScoreboardName() + " ━━━━━━━━").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Primary Title: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ProfessionManager.getPrimaryProfessionTitle(target.getUUID())).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);

        for (ProfessionType type : ProfessionType.values()) {
            int lvl = ProfessionManager.getLevel(target.getUUID(), type);
            int xp = ProfessionManager.getXp(target.getUUID(), type);
            int nextThreshold = (lvl < 5) ? ProfessionManager.XP_THRESHOLDS[lvl + 1] : ProfessionManager.XP_THRESHOLDS[5];

            src.sendSuccess(() -> Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(type.getDisplayName() + ": ").withStyle(type.getColor(), ChatFormatting.BOLD))
                    .append(Component.literal("Lv. " + lvl + "/5").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" [" + xp + "/" + nextThreshold + " XP]").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(lvl >= 5 ? " ★ MASTER" : "").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);
        }
        return 1;
    }

    private static void showTradeDetails(CommandSourceStack src, ProfessionType type) {
        src.sendSuccess(() -> Component.literal("━━━━━━━━ 📖 " + type.getDisplayName().toUpperCase() + " UNLOCKS ━━━━━━━━").withStyle(type.getColor()), false);
        src.sendSuccess(() -> Component.literal("Master Title: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(type.getMasterTitle()).withStyle(type.getColor(), ChatFormatting.BOLD)), false);
        src.sendSuccess(() -> Component.literal("Level 5 Perk: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(type.getMasterUnlockDescription()).withStyle(ChatFormatting.YELLOW)), false);
        src.sendSuccess(() -> Component.literal("XP Sources: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(switch (type) {
                    case FISHER -> "Catching fish, treasure, and Leviathans.";
                    case FARMER -> "Harvesting crops and cross-breeding.";
                    case MINER -> "Mining raw ores and deepslate minerals.";
                    case BLACKSMITH -> "Upgrading crowns and forging masterwork gear.";
                    case ALCHEMIST -> "Brewing potions and extracting elixirs.";
                    case HUNTER -> "Slaying monsters and claiming wanted bounties.";
                    case MERCHANT -> "Trading on the global market and caravan runs.";
                }).withStyle(ChatFormatting.WHITE)), false);
    }
}
