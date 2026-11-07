package org.minecraft.hatlas.donjon;

import net.kyori.adventure.text.format.NamedTextColor;

public enum DonjonRarity {
    COMMON("Common",    NamedTextColor.WHITE,        1.0),
    RARE("Rare",        NamedTextColor.AQUA,         1.5),
    EPIC("Epic",        NamedTextColor.LIGHT_PURPLE, 2.5),
    LEGENDARY("Legendary", NamedTextColor.GOLD,      5.0),
    MYSTIC("Mystic",    NamedTextColor.RED,          10.0),
    GODDESS("Goddess",  NamedTextColor.YELLOW,       20.0);

    private final String displayName;
    private final NamedTextColor color;
    private final double expMultiplier;

    DonjonRarity(String displayName, NamedTextColor color, double expMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.expMultiplier = expMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public NamedTextColor getColor() { return color; }
    public double getExpMultiplier() { return expMultiplier; }
}
