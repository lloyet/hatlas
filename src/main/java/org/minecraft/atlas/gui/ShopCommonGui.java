package org.minecraft.atlas.gui;

import org.bukkit.entity.Player;
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopTier;

/** Common items of a shop — open to any faction member who has a job. */
public class ShopCommonGui extends ShopItemsGui {

    private final Shop shop;

    public ShopCommonGui(Player player, Shop shop) {
        super(player, shop, ShopTier.COMMON);
        this.shop = shop;
    }

    @Override
    protected ShopItemsGui reopen(Player player) {
        return new ShopCommonGui(player, shop);
    }
}
