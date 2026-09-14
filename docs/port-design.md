# Port design: SchematicPreview → LiteLoader 1.12.2

Companion to `AGENTS.md` (facts) and `PLAN.md` (tasks). This document is the *how*: which
Litematica/MaLiLib hooks are used, how the 3D preview is rendered on the 1.12.2 pipeline,
the build setup, and the risks. Every `file:line` below was checked against the reference
sources listed at the top of `PLAN.md` (Litematica `1db931a6`, MaLiLib `2b8e96ce`).

## 1. Scope

In: everything the Fabric mod does — 3D previews (side panel, fullscreen, freecam, per-entry
list/tile previews, preview-type button), directory icons, material-list Replace, config
screen + hotkey. Out: ModMenu (Fabric only), translations other than `en_us`, any code
reuse from the original (All rights reserved).

## 2. Feature parity → 1.12.2 approach

| Original (Fabric, MC 26.x) | 1.12.2 / LiteLoader approach | Task |
|---|---|---|
| `ClientModInitializer` + Fabric events | `LiteMod.init()` → `Registry.INITIALIZATION_DISPATCHER` → malilib `InitializationHandler` | 1 |
| malilib `ConfigManager` + `IConfigHandler` JSON | `JsonModConfig.createJsonModConfig(MOD_INFO, version, categories)` + `Registry.CONFIG_MANAGER` | 1 |
| `ConfigHotkey` callback opening config GUI | `HotkeyConfig.setHotkeyCallback(...)` + `HotkeyProvider` registered in `Registry.HOTKEY_MANAGER` | 1 |
| ModMenu entry | `Configurable.getConfigPanelClass()` → `RedirectingConfigPanel` subclass | 1 |
| `WorldSchematicWrapper extends Level` (fake world) | `SchematicBlockAccess implements IBlockAccess` (no `World` subclass needed in 1.12) | 2 |
| `BlockModelRendererSchematic.tessellateBlock` + `FluidRenderer` | `BlockRendererDispatcher.renderBlock(state, pos, access, buffer)` handles both models and fluids | 2 |
| `GpuBuffer` / `RenderPass` / `RenderTarget` | `VertexBuffer` (VBO) or display list + `net.minecraft.client.shader.Framebuffer` + `GlStateManager` | 2 |
| `SchematicPreviewWidget extends WidgetBase` | `PreviewWidget extends InteractableWidget` (`renderAt`, `onMouseClicked/Released/Scrolled/Moved`) | 2 |
| Mixin `GuiSchematicBrowserBaseMixin` replacing `WidgetSchematicBrowser` | Mixin `BaseSchematicBrowserScreen.createListWidget()` (RETURN) — set factories on the returned `BaseFileBrowserWidget` | 3 |
| `CustomDirectoryEntry extends WidgetDirectoryEntry` | `PreviewDirectoryEntryWidget extends DirectoryEntryWidget` | 3 |
| custom `reCreateListEntryWidgets` for columns | `ListEntryWidgetFactory` (malilib interface made for this) | 3 |
| `SpriteButton` for preview type | `GenericButton.create(w, h, MultiIcon)` with our own icon sheet | 3 |
| Mixin `WidgetSchematicBrowserMixin.drawSelectedSchematicInfo` | Mixin `SchematicInfoWidget.reCreateSubWidgets` | 2 |
| `SchematicPreviewCache` JSON via Codec | `DirectoryIconStore` with Gson (`JsonUtils`) — no Codec in 1.12 | 4 |
| `GuiEntryIconEdit`, `GuiBlockSelect` | `BaseScreen` subclasses with `BaseTextFieldWidget` / `DataListWidget<Block>` | 4, 5 |
| Mixin `WidgetListMaterialListMixin.createListEntryWidget` | Mixin `MaterialListScreen.createListWidget()` (RETURN) → new entry widget factory | 5 |
| `MaterialListSchematicAccessor` etc. | Same idea: `@Accessor` mixins (fields are `private final`) | 5 |
| `Identifier`, `BuiltInRegistries.ITEM` | `ResourceLocation`, `Item.REGISTRY.getObject(...)`, `Block.REGISTRY` | 4, 5 |

