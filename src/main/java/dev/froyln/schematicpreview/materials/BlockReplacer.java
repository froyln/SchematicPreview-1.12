package dev.froyln.schematicpreview.materials;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStateContainer;

/**
 * Replaces every block state that a material list row stands for, across the given regions
 * of a schematic, with another block variant, keeping whichever placement/orientation
 * properties the old state had that don't belong to the new variant's own identity (e.g. a
 * slab's top/bottom half survives, but its stone/quartz/etc. variant is fully replaced
 * instead of merged with the old one).
 * <p>
 * A row is matched by asking Litematica's {@link MaterialCache} for the items each placed
 * state requires - the exact mapping the material list was built from - so rows for
 * item-placed blocks (doors, redstone dust, repeaters, beds, crops...) and for states that
 * need several items (double slabs) resolve to their blocks too, which
 * {@link Block#getBlockFromItem} alone never could. The new variant's identity is expressed
 * via {@link Block#damageDropped(IBlockState)} rather than the raw state metadata, since
 * several vanilla blocks - slabs being the obvious case - pack placement-only bits into it.
 */
public final class BlockReplacer
{
    private BlockReplacer()
    {
    }

    public static long replace(ItemStack oldStack, Block newBlock, int newMeta,
                               ISchematic schematic, Collection<String> regionNames)
    {
        MaterialCache cache = MaterialCache.getInstance();
        // MaterialCache.getItems() rebuilds its list on every call; a schematic has millions
        // of positions but only a palette's worth of distinct states, so memoize per state.
        Map<IBlockState, Boolean> matches = new IdentityHashMap<>();
        IBlockState newBase = newBlock.getStateFromMeta(newMeta);
        long count = 0;

        for (String regionName : regionNames)
        {
            ISchematicRegion region = schematic.getSchematicRegion(regionName);

            if (region == null)
            {
                continue;
            }

            ILitematicaBlockStateContainer container = region.getBlockStateContainer();
            Vec3i size = container.getSize();

            for (int y = 0; y < size.getY(); ++y)
            {
                for (int z = 0; z < size.getZ(); ++z)
                {
                    for (int x = 0; x < size.getX(); ++x)
                    {
                        IBlockState state = container.getBlockState(x, y, z);

                        if (matches.computeIfAbsent(state, s -> requiresItem(cache, s, oldStack)))
                        {
                            container.setBlockState(x, y, z, buildReplacement(state, newBlock, newBase, newMeta));
                            ++count;

                            if (newBlock != state.getBlock())
                            {
                                replaceBlockEntityData(region, new BlockPos(x, y, z), newBlock, newMeta);
                            }
                        }
                    }
                }
            }
        }

        if (count > 0)
        {
            SchematicMetadata meta = schematic.getMetadata();

            // Air is never a material list row, so a count change can only be non-air -> air.
            if (meta.getTotalBlocks() >= 0 && newBlock == Blocks.AIR)
            {
                meta.setTotalBlocks(meta.getTotalBlocks() - count);
            }

            meta.setTimeModifiedToNow();
            meta.setModifiedSinceSaved();

            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
        }

        return count;
    }

    private static boolean requiresItem(MaterialCache cache, IBlockState state, ItemStack stack)
    {
        if (state.getBlock() == Blocks.AIR)
        {
            return false;
        }

        for (ItemStack required : cache.getItems(state))
        {
            if (required.getItem() == stack.getItem() && required.getMetadata() == stack.getMetadata())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The old block's tile entity NBT and pending tick must not survive a change of block:
     * a chest's NBT left under a stone block makes the preview draw a chest lid over it and
     * the saved file carry dead data. A new tile-entity block gets a fresh default tag
     * (position in region-local coordinates, same convention as Litematica's own map), so
     * TESR-only blocks like chests don't come out invisible in the preview.
     */
    private static void replaceBlockEntityData(ISchematicRegion region, BlockPos pos, Block newBlock, int newMeta)
    {
        region.getBlockTickMap().remove(pos);

        NBTTagCompound nbt = createDefaultTileEntityNbt(newBlock, newMeta, pos);

        if (nbt != null)
        {
            region.getBlockEntityMap().put(pos, nbt);
        }
        else
        {
            region.getBlockEntityMap().remove(pos);
        }
    }

    @Nullable
    private static NBTTagCompound createDefaultTileEntityNbt(Block block, int meta, BlockPos pos)
    {
        if ((block instanceof ITileEntityProvider) == false)
        {
            return null;
        }

        try
        {
            TileEntity te = ((ITileEntityProvider) block).createNewTileEntity(null, meta);

            if (te == null)
            {
                return null;
            }

            te.setPos(pos);
            return te.writeToNBT(new NBTTagCompound());
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static IBlockState buildReplacement(IBlockState oldState, Block newBlock, IBlockState newBase, int newMeta)
    {
        IBlockState result = newBase;

        for (IProperty<?> property : oldState.getPropertyKeys())
        {
            if (result.getPropertyKeys().contains(property))
            {
                IBlockState candidate = copyProperty(oldState, result, property);

                // Only keep the old value if it doesn't change the new block's item identity -
                // i.e. it's a placement/orientation property (like a slab's half), not one of
                // the properties that make up the picked variant (like a slab's stone/quartz type).
                if (newBlock.damageDropped(candidate) == newMeta)
                {
                    result = candidate;
                }
            }
        }

        return result;
    }

    private static <T extends Comparable<T>> IBlockState copyProperty(IBlockState from, IBlockState to, IProperty<T> property)
    {
        return to.withProperty(property, from.getValue(property));
    }
}
