package net.kingsmp.commands;

import net.kingsmp.events.CombatTracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;

public class RtpCommand {

    private static final Map<ResourceKey<Level>, Queue<BlockPos>> safeLocationsCache = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Integer> activeAsyncSearches = new ConcurrentHashMap<>();
    private static final int CACHE_TARGET_SIZE = 10;
    private static final int MAX_IN_FLIGHT_SEARCHES = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
                .executes(ctx -> cmdRtp(ctx)));

        // Register server tick event to periodically replenish cache
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick(server);
        });
    }

    private static int cmdRtp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (CombatTracker.isInCombat(player)) {
                src.sendFailure(Component.literal("You cannot use random teleport while in combat! (" 
                        + CombatTracker.getRemainingSeconds(player) + "s remaining)"));
                return 0;
            }

            ServerLevel level = (ServerLevel) player.level();
            Queue<BlockPos> queue = safeLocationsCache.get(level.dimension());
            BlockPos safePos = null;
            if (queue != null) {
                safePos = queue.poll();
            }

            if (safePos == null) {
                // If cache is empty, find safe position asynchronously
                src.sendSuccess(() -> Component.literal("🔍 Finding a safe teleport location, please wait...")
                        .withStyle(ChatFormatting.YELLOW), false);
                teleportPlayerAsync(player, level, src);
                return 1;
            }

            // Verify safePos is within the current border (in case border shrunk since caching)
            if (!level.getWorldBorder().isWithinBounds(safePos)) {
                // Discard and retry (recursively or just try again)
                return cmdRtp(ctx);
            }

            teleportPlayer(player, level, safePos, src);

        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can run this command."));
        }
        return 1;
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, BlockPos safePos, CommandSourceStack src) {
        net.kingsmp.events.TeleportManager.startChannel(player, "Wilderness", () -> {
            player.teleportTo(level, safePos.getX() + 0.5, safePos.getY() + 0.1, safePos.getZ() + 0.5, 
                    java.util.Collections.emptySet(), player.getYRot(), player.getXRot(), true);

            player.sendSystemMessage(Component.literal("✨ Randomly teleported to safe coordinates: " 
                    + safePos.getX() + ", " + safePos.getY() + ", " + safePos.getZ())
                    .withStyle(ChatFormatting.AQUA));
        });
    }

    private static void teleportPlayerAsync(ServerPlayer player, ServerLevel level, CommandSourceStack src) {
        teleportPlayerAsyncAttempt(player, level, src, 0);
    }

    private static void teleportPlayerAsyncAttempt(ServerPlayer player, ServerLevel level, CommandSourceStack src, int attempt) {
        if (attempt >= 10) {
            src.sendFailure(Component.literal("❌ Could not find a safe location after 10 attempts. Please try again."));
            return;
        }

        WorldBorder border = level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        double margin = Math.min(20.0, size / 10.0);
        double range = (size / 2.0) - margin;

        Random rand = new Random();
        int x = (int) (centerX - range + rand.nextDouble() * (range * 2));
        int z = (int) (centerZ - range + rand.nextDouble() * (range * 2));

        level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
            .thenAcceptAsync(chunkResult -> {
                if (chunkResult.isSuccess()) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isSafe(level, pos)) {
                        teleportPlayer(player, level, pos, src);
                    } else {
                        teleportPlayerAsyncAttempt(player, level, src, attempt + 1);
                    }
                } else {
                    teleportPlayerAsyncAttempt(player, level, src, attempt + 1);
                }
            }, level.getServer());
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            Queue<BlockPos> queue = safeLocationsCache.computeIfAbsent(dimension, k -> new ConcurrentLinkedQueue<>());
            int active = activeAsyncSearches.getOrDefault(dimension, 0);

            if (queue.size() + active < CACHE_TARGET_SIZE && active < MAX_IN_FLIGHT_SEARCHES) {
                activeAsyncSearches.put(dimension, active + 1);
                startAsyncSearch(level, queue);
            }
        }
    }

    private static void startAsyncSearch(ServerLevel level, Queue<BlockPos> queue) {
        WorldBorder border = level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        double margin = Math.min(20.0, size / 10.0);
        double range = (size / 2.0) - margin;

        Random rand = new Random();
        int x = (int) (centerX - range + rand.nextDouble() * (range * 2));
        int z = (int) (centerZ - range + rand.nextDouble() * (range * 2));

        level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
            .thenAcceptAsync(chunkResult -> {
                try {
                    if (chunkResult.isSuccess()) {
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (isSafe(level, pos)) {
                            queue.add(pos);
                        }
                    }
                } finally {
                    activeAsyncSearches.compute(level.dimension(), (k, v) -> v == null ? 0 : Math.max(0, v - 1));
                }
            }, level.getServer());
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        BlockState groundState = level.getBlockState(pos.below());
        
        if (feetState.isAir() && headState.isAir()) {
            if (!groundState.isAir() && groundState.getFluidState().isEmpty()) {
                net.minecraft.world.level.block.Block block = groundState.getBlock();
                return block != Blocks.LAVA &&
                       block != Blocks.MAGMA_BLOCK &&
                       block != Blocks.CACTUS &&
                       block != Blocks.FIRE;
            }
        }
        return false;
    }
}
