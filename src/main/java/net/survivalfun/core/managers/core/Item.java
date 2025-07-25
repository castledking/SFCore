package net.survivalfun.core.managers.core;

import net.survivalfun.core.PluginStart;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class Item {

    private static final List<Material> giveableItems = new ArrayList<>();
    private static PluginStart plugin;

    public static void initialize(PluginStart pluginInstance) {
        plugin = pluginInstance;
        // Clear the list in case this is a reload
        giveableItems.clear();
        
        // Iterate through all Material enum values
        for (Material material : Material.values()) {
            // Skip legacy materials as they're handled separately
            if (material.isLegacy() || material.name().startsWith("LEGACY_")) {
                continue;
            }
            
            // Skip air and other special materials
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                continue;
            }
            
            // Try to create an ItemStack
            try {
                new ItemStack(material, 1);
                // If successful, add to the cache
                giveableItems.add(material);
            } catch (IllegalArgumentException e) {
            }
        }
        
        if (plugin != null) {
            if (plugin.getConfigManager().getBoolean("debug-mode")) {
                plugin.getLogger().info("Initialized " + giveableItems.size() + " giveable items");
            }
        }
    }

    public static List<Material> getGiveableItems() {
        return giveableItems;
    }

    public static boolean isGiveable(Material material) {
        // First check if the material is in our giveable items list
        if (giveableItems.contains(material)) {
            return true;
        }
        
        // If not in the list, do a more lenient check
        // Only block items that are definitely not giveable
        return material != null && 
               material != Material.AIR && 
               !material.isLegacy() &&
               !material.name().startsWith("LEGACY_");
    }
}