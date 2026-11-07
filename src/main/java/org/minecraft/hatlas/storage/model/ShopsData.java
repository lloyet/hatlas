package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain SHOPS: shops (job, NPC, item pools), the current rotation and the last reset time.
 * Tuning and the per-job default pools stay in shops.yml.
 *
 * @param rotation shop name → tier key → item ids currently on sale (in display order)
 */
public record ShopsData(List<ShopRow> shops, Map<String, Map<String, List<String>>> rotation, long lastReset) {

    public static ShopsData empty() { return new ShopsData(List.of(), Map.of(), 0L); }

    /** @param npc null if the shop has no NPC location; @param npcUuid null if no NPC spawned */
    public record ShopRow(String name, String job, LocationData npc, UUID npcUuid, List<Item> items) {}

    /**
     * @param item     Paper item bytes ({@code ItemStack.serializeAsBytes})
     * @param material item type key (e.g. {@code minecraft:diamond_pickaxe}) and {@code amount} the
     *                 stack size, written for the website (read model only, never read back);
     *                 null when unknown (rows loaded from storage or YAML)
     */
    public record Item(String tier, String id, String name, byte[] item, int price, int weight,
                       String material, Integer amount) {}
}
