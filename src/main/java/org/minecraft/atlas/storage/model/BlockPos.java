package org.minecraft.atlas.storage.model;

/** Integer block coordinates in a world. */
public record BlockPos(String world, int x, int y, int z) {}
