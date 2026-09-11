package net.kingsmp.professions;

import net.minecraft.ChatFormatting;

public enum ProfessionType {
    FISHER("Fisher", "Master Angler", "Lucky Line", ChatFormatting.AQUA),
    FARMER("Farmer", "Master Harvester", "Bountiful Soil (+25% yield)", ChatFormatting.GREEN),
    MINER("Miner", "Master Prospector", "Vein Sense", ChatFormatting.GRAY),
    BLACKSMITH("Blacksmith", "Master Blacksmith", "Masterwork Smithing & Crown Station", ChatFormatting.GOLD),
    ALCHEMIST("Alchemist", "Grand Alchemist", "Elixir Synthesis", ChatFormatting.DARK_PURPLE),
    HUNTER("Hunter", "Apex Predator", "Elite Mob Radar", ChatFormatting.RED),
    MERCHANT("Merchant", "Grand Trader", "Low Market Fee (3%)", ChatFormatting.YELLOW);

    private final String displayName;
    private final String masterTitle;
    private final String masterUnlockDescription;
    private final ChatFormatting color;

    ProfessionType(String displayName, String masterTitle, String masterUnlockDescription, ChatFormatting color) {
        this.displayName = displayName;
        this.masterTitle = masterTitle;
        this.masterUnlockDescription = masterUnlockDescription;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMasterTitle() {
        return masterTitle;
    }

    public String getMasterUnlockDescription() {
        return masterUnlockDescription;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getLevelTitle(int level) {
        return switch (level) {
            case 2 -> "Apprentice " + displayName;
            case 3 -> "Journeyman " + displayName;
            case 4 -> "Expert " + displayName;
            case 5 -> masterTitle;
            default -> "Novice " + displayName;
        };
    }

    public static ProfessionType fromString(String str) {
        if (str == null) return null;
        try {
            return ProfessionType.valueOf(str.toUpperCase());
        } catch (Exception e) {
            return switch (str.toLowerCase()) {
                case "fish", "fisher", "fishing" -> FISHER;
                case "farm", "farmer", "farming" -> FARMER;
                case "mine", "miner", "mining", "prospector" -> MINER;
                case "smith", "blacksmith", "forge" -> BLACKSMITH;
                case "alch", "alchemist", "brew", "brewing" -> ALCHEMIST;
                case "hunt", "hunter", "trapper" -> HUNTER;
                case "merchant", "trade", "trader" -> MERCHANT;
                default -> null;
            };
        }
    }
}
