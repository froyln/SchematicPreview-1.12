package dev.froyln.schematicpreview.materials;

import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3i;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStateContainer;

/**
 * Replaces every occurrence of a block, across the given regions of a schematic, with another
 * block, keeping whichever block-state properties the two blocks share.
 */
public final class BlockReplacer
{
    private BlockReplacer()
    {
    }

    public static long replace(Block oldBlock, Block newBlock, ISchematic schematic, Collection<String> regionNames)
    {
        IBlockState newDefault = newBlock.getDefaultState();
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

                        if (state.getBlock() == oldBlock)
                        {
                            container.setBlockState(x, y, z, copyProperties(state, newDefault));
                            ++count;
                        }
                    }
                }
            }
        }

        if (count > 0)
        {
            SchematicMetadata meta = schematic.getMetadata();

            if (meta.getTotalBlocks() >= 0 && (oldBlock == Blocks.AIR) != (newBlock == Blocks.AIR))
            {
                meta.setTotalBlocks(meta.getTotalBlocks() + (newBlock == Blocks.AIR ? -count : count));
            }

            meta.setTimeModifiedToNow();
            meta.setModifiedSinceSaved();

            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
        }

        return count;
    }

    private static IBlockState copyProperties(IBlockState from, IBlockState to)
    {
        for (IProperty<?> property : from.getPropertyKeys())
        {
            if (to.getPropertyKeys().contains(property))
            {
                to = copyProperty(from, to, property);
            }
        }

        return to;
    }

    private static <T extends Comparable<T>> IBlockState copyProperty(IBlockState from, IBlockState to, IProperty<T> property)
    {
        return to.withProperty(property, from.getValue(property));
    }
}
