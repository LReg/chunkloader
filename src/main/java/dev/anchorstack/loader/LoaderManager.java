package dev.anchorstack.loader;

import dev.anchorstack.AnchorstackPlugin;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class LoaderManager {

    private final AnchorstackPlugin plugin;
    private final LoaderStore store;
    private final Map<UUID, Loader> loaders = new LinkedHashMap<>();
    private final Map<BlockKey, UUID> index = new HashMap<>();
    /** Wie viele Loader einen Chunk halten. Das Plugin-Ticket gibt es nur einmal pro Chunk. */
    private final Map<ChunkKey, Integer> tickets = new HashMap<>();
    /** Welche Chunks jeder Loader gerade haelt. So bleibt die Zaehlung stimmig, wenn sich ein Radius in der Config aendert. */
    private final Map<UUID, Set<ChunkKey>> held = new HashMap<>();
    /** false, solange load-when-empty aus ist und niemand online ist: dann werden keine Chunks gehalten. */
    private boolean active = true;

    public LoaderManager(AnchorstackPlugin plugin, LoaderStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load() {
        try {
            for (Loader loader : store.load()) {
                loaders.put(loader.id(), loader);
                indexBlocks(loader);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "loaders.json konnte nicht gelesen werden, es wird nichts geladen", e);
            store.backupBroken().ifPresent(path ->
                    plugin.getLogger().severe("Die kaputte Datei wurde als " + path.getFileName() + " gesichert."));
            loaders.clear();
            index.clear();
            return;
        }
        active = shouldBeActive();
        for (World world : Bukkit.getWorlds()) {
            activateWorld(world);
        }
    }

    private boolean shouldBeActive() {
        return plugin.getConfig().getBoolean("load-when-empty", false) || !Bukkit.getOnlinePlayers().isEmpty();
    }

    /** Haelt oder gibt die Chunks frei, je nachdem ob load-when-empty gilt oder Spieler online sind. */
    public void updatePresence() {
        boolean should = shouldBeActive();
        if (should == active) {
            return;
        }
        active = should;
        for (Loader loader : loaders.values()) {
            World world = Bukkit.getWorld(loader.world());
            if (world == null) {
                continue;
            }
            if (active) {
                holdChunks(world, loader);
            } else {
                releaseChunks(world, loader);
            }
        }
    }

    public void shutdown() {
        for (Loader loader : loaders.values()) {
            World world = Bukkit.getWorld(loader.world());
            if (world != null) {
                releaseChunks(world, loader);
            }
        }
        tickets.clear();
    }

    public Collection<Loader> all() {
        return List.copyOf(loaders.values());
    }

    public List<Loader> ownedBy(UUID owner) {
        return loaders.values().stream().filter(l -> l.owner().equals(owner)).toList();
    }

    public Loader get(UUID id) {
        return loaders.get(id);
    }

    public Loader byBlock(Block block) {
        UUID id = index.get(BlockKey.of(block));
        return id == null ? null : loaders.get(id);
    }

    /** Findet einen Loader ueber den Anfang seiner ID. Mindestens 4 Zeichen, und nur wenn es genau einen Treffer gibt. */
    public Loader findByPrefix(String prefix, UUID onlyOwner) {
        if (prefix.length() < 4) {
            return null;
        }
        String p = prefix.toLowerCase();
        Loader found = null;
        for (Loader loader : loaders.values()) {
            if (loader.id().toString().startsWith(p) && (onlyOwner == null || loader.owner().equals(onlyOwner))) {
                if (found != null) {
                    return null;
                }
                found = loader;
            }
        }
        return found;
    }

    public int radius(int tier) {
        return Math.max(0, plugin.getConfig().getInt("tiers." + tier + ".radius", tier));
    }

    public int chunkCount(int tier) {
        int side = radius(tier) * 2 + 1;
        return side * side;
    }

    /** Anzahl Chunks, die zusaetzlich dauerhaft geladen wuerden. */
    public int newChunks(World world, int baseX, int baseZ, int tier) {
        int r = radius(tier);
        int added = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!tickets.containsKey(new ChunkKey(world.getUID(), (baseX >> 4) + dx, (baseZ >> 4) + dz))) {
                    added++;
                }
            }
        }
        return added;
    }

    public int heldChunks() {
        return tickets.size();
    }

    public Loader create(UUID owner, Block base, int tier) {
        Loader loader = new Loader(UUID.randomUUID(), owner, base.getWorld().getUID(),
                base.getX(), base.getY(), base.getZ(), tier, System.currentTimeMillis());
        loaders.put(loader.id(), loader);
        indexBlocks(loader);
        holdChunks(base.getWorld(), loader);
        save();
        return loader;
    }

    public void remove(Loader loader) {
        if (loaders.remove(loader.id()) == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            index.remove(new BlockKey(loader.world(), loader.x(), loader.y() + i, loader.z()));
        }
        World world = Bukkit.getWorld(loader.world());
        if (world != null) {
            releaseChunks(world, loader);
        }
        save();
    }

    /** Wird beim Laden einer Welt gerufen, auch beim Serverstart. */
    public void activateWorld(World world) {
        if (!active) {
            return;
        }
        for (Loader loader : loaders.values()) {
            if (loader.world().equals(world.getUID())) {
                holdChunks(world, loader);
            }
        }
    }

    public void deactivateWorld(World world) {
        tickets.keySet().removeIf(key -> key.world().equals(world.getUID()));
        for (Loader loader : loaders.values()) {
            if (loader.world().equals(world.getUID())) {
                held.remove(loader.id());
            }
        }
    }

    /** Setzt die Tickets neu, etwa nach /anchorstack reload, wenn sich ein Radius in der Config geaendert hat. */
    public void refresh() {
        active = shouldBeActive();
        for (Loader loader : loaders.values()) {
            World world = Bukkit.getWorld(loader.world());
            if (world != null) {
                releaseChunks(world, loader);
                if (active) {
                    holdChunks(world, loader);
                }
            }
        }
    }

    /**
     * Stufe der Saeule, die gerade an der Stelle des Loaders steht: 0 wenn keine gueltige Saeule da ist,
     * -1 wenn sich das gerade nicht sagen laesst (Chunk nicht geladen).
     */
    public int currentTier(Loader loader) {
        World world = Bukkit.getWorld(loader.world());
        if (world == null || !world.isChunkLoaded(loader.x() >> 4, loader.z() >> 4)) {
            return -1;
        }
        return Column.tierAt(world.getBlockAt(loader.x(), loader.y(), loader.z()));
    }

    /** Aendert die Stufe, behaelt aber ID und Besitzer. Gibt den neuen Loader zurueck. */
    public Loader changeTier(Loader old, int tier) {
        World world = Bukkit.getWorld(old.world());
        if (world == null || loaders.get(old.id()) == null) {
            return old;
        }
        releaseChunks(world, old);
        Loader updated = new Loader(old.id(), old.owner(), old.world(), old.x(), old.y(), old.z(), tier, old.created());
        loaders.put(updated.id(), updated);
        holdChunks(world, updated);
        save();
        return updated;
    }

    private void indexBlocks(Loader loader) {
        for (int i = 0; i < 3; i++) {
            index.put(new BlockKey(loader.world(), loader.x(), loader.y() + i, loader.z()), loader.id());
        }
    }

    private Set<ChunkKey> chunksOf(World world, Loader loader) {
        int r = radius(loader.tier());
        Set<ChunkKey> keys = new HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                keys.add(new ChunkKey(world.getUID(), (loader.x() >> 4) + dx, (loader.z() >> 4) + dz));
            }
        }
        return keys;
    }

    private void holdChunks(World world, Loader loader) {
        if (held.containsKey(loader.id())) {
            return;
        }
        Set<ChunkKey> keys = chunksOf(world, loader);
        held.put(loader.id(), keys);
        for (ChunkKey key : keys) {
            if (tickets.merge(key, 1, Integer::sum) == 1) {
                world.addPluginChunkTicket(key.x(), key.z(), plugin);
            }
        }
    }

    private void releaseChunks(World world, Loader loader) {
        Set<ChunkKey> keys = held.remove(loader.id());
        if (keys == null) {
            return;
        }
        for (ChunkKey key : keys) {
            Integer count = tickets.get(key);
            if (count == null) {
                continue;
            }
            if (count <= 1) {
                tickets.remove(key);
                world.removePluginChunkTicket(key.x(), key.z(), plugin);
            } else {
                tickets.put(key, count - 1);
            }
        }
    }

    private void save() {
        try {
            store.save(loaders.values());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "loaders.json konnte nicht geschrieben werden", e);
        }
    }
}
