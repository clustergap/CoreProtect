package net.coreprotect.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CraftEngineHook {
    private static Boolean available = null;
    private static Method isCustomBlockMethod;
    private static Method getCustomBlockStateMethod;
    private static Method ownerMethod;
    private static Method registeredNameMethod;

    public static boolean isAvailable() {
        if (available == null) {
            available = checkAvailability();
        }
        return available;
    }

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
     * Returns null if the block is not a CraftEngine custom block
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
}
