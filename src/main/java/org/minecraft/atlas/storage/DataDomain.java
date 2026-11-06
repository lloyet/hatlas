package org.minecraft.atlas.storage;

import org.minecraft.atlas.storage.model.CrystalsData;
import org.minecraft.atlas.storage.model.DonjonsData;
import org.minecraft.atlas.storage.model.FactionsData;
import org.minecraft.atlas.storage.model.HomesData;
import org.minecraft.atlas.storage.model.JobsData;
import org.minecraft.atlas.storage.model.MiscData;
import org.minecraft.atlas.storage.model.SafeZonesData;
import org.minecraft.atlas.storage.model.ShopsData;

import java.util.function.Supplier;

/**
 * A unit of persisted runtime state. Each domain is always written as a whole (full
 * replacement inside one transaction), so a write can never leave half a domain on disk.
 */
public enum DataDomain {
    FACTIONS(FactionsData.class, FactionsData::empty),
    CRYSTALS(CrystalsData.class, CrystalsData::empty),
    JOBS(JobsData.class, JobsData::empty),
    DONJONS(DonjonsData.class, DonjonsData::empty),
    HOMES(HomesData.class, HomesData::empty),
    SAFEZONES(SafeZonesData.class, SafeZonesData::empty),
    COMBAT(MiscData.CombatData.class, MiscData.CombatData::empty),
    BLOCKS(MiscData.BlocksData.class, MiscData.BlocksData::empty),
    AUCTIONS(MiscData.AuctionsData.class, MiscData.AuctionsData::empty),
    OREZONES(MiscData.OreZonesData.class, MiscData.OreZonesData::empty),
    SHOPS(ShopsData.class, ShopsData::empty),
    TAGS(MiscData.TagsData.class, MiscData.TagsData::empty),
    SPAWN(MiscData.SpawnData.class, MiscData.SpawnData::empty);

    private final Class<?> type;
    private final Supplier<?> empty;

    DataDomain(Class<?> type, Supplier<?> empty) {
        this.type = type;
        this.empty = empty;
    }

    public Class<?> type() { return type; }

    public Object empty() { return empty.get(); }
}
