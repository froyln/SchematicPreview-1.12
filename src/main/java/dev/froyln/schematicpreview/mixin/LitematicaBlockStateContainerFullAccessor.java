package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainerFull;

/**
 * Exposes {@code checkForFreedIds} so Replace can force a real palette resize instead of the
 * freed-id reuse path - see {@code ContainerAccessors.forceRealResizeOnOverflow}.
 */
@Mixin(LitematicaBlockStateContainerFull.class)
public interface LitematicaBlockStateContainerFullAccessor
{
    @Accessor(value = "checkForFreedIds", remap = false)
    void schematicpreview$setCheckForFreedIds(boolean value);
}
