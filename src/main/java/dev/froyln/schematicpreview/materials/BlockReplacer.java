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
 * Replaces every block state a material list row stands for, across the given regions, with
 * another block variant, keeping the old state's placement/orientation properties (a slab's
 * half) but not the ones that make up the variant itself (its stone/quartz type).
 * <p>
 * Rows are matched through Litematica's {@link MaterialCache} - the mapping the list was built
 * from - so item-placed blocks (doors, redstone dust) and multi-item states (double slabs)
 * resolve; {@link Block#getBlockFromItem} alone never could. Variant identity is
 * {@link Block#damageDropped(IBlockState)}, since raw metadata packs placement bits.
 * <p>
 * {@link ContainerAccessors#forceRealResizeOnOverflow} is called on every region's container
 * before any {@code setBlockState} - see its doc for why: without it, replacing enough distinct
 * states to overflow a hash-map palette can write a file with a palette one entry too large for
 * its packed bit width, which crashes the game on load/place.
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
        // MaterialCache.getItems() rebuilds its list on every call; memoize per state.
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
            ContainerAccessors.forceRealResizeOnOverflow(container);
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
     * Drops the old block's tile entity NBT and pending tick, and gives a new tile-entity block
     * a fresh default tag (TESR-only blocks like chests are invisible without one).
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

                // Keep the old value only if it doesn't change the new block's item identity.
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
