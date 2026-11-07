package org.minecraft.hatlas.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.minecraft.hatlas.tag.TagManager;

public class TagListener implements Listener {

    // -------------------------------------------------------------------------
    // Entities load — restore TextDisplay tags from disk
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (TagManager.keyTagName == null) return;

        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof TextDisplay td)) continue;
            if (!td.getPersistentDataContainer().has(TagManager.keyTagName)) continue;

            TagManager.restoreDisplay(td);
        }
    }
}
