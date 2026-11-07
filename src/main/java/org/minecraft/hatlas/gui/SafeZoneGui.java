package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.safezone.SafeZone;
import org.minecraft.hatlas.safezone.SafeZoneManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SafeZoneGui implements HatlasGui {

    private final Inventory inventory;
    private final List<SafeZone> entries = new ArrayList<>();
    private final UUID viewer;

    public SafeZoneGui(Player player) {
        this.viewer = player.getUniqueId();
        for (SafeZone z : SafeZoneManager.getAll()) {
            if (z.getSpawnPoint() == null) continue;
            entries.add(z);
        }

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Explorer — Safe Zones", NamedTextColor.AQUA));

        int[] slots = GuiUtil.contentSlots54();
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            SafeZone zone = entries.get(i);
            boolean visited = SafeZoneManager.hasVisited(viewer, zone.getName());
            this.inventory.setItem(slots[i], buildZoneItem(zone, visited));
        }

        finishGui();
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (slot == inventory.getSize() - 1) {
            player.closeInventory();
            return;
        }

        int[] slots = GuiUtil.contentSlots54();
        int idx = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { idx = i; break; }
        }
        if (idx < 0 || idx >= entries.size()) return;

        SafeZone zone = entries.get(idx);
        if (!SafeZoneManager.hasVisited(viewer, zone.getName())) {
            player.sendMessage(Component.text("You must visit ", NamedTextColor.RED)
                    .append(Component.text(SafeZoneManager.capitalizedName(zone.getName()), NamedTextColor.AQUA))
                    .append(Component.text(" at least once before you can teleport there.", NamedTextColor.RED)));
            return;
        }

        Location dest = zone.getSpawnPoint();
        if (dest == null) {
            player.sendMessage(Component.text("This safe zone has no teleport point.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.teleport(dest);
        player.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(SafeZoneManager.capitalizedName(zone.getName()), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildZoneItem(SafeZone zone, boolean visited) {
        ItemStack item = new ItemStack(visited ? Material.COMPASS : Material.MAP);
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(Component.text(SafeZoneManager.capitalizedName(zone.getName()),
                        visited ? NamedTextColor.AQUA : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        String description = zone.getDescription();
        if (description != null && !description.isEmpty()) {
            lore.add(Component.empty());
            for (String line : wrap(description, 36)) {
                lore.add(Component.text("  " + line, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        if (visited) {
            lore.add(Component.text("  Click to teleport", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Undiscovered", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Visit this zone to unlock teleport", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (current.length() + 1 + word.length() > width && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
