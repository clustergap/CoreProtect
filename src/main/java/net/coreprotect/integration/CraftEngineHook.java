package net.coreprotect.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

public class CraftEngineHook {
    private static Boolean available = null;

    // Block detection methods
    private static Method isCustomBlockMethod;
    private static Method getCustomBlockStateMethod;
    private static Method ownerMethod;
    private static Method registeredNameMethod;

    // Display name methods
    private static Method keyOptionalMethod;
    private static Method locationMethod;
    private static Method keyAsStringMethod;
    private static Object itemManagerInstance;
    private static Method getItemDefinitionMethod;
    private static Method buildBukkitItemMethod;
    private static Method emptyContextMethod;

    public static boolean isAvailable() {
        if (available == null) {
            available = checkAvailability();
        }
        return available;
    }

    @SuppressWarnings("unchecked")
    private static boolean checkAvailability() {
        try {
            Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
            if (craftEngine == null || !craftEngine.isEnabled()) return false;

            // Block detection
            Class<?> blocksClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            isCustomBlockMethod = blocksClass.getMethod("isCustomBlock", Block.class);
            getCustomBlockStateMethod = blocksClass.getMethod("getCustomBlockState", Block.class);

            Class<?> stateClass = Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState");
            ownerMethod = stateClass.getMethod("owner");

            Class<?> holderClass = Class.forName("net.momirealms.craftengine.core.registry.Holder");
            registeredNameMethod = holderClass.getMethod("registeredName");
            keyOptionalMethod = holderClass.getMethod("keyOptional");

            // Key and ResourceKey methods
            Class<?> resourceKeyClass = Class.forName("net.momirealms.craftengine.core.util.ResourceKey");
            locationMethod = resourceKeyClass.getMethod("location");

            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            keyAsStringMethod = keyClass.getMethod("asString");

            // Item manager for display name lookup
            Class<?> itemManagerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager");
            Method itemManagerInstanceMethod = itemManagerClass.getMethod("instance");
            itemManagerInstance = itemManagerInstanceMethod.invoke(null);
            getItemDefinitionMethod = itemManagerClass.getMethod("getItemDefinition", keyClass);

            // Item building for display name
            Class<?> itemDefClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");
            Class<?> contextClass = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext");
            buildBukkitItemMethod = itemDefClass.getMethod("buildBukkitItem", contextClass);
            emptyContextMethod = contextClass.getMethod("empty");

            return true;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }

    /**
     * Check if the given block state is a CraftEngine custom block
     */
    public static boolean isCustomBlock(BlockState blockState) {
        if (!isAvailable() || blockState == null) return false;
        try {
            Block block = blockState.getBlock();
            if (block == null) return false;
            return (boolean) isCustomBlockMethod.invoke(null, block);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the custom block's registered name (e.g. "mypack:custom_stone")
     */
    public static String getCustomBlockName(BlockState blockState) {
        if (!isAvailable() || blockState == null) return null;
        try {
            Block block = blockState.getBlock();
            if (block == null) return null;

            boolean isCustom = (boolean) isCustomBlockMethod.invoke(null, block);
            if (!isCustom) return null;

            Object state = getCustomBlockStateMethod.invoke(null, block);
            if (state == null) return null;

            Object holder = ownerMethod.invoke(state);
            if (holder == null) return null;

            return (String) registeredNameMethod.invoke(holder);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the custom block's display name (Chinese name from CE config, if available).
     * Falls back to the registered name if no display name is found.
     */
    public static String getCustomBlockDisplayName(BlockState blockState) {
        if (!isAvailable() || blockState == null) return null;
        try {
            Block block = blockState.getBlock();
            if (block == null) return null;

            boolean isCustom = (boolean) isCustomBlockMethod.invoke(null, block);
            if (!isCustom) return null;

            Object state = getCustomBlockStateMethod.invoke(null, block);
            if (state == null) return null;

            Object holder = ownerMethod.invoke(state);
            if (holder == null) return null;

            String registeredName = (String) registeredNameMethod.invoke(holder);
            if (registeredName == null) return null;

            // Try to get the item definition and build the item for display name
            Optional<?> keyOptional = (Optional<?>) keyOptionalMethod.invoke(holder);
            if (keyOptional.isPresent()) {
                Object resourceKey = keyOptional.get();
                Object key = locationMethod.invoke(resourceKey);

                // Look up item definition by the block's key
                Optional<?> itemDefOptional = (Optional<?>) getItemDefinitionMethod.invoke(itemManagerInstance, key);
                if (itemDefOptional.isPresent()) {
                    Object itemDef = itemDefOptional.get();
                    Object emptyContext = emptyContextMethod.invoke(null);
                    ItemStack itemStack = (ItemStack) buildBukkitItemMethod.invoke(itemDef, emptyContext);

                    if (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                        String displayName = itemStack.getItemMeta().getDisplayName();
                        if (displayName != null && !displayName.isEmpty()) {
                            // Strip legacy color codes
                            displayName = displayName.replaceAll("§[0-9a-fk-or]", "").trim();
                            if (!displayName.isEmpty()) {
                                return displayName;
                            }
                        }
                    }
                }
            }

            // Fallback to registered name
            return registeredName;
        } catch (Exception e) {
            return null;
        }
    }
}
