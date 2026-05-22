package net.coreprotect.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CraftEngineHook {
    private static Boolean available = null;
    private static boolean itemBuildFailed = false;

    // Block detection methods
    private static Method isCustomBlockMethod;
    private static Method getCustomBlockStateMethod;
    private static Method ownerMethod;
    private static Method registeredNameMethod;

    // Display name methods (may fail on some CE versions)
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

            // Block detection - essential
            Class<?> blocksClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            isCustomBlockMethod = blocksClass.getMethod("isCustomBlock", Block.class);
            getCustomBlockStateMethod = blocksClass.getMethod("getCustomBlockState", Block.class);

            Class<?> stateClass = Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState");
            ownerMethod = stateClass.getMethod("owner");

            Class<?> holderClass = Class.forName("net.momirealms.craftengine.core.registry.Holder");
            registeredNameMethod = holderClass.getMethod("registeredName");

            // Item building - optional, may fail on some setups
            try {
                Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
                Class<?> resourceKeyClass = Class.forName("net.momirealms.craftengine.core.util.ResourceKey");

                Class<?> itemManagerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager");
                Method itemManagerInstanceMethod = itemManagerClass.getMethod("instance");
                itemManagerInstance = itemManagerInstanceMethod.invoke(null);
                getItemDefinitionMethod = itemManagerClass.getMethod("getItemDefinition", keyClass);

                Class<?> itemDefClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");
                Class<?> contextClass = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext");
                buildBukkitItemMethod = itemDefClass.getMethod("buildBukkitItem", contextClass);
                emptyContextMethod = contextClass.getMethod("empty");
            } catch (Exception e) {
                itemBuildFailed = true;
            }

            return true;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }

    /**
     * Get the custom block's display name or registered name.
     * Returns the CE registered name (key) if the block is a CE custom block,
     * null otherwise.
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

            // Try to get display name via item building (may fail)
            if (!itemBuildFailed && itemManagerInstance != null) {
                try {
                    Class<?> holderClass = holder.getClass();
                    Method keyOptionalMethod = holderClass.getMethod("keyOptional");
                    java.util.Optional<?> keyOptional = (java.util.Optional<?>) keyOptionalMethod.invoke(holder);
                    if (keyOptional.isPresent()) {
                        Object resourceKey = keyOptional.get();
                        Method locationMethod = resourceKey.getClass().getMethod("location");
                        Object key = locationMethod.invoke(resourceKey);

                        java.util.Optional<?> itemDefOptional = (java.util.Optional<?>) getItemDefinitionMethod.invoke(itemManagerInstance, key);
                        if (itemDefOptional.isPresent()) {
                            Object itemDef = itemDefOptional.get();
                            Object emptyContext = emptyContextMethod.invoke(null);
                            org.bukkit.inventory.ItemStack itemStack = (org.bukkit.inventory.ItemStack) buildBukkitItemMethod.invoke(itemDef, emptyContext);
                            if (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                                String dn = itemStack.getItemMeta().getDisplayName().replaceAll("§[0-9a-fk-or]", "").trim();
                                if (!dn.isEmpty()) return dn;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            return registeredName;
        } catch (Exception e) {
            return null;
        }
    }
}
