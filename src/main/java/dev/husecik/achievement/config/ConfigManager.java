package dev.husecik.achievement.config;

import dev.husecik.achievement.CoreviaAchievement;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final CoreviaAchievement plugin;
    private volatile FileConfiguration config;

    public ConfigManager(CoreviaAchievement plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    public boolean isDebug() {
        return config.getBoolean("settings.debug", false);
    }

    public Material getIconMaterial() {
        String materialName = config.getString("achievement.icon.material", "DIAMOND");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + materialName + ", using DIAMOND");
            return Material.DIAMOND;
        }
    }

    public int getCustomModelData() {
        return config.getInt("achievement.icon.custom-model-data", 0);
    }

    public String getFrameType() {
        return config.getString("achievement.frame.type", "GOAL").toUpperCase();
    }

    public String getTitlePrefix() {
        return config.getString("achievement.title.prefix", "");
    }

    public String getTitleSuffix() {
        return config.getString("achievement.title.suffix", "");
    }

    public boolean isTitleColorEnabled() {
        return config.getBoolean("achievement.title.color", true);
    }

    public boolean isAnimationEnabled() {
        return config.getBoolean("achievement.animation.enabled", true);
    }

    public int getAnimationDuration() {
        return config.getInt("achievement.animation.duration", 5);
    }

    public boolean isSoundEnabled() {
        return config.getBoolean("advanced.sound.enabled", true);
    }

    public String getSoundType() {
        return config.getString("advanced.sound.type", "UI_TOAST_CHALLENGE_COMPLETE");
    }

    public float getSoundVolume() {
        return (float) config.getDouble("advanced.sound.volume", 1.0);
    }

    public float getSoundPitch() {
        return (float) config.getDouble("advanced.sound.pitch", 1.0);
    }

    public boolean isStripColors() {
        return config.getBoolean("advanced.format.strip-colors", false);
    }

    public boolean isAllowLegacyColors() {
        return config.getBoolean("advanced.format.allow-legacy-colors", true);
    }

    public boolean isAllowHexColors() {
        return config.getBoolean("advanced.format.allow-hex-colors", true);
    }

    public boolean isUseLegacyTitle() {
        return config.getBoolean("advanced.compatibility.use-legacy-title", false);
    }

    public String getPacketMethod() {
        return config.getString("advanced.compatibility.packet-method", "AUTO");
    }

    public String getMessage(String path) {
        return translateColors(config.getString("messages." + path, "&cMessage not found: " + path));
    }

    public String translateColors(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }
}

