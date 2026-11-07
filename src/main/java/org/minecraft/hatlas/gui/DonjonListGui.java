package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.donjon.Donjon;
import org.minecraft.hatlas.donjon.DonjonManager;
import org.minecraft.hatlas.donjon.DonjonStatus;
import org.minecraft.hatlas.donjon.SmugglerManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DonjonListGui implements HatlasGui {

    /** Center of row 1 — shows the next activation timer. */
    private static final int TIMER_SLOT = 4;
    /** Donjon items start on row 3. */
    private static final int DONJON_START_SLOT = 18;

    private final Inventory inventory;
    /** Maps inventory slot → donjon ID for click handling. */
    private final Map<Integer, String> slotToDonjonId = new HashMap<>();

    public DonjonListGui(Player player) {
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("⚓ Smuggler's Chart", NamedTextColor.AQUA));

        UUID playerUUID = player.getUniqueId();
        List<Donjon> donjons = new ArrayList<>(DonjonManager.getDonjons().values());

        // Donjon items fill rows 3–6 (slots 18–53)
        int currentSlot = DONJON_START_SLOT;
        for (Donjon donjon : donjons) {
            if (currentSlot >= 54) break;
            boolean visited = DonjonManager.hasPlayerVisitedDonjon(playerUUID, donjon.getId());
            this.inventory.setItem(currentSlot, buildDonjonItem(donjon, visited));
            slotToDonjonId.put(currentSlot, donjon.getId());
            currentSlot++;
        }

        // Timer item on row 1 (center)
        this.inventory.setItem(TIMER_SLOT, buildTimerItem());

        GuiUtil.fillGray(this.inventory);
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        String donjonId = slotToDonjonId.get(slot);
        if (donjonId == null) return;

        Donjon donjon = DonjonManager.getDonjon(donjonId);
        if (donjon == null) return;

        UUID uuid = player.getUniqueId();

        if (!DonjonManager.hasPlayerVisitedDonjon(uuid, donjonId)) {
            player.sendMessage(Component.text(
                            "You must visit ", NamedTextColor.RED)
                    .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                    .append(Component.text(" first before using the teleporter!", NamedTextColor.RED)));
            return;
        }

        if (!player.isOp() && SmugglerManager.isOnCooldown(uuid)) {
            long remaining = (SmugglerManager.getRemainingCooldown(uuid) + 999) / 1000;
            player.sendMessage(Component.text(
                    "The smuggler's map is on cooldown. " + remaining + "s remaining.",
                    NamedTextColor.RED));
            return;
        }

        Location dest = donjon.getTeleportSpawn();
        if (dest == null) {
            player.sendMessage(Component.text(
                    "The smuggler has lost track of that destination.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.teleport(dest);
        if (!player.isOp()) SmugglerManager.startCooldown(uuid);
        player.sendMessage(Component.text("The smuggler has sent you to ", NamedTextColor.AQUA)
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.text("!", NamedTextColor.AQUA)));
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildDonjonItem(Donjon donjon, boolean visited) {
        boolean active = donjon.getStatus() == DonjonStatus.ACTIVE;
        Material mat = active ? Material.VAULT : Material.TRIAL_SPAWNER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(donjon.getName(), donjon.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Type", DonjonManager.getTypeDisplayName(donjon.getType()), NamedTextColor.WHITE));
        lore.add(GuiUtil.loreLine("Level", String.valueOf(donjon.getLevel()), NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Rarity", donjon.getRarity().getDisplayName(), donjon.getRarity().getColor()));
        lore.add(GuiUtil.loreLine("Status",
                active ? "Active" : "Idle",
                active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (visited) {
            lore.add(Component.text("  ✦ Click to teleport", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  ⚠ You must visit this dungeon first", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  to unlock teleportation.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildTimerItem() {
        long secs = DonjonManager.getSecondsUntilNextActivation();
        String timeStr = formatSeconds(secs);

        ItemStack item = new ItemStack(Material.TRIAL_KEY);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⏱ Next Activation", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (secs == 0) {
            lore.add(Component.text("  ⚡ A donjon will activate soon!", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(GuiUtil.loreLine("  Time remaining", timeStr, NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Donjons auto-activate periodically.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String formatSeconds(long secs) {
        if (secs >= 3600) return (secs / 3600) + "h " + ((secs % 3600) / 60) + "m";
        if (secs >= 60)   return (secs / 60) + "m " + (secs % 60) + "s";
        return secs + "s";
    }
}
