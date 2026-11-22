package dev.husecik.achievement.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia: global work goes to the global region scheduler, player work to the player's
 * own entity scheduler, which follows them across regions.
 *
 * <p>Only loaded when Folia is detected, so the Folia API classes are never touched on Spigot.
 */
final class FoliaTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    FoliaTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task, Runnable retired, long delayTicks) {
        ScheduledTask scheduled = delayTicks <= 0
                ? player.getScheduler().run(plugin, t -> task.run(), retired)
                // Folia rejects delays below one tick.
                : player.getScheduler().runDelayed(plugin, t -> task.run(), retired, delayTicks);

        // null means the player was already removed; the retired callback is not invoked in that case.
        if (scheduled == null) {
            retired.run();
        }
    }
}
