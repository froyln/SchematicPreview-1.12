package dev.froyln.schematicpreview.materials;

import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainerFull;

import dev.froyln.schematicpreview.mixin.LitematicaBlockStateContainerFullAccessor;

/**
 * Plain call site for {@code LitematicaBlockStateContainerFullAccessor}. Must live outside the
 * mixin package and outside any mixin's injected method - both cases fail at runtime, not at
 * compile time. See AGENTS.md Gotchas.
 */
public final class ContainerAccessors
{
    private ContainerAccessors()
    {
    }

    /**
     * {@code LitematicaBlockStateContainerFull.onResize} defaults to reusing a freed (zero-count)
     * palette id instead of growing the backing bit array when {@code checkForFreedIds} is set.
     * That reuse only replaces the palette entry - it never widens {@code bits} - but
     * {@code idFor} on the hash-map palette variant has already inserted the new state and grown
     * the palette to one entry past its bit-width capacity by the time {@code onResize} runs. The
     * freed-id path then leaves that oversized palette in place with the old, now too-narrow
     * {@code bits}: the file Litematica later writes packs {@code BlockStates} at the old width
     * but lists one extra {@code BlockStatePalette} entry, so a reader deriving the width back
     * from the palette size sees {@code bits + 1} and reads past the backing array -
     * {@code ArrayIndexOutOfBoundsException} on load/place. Forcing the real-resize branch keeps
     * the palette size and {@code bits} in the relationship every reader assumes.
     */
    public static void forceRealResizeOnOverflow(ILitematicaBlockStateContainer container)
    {
        if (container instanceof LitematicaBlockStateContainerFull)
        {
            ((LitematicaBlockStateContainerFullAccessor) container).schematicpreview$setCheckForFreedIds(false);
        }
    }
}
