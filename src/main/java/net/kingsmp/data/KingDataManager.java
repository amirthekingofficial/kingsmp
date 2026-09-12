package net.kingsmp.data;

import com.google.gson.*;
import net.kingsmp.KingSMPMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class KingDataManager {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, String> currentKings = new LinkedHashMap<>();
    private final Map<UUID, UUID> currentVotes = new HashMap<>();
    private final Map<UUID, Long> reignTicks = new HashMap<>();

    // ── ECONOMY MAPS ──
    private final Map<UUID, Integer> silverBalances = new HashMap<>();
    private final Map<UUID, Integer> activeBounties = new HashMap<>();
    
    // ── AUCTION HOUSE MAP ──
    private final Map<Long, AuctionListing> activeAuctions = new LinkedHashMap<>();

    // ── HOMES MAP ──
    private final Map<UUID, HomeLocation> playerHomes = new HashMap<>();


    // ── TPAUTO MAP ──
    private final Set<UUID> tpAutoPlayers = new HashSet<>();

    // ── ENDER CHEST UNLOCK MAP ──
    private final Set<UUID> unlockedEnderChests = new HashSet<>();
    private final Map<UUID, List<ItemStack>> expandedEnderChests = new HashMap<>();
    private final Set<UUID> unlockedLargeEnderChests = new HashSet<>();

    // ── QUESTS MAP ──
    private final Map<UUID, ActiveQuest> activeQuests = new HashMap<>();

    // ── FACTION DAILY TAXES ──
    private final Map<String, Integer> dailyFactionTaxes = new HashMap<>();
    private long lastTaxDay = 0;

    // ── CROWN DUPLICATION PREVENTION ──
    private final Set<UUID> receivedInitialCrown = new HashSet<>();

    // ── HUNTER APEX PREDATOR DAILY CAP ──
    private final Map<UUID, Integer> hunterDailySilver = new HashMap<>();
    private final Map<UUID, Long> hunterDailyTimestamps = new HashMap<>();

    public static class ActiveQuest {
        public final String type; // "mining", "hunting", "farming"
        public final String difficulty; // "easy", "medium", "hard"
        public final String description;
        public int progress;
        public final int target;
        public final String targetId; // block or entity identifier, e.g. "diamond_ore"
        public final int silverReward;
        public final String itemRewardId; // e.g. "diamond", or null/empty
        public final int itemRewardCount;

        public ActiveQuest(String type, String difficulty, String description, int progress, int target, String targetId, int silverReward, String itemRewardId, int itemRewardCount) {
            this.type = type;
            this.difficulty = difficulty;
            this.description = description;
            this.progress = progress;
            this.target = target;
            this.targetId = targetId;
            this.silverReward = silverReward;
            this.itemRewardId = itemRewardId;
            this.itemRewardCount = itemRewardCount;
        }
    }

    public ActiveQuest getActiveQuest(UUID uuid) {
        return activeQuests.get(uuid);
    }

    public void setActiveQuest(UUID uuid, ActiveQuest quest) {
        if (quest == null) {
            activeQuests.remove(uuid);
        } else {
            activeQuests.put(uuid, quest);
        }
    }

    public void addFactionTax(String faction, int amount) {
        long currentDay = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
        if (currentDay != lastTaxDay) {
            dailyFactionTaxes.clear();
            lastTaxDay = currentDay;
        }
        int currentDaily = dailyFactionTaxes.getOrDefault(faction.toLowerCase(), 0);
        dailyFactionTaxes.put(faction.toLowerCase(), currentDaily + amount);
    }

    public int getFactionDailyTax(String faction) {
        long currentDay = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
        if (currentDay != lastTaxDay) {
            return 0;
        }
        return dailyFactionTaxes.getOrDefault(faction.toLowerCase(), 0);
    }


    public static class HomeLocation {
        public final String dimension;
        public final double x, y, z;
        public final float yaw, pitch;
        public final Set<UUID> sharedWith = new HashSet<>();

        public HomeLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    // ── HOMES LOGIC ──────────────────────────────────────────────────────────

    public void setHome(UUID owner, String dimension, double x, double y, double z, float yaw, float pitch) {
        HomeLocation home = playerHomes.get(owner);
        HomeLocation newHome = new HomeLocation(dimension, x, y, z, yaw, pitch);
        if (home != null) {
            newHome.sharedWith.addAll(home.sharedWith); // Preserve shared users
        }
        playerHomes.put(owner, newHome);
    }

    public HomeLocation getHome(UUID owner) {
        return playerHomes.get(owner);
    }

    public void deleteHome(UUID owner) {
        playerHomes.remove(owner);
    }

    // ── TPA LOGIC ────────────────────────────────────────────────────────────

    public boolean isTpAutoEnabled(UUID uuid) {
        return tpAutoPlayers.contains(uuid);
    }

    public boolean toggleTpAuto(UUID uuid) {
        if (tpAutoPlayers.contains(uuid)) {
            tpAutoPlayers.remove(uuid);
            return false;
        } else {
            tpAutoPlayers.add(uuid);
            return true;
        }
    }

    // ── ENDER CHEST REMOTE LOGIC ─────────────────────────────────────────────

    public boolean hasUnlockedEnderChest(UUID uuid) {
        return unlockedEnderChests.contains(uuid);
    }

    public void unlockEnderChest(UUID uuid) {
        unlockedEnderChests.add(uuid);
    }

    public boolean hasUnlockedLargeEnderChest(UUID uuid) {
        return unlockedLargeEnderChests.contains(uuid);
    }

    public void unlockLargeEnderChest(UUID uuid) {
        unlockedLargeEnderChests.add(uuid);
        if (!expandedEnderChests.containsKey(uuid)) {
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < 54; i++) {
                list.add(ItemStack.EMPTY);
            }
            expandedEnderChests.put(uuid, list);
        }
    }

    public List<ItemStack> getExpandedEnderChest(UUID uuid) {
        return expandedEnderChests.computeIfAbsent(uuid, k -> {
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < 54; i++) {
                list.add(ItemStack.EMPTY);
            }
            return list;
        });
    }


    // ── AUCTION HOUSE LOGIC ──────────────────────────────────────────────────

    public void addAuction(AuctionListing listing) {
        activeAuctions.put(listing.getId(), listing);
    }

    public AuctionListing getAuction(long id) {
        return activeAuctions.get(id);
    }

    public void removeAuction(long id) {
        activeAuctions.remove(id);
    }

    public Map<Long, AuctionListing> getAllAuctions() {
        return Collections.unmodifiableMap(activeAuctions);
    }

    // ── King queries ─────────────────────────────────────────────────────────

    public boolean isKing(UUID uuid) { return currentKings.containsKey(uuid); }
    public int getKingCount() { return currentKings.size(); }
    public Map<UUID, String> getCurrentKings() { return Collections.unmodifiableMap(currentKings); }

    public void addKing(UUID uuid, String name) {
        currentKings.put(uuid, name);
        KingSMPMod.LOGGER.info("{} ({}) has been crowned King!", name, uuid);
    }

    public void removeKing(UUID uuid) {
        String name = currentKings.remove(uuid);
        if (name != null) KingSMPMod.LOGGER.info("{} has lost the Crown.", name);
    }

    public void clearKings() { currentKings.clear(); }

    // ── Vote management ───────────────────────────────────────────────────────

    public boolean hasVoted(UUID voter) { return currentVotes.containsKey(voter); }

    public boolean castVote(UUID voter, UUID candidate) {
        boolean isNew = !currentVotes.containsKey(voter);
        currentVotes.put(voter, candidate);
        return isNew;
    }

    public Map<UUID, UUID> getCurrentVotes() { return Collections.unmodifiableMap(currentVotes); }

    public List<Map.Entry<UUID, Integer>> tallyVotes() {
        Map<UUID, Integer> tally = new HashMap<>();
        for (UUID candidate : currentVotes.values()) {
            tally.merge(candidate, 1, Integer::sum);
        }
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(tally.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        return sorted;
    }

    public void clearVotes() { currentVotes.clear(); }

    // ── Reign tracking ────────────────────────────────────────────────────────

    public void addReignTick(UUID uuid) { reignTicks.merge(uuid, 1L, Long::sum); }
    public long getReignTicks(UUID uuid) { return reignTicks.getOrDefault(uuid, 0L); }
    public Map<UUID, Long> getAllReignTicks() { return Collections.unmodifiableMap(reignTicks); }
    public void setReignTicks(UUID uuid, long ticks) {
        if (ticks <= 0) {
            reignTicks.remove(uuid);
        } else {
            reignTicks.put(uuid, ticks);
        }
    }
    public void clearReignTicks(UUID uuid) {
        reignTicks.remove(uuid);
    }

    // ── VIRTUAL CURRENCY LOGIC ──────────────────────────────────────────────

    public int getSilver(UUID uuid) { return silverBalances.getOrDefault(uuid, 0); }
    public void addSilver(UUID uuid, int amount) { silverBalances.put(uuid, getSilver(uuid) + amount); }

    /**
     * Deducts Silver from a player.
     * Note: Per Directive v2.0 (Fixing Flaw 7), Faction Tax is never minted out of thin air.
     * Taxes are deducted from real transaction proceeds in ShopScreenHandler.
     */
    public boolean removeSilver(UUID uuid, int amount) {
        int current = getSilver(uuid);
        if (current >= amount) {
            silverBalances.put(uuid, current - amount);
            return true;
        }
        return false;
    }

    public boolean removeSilver(UUID uuid, int amount, boolean applyTax) {
        return removeSilver(uuid, amount);
    }

    // ── NEW PLAYER PROTECTION WINDOW ────────────────────────────────────────
    private final Map<UUID, Long> firstJoinTimes = new HashMap<>();

    public void recordJoin(UUID uuid) {
        firstJoinTimes.putIfAbsent(uuid, System.currentTimeMillis());
    }

    public boolean isProtectedNewPlayer(UUID uuid) {
        long firstJoin = firstJoinTimes.getOrDefault(uuid, System.currentTimeMillis());
        long elapsedMillis = System.currentTimeMillis() - firstJoin;
        long protectionMillis = net.kingsmp.config.KingSMPConfig.newPlayerProtectionMinutes * 60 * 1000L;
        return elapsedMillis < protectionMillis;
    }

    public void revokeProtection(UUID uuid) {
        firstJoinTimes.put(uuid, 0L);
    }

    // ── BOUNTY LOGIC ────────────────────────────────────────────────────────

    public int getBounty(UUID target) { return activeBounties.getOrDefault(target, 0); }

    public void addBounty(UUID target, int amount) {
        activeBounties.put(target, getBounty(target) + amount);
    }

    public void clearBounty(UUID target) { activeBounties.remove(target); }

    public Map<UUID, Integer> getAllBounties() { return Collections.unmodifiableMap(activeBounties); }

    // ── CROWN INITIAL BESTOWAL ───────────────────────────────────────────────

    public boolean hasReceivedInitialCrown(UUID uuid) {
        return receivedInitialCrown.contains(uuid);
    }

    public void setReceivedInitialCrown(UUID uuid, boolean received) {
        if (received) {
            receivedInitialCrown.add(uuid);
        } else {
            receivedInitialCrown.remove(uuid);
        }
    }

    // ── HUNTER DAILY CAP ─────────────────────────────────────────────────────

    public int getHunterDailySilver(UUID uuid) {
        long now = System.currentTimeMillis();
        long lastReset = hunterDailyTimestamps.getOrDefault(uuid, 0L);
        if (now - lastReset > 24L * 60 * 60 * 1000L) {
            hunterDailySilver.put(uuid, 0);
            hunterDailyTimestamps.put(uuid, now);
            return 0;
        }
        return hunterDailySilver.getOrDefault(uuid, 0);
    }

    public int getRemainingHunterSilverCap(UUID uuid) {
        return Math.max(0, 300 - getHunterDailySilver(uuid));
    }

    public int addHunterSilverWithCap(UUID uuid, int desiredAmount) {
        int current = getHunterDailySilver(uuid);
        int allowed = Math.min(desiredAmount, Math.max(0, 300 - current));
        if (allowed > 0) {
            hunterDailySilver.put(uuid, current + allowed);
            hunterDailyTimestamps.putIfAbsent(uuid, System.currentTimeMillis());
        }
        return allowed;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private Path getSaveFile(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("kingsmp_data.json");
    }

    public void saveToDisk(MinecraftServer server) {
        JsonObject root = new JsonObject();

        // NEW: Save Auctions (Converting ItemStack to NBT String for 1.21)
        JsonArray ahArr = new JsonArray();
        activeAuctions.forEach((id, listing) -> {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("sellerUuid", listing.getSellerUuid().toString());
            o.addProperty("sellerName", listing.getSellerName());
            o.addProperty("price", listing.getPrice());
            try {
                net.minecraft.nbt.Tag nbt = net.minecraft.world.item.ItemStack.CODEC
                        .encodeStart(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), listing.getItem())
                        .getOrThrow();
                o.addProperty("itemNbt", nbt.toString());
            } catch (Exception e) {
                KingSMPMod.LOGGER.error("Failed to save auction item NBT", e);
            }
            ahArr.add(o);
        });
        root.add("auctions", ahArr);

        JsonArray kingsArr = new JsonArray();
        currentKings.forEach((uuid, name) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("name", name);
            kingsArr.add(o);
        });
        root.add("kings", kingsArr);

        JsonArray reignArr = new JsonArray();
        reignTicks.forEach((uuid, ticks) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("ticks", ticks);
            reignArr.add(o);
        });
        root.add("reignTicks", reignArr);

        // NEW: Save Silver Balances
        JsonArray silverArr = new JsonArray();
        silverBalances.forEach((uuid, amount) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("amount", amount);
            silverArr.add(o);
        });
        root.add("silver", silverArr);

        // NEW: Save Bounties
        JsonArray bountyArr = new JsonArray();
        activeBounties.forEach((uuid, amount) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("amount", amount);
            bountyArr.add(o);
        });
        root.add("bounties", bountyArr);

        // NEW: Save Homes
        JsonArray homesArr = new JsonArray();
        playerHomes.forEach((uuid, home) -> {
            JsonObject o = new JsonObject();
            o.addProperty("owner", uuid.toString());
            o.addProperty("dimension", home.dimension);
            o.addProperty("x", home.x);
            o.addProperty("y", home.y);
            o.addProperty("z", home.z);
            o.addProperty("yaw", home.yaw);
            o.addProperty("pitch", home.pitch);
            JsonArray sharedArr = new JsonArray();
            for (UUID sharedId : home.sharedWith) {
                sharedArr.add(sharedId.toString());
            }
            o.add("sharedWith", sharedArr);
            homesArr.add(o);
        });
        root.add("homes", homesArr);

        // NEW: Save tpAuto players
        JsonArray tpAutoArr = new JsonArray();
        for (UUID uuid : tpAutoPlayers) {
            tpAutoArr.add(uuid.toString());
        }
        root.add("tpAuto", tpAutoArr);

        // NEW: Save unlocked ender chests
        JsonArray enderArr = new JsonArray();
        for (UUID uuid : unlockedEnderChests) {
            enderArr.add(uuid.toString());
        }
        root.add("unlockedEnderChests", enderArr);

        // NEW: Save Factions
        root.add("factionData", net.kingsmp.factions.FactionManager.save());

        // NEW: Save Professions & First Join Protection Times
        root.add("professions", net.kingsmp.professions.ProfessionManager.save());
        JsonObject joinObj = new JsonObject();
        firstJoinTimes.forEach((uuid, time) -> joinObj.addProperty(uuid.toString(), time));
        root.add("firstJoinTimes", joinObj);

        // Save active quests
        JsonArray questArr = new JsonArray();
        activeQuests.forEach((uuid, quest) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("type", quest.type);
            o.addProperty("difficulty", quest.difficulty);
            o.addProperty("description", quest.description);
            o.addProperty("progress", quest.progress);
            o.addProperty("target", quest.target);
            o.addProperty("targetId", quest.targetId);
            o.addProperty("silverReward", quest.silverReward);
            o.addProperty("itemRewardId", quest.itemRewardId);
            o.addProperty("itemRewardCount", quest.itemRewardCount);
            questArr.add(o);
        });
        root.add("quests", questArr);

        // Save player market listings
        JsonArray marketArr = new JsonArray();
        KingSMPMod.marketManager.getActiveListings().forEach(listing -> {
            if (!listing.isServerShop()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", listing.getId());
                o.addProperty("sellerUuid", listing.getSellerId().toString());
                o.addProperty("sellerName", listing.getSellerName());
                o.addProperty("silverPrice", listing.getSilverPrice());
                try {
                    net.minecraft.nbt.Tag nbt = net.minecraft.world.item.ItemStack.CODEC
                            .encodeStart(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), listing.getItemToSell())
                            .getOrThrow();
                    o.addProperty("itemNbt", nbt.toString());
                } catch (Exception e) {
                    KingSMPMod.LOGGER.error("Failed to save market item NBT", e);
                }
                marketArr.add(o);
            }
        });
        root.add("marketListings", marketArr);


        // Save expanded ender chests
        JsonArray expandedEnderArr = new JsonArray();
        expandedEnderChests.forEach((uuid, items) -> {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            JsonArray itemsArr = new JsonArray();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("slot", i);
                    try {
                        net.minecraft.nbt.Tag nbt = net.minecraft.world.item.ItemStack.CODEC
                                .encodeStart(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), stack)
                                .getOrThrow();
                        itemObj.addProperty("itemNbt", nbt.toString());
                    } catch (Exception e) {
                        KingSMPMod.LOGGER.error("Failed to save expanded ender chest item NBT", e);
                    }
                    itemsArr.add(itemObj);
                }
            }
            o.add("items", itemsArr);
            expandedEnderArr.add(o);
        });
        root.add("expandedEnderChests", expandedEnderArr);

        // Save unlocked large ender chests
        JsonArray largeEnderUnlockArr = new JsonArray();
        for (UUID uuid : unlockedLargeEnderChests) {
            largeEnderUnlockArr.add(uuid.toString());
        }
        root.add("unlockedLargeEnderChests", largeEnderUnlockArr);

        // Save Faction Daily Taxes
        JsonObject dailyTaxesObj = new JsonObject();
        dailyFactionTaxes.forEach(dailyTaxesObj::addProperty);
        root.add("dailyFactionTaxes", dailyTaxesObj);
        root.addProperty("lastTaxDay", lastTaxDay);

        // Save received initial crown
        JsonArray crownArr = new JsonArray();
        for (UUID uuid : receivedInitialCrown) {
            crownArr.add(uuid.toString());
        }
        root.add("receivedInitialCrown", crownArr);

        // Save hunter daily silver & timestamps
        JsonObject hunterSilverObj = new JsonObject();
        hunterDailySilver.forEach((uuid, amt) -> hunterSilverObj.addProperty(uuid.toString(), amt));
        root.add("hunterDailySilver", hunterSilverObj);

        JsonObject hunterTimeObj = new JsonObject();
        hunterDailyTimestamps.forEach((uuid, time) -> hunterTimeObj.addProperty(uuid.toString(), time));
        root.add("hunterDailyTimestamps", hunterTimeObj);

        try {
            Path path = getSaveFile(server);
            Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tempPath, gson.toJson(root), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            KingSMPMod.LOGGER.error("Failed to save KingSMP data", e);
        }
    }

    public void loadFromDisk(MinecraftServer server) {
        Path path = getSaveFile(server);
        if (!Files.exists(path)) return;

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            currentKings.clear();
            if (root.has("kings")) {
                for (JsonElement el : root.getAsJsonArray("kings")) {
                    JsonObject o = el.getAsJsonObject();
                    currentKings.put(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString());
                }
            }

            reignTicks.clear();
            if (root.has("reignTicks")) {
                for (JsonElement el : root.getAsJsonArray("reignTicks")) {
                    JsonObject o = el.getAsJsonObject();
                    reignTicks.put(UUID.fromString(o.get("uuid").getAsString()), o.get("ticks").getAsLong());
                }
            }

            // NEW: Load Silver Balances
            silverBalances.clear();
            if (root.has("silver")) {
                for (JsonElement el : root.getAsJsonArray("silver")) {
                    JsonObject o = el.getAsJsonObject();
                    silverBalances.put(UUID.fromString(o.get("uuid").getAsString()), o.get("amount").getAsInt());
                }
            }

            // Load active quests
            activeQuests.clear();
            if (root.has("quests")) {
                for (JsonElement el : root.getAsJsonArray("quests")) {
                    JsonObject o = el.getAsJsonObject();
                    UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                    ActiveQuest quest = new ActiveQuest(
                            o.get("type").getAsString(),
                            o.get("difficulty").getAsString(),
                            o.get("description").getAsString(),
                            o.get("progress").getAsInt(),
                            o.get("target").getAsInt(),
                            o.get("targetId").getAsString(),
                            o.get("silverReward").getAsInt(),
                            o.has("itemRewardId") ? o.get("itemRewardId").getAsString() : "",
                            o.has("itemRewardCount") ? o.get("itemRewardCount").getAsInt() : 0
                    );
                    activeQuests.put(uuid, quest);
                }
            }

            // NEW: Load Bounties
            activeBounties.clear();
            if (root.has("bounties")) {
                for (JsonElement el : root.getAsJsonArray("bounties")) {
                    JsonObject o = el.getAsJsonObject();
                    activeBounties.put(UUID.fromString(o.get("uuid").getAsString()), o.get("amount").getAsInt());
                }
            }

            // NEW: Load Auctions
            activeAuctions.clear();
            if (root.has("auctions")) {
                for (JsonElement el : root.getAsJsonArray("auctions")) {
                    JsonObject o = el.getAsJsonObject();
                    try {
                        long id = o.get("id").getAsLong();
                        UUID sellerUuid = UUID.fromString(o.get("sellerUuid").getAsString());
                        String sellerName = o.get("sellerName").getAsString();
                        int price = o.get("price").getAsInt();

                        net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(o.get("itemNbt").getAsString());
                        ItemStack item = net.minecraft.world.item.ItemStack.CODEC
                                .parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), nbt)
                                .result()
                                .orElse(ItemStack.EMPTY);

                        activeAuctions.put(id, new AuctionListing(id, sellerUuid, sellerName, item, price));
                    } catch (Exception e) {
                        KingSMPMod.LOGGER.error("Failed to load an auction listing", e);
                    }
                }
            }

            // Load Market Listings
            if (KingSMPMod.marketManager != null) {
                KingSMPMod.marketManager.getActiveListings().removeIf(listing -> !listing.isServerShop());
                if (root.has("marketListings")) {
                    for (JsonElement el : root.getAsJsonArray("marketListings")) {
                        JsonObject o = el.getAsJsonObject();
                        try {
                            long id = o.get("id").getAsLong();
                            UUID sellerUuid = UUID.fromString(o.get("sellerUuid").getAsString());
                            String sellerName = o.get("sellerName").getAsString();
                            int silverPrice = o.get("silverPrice").getAsInt();

                            net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(o.get("itemNbt").getAsString());
                            ItemStack item = net.minecraft.world.item.ItemStack.CODEC
                                    .parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), nbt)
                                    .result()
                                    .orElse(ItemStack.EMPTY);

                            KingSMPMod.marketManager.addListing(new net.kingsmp.shop.MarketListing(id, sellerUuid, sellerName, item, silverPrice, false));
                        } catch (Exception e) {
                            KingSMPMod.LOGGER.error("Failed to load a market listing", e);
                        }
                    }
                }

                // Migrate legacy active auctions to Player Market!
                if (root.has("auctions")) {
                    for (JsonElement el : root.getAsJsonArray("auctions")) {
                        JsonObject o = el.getAsJsonObject();
                        try {
                            long id = o.get("id").getAsLong();
                            UUID sellerUuid = UUID.fromString(o.get("sellerUuid").getAsString());
                            String sellerName = o.get("sellerName").getAsString();
                            int price = o.get("price").getAsInt();

                            net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(o.get("itemNbt").getAsString());
                            ItemStack item = net.minecraft.world.item.ItemStack.CODEC
                                    .parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), nbt)
                                    .result()
                                    .orElse(ItemStack.EMPTY);

                            boolean exists = false;
                            for (net.kingsmp.shop.MarketListing existing : KingSMPMod.marketManager.getActiveListings()) {
                                if (existing.getId() == id) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists && !item.isEmpty()) {
                                KingSMPMod.marketManager.addListing(new net.kingsmp.shop.MarketListing(id, sellerUuid, sellerName, item, price, false));
                            }
                        } catch (Exception e) {
                            KingSMPMod.LOGGER.error("Failed to migrate auction listing to market", e);
                        }
                    }
                }
            }

            // NEW: Load Factions
            if (root.has("factionData")) {
                net.kingsmp.factions.FactionManager.load(root.getAsJsonObject("factionData"));
            } else {
                net.kingsmp.factions.FactionManager.load(null);
            }

            // NEW: Load Homes
            playerHomes.clear();
            if (root.has("homes")) {
                for (JsonElement el : root.getAsJsonArray("homes")) {
                    JsonObject o = el.getAsJsonObject();
                    UUID owner = UUID.fromString(o.get("owner").getAsString());
                    HomeLocation home = new HomeLocation(
                            o.get("dimension").getAsString(),
                            o.get("x").getAsDouble(),
                            o.get("y").getAsDouble(),
                            o.get("z").getAsDouble(),
                            o.get("yaw").getAsFloat(),
                            o.get("pitch").getAsFloat()
                    );
                    if (o.has("sharedWith")) {
                        for (JsonElement sharedEl : o.getAsJsonArray("sharedWith")) {
                            home.sharedWith.add(UUID.fromString(sharedEl.getAsString()));
                        }
                    }
                    playerHomes.put(owner, home);
                }
            }

            // NEW: Load tpAuto players
            tpAutoPlayers.clear();
            if (root.has("tpAuto")) {
                for (JsonElement el : root.getAsJsonArray("tpAuto")) {
                    tpAutoPlayers.add(UUID.fromString(el.getAsString()));
                }
            }

            // NEW: Load unlocked ender chests
            unlockedEnderChests.clear();
            if (root.has("unlockedEnderChests")) {
                for (JsonElement el : root.getAsJsonArray("unlockedEnderChests")) {
                    unlockedEnderChests.add(UUID.fromString(el.getAsString()));
                }
            }

            // Load unlocked large ender chests
            unlockedLargeEnderChests.clear();
            if (root.has("unlockedLargeEnderChests")) {
                for (JsonElement el : root.getAsJsonArray("unlockedLargeEnderChests")) {
                    unlockedLargeEnderChests.add(UUID.fromString(el.getAsString()));
                }
            }

            // Load expanded ender chests
            expandedEnderChests.clear();
            if (root.has("expandedEnderChests")) {
                for (JsonElement el : root.getAsJsonArray("expandedEnderChests")) {
                    JsonObject o = el.getAsJsonObject();
                    UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                    List<ItemStack> list = new ArrayList<>();
                    for (int i = 0; i < 54; i++) {
                        list.add(ItemStack.EMPTY);
                    }
                    if (o.has("items")) {
                        for (JsonElement itemEl : o.getAsJsonArray("items")) {
                            JsonObject itemObj = itemEl.getAsJsonObject();
                            int slot = itemObj.get("slot").getAsInt();
                            try {
                                net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(itemObj.get("itemNbt").getAsString());
                                ItemStack item = net.minecraft.world.item.ItemStack.CODEC
                                        .parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess()), nbt)
                                        .result()
                                        .orElse(ItemStack.EMPTY);
                                if (slot >= 0 && slot < 54) {
                                    list.set(slot, item);
                                }
                            } catch (Exception e) {
                                KingSMPMod.LOGGER.error("Failed to load expanded ender chest item", e);
                            }
                        }
                    }
                    expandedEnderChests.put(uuid, list);
                }
            }

            // Load Faction Daily Taxes
            dailyFactionTaxes.clear();
            if (root.has("dailyFactionTaxes")) {
                JsonObject dailyTaxesObj = root.getAsJsonObject("dailyFactionTaxes");
                for (Map.Entry<String, JsonElement> entry : dailyTaxesObj.entrySet()) {
                    dailyFactionTaxes.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
            if (root.has("lastTaxDay")) {
                lastTaxDay = root.get("lastTaxDay").getAsLong();
            }

            // Load Professions
            if (root.has("professions")) {
                net.kingsmp.professions.ProfessionManager.load(root.getAsJsonArray("professions"));
            }

            // Load First Join Protection Times
            firstJoinTimes.clear();
            if (root.has("firstJoinTimes")) {
                JsonObject joinObj = root.getAsJsonObject("firstJoinTimes");
                for (Map.Entry<String, JsonElement> entry : joinObj.entrySet()) {
                    firstJoinTimes.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            }

            // Load received initial crowns
            receivedInitialCrown.clear();
            if (root.has("receivedInitialCrown")) {
                for (JsonElement el : root.getAsJsonArray("receivedInitialCrown")) {
                    receivedInitialCrown.add(UUID.fromString(el.getAsString()));
                }
            }

            // Load hunter daily silver & timestamps
            hunterDailySilver.clear();
            if (root.has("hunterDailySilver")) {
                JsonObject obj = root.getAsJsonObject("hunterDailySilver");
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    hunterDailySilver.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
                }
            }

            hunterDailyTimestamps.clear();
            if (root.has("hunterDailyTimestamps")) {
                JsonObject obj = root.getAsJsonObject("hunterDailyTimestamps");
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    hunterDailyTimestamps.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            }

        } catch (Exception e) {
            KingSMPMod.LOGGER.error("Failed to load KingSMP data", e);
        }
    }
}