package dev.anchorstack.loader;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.LightningRod;

/** Die Drei-Block-Saeule: Kupferblock, Obsidian oder Crying Obsidian, aufrechter Blitzableiter. */
public final class Column {

    private Column() {
    }

    /** Kupferblock in allen Oxidationsstufen, gewachst oder nicht. */
    public static boolean isCopperBlock(Material m) {
        return switch (m) {
            case COPPER_BLOCK, EXPOSED_COPPER, WEATHERED_COPPER, OXIDIZED_COPPER,
                 WAXED_COPPER_BLOCK, WAXED_EXPOSED_COPPER, WAXED_WEATHERED_COPPER, WAXED_OXIDIZED_COPPER -> true;
            default -> false;
        };
    }

    /** Blitzableiter in allen Oxidationsstufen, gewachst oder nicht. */
    public static boolean isLightningRod(Material m) {
        return switch (m) {
            case LIGHTNING_ROD, EXPOSED_LIGHTNING_ROD, WEATHERED_LIGHTNING_ROD, OXIDIZED_LIGHTNING_ROD,
                 WAXED_LIGHTNING_ROD, WAXED_EXPOSED_LIGHTNING_ROD, WAXED_WEATHERED_LIGHTNING_ROD,
                 WAXED_OXIDIZED_LIGHTNING_ROD -> true;
            default -> false;
        };
    }

    public static boolean isPart(Material m) {
        return isCopperBlock(m) || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || isLightningRod(m);
    }

    /** Liefert die Stufe (1 oder 2), oder 0 wenn ueber base keine gueltige Saeule steht. */
    public static int tierAt(Block base) {
        if (!isCopperBlock(base.getType())) {
            return 0;
        }
        int tier = switch (base.getRelative(BlockFace.UP).getType()) {
            case OBSIDIAN -> 1;
            case CRYING_OBSIDIAN -> 2;
            default -> 0;
        };
        if (tier == 0) {
            return 0;
        }
        Block top = base.getRelative(0, 2, 0);
        if (!isLightningRod(top.getType())) {
            return 0;
        }
        if (top.getBlockData() instanceof LightningRod rod && rod.getFacing() == BlockFace.UP) {
            return tier;
        }
        return 0;
    }
}
