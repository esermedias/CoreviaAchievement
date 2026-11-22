package dev.husecik.achievement.util;

import dev.husecik.achievement.CoreviaAchievement;
import dev.husecik.achievement.config.ConfigManager;
import dev.husecik.achievement.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AchievementSender {

    private static final Pattern GRADIENT = Pattern.compile(
            "<(g|gradient):(#[0-9A-Fa-f]{6}(?::#[0-9A-Fa-f]{6})+)>([^<]*)</\\1>", Pattern.DOTALL);

    private final CoreviaAchievement plugin;
    private final TaskScheduler scheduler;
    private final int serverVersion;
    private final boolean useNewIconFormat;

    public AchievementSender(CoreviaAchievement plugin) {
        this.plugin = plugin;
        this.scheduler = TaskScheduler.create(plugin);
        this.serverVersion = getNumericVersion();
        this.useNewIconFormat = shouldUseNewIconFormat();
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

    /** Shows the notification to every online player. Safe to call from any thread. */
    public void sendToAll(String message) {
        ConfigManager config = plugin.getConfigManager();

        if (!config.isEnabled()) {
            return;
        }

        String formattedMessage = applyPrefixSuffix(message, config);

        for (Player player : Bukkit.getOnlinePlayers()) {
            deliver(player, formattedMessage);
        }
    }

    /** Shows the notification to a single player. Safe to call from any thread. */
    public void sendAchievement(Player player, String message) {
        ConfigManager config = plugin.getConfigManager();

        if (!config.isEnabled()) {
            return;
        }

        deliver(player, applyPrefixSuffix(message, config));
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

    /*
     * A toast is shown by loading a throwaway advancement, granting it, then revoking and
     * unloading it once the toast has been on screen long enough.
     *
     * The advancement registry is server-wide, so loading and unloading always happens on the
     * global thread. Granting, revoking and the sound belong to the player and run on whatever
     * thread owns them. On Spigot/Paper both of these are just the main thread.
     */
    private void deliver(Player player, String message) {
        ConfigManager config = plugin.getConfigManager();
        NamespacedKey key = new NamespacedKey(plugin, "temp_achievement_" + UUID.randomUUID().toString().substring(0, 8));

        String json;
        try {
            json = buildAdvancementJson(message, config.getIconMaterial(), config.getFrameType());
        } catch (Exception e) {
            debug("Could not build advancement for " + player.getName(), e);
            return;
        }

        long displayTicks = config.isAnimationEnabled()
                ? Math.max(1, config.getAnimationDuration()) * 20L
                : 20L;
        Runnable unload = () -> scheduler.runGlobal(() -> unloadAdvancement(key));

        scheduler.runGlobal(() -> {
            Advancement advancement;
            try {
                if (config.isDebug()) {
                    plugin.getLogger().info("Loading advancement with JSON: " + json);
                }
                advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
            } catch (Exception e) {
                debug("Could not load advancement for " + player.getName(), e);
                return;
            }

            if (advancement == null) {
                debug("Advancement is null after loading for " + player.getName(), null);
                return;
            }

            // Grant one tick later so the client already knows about the new advancement.
            scheduler.runForPlayer(player, () -> grant(player, advancement), unload, 1L);
            scheduler.runForPlayer(player, () -> {
                revoke(player, advancement);
                unload.run();
            }, unload, displayTicks);
        });
    }

    private void grant(Player player, Advancement advancement) {
        ConfigManager config = plugin.getConfigManager();
        try {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteria);
            }
            if (config.isSoundEnabled()) {
                playSound(player);
            }
        } catch (Exception e) {
            debug("Error granting advancement to " + player.getName(), e);
        }
    }

    private void revoke(Player player, Advancement advancement) {
        try {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        } catch (Exception e) {
            debug("Error revoking advancement from " + player.getName(), e);
        }
    }

    private void unloadAdvancement(NamespacedKey key) {
        try {
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Exception e) {
            debug("Error unloading advancement " + key, e);
        }
    }

    private void debug(String message, Exception e) {
        if (!plugin.getConfigManager().isDebug()) {
            return;
        }
        if (e == null) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(java.util.logging.Level.WARNING, message, e);
        }
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

    // Handles <g:#A:#B>text</g> and <gradient:#A:#B:#C>text</gradient>, with two or more stops.
    private String processGradients(String message) {
        Matcher matcher = GRADIENT.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String[] stops = matcher.group(2).split(":");
            String gradientText = applyGradient(matcher.group(3), stops);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private String applyGradient(String text, String[] stops) {
        if (text.isEmpty()) return text;

        int[][] colors = new int[stops.length][];
        for (int i = 0; i < stops.length; i++) {
            colors[i] = hexToRgb(stops[i]);
        }

        StringBuilder result = new StringBuilder();
        int length = text.length();
        int segments = colors.length - 1;

        for (int i = 0; i < length; i++) {
            double position = length > 1 ? (double) i / (length - 1) * segments : 0;
            int segment = Math.min((int) position, segments - 1);
            double ratio = position - segment;

            int[] from = colors[segment];
            int[] to = colors[segment + 1];
            int r = (int) Math.round(from[0] + (to[0] - from[0]) * ratio);
            int g = (int) Math.round(from[1] + (to[1] - from[1]) * ratio);
            int b = (int) Math.round(from[2] + (to[2] - from[2]) * ratio);

            result.append(String.format("#%02X%02X%02X", r, g, b)).append(text.charAt(i));
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

