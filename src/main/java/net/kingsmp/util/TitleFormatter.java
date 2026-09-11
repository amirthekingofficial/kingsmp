package net.kingsmp.util;

import net.kingsmp.KingSMPMod;
import net.kingsmp.crowns.CrownManager;
import net.kingsmp.crowns.CrownType;
import net.kingsmp.factions.FactionManager;
import net.kingsmp.professions.ProfessionManager;
import net.kingsmp.professions.ProfessionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class TitleFormatter {

    public static String getPrefix(ServerPlayer player) {
        if (KingSMPMod.dataManager.isKing(player.getUUID())) {
            ItemStack crown = CrownManager.getCrownInInventory(player);
            if (crown != null) {
                CrownType type = KingSMPMod.getCrownType(crown);
                return switch (type) {
                    case SKULLS -> "War-King";
                    case GOLD -> "Merchant-King";
                    case END -> "Void-King";
                    case LAVA -> "Pyromancer-King";
                    case ICE -> "Wraith-King";
                };
            }
            return "King";
        }

        String faction = FactionManager.getPlayerFaction(player);
        if (faction != null) {
            if (FactionManager.isFactionLeader(player)) {
                return "Leader";
            }
            if (FactionManager.isCommander(player)) {
                return "Commander";
            }
            FactionManager.RankPath path = FactionManager.getPlayerPath(player);
            int rung = FactionManager.getPlayerRung(player);
            if (path != null && rung > 0) {
                return path.getRungTitle(rung);
            }
            return "Member";
        }

        return "Wanderer";
    }

    public static String getProfessionTitle(ServerPlayer player) {
        return ProfessionManager.getPrimaryProfessionTitle(player.getUUID());
    }

    public static Component formatFullTitle(ServerPlayer player) {
        String prefix = getPrefix(player);
        String prof = getProfessionTitle(player);

        MutableComponent comp = Component.empty();

        // Prefix
        comp.append(Component.literal("[" + prefix + "] ").withStyle(
                prefix.contains("King") ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
                ChatFormatting.BOLD
        ));

        // Player Name
        comp.append(Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.WHITE));

        // Profession suffix
        if (prof != null) {
            comp.append(Component.literal(", " + prof).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        return comp;
    }
}
