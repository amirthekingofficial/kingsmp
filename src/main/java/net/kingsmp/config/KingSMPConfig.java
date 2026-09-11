package net.kingsmp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kingsmp.KingSMPMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * Global configuration management for KingSMP.
 * All numeric values and server-wide tunables reside here.
 *
 * NOTE ON STAT CAPS:
 * Hard caps (Resistance I, Strength II, Speed II) are enforced server-side
 * in the Crown and Rank logic. Even if config values are edited higher,
 * the server logic clamps values to prevent broken PvP netcode and combat stagnation.
 */
public class KingSMPConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "kingsmp-config.json";

    // ── ECONOMY & MARKET ───────────────────────────────────────────────────
    public static double marketFeePercent = 5.0;
    public static double masterMerchantMarketFeePercent = 3.0;
    public static int factionCreationFee = 5000;
    public static int rankPathSwitchFee = 500;

    // ── TELEPORTATION & COMBAT ─────────────────────────────────────────────
    public static int teleportChannelSeconds = 3;
    public static double channelMaxMoveDistance = 0.5;
    public static int combatTagDurationSeconds = 15;
    public static int rtpCooldownSeconds = 300;
    public static int rtpPathfinderCooldownSeconds = 120;
    public static long newPlayerProtectionMinutes = 60L; // ~3 Minecraft days

    // ── CROWN SETTINGS ────────────────────────────────────────────────────
    public static double crownBaseHealthBonus = 4.0; // +2 Hearts
    public static double crownSkullsHealthBonus = 12.0; // +6 Hearts (Tank)
    public static int crownAuraRadiusBlocks = 30;
    public static int crownLastStandCooldownSeconds = 120;
    public static int crownEmberBurstCooldownSeconds = 90;
    public static int crownVanishCooldownHours = 24;

    // ── PROFESSIONS ────────────────────────────────────────────────────────
    public static int professionMaxLevel = 5;
    public static double multiclassXpPenalty = 0.50; // 50% XP penalty on 3rd+ active trade

    public static void loadConfig() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

            if (!configFile.exists()) {
                saveConfig();
                return;
            }

            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                if (json.has("marketFeePercent")) marketFeePercent = json.get("marketFeePercent").getAsDouble();
                if (json.has("masterMerchantMarketFeePercent")) masterMerchantMarketFeePercent = json.get("masterMerchantMarketFeePercent").getAsDouble();
                if (json.has("factionCreationFee")) factionCreationFee = json.get("factionCreationFee").getAsInt();
                if (json.has("rankPathSwitchFee")) rankPathSwitchFee = json.get("rankPathSwitchFee").getAsInt();

                if (json.has("teleportChannelSeconds")) teleportChannelSeconds = json.get("teleportChannelSeconds").getAsInt();
                if (json.has("channelMaxMoveDistance")) channelMaxMoveDistance = json.get("channelMaxMoveDistance").getAsDouble();
                if (json.has("combatTagDurationSeconds")) combatTagDurationSeconds = json.get("combatTagDurationSeconds").getAsInt();
                if (json.has("rtpCooldownSeconds")) rtpCooldownSeconds = json.get("rtpCooldownSeconds").getAsInt();
                if (json.has("rtpPathfinderCooldownSeconds")) rtpPathfinderCooldownSeconds = json.get("rtpPathfinderCooldownSeconds").getAsInt();
                if (json.has("newPlayerProtectionMinutes")) newPlayerProtectionMinutes = json.get("newPlayerProtectionMinutes").getAsLong();

                if (json.has("crownBaseHealthBonus")) crownBaseHealthBonus = json.get("crownBaseHealthBonus").getAsDouble();
                if (json.has("crownSkullsHealthBonus")) crownSkullsHealthBonus = json.get("crownSkullsHealthBonus").getAsDouble();
                if (json.has("crownAuraRadiusBlocks")) crownAuraRadiusBlocks = json.get("crownAuraRadiusBlocks").getAsInt();
                if (json.has("crownLastStandCooldownSeconds")) crownLastStandCooldownSeconds = json.get("crownLastStandCooldownSeconds").getAsInt();
                if (json.has("crownEmberBurstCooldownSeconds")) crownEmberBurstCooldownSeconds = json.get("crownEmberBurstCooldownSeconds").getAsInt();
                if (json.has("crownVanishCooldownHours")) crownVanishCooldownHours = json.get("crownVanishCooldownHours").getAsInt();

                if (json.has("professionMaxLevel")) professionMaxLevel = json.get("professionMaxLevel").getAsInt();
                if (json.has("multiclassXpPenalty")) multiclassXpPenalty = json.get("multiclassXpPenalty").getAsDouble();
            }
        } catch (Exception e) {
            KingSMPMod.LOGGER.error("Failed to load " + CONFIG_FILE_NAME + ", using defaults", e);
        }
    }

    public static void saveConfig() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

            JsonObject json = new JsonObject();
            json.addProperty("marketFeePercent", marketFeePercent);
            json.addProperty("masterMerchantMarketFeePercent", masterMerchantMarketFeePercent);
            json.addProperty("factionCreationFee", factionCreationFee);
            json.addProperty("rankPathSwitchFee", rankPathSwitchFee);

            json.addProperty("teleportChannelSeconds", teleportChannelSeconds);
            json.addProperty("channelMaxMoveDistance", channelMaxMoveDistance);
            json.addProperty("combatTagDurationSeconds", combatTagDurationSeconds);
            json.addProperty("rtpCooldownSeconds", rtpCooldownSeconds);
            json.addProperty("rtpPathfinderCooldownSeconds", rtpPathfinderCooldownSeconds);
            json.addProperty("newPlayerProtectionMinutes", newPlayerProtectionMinutes);

            json.addProperty("crownBaseHealthBonus", crownBaseHealthBonus);
            json.addProperty("crownSkullsHealthBonus", crownSkullsHealthBonus);
            json.addProperty("crownAuraRadiusBlocks", crownAuraRadiusBlocks);
            json.addProperty("crownLastStandCooldownSeconds", crownLastStandCooldownSeconds);
            json.addProperty("crownEmberBurstCooldownSeconds", crownEmberBurstCooldownSeconds);
            json.addProperty("crownVanishCooldownHours", crownVanishCooldownHours);

            json.addProperty("professionMaxLevel", professionMaxLevel);
            json.addProperty("multiclassXpPenalty", multiclassXpPenalty);

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            KingSMPMod.LOGGER.error("Failed to save " + CONFIG_FILE_NAME, e);
        }
    }
}
