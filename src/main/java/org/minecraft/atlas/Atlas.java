package org.minecraft.atlas;

import com.github.retrooper.packetevents.PacketEvents;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import org.minecraft.atlas.combat.CombatLogManager;
import org.minecraft.atlas.command.RandomTeleportCommand;
import org.minecraft.atlas.command.HomeCommand;
import org.minecraft.atlas.command.SafeZoneCommand;
import org.minecraft.atlas.command.SpawnCommand;
import org.minecraft.atlas.command.TpaCommand;
import org.minecraft.atlas.command.CrystalCommand;
import org.minecraft.atlas.command.DonjonCommand;
import org.minecraft.atlas.command.FactionCommand;
import org.minecraft.atlas.command.ItemCommand;
import org.minecraft.atlas.command.TagCommand;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.SmugglerManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
import org.minecraft.atlas.teleport.RandomTeleportManager;
import org.minecraft.atlas.teleport.DeathTeleportCooldownManager;
import org.minecraft.atlas.teleport.HomeManager;
import org.minecraft.atlas.teleport.HomeTeleportManager;
import org.minecraft.atlas.safezone.SafeZoneManager;
import org.minecraft.atlas.safezone.SafeZoneTeleportManager;
import org.minecraft.atlas.spawn.SpawnManager;
import org.minecraft.atlas.teleport.TeleportAtManager;
import org.minecraft.atlas.listener.CrystalListener;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionClaimBorderRenderer;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.listener.ChatListener;
import org.minecraft.atlas.listener.DonjonListener;
import org.minecraft.atlas.listener.FactionListener;
import org.minecraft.atlas.listener.SafeZoneNpcListener;
import org.minecraft.atlas.listener.SafeZoneListener;
import org.minecraft.atlas.listener.TagListener;
import org.minecraft.atlas.command.JobCommand;
import org.minecraft.atlas.tag.TagManager;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.JokeyriniManager;
import org.minecraft.atlas.quest.QuestCommand;
import org.minecraft.atlas.quest.QuestManager;
import org.minecraft.atlas.listener.GuiListener;
import org.minecraft.atlas.listener.JobListener;
import org.minecraft.atlas.block.RubyBlockManager;
import org.minecraft.atlas.Item.AmethystItem;
import org.minecraft.atlas.Item.RubyItem;
import org.minecraft.atlas.listener.BlockListener;
import org.minecraft.atlas.listener.RubyRestrictionListener;
import org.minecraft.atlas.listener.AuctionNpcListener;
import org.minecraft.atlas.listener.LocatorBarListener;
import org.minecraft.atlas.auction.AuctionManager;
import org.minecraft.atlas.command.AuctionCommand;
import org.minecraft.atlas.command.OreZoneCommand;
import org.minecraft.atlas.orezone.OreZoneManager;
import org.minecraft.atlas.command.ShopCommand;
import org.minecraft.atlas.listener.ShopNpcListener;
import org.minecraft.atlas.shop.ShopManager;
import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.DataSet;
import org.minecraft.atlas.storage.Storage;
import org.minecraft.atlas.storage.StorageException;
import org.minecraft.atlas.storage.StorageSettings;
import org.minecraft.atlas.storage.model.CrystalsData;
import org.minecraft.atlas.storage.model.DonjonsData;
import org.minecraft.atlas.storage.model.FactionsData;
import org.minecraft.atlas.storage.model.HomesData;
import org.minecraft.atlas.storage.model.JobsData;
import org.minecraft.atlas.storage.model.MiscData;
import org.minecraft.atlas.storage.model.SafeZonesData;
import org.minecraft.atlas.storage.model.ShopsData;
import org.minecraft.atlas.util.AfkManager;
import org.minecraft.atlas.util.ItemClearManager;
import org.minecraft.atlas.util.NpcLookHelper;
import org.minecraft.atlas.util.ResourcePackManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.atlas.listener.GolemListener;
import org.minecraft.atlas.listener.TradeListener;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    // Tuning files (shipped in resources). Runtime data goes through Storage.
    public static YamlConfiguration factionsConfig;
    public static File factionsFile;

    public static YamlConfiguration donjonsConfig;
    public static File donjonsFile;

    public static YamlConfiguration jobsConfig;
    public static File jobsFile;

    public static YamlConfiguration homesConfig;
    public static File homesFile;

    public static YamlConfiguration safezonesConfig;
    public static File safezonesFile;

    public static YamlConfiguration combatsConfig;
    public static File combatsFile;

    public static YamlConfiguration auctionsConfig;
    public static File auctionsFile;

    public static YamlConfiguration orezonesConfig;
    public static File orezonesFile;

    public static YamlConfiguration shopsConfig;
    public static File shopsFile;

    @Override
    public void onEnable() {
        instance = this;

        // Locator-bar faction filter. packetevents runs as its own plugin and owns the API
        // lifecycle (load/init/terminate) — we only register our listener against it.
        PacketEvents.getAPI().getEventManager().registerListener(new LocatorBarListener());

        // config.yml — storage, tpa, random teleport, AFK, death-teleport cooldown, resource pack.
        // copyDefaults adds new sections (e.g. storage) to an existing config.yml.
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        FileConfiguration configFile = getConfig();

        factionsConfig  = loadTuning("factions.yml");
        donjonsConfig   = loadTuning("donjons.yml");
        jobsConfig      = loadTuning("jobs.yml");
        homesConfig     = loadTuning("homes.yml");
        safezonesConfig = loadTuning("safezones.yml");
        combatsConfig   = loadTuning("combats.yml");
        auctionsConfig  = loadTuning("auctions.yml");
        orezonesConfig  = loadTuning("orezones.yml");
        shopsConfig     = loadTuning("shops.yml");
        factionsFile  = new File(getDataFolder(), "factions.yml");
        donjonsFile   = new File(getDataFolder(), "donjons.yml");
        jobsFile      = new File(getDataFolder(), "jobs.yml");
        homesFile     = new File(getDataFolder(), "homes.yml");
        safezonesFile = new File(getDataFolder(), "safezones.yml");
        combatsFile   = new File(getDataFolder(), "combats.yml");
        auctionsFile  = new File(getDataFolder(), "auctions.yml");
        orezonesFile  = new File(getDataFolder(), "orezones.yml");
        shopsFile     = new File(getDataFolder(), "shops.yml");

        // Tuning
        AtlasCrystalManager.loadConfig(factionsConfig);
        SafeZoneTeleportManager.loadConfig(safezonesConfig);
        RandomTeleportManager.loadConfig(configFile);
        HomeManager.loadConfig(homesConfig);
        HomeTeleportManager.loadConfig(factionsConfig);
        TeleportAtManager.loadConfig(configFile);
        AfkManager.loadConfig(configFile);
        DeathTeleportCooldownManager.loadConfig(configFile);
        CombatLogManager.loadConfig(combatsConfig);
        FactionLevelManager.loadUpgrades(factionsConfig);
        QuestManager.loadQuestConfig(jobsConfig);
        JokeyriniManager.loadJokeyriniConfig(jobsConfig);
        DonjonManager.loadConfig(donjonsConfig);
        SmugglerManager.loadConfig(donjonsConfig);
        AuctionManager.loadConfig(auctionsConfig);
        OreZoneManager.loadConfig(orezonesConfig);
        ShopManager.loadConfig(shopsConfig);
        ResourcePackManager.loadConfig(configFile);

        ElectricalCreeperManager.init();
        SmugglerManager.init();
        RaiderPickaxe.init();
        RubyItem.init();
        RubyItem.registerRecipes();
        AmethystItem.init();
        AmethystItem.registerRecipes();
        RubyBlockManager.init();
        TagManager.init();

        // Runtime data (YAML, SQLite or PostgreSQL — see config.yml > storage)
        DataSet data;
        try {
            data = Storage.start(this, StorageSettings.from(configFile, getDataFolder()));
        } catch (StorageException e) {
            getLogger().log(Level.SEVERE, "Storage unavailable — Atlas is disabled to protect the data: "
                    + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerSnapshots();

        // Restore order matters: factions before crystals (orphan check) and auctions
        // (faction fallback); config before donjons/shops/ore zones/blocks.
        FactionManager.restore(data.get(DataDomain.FACTIONS, FactionsData.class));
        if (AtlasCrystalManager.restoreData(data.get(DataDomain.CRYSTALS, CrystalsData.class)) > 0) {
            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        }
        JobManager.restore(data.get(DataDomain.JOBS, JobsData.class));
        DonjonManager.restore(data.get(DataDomain.DONJONS, DonjonsData.class));
        HomeManager.restore(data.get(DataDomain.HOMES, HomesData.class));
        SafeZoneManager.restore(data.get(DataDomain.SAFEZONES, SafeZonesData.class));
        CombatLogManager.restore(data.get(DataDomain.COMBAT, MiscData.CombatData.class));
        RubyBlockManager.restore(data.get(DataDomain.BLOCKS, MiscData.BlocksData.class));
        AuctionManager.restore(data.get(DataDomain.AUCTIONS, MiscData.AuctionsData.class));
        OreZoneManager.restore(data.get(DataDomain.OREZONES, MiscData.OreZonesData.class));
        TagManager.restore(data.get(DataDomain.TAGS, MiscData.TagsData.class));
        SpawnManager.restore(data.get(DataDomain.SPAWN, MiscData.SpawnData.class));
        if (ShopManager.restore(data.get(DataDomain.SHOPS, ShopsData.class), shopsConfig)) {
            // Shops were imported from the old shops.yml layout: store them, then drop the
            // section so they are not imported again.
            Storage.save(DataDomain.SHOPS);
            shopsConfig.set("shops", null);
            try {
                shopsConfig.save(shopsFile);
            } catch (IOException e) {
                getLogger().warning("Could not remove the imported 'shops' section from shops.yml: " + e.getMessage());
            }
        }
        // Every manager now holds its data: writes are safe from here on.
        Storage.markReady();
        ShopManager.spawnMissingNpcs();

        // Register all listeners
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);
        getServer().getPluginManager().registerEvents(new JobListener(), this);
        getServer().getPluginManager().registerEvents(new DonjonListener(), this);
        getServer().getPluginManager().registerEvents(new TagListener(), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new SafeZoneListener(), this);
        getServer().getPluginManager().registerEvents(new SafeZoneNpcListener(), this);
        getServer().getPluginManager().registerEvents(new BlockListener(), this);
        getServer().getPluginManager().registerEvents(new RubyRestrictionListener(), this);
        getServer().getPluginManager().registerEvents(new AuctionNpcListener(), this);
        getServer().getPluginManager().registerEvents(new ShopNpcListener(), this);
        AfkManager afkManager = new AfkManager();
        getServer().getPluginManager().registerEvents(afkManager, this);
        getServer().getPluginManager().registerEvents(new org.minecraft.atlas.listener.CombatLogListener(), this);
        getServer().getPluginManager().registerEvents(new ResourcePackManager(), this);
        ResourcePackManager.sendToAll();

        // Schedulers
        GolemListener.schedule(this);
        AtlasCrystalManager.schedule(this);
        DonjonManager.schedule(this);
        QuestManager.scheduleExpiry(this);
        ItemClearManager.schedule(this);
        AfkManager.schedule(this);
        CombatLogManager.schedule(this);
        FactionClaimBorderRenderer.schedule(this);
        NpcLookHelper.schedule(this);
        OreZoneManager.schedule(this);
        ShopManager.schedule(this);

        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(FactionCommand.build(), "Faction management commands", List.of("f"));
                        event.registrar().register(TradeCommand.build());
                        event.registrar().register(JobCommand.build());
                        event.registrar().register(QuestCommand.build());
                        event.registrar().register(CrystalCommand.build());
                        event.registrar().register(DonjonCommand.build());
                        event.registrar().register(TagCommand.build());
                        event.registrar().register(RandomTeleportCommand.build());
                        event.registrar().register(SpawnCommand.build());
                        event.registrar().register(SafeZoneCommand.build());
                        event.registrar().register(TpaCommand.build());
                        event.registrar().register(HomeCommand.buildSetHome());
                        event.registrar().register(HomeCommand.buildHome());
                        event.registrar().register(HomeCommand.buildDelHome());
                        event.registrar().register(ItemCommand.build());
                        event.registrar().register(AuctionCommand.build());
                        event.registrar().register(OreZoneCommand.build());
                        event.registrar().register(ShopCommand.build());
                    }
                }
        );

        getLogger().info("Atlas enabled.");
    }

    /** Each domain's snapshot supplier, used by Storage for saves, autosave and shutdown. */
    private static void registerSnapshots() {
        Storage.register(DataDomain.FACTIONS, FactionManager::snapshot);
        Storage.register(DataDomain.CRYSTALS, AtlasCrystalManager::snapshot);
        Storage.register(DataDomain.JOBS, JobManager::snapshot);
        Storage.register(DataDomain.DONJONS, DonjonManager::snapshot);
        Storage.register(DataDomain.HOMES, HomeManager::snapshot);
        Storage.register(DataDomain.SAFEZONES, SafeZoneManager::snapshot);
        Storage.register(DataDomain.COMBAT, CombatLogManager::snapshot);
        Storage.register(DataDomain.BLOCKS, RubyBlockManager::snapshot);
        Storage.register(DataDomain.AUCTIONS, AuctionManager::snapshot);
        Storage.register(DataDomain.OREZONES, OreZoneManager::snapshot);
        Storage.register(DataDomain.SHOPS, ShopManager::snapshot);
        Storage.register(DataDomain.TAGS, TagManager::snapshot);
        Storage.register(DataDomain.SPAWN, SpawnManager::snapshot);
    }

    /** Loads a tuning file, copying the default from the jar on first start. */
    private YamlConfiguration loadTuning(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
        return YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void onDisable() {
        // Mark shutdown first so any incoming PlayerQuitEvent does not trigger the
        // combat-log kill — the combat timers are persisted below.
        CombatLogManager.setShuttingDown(true);
        // Waits for pending async saves, then writes every domain synchronously.
        // Does nothing if the data was never loaded (failed startup), so stored data is safe.
        Storage.shutdown();
        getLogger().info("Atlas disabled.");
    }
}
