package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.factions.FactionManager;
import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.kingsmp.util.TitleFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class WhoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, KingDataManager dataManager) {
        dispatcher.register(Commands.literal("who")
                .executes(ctx -> cmdWhoAll(ctx, dataManager))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cmdWhoPlayer(ctx, EntityArgument.getPlayer(ctx, "player"), dataManager))));
    }

    private static int cmdWhoAll(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        List<ServerPlayer> players = src.getServer().getPlayerList().getPlayers();

        src.sendSuccess(() -> Component.literal("━━━ 👥 Online Citizens (" + players.size() + ") ━━━")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (ServerPlayer p : players) {
            Component formatted = TitleFormatter.formatFullTitle(p);
            String faction = FactionManager.getPlayerFaction(p);
            String factionTag = faction != null ? " [" + faction + "]" : "";

            src.sendSuccess(() -> Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(formatted)
                    .append(Component.literal(factionTag).withStyle(ChatFormatting.AQUA)),
                    false);
        }

        return 1;
    }

    private static int cmdWhoPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();

        src.sendSuccess(() -> Component.literal("━━━ 📜 Dossier: " + target.getScoreboardName() + " ━━━")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Title & Faction
        Component fullTitle = TitleFormatter.formatFullTitle(target);
        src.sendSuccess(() -> Component.literal(" Title: ").withStyle(ChatFormatting.YELLOW)
                .append(fullTitle), false);

        String faction = FactionManager.getPlayerFaction(target);
        FactionManager.RankPath path = FactionManager.getPlayerPath(target);
        int rung = FactionManager.getPlayerRung(target);

        if (faction != null) {
            String rankStr = path != null ? path.getDisplayName() + " (Rung " + rung + ": " + path.getRungTitle(rung) + ")" : "Unassigned";
            src.sendSuccess(() -> Component.literal(" Faction: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(faction).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(" | Rank: " + rankStr).withStyle(ChatFormatting.GRAY)), false);
        } else {
            src.sendSuccess(() -> Component.literal(" Faction: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("None (Independent Wanderer)").withStyle(ChatFormatting.GRAY)), false);
        }

        // Silver & Bounty
        int silver = dataManager.getSilver(target.getUUID());
        int bounty = dataManager.getBounty(target.getUUID());
        src.sendSuccess(() -> Component.literal(" Economy: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(silver + " 🪙 Silver").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(bounty > 0 ? " | Bounty: " + bounty + " 🪙" : " | No Bounty").withStyle(bounty > 0 ? ChatFormatting.RED : ChatFormatting.GRAY)),
                false);

        // Professions
        StringBuilder profs = new StringBuilder();
        for (ProfessionType pt : ProfessionType.values()) {
            int lvl = ProfessionManager.getLevel(target.getUUID(), pt);
            int xp = ProfessionManager.getXp(target.getUUID(), pt);
            profs.append(pt.getDisplayName()).append(" Lv.").append(lvl).append(" (").append(xp).append(" XP), ");
        }
        if (profs.length() > 2) {
            profs.setLength(profs.length() - 2);
        }
        src.sendSuccess(() -> Component.literal(" Professions: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(profs.toString()).withStyle(ChatFormatting.GRAY)), false);

        // Protection status
        if (dataManager.isProtectedNewPlayer(target.getUUID())) {
            src.sendSuccess(() -> Component.literal(" Protection: 🛡 New Player Protection Active (Safe from hostile PvP/Raids)")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        return 1;
    }
}
