package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.gui.ShopMainGui;
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopManager;

public class ShopNpcListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!ShopManager.isShopNpc(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Shop shop = ShopManager.getShopByNpc(event.getRightClicked());
        if (shop == null) {
            player.sendMessage(Component.text("This shop is closed.", NamedTextColor.RED));
            return;
        }
        Component denied = ShopManager.accessError(player, shop, null);
        if (denied != null) {
            player.sendMessage(denied);
            return;
        }
        new ShopMainGui(player, shop).open(player);
    }

    // Covers every damage source (players, mobs, fire, fall, /damage …), not just entity attacks.
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (ShopManager.isShopNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // Lightning (villager → witch) and zombie infection bypass invulnerability.
    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (ShopManager.isShopNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
