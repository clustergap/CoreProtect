package net.coreprotect.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CraftEngineHook {
    private static Boolean available = null;

    // Block detection
    private static Method isCustomBlockMethod;
    private static Method getCustomBlockStateMethod;
    private static Method ownerMethod;
    private static Method registeredNameMethod;

    // Processor for extracting display name from definition
    private static Object itemManagerInstance;
    private static Method getItemDefinitionMethod;
    private static Method processorsMethod;
    private static Method customNameMethod;
    private static Method keyOptionalMethod;
    private static Method locationMethod;

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

            Class<?> blocksClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            isCustomBlockMethod = blocksClass.getMethod("isCustomBlock", Block.class);
            getCustomBlockStateMethod = blocksClass.getMethod("getCustomBlockState", Block.class);

            Class<?> stateClass = Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState");
            ownerMethod = stateClass.getMethod("owner");

            Class<?> holderClass = Class.forName("net.momirealms.craftengine.core.registry.Holder");
            registeredNameMethod = holderClass.getMethod("registeredName");
            keyOptionalMethod = holderClass.getMethod("keyOptional");

            Class<?> resourceKeyClass = Class.forName("net.momirealms.craftengine.core.util.ResourceKey");
            locationMethod = resourceKeyClass.getMethod("location");

            // Item definition & processor for display name
            try {
                Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");

                Class<?> itemManagerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager");
                Method instanceMethod = itemManagerClass.getMethod("instance");
                itemManagerInstance = instanceMethod.invoke(null);
                getItemDefinitionMethod = itemManagerClass.getMethod("getItemDefinition", keyClass);

                Class<?> itemDefClass = Class.forName("net.momirealms.craftengine.core.item.ItemDefinition");
                processorsMethod = itemDefClass.getMethod("processors");

                Class<?> processorClass = Class.forName("net.momirealms.craftengine.core.item.processor.CustomNameProcessor");
                customNameMethod = processorClass.getMethod("customName");
            } catch (Exception ignored) {
            }

            return true;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }

    /**
     * Get the custom block's display name.
     * Priority: CE custom name (Chinese) > CE registered key
     * Returns null if the block is not a CE custom block.
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

            // Try to extract display name from CustomNameProcessor
            if (itemManagerInstance != null && customNameMethod != null) {
                try {
                    java.util.Optional<?> keyOptional = (java.util.Optional<?>) keyOptionalMethod.invoke(holder);
                    if (keyOptional.isPresent()) {
                        Object resourceKey = keyOptional.get();
                        Object key = locationMethod.invoke(resourceKey);

                        java.util.Optional<?> itemDefOptional = (java.util.Optional<?>) getItemDefinitionMethod.invoke(itemManagerInstance, key);
                        if (itemDefOptional.isPresent()) {
                            Object itemDef = itemDefOptional.get();
                            Object[] processors = (Object[]) processorsMethod.invoke(itemDef);
                            if (processors != null) {
                                for (Object processor : processors) {
                                    if (customNameMethod.getDeclaringClass().isInstance(processor)) {
                                        String customName = (String) customNameMethod.invoke(processor);
                                        if (customName != null && !customName.isEmpty()) {
                                            // Strip MiniMessage tags: <tag>, </tag>, <!tag>
                                            customName = customName.replaceAll("<[^>]*>", "").trim();
                                            if (!customName.isEmpty()) {
                                                return customName;
                                            }
                                        }
                                    }
                                }
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
