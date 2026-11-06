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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.Item.RubyItem;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.job.PlayerJobData;
import org.minecraft.atlas.job.JobManager;
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
 * <p>{@code shops.yml} is the source of truth for tuning, per-job default pools and the
 * shops themselves (job, NPC location, pools). Admin commands edit that file's sections
 * in place, so hand-written entries keep their format. The current rotation and the last
 * reset time are runtime state saved to {@code shops-data.yml}, so the reset timer
 * survives restarts.
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

    // ── Loading (shops.yml) ───────────────────────────────────────────────────

    /** Loads settings and shops from {@code shops.yml}. Call before {@link #loadData}. */
    public static void loadConfig(FileConfiguration config) {
        resetIntervalHours = Math.max(1L, config.getLong("reset_interval_hours", 168L));
        for (ShopTier tier : ShopTier.values()) {
            int n = config.getInt("items_per_shop." + tier.key(), 12);
            itemsPerTier.put(tier, Math.clamp(n, 1, MAX_ITEMS_PER_TIER));
        }

        shops.clear();
        ConfigurationSection root = config.getConfigurationSection("shops");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            Shop shop = parseShop(name.toLowerCase(), s);
            if (shop != null) shops.put(shop.getName(), shop);
        }
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
            ConfigurationSection pool = s.getConfigurationSection(tier.key());
            if (pool == null) continue;
            for (String id : pool.getKeys(false)) {
                ConfigurationSection entry = pool.getConfigurationSection(id);
                if (entry == null) continue;
                ShopItem item = parseItem(name, id, entry);
                if (item != null) shop.getPool(tier).put(id, item);
            }
        }
        return shop;
    }

    /**
     * Parses one pool entry. Either {@code item:} (a serialized stack, written by
     * {@code /shop additem}) or {@code material:} plus optional {@code amount},
     * {@code potion} and {@code enchantments} (hand-written).
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

    // ── Runtime state (shops-data.yml) ────────────────────────────────────────

    public static void loadData(FileConfiguration data) {
        lastResetMs = data.getLong("last_reset", 0L);
        ConfigurationSection root = data.getConfigurationSection("shops");
        for (Shop shop : shops.values()) {
            for (ShopTier tier : ShopTier.values()) {
                List<String> ids = shop.getCurrentIds(tier);
                ids.clear();
                if (root != null) {
                    for (String id : root.getStringList(shop.getName() + "." + tier.key())) {
                        if (shop.getPool(tier).containsKey(id)) ids.add(id);
                    }
                }
                // Selection lost (new shop, or its entries were removed from shops.yml) — refill now.
                if (ids.isEmpty()) roll(shop, tier);
            }
        }
    }

    public static void saveData(FileConfiguration data) {
        data.set("last_reset", lastResetMs);
        data.set("shops", null);
        for (Shop shop : shops.values()) {
            for (ShopTier tier : ShopTier.values()) {
                data.set("shops." + shop.getName() + "." + tier.key(), new ArrayList<>(shop.getCurrentIds(tier)));
            }
        }
    }

    private static void persistData() {
        saveData(Atlas.shopsDataConfig);
        Atlas.saveShopsDataConfig();
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

    // ── Admin mutations (edit shops.yml in place) ─────────────────────────────

    /**
     * Creates a shop for {@code job}, seeded with that job's default pools from
     * {@code job_defaults}, and spawns its NPC at {@code loc}. Returns an error message,
     * or null on success.
     */
    public static String createShop(String rawName, Job job, Location loc) {
        String name = rawName.toLowerCase();
        // Names become YAML path segments — keep them to a safe charset (no dots).
        if (!name.matches("[a-z0-9_-]+")) return "Shop names may only contain letters, digits, '_' and '-'.";
        if (shops.containsKey(name)) return "A shop named '" + name + "' already exists.";
        if (job == Job.JOKEYRINI) return "Jokeyrini is not a playable job.";

        FileConfiguration config = Atlas.shopsConfig;
        ConfigurationSection s = config.createSection("shops." + name);
        s.set("job", job.name());
        ConfigurationSection defaults = config.getConfigurationSection("job_defaults." + job.name());
        for (ShopTier tier : ShopTier.values()) {
            ConfigurationSection pool = defaults == null ? null : defaults.getConfigurationSection(tier.key());
            ConfigurationSection target = s.createSection(tier.key());
            if (pool != null) copySection(pool, target);
        }

        Shop shop = parseShop(name, s);
        if (shop == null) {
            config.set("shops." + name, null);
            return "Could not create the shop.";
        }
        shops.put(name, shop);
        spawnNpc(shop, loc);
        for (ShopTier tier : ShopTier.values()) roll(shop, tier);
        saveConfig();
        persistData();
        return null;
    }

    /** Deletes a shop and its NPC. Returns false if no such shop exists. */
    public static boolean deleteShop(String name) {
        Shop shop = shops.remove(name.toLowerCase());
        if (shop == null) return false;
        removeNpc(shop);
        Atlas.shopsConfig.set("shops." + shop.getName(), null);
        saveConfig();
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

        ConfigurationSection s = Atlas.shopsConfig.createSection(
                "shops." + shop.getName() + "." + tier.key() + "." + id);
        s.set("name", label);
        s.set("item", stack.clone());
        s.set("price", price);
        s.set("weight", weight);
        saveConfig();

        shop.getPool(tier).put(id, new ShopItem(id, label, stack.clone(), price, weight));
        if (weight > 0 && shop.getCurrentIds(tier).size() < getItemsPerTier(tier)) {
            shop.getCurrentIds(tier).add(id);
            persistData();
        }
        return id;
    }

    private static void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection sub) copySection(sub, to.createSection(key));
            else to.set(key, value);
        }
    }

    private static void saveConfig() {
        try {
            Atlas.shopsConfig.save(Atlas.shopsFile);
        } catch (java.io.IOException e) {
            Atlas.instance.getLogger().severe("Could not save shops.yml: " + e.getMessage());
        }
    }

    // ── NPCs ──────────────────────────────────────────────────────────────────

    /** Spawns NPCs for shops that have a location in shops.yml but no NPC yet (hand-added shops). */
    public static void spawnMissingNpcs() {
        boolean changed = false;
        for (Shop shop : shops.values()) {
            if (shop.getNpcUuid() != null || shop.getNpcLocation() == null) continue;
            spawnNpc(shop, shop.getNpcLocation());
            changed = true;
        }
        if (changed) saveConfig();
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

        ConfigurationSection npc = Atlas.shopsConfig.createSection("shops." + shop.getName() + ".npc");
        Location l = villager.getLocation();
        npc.set("world", l.getWorld().getName());
        npc.set("x", l.getX());
        npc.set("y", l.getY());
        npc.set("z", l.getZ());
        npc.set("yaw", l.getYaw());
        npc.set("pitch", l.getPitch());
        npc.set("uuid", villager.getUniqueId().toString());
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
     * Buys one entry, paying rubies from the player's inventory. Re-checks access and that
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
        int balance = countRubies(player);
        if (balance < item.price()) {
            player.sendMessage(Component.text("You have " + balance + " rubies — need "
                    + item.price() + ".", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        takeRubies(player, item.price());
        for (ItemStack overflow : player.getInventory().addItem(item.createStack()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        player.sendMessage(Component.text("Bought ", NamedTextColor.GREEN)
                .append(Component.text(item.name(), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Component.text(item.price() + " ruby", NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.GREEN)));
        return true;
    }

    /** Ruby gems in the player's inventory (storage slots + offhand). */
    public static int countRubies(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (RubyItem.isRubyGem(s)) total += s.getAmount();
        }
        return total;
    }

    private static void takeRubies(Player player, int count) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        int remaining = count;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (!RubyItem.isRubyGem(s)) continue;
            int take = Math.min(s.getAmount(), remaining);
            s.setAmount(s.getAmount() - take);
            inv.setItem(i, s.getAmount() <= 0 ? null : s);
            remaining -= take;
        }
    }
}
