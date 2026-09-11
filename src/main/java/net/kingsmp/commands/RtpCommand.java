package net.kingsmp.commands;

import net.kingsmp.events.CombatTracker;
import net.kingsmp.events.TeleportManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Random;

public class RtpCommand {

    private static final int MIN_RADIUS = 500;
    private static final int MAX_RADIUS = 3000;
    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
                .executes(RtpCommand::cmdRtp));
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

            if (TeleportManager.isChanneling(player.getUUID())) {
                src.sendFailure(Component.literal("You are already teleporting! Stand still."));
                return 0;
            }

            ServerLevel level = (ServerLevel) player.level();
            if (level.dimension() != Level.OVERWORLD) {
                src.sendFailure(Component.literal("Random teleport is only permitted in the Overworld!"));
                return 0;
            }

            // Start stationary channel, then teleport upon warmup
            TeleportManager.startChannel(player, "Wilderness", () -> {
                findAndTeleport(player, level, 0);
            });

        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can run this command."));
        }
        return 1;
    }

    private static void findAndTeleport(ServerPlayer player, ServerLevel level, int attempt) {
        if (attempt >= 5) {
            player.sendSystemMessage(Component.literal("❌ Could not find a safe wilderness location. Please try /rtp again.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        WorldBorder border = level.getWorldBorder();
        double maxBound = Math.min(MAX_RADIUS, border.getSize() / 2.0 - 50);
        if (maxBound <= MIN_RADIUS) {
            maxBound = Math.max(MIN_RADIUS + 100, border.getSize() / 2.0 - 10);
        }

        double angle = RANDOM.nextDouble() * 2 * Math.PI;
        double dist = MIN_RADIUS + RANDOM.nextDouble() * (maxBound - MIN_RADIUS);

        int targetX = (int) (border.getCenterX() + Math.cos(angle) * dist);
        int targetZ = (int) (border.getCenterZ() + Math.sin(angle) * dist);

        level.getChunkSource().getChunkFuture(targetX >> 4, targetZ >> 4, ChunkStatus.FULL, true)
            .thenAcceptAsync(chunkResult -> {
                if (chunkResult.isSuccess()) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
                    BlockPos candidate = new BlockPos(targetX, y, targetZ);

                    if (isSafe(level, candidate)) {
                        teleportPlayer(player, level, candidate);
                        return;
                    }

                    // Try nearby blocks in same chunk
                    for (int dx = -4; dx <= 4; dx += 2) {
                        for (int dz = -4; dz <= 4; dz += 2) {
                            int nx = targetX + dx;
                            int nz = targetZ + dz;
                            int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz);
                            BlockPos alt = new BlockPos(nx, ny, nz);
                            if (isSafe(level, alt)) {
                                teleportPlayer(player, level, alt);
                                return;
                            }
                        }
                    }
                }

                // If this attempt wasn't safe, try next attempt
                findAndTeleport(player, level, attempt + 1);
            }, level.getServer());
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, BlockPos safePos) {
        player.teleportTo(level, safePos.getX() + 0.5, safePos.getY() + 0.1, safePos.getZ() + 0.5, 
                java.util.Collections.emptySet(), player.getYRot(), player.getXRot(), true);

        player.sendSystemMessage(Component.literal("✨ Randomly teleported to safe coordinates: " 
                + safePos.getX() + ", " + safePos.getY() + ", " + safePos.getZ())
                .withStyle(ChatFormatting.AQUA));
        net.kingsmp.KingSMPMod.playSoundToPlayer(player, net.minecraft.sounds.SoundEvents.PLAYER_TELEPORT, 1.0f, 1.0f);
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinY() + 5 || pos.getY() >= level.getMaxY() - 5) {
            return false;
        }

        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        BlockState groundState = level.getBlockState(pos.below());

        if (feetState.isAir() && headState.isAir()) {
            if (!groundState.isAir() && groundState.getFluidState().isEmpty()) {
                net.minecraft.world.level.block.Block block = groundState.getBlock();
                return block != Blocks.LAVA &&
                       block != Blocks.MAGMA_BLOCK &&
                       block != Blocks.CACTUS &&
                       block != Blocks.FIRE &&
                       block != Blocks.POWDER_SNOW &&
                       block != Blocks.WATER;
            }
        }
        return false;
    }
}
