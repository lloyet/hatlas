package org.minecraft.atlas.shop;

import net.kyori.adventure.text.format.NamedTextColor;

/** The two item pools every shop carries. {@link #key()} is the section name in {@code shops.yml}. */
public enum ShopTier {

    COMMON("common", "Common", NamedTextColor.WHITE),
    RARE  ("rare",   "Rare",   NamedTextColor.LIGHT_PURPLE);

    private final String         key;
    private final String         displayName;
    private final NamedTextColor color;

    ShopTier(String key, String displayName, NamedTextColor color) {
        this.key         = key;
        this.displayName = displayName;
        this.color       = color;
    }

    public String         key()         { return key; }
    public String         displayName() { return displayName; }
    public NamedTextColor color()       { return color; }

    /** Parses a config/command key ("common" / "rare"), case-insensitive. Returns null if unknown. */
    public static ShopTier fromKey(String key) {
        if (key == null) return null;
        for (ShopTier t : values()) {
            if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }
}
