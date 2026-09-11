package net.kingsmp.professions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kingsmp.KingSMPMod;
import net.kingsmp.config.KingSMPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class ProfessionManager {

    // Thresholds: Level 1 (0), Level 2 (500), Level 3 (1500), Level 4 (3500), Level 5 (7000)
    public static final int[] XP_THRESHOLDS = {0, 0, 500, 1500, 3500, 7000};

    private static final Map<UUID, Map<ProfessionType, Integer>> playerLevels = new HashMap<>();
    private static final Map<UUID, Map<ProfessionType, Integer>> playerXp = new HashMap<>();

    // Cooldown for Miner's Vein Sense
    private static final Map<UUID, Long> veinSenseCooldowns = new HashMap<>();

    public static int getLevel(UUID uuid, ProfessionType type) {
        return playerLevels.computeIfAbsent(uuid, k -> new EnumMap<>(ProfessionType.class)).getOrDefault(type, 1);
    }

    public static int getXp(UUID uuid, ProfessionType type) {
        return playerXp.computeIfAbsent(uuid, k -> new EnumMap<>(ProfessionType.class)).getOrDefault(type, 0);
    }

    public static boolean isMaster(UUID uuid, ProfessionType type) {
        return getLevel(uuid, type) >= KingSMPConfig.professionMaxLevel;
    }

    public static void addXp(ServerPlayer player, ProfessionType type, int amount) {
        UUID uuid = player.getUUID();
        int currentLevel = getLevel(uuid, type);
        if (currentLevel >= KingSMPConfig.professionMaxLevel) return;

        // Multiclass penalty: If player has 2 or more other trades at Level 2+, apply penalty
        int activeTrades = 0;
        var levels = playerLevels.computeIfAbsent(uuid, k -> new EnumMap<>(ProfessionType.class));
        for (var entry : levels.entrySet()) {
            if (entry.getKey() != type && entry.getValue() >= 2) {
                activeTrades++;
            }
        }

        double finalAmount = amount;
        if (activeTrades >= 2) {
            finalAmount *= (1.0 - KingSMPConfig.multiclassXpPenalty); // 50% XP penalty
        }

        int currentXp = getXp(uuid, type) + (int) Math.max(1, Math.round(finalAmount));
        playerXp.computeIfAbsent(uuid, k -> new EnumMap<>(ProfessionType.class)).put(type, currentXp);

        // Check level up
        int nextLevel = currentLevel + 1;
        if (nextLevel <= KingSMPConfig.professionMaxLevel && currentXp >= XP_THRESHOLDS[nextLevel]) {
            levels.put(type, nextLevel);
            KingSMPMod.saveNow();

            String newTitle = type.getLevelTitle(nextLevel);
            player.sendSystemMessage(Component.literal("🎉 PROFESSION LEVEL UP! ").withStyle(type.getColor(), ChatFormatting.BOLD)
                    .append(Component.literal("You are now a ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(newTitle).withStyle(type.getColor(), ChatFormatting.BOLD)));

            if (nextLevel == KingSMPConfig.professionMaxLevel) {
                player.sendSystemMessage(Component.literal("★ MASTER UNLOCK: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(type.getMasterUnlockDescription()).withStyle(ChatFormatting.YELLOW)));
            }

            KingSMPMod.playSoundToPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    public static String getPrimaryProfessionTitle(UUID uuid) {
        var levels = playerLevels.get(uuid);
        if (levels == null || levels.isEmpty()) {
            return "Apprentice";
        }

        ProfessionType highestType = null;
        int maxLvl = 1;
        int maxXp = 0;

        for (var entry : levels.entrySet()) {
            int lvl = entry.getValue();
            int xp = getXp(uuid, entry.getKey());
            if (lvl > maxLvl || (lvl == maxLvl && xp > maxXp)) {
                maxLvl = lvl;
                maxXp = xp;
                highestType = entry.getKey();
            }
        }

        if (highestType == null || maxLvl <= 1) {
            return "Apprentice";
        }
        return highestType.getLevelTitle(maxLvl);
    }

    // ── MINER VEIN SENSE ──────────────────────────────────────────────────

    public static boolean triggerVeinSense(ServerPlayer player) {
        if (!isMaster(player.getUUID(), ProfessionType.MINER)) {
            player.sendSystemMessage(Component.literal("Only Master Miners can use Vein Sense!").withStyle(ChatFormatting.RED));
            return false;
        }

        long now = System.currentTimeMillis();
        long cd = veinSenseCooldowns.getOrDefault(player.getUUID(), 0L);
        if (now < cd) {
            long remSec = (cd - now) / 1000L;
            player.sendSystemMessage(Component.literal("Vein Sense is on cooldown! " + remSec + "s remaining.").withStyle(ChatFormatting.RED));
            return false;
        }

        veinSenseCooldowns.put(player.getUUID(), now + (300 * 1000L)); // 5 minute cooldown

        // Scan 12-block cube for precious ores (diamond, ancient debris)
        int found = 0;
        net.minecraft.core.BlockPos pos = player.blockPosition();
        var level = player.level();

        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    var bPos = pos.offset(dx, dy, dz);
                    var state = level.getBlockState(bPos);
                    if (state.is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE) ||
                        state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE) ||
                        state.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS)) {
                        found++;
                    }
                }
            }
        }

        player.sendSystemMessage(Component.literal("💎 Vein Sense pulse detected " + found + " precious ore blocks nearby!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        KingSMPMod.playSoundToPlayer(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
        return true;
    }

    // ── PERSISTENCE ───────────────────────────────────────────────────────

    public static JsonArray save() {
        JsonArray arr = new JsonArray();
        playerLevels.forEach((uuid, map) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            JsonObject levelsObj = new JsonObject();
            map.forEach((type, lvl) -> levelsObj.addProperty(type.name(), lvl));
            o.add("levels", levelsObj);

            JsonObject xpObj = new JsonObject();
            var xpMap = playerXp.get(uuid);
            if (xpMap != null) {
                xpMap.forEach((type, xp) -> xpObj.addProperty(type.name(), xp));
            }
            o.add("xp", xpObj);
            arr.add(o);
        });
        return arr;
    }

    public static void load(JsonArray arr) {
        playerLevels.clear();
        playerXp.clear();
        if (arr == null) return;

        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            UUID uuid = UUID.fromString(o.get("uuid").getAsString());

            if (o.has("levels")) {
                JsonObject levelsObj = o.getAsJsonObject("levels");
                var map = new EnumMap<ProfessionType, Integer>(ProfessionType.class);
                levelsObj.entrySet().forEach(e -> {
                    try {
                        map.put(ProfessionType.valueOf(e.getKey()), e.getValue().getAsInt());
                    } catch (Exception ignored) {}
                });
                playerLevels.put(uuid, map);
            }

            if (o.has("xp")) {
                JsonObject xpObj = o.getAsJsonObject("xp");
                var map = new EnumMap<ProfessionType, Integer>(ProfessionType.class);
                xpObj.entrySet().forEach(e -> {
                    try {
                        map.put(ProfessionType.valueOf(e.getKey()), e.getValue().getAsInt());
                    } catch (Exception ignored) {}
                });
                playerXp.put(uuid, map);
            }
        }
    }
}
