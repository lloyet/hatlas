package org.minecraft.hatlas.donjon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DonjonWave {

    private final int waveNumber;
    private final boolean bossWave;
    private final List<String> mobTypesToSpawn;
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private boolean started;

    public DonjonWave(int waveNumber, boolean bossWave, List<String> mobTypesToSpawn) {
        this.waveNumber = waveNumber;
        this.bossWave = bossWave;
        this.mobTypesToSpawn = mobTypesToSpawn;
        this.started = false;
    }

    public int getWaveNumber() { return waveNumber; }
    public boolean isBossWave() { return bossWave; }
    public List<String> getMobTypesToSpawn() { return mobTypesToSpawn; }
    public Set<UUID> getSpawnedEntities() { return spawnedEntities; }
    public boolean isStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }

    public void addSpawnedEntity(UUID uuid) { spawnedEntities.add(uuid); }
    public void removeSpawnedEntity(UUID uuid) { spawnedEntities.remove(uuid); }
    public boolean isCompleted() { return started && spawnedEntities.isEmpty(); }
}
