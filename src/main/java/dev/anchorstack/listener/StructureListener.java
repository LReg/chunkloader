package dev.anchorstack.listener;

import dev.anchorstack.AnchorstackPlugin;
import dev.anchorstack.loader.BorderDisplay;
import dev.anchorstack.loader.Column;
import dev.anchorstack.loader.Loader;
import dev.anchorstack.loader.LoaderManager;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Baut Loader beim Platzieren der Saeule und beendet sie, wenn die Saeule zerstoert wird. */
public final class StructureListener implements Listener {

    private final AnchorstackPlugin plugin;
    private final LoaderManager manager;
    private final BorderDisplay border;

    public StructureListener(AnchorstackPlugin plugin, LoaderManager manager, BorderDisplay border) {
        this.plugin = plugin;
        this.manager = manager;
        this.border = border;
    }

    // --- Aktivieren ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Material type = placed.getType();
        if (!Column.isPart(type)) {
            return;
        }
        int above = Column.isCopperBlock(type) ? 0 : Column.isLightningRod(type) ? 2 : 1;
        Block base = placed.getRelative(0, -above, 0);
        Player player = event.getPlayer();
        // Einen Tick spaeter, damit der Block sicher gesetzt und seine Ausrichtung gelesen ist
        plugin.getServer().getScheduler().runTask(plugin, () -> tryCreate(player, base));
    }

    private void tryCreate(Player player, Block base) {
        Loader existing = manager.byBlock(base);
        if (existing != null) {
            // Umgebaute Saeule: Stufe anpassen
            plugin.check(existing);
            return;
        }
        int tier = Column.tierAt(base);
        if (tier == 0) {
            return;
        }
        if (!player.hasPermission("anchorstack.create")) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        int max = plugin.limitFor(player);
        if (manager.ownedBy(player.getUniqueId()).size() >= max) {
            player.sendMessage(plugin.msg("limit-player", "max", String.valueOf(max)));
            return;
        }
        int added = manager.newChunks(base.getWorld(), base.getX(), base.getZ(), tier);
        if (manager.heldChunks() + added > plugin.getConfig().getInt("limits.max-total-chunks", 400)) {
            player.sendMessage(plugin.msg("limit-total"));
            return;
        }
        Loader loader = manager.create(player.getUniqueId(), base, tier);
        border.show(loader);
        player.sendMessage(plugin.msg("created", "tier", String.valueOf(tier),
                "chunks", String.valueOf(manager.chunkCount(tier))));
        Location top = base.getLocation().add(0.5, 3.2, 0.5);
        base.getWorld().spawnParticle(Particle.END_ROD, top, 40, 0.3, 0.4, 0.3, 0.08);
        base.getWorld().playSound(top, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
    }

    // --- Grenzen anzeigen ---

    /** Rechtsklick auf die Saeule zeigt die Grenzen noch einmal. Wer einen Block in der Hand hat, baut nur. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null) {
            return;
        }
        Loader loader = manager.byBlock(block);
        Material held = event.getPlayer().getInventory().getItemInMainHand().getType();
        if (loader == null || (held.isBlock() && !held.isAir())) {
            return;
        }
        border.show(loader);
    }

    // --- Schutz der Saeule ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        end(event.getBlock(), "reason-broken");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            end(block, "reason-broken");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            end(block, "reason-broken");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        end(event.getBlock(), "reason-broken");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        cancelIfStructure(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        cancelIfStructure(event.getBlocks(), event);
    }

    private void cancelIfStructure(List<Block> blocks, org.bukkit.event.Cancellable event) {
        for (Block block : blocks) {
            if (manager.byBlock(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void end(Block block, String reasonKey) {
        Loader loader = manager.byBlock(block);
        if (loader != null) {
            manager.remove(loader);
            plugin.notifyOwner(loader, "removed", reasonKey);
        }
    }
}
