package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.UUID;

/** Domain FACTIONS: every faction with its members, allies, purchased tiers and vault. */
public record FactionsData(List<FactionRow> factions) {

    public static FactionsData empty() { return new FactionsData(List.of()); }

    /**
     * @param vaultSize     null = the default size from factions.yml
     * @param vaultContents Paper item bytes ({@code ItemStack.serializeItemsAsBytes}), null if empty
     * @param vaultGems     ruby gems in the vault, written for the website (read model only, never
     *                      read back); null when unknown (rows loaded from storage or YAML)
     */
    public record FactionRow(String name, UUID owner, String color, String description,
                             int level, int exp, int skillPoints, boolean outpostUnlocked,
                             Integer vaultSize, int downgradeMul, long downgradeWindowEnd,
                             List<Member> members, List<String> allies,
                             List<Integer> homeTiers, List<Integer> vaultTiers,
                             byte[] vaultContents, Integer vaultGems) {}

    /**
     * A player linked to a faction. {@code member=false} keeps a role entry for a player
     * that is not in the member list (the owner is stored on the faction row).
     *
     * @param role FactionRole name, or null when the player has no explicit role (MEMBER)
     */
    public record Member(UUID uuid, boolean member, String role) {}
}
