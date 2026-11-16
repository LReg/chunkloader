package dev.anchorstack.loader;

import dev.anchorstack.AnchorstackPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;

/** Zeigt die Grenzen des geladenen Bereichs eine Zeit lang als Partikel, die von der Saeule nach aussen laufen. */
public final class BorderDisplay {

    private static final double REVEAL_PER_TICK = 1.5;
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.AQUA, 1.2f);

    private final AnchorstackPlugin plugin;
    private final LoaderManager manager;
    /** Loader-ID zu den Ticks, die seit dem Start der Anzeige vergangen sind. */
    private final Map<UUID, Integer> elapsed = new HashMap<>();

    public BorderDisplay(AnchorstackPlugin plugin, LoaderManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public int intervalTicks() {
        return Math.max(2, plugin.getConfig().getInt("border.interval-ticks", 10));
    }

    /** Startet die Anzeige, oder setzt sie auf Anfang, falls sie schon laeuft. */
    public void show(Loader loader) {
        elapsed.put(loader.id(), 0);
    }

    public void tick() {
        int step = intervalTicks();
        int duration = plugin.getConfig().getInt("border.duration-seconds", 20) * 20;
        Iterator<Map.Entry<UUID, Integer>> it = elapsed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Loader loader = manager.get(entry.getKey());
            World world = loader == null ? null : plugin.getServer().getWorld(loader.world());
            int ticks = entry.getValue();
            if (world == null || ticks >= duration) {
                it.remove();
                continue;
            }
            if (!world.getPlayers().isEmpty() && world.isChunkLoaded(loader.x() >> 4, loader.z() >> 4)) {
                draw(world, loader, ticks);
            }
            entry.setValue(ticks + step);
        }
    }

    private void draw(World world, Loader loader, int ticks) {
        int r = manager.radius(loader.tier());
        int cx = loader.x() >> 4;
        int cz = loader.z() >> 4;
        int minX = (cx - r) << 4;
        int maxX = (cx + r + 1) << 4;
        int minZ = (cz - r) << 4;
        int maxZ = (cz + r + 1) << 4;
        double reveal = ticks * REVEAL_PER_TICK + 2;
        double y = loader.y() + 1.5;

        for (int x = minX; x <= maxX; x += 2) {
            point(world, loader, reveal, x, y, minZ);
            point(world, loader, reveal, x, y, maxZ);
        }
        for (int z = minZ + 2; z < maxZ; z += 2) {
            point(world, loader, reveal, minX, y, z);
            point(world, loader, reveal, maxX, y, z);
        }
        int[][] corners = {{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
        for (int[] corner : corners) {
            for (int h = 0; h <= 6; h++) {
                point(world, loader, reveal, corner[0], loader.y() + 0.5 + h, corner[1]);
            }
        }
    }

    private void point(World world, Loader loader, double reveal, double x, double y, double z) {
        double dx = x - (loader.x() + 0.5);
        double dz = z - (loader.z() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= reveal) {
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, DUST);
        }
    }
}
