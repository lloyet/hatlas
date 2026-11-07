package org.minecraft.hatlas.storage.model;

/** A chunk in a world. Runtime code keys chunks as {@code "world:cx:cz"} strings. */
public record ChunkPos(String world, int x, int z) {

    public String runtimeKey() {
        return world + ":" + x + ":" + z;
    }

    /** Parses {@code "world:cx:cz"}; returns null on malformed input. */
    public static ChunkPos fromRuntimeKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            return new ChunkPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Same packing as {@code org.bukkit.Chunk.getChunkKey(x, z)}. */
    public static long packKey(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    public static int unpackX(long key) { return (int) key; }
    public static int unpackZ(long key) { return (int) (key >> 32); }
}
