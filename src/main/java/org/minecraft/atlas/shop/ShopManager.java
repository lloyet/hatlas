package org.minecraft.atlas.shop;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.FactionVault;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.job.PlayerJobData;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.ItemBytes;
import org.minecraft.atlas.storage.Storage;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.storage.model.ShopsData;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Job shops. Each shop is a villager NPC that sells a rotating selection drawn by weight
 * from its common and rare item pools.
 *
 * <p>{@code shops.yml} holds tuning and the per-job default pools used to seed new shops.
 * The shops themselves (job, NPC, pools), the current rotation and the last reset time
 * are persisted through {@code Storage} (domain SHOPS). Shops still hand-written in the old
 * {@code shops.yml > shops} section are imported once at startup.
 */
public final class ShopManager {

    /** Upper bound on items per tier — the shop GUI shows at most 28 (4 rows × 7). */
    public static final int MAX_ITEMS_PER_TIER = 28;

    // shop name (lowercase) -> Shop, insertion-ordered for stable listing
    private static final Map<String, Shop> shops = new LinkedHashMap<>();

    private static long resetIntervalHours = 168L;
    private static final Map<ShopTier, Integer> itemsPerTier = new EnumMap<>(ShopTier.class);
    private static long lastResetMs = 0L;

    private static NamespacedKey KEY_NPC;

    private ShopManager() {}

