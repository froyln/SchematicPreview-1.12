package dev.froyln.schematicpreview.render;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStateContainer;

/**
 * A light-weight {@link IBlockAccess} over an {@link ISchematic}'s regions, modeled after
 * Litematica's own {@code ChunkCacheSchematic} (full skylight, no real World). Positions are
 * in schematic-local space: each region's blocks live at {@code region.getPosition() + local},
 * where {@code local} ranges over the region's container indices (always {@code 0..|size|-1},
 * regardless of the sign of {@link ISchematicRegion#getSize()}).
 */
public class SchematicBlockAccess implements IBlockAccess
{
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private final List<RegionEntry> regions = new ArrayList<>();
    private final BlockPos boxMin;
    private final BlockPos boxMax;

    public SchematicBlockAccess(ISchematic schematic)
    {
        BlockPos min = null;
        BlockPos max = null;

        for (ISchematicRegion region : schematic.getRegions().values())
        {
            BlockPos pos = region.getPosition();
            Vec3i size = region.getSize();
            int minX = pos.getX() + (size.getX() < 0 ? size.getX() + 1 : 0);
            int minY = pos.getY() + (size.getY() < 0 ? size.getY() + 1 : 0);
            int minZ = pos.getZ() + (size.getZ() < 0 ? size.getZ() + 1 : 0);
            BlockPos regionMin = new BlockPos(minX, minY, minZ);
            BlockPos regionMax = regionMin.add(Math.abs(size.getX()) - 1, Math.abs(size.getY()) - 1, Math.abs(size.getZ()) - 1);

            this.regions.add(new RegionEntry(regionMin, region.getBlockStateContainer(), region.getBlockEntityMap()));

            min = min == null ? regionMin : new BlockPos(Math.min(min.getX(), regionMin.getX()), Math.min(min.getY(), regionMin.getY()), Math.min(min.getZ(), regionMin.getZ()));
            max = max == null ? regionMax : new BlockPos(Math.max(max.getX(), regionMax.getX()), Math.max(max.getY(), regionMax.getY()), Math.max(max.getZ(), regionMax.getZ()));
        }

        this.boxMin = min != null ? min : BlockPos.ORIGIN;
        this.boxMax = max != null ? max : BlockPos.ORIGIN;
    }

    public BlockPos getBoxMin()
    {
        return this.boxMin;
    }

    public Vec3i getBoxSize()
    {
        return new Vec3i(this.boxMax.getX() - this.boxMin.getX() + 1,
                          this.boxMax.getY() - this.boxMin.getY() + 1,
                          this.boxMax.getZ() - this.boxMin.getZ() + 1);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos)
    {
        for (RegionEntry region : this.regions)
        {
            IBlockState state = region.getBlockState(pos);

            if (state != null)
            {
                return state;
            }
        }

        return AIR;
    }

    @Override
    public boolean isAirBlock(BlockPos pos)
    {
        return this.getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue)
    {
        return FULL_BRIGHT_LIGHT;
    }

    @Override
    public Biome getBiome(BlockPos pos)
    {
        return Biomes.PLAINS;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction)
    {
        return 0;
    }

    @Override
    public WorldType getWorldType()
    {
        return WorldType.DEFAULT;
    }

    public List<BlockPos> getTileEntityPositions()
    {
        List<BlockPos> positions = new ArrayList<>();

        for (RegionEntry region : this.regions)
        {
            for (BlockPos local : region.blockEntities.keySet())
            {
                positions.add(region.min.add(local));
            }
        }

        return positions;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos)
    {
        for (RegionEntry region : this.regions)
        {
            TileEntity te = region.getTileEntity(pos);

            if (te != null)
            {
                return te;
            }
        }

        return null;
    }

    private static final class RegionEntry
    {
        private final BlockPos min;
        private final ILitematicaBlockStateContainer container;
        private final java.util.Map<BlockPos, NBTTagCompound> blockEntities;
        private final java.util.Map<BlockPos, TileEntity> createdTileEntities = new java.util.HashMap<>();

        RegionEntry(BlockPos min, ILitematicaBlockStateContainer container, java.util.Map<BlockPos, NBTTagCompound> blockEntities)
        {
            this.min = min;
            this.container = container;
            this.blockEntities = blockEntities;
        }

        @Nullable
        IBlockState getBlockState(BlockPos pos)
        {
            Vec3i size = this.container.getSize();
            int x = pos.getX() - this.min.getX();
            int y = pos.getY() - this.min.getY();
            int z = pos.getZ() - this.min.getZ();

            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ())
            {
                return null;
            }

            return this.container.getBlockState(x, y, z);
        }

        @Nullable
        TileEntity getTileEntity(BlockPos pos)
        {
            if (this.createdTileEntities.containsKey(pos))
            {
                return this.createdTileEntities.get(pos);
            }

            TileEntity te = null;
            NBTTagCompound tag = this.blockEntities.get(pos.subtract(this.min));

            if (tag != null)
            {
                try
                {
                    te = TileEntity.create(null, tag);
                }
                catch (Throwable ignored)
                {
                    te = null;
                }
            }

            this.createdTileEntities.put(pos, te);

            return te;
        }
    }
}
