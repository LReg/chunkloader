package dev.anchorstack.listener;

import dev.anchorstack.AnchorstackPlugin;
import dev.anchorstack.loader.Loader;
import dev.anchorstack.loader.LoaderManager;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Welten, die erst nach dem Plugin-Start laden, bekommen ihre Tickets beim Laden. */
public final class WorldListener implements Listener {

    private final AnchorstackPlugin plugin;
    private final LoaderManager manager;

    public WorldListener(AnchorstackPlugin plugin, LoaderManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onLoad(WorldLoadEvent event) {
        manager.activateWorld(event.getWorld());
    }

    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        if (!event.isCancelled()) {
            manager.deactivateWorld(event.getWorld());
        }
    }

    /** Prueft einen Loader sofort, sobald sein Chunk geladen wird, damit er nicht auf den Zeitgeber warten muss. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Loader loader : manager.all()) {
            if (!loader.world().equals(chunk.getWorld().getUID())
                    || (loader.x() >> 4) != chunk.getX() || (loader.z() >> 4) != chunk.getZ()) {
                continue;
            }
            // Einen Tick spaeter, damit der Chunk fertig geladen ist und Tickets nicht mitten im Ladeereignis geaendert werden
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.check(loader));
        }
    }
}