    public static NamespacedKey getKeyNpc() {
        if (KEY_NPC == null) KEY_NPC = new NamespacedKey(Atlas.instance, "shop_npc");
        return KEY_NPC;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static Shop getShop(String name) {
        return name == null ? null : shops.get(name.toLowerCase());
    }

    public static Collection<Shop> getShops() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public static int getItemsPerTier(ShopTier tier) {
        return itemsPerTier.getOrDefault(tier, 12);
    }

    /** Epoch ms of the next automatic rotation. */
    public static long getNextResetMs() {
        return lastResetMs + resetIntervalHours * 3_600_000L;
    }

    // ── Tuning (shops.yml) ────────────────────────────────────────────────────

    /** Per-job default pools ({@code shops.yml > job_defaults}), used to seed new shops. */
    private static ConfigurationSection jobDefaults;

    /**
     * Loads tuning and the per-job default pools from {@code shops.yml}. The shops
     * themselves come from storage ({@link #restore}).
     */
    public static void loadConfig(FileConfiguration config) {
        resetIntervalHours = Math.max(1L, config.getLong("reset_interval_hours", 168L));
        for (ShopTier tier : ShopTier.values()) {
            int n = config.getInt("items_per_shop." + tier.key(), 12);
            itemsPerTier.put(tier, Math.clamp(n, 1, MAX_ITEMS_PER_TIER));
        }
        jobDefaults = config.getConfigurationSection("job_defaults");
    }

    private static Shop parseShop(String name, ConfigurationSection s) {
        Job job;
        try {
            job = Job.valueOf(s.getString("job", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            Atlas.instance.getLogger().warning("shops.yml: shop '" + name + "' has an invalid job — skipped.");
            return null;
        }
        Shop shop = new Shop(name, job);

        ConfigurationSection npc = s.getConfigurationSection("npc");
        if (npc != null) {
            World world = Bukkit.getWorld(npc.getString("world", ""));
            if (world != null) {
                shop.setNpcLocation(new Location(world,
                        npc.getDouble("x"), npc.getDouble("y"), npc.getDouble("z"),
                        (float) npc.getDouble("yaw"), (float) npc.getDouble("pitch")));
            }
            String uuid = npc.getString("uuid");
            if (uuid != null) {
                try { shop.setNpcUuid(UUID.fromString(uuid)); } catch (IllegalArgumentException ignored) {}
            }
        }

        for (ShopTier tier : ShopTier.values()) {
            parsePool(name, s.getConfigurationSection(tier.key()), shop.getPool(tier));
        }
        return shop;
    }

    private static void parsePool(String shopName, ConfigurationSection pool, Map<String, ShopItem> into) {
        if (pool == null) return;
        for (String id : pool.getKeys(false)) {
            ConfigurationSection entry = pool.getConfigurationSection(id);
            if (entry == null) continue;
            ShopItem item = parseItem(shopName, id, entry);
            if (item != null) into.put(id, item);
        }
    }

    /**
     * Parses one pool entry. Either {@code item:} (a serialized stack) or {@code material:}
     * plus optional {@code amount}, {@code potion} and {@code enchantments} (hand-written).
     */
    private static ShopItem parseItem(String shopName, String id, ConfigurationSection s) {
        ItemStack stack = s.getItemStack("item");
        if (stack == null) {
            Material mat = Material.matchMaterial(s.getString("material", ""));
            if (mat == null || !mat.isItem() || mat.isAir()) {
                Atlas.instance.getLogger().warning("shops.yml: shop '" + shopName + "' entry '" + id
                        + "' has an invalid material — skipped.");
                return null;
            }
            stack = new ItemStack(mat, Math.clamp(s.getInt("amount", 1), 1, mat.getMaxStackSize()));
            ItemMeta meta = stack.getItemMeta();

            String potion = s.getString("potion");
            if (potion != null && meta instanceof PotionMeta potionMeta) {
                NamespacedKey key = NamespacedKey.fromString(potion.toLowerCase());
                PotionType type = key == null ? null
                        : RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION).get(key);
                if (type != null) potionMeta.setBasePotionType(type);
            }

            ConfigurationSection enchants = s.getConfigurationSection("enchantments");
            if (enchants != null && meta != null) {
                for (String encName : enchants.getKeys(false)) {
                    NamespacedKey key = NamespacedKey.fromString(encName.toLowerCase());
                    Enchantment enc = key == null ? null
                            : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
                    if (enc == null) continue;
                    int level = Math.max(1, enchants.getInt(encName, 1));
                    if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(enc, level, true);
                    else meta.addEnchant(enc, level, true);
                }
            }
            stack.setItemMeta(meta);
        }

        String name = s.getString("name", GuiUtil.formatMaterial(stack.getType().name()));
        int price  = Math.max(0, s.getInt("price", 1));
        int weight = Math.max(0, s.getInt("weight", 1));
        return new ShopItem(id, name, stack, price, weight);
    }

    // ── Storage (domain SHOPS) ────────────────────────────────────────────────

    /** Shops, pools, NPCs, rotation and the reset timer, for storage. Main thread only. */
    public static ShopsData snapshot() {
        List<ShopsData.ShopRow> rows = new ArrayList<>();
        Map<String, Map<String, List<String>>> rotation = new LinkedHashMap<>();
        for (Shop shop : shops.values()) {
            List<ShopsData.Item> items = new ArrayList<>();
            Map<String, List<String>> tiers = new LinkedHashMap<>();
            for (ShopTier tier : ShopTier.values()) {
                for (ShopItem it : shop.getPool(tier).values()) {
                    byte[] bytes = ItemBytes.fromItem(it.item());
                    if (bytes != null) {
                        items.add(new ShopsData.Item(tier.key(), it.id(), it.name(), bytes, it.price(), it.weight()));
                    }
                }
                tiers.put(tier.key(), List.copyOf(shop.getCurrentIds(tier)));
            }
            rows.add(new ShopsData.ShopRow(shop.getName(), shop.getJob().name(),
                    LocationData.of(shop.getNpcLocation()), shop.getNpcUuid(), List.copyOf(items)));
            rotation.put(shop.getName(), tiers);
        }
        return new ShopsData(List.copyOf(rows), rotation, lastResetMs);
    }

    /**
     * Rebuilds shops from storage, imports the shops still hand-written in
     * {@code shops.yml > shops} (old format) and restores the rotation. Call after
     * {@link #loadConfig}.
     *
     * @return true if shops were imported from shops.yml (the caller then saves SHOPS
     *         and removes the {@code shops} section from shops.yml)
     */
    public static boolean restore(ShopsData data, FileConfiguration config) {
        shops.clear();
        for (ShopsData.ShopRow row : data.shops()) {
            Job job;
            try {
                job = Job.valueOf(row.job());
            } catch (IllegalArgumentException e) {
                Atlas.instance.getLogger().warning("Shop '" + row.name() + "' has an invalid job — skipped.");
                continue;
            }
            Shop shop = new Shop(row.name(), job);
            if (row.npc() != null) shop.setNpcLocation(row.npc().toLocation());
            shop.setNpcUuid(row.npcUuid());
            for (ShopsData.Item it : row.items()) {
                ShopTier tier = ShopTier.fromKey(it.tier());
                ItemStack stack = ItemBytes.toItem(it.item());
                if (tier == null || stack == null) continue;
                shop.getPool(tier).put(it.id(), new ShopItem(it.id(), it.name(), stack, it.price(), it.weight()));
            }
            shops.put(shop.getName(), shop);
        }

        boolean imported = false;
        ConfigurationSection legacy = config.getConfigurationSection("shops");
        if (legacy != null) {
            for (String name : legacy.getKeys(false)) {
                String key = name.toLowerCase();
                ConfigurationSection s = legacy.getConfigurationSection(name);
                if (s == null || shops.containsKey(key)) continue;
                Shop shop = parseShop(key, s);
                if (shop == null) continue;
                shops.put(key, shop);
                imported = true;
                Atlas.instance.getLogger().info("Shop '" + key + "' imported from shops.yml into storage.");
            }
        }

        lastResetMs = data.lastReset();
        for (Shop shop : shops.values()) {
            Map<String, List<String>> saved = data.rotation().getOrDefault(shop.getName(), Map.of());
            for (ShopTier tier : ShopTier.values()) {
                List<String> ids = shop.getCurrentIds(tier);
                ids.clear();
                for (String id : saved.getOrDefault(tier.key(), List.of())) {
                    if (shop.getPool(tier).containsKey(id)) ids.add(id);
                }
                // Selection lost (new shop, or its entries were removed) — refill now.
                if (ids.isEmpty()) roll(shop, tier);
            }
        }
        return imported;
    }

    private static void persistData() {
        Storage.save(DataDomain.SHOPS);
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    /** Checks once a minute whether the reset interval has elapsed. */
    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= getNextResetMs()) resetAll();
        }, 20L, 20L * 60L);
    }

    /** Re-rolls every shop's common and rare selection and restarts the reset timer. */
    public static void resetAll() {
        for (Shop shop : shops.values()) {
            for (ShopTier tier : ShopTier.values()) roll(shop, tier);
        }
        lastResetMs = System.currentTimeMillis();
        persistData();
    }

    /** Re-rolls a single shop without touching the global reset timer. */
    public static void resetShop(Shop shop) {
        for (ShopTier tier : ShopTier.values()) roll(shop, tier);
        persistData();
    }

    /** Weighted draw without replacement of up to {@link #getItemsPerTier} entries. */
    private static void roll(Shop shop, ShopTier tier) {
        List<ShopItem> candidates = new ArrayList<>();
        for (ShopItem item : shop.getPool(tier).values()) {
            if (item.weight() > 0) candidates.add(item);
        }
        List<String> picked = shop.getCurrentIds(tier);
        picked.clear();
        int target = getItemsPerTier(tier);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (picked.size() < target && !candidates.isEmpty()) {
            int total = 0;
            for (ShopItem c : candidates) total += c.weight();
            int r = random.nextInt(total);
            for (int i = 0; i < candidates.size(); i++) {
                r -= candidates.get(i).weight();
                if (r < 0) {
                    picked.add(candidates.remove(i).id());
                    break;
                }
            }
        }
    }

    // ── Admin mutations ───────────────────────────────────────────────────────

    /**
     * Creates a shop for {@code job}, seeded with that job's default pools from
     * {@code shops.yml > job_defaults}, and spawns its NPC at {@code loc}. Returns an error
     * message, or null on success.
     */
    public static String createShop(String rawName, Job job, Location loc) {
        String name = rawName.toLowerCase();
        if (!name.matches("[a-z0-9_-]+")) return "Shop names may only contain letters, digits, '_' and '-'.";
        if (shops.containsKey(name)) return "A shop named '" + name + "' already exists.";
        if (job == Job.JOKEYRINI) return "Jokeyrini is not a playable job.";

        Shop shop = new Shop(name, job);
        ConfigurationSection defaults = jobDefaults == null ? null : jobDefaults.getConfigurationSection(job.name());
        for (ShopTier tier : ShopTier.values()) {
            parsePool(name, defaults == null ? null : defaults.getConfigurationSection(tier.key()), shop.getPool(tier));
        }
        shops.put(name, shop);
        spawnNpc(shop, loc);
        for (ShopTier tier : ShopTier.values()) roll(shop, tier);
        persistData();
        return null;
    }

    /** Deletes a shop and its NPC. Returns false if no such shop exists. */
    public static boolean deleteShop(String name) {
        Shop shop = shops.remove(name.toLowerCase());
        if (shop == null) return false;
        removeNpc(shop);
        persistData();
        return true;
    }

    /**
     * Adds {@code stack} to a shop pool with the given weight and price. Returns the new
     * entry id. If the tier's current selection is not full yet, the item goes on sale
     * immediately; otherwise it joins the next rotation.
     */
    public static String addItem(Shop shop, ShopTier tier, ItemStack stack, int weight, int price) {
        String base = stack.getType().name().toLowerCase();
        String id = base;
        for (int i = 2; shop.getPool(tier).containsKey(id); i++) id = base + "_" + i;

        String label = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(stack.getItemMeta().displayName())
                : GuiUtil.formatMaterial(stack.getType().name());

        shop.getPool(tier).put(id, new ShopItem(id, label, stack.clone(), price, weight));
        if (weight > 0 && shop.getCurrentIds(tier).size() < getItemsPerTier(tier)) {
            shop.getCurrentIds(tier).add(id);
        }
        persistData();
        return id;
    }

    // ── NPCs ──────────────────────────────────────────────────────────────────

    /** Spawns NPCs for shops that have a location but no NPC yet (shops imported from shops.yml). */
    public static void spawnMissingNpcs() {
        boolean changed = false;
        for (Shop shop : shops.values()) {
            if (shop.getNpcUuid() != null || shop.getNpcLocation() == null) continue;
            spawnNpc(shop, shop.getNpcLocation());
            changed = true;
        }
        if (changed) persistData();
    }

    private static void spawnNpc(Shop shop, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Job job = shop.getJob();
        Villager villager = loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setGravity(true);
            v.setSilent(true);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
            v.setVillagerType(Villager.Type.SAVANNA);
            v.setProfession(job.getProfession());
            v.customName(Component.text(displayName(shop), job.getColor())
                    .decoration(TextDecoration.ITALIC, false));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(getKeyNpc(), PersistentDataType.STRING, shop.getName());
        });
        shop.setNpcLocation(villager.getLocation());
        shop.setNpcUuid(villager.getUniqueId());
    }

    /** Removes the shop's NPC, loading its chunk's entities if needed. */
    private static void removeNpc(Shop shop) {
        Entity byUuid = shop.getNpcUuid() == null ? null : Bukkit.getEntity(shop.getNpcUuid());
        if (byUuid != null) {
            byUuid.remove();
            return;
        }
        Location loc = shop.getNpcLocation();
        if (loc == null || loc.getWorld() == null) return;
        // Chunk#getEntities waits for the chunk's entities to load.
        for (Entity e : loc.getChunk().getEntities()) {
            if (shop.getName().equals(getShopName(e))) e.remove();
        }
    }

    public static boolean isShopNpc(Entity entity) {
        return getShopName(entity) != null;
    }

    /** The shop an NPC belongs to, or null if the entity is not a shop NPC (or its shop was deleted). */
    public static Shop getShopByNpc(Entity entity) {
        return getShop(getShopName(entity));
    }

    private static String getShopName(Entity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(getKeyNpc(), PersistentDataType.STRING);
    }

    /** "miner_shop" -> "Miner Shop". */
    public static String displayName(Shop shop) {
        return GuiUtil.formatMaterial(shop.getName());
    }

    // ── Access rules ──────────────────────────────────────────────────────────

    /**
     * Returns why {@code player} may not open the given part of the shop, or null if allowed.
     * {@code tier == null} is the main menu: faction required. Common: faction + any job.
     * Rare: faction + the shop's job.
     */
    public static Component accessError(Player player, Shop shop, ShopTier tier) {
        UUID uuid = player.getUniqueId();
        if (FactionManager.getPlayerFaction(uuid) == null) {
            return Component.text("You must join a faction to use shops.", NamedTextColor.RED);
        }
        if (tier == null) return null;
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) {
            return Component.text("You need a job to buy from shops.", NamedTextColor.RED);
        }
        if (tier == ShopTier.RARE && data.getJob() != shop.getJob()) {
            return Component.text("Only ", NamedTextColor.RED)
                    .append(Component.text(shop.getJob().getDisplayName() + "s", shop.getJob().getColor()))
                    .append(Component.text(" can buy rare items here.", NamedTextColor.RED));
        }
        return null;
    }

    // ── Purchase ──────────────────────────────────────────────────────────────

    /**
     * Buys one entry, paying rubies from the faction vault (any member with access to the
     * tier may spend it). Re-checks access and that
     * the entry is still on sale (a rotation may have happened while the GUI was open).
     * Returns true on success; failures are reported in chat.
     */
    public static boolean purchase(Player player, Shop shop, ShopTier tier, String itemId) {
        if (getShop(shop.getName()) != shop) {
            player.sendMessage(Component.text("This shop no longer exists.", NamedTextColor.RED));
            return false;
        }
        Component denied = accessError(player, shop, tier);
        if (denied != null) {
            player.sendMessage(denied);
            return false;
        }
        ShopItem item = shop.getPool(tier).get(itemId);
        if (item == null || !shop.getCurrentIds(tier).contains(itemId)) {
            player.sendMessage(Component.text("This item is no longer on sale.", NamedTextColor.RED));
            return false;
        }
        Faction faction = FactionManager.getFaction(FactionManager.getPlayerFaction(player.getUniqueId()));
        int balance = faction == null ? 0 : FactionVault.countGems(faction);
        if (faction == null || balance < item.price() || !FactionVault.withdraw(faction, item.price())) {
            player.sendMessage(Component.text("Your faction vault has " + balance + " rubies — need "
                    + item.price() + ".", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        for (ItemStack overflow : player.getInventory().addItem(item.createStack()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        player.sendMessage(Component.text("Bought ", NamedTextColor.GREEN)
                .append(Component.text(item.name(), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Component.text(item.price() + " ruby", NamedTextColor.RED))
                .append(Component.text(" from the faction vault.", NamedTextColor.GREEN)));
        return true;
    }

    /**
     * Ruby gems in the vault of the player's faction (shops are paid from the faction vault,
     * like the auction house). 0 when the player has no faction.
     */
    public static int vaultBalance(Player player) {
        Faction faction = FactionManager.getFaction(FactionManager.getPlayerFaction(player.getUniqueId()));
        return faction == null ? 0 : FactionVault.countGems(faction);
    }
}
