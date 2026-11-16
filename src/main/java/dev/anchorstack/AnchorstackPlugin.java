package dev.anchorstack;

import dev.anchorstack.command.AnchorstackCommand;
import dev.anchorstack.listener.StructureListener;
import dev.anchorstack.listener.WorldListener;
import dev.anchorstack.loader.BorderDisplay;
import dev.anchorstack.loader.Loader;
import dev.anchorstack.loader.LoaderManager;
import dev.anchorstack.loader.LoaderStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnchorstackPlugin extends JavaPlugin {

    private LoaderManager manager;
    private BorderDisplay border;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new LoaderManager(this, new LoaderStore(getDataFolder().toPath().resolve("loaders.json")));
        manager.load();
        border = new BorderDisplay(this, manager);

        getServer().getPluginManager().registerEvents(new StructureListener(this, manager, border), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this, manager), this);

        AnchorstackCommand command = new AnchorstackCommand(this, manager);
        getCommand("anchorstack").setExecutor(command);
        getCommand("anchorstack").setTabCompleter(command);

        startTasks();
        // Kurz nach dem Start, wenn die gehaltenen Chunks geladen sind, tote Loader aufraeumen.
        // Nach startTasks(), weil das alle Tasks des Plugins neu setzt.
        getServer().getScheduler().runTaskLater(this, this::cleanup, 100L);
        getLogger().info(manager.all().size() + " Chunkloader geladen, " + manager.heldChunks() + " Chunks gehalten.");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (manager != null) {
            manager.shutdown();
        }
    }

    /** Startet die Pruefung und den Partikelring neu, zum Beispiel nach /anchorstack reload. */
    public void startTasks() {
        getServer().getScheduler().cancelTasks(this);

        long validate = Math.max(10, getConfig().getLong("validate-interval-seconds", 300)) * 20L;
        getServer().getScheduler().runTaskTimer(this, this::cleanup, validate, validate);

        int borderStep = border.intervalTicks();
        getServer().getScheduler().runTaskTimer(this, border::tick, borderStep, borderStep);

        long particles = getConfig().getLong("particle-interval-seconds", 4) * 20L;
        if (particles > 0) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (Loader loader : manager.all()) {
                    World world = Bukkit.getWorld(loader.world());
                    if (world == null || !world.isChunkLoaded(loader.x() >> 4, loader.z() >> 4)) {
                        continue;
                    }
                    // Kleiner Funkenring um die Blitzableiter-Spitze als Zeichen, dass der Loader laeuft
                    for (int i = 0; i < 6; i++) {
                        double angle = i * Math.PI / 3;
                        world.spawnParticle(Particle.END_ROD, loader.x() + 0.5 + Math.cos(angle) * 0.45,
                                loader.y() + 3.1, loader.z() + 0.5 + Math.sin(angle) * 0.45, 0, 0, 0.05, 0, 1);
                    }
                }
            }, particles, particles);
        }
    }

    /** Entfernt alle Loader, deren Saeule nicht mehr steht. Liefert, wie viele es waren. */
    public int cleanup() {
        int removed = 0;
        for (Loader loader : manager.all()) {
            if (check(loader)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Gleicht einen Loader mit seiner Saeule ab. Fehlt die Saeule, wird er entfernt (Rueckgabe true).
     * Wurde der Mittelblock getauscht, bekommt er die neue Stufe und behaelt ID und Besitzer.
     */
    public boolean check(Loader loader) {
        int now = manager.currentTier(loader);
        if (now < 0 || manager.get(loader.id()) == null) {
            return false;
        }
        if (now == 0) {
            manager.remove(loader);
            notifyOwner(loader, "removed", "reason-invalid");
            getLogger().info("Loader " + loader.shortId() + " bei " + loader.pos() + " entfernt: Saeule fehlt. " + describe(loader));
            return true;
        }
        if (now != loader.tier()) {
            retier(loader, now);
        }
        return false;
    }

    private void retier(Loader loader, int tier) {
        World world = Bukkit.getWorld(loader.world());
        if (world == null) {
            return;
        }
        if (tier > loader.tier()) {
            int added = manager.newChunks(world, loader.x(), loader.z(), tier);
            if (manager.heldChunks() + added > getConfig().getInt("limits.max-total-chunks", 400)) {
                return;
            }
        }
        Loader updated = manager.changeTier(loader, tier);
        border.show(updated);
        getLogger().info("Loader " + updated.shortId() + " bei " + updated.pos() + " auf Stufe " + tier + " gestellt.");
        Player owner = Bukkit.getPlayer(updated.owner());
        if (owner != null) {
            owner.sendMessage(msg("upgraded", "tier", String.valueOf(tier), "chunks", String.valueOf(manager.chunkCount(tier))));
            Location top = new Location(world, updated.x() + 0.5, updated.y() + 3.2, updated.z() + 0.5);
            world.spawnParticle(Particle.END_ROD, top, 30, 0.3, 0.4, 0.3, 0.06);
            world.playSound(top, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        }
    }

    /** Welche Bloecke an den drei Saeulenpositionen stehen, fuer das Log. */
    private String describe(Loader loader) {
        World world = Bukkit.getWorld(loader.world());
        if (world == null) {
            return "(Welt fehlt)";
        }
        StringBuilder sb = new StringBuilder("Bloecke: ");
        for (int i = 0; i < 3; i++) {
            sb.append(world.getBlockAt(loader.x(), loader.y() + i, loader.z()).getBlockData().getAsString()).append(' ');
        }
        return sb.toString().trim();
    }

    /** Liest eine Nachricht aus der Config, ersetzt {platzhalter} und macht daraus eine Komponente. */
    public Component msg(String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return MiniMessage.miniMessage().deserialize(text);
    }

    /** Sagt dem Besitzer, falls online, dass sein Loader ausgefallen ist. */
    public void notifyOwner(Loader loader, String key, String reasonKey) {
        Player owner = Bukkit.getPlayer(loader.owner());
        if (owner != null) {
            String reason = getConfig().getString("messages." + reasonKey, reasonKey);
            owner.sendMessage(msg(key, "pos", loader.pos(), "reason", reason));
        }
    }

    /** Wie viele Loader ein Spieler haben darf: Config-Wert oder hoechste Permission anchorstack.limit.n. */
    public int limitFor(Player player) {
        int max = getConfig().getInt("limits.per-player", 5);
        for (var info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith("anchorstack.limit.")) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring("anchorstack.limit.".length())));
                } catch (NumberFormatException ignored) {
                    // Permission mit anderem Suffix, nicht fuer uns
                }
            }
        }
        return max;
    }
}
