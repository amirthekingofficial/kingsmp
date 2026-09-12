package net.kingsmp.factions;

import net.kingsmp.KingSMPMod;
import net.kingsmp.config.KingSMPConfig;
import net.kingsmp.crowns.CrownType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class FactionManager {

    // ── BACKWARD COMPATIBLE FACTION RANK ENUM ──
    public enum FactionRank { KING, COMMANDER, KNIGHT, RECRUIT }

    // ── NEW: 4 BRANCHING RANK PATHS ──
    public enum RankPath {
        MILITARY("Military", CrownType.SKULLS),
        LOGISTICS("Logistics", CrownType.GOLD),
        SCOUT("Scout", CrownType.ICE),
        OCCULT("Occult", CrownType.LAVA);

        private final String displayName;
        private final CrownType crownEligibility;

        RankPath(String displayName, CrownType crownEligibility) {
            this.displayName = displayName;
            this.crownEligibility = crownEligibility;
        }

        public String getDisplayName() {
            return displayName;
        }

        public CrownType getCrownEligibility() {
            return crownEligibility;
        }

        public static RankPath fromString(String name) {
            if (name == null) return null;
            try {
                return RankPath.valueOf(name.toUpperCase());
            } catch (Exception e) {
                return switch (name.toLowerCase()) {
                    case "mil", "military", "war" -> MILITARY;
                    case "log", "logistics", "trade", "econ" -> LOGISTICS;
                    case "scout", "ranger" -> SCOUT;
                    case "occult", "magic", "fire" -> OCCULT;
                    default -> null;
                };
            }
        }

        public String getRungTitle(int rung) {
            return switch (this) {
                case MILITARY -> switch (rung) {
                    case 2 -> "Man-at-Arms";
                    case 3 -> "Knight";
                    case 4 -> "Commander";
                    default -> "Recruit";
                };
                case LOGISTICS -> switch (rung) {
                    case 2 -> "Steward";
                    case 3 -> "Quartermaster";
                    case 4 -> "Seneschal";
                    default -> "Recruit";
                };
                case SCOUT -> switch (rung) {
                    case 2 -> "Outrider";
                    case 3 -> "Ranger";
                    case 4 -> "Pathfinder";
                    default -> "Recruit";
                };
                case OCCULT -> switch (rung) {
                    case 2 -> "Acolyte";
                    case 3 -> "Chanter";
                    case 4 -> "Warden of Lava";
                    default -> "Recruit";
                };
            };
        }
    }

    public static class OutpostLocation {
        public final String dimension;
        public final double x, y, z;
        public final float yaw, pitch;

        public OutpostLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final Map<String, Map<String, OutpostLocation>> factionOutposts = new HashMap<>();
    private static final Map<String, Map<String, OutpostLocation>> factionWaypoints = new HashMap<>();

    private static final Map<String, UUID> factions = new HashMap<>(); // FactionName -> King's UUID
    private static final Map<UUID, String> playerFactions = new HashMap<>(); // Player UUID -> FactionName
    private static final Map<String, Integer> factionTaxes = new HashMap<>(); // FactionName -> taxRate (0-5)
    private static final Map<UUID, FactionRank> playerRanks = new HashMap<>(); // Legacy rank tracker

    // Branching rank tracking
    private static final Map<UUID, RankPath> playerPaths = new HashMap<>();
    private static final Map<UUID, Integer> playerRungs = new HashMap<>(); // 1 to 4

    private static final Map<UUID, String> pendingInvites = new HashMap<>();
    private static final Map<UUID, Long> chanterBuffCooldowns = new HashMap<>();

    // ── OUTPOSTS ──────────────────────────────────────────────────────────

    public static int getMaxOutposts(String faction) {
        if (faction == null) return 3;
        UUID kingUuid = factions.get(faction.toLowerCase());
        if (kingUuid != null) {
            // If the Faction King holds Crown of the End, grant +2 outposts!
            net.minecraft.server.level.ServerPlayer king = KingSMPMod.server != null ? KingSMPMod.server.getPlayerList().getPlayer(kingUuid) : null;
            if (king != null) {
                ItemStack crown = net.kingsmp.crowns.CrownManager.getCrownInInventory(king);
                if (crown != null && KingSMPMod.getCrownType(crown) == CrownType.END) {
                    return 5;
                }
            }
        }
        return 3;
    }

    public static int getOutpostCount(String faction) {
        if (faction == null) return 0;
        Map<String, OutpostLocation> outposts = factionOutposts.get(faction.toLowerCase());
        return outposts == null ? 0 : outposts.size();
    }

    public static void setOutpost(String faction, String name, OutpostLocation loc) {
        if (faction == null) return;
        factionOutposts.putIfAbsent(faction.toLowerCase(), new HashMap<>());
        factionOutposts.get(faction.toLowerCase()).put(name.toLowerCase(), loc);
    }

    public static OutpostLocation getOutpost(String faction, String name) {
        if (faction == null) return null;
        Map<String, OutpostLocation> outposts = factionOutposts.get(faction.toLowerCase());
        return outposts == null ? null : outposts.get(name.toLowerCase());
    }

    public static boolean deleteOutpost(String faction, String name) {
        if (faction == null) return false;
        Map<String, OutpostLocation> outposts = factionOutposts.get(faction.toLowerCase());
        if (outposts != null) {
            boolean removed = outposts.remove(name.toLowerCase()) != null;
            if (outposts.isEmpty()) {
                factionOutposts.remove(faction.toLowerCase());
            }
            return removed;
        }
        return false;
    }

    public static Map<String, OutpostLocation> getOutposts(String faction) {
        if (faction == null) return new HashMap<>();
        Map<String, OutpostLocation> outposts = factionOutposts.get(faction.toLowerCase());
        return outposts == null ? new HashMap<>() : Collections.unmodifiableMap(outposts);
    }

    // ── SCOUT WAYPOINTS ───────────────────────────────────────────────────

    public static void setWaypoint(String faction, String name, OutpostLocation loc) {
        if (faction == null) return;
        factionWaypoints.putIfAbsent(faction.toLowerCase(), new HashMap<>());
        factionWaypoints.get(faction.toLowerCase()).put(name.toLowerCase(), loc);
    }

    public static OutpostLocation getWaypoint(String faction, String name) {
        if (faction == null) return null;
        Map<String, OutpostLocation> waypoints = factionWaypoints.get(faction.toLowerCase());
        return waypoints == null ? null : waypoints.get(name.toLowerCase());
    }

    public static Map<String, OutpostLocation> getWaypoints(String faction) {
        if (faction == null) return new HashMap<>();
        Map<String, OutpostLocation> waypoints = factionWaypoints.get(faction.toLowerCase());
        return waypoints == null ? new HashMap<>() : Collections.unmodifiableMap(waypoints);
    }

    // ── FACTION LIFECYCLE ─────────────────────────────────────────────────

    public static boolean createFaction(ServerPlayer king, String factionName) {
        String lowerName = factionName.toLowerCase();
        if (factions.containsKey(lowerName) || playerFactions.containsKey(king.getUUID())) return false;

        factions.put(lowerName, king.getUUID());
        playerFactions.put(king.getUUID(), lowerName);
        playerRanks.put(king.getUUID(), FactionRank.KING);
        playerRungs.put(king.getUUID(), 4);
        return true;
    }

    public static boolean invitePlayer(ServerPlayer inviter, ServerPlayer target) {
        String factionName = playerFactions.get(inviter.getUUID());
        FactionRank rank = playerRanks.get(inviter.getUUID());

        if (factionName == null || (rank != FactionRank.KING && rank != FactionRank.COMMANDER)) {
            inviter.sendSystemMessage(Component.literal("Only Kings and Commanders can invite players!").withStyle(ChatFormatting.RED));
            return false;
        }
        if (playerFactions.containsKey(target.getUUID())) {
            inviter.sendSystemMessage(Component.literal("That player is already in a faction!").withStyle(ChatFormatting.RED));
            return false;
        }

        pendingInvites.put(target.getUUID(), factionName);
        return true;
    }

    public static boolean acceptInvite(ServerPlayer player) {
        String factionName = pendingInvites.get(player.getUUID());
        if (factionName == null) {
            player.sendSystemMessage(Component.literal("You don't have any pending invites!").withStyle(ChatFormatting.RED));
            return false;
        }

        playerFactions.put(player.getUUID(), factionName);
        playerRanks.put(player.getUUID(), FactionRank.RECRUIT);
        playerRungs.put(player.getUUID(), 1);
        pendingInvites.remove(player.getUUID());
        return true;
    }

    // ── BRANCHING RANKS ───────────────────────────────────────────────────

    public static RankPath getPlayerPath(UUID uuid) {
        return playerPaths.get(uuid);
    }

    public static int getPlayerRung(UUID uuid) {
        if (factions.containsValue(uuid)) {
            return 4; // Faction Leaders/Kings always possess maximum Rung 4 standing
        }
        return playerRungs.getOrDefault(uuid, 1);
    }

    public static boolean selectPath(ServerPlayer player, RankPath newPath) {
        if (!playerFactions.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You must belong to a faction to choose a rank path!").withStyle(ChatFormatting.RED));
            return false;
        }

        RankPath currentPath = playerPaths.get(player.getUUID());
        if (currentPath == newPath) {
            player.sendSystemMessage(Component.literal("You are already on the " + newPath.getDisplayName() + " path!").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        boolean isLeader = factions.containsValue(player.getUUID());

        if (currentPath != null && !isLeader) {
            // Switching paths costs Silver and resets rung to 1 (only for non-leaders)
            int fee = KingSMPConfig.rankPathSwitchFee;
            if (KingSMPMod.dataManager.getSilver(player.getUUID()) < fee) {
                player.sendSystemMessage(Component.literal("Switching rank paths costs " + fee + " 🪙 Silver!").withStyle(ChatFormatting.RED));
                return false;
            }
            KingSMPMod.dataManager.removeSilver(player.getUUID(), fee, false);
            player.sendSystemMessage(Component.literal("Paid " + fee + " 🪙 Silver to re-specialize your rank path.").withStyle(ChatFormatting.YELLOW));
        }

        playerPaths.put(player.getUUID(), newPath);
        if (isLeader) {
            playerRungs.put(player.getUUID(), 4);
            playerRanks.put(player.getUUID(), FactionRank.KING);
        } else {
            playerRungs.put(player.getUUID(), 1);
        }
        KingSMPMod.saveNow();

        if (isLeader) {
            player.sendSystemMessage(Component.literal("⚔ Chosen Rank Path: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(newPath.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" (Faction Leader: Max Rung 4 - " + newPath.getRungTitle(4) + ")").withStyle(ChatFormatting.YELLOW)));
        } else {
            player.sendSystemMessage(Component.literal("⚔ Chosen Rank Path: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(newPath.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" (Starting at Rung 1: Recruit)").withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }

    public static boolean promotePlayerRung(ServerPlayer leader, ServerPlayer target) {
        String faction = playerFactions.get(leader.getUUID());
        if (faction == null || !faction.equalsIgnoreCase(playerFactions.get(target.getUUID()))) {
            leader.sendSystemMessage(Component.literal("You and the target must be in the same faction!").withStyle(ChatFormatting.RED));
            return false;
        }

        FactionRank leaderRank = playerRanks.get(leader.getUUID());
        if (leaderRank != FactionRank.KING && leaderRank != FactionRank.COMMANDER) {
            leader.sendSystemMessage(Component.literal("Only Faction Kings and Commanders can promote members!").withStyle(ChatFormatting.RED));
            return false;
        }

        RankPath path = playerPaths.get(target.getUUID());
        if (path == null) {
            leader.sendSystemMessage(Component.literal("Target must first choose a rank path with /rank path <name>!").withStyle(ChatFormatting.RED));
            return false;
        }

        int currentRung = getPlayerRung(target.getUUID());
        if (currentRung >= 4) {
            leader.sendSystemMessage(Component.literal("Target is already at the maximum rung (Rung 4: " + path.getRungTitle(4) + ")!").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int nextRung = currentRung + 1;
        int costSilver = nextRung * 250;

        if (KingSMPMod.dataManager.getSilver(leader.getUUID()) < costSilver) {
            leader.sendSystemMessage(Component.literal("Promoting to Rung " + nextRung + " requires " + costSilver + " 🪙 Silver from your balance!").withStyle(ChatFormatting.RED));
            return false;
        }

        KingSMPMod.dataManager.removeSilver(leader.getUUID(), costSilver, false);
        playerRungs.put(target.getUUID(), nextRung);

        // Sync legacy rank if Commander
        if (nextRung == 4 && path == RankPath.MILITARY) {
            playerRanks.put(target.getUUID(), FactionRank.COMMANDER);
        } else if (nextRung >= 3 && path == RankPath.MILITARY) {
            playerRanks.put(target.getUUID(), FactionRank.KNIGHT);
        }

        KingSMPMod.saveNow();

        String newTitle = path.getRungTitle(nextRung);
        leader.sendSystemMessage(Component.literal("Promoted " + target.getScoreboardName() + " to " + newTitle + "!").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("🎖 You have been promoted to " + newTitle + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        KingSMPMod.playSoundToPlayer(target, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        return true;
    }

    public static String getPlayerRankTitle(UUID uuid) {
        if (factions.containsValue(uuid)) {
            RankPath path = playerPaths.get(uuid);
            if (path != null) {
                return "Faction King (" + path.getRungTitle(4) + ")";
            }
            return "Faction King";
        }
        String faction = playerFactions.get(uuid);
        if (faction == null) {
            return "Citizen";
        }
        RankPath path = playerPaths.get(uuid);
        if (path != null) {
            int rung = getPlayerRung(uuid);
            return path.getRungTitle(rung);
        }
        return "Recruit";
    }

    public static boolean isEligibleForCrown(UUID uuid, CrownType crownType) {
        if (crownType == CrownType.END) {
            return factions.containsValue(uuid); // Faction King
        }
        RankPath path = playerPaths.get(uuid);
        if (path == null) return false;

        return path.getCrownEligibility() == crownType && getPlayerRung(uuid) >= 4;
    }

    // ── CHANTER OCCULT FACTION BUFF ───────────────────────────────────────

    public static boolean triggerChanterBuff(ServerPlayer chanter) {
        String faction = playerFactions.get(chanter.getUUID());
        if (faction == null) {
            chanter.sendSystemMessage(Component.literal("You must be in a faction to cast faction buffs!").withStyle(ChatFormatting.RED));
            return false;
        }

        RankPath path = playerPaths.get(chanter.getUUID());
        int rung = getPlayerRung(chanter.getUUID());

        if (path != RankPath.OCCULT || rung < 3) {
            chanter.sendSystemMessage(Component.literal("You must be an Occult Chanter (Rung 3+) to cast this buff!").withStyle(ChatFormatting.RED));
            return false;
        }

        long now = System.currentTimeMillis();
        long cd = chanterBuffCooldowns.getOrDefault(chanter.getUUID(), 0L);
        if (now < cd) {
            long remSec = (cd - now) / 1000L;
            chanter.sendSystemMessage(Component.literal("Chant is on cooldown! " + remSec + "s remaining.").withStyle(ChatFormatting.RED));
            return false;
        }

        int cdDuration = (rung == 4) ? 180 : 300; // Warden of Lava has reduced 3m CD vs 5m
        chanterBuffCooldowns.put(chanter.getUUID(), now + (cdDuration * 1000L));

        // Apply Strength I for 15s to faction members within 20 blocks
        double radiusSq = 20.0 * 20.0;
        int buffedCount = 0;
        for (ServerPlayer member : chanter.level().getServer().getPlayerList().getPlayers()) {
            if (faction.equalsIgnoreCase(playerFactions.get(member.getUUID())) && member.level() == chanter.level()) {
                if (member.distanceToSqr(chanter) <= radiusSq) {
                    member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 0, false, false, true));
                    member.sendSystemMessage(Component.literal("⚡ Blessed with Strength I by Chanter " + chanter.getScoreboardName() + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
                    buffedCount++;
                }
            }
        }

        chanter.sendSystemMessage(Component.literal("✨ Chanted blessing granted to " + buffedCount + " faction allies!").withStyle(ChatFormatting.GOLD));
        KingSMPMod.playSoundToPlayer(chanter, SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.0f);
        return true;
    }

    // ── LEGACY & MANAGEMENT METHODS ───────────────────────────────────────

    public static boolean kickPlayer(ServerPlayer kicker, ServerPlayer target) {
        String factionName = playerFactions.get(kicker.getUUID());
        FactionRank kickerRank = playerRanks.get(kicker.getUUID());

        if (factionName == null || !factionName.equals(playerFactions.get(target.getUUID()))) return false;
        if (kickerRank != FactionRank.KING && kickerRank != FactionRank.COMMANDER) return false;
        if (playerRanks.get(target.getUUID()) == FactionRank.KING) return false;

        playerFactions.remove(target.getUUID());
        playerRanks.remove(target.getUUID());
        playerPaths.remove(target.getUUID());
        playerRungs.remove(target.getUUID());
        return true;
    }

    public static boolean leaveFaction(ServerPlayer player) {
        if (!playerFactions.containsKey(player.getUUID())) return false;
        if (playerRanks.get(player.getUUID()) == FactionRank.KING) {
            player.sendSystemMessage(Component.literal("The King cannot leave! You must /faction disband.").withStyle(ChatFormatting.RED));
            return false;
        }

        playerFactions.remove(player.getUUID());
        playerRanks.remove(player.getUUID());
        playerPaths.remove(player.getUUID());
        playerRungs.remove(player.getUUID());
        return true;
    }

    public static boolean disbandFaction(ServerPlayer king) {
        String factionName = playerFactions.get(king.getUUID());
        if (factionName == null || playerRanks.get(king.getUUID()) != FactionRank.KING) return false;

        factions.remove(factionName);
        playerFactions.entrySet().removeIf(entry -> entry.getValue().equals(factionName));
        return true;
    }

    public static String getPlayerFaction(ServerPlayer player) {
        return playerFactions.get(player.getUUID());
    }

    public static String getFactionNameForDisplay(String factionName) {
        return factionName != null ? factionName.toUpperCase() : "NONE";
    }

    public static FactionRank getPlayerRank(ServerPlayer player) {
        return playerRanks.getOrDefault(player.getUUID(), null);
    }

    public static FactionRank getPlayerRankByUuid(UUID uuid) {
        return playerRanks.getOrDefault(uuid, null);
    }

    public static String getPlayerFactionByUuid(UUID uuid) {
        return playerFactions.get(uuid);
    }

    public static UUID getFactionKing(String faction) {
        if (faction == null) return null;
        return factions.get(faction.toLowerCase());
    }

    public static int getFactionTax(String faction) {
        if (faction == null) return 0;
        return factionTaxes.getOrDefault(faction.toLowerCase(), 0);
    }

    public static void setFactionTax(String faction, int tax) {
        if (faction == null) return;
        factionTaxes.put(faction.toLowerCase(), Math.max(0, Math.min(5, tax)));
    }

    public static boolean isFactionLeader(ServerPlayer player) {
        if (player == null) return false;
        String faction = getPlayerFaction(player);
        if (faction == null) return false;
        UUID kingUuid = getFactionKing(faction);
        return kingUuid != null && kingUuid.equals(player.getUUID());
    }

    public static boolean isCommander(ServerPlayer player) {
        if (player == null) return false;
        FactionRank rank = getPlayerRank(player);
        if (rank == FactionRank.COMMANDER) return true;
        RankPath path = getPlayerPath(player.getUUID());
        int rung = getPlayerRung(player.getUUID());
        return path == RankPath.MILITARY && rung >= 4;
    }

    public static RankPath getPlayerPath(ServerPlayer player) {
        return player != null ? getPlayerPath(player.getUUID()) : null;
    }

    public static int getPlayerRung(ServerPlayer player) {
        return player != null ? getPlayerRung(player.getUUID()) : 1;
    }

    public static boolean isInFactionTerritory(ServerPlayer player) {
        if (player == null) return false;
        String faction = getPlayerFaction(player);
        if (faction == null) return false;
        Map<String, OutpostLocation> outposts = getOutposts(faction);
        if (outposts == null || outposts.isEmpty()) return false;
        String currentDim = player.level().dimension().identifier().toString();
        for (OutpostLocation outpost : outposts.values()) {
            if (outpost.dimension.equalsIgnoreCase(currentDim)) {
                double dx = outpost.x - player.getX();
                double dy = outpost.y - player.getY();
                double dz = outpost.z - player.getZ();
                if ((dx * dx + dy * dy + dz * dz) <= (64.0 * 64.0)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean promotePlayer(ServerPlayer leader, ServerPlayer target, FactionRank newRank) {
        String faction = getPlayerFaction(leader);
        if (faction == null || !faction.equals(getPlayerFaction(target))) return false;
        if (!isFactionLeader(leader) && !isCommander(leader)) return false;
        playerRanks.put(target.getUUID(), newRank);
        return true;
    }

    public static List<UUID> getFactionMembers(String factionName) {
        List<UUID> members = new ArrayList<>();
        if (factionName == null) return members;
        for (Map.Entry<UUID, String> entry : playerFactions.entrySet()) {
            if (entry.getValue().equals(factionName)) {
                members.add(entry.getKey());
            }
        }
        return members;
    }

    // ── PERSISTENCE ───────────────────────────────────────────────────────

    public static com.google.gson.JsonObject save() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();

        com.google.gson.JsonArray factionsArr = new com.google.gson.JsonArray();
        factions.forEach((name, uuid) -> {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("name", name);
            o.addProperty("uuid", uuid.toString());
            factionsArr.add(o);
        });
        root.add("factions", factionsArr);

        com.google.gson.JsonArray pfArr = new com.google.gson.JsonArray();
        playerFactions.forEach((uuid, name) -> {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("name", name);
            pfArr.add(o);
        });
        root.add("playerFactions", pfArr);

        com.google.gson.JsonArray prArr = new com.google.gson.JsonArray();
        playerRanks.forEach((uuid, rank) -> {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("rank", rank.name());
            prArr.add(o);
        });
        root.add("playerRanks", prArr);

        // Save branching rank paths and rungs
        com.google.gson.JsonArray pathsArr = new com.google.gson.JsonArray();
        playerPaths.forEach((uuid, path) -> {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("path", path.name());
            o.addProperty("rung", playerRungs.getOrDefault(uuid, 1));
            pathsArr.add(o);
        });
        root.add("branchingRanks", pathsArr);

        com.google.gson.JsonObject outpostsObj = new com.google.gson.JsonObject();
        factionOutposts.forEach((factionName, outposts) -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            outposts.forEach((name, loc) -> {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("name", name);
                o.addProperty("dimension", loc.dimension);
                o.addProperty("x", loc.x);
                o.addProperty("y", loc.y);
                o.addProperty("z", loc.z);
                o.addProperty("yaw", loc.yaw);
                o.addProperty("pitch", loc.pitch);
                arr.add(o);
            });
            outpostsObj.add(factionName, arr);
        });
        root.add("outposts", outpostsObj);

        com.google.gson.JsonObject waypointsObj = new com.google.gson.JsonObject();
        factionWaypoints.forEach((factionName, waypoints) -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            waypoints.forEach((name, loc) -> {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("name", name);
                o.addProperty("dimension", loc.dimension);
                o.addProperty("x", loc.x);
                o.addProperty("y", loc.y);
                o.addProperty("z", loc.z);
                o.addProperty("yaw", loc.yaw);
                o.addProperty("pitch", loc.pitch);
                arr.add(o);
            });
            waypointsObj.add(factionName, arr);
        });
        root.add("waypoints", waypointsObj);

        com.google.gson.JsonObject taxObj = new com.google.gson.JsonObject();
        factionTaxes.forEach((factionName, tax) -> taxObj.addProperty(factionName, tax));
        root.add("taxes", taxObj);

        return root;
    }

    public static void load(com.google.gson.JsonObject root) {
        factions.clear();
        playerFactions.clear();
        playerRanks.clear();
        playerPaths.clear();
        playerRungs.clear();
        pendingInvites.clear();
        factionOutposts.clear();
        factionWaypoints.clear();
        if (root == null) return;

        if (root.has("factions")) {
            for (com.google.gson.JsonElement el : root.getAsJsonArray("factions")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                factions.put(o.get("name").getAsString(), UUID.fromString(o.get("uuid").getAsString()));
            }
        }
        if (root.has("playerFactions")) {
            for (com.google.gson.JsonElement el : root.getAsJsonArray("playerFactions")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                playerFactions.put(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString());
            }
        }
        if (root.has("playerRanks")) {
            for (com.google.gson.JsonElement el : root.getAsJsonArray("playerRanks")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                try {
                    playerRanks.put(UUID.fromString(o.get("uuid").getAsString()), FactionRank.valueOf(o.get("rank").getAsString()));
                } catch(Exception ignored) {}
            }
        }
        if (root.has("branchingRanks")) {
            for (com.google.gson.JsonElement el : root.getAsJsonArray("branchingRanks")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                try {
                    UUID id = UUID.fromString(o.get("uuid").getAsString());
                    playerPaths.put(id, RankPath.valueOf(o.get("path").getAsString()));
                    playerRungs.put(id, o.get("rung").getAsInt());
                } catch(Exception ignored) {}
            }
        }
        if (root.has("outposts")) {
            com.google.gson.JsonObject outpostsObj = root.getAsJsonObject("outposts");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : outpostsObj.entrySet()) {
                String factionName = entry.getKey();
                Map<String, OutpostLocation> outposts = new HashMap<>();
                for (com.google.gson.JsonElement el : entry.getValue().getAsJsonArray()) {
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    String name = o.get("name").getAsString();
                    OutpostLocation loc = new OutpostLocation(
                            o.get("dimension").getAsString(),
                            o.get("x").getAsDouble(),
                            o.get("y").getAsDouble(),
                            o.get("z").getAsDouble(),
                            o.get("yaw").getAsFloat(),
                            o.get("pitch").getAsFloat()
                    );
                    outposts.put(name, loc);
                }
                factionOutposts.put(factionName, outposts);
            }
        }
        if (root.has("waypoints")) {
            com.google.gson.JsonObject waypointsObj = root.getAsJsonObject("waypoints");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : waypointsObj.entrySet()) {
                String factionName = entry.getKey();
                Map<String, OutpostLocation> waypoints = new HashMap<>();
                for (com.google.gson.JsonElement el : entry.getValue().getAsJsonArray()) {
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    String name = o.get("name").getAsString();
                    OutpostLocation loc = new OutpostLocation(
                            o.get("dimension").getAsString(),
                            o.get("x").getAsDouble(),
                            o.get("y").getAsDouble(),
                            o.get("z").getAsDouble(),
                            o.get("yaw").getAsFloat(),
                            o.get("pitch").getAsFloat()
                    );
                    waypoints.put(name, loc);
                }
                factionWaypoints.put(factionName, waypoints);
            }
        }
        if (root.has("taxes")) {
            com.google.gson.JsonObject taxObj = root.getAsJsonObject("taxes");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : taxObj.entrySet()) {
                factionTaxes.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
    }
}