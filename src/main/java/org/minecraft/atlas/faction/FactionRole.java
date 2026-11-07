package org.minecraft.atlas.faction;

/**
 * Faction roles, lowest first (promote/demote walk this order). The owner is stored apart
 * and outranks every role.
 * <ul>
 *   <li>MEMBER: no management right.</li>
 *   <li>BANKER: may spend the faction vault's rubies (take gems out, shop and auction
 *       purchases) and nothing else.</li>
 *   <li>MODERATOR: banker rights + invite/kick members.</li>
 *   <li>LEADER: moderator rights + claims, homes, allies, rename, auction selling…</li>
 * </ul>
 */
public enum FactionRole {
    MEMBER,
    BANKER,
    MODERATOR,
    LEADER;

    public boolean isAtLeast(FactionRole required) {
        return this.ordinal() >= required.ordinal();
    }

    public String displayName() {
        return switch (this) {
            case MEMBER -> "Member";
            case BANKER -> "Banker";
            case MODERATOR -> "Moderator";
            case LEADER -> "Leader";
        };
    }
}
