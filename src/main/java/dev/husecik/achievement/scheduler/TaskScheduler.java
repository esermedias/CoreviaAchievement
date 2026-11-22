package dev.husecik.achievement.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Small scheduling layer so the same code runs on Spigot/Paper and on Folia.
 *
 * <p>Folia has no main thread: server-wide state lives on the global region thread and
 * each player is ticked by the region that currently owns them. Bukkit's scheduler
 * throws on Folia, so everything goes through here instead.
 */
public interface TaskScheduler {

    /** Runs a task that touches server-wide state (e.g. the advancement registry). */
    void runGlobal(Runnable task);

    /**
     * Runs a task on the thread that owns the given player.
     *
     * @param task       what to do with the player
     * @param retired    called instead of {@code task} if the player is gone by then
     * @param delayTicks delay in ticks, 0 to run as soon as possible
     */
    void runForPlayer(Player player, Runnable task, Runnable retired, long delayTicks);

    static TaskScheduler create(Plugin plugin) {
        return isFolia() ? new FoliaTaskScheduler(plugin) : new BukkitTaskScheduler(plugin);
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
