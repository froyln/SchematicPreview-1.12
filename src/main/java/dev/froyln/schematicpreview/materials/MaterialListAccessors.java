package dev.froyln.schematicpreview.materials;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.ISchematic;

import dev.froyln.schematicpreview.mixin.MaterialListPlacementAccessor;
import dev.froyln.schematicpreview.mixin.MaterialListSchematicAccessor;

/**
 * Plain call sites for {@code MaterialListSchematicAccessor}/{@code MaterialListPlacementAccessor}.
 * See {@code gui.BrowserWidgetAccessors} for why this can't live in the mixin package or inside
 * a mixin's own injected method.
 */
public final class MaterialListAccessors
{
    private MaterialListAccessors()
    {
    }

    public static ISchematic getSchematic(MaterialListSchematic materialList)
    {
        return ((MaterialListSchematicAccessor) materialList).schematicpreview$getSchematic();
    }

    public static ImmutableList<String> getRegions(MaterialListSchematic materialList)
    {
        return ((MaterialListSchematicAccessor) materialList).schematicpreview$getRegions();
    }

    public static ISchematic getSchematic(MaterialListPlacement materialList)
    {
        return ((MaterialListPlacementAccessor) materialList).schematicpreview$getPlacement().getSchematic();
    }
}
