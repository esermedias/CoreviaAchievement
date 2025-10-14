package dev.husecik.achievement.command;

import dev.husecik.achievement.CoreviaAchievement;
import dev.husecik.achievement.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AchievementCommand implements CommandExecutor, TabCompleter {

    private final CoreviaAchievement plugin;

    public AchievementCommand(CoreviaAchievement plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (args.length == 0) {
            sender.sendMessage(config.getMessage("invalid-usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "send":
                return handleSendCommand(sender, args, config);
            case "reload":
                return handleReloadCommand(sender, config);
            default:
                sender.sendMessage(config.getMessage("invalid-usage"));
                return true;
        }
    }

    private boolean handleSendCommand(CommandSender sender, String[] args, ConfigManager config) {
        if (!sender.hasPermission("achievement.send")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return true;
        }

        if (!config.isEnabled()) {
            sender.sendMessage(config.getMessage("plugin-disabled"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(config.getMessage("invalid-usage"));
            return true;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]);
            if (i < args.length - 1) {
                messageBuilder.append(" ");
            }
        }

        String message = messageBuilder.toString().trim();

        if (message.isEmpty()) {
            sender.sendMessage(config.getMessage("empty-message"));
            return true;
        }

        plugin.getAchievementSender().sendToAll(message);
        sender.sendMessage(config.getMessage("message-sent"));

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender, ConfigManager config) {
        if (!sender.hasPermission("achievement.reload")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return true;
        }

        try {
            plugin.reload();
            sender.sendMessage(config.getMessage("reload-success"));
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("reload-error"));
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("achievement.send")) {
                subCommands.add("send");
            }
            if (sender.hasPermission("achievement.reload")) {
                subCommands.add("reload");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            if (sender.hasPermission("achievement.send")) {
                completions.add("<message>");
            }
        }

        return completions;
    }
}

