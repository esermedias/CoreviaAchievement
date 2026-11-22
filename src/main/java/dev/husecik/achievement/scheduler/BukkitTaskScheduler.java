package dev.husecik.achievement.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Spigot and Paper: everything runs on the main thread. */
final class BukkitTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runForPlayer(Player player, Runnable task, Runnable retired, long delayTicks) {
        Runnable guarded = () -> {
            if (player.isOnline()) {
                task.run();
            } else {
                retired.run();
            }
        };

        if (delayTicks <= 0 && Bukkit.isPrimaryThread()) {
            guarded.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, guarded, Math.max(0L, delayTicks));
        }
    }
}
