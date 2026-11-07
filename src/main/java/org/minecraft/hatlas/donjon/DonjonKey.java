package org.minecraft.hatlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class DonjonKey {

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.TRIAL_KEY, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Donjon Trial Key", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
            Component.empty(),
            Component.text("  Right-click the Vault in an ACTIVE donjon", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
            Component.text("  to begin your faction's trial.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("  Obtained from Jokeyrini.", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(
                DonjonManager.keyDonjonMarker, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
