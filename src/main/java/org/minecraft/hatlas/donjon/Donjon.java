package org.minecraft.hatlas.donjon;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.Collections;

public class Donjon {

    private final String id;
    private String name;
    private final String type;
    private final World world;
    private int level;
    private DonjonRarity rarity;
    private DonjonStatus status;

    // Wave progression
    private List<DonjonWave> waves = new ArrayList<>();
    private int currentWaveIndex;
    private boolean inProgress;

    // Timing
    private long activationTime;
    private boolean timeoutWarned;

    // Faction tracking
    private String startingFaction;
    private final Map<String, Double> bossDamageMap = new HashMap<>();

    // Player presence (recalculated each tick, not persisted)
    private final Set<UUID> playersInside = new HashSet<>();

    // Auxiliary entities (e.g. chickens in chicken-jockeys) — tracked for cleanup only
    private final Set<UUID> auxiliaryEntities = new HashSet<>();

    // Protected chunk keys — admin-managed via /donjon claim and /donjon unclaim
    private final Set<Long> protectedChunkKeys = new HashSet<>();

    // Wave spawn points — keyed by auto-increment ID for admin management
    private final LinkedHashMap<Integer, Location> spawnPoints = new LinkedHashMap<>();
    private int nextSpawnId = 0;

    // Nametag display
    private Location nametagLocation;
    private UUID textDisplayUUID;

    // Block players right-click to trigger this donjon — set via /donjon set vault
    private Location vaultLocation;

    // Player teleport destination set via /donjon set spawn
    private Location teleportSpawn;

    public Donjon(String id, String name, String type, World world, int level, DonjonRarity rarity) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.world = world;
        this.level = level;
        this.rarity = rarity;
        this.status = DonjonStatus.IDLE;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public World getWorld() { return world; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public DonjonRarity getRarity() { return rarity; }
    public void setRarity(DonjonRarity rarity) { this.rarity = rarity; }
    public DonjonStatus getStatus() { return status; }
    public void setStatus(DonjonStatus status) { this.status = status; }

    public List<DonjonWave> getWaves() { return waves; }
    public void setWaves(List<DonjonWave> waves) { this.waves = waves; }
    public int getCurrentWaveIndex() { return currentWaveIndex; }
    public void setCurrentWaveIndex(int idx) { this.currentWaveIndex = idx; }
    public boolean isInProgress() { return inProgress; }
    public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }

    public long getActivationTime() { return activationTime; }
    public void setActivationTime(long time) { this.activationTime = time; }
    public boolean isTimeoutWarned() { return timeoutWarned; }
    public void setTimeoutWarned(boolean warned) { this.timeoutWarned = warned; }

    public String getStartingFaction() { return startingFaction; }
    public void setStartingFaction(String faction) { this.startingFaction = faction; }
    public Map<String, Double> getBossDamageMap() { return bossDamageMap; }

    public Set<UUID> getPlayersInside() { return playersInside; }
    public Set<UUID> getAuxiliaryEntities() { return auxiliaryEntities; }
    public Set<Long> getProtectedChunkKeys() { return protectedChunkKeys; }

    /** Returns a copy of all spawn point locations (list order = insertion order). Used by the wave system. */
    public List<Location> getSpawnPoints() { return new ArrayList<>(spawnPoints.values()); }

    /** Replaces all spawn points; resets ID counter to 0. */
    public void setSpawnPoints(List<Location> points) {
        spawnPoints.clear();
        nextSpawnId = 0;
        for (Location l : points) spawnPoints.put(nextSpawnId++, l.clone());
    }

    /** Adds a spawn point with the next auto-increment ID. Returns the assigned ID. */
    public int addSpawnPoint(Location loc) {
        int id = nextSpawnId++;
        spawnPoints.put(id, loc.clone());
        return id;
    }

    /** Inserts a spawn point at a specific ID (used when loading from file). */
    public void putSpawnPoint(int id, Location loc) {
        spawnPoints.put(id, loc.clone());
        if (id >= nextSpawnId) nextSpawnId = id + 1;
    }

    /** Removes the spawn point with the given ID. Returns true if it existed. */
    public boolean removeSpawnPoint(int id) { return spawnPoints.remove(id) != null; }

    /** Returns an unmodifiable view of the ID → location map. */
    public Map<Integer, Location> getSpawnPointsById() { return Collections.unmodifiableMap(spawnPoints); }

    public int getNextSpawnId() { return nextSpawnId; }
    public void setNextSpawnId(int id) { this.nextSpawnId = id; }

    public Location getNametagLocation() { return nametagLocation != null ? nametagLocation.clone() : null; }
    public void setNametagLocation(Location loc) { this.nametagLocation = loc != null ? loc.clone() : null; }
    public UUID getTextDisplayUUID() { return textDisplayUUID; }
    public void setTextDisplayUUID(UUID uuid) { this.textDisplayUUID = uuid; }

    public Location getVaultLocation() { return vaultLocation != null ? vaultLocation.clone() : null; }
    public void setVaultLocation(Location loc) { this.vaultLocation = loc != null ? loc.clone() : null; }

    public Location getTeleportSpawn() { return teleportSpawn != null ? teleportSpawn.clone() : null; }
    public void setTeleportSpawn(Location loc) { this.teleportSpawn = loc != null ? loc.clone() : null; }

    public DonjonWave getCurrentWave() {
        if (waves.isEmpty() || currentWaveIndex >= waves.size()) return null;

        return waves.get(currentWaveIndex);
    }
}
