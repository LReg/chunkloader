package dev.anchorstack.loader;

import dev.anchorstack.AnchorstackPlugin;
import com.destroystokyo.paper.ParticleBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Zeigt die Grenzen des geladenen Bereichs eine Zeit lang als Partikel, die von der Saeule nach aussen laufen. */
public final class BorderDisplay {

    private static final double REVEAL_PER_TICK = 1.5;
    /** Spieler weiter als das von der Saeule entfernt sehen die Grenzen nicht. */
    private static final double VIEW_DISTANCE = 80;
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
            if (world.isChunkLoaded(loader.x() >> 4, loader.z() >> 4)) {
                List<Player> nearby = nearbyPlayers(world, loader);
                if (!nearby.isEmpty()) {
                    draw(world, loader, ticks, nearby);
                }
            }
            entry.setValue(ticks + step);
        }
    }

    /** Spieler, die die Grenzen sehen sollen. Der Rand liegt bei Stufe 2 bis zu 48 Bloecke von der Saeule weg. */
    private List<Player> nearbyPlayers(World world, Loader loader) {
        double max = VIEW_DISTANCE * VIEW_DISTANCE;
        double cx = loader.x() + 0.5;
        double cz = loader.z() + 0.5;
        List<Player> result = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - cx;
            double dz = at.getZ() - cz;
            if (dx * dx + dz * dz <= max) {
                result.add(player);
            }
        }
        return result;
    }

    private void draw(World world, Loader loader, int ticks, List<Player> receivers) {
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
            point(world, loader, receivers, reveal, x, y, minZ);
            point(world, loader, receivers, reveal, x, y, maxZ);
        }
        for (int z = minZ + 2; z < maxZ; z += 2) {
            point(world, loader, receivers, reveal, minX, y, z);
            point(world, loader, receivers, reveal, maxX, y, z);
        }
        int[][] corners = {{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
        for (int[] corner : corners) {
            for (int h = 0; h <= 6; h++) {
                point(world, loader, receivers, reveal, corner[0], loader.y() + 0.5 + h, corner[1]);
            }
        }
    }

    private void point(World world, Loader loader, List<Player> receivers, double reveal, double x, double y, double z) {
        double dx = x - (loader.x() + 0.5);
        double dz = z - (loader.z() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= reveal) {
            // force hebt das 32-Bloecke-Limit des Clients auf, sonst sieht man den Rand bei Stufe 2 nicht
            new ParticleBuilder(Particle.DUST).location(world, x, y, z).count(1).data(DUST)
                    .force(true).receivers(receivers).spawn();
        }
    }
}
