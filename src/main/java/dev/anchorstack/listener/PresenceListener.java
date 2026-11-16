package dev.anchorstack.listener;

import dev.anchorstack.AnchorstackPlugin;
import dev.anchorstack.loader.LoaderManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Fuer load-when-empty: Chunks nur halten, solange jemand online ist. */
public final class PresenceListener implements Listener {

    private final AnchorstackPlugin plugin;
    private final LoaderManager manager;

    public PresenceListener(AnchorstackPlugin plugin, LoaderManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.updatePresence();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Der gehende Spieler zaehlt waehrend des Ereignisses noch als online, deshalb einen Tick spaeter pruefen
        plugin.getServer().getScheduler().runTask(plugin, manager::updatePresence);
    }
}
