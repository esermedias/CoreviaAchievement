package dev.husecik.achievement;

import dev.husecik.achievement.command.AchievementCommand;
import dev.husecik.achievement.config.ConfigManager;
import dev.husecik.achievement.util.AchievementSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreviaAchievement extends JavaPlugin {

    private static CoreviaAchievement instance;
    private ConfigManager configManager;
    private AchievementSender achievementSender;

    @Override
    public void onEnable() {
        instance = this;
        
        this.configManager = new ConfigManager(this);
        this.achievementSender = new AchievementSender(this);
        
        registerCommands();
        
        getLogger().info("CoreviaAchievement has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CoreviaAchievement has been disabled!");
    }

    private void registerCommands() {
        AchievementCommand achievementCommand = new AchievementCommand(this);
        getCommand("achievement").setExecutor(achievementCommand);
        getCommand("achievement").setTabCompleter(achievementCommand);
    }

    public void reload() {
        configManager.reload();
    }

    public static CoreviaAchievement getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AchievementSender getAchievementSender() {
        return achievementSender;
    }
}

