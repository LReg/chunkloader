package dev.anchorstack.loader;

import java.util.UUID;
import org.bukkit.block.Block;

public record BlockKey(UUID world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
