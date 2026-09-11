package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.kingsmp.KingSMPMod;
import net.kingsmp.data.KingDataManager.ActiveQuest;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.UUID;

public class QuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quest")
                .executes(QuestCommand::cmdQuestHelp)
                .then(Commands.literal("accept")
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(QuestCommand::cmdQuestAccept)))
                .then(Commands.literal("status")
                        .executes(QuestCommand::cmdQuestStatus))
                .then(Commands.literal("cancel")
                        .executes(QuestCommand::cmdQuestCancel))
                .then(Commands.literal("abandon")
                        .executes(QuestCommand::cmdQuestCancel))
        );
    }

    private static int cmdQuestHelp(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (syncId, playerInv, p) -> new net.kingsmp.economy.QuestScreenHandler(syncId, playerInv),
                    Component.literal("Daily Contracts").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD)
            ));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdQuestAccept(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();
            
            if (KingSMPMod.dataManager.getActiveQuest(uuid) != null) {
                player.sendSystemMessage(Component.literal("❌ You already have an active quest! Use /quest cancel to abandon it first.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            String difficulty = StringArgumentType.getString(ctx, "difficulty").toLowerCase();
            ActiveQuest quest;

            switch (difficulty) {
                case "easy":
                    quest = new ActiveQuest("mining", "easy", "Mine 32 Iron Ores", 0, 32, "iron_ore", 200, "", 0);
                    break;
                case "medium":
                    quest = new ActiveQuest("hunting", "medium", "Hunt 10 Zombies", 0, 10, "zombie", 500, "diamond", 1);
                    break;
                case "hard":
                    quest = new ActiveQuest("mining", "hard", "Mine 128 Diamond Ores", 0, 128, "diamond_ore", 1000, "diamond", 4);
                    break;
                default:
                    player.sendSystemMessage(Component.literal("❌ Unknown difficulty. Choose easy, medium, or hard.")
                            .withStyle(ChatFormatting.RED));
                    return 0;
            }

            KingSMPMod.dataManager.setActiveQuest(uuid, quest);
            KingSMPMod.saveNow();

            player.sendSystemMessage(Component.literal("📜 Quest Accepted: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(quest.description).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdQuestStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();
            ActiveQuest quest = KingSMPMod.dataManager.getActiveQuest(uuid);

            if (quest == null) {
                player.sendSystemMessage(Component.literal("❌ You do not have an active quest. Use /quest to view options.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            player.sendSystemMessage(Component.literal("━━━ 📜 Active Quest Status ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal("Quest: ").withStyle(ChatFormatting.GRAY).append(Component.literal(quest.description).withStyle(ChatFormatting.YELLOW)));
            player.sendSystemMessage(Component.literal("Progress: ").withStyle(ChatFormatting.GRAY).append(Component.literal(quest.progress + "/" + quest.target).withStyle(ChatFormatting.GREEN)));
            player.sendSystemMessage(Component.literal("Difficulty: ").withStyle(ChatFormatting.GRAY).append(Component.literal(quest.difficulty.toUpperCase()).withStyle(ChatFormatting.AQUA)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdQuestCancel(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();
            ActiveQuest quest = KingSMPMod.dataManager.getActiveQuest(uuid);

            if (quest == null) {
                player.sendSystemMessage(Component.literal("❌ You do not have an active quest to cancel.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            KingSMPMod.dataManager.setActiveQuest(uuid, null);
            KingSMPMod.saveNow();

            player.sendSystemMessage(Component.literal("🗑️ Quest '" + quest.description + "' has been abandoned.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
