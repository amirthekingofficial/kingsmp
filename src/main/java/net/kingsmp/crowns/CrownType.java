package net.kingsmp.crowns;

import net.minecraft.ChatFormatting;

public enum CrownType {
    SKULLS("Crown of Skulls", "War-King", 5.0f, ChatFormatting.DARK_RED, "kingsmp:item/skull_crown"),
    GOLD("Crown of Gold", "Merchant-King", 1.0f, ChatFormatting.GOLD, "kingsmp:item/crown"),
    END("Crown of the End", "Void-King", 2.0f, ChatFormatting.DARK_PURPLE, "kingsmp:item/end_crown"),
    LAVA("Crown of Lava", "Pyromancer-King", 4.0f, ChatFormatting.GOLD, "kingsmp:item/lava_crown"),
    ICE("Crown of Ice", "Wraith-King", 3.0f, ChatFormatting.AQUA, "kingsmp:item/ice_crown");

    private final String displayName;
    private final String title;
    private final float customModelData;
    private final ChatFormatting color;
    private final String modelPath;

    CrownType(String displayName, String title, float customModelData, ChatFormatting color, String modelPath) {
        this.displayName = displayName;
        this.title = title;
        this.customModelData = customModelData;
        this.color = color;
        this.modelPath = modelPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTitle() {
        return title;
    }

    public float getCustomModelData() {
        return customModelData;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getModelPath() {
        return modelPath;
    }

    public static CrownType fromName(String name) {
        return fromString(name);
    }

    public static CrownType fromString(String name) {
        if (name == null) return SKULLS;
        try {
            return CrownType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (name.toLowerCase()) {
                case "skull", "skulls", "war", "iron" -> SKULLS;
                case "gold", "merchant" -> GOLD;
                case "end", "void", "stone" -> END;
                case "lava", "fire", "ash" -> LAVA;
                case "ice", "frost", "wraith" -> ICE;
                default -> SKULLS;
            };
        }
    }
}
