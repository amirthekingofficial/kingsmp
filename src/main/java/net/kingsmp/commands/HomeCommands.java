package net.kingsmp.commands;

import net.kingsmp.KingSMPMod;
import net.kingsmp.events.CombatTracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.kingsmp.data.KingDataManager;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
// import net.minecraft.resources.Identifier;
// import net.minecraft.resources.ResourceKey;
// import net.minecraft.core.registries.Registries;
import java.util.UUID;

public class HomeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, KingDataManager dataManager) {
        dispatcher.register(Commands.literal("home")
                .executes(ctx -> cmdHomeTp(ctx, dataManager, null))
                .then(Commands.literal("set")
                        .executes(ctx -> cmdHomeSet(ctx, dataManager)))
                .then(Commands.literal("tp")
                        .executes(ctx -> cmdHomeTp(ctx, dataManager, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdHomeTp(ctx, dataManager, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("delete")
                        .executes(ctx -> cmdHomeDelete(ctx, dataManager)))
                .then(Commands.literal("share")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdHomeShare(ctx, dataManager)))));
    }

    private static int cmdHomeSet(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            String dimension = player.level().dimension().identifier().toString();
            dataManager.setHome(player.getUUID(), dimension, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            KingSMPMod.saveNow();

            src.sendSuccess(() -> Component.literal("🏠 Home set successfully at your current location!")
                    .withStyle(ChatFormatting.GREEN), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can set a home."));
        }
        return 1;
    }

    private static int cmdHomeTp(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager,
            ServerPlayer targetPlayer) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (CombatTracker.isInCombat(player)) {
                src.sendFailure(Component.literal("You cannot use home teleport while in combat! (" + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
                return 0;
            }
            UUID targetUuid = targetPlayer != null ? targetPlayer.getUUID() : player.getUUID();
            KingDataManager.HomeLocation home = dataManager.getHome(targetUuid);

            if (home == null) {
                if (targetPlayer != null) {
                    src.sendFailure(Component.literal("That player does not have a home set."));
                } else {
                    src.sendFailure(Component.literal("You don't have a home set! Use /home set."));
                }
                return 0;
            }

            if (targetPlayer != null && !targetUuid.equals(player.getUUID())
                    && !home.sharedWith.contains(player.getUUID())) {
                src.sendFailure(Component.literal("You do not have permission to teleport to this player's home.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            ServerLevel world = src.getServer()
                    .getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.Identifier.parse(home.dimension)));
            if (world == null) {
                src.sendFailure(Component.literal("The dimension your home is in could not be found!"));
                return 0;
            }

            net.kingsmp.events.TeleportManager.startChannel(player, "Home", () -> {
                player.teleportTo(world, home.x, home.y, home.z, java.util.Collections.emptySet(), home.yaw, home.pitch,
                        true);
                player.sendSystemMessage(Component.literal("✨ Teleported to home!").withStyle(ChatFormatting.AQUA));
            });
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can teleport."));
        }
        return 1;
    }

    private static int cmdHomeDelete(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (dataManager.getHome(player.getUUID()) != null) {
                dataManager.deleteHome(player.getUUID());
                KingSMPMod.saveNow();
                src.sendSuccess(
                        () -> Component.literal("🗑️ Home deleted successfully.").withStyle(ChatFormatting.YELLOW),
                        false);
            } else {
                src.sendFailure(Component.literal("You don't have a home set!"));
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can delete a home."));
        }
        return 1;
    }

    private static int cmdHomeShare(CommandContext<CommandSourceStack> ctx, KingDataManager dataManager) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            KingDataManager.HomeLocation home = dataManager.getHome(player.getUUID());
            if (home == null) {
                src.sendFailure(Component.literal("You need to set a home first with /home set!"));
                return 0;
            }

            if (player.getUUID().equals(target.getUUID())) {
                src.sendFailure(Component.literal("You cannot share a home with yourself."));
                return 0;
            }

            if (home.sharedWith.contains(target.getUUID())) {
                home.sharedWith.remove(target.getUUID());
                KingSMPMod.saveNow();
                src.sendSuccess(() -> Component.literal("🔒 Unshared your home with ").withStyle(ChatFormatting.YELLOW)
                        .append(target.getName().copy().withStyle(ChatFormatting.GOLD)), false);
            } else {
                home.sharedWith.add(target.getUUID());
                KingSMPMod.saveNow();
                src.sendSuccess(() -> Component.literal("🤝 Shared your home with ").withStyle(ChatFormatting.GREEN)
                        .append(target.getName().copy().withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("! They can teleport using /home tp " + player.getScoreboardName())
                                .withStyle(ChatFormatting.GRAY)),
                        false);

                target.sendSystemMessage(Component.literal("🏠 ").withStyle(ChatFormatting.GREEN)
                        .append(player.getName().copy().withStyle(ChatFormatting.GOLD))
                        .append(Component
                                .literal(" has shared their home with you! Use /home tp " + player.getScoreboardName())
                                .withStyle(ChatFormatting.GRAY)));
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error sharing home."));
        }
        return 1;
    }
}
