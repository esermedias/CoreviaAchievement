package dev.husecik.achievement.util;

import dev.husecik.achievement.CoreviaAchievement;
import dev.husecik.achievement.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class AchievementSender {

    private final CoreviaAchievement plugin;
    private final String nmsVersion;
    private final int serverVersion;
    private final boolean useNewIconFormat;

    public AchievementSender(CoreviaAchievement plugin) {
        this.plugin = plugin;
        this.nmsVersion = getNMSVersion();
        this.serverVersion = getNumericVersion();
        this.useNewIconFormat = shouldUseNewIconFormat();
    }

    private String getNMSVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private int getNumericVersion() {
        String version = Bukkit.getBukkitVersion();
        try {
            String[] parts = version.split("-")[0].split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);
                return minor;
            }
        } catch (Exception ignored) {}
        return 16;
    }

    private boolean shouldUseNewIconFormat() {
        String version = Bukkit.getBukkitVersion();
        try {
            String[] parts = version.split("-")[0].split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);
                if (minor > 20) {
                    return true;
                } else if (minor == 20 && parts.length >= 3) {
                    int patch = Integer.parseInt(parts[2]);
                    return patch >= 5;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void sendToAll(String message) {
        ConfigManager config = plugin.getConfigManager();
        
        if (!config.isEnabled()) {
            return;
        }

        String formattedMessage = applyPrefixSuffix(message, config);

        for (Player player : Bukkit.getOnlinePlayers()) {
            sendToPlayer(player, formattedMessage);
        }
    }

    private String applyPrefixSuffix(String message, ConfigManager config) {
        String formatted = message;

        String prefix = config.getTitlePrefix();
        String suffix = config.getTitleSuffix();

        if (!prefix.isEmpty()) {
            formatted = prefix + formatted;
        }

        if (!suffix.isEmpty()) {
            formatted = formatted + suffix;
        }

        return formatted;
    }

    private void sendToPlayer(Player player, String message) {
        ConfigManager config = plugin.getConfigManager();

        try {
            NamespacedKey key = new NamespacedKey(plugin, "temp_achievement_" + UUID.randomUUID().toString().substring(0, 8));
            
            createAndGrantAdvancement(player, key, message);
            
            if (config.isSoundEnabled()) {
                playSound(player);
            }

            if (config.isAnimationEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    removeAdvancement(player, key);
                }, config.getAnimationDuration() * 20L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    removeAdvancement(player, key);
                }, 20L);
            }
        } catch (Exception e) {
            if (config.isDebug()) {
                plugin.getLogger().warning("Failed to send achievement to " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void createAndGrantAdvancement(Player player, NamespacedKey key, String message) {
        try {
            ConfigManager config = plugin.getConfigManager();
            Material iconMaterial = config.getIconMaterial();
            String frameType = config.getFrameType();

            String json = buildAdvancementJson(message, iconMaterial, frameType);
            
            if (config.isDebug()) {
                plugin.getLogger().info("Loading advancement with JSON: " + json);
            }
            
            Bukkit.getUnsafe().loadAdvancement(key, json);
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Advancement advancement = Bukkit.getAdvancement(key);
                    if (advancement != null) {
                        AdvancementProgress progress = player.getAdvancementProgress(advancement);
                        Collection<String> remaining = progress.getRemainingCriteria();
                        
                        if (config.isDebug()) {
                            plugin.getLogger().info("Advancement loaded for " + player.getName() + ", remaining criteria: " + remaining.size());
                        }
                        
                        for (String criteria : remaining) {
                            progress.awardCriteria(criteria);
                        }
                    } else {
                        if (config.isDebug()) {
                            plugin.getLogger().warning("Advancement is null after loading for " + player.getName());
                        }
                    }
                } catch (Exception e) {
                    if (config.isDebug()) {
                        plugin.getLogger().warning("Error granting advancement: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Error creating advancement: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void removeAdvancement(Player player, NamespacedKey key) {
        try {
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement != null) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
            
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Exception ignored) {}
    }

    private String buildAdvancementJson(String message, Material material, String frameType) {
        String materialName = getMaterialNamespace(material);
        String frame = frameType.toLowerCase(java.util.Locale.ENGLISH);
        
        String iconKey = useNewIconFormat ? "id" : "item";

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"criteria\":{");
        json.append("\"trigger\":{");
        json.append("\"trigger\":\"minecraft:impossible\"");
        json.append("}");
        json.append("},");
        json.append("\"display\":{");
        json.append("\"icon\":{");
        json.append("\"").append(iconKey).append("\":\"").append(materialName).append("\"");
        json.append("},");
        json.append("\"title\":");
        json.append(convertToJsonComponent(message));
        json.append(",");
        json.append("\"description\":{\"text\":\"\"}");
        json.append(",");
        json.append("\"background\":\"minecraft:textures/gui/advancements/backgrounds/stone.png\",");
        json.append("\"frame\":\"").append(frame).append("\",");
        json.append("\"announce_to_chat\":false,");
        json.append("\"show_toast\":true,");
        json.append("\"hidden\":true");
        json.append("}");
        json.append("}");

        return json.toString();
    }

    private String getMaterialNamespace(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ENGLISH);
        if (name.startsWith("legacy_")) {
            name = name.substring(7);
        }
        return "minecraft:" + name;
    }

    private String convertToJsonComponent(String message) {
        if (message == null || message.isEmpty()) {
            return "{\"text\":\"\"}";
        }

        ConfigManager config = plugin.getConfigManager();
        
        if (config.isAllowLegacyColors()) {
            message = message.replace("&", "§");
        }

        message = processGradients(message);

        List<TextComponent> components = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            
            if (c == '#' && i + 6 < message.length() && isValidHexColor(message.substring(i, i + 7))) {
                if (currentText.length() > 0) {
                    components.add(new TextComponent(
                        escapeJson(currentText.toString()),
                        currentColor, bold, italic, underlined, strikethrough, obfuscated
                    ));
                    currentText = new StringBuilder();
                }
                
                currentColor = message.substring(i, i + 7);
                bold = italic = underlined = strikethrough = obfuscated = false;
                i += 6;
            }
            else if (c == '§' && i + 1 < message.length()) {
                char code = message.charAt(i + 1);
                
                if (currentText.length() > 0) {
                    components.add(new TextComponent(
                        escapeJson(currentText.toString()),
                        currentColor, bold, italic, underlined, strikethrough, obfuscated
                    ));
                    currentText = new StringBuilder();
                }
                
                if (code == 'x' && i + 13 < message.length() && message.charAt(i + 2) == '§') {
                    String hexColor = extractHexFromLegacy(message.substring(i, i + 14));
                    if (hexColor != null) {
                        currentColor = hexColor;
                        bold = italic = underlined = strikethrough = obfuscated = false;
                        i += 13;
                        continue;
                    }
                }
                
                if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
                    currentColor = getColorName(code);
                    bold = italic = underlined = strikethrough = obfuscated = false;
                } else if (code == 'r') {
                    currentColor = null;
                    bold = italic = underlined = strikethrough = obfuscated = false;
                } else if (code == 'l') bold = true;
                else if (code == 'o') italic = true;
                else if (code == 'n') underlined = true;
                else if (code == 'm') strikethrough = true;
                else if (code == 'k') obfuscated = true;
                
                i++;
            } else {
                currentText.append(c);
            }
        }
        
        if (currentText.length() > 0) {
            components.add(new TextComponent(
                escapeJson(currentText.toString()),
                currentColor, bold, italic, underlined, strikethrough, obfuscated
            ));
        }

        if (components.isEmpty()) {
            return "{\"text\":\"\"}";
        }

        if (components.size() == 1) {
            return components.get(0).toJson();
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) json.append(",");
            json.append(components.get(i).toJson());
        }
        json.append("]}");
        
        return json.toString();
    }

    private String processGradients(String message) {
        String result = message;
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<gradient:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>([^<]*)</gradient>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String startColor = matcher.group(1);
            String endColor = matcher.group(2);
            String text = matcher.group(3);
            
            String gradientText = applyGradient(text, startColor, endColor);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);
        result = buffer.toString();
        
        pattern = java.util.regex.Pattern.compile("<g:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>([^<]*)</g>", java.util.regex.Pattern.DOTALL);
        matcher = pattern.matcher(result);
        buffer = new StringBuffer();
        
        while (matcher.find()) {
            String startColor = matcher.group(1);
            String endColor = matcher.group(2);
            String text = matcher.group(3);
            
            String gradientText = applyGradient(text, startColor, endColor);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);
        result = buffer.toString();
        
        return result;
    }

    private String applyGradient(String text, String startHex, String endHex) {
        if (text.isEmpty()) return text;
        
        int[] startRGB = hexToRgb(startHex);
        int[] endRGB = hexToRgb(endHex);
        
        StringBuilder result = new StringBuilder();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            double ratio = length > 1 ? (double) i / (length - 1) : 0;
            
            int r = (int) (startRGB[0] + (endRGB[0] - startRGB[0]) * ratio);
            int g = (int) (startRGB[1] + (endRGB[1] - startRGB[1]) * ratio);
            int b = (int) (startRGB[2] + (endRGB[2] - startRGB[2]) * ratio);
            
            String color = String.format("#%02X%02X%02X", r, g, b);
            result.append(color).append(text.charAt(i));
        }
        
        return result.toString();
    }

    private int[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        return new int[] {
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private boolean isValidHexColor(String hex) {
        if (hex.length() != 7 || hex.charAt(0) != '#') {
            return false;
        }
        for (int i = 1; i < 7; i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private String extractHexFromLegacy(String legacy) {
        if (legacy.length() < 14) return null;
        try {
            StringBuilder hex = new StringBuilder("#");
            for (int i = 3; i < 14; i += 2) {
                hex.append(legacy.charAt(i));
            }
            return hex.toString().toUpperCase(java.util.Locale.ENGLISH);
        } catch (Exception e) {
            return null;
        }
    }

    private String getColorName(char code) {
        switch (code) {
            case '0': return "black";
            case '1': return "dark_blue";
            case '2': return "dark_green";
            case '3': return "dark_aqua";
            case '4': return "dark_red";
            case '5': return "dark_purple";
            case '6': return "gold";
            case '7': return "gray";
            case '8': return "dark_gray";
            case '9': return "blue";
            case 'a': return "green";
            case 'b': return "aqua";
            case 'c': return "red";
            case 'd': return "light_purple";
            case 'e': return "yellow";
            case 'f': return "white";
            default: return null;
        }
    }

    private static class TextComponent {
        String text;
        String color;
        boolean bold;
        boolean italic;
        boolean underlined;
        boolean strikethrough;
        boolean obfuscated;

        TextComponent(String text, String color, boolean bold, boolean italic, 
                     boolean underlined, boolean strikethrough, boolean obfuscated) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.strikethrough = strikethrough;
            this.obfuscated = obfuscated;
        }

        String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\"text\":\"").append(text).append("\"");
            if (color != null) json.append(",\"color\":\"").append(color).append("\"");
            if (bold) json.append(",\"bold\":true");
            if (italic) json.append(",\"italic\":true");
            if (underlined) json.append(",\"underlined\":true");
            if (strikethrough) json.append(",\"strikethrough\":true");
            if (obfuscated) json.append(",\"obfuscated\":true");
            json.append("}");
            return json.toString();
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("/", "\\/");
    }

    private void playSound(Player player) {
        ConfigManager config = plugin.getConfigManager();
        try {
            Sound sound = Sound.valueOf(config.getSoundType());
            player.playSound(player.getLocation(), sound, config.getSoundVolume(), config.getSoundPitch());
        } catch (IllegalArgumentException e) {
            try {
                if (serverVersion >= 16) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 
                        config.getSoundVolume(), config.getSoundPitch());
                } else {
                    player.playSound(player.getLocation(), "ui.toast.challenge_complete", 
                        config.getSoundVolume(), config.getSoundPitch());
                }
            } catch (Exception ex) {
                if (config.isDebug()) {
                    plugin.getLogger().warning("Could not play sound: " + e.getMessage());
                }
            }
        }
    }
}