## 3. Integration hooks (verified)

### 3.1 Schematic browser list — `BaseSchematicBrowserScreen`
- Target: `gui/BaseSchematicBrowserScreen.java:72` `protected BaseFileBrowserWidget createListWidget()`.
  Subclasses `SchematicManagerScreen` (`:94`) call `super.createListWidget()` and keep the
  instance, so one injection covers Load Schematics, Manager and the Save/Convert screens.
- Mixin: `@Inject(method = "createListWidget", at = @At("RETURN"), remap = false)` — read
  `cir.getReturnValue()` and:
  - `listWidget.setDataListEntryWidgetFactory((e, cd) -> new PreviewDirectoryEntryWidget(e, cd, listWidget, iconProvider, type))`
    (`DataListWidget.java:84`). The icon provider is the screen's `protected final SchematicBrowserIconProvider cachingIconProvider` → `@Shadow`.
  - When `type.getColumns() > 1`: `listWidget.setListEntryWidgetFactory(new TileEntryWidgetFactory(listWidget, type))`
    (`BaseListWidget.java:83`).
  - Entry heights: `listWidget.setListEntryWidgetFixedHeight(type.getHeight(entryWidth))`
    (`BaseListWidget.java:280`). NOTE `BaseFileBrowserWidget.getHeightForListEntryWidgetCreation`
    (`:383`) forces 14 px for directories — fine for LIST, but tile/list-preview types need
    uniform heights: `PreviewDirectoryEntryWidget` sets its own height in the constructor from
    `DataListEntryWidgetData` (`width`, `height` fields are public, `DataListEntryWidgetData.java:7-10`).
- Preview-type button: add in the same injection a `GenericButton` positioned left of the
  navigation widget (`BaseFileBrowserWidget.navigationWidget`, protected). Clicking cycles
  `Configs.Menu.PREVIEW_TYPE` and calls `listWidget.refreshEntries()` — the factories read the
  type lazily so no re-injection is needed.
- `Configs.Generic.ENABLED == false` → injection returns immediately (vanilla behavior).

### 3.2 Side-panel preview — `SchematicInfoWidget`
- Target: `gui/widget/SchematicInfoWidget.java:47` `protected void reCreateSubWidgets()`; the
  thumbnail branch is `:62-66` (`if (this.currentInfo.texture != null) { createPreviewIconWidget(...) }`).
- Mixin: `@Inject(method = "reCreateSubWidgets", at = @At(value = "INVOKE", target = "...createInfoLabelWidget..."), shift = AFTER, locals = CAPTURE_FAILHARD, cancellable = true, remap = false)`
  is fragile (local capture). Simpler and robust: `@Inject(at = @At("TAIL"))` and in the handler
  (a) remove any `IconWidget` from the container (`ContainerWidget.removeWidget`, subwidgets
  list is protected), (b) add `PreviewWidget` at `x = getX()+4`, `y = label.getBottom()+4`
  — we know the label: it is the first sub-widget. Height = `getBottom() - y - 4`.
- The widget needs the current file: `@Shadow @Nullable protected SchematicInfo currentInfo;`
  → `currentInfo.schematic.getFile()`; prefer the already-parsed `currentInfo.schematic`
  (`SchematicInfoCache` parses synchronously on selection, `:44-64`), so the side panel needs no
  async load at all — only the small per-entry widgets do.
- `clearCache()` (`:78`) → `@Inject TAIL` → `PreviewCache.close()`.

### 3.3 Material list Replace — `MaterialListScreen`
- Target: `gui/MaterialListScreen.java:141` `createListWidget()`; line `:151` installs
  `MaterialListEntryWidget` via `setDataListEntryWidgetFactory`. `materialList` is
  `protected final` (`:40`).
