package org.minecraft.hatlas.job;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

public enum Job {

    MINER     ("Miner",     Material.IRON_PICKAXE, NamedTextColor.GRAY,         Villager.Profession.MASON),
    LUMBERJACK("Lumberjack", Material.IRON_AXE,  NamedTextColor.GREEN,        Villager.Profession.FLETCHER),
    HUNTER    ("Hunter",    Material.BOW,          NamedTextColor.RED,          Villager.Profession.WEAPONSMITH),
    FARMER    ("Farmer",    Material.WHEAT_SEEDS,  NamedTextColor.YELLOW,       Villager.Profession.FARMER),
    ALCHEMIST ("Alchemist", Material.BLAZE_POWDER, NamedTextColor.LIGHT_PURPLE, Villager.Profession.CLERIC),
    /** Used only for Jokeyrini task templates — not a playable regular job. */
    JOKEYRINI ("Jokeyrini", Material.NETHER_STAR,  NamedTextColor.DARK_PURPLE,  Villager.Profession.NITWIT);

    private final String               displayName;
    private final Material             icon;
    private final NamedTextColor       color;
    private final Villager.Profession  profession;

    Job(String displayName, Material icon, NamedTextColor color, Villager.Profession profession) {
        this.displayName = displayName;
        this.icon        = icon;
        this.color       = color;
        this.profession  = profession;
    }

    public String              getDisplayName() { return displayName; }
    public Material            getIcon()        { return icon; }
    public NamedTextColor      getColor()       { return color; }
    public Villager.Profession getProfession()  { return profession; }
}
