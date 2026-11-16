package dev.anchorstack.loader;

import java.util.UUID;

/** Ein aktiver Chunkloader. x/y/z ist der Kupferblock ganz unten in der Saeule. */
public record Loader(UUID id, UUID owner, UUID world, int x, int y, int z, int tier, long created) {

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public String pos() {
        return x + " " + y + " " + z;
    }
}
