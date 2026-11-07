package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.UUID;

/** Domain HOMES: named player homes, in creation order per player. */
public record HomesData(List<Home> homes) {

    public static HomesData empty() { return new HomesData(List.of()); }

    public record Home(UUID player, String name, LocationData location) {}
}
