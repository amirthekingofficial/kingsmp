package net.kingsmp.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuestManager {

    public static class QuestTemplate {
        public final String type; // "mining", "hunting"
        public final String difficulty; // "easy", "medium", "hard"
        public final String description;
        public final int target;
        public final String targetId;
        public final int silverReward;
        public final String itemRewardId;
        public final int itemRewardCount;

        public QuestTemplate(String type, String difficulty, String description, int target, String targetId, int silverReward, String itemRewardId, int itemRewardCount) {
            this.type = type;
            this.difficulty = difficulty;
            this.description = description;
            this.target = target;
            this.targetId = targetId;
            this.silverReward = silverReward;
            this.itemRewardId = itemRewardId;
            this.itemRewardCount = itemRewardCount;
        }
    }

    private static final List<QuestTemplate> EASY_QUESTS = new ArrayList<>();
    private static final List<QuestTemplate> MEDIUM_QUESTS = new ArrayList<>();
    private static final List<QuestTemplate> HARD_QUESTS = new ArrayList<>();

    static {
        // --- EASY QUESTS (200 Silver, No Item Reward) ---
        EASY_QUESTS.add(new QuestTemplate("mining", "easy", "Mine 32 Iron Ores", 32, "iron_ore", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("mining", "easy", "Mine 64 Coal Ores", 64, "coal_ore", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("mining", "easy", "Mine 32 Copper Ores", 32, "copper_ore", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("mining", "easy", "Mine 16 Gold Ores", 16, "gold_ore", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("mining", "easy", "Mine 32 Redstone Ores", 32, "redstone_ore", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("hunting", "easy", "Hunt 12 Skeletons", 12, "skeleton", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("hunting", "easy", "Hunt 10 Spiders", 10, "spider", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("hunting", "easy", "Hunt 8 Creepers", 8, "creeper", 200, "", 0));
        EASY_QUESTS.add(new QuestTemplate("hunting", "easy", "Hunt 12 Zombies", 12, "zombie", 200, "", 0));

        // --- MEDIUM QUESTS (500 Silver, 1 Diamond) ---
        MEDIUM_QUESTS.add(new QuestTemplate("hunting", "medium", "Hunt 25 Zombies", 25, "zombie", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("hunting", "medium", "Hunt 12 Endermen", 12, "enderman", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("hunting", "medium", "Hunt 15 Blazes", 15, "blaze", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("hunting", "medium", "Hunt 15 Piglins", 15, "piglin", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("mining", "medium", "Mine 32 Gold Ores", 32, "gold_ore", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("mining", "medium", "Mine 24 Lapis Ores", 24, "lapis_ore", 500, "diamond", 1));
        MEDIUM_QUESTS.add(new QuestTemplate("mining", "medium", "Mine 32 Nether Quartz Ores", 32, "nether_quartz_ore", 500, "diamond", 1));

        // --- HARD QUESTS (1000 Silver, 4 Diamonds) ---
        HARD_QUESTS.add(new QuestTemplate("mining", "hard", "Mine 16 Diamond Ores", 16, "diamond_ore", 1000, "diamond", 4));
        HARD_QUESTS.add(new QuestTemplate("mining", "hard", "Mine 8 Ancient Debris", 8, "ancient_debris", 1000, "diamond", 4));
        HARD_QUESTS.add(new QuestTemplate("mining", "hard", "Mine 8 Emerald Ores", 8, "emerald_ore", 1000, "diamond", 4));
        HARD_QUESTS.add(new QuestTemplate("hunting", "hard", "Hunt 12 Wither Skeletons", 12, "wither_skeleton", 1000, "diamond", 4));
        HARD_QUESTS.add(new QuestTemplate("hunting", "hard", "Hunt 8 Ghasts", 8, "ghast", 1000, "diamond", 4));
        HARD_QUESTS.add(new QuestTemplate("hunting", "hard", "Hunt 30 Drowned", 30, "drowned", 1000, "diamond", 4));
    }

    public static QuestTemplate getDailyQuest(String difficulty, long seedOffset) {
        // Use calendar day to get a randomized index that rotates daily
        long day = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
        Random rand = new Random(day + seedOffset);

        switch (difficulty.toLowerCase()) {
            case "easy":
                return EASY_QUESTS.get(rand.nextInt(EASY_QUESTS.size()));
            case "medium":
                return MEDIUM_QUESTS.get(rand.nextInt(MEDIUM_QUESTS.size()));
            case "hard":
                return HARD_QUESTS.get(rand.nextInt(HARD_QUESTS.size()));
            default:
                return EASY_QUESTS.get(0);
        }
    }
}
