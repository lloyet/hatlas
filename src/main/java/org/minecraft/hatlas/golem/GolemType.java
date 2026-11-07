package org.minecraft.hatlas.golem;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum GolemType {

    IRON           ("Iron",            Material.IRON_BLOCK,       100.00, 15.0,  1.00, NamedTextColor.WHITE),
    SOULSAND       ("Soul Sand",       Material.SOUL_SAND,         33.33,  7.5,  1.00, NamedTextColor.DARK_GRAY),
    AMETHYST       ("Amethyst",        Material.AMETHYST_BLOCK,    50.00,  1.0,  1.00, NamedTextColor.LIGHT_PURPLE), // damage handled by event (set to 0)
    GOLD           ("Gold",            Material.GOLD_BLOCK,        20.00, 19.5,  1.00, NamedTextColor.GOLD),
    EMERALD        ("Emerald",         Material.EMERALD_BLOCK,    100.00, 15.0,  1.00, NamedTextColor.GREEN),
    CRYING_OBSIDIAN("Crying Obsidian", Material.CRYING_OBSIDIAN,  200.00,  7.5,  0.75, NamedTextColor.DARK_PURPLE);

    private final String         displayName;
    private final Material       material;
    private final double         maxHealth;
    private final double         attackDamage;
    private final double         speedMultiplier;
    private final NamedTextColor color;

    GolemType(String displayName, Material material, double maxHealth, double attackDamage,
              double speedMultiplier, NamedTextColor color) {
        this.displayName     = displayName;
        this.material        = material;
        this.maxHealth       = maxHealth;
        this.attackDamage    = attackDamage;
        this.speedMultiplier = speedMultiplier;
        this.color           = color;
    }

    public String         getDisplayName()     { return displayName; }
    public Material       getMaterial()        { return material; }
    public double         getMaxHealth()       { return maxHealth; }
    public double         getAttackDamage()    { return attackDamage; }
    public double         getSpeedMultiplier() { return speedMultiplier; }
    public NamedTextColor getColor()           { return color; }

    /** Returns the GolemType whose body material matches, or null if none. */
    public static GolemType fromMaterial(Material material) {
        for (GolemType type : values()) {
            if (type.material == material) return type;
        }

        return null;
    }
}

