package net.kingsmp.election;

import net.kingsmp.KingSMPMod;
import net.kingsmp.crowns.CrownType;
import net.kingsmp.data.KingDataManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;

/**
 * Manages the election lifecycle:
 * IDLE → (admin starts) → VOTING → (time expires / admin ends) → CROWNING →
 * IDLE
 *
 * Voting period defaults to 10 minutes (12 000 ticks).
 */
public class ElectionManager {

    public enum Phase {
        IDLE, VOTING, CROWNING
    }

    // 10 minutes in ticks
    public static final int DEFAULT_VOTE_DURATION_TICKS = 12_000;

    private Phase phase = Phase.IDLE;
    private int ticksRemaining = 0;
    private MinecraftServer server;

    private final KingDataManager data;

    public ElectionManager(KingDataManager data) {
        this.data = data;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    // ── Start election ─────────────────────────────────────────────────────

    public boolean startElection(int durationTicks) {
        if (phase != Phase.IDLE)
            return false;

        data.clearVotes();
        data.clearKings();

        phase = Phase.VOTING;
        ticksRemaining = durationTicks;

        broadcastTitle(
                Component.literal("⚔ ELECTION STARTED ⚔").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Use /king vote <player> to cast your vote!").withStyle(ChatFormatting.YELLOW));
        broadcast(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.GOLD));
        broadcast(Component.literal("  A new election has begun! Only the person who is #1 gets the Crown.")
                .withStyle(ChatFormatting.YELLOW));
        broadcast(Component.literal("  Type /king vote <playername> to vote.")
                .withStyle(ChatFormatting.WHITE));
        broadcast(Component.literal("  Election ends in " + (durationTicks / 20) + " seconds.")
                .withStyle(ChatFormatting.GRAY));
        broadcast(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.GOLD));

        return true;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (phase != Phase.VOTING)
            return;

        ticksRemaining--;

        // Countdown announcements
        int secondsLeft = ticksRemaining / 20;
        if (ticksRemaining == 6000)
            announceTimeLeft("5 minutes");
        if (ticksRemaining == 1800)
            announceTimeLeft("90 seconds");
        if (ticksRemaining == 600)
            announceTimeLeft("30 seconds");
        if (ticksRemaining == 200)
            announceTimeLeft("10 seconds");
        if (ticksRemaining <= 100 && ticksRemaining > 0 && ticksRemaining % 20 == 0) {
            broadcast(Component.literal("⏱ " + secondsLeft + " seconds remaining!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        if (ticksRemaining <= 0) {
            concludeElection(server);
        }
    }

    // ── Conclude ──────────────────────────────────────────────────────────

    public void concludeElection(MinecraftServer server) {
        if (phase == Phase.IDLE)
            return;

        phase = Phase.CROWNING;

        List<Map.Entry<UUID, Integer>> tally = data.tallyVotes();

        broadcast(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.GOLD));
        broadcast(Component.literal("  👑  ELECTION RESULTS  👑").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (tally.isEmpty()) {
            broadcast(Component.literal("  No votes were cast! No kings elected.")
                    .withStyle(ChatFormatting.RED));
            phase = Phase.IDLE;
            return;
        }

        int crownedCount = 0;
        for (Map.Entry<UUID, Integer> entry : tally) {
            if (crownedCount >= KingSMPMod.MAX_KINGS)
                break;

            UUID uuid = entry.getKey();
            int votes = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            String name = (player != null)
                    ? player.getScoreboardName()
                    : uuid.toString().substring(0, 8);

            data.addKing(uuid, name);

            // Give crown item if player is online
            if (player != null) {
                giveCrown(player);
                player.sendSystemMessage(
                        Component.literal("👑 You have been crowned a King of KingSMP! (" + votes + " votes)")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }

            broadcast(Component.literal("  #" + (crownedCount + 1) + " ► " + name
                    + "  [" + votes + " vote" + (votes == 1 ? "" : "s") + "]")
                    .withStyle(crownedCount == 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));

            crownedCount++;
        }

        broadcast(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.GOLD));

        broadcastTitle(
                Component.literal("👑 KINGS CROWNED 👑").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("A new King has been chosen!").withStyle(ChatFormatting.YELLOW));

        data.clearVotes();
        phase = Phase.IDLE;
    }

    // ── Force end ─────────────────────────────────────────────────────────

    public boolean forceEnd(MinecraftServer server) {
        if (phase == Phase.IDLE)
            return false;
        concludeElection(server);
        return true;
    }

    private CrownType determineArchetype(ServerPlayer player) {
        if (player == null) return CrownType.GOLD;
        if (net.kingsmp.factions.FactionManager.isFactionLeader(player)) {
            return CrownType.END;
        }
        net.kingsmp.factions.FactionManager.RankPath path = net.kingsmp.factions.FactionManager.getPlayerPath(player);
        if (path != null) {
            return switch (path) {
                case MILITARY -> CrownType.SKULLS;
                case LOGISTICS -> CrownType.GOLD;
                case OCCULT -> CrownType.LAVA;
                case SCOUT -> CrownType.ICE;
            };
        }
        return CrownType.GOLD;
    }

    private void giveCrown(ServerPlayer player) {
        CrownType archetype = determineArchetype(player);
        ItemStack crown = KingSMPMod.createCrown(archetype, 1);

        if (!player.getInventory().add(crown)) {
            player.drop(crown, false);
        }
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────

    private void broadcast(Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        if (server == null)
            return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
        }
    }

    private void announceTimeLeft(String timeStr) {
        broadcast(Component.literal("⏱ Election ends in " + timeStr + "!")
                .withStyle(ChatFormatting.YELLOW));
    }
}