package org.minecraft.atlas.gui;

import org.bukkit.entity.Player;
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopTier;

/** Rare items of a shop — open only to faction members whose job matches the shop's. */
public class ShopRareGui extends ShopItemsGui {

    private final Shop shop;

    public ShopRareGui(Player player, Shop shop) {
        super(player, shop, ShopTier.RARE);
        this.shop = shop;
    }

    @Override
    protected ShopItemsGui reopen(Player player) {
        return new ShopRareGui(player, shop);
    }
}
