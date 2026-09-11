package net.kingsmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kingsmp.KingSMPMod;
import net.kingsmp.crowns.CrownManager;
import net.kingsmp.crowns.CrownType;
import net.kingsmp.data.KingDataManager;
import net.kingsmp.election.ElectionManager;
import net.kingsmp.professions.BlacksmithScreenHandler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.EntityArgument;
// import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.world.item.ItemStack;
// import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;

import static net.minecraft.commands.Commands.*;

public class KingCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            KingDataManager data,
            ElectionManager election) {

        registerKingCommands(dispatcher, data, election);
    }

    // ── COMMAND REGISTRATIONS ─────────────────────────────────────────────────

    private static void registerKingCommands(CommandDispatcher<CommandSourceStack> dispatcher, KingDataManager data,
            ElectionManager election) {

        // ── TOP-LEVEL PLAYER COMMANDS (EASIER ACCESS) ──
        dispatcher.register(literal("upgrade").executes(KingCommands::cmdUpgrade));
        dispatcher.register(literal("bal").executes(ctx -> cmdBalance(ctx, data)));
        dispatcher.register(literal("balance").executes(ctx -> cmdBalance(ctx, data)));
        dispatcher.register(literal("status").executes(ctx -> cmdStatus(ctx, data, election)));
        dispatcher.register(literal("leaderboard").executes(ctx -> cmdLeaderboard(ctx, data)));
        dispatcher.register(literal("myvote").executes(ctx -> cmdMyVote(ctx, data)));
        dispatcher.register(literal("vote")
                .then(argument("player", EntityArgument.player())
                        .executes(ctx -> cmdVote(ctx, data, election))));

        // ── THE /king COMMAND (RETAINED AS A HUB) ──
        dispatcher.register(
                literal("king")
                        // ── Admin Force Upgrade ──
                        .then(literal("forceupgrade").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("level", IntegerArgumentType.integer(1, 5))
                                                .executes(KingCommands::cmdAdminUpgrade))))

                        // Player Commands (Sub-aliases)
                        .then(literal("help").executes(KingCommands::cmdHelp))
                        .then(literal("bal")
                                .executes(ctx -> cmdBalance(ctx, data))
                                .then(argument("player", EntityArgument.player()).requires(KingCommands::isOp)
                                        .executes(ctx -> cmdAdminBalance(ctx, data))))
                        .then(literal("upgrade").executes(KingCommands::cmdUpgrade))
                        .then(literal("ability").executes(KingCommands::cmdAbility))
                        .then(literal("status").executes(ctx -> cmdStatus(ctx, data, election)))
                        .then(literal("myvote").executes(ctx -> cmdMyVote(ctx, data)))
                        .then(literal("leaderboard").executes(ctx -> cmdLeaderboard(ctx, data)))
                        .then(literal("vote")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdVote(ctx, data, election))))

                        // Admin Commands
                        .then(literal("election").requires(KingCommands::isOp)
                                .then(literal("start")
                                        .executes(ctx -> cmdElectionStart(ctx, election,
                                                ElectionManager.DEFAULT_VOTE_DURATION_TICKS))
                                        .then(argument("seconds", IntegerArgumentType.integer(30, 3600))
                                                .executes(ctx -> cmdElectionStart(ctx, election,
                                                        IntegerArgumentType.getInteger(ctx, "seconds") * 20))))
                                .then(literal("end").executes(ctx -> cmdElectionEnd(ctx, election))))
                        .then(literal("crown").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdCrown(ctx, data, null))
                                        .then(argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("skulls");
                                                    builder.suggest("gold");
                                                    builder.suggest("end");
                                                    builder.suggest("lava");
                                                    builder.suggest("ice");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> cmdCrown(ctx, data, StringArgumentType.getString(ctx, "type"))))))
                        .then(literal("dethrone").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdDethrone(ctx, data))))
                        .then(literal("reset").requires(KingCommands::isOp)
                                .executes(ctx -> cmdReset(ctx, data, election)))
                        .then(literal("mint").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(KingCommands::cmdMint))))
                        .then(literal("remove").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> cmdAdminRemove(ctx, data)))))
                        .then(literal("set").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> cmdAdminSet(ctx, data)))))
                        .then(literal("bounty").requires(KingCommands::isOp)
                                .then(literal("set")
                                        .then(argument("player", EntityArgument.player())
                                                .then(argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> cmdAdminBountySet(ctx, data)))))
                                .then(literal("remove")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdAdminBountyRemove(ctx, data)))))
                        .then(literal("unlockender").requires(KingCommands::isOp)
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdAdminUnlockEnder(ctx, data, false))
                                        .then(literal("large")
                                                .executes(ctx -> cmdAdminUnlockEnder(ctx, data, true)))))
                        .then(literal("quest").requires(KingCommands::isOp)
                                .then(literal("view")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdAdminQuestView(ctx, data))))
                                .then(literal("reset")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdAdminQuestReset(ctx, data))))
                                .then(literal("complete")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdAdminQuestComplete(ctx, data)))))
                        .then(literal("market").requires(KingCommands::isOp)
                                .then(literal("remove")
                                        .then(argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                                .executes(KingCommands::cmdAdminMarketRemove))))
                        .then(literal("reign").requires(KingCommands::isOp)
                                .then(literal("set")
                                        .then(argument("player", EntityArgument.player())
                                                .then(argument("ticks", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                                        .executes(ctx -> cmdAdminReignSet(ctx, data)))))
                                .then(literal("clear")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdAdminReignClear(ctx, data)))))
                        .then(literal("combat").requires(KingCommands::isOp)
                                .then(literal("clear")
                                        .then(argument("player", EntityArgument.player())
                                                .executes(KingCommands::cmdAdminCombatClear)))));
    }

    // ── PLAYER KING COMMANDS ──────────────────────────────────────────────────

    private static int cmdHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (syncId, playerInv, p) -> new net.kingsmp.economy.HelpScreenHandler(syncId, playerInv),
                    Component.literal("KingSMP Command Guide").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            ));
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can view the help GUI."));
        }
        return 1;
    }

    private static int cmdBalance(CommandContext<CommandSourceStack> ctx, KingDataManager data) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer p = src.getPlayerOrException();
            int bal = data.getSilver(p.getUUID());
            src.sendSuccess(
                    () -> Component.literal("💰 Your Balance: " + bal + " 🪙 Silver").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD),
                    false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can have a balance."));
        }
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx, KingDataManager data,
            ElectionManager election) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> header("KingSMP Status"), false);

        String phase = switch (election.getPhase()) {
            case VOTING -> "🗳  VOTING OPEN — " + (election.getTicksRemaining() / 20) + "s remaining";
            case CROWNING -> "👑 Crowning in progress...";
            case IDLE -> "😴 No active election";
        };
        src.sendSuccess(() -> Component.literal("  Election: " + phase).withStyle(ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.literal(""), false);

        src.sendSuccess(
                () -> Component
                        .literal("  👑 Archetype Thrones (" + data.getKingCount() + "/" + KingSMPMod.MAX_KINGS + " active):")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);

        for (CrownType type : CrownType.values()) {
            String rulerInfo = "Vacant";
            for (Map.Entry<UUID, String> entry : data.getCurrentKings().entrySet()) {
                ServerPlayer p = src.getServer().getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    ItemStack c = CrownManager.getCrownInInventory(p);
                    if (c != null && KingSMPMod.getCrownType(c) == type) {
                        long ticks = data.getReignTicks(entry.getKey());
                        long totalSeconds = ticks * 2L;
                        long mins = totalSeconds / 60L;
                        long secs = totalSeconds % 60L;
                        rulerInfo = entry.getValue() + " (" + mins + "m " + secs + "s reign, Step " + KingSMPMod.getCrownStep(c) + ")";
                        break;
                    }
                }
            }
            final String finalRuler = rulerInfo;
            src.sendSuccess(() -> Component.literal("    • " + type.getDisplayName() + ": ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(finalRuler).withStyle(finalRuler.equals("Vacant") ? ChatFormatting.GRAY : ChatFormatting.GREEN)),
                    false);
        }

        return 1;
    }

    private static int cmdVote(CommandContext<CommandSourceStack> ctx, KingDataManager data,
            ElectionManager election) {
        CommandSourceStack src = ctx.getSource();
        if (election.getPhase() != ElectionManager.Phase.VOTING) {
            src.sendFailure(Component.literal("There is no active election to vote in!"));
            return 0;
        }

        ServerPlayer voter;
        ServerPlayer candidate;
        try {
            voter = src.getPlayerOrException();
            candidate = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error processing vote."));
            return 0;
        }

        if (candidate.getUUID().equals(voter.getUUID())) {
            src.sendFailure(Component.literal("You cannot vote for yourself!"));
            return 0;
        }

        boolean isNew = data.castVote(voter.getUUID(), candidate.getUUID());

        if (isNew) {
            src.sendSuccess(() -> Component.literal("✅ Vote cast for ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(candidate.getScoreboardName()).withStyle(ChatFormatting.GOLD)), false);
        } else {
            src.sendSuccess(() -> Component.literal("🔄 Vote changed to ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(candidate.getScoreboardName()).withStyle(ChatFormatting.GOLD)), false);
        }
        return 1;
    }

    private static int cmdMyVote(CommandContext<CommandSourceStack> ctx, KingDataManager data) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (!data.hasVoted(player.getUUID())) {
                src.sendSuccess(() -> Component.literal("You haven't voted yet.").withStyle(ChatFormatting.GRAY),
                        false);
                return 1;
            }
            UUID candidateUuid = data.getCurrentVotes().get(player.getUUID());
            ServerPlayer candidate = src.getServer().getPlayerList().getPlayer(candidateUuid);
            String name = candidate != null ? candidate.getScoreboardName()
                    : candidateUuid.toString().substring(0, 8) + "...";

            src.sendSuccess(() -> Component.literal("Your current vote: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.GOLD)), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this."));
        }
        return 1;
    }

    private static int cmdLeaderboard(CommandContext<CommandSourceStack> ctx, KingDataManager data) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> header("Reign Leaderboard"), false);

        Map<UUID, Long> reignMap = data.getAllReignTicks();
        if (reignMap.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  No reign data yet.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(reignMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        int rank = 1;
        for (Map.Entry<UUID, Long> entry : sorted) {
            UUID uuid = entry.getKey();
            long ticks = entry.getValue();
            
            // Resolve name
            String name = "Offline Player";
            ServerPlayer onlinePlayer = src.getServer().getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                name = onlinePlayer.getScoreboardName();
            } else {
                String offlineName = getOfflinePlayerName(src.getServer(), uuid);
                if (offlineName != null) {
                    name = offlineName;
                } else if (data.getCurrentKings().containsKey(uuid)) {
                    name = data.getCurrentKings().get(uuid);
                } else {
                    name = "Offline Player (" + uuid.toString().substring(0, 8) + ")";
                }
            }

            long totalSeconds = ticks * 2L;
            long mins = totalSeconds / 60L;
            long secs = totalSeconds % 60L;
            String timeStr = mins + "m " + secs + "s";

            final String finalName = name;
            final int finalRank = rank;
            src.sendSuccess(
                    () -> Component.literal("  #" + finalRank + " " + finalName + "  — " + timeStr)
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            rank++;
        }
        return 1;
    }

    // ── ADMIN KING COMMANDS ───────────────────────────────────────────────────

    private static int cmdElectionStart(CommandContext<CommandSourceStack> ctx, ElectionManager election, int ticks) {
        if (!election.startElection(ticks)) {
            ctx.getSource().sendFailure(Component.literal("An election is already in progress!"));
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("Election started for " + ticks / 20 + " seconds.")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdElectionEnd(CommandContext<CommandSourceStack> ctx, ElectionManager election) {
        if (!election.forceEnd(ctx.getSource().getServer())) {
            ctx.getSource().sendFailure(Component.literal("No election is in progress."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Election ended early.").withStyle(ChatFormatting.YELLOW),
                true);
        return 1;
    }

    private static int cmdCrown(CommandContext<CommandSourceStack> ctx, KingDataManager data, String typeStr) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            if (data.isKing(player.getUUID())) {
                src.sendFailure(Component.literal(player.getScoreboardName() + " is already a king!"));
                return 0;
            }
            if (data.getKingCount() >= KingSMPMod.MAX_KINGS) {
                src.sendFailure(Component.literal("The maximum number of King positions (" + KingSMPMod.MAX_KINGS + ") is already filled!"));
                return 0;
            }

            CrownType type = CrownType.SKULLS;
            if (typeStr != null) {
                type = CrownType.fromName(typeStr);
            }

            data.addKing(player.getUUID(), player.getScoreboardName());
            ItemStack crown = KingSMPMod.createCrown(type, 1);
            if (!player.getInventory().add(crown)) {
                player.drop(crown, false);
            }

            net.kingsmp.events.PlayerEventHandler.onPlayerJoin(player, data);
            KingSMPMod.saveNow();

            CrownType finalType = type;
            src.sendSuccess(
                    () -> Component.literal("👑 Crowned " + player.getScoreboardName() + " as ruler of the " + finalType.getDisplayName() + "!")
                            .withStyle(ChatFormatting.GOLD),
                    true);

            src.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("👑 " + player.getScoreboardName() + " has claimed the " + finalType.getDisplayName() + "!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player not found."));
        }
        return 1;
    }

    private static int cmdDethrone(CommandContext<CommandSourceStack> ctx, KingDataManager data) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            if (!data.isKing(player.getUUID())) {
                src.sendFailure(Component.literal(player.getScoreboardName() + " is not a king!"));
                return 0;
            }
            data.removeKing(player.getUUID());
            KingSMPMod.saveNow();
            src.sendSuccess(() -> Component.literal("🗡 Dethroned " + player.getScoreboardName() + ".")
                    .withStyle(ChatFormatting.YELLOW), true);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player not found."));
        }
        return 1;
    }

    private static int cmdReset(CommandContext<CommandSourceStack> ctx, KingDataManager data,
            ElectionManager election) {
        CommandSourceStack src = ctx.getSource();
        if (election.getPhase() == ElectionManager.Phase.VOTING) {
            election.forceEnd(src.getServer());
        }
        data.clearKings();
        data.clearVotes();
        KingSMPMod.saveNow();
        src.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal("⚠ All kings have been removed. A new election is coming!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        src.sendSuccess(() -> Component.literal("Reset complete.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cmdMint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        KingSMPMod.dataManager.addSilver(target.getUUID(), amount);
        KingSMPMod.saveNow();
        ctx.getSource()
                .sendSuccess(
                        () -> Component.literal("Minted " + amount + " 🪙 Silver for " + target.getScoreboardName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
        return 1;
    }

    // ── UPGRADE SYSTEM ────────────────────────────────────────────────────────

    private static int cmdUpgrade(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            BlacksmithScreenHandler.open(player);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can open the Blacksmith station."));
            return 0;
        }
    }

    private static int cmdAbility(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            boolean success = CrownManager.triggerActiveAbility(player);
            return success ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use Crown abilities."));
            return 0;
        }
    }

    private static void upgradeCrown(CommandSourceStack src, ServerPlayer player, int slot, ItemStack oldCrown,
            int newLevel) {
        KingSMPMod.setCrownStep(oldCrown, newLevel);
        player.getInventory().setItem(slot, oldCrown);

        src.sendSuccess(
                () -> Component.literal("🎉 Crown Upgraded to Step " + newLevel + "!").withStyle(ChatFormatting.GREEN,
                        ChatFormatting.BOLD),
                true);
        src.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal("👑 " + player.getScoreboardName() + "'s Crown has ascended to Step " + newLevel + "!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    /**
     * Checks if the command source has moderator-level permissions.
     *
     * Per the official 1.21.11 Fabric docs
     * (https://docs.fabricmc.net/develop/commands/basics#command-requirements):
     * source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)
     *
     * This calls getPermissions() on the source and compares against the
     * Permissions.COMMANDS_MODERATOR constant (op level 2), exactly as documented.
     */
    private static boolean isOp(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR);
    }

    private static Component header(String title) {
        return Component.literal("━━━ 👑 " + title + " 👑 ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static boolean tryCharge(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item))
                found += stack.getCount();
        }

        if (found < count)
            return false;

        int needed = count;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(needed, stack.getCount());
                stack.shrink(take);
                needed -= take;
                if (needed <= 0)
                    break;
            }
        }
        return true;
    }

    // ── ADMIN UPGRADE COMMAND ─────────────────────────────────────────────────
    private static int cmdAdminUpgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int level = IntegerArgumentType.getInteger(ctx, "level");

        ItemStack crown = null;
        int crownSlot = -1;
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack stack = target.getInventory().getItem(i);
            if (KingSMPMod.isCrown(stack)) {
                crown = stack;
                crownSlot = i;
                break;
            }
        }

        if (crown == null || crownSlot == -1) {
            src.sendFailure(
                    Component.literal(target.getScoreboardName() + " does not have the Crown in their inventory!"));
            return 0;
        }

        upgradeCrown(src, target, crownSlot, crown, level);

        target.sendSystemMessage(
                Component.literal("✨ Your Crown was instantly upgraded by an Admin to Level " + level + "!")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        return 1;
    }

    private static String getOfflinePlayerName(net.minecraft.server.MinecraftServer server, UUID uuid) {
        try {
            for (java.lang.reflect.Method method : server.getClass().getMethods()) {
                if (method.getParameterCount() == 0) {
                    Class<?> returnType = method.getReturnType();
                    String returnTypeName = returnType.getSimpleName();
                    if (returnTypeName.equals("GameProfileCache") || returnTypeName.equals("UserCache")) {
                        Object cache = method.invoke(server);
                        if (cache != null) {
                            for (java.lang.reflect.Method cacheMethod : cache.getClass().getMethods()) {
                                if (cacheMethod.getParameterCount() == 1 && cacheMethod.getParameterTypes()[0] == UUID.class) {
                                    Object result = cacheMethod.invoke(cache, uuid);
                                    if (result instanceof java.util.Optional) {
                                        java.util.Optional<?> opt = (java.util.Optional<?>) result;
                                        if (opt.isPresent()) {
                                            Object profile = opt.get();
                                            java.lang.reflect.Method nameMethod;
                                            try {
                                                nameMethod = profile.getClass().getMethod("getName");
                                            } catch (NoSuchMethodException e) {
                                                nameMethod = profile.getClass().getMethod("name");
                                            }
                                            return (String) nameMethod.invoke(profile);
                                        }
                                    } else if (result != null) {
                                        java.lang.reflect.Method nameMethod;
                                        try {
                                            nameMethod = result.getClass().getMethod("getName");
                                        } catch (NoSuchMethodException e) {
                                            nameMethod = result.getClass().getMethod("name");
                                        }
                                        return (String) nameMethod.invoke(result);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return null;
    }

    private static int cmdAdminBalance(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int bal = data.getSilver(target.getUUID());
        ctx.getSource().sendSuccess(
                () -> Component.literal("💰 " + target.getScoreboardName() + "'s Balance: " + bal + " 🪙 Silver").withStyle(ChatFormatting.GOLD),
                false);
        return 1;
    }

    private static int cmdAdminRemove(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = data.getSilver(target.getUUID());
        int toRemove = Math.min(current, amount);
        data.removeSilver(target.getUUID(), toRemove, false); // Don't tax admin remove
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Removed " + toRemove + " 🪙 Silver from " + target.getScoreboardName() + ". New Balance: " + data.getSilver(target.getUUID())).withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminSet(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = data.getSilver(target.getUUID());
        if (amount > current) {
            data.addSilver(target.getUUID(), amount - current);
        } else if (amount < current) {
            data.removeSilver(target.getUUID(), current - amount, false);
        }
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set " + target.getScoreboardName() + "'s balance to " + amount + " 🪙 Silver.").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminBountySet(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        data.clearBounty(target.getUUID());
        if (amount > 0) {
            data.addBounty(target.getUUID(), amount);
        }
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set bounty for " + target.getScoreboardName() + " to " + amount + " 🪙 Silver.").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminBountyRemove(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        data.clearBounty(target.getUUID());
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Cleared bounty for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminUnlockEnder(CommandContext<CommandSourceStack> ctx, KingDataManager data, boolean large) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (large) {
            if (!data.hasUnlockedEnderChest(target.getUUID())) {
                data.unlockEnderChest(target.getUUID());
            }
            if (!data.hasUnlockedLargeEnderChest(target.getUUID())) {
                data.unlockLargeEnderChest(target.getUUID());
                // Migrate items from normal ender chest
                net.minecraft.world.inventory.PlayerEnderChestContainer oldChest = target.getEnderChestInventory();
                java.util.List<ItemStack> newChest = data.getExpandedEnderChest(target.getUUID());
                for (int i = 0; i < oldChest.getContainerSize(); i++) {
                    newChest.set(i, oldChest.getItem(i).copy());
                }
                oldChest.clearContent();
            }
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Unlocked Large Remote Ender Chest for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                    true);
        } else {
            data.unlockEnderChest(target.getUUID());
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Unlocked Normal Remote Ender Chest for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                    true);
        }
        KingSMPMod.saveNow();
        return 1;
    }

    private static int cmdAdminQuestView(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        KingDataManager.ActiveQuest quest = data.getActiveQuest(target.getUUID());
        if (quest == null) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal(target.getScoreboardName() + " has no active quest.").withStyle(ChatFormatting.YELLOW),
                    false);
        } else {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Quest for " + target.getScoreboardName() + ": " + quest.description + " (" + quest.progress + "/" + quest.target + ")").withStyle(ChatFormatting.GREEN),
                    false);
        }
        return 1;
    }

    private static int cmdAdminQuestReset(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        data.setActiveQuest(target.getUUID(), null);
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Reset active quest for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminQuestComplete(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        KingDataManager.ActiveQuest quest = data.getActiveQuest(target.getUUID());
        if (quest == null) {
            ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + " has no active quest to complete."));
            return 0;
        }
        net.kingsmp.events.PlayerBlockBreakHook.completeQuest(target, quest);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Force-completed quest for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminMarketRemove(CommandContext<CommandSourceStack> ctx) {
        long id = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id");
        net.kingsmp.shop.MarketListing listing = null;
        for (net.kingsmp.shop.MarketListing ml : KingSMPMod.marketManager.getActiveListings()) {
            if (ml.getId() == id) {
                listing = ml;
                break;
            }
        }
        if (listing == null) {
            ctx.getSource().sendFailure(Component.literal("Listing with ID " + id + " not found."));
            return 0;
        }
        KingSMPMod.marketManager.removeListing(listing);
        KingSMPMod.saveNow();
        net.kingsmp.shop.MarketListing finalListing = listing;
        ctx.getSource().sendSuccess(
                () -> Component.literal("Removed listing " + id + " (" + finalListing.getItemToSell().getHoverName().getString() + ") from the market.").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminReignSet(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long ticks = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "ticks");
        data.setReignTicks(target.getUUID(), ticks);
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set reign ticks for " + target.getScoreboardName() + " to " + ticks + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminReignClear(CommandContext<CommandSourceStack> ctx, KingDataManager data) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        data.clearReignTicks(target.getUUID());
        KingSMPMod.saveNow();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Cleared reign ticks for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int cmdAdminCombatClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        net.kingsmp.events.CombatTracker.removePlayer(target.getUUID());
        ctx.getSource().sendSuccess(
                () -> Component.literal("Cleared combat tag for " + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }
}