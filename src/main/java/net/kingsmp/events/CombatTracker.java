package net.kingsmp.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker {
    public static final long COMBAT_TAG_DURATION_MS = 15000; // 15 seconds
    private static final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();

    public static void tag(ServerPlayer victim, ServerPlayer attacker) {
        long expireTime = System.currentTimeMillis() + COMBAT_TAG_DURATION_MS;
        
        // Tag victim
        tagPlayer(victim, expireTime);
        // Tag attacker
        tagPlayer(attacker, expireTime);
    }

    private static void tagPlayer(ServerPlayer player, long expireTime) {
        UUID uuid = player.getUUID();
        boolean alreadyInCombat = isInCombat(player);
        combatTags.put(uuid, expireTime);

        if (!alreadyInCombat) {
            player.sendSystemMessage(Component.literal("⚔️ You are now in combat! Do not log out or teleport.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    public static boolean isInCombat(ServerPlayer player) {
        if (player == null) return false;
        Long expireTime = combatTags.get(player.getUUID());
        return expireTime != null && System.currentTimeMillis() < expireTime;
    }

    public static long getRemainingSeconds(ServerPlayer player) {
        if (player == null) return 0;
        Long expireTime = combatTags.get(player.getUUID());
        if (expireTime == null) return 0;
        long diff = expireTime - System.currentTimeMillis();
        return diff <= 0 ? 0 : (diff + 999) / 1000; // round up
    }

    public static void removePlayer(UUID uuid) {
        combatTags.remove(uuid);
    }
}
