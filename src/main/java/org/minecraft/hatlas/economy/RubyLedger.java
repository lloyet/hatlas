package org.minecraft.hatlas.economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.minecraft.hatlas.Item.RubyItem;
import org.minecraft.hatlas.block.RubyBlockManager;
import org.minecraft.hatlas.storage.Storage;
import org.minecraft.hatlas.storage.model.LedgerRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The ruby ledger: an append-only record of every ruby move (table {@code ruby_ledger}, see
 * {@code docs/economy.md}). Rows are buffered and appended by the storage writer every second
 * and on shutdown.
 *
 * <p><b>Dropped rubies</b> keep their owner: every ruby item entity Hatlas or a player drops is
 * tagged with the party it belongs to and why it was dropped. A pickup by another party writes
 * a {@link LedgerKind#PICKUP} row (owner → picker), a disappearance a {@link LedgerKind#LOST}
 * row. Stacks of different owners never merge.
 */
public final class RubyLedger {

    private static final int FLUSH_TICKS = 20;

    private static final ConcurrentLinkedDeque<LedgerRow> pending = new ConcurrentLinkedDeque<>();
    private static NamespacedKey ownerTypeKey, ownerIdKey, ownerNameKey, originKey;

    private RubyLedger() {}

    public static void init(Plugin plugin) {
        ownerTypeKey = new NamespacedKey(plugin, "ledger_owner_type");
        ownerIdKey = new NamespacedKey(plugin, "ledger_owner_id");
        ownerNameKey = new NamespacedKey(plugin, "ledger_owner_name");
        originKey = new NamespacedKey(plugin, "ledger_origin");
    }

    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, RubyLedger::flush, FLUSH_TICKS, FLUSH_TICKS);
    }

    /** Hands the buffered rows to the storage writer (call before {@code Storage.shutdown()}). */
    public static void flush() {
        if (pending.isEmpty()) return;
        List<LedgerRow> batch = new ArrayList<>();
        LedgerRow r;
        while ((r = pending.pollFirst()) != null) batch.add(r);
        Storage.appendLedger(batch, failed -> {
            for (int i = failed.size() - 1; i >= 0; i--) pending.addFirst(failed.get(i));
        });
    }

    // ── Values ───────────────────────────────────────────────────────────────

    /** Ruby value of a stack in gems: gem = 1, ruby block = 9, anything else (ores too) = 0. */
    public static int gemValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (RubyItem.isRubyGem(stack)) return stack.getAmount();
        if ("ruby_block".equals(RubyBlockManager.customBlockIdForItem(stack))) return stack.getAmount() * 9;
        return 0;
    }

    /** Ruby value (gems + blocks) in a player's inventory plus the cursor. */
    public static int rubyValueHeld(Player player) {
        int total = gemValue(player.getItemOnCursor());
        for (ItemStack s : player.getInventory().getContents()) total += gemValue(s);
        return total;
    }

    /** Gems (only gems, as the vault holds) in a player's inventory plus the cursor. */
    public static int gemsHeld(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && RubyItem.isRubyGem(s)) total += s.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (RubyItem.isRubyGem(cursor)) total += cursor.getAmount();
        return total;
    }

    // ── Recording ────────────────────────────────────────────────────────────

    /**
     * Records a move. {@code sender} null = created, {@code receiver} null = destroyed;
     * {@code actor} = the player who caused it (may be null), {@code where} may be null.
     */
    public static void record(LedgerKind kind, int amount, Party sender, Party receiver,
                              Player actor, String ref, Location where) {
        if (amount <= 0 || (sender == null && receiver == null)) return;
        if (sender != null && sender.sameAs(receiver)) return;
        pending.addLast(new LedgerRow(System.currentTimeMillis(), kind.name(), amount,
                type(sender), sender == null ? null : sender.id(), sender == null ? null : sender.name(),
                sender == null ? null : sender.faction(),
                type(receiver), receiver == null ? null : receiver.id(), receiver == null ? null : receiver.name(),
                receiver == null ? null : receiver.faction(),
                actor == null ? null : actor.getUniqueId().toString(), actor == null ? null : actor.getName(),
                ref,
                where == null || where.getWorld() == null ? null : where.getWorld().getName(),
                where == null ? null : where.getBlockX(), where == null ? null : where.getBlockY(),
                where == null ? null : where.getBlockZ()));
    }

    private static String type(Party p) {
        return p == null ? null : p.type().name();
    }

    // ── Dropped rubies ───────────────────────────────────────────────────────

    /**
     * Drops {@code stack} like {@code dropItemNaturally}; a ruby stack is tagged with its owner
     * and origin (e.g. {@code PROTECTION_BREAK}) so its pickup or loss is recorded.
     */
    public static void drop(Location loc, ItemStack stack, Party owner, String origin) {
        if (loc == null || loc.getWorld() == null || stack == null || stack.isEmpty()) return;
        if (gemValue(stack) <= 0 || owner == null) {
            loc.getWorld().dropItemNaturally(loc, stack);
            return;
        }
        loc.getWorld().dropItemNaturally(loc, stack, item -> tag(item, owner, origin));
    }

    /** Tags an existing item entity (e.g. thrown by a player). Non-ruby entities are left alone. */
    public static void tag(Item item, Party owner, String origin) {
        if (gemValue(item.getItemStack()) <= 0) return;
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        pdc.set(ownerTypeKey, PersistentDataType.STRING, owner.type().name());
        pdc.set(ownerIdKey, PersistentDataType.STRING, owner.id());
        pdc.set(ownerNameKey, PersistentDataType.STRING, owner.name() == null ? "" : owner.name());
        pdc.set(originKey, PersistentDataType.STRING, origin);
    }

    /** Owner of a dropped ruby entity, or null if it was never tagged. */
    public static Party ownerOf(Item item) {
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        String type = pdc.get(ownerTypeKey, PersistentDataType.STRING);
        String id = pdc.get(ownerIdKey, PersistentDataType.STRING);
        if (type == null || id == null) return null;
        String name = pdc.get(ownerNameKey, PersistentDataType.STRING);
        Party.Type t;
        try {
            t = Party.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Party(t, id, name == null || name.isEmpty() ? id : name, null).refreshed();
    }

    public static String originOf(Item item) {
        return item.getPersistentDataContainer().get(originKey, PersistentDataType.STRING);
    }
}
