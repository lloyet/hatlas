package org.minecraft.atlas.shop;

import org.bukkit.inventory.ItemStack;

/**
 * One entry of a shop's item pool.
 *
 * @param id     key of the entry inside its tier section in {@code shops.yml}
 * @param name   label shown in the shop GUI (the delivered item keeps its own name)
 * @param item   the exact stack handed to the buyer
 * @param price  cost in ruby gems, taken from the buyer's inventory
 * @param weight relative chance of being picked at each rotation
 */
public record ShopItem(String id, String name, ItemStack item, int price, int weight) {

    /** A fresh copy of the stack to deliver — never hand out the pooled instance. */
    public ItemStack createStack() {
        return item.clone();
    }
}
