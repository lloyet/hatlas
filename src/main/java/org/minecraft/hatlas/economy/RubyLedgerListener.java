package org.minecraft.hatlas.economy;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Follows rubies on the ground for the {@link RubyLedger}: tags what players throw or drop on
 * death, records pickups by another party and losses, and keeps stacks of different owners apart.
 */
public final class RubyLedgerListener implements Listener {

    /** Removal causes that destroy the rubies (the others keep them in the world or in an inventory). */
    private static final Set<EntityRemoveEvent.Cause> LOSS = EnumSet.of(
            EntityRemoveEvent.Cause.DESPAWN, EntityRemoveEvent.Cause.DEATH, EntityRemoveEvent.Cause.PLUGIN,
            EntityRemoveEvent.Cause.DISCARD, EntityRemoveEvent.Cause.OUT_OF_WORLD, EntityRemoveEvent.Cause.EXPLODE,
            EntityRemoveEvent.Cause.HIT);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(PlayerDropItemEvent event) {
        RubyLedger.tag(event.getItemDrop(), Party.player(event.getPlayer()), "DROP");
    }

    /** Ruby drops are spawned by Hatlas so they carry the dead player as owner. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        List<ItemStack> rubies = new ArrayList<>();
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (RubyLedger.gemValue(s) > 0) {
                rubies.add(s);
                it.remove();
            }
        }
        Party owner = Party.player(dead);
        for (ItemStack s : rubies) RubyLedger.drop(dead.getLocation(), s, owner, "DEATH");
    }

    /** Mobs (allays, foxes, zombies…) never pick up rubies: they would carry them off the books. */
    @EventHandler(ignoreCancelled = true)
    public void onMobPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player) && RubyLedger.gemValue(event.getItem().getItemStack()) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) return;
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        int value = RubyLedger.gemValue(stack);
        if (value <= 0) return;
        int picked = stack.getAmount() - event.getRemaining();
        if (picked <= 0) return;
        int amount = value / stack.getAmount() * picked;

        Party owner = RubyLedger.ownerOf(item);
        Party receiver = Party.player(picker);
        if (owner == null) {
            RubyLedger.record(LedgerKind.UNTRACKED_PICKUP, amount, null, receiver, picker, null, item.getLocation());
        } else if (!owner.sameAs(receiver)) {
            RubyLedger.record(LedgerKind.PICKUP, amount, owner, receiver, picker, RubyLedger.originOf(item), item.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item) || !LOSS.contains(event.getCause())) return;
        int value = RubyLedger.gemValue(item.getItemStack());
        Party owner = value > 0 ? RubyLedger.ownerOf(item) : null;
        if (owner == null) return;
        RubyLedger.record(LedgerKind.LOST, value, owner, null, null, event.getCause().name(), item.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        Item a = event.getEntity(), b = event.getTarget();
        if (RubyLedger.gemValue(a.getItemStack()) <= 0) return;
        Party pa = RubyLedger.ownerOf(a), pb = RubyLedger.ownerOf(b);
        boolean same = pa == null ? pb == null : pa.sameAs(pb);
        if (!same || !Objects.equals(RubyLedger.originOf(a), RubyLedger.originOf(b))) event.setCancelled(true);
    }
}