- Mixin: `@Inject(method = "createListWidget", at = @At("RETURN"), remap = false)` →
  `cir.getReturnValue().setDataListEntryWidgetFactory((d, cd) -> new ReplaceMaterialListEntryWidget(d, cd, this.materialList))`
  only when `materialList instanceof MaterialListSchematic || MaterialListPlacement`.
- `ReplaceMaterialListEntryWidget extends MaterialListEntryWidget`: `ignoreButton` and
  `materialList` are `protected` (`:48-49`), so override `reAddSubWidgets`/`updateSubWidgetPositions`
  (`:91`, `:100`) to add a `Replace` button left of the ignore button. No mixin on the widget.
- Accessors: `MaterialListSchematic` `private final ISchematic schematic; ImmutableList<String> regions` (`:10-11`);
  `MaterialListPlacement` `private final SchematicPlacement placement` (`:12`) →
  `placement.getSchematic()` (`SchematicPlacement.java:79`), regions = `schematic.getRegionNames()`.
- Rebuild after replace: `DataManager.getSchematicPlacementManager()` (`DataManager.java:114`)
  → `getAllPlacementsOfSchematic(schematic)` (`SchematicPlacementManager.java:439`) →
  `markChunksForRebuild(placement)` (`:943`). Then `materialList.reCreateMaterialList()`.

### 3.4 Tick / lifecycle
- `Registry.TICK_EVENT_DISPATCHER.registerClientTickHandler(handler)`; `ClientTickHandler.onClientTick()`
  (`event/ClientTickHandler.java:16`), profiler supplier has a default.
- `PreviewCache.tickClose()`: when `Minecraft.getMinecraft().currentScreen == null` and not
  already closed → free GL resources, cancel futures. Also save `DirectoryIconStore` if dirty.

## 4. Rendering pipeline (task 2)

```
PreviewRenderer.setup(ISchematic s)                       // client thread
  origin = min over regions of region.getPosition()       // regions may have negative offsets
  size   = s.getEnclosingSize()
  access = new SchematicBlockAccess(s, origin)            // IBlockAccess
  jobs   = all (x,y,z) in [0,size) ; budget = 4096 blocks/frame   // ponytail: constant, make config if too slow
  buffers[layer] = new BufferBuilder(2 MiB) begun with GL_QUADS / DefaultVertexFormats.BLOCK
PreviewRenderer.tick()                                    // called from renderAt, before draw
  for up to `budget` jobs:
     state = access.getBlockState(pos); if air → skip
     layer = state.getBlock().getRenderLayer()            // vanilla 1.12: ONE layer per block (no Forge canRenderInLayer)
     buffers[layer].setTranslation(0,0,0)                 // positions are already world coords
     mc.getBlockRendererDispatcher().renderBlock(state, pos, access, buffers[layer])
  when jobs empty and !uploaded: for each layer: buffer.finishDrawing(); upload
     VBO   : new VertexBuffer(BLOCK); vertexBufferUploader.setVertexBuffer(vb); uploader.draw(buffer)
     no VBO: display list via GLAllocation.generateDisplayLists + Tessellator-style draw
PreviewRenderer.draw(fbo, w, h, camera)                   // every frame
  fbo.bindFramebuffer(true); GL viewport 0,0,w,h; clear color+depth (alpha 0)
  matrix: GL_PROJECTION ← Project.gluPerspective(fov, w/h, 0.05, far)
          GL_MODELVIEW  ← rotate(xRot,1,0,0) rotate(yRot,0,1,0) translate(-cam)
  RenderHelper.disableStandardItemLighting(); GlStateManager.enableDepth(); enableCull
  bind blocks atlas (TextureMap.LOCATION_BLOCKS_TEXTURE); lightmap: OpenGlHelper.setLightmapTextureCoords(240,240) + enable lightmap unit
  draw SOLID, CUTOUT_MIPPED, CUTOUT (alpha test on), TRANSLUCENT (blend on, depthMask false)
  if Configs.Preview.RENDER_TILE_ENTITIES: for each TE from access (created lazily from region NBT,
       te.setWorld(mc.world) is NOT possible without a world → only render TEs whose renderer
       does not need a world: chests, signs, banners, beds, skulls work; catch Throwable per TE and blacklist the class)
  restore: mc.getFramebuffer().bindFramebuffer(true); GL viewport back to display size;
           matrices via GL_PROJECTION/GL_MODELVIEW pop; GlStateManager.disableDepth (GUI default), color 1,1,1,1
PreviewWidget.renderAt(x, y, z, ctx)
  ensure fbo size == (width*scale, height*scale) else recreate (ScaledResolution.getScaleFactor)
  renderer.tick(); renderer.draw(...)
  blit fbo.framebufferTexture as a quad at (x, y, w, h) with v flipped; then overlay buttons
```

Camera: `distance = |(sx*0.5, sy*0.7, sz*0.5)| * 2`, `yRot/xRot` from Preview configs, target
= schematic center. Drag (button 0) → orbit; scroll → distance; freecam mode → drag pans the
target, right-drag rotates. Fullscreen → `PreviewFullscreenScreen` with the same renderer
instance (no re-tessellation).

GL state checklist after `draw()`: framebuffer, viewport, both matrices, depth test off,
cull enabled, blend on/alpha on (GUI defaults), texture unit 0 bound to whatever the GUI
expects (rebind is done by the next widget anyway), lightmap unit disabled.

Thread model: NBT read + `SchematicType.tryCreateSchematicFrom` (pure data) on a
`Executors.newSingleThreadExecutor` daemon; everything touching `BlockRendererDispatcher`, GL,
or `TileEntity` on the client thread. `CompletableFuture.isDone()` polled from `renderAt`.

Volume gate: entry-level previews skip schematics with `metadata.getTotalVolume() > previewMaxVolume`
(metadata is available before tessellating). Side panel/fullscreen ignore the gate (budgeted).

## 5. Data formats

`config/schematicpreview.json` (malilib-managed):
```json
{ "config_version": 1,
  "Generic": { "enabled": true, "openConfigScreen": "RIGHT_SHIFT,F8" },
  "Menu":    { "previewType": "list", "entryGapX": 2, "entryGapY": 2, "listEntryHeight": 15, "listPreviewEntryHeight": 35, "tileHeightRatio": 1.0 },
  "Preview": { "previewMaxVolume": 125000, "renderTileEntities": true, "previewFov": 50.0, "previewRotationY": -45.0, "previewRotationX": 30.0 } }
```
`config/schematicpreview_icons.json` (ours, Gson):
```json
{ "icons": { "/home/me/.minecraft/schematics/farms": { "itemId": "minecraft:wheat", "pos": "center" } } }
```
`pos` ∈ `default` | `center` | `default_with_schematic`. Key = absolute path with `/` separators.

## 6. Build setup (task 1)

`build.properties`:
```
group = dev.froyln
mod_id = schematicpreview
mod_file_name = schematicpreview-liteloader
mod_name = SchematicPreview
author = froyln
mod_version = 0.1.0
malilib_version = 0.53.0
litematica_version = 0.31.4
minecraft_version_out = 1.12.2
minecraft_version = 1.12.2
mappings_version = stable_39
```
`build.gradle` = Litematica's with: `repositories { maven { url 'https://masa.dy.fi/maven' }; flatDir { dirs 'libs' } }`,
`dependencies { deobfCompile "fi.dy.masa.malilib:malilib-liteloader-1.12.2:${malilib_version}:deobf"; deobfCompile name: "litematica-liteloader-1.12.2-${litematica_version}", ext: "litemod" }`,
`litemod.json { dependsOn = ['malilib', 'litematica']; mixinConfigs = ['mixins.schematicpreview.json'] }`,
`sourceSets.main.ext.refMap = 'mixins.schematicpreview.refmap.json'`, `mixin { defaultObfuscationEnv notch }`,
`compileJava { sourceCompatibility = targetCompatibility = 1.8 }`. Buildscript repos:
`https://maven.minecraftforge.net` and `https://repo.spongepowered.org/repository/maven-public`.
Wrapper: Gradle 2.14.1 (what FG 2.3 supports). `libs/*.litemod` gitignored.

If `deobfCompile name:` does not deobfuscate the flatDir jar (FG 2.3 quirk), fallback in this
order: (1) `deobfProvided`, (2) build Litematica from source → `publishToMavenLocal` and use
`mavenLocal()` + coordinates `fi.dy.masa.litematica:litematica-liteloader-1.12.2:0.31.4:deobf`.

`mixins.schematicpreview.json`:
```json
{ "required": true, "package": "dev.froyln.schematicpreview.mixin", "compatibilityLevel": "JAVA_8",
  "refmap": "mixins.schematicpreview.refmap.json", "minVersion": "0.7",
  "client": ["BaseSchematicBrowserScreenMixin", "SchematicInfoWidgetMixin", "MaterialListScreenMixin",
             "MaterialListSchematicAccessor", "MaterialListPlacementAccessor"],
  "injectors": { "defaultRequire": 1 } }
```
All targets are Litematica classes → `remap = false` everywhere; the refmap will be empty but
must still exist.

## 7. Order and dependencies

```
T1 skeleton ──► T2 renderer + side panel ──► T3 entry types/tile grid ──► T4 directory icons
                                                                        └► T5 Replace (independent of T3/T4, needs T1 only)
                                                                           T6 release (after all)
```
T5 can be done right after T1 if the renderer stalls. T3 depends on T2's `PreviewCache`
small widgets. T4 depends on T3's entry widget.

Definition of done per task = its `PLAN.md` Acceptance + `./gradlew build` exit 0 + `/review`.

## 8. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| ForgeGradle 2.3 toolchain fails in 2026 (dead URLs, TLS, Gradle 2.14 on modern OS) | medium | JDK 8 only; forge maven URL updated; keep `~/.gradle` cache once it works; document in README. Last resort: build inside a Docker image with JDK 8. |
| flatDir `deobfCompile` of the Litematica `.litemod` does not remap | medium | fallback chain in §6 |
| `BlockRendererDispatcher.renderBlock` needs `IBlockAccess.getBiome`, `getStrongPower`, `isSideSolid` for some blocks | certain | implement all `IBlockAccess` methods with sane defaults (plains, 0, `state.isSideSolid`) |
| Tile entity renderers NPE without a `World` | high | try/catch per TE class, blacklist on failure, config `renderTileEntities` off switch |
| Large schematics stall the GUI | high | per-frame tessellation budget + volume gate for entry previews |
| VRAM leak from FBOs/VBOs per entry widget | high | single owner (`PreviewCache`), `tickClose()`; small widgets share one FBO sized to the largest entry and render sequentially |
| Multi-column layout breaks malilib keyboard navigation / selection index math | medium | `TileEntryWidgetFactory.getTotalListWidgetCount()` returns row count; test arrow keys + Enter in acceptance |
| Mixin `@Inject` into `reCreateSubWidgets` misses future Litematica versions | low | version pinned (0.31.4 is the final 1.12.2 build) |
| Off-thread schematic parsing calls `MessageDispatcher` (GUI) on failure | low | catch inside the future, log only, return null |

## 9. Decisions still open (defaults chosen, change in AGENTS.md if you disagree)

- Package `dev.froyln.schematicpreview`, mod id `schematicpreview`, file `schematicpreview-liteloader-1.12.2-<ver>.litemod`.
- License: LGPL-3.0 (same as Litematica/MaLiLib) — set in T6.
- Preview button icons: hand-drawn 12×12 sprites in `assets/schematicpreview/textures/gui/widgets.png`.
- Config hotkey default `RIGHT_SHIFT,F8` kept from the original for muscle memory.
