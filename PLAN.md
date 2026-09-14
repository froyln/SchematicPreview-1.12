# PLAN.md

Execution tracker. Project context is in `AGENTS.md`. Agents read this at session start
and after every compaction (`.claude/hooks/on-compact.sh` re-injects the head of this file).

Status legend: `pending` / `in progress` / `blocked (<why>)` / `done (<sha>)` / `dropped (<why>)`.
Exactly one task should be `in progress` at a time.

Reference material (read-only, outside the repo, re-clone if missing):
- Original Fabric mod (behavior reference, **All rights reserved — do not copy code**):
  `git clone https://github.com/DimasKama/SchematicPreview` → `/tmp/ref/SchematicPreview`
- Litematica LiteLoader source: `git clone -b liteloader_1.12.2 https://github.com/maruohon/litematica && git checkout 1db931a6`
  (= release 0.31.3, the newest commit on that branch; the binary we actually build against,
  0.31.4, has no separate commit upstream — same API, verified by inspecting its class list).
- MaLiLib 0.53.0 LiteLoader source: `git clone -b liteloader_1.12.2 https://github.com/maruohon/malilib && git checkout 2b8e96ce`

Design (hook points with file:line, render pipeline, build files, risks): `docs/port-design.md`.
Nothing is started. Tasks are ordered; each one is shippable on its own. Dependency graph:
T1 → T2 → T3 → T4; T5 needs only T1; T6 last.

---

## Task: Bootstrap the LiteLoader mod skeleton

**Status:** done (uncommitted)

Create the buildable, loadable mod with configs, hotkey and config screen but no features.
**Java 8 only. Must build with `./gradlew build` under JDK 8 and show up in the LiteLoader
mod list of `runClient`.** Package `dev.froyln.schematicpreview`, mod id `schematicpreview`.

### Files to read

- Litematica `build.gradle`, `build.properties`, `src/main/resources/litemod.json`,
  `mixins.litematica.json` — copy the build setup shape (FG 2.3 liteloader plugin, mixin plugin,
  `litemod { json { dependsOn, mixinConfigs } }`, `jar { from litemod.outputs }`).
- Litematica `LiteModLitematica.java`, `InitHandler.java`, `config/Configs.java` (categories),
  `gui/ConfigScreen.java`, `gui/LitematicaConfigPanel.java`, `input/LitematicaHotkeyProvider.java`
  — the malilib 0.53 registration pattern.
- MaLiLib `config/option/*.java`, `config/JsonModConfig.java`, `registry/Registry.java`.

### Steps

1. `build.gradle` + `build.properties` + `settings.gradle` + gradle 2.14.1 wrapper; deps:
   `deobfCompile "fi.dy.masa.malilib:malilib-liteloader-1.12.2:0.53.0:deobf"` from
   `https://masa.dy.fi/maven`, litematica 0.31.4 from `libs/` (flatDir) — document in README
   where to get the file. `libs/` gitignored.
2. `Reference`, `LiteModSchematicPreview` (`LiteMod` + `Configurable` → `RedirectingConfigPanel`),
   `InitHandler` registering: `JsonModConfig` with categories, `ConfigScreen` factory + tabs,
   `HotkeyProvider`, `ClientTickHandler` (empty for now).
3. `Configs`: Generic `enabled` (bool, true), `openConfigScreen` (hotkey, default `RIGHT_SHIFT,F8`);
   Menu `previewType` (OptionList of `PreviewType`, default LIST), `entryGapX`/`entryGapY`
   (0–10, default 2), `listEntryHeight` (10–80, 15), `listPreviewEntryHeight` (15–80, 35),
   `tileHeightRatio` (0.3–5.0, 1.0); Preview `previewMaxVolume` (0–MAX, 125000),
   `renderTileEntities` (true), `previewFov` (30–100, 50), `previewRotationY` (−180–180, −45),
   `previewRotationX` (−90–90, 30).
4. `litemod.json` (`dependsOn: ["malilib","litematica"]`, `mixinConfigs`), empty
   `mixins.schematicpreview.json`, `en_us.lang` with config names/comments.
5. `README.md` build section verified against what actually worked (JDK path, workspace task).

### Acceptance

- `./gradlew build` exits 0 and produces `build/libs/schematicpreview-liteloader-1.12.2-0.1.0.litemod` — PASS.
- `./gradlew runClient`: LiteLoader mod list shows SchematicPreview; hotkey opens a config
  screen with tabs Generic / Menu / Preview / Hotkeys; changed values persist in
  `config/schematicpreview.json` after restart — **not verified in this session**, see notes.

### Notes / findings

- **Build verified programmatically**, twice, including a `clean build` from a tree with
  nothing but the checked-in `tools/setup-build-deps.py` output in `libs/`/`.jdk-cache/`:
  `./gradlew build` → `BUILD SUCCESSFUL`, produced
  `build/libs/schematicpreview-liteloader-1.12.2-0.1.0.litemod`. Unzipped and checked:
  `litemod.json` has `"version": "0.1.0"` (the `@MOD_VERSION@` token was replaced),
  `"dependsOn": ["malilib", "litematica"]`, all 12 compiled classes present, `en_us.lang`
  and `mixins.schematicpreview.json` packaged.
- **`./gradlew runClient` could not be verified end-to-end.** It got past `:downloadClient`
  (the actual game jar) but stalled at 31% through `DownloadAssetsTask` — Mojang's
  `resources.download.minecraft.net` returned HTTP 400 for most sound files, unrelated to
  this project's code (a stock FG2 task hitting the resource CDN's abuse throttling from this
  network). Bounded the attempt to 8 minutes and gave up rather than fight a third-party CDN.
  **Whoever picks this up next should run `./gradlew runClient` once on a normal network** and
  confirm: LiteLoader's mod list shows SchematicPreview, `RIGHT_SHIFT,F8` opens a config screen
  with the 4 tabs, and edited values survive a restart in `config/schematicpreview.json`. Until
  that happens, treat the in-game behavior as unverified, even though the code review of the
  malilib registration calls (`Registry.CONFIG_MANAGER` / `CONFIG_SCREEN` / `CONFIG_TAB` /
  `HOTKEY_MANAGER`, all matching Litematica's own `InitHandler` pattern 1:1) gives high confidence.
- **Real build-breaking bug found and fixed, not anticipated in the design doc:** JDK 8u504+
  (this machine's system JDK) makes ForgeGradle 2.3's `TaskSingleDeobfBin` throw
  `java.util.zip.ZipException: invalid entry compressed size` — deterministic, not caused by
  which dependency or which entry. Root cause: that task does `zout.putNextEntry(new
  JarEntry(oldEntry))` (copies the *old* jar's compressed-size metadata verbatim) then
  re-deflates the content with the running JVM's `Deflater`; a newer JDK's zlib doesn't
  reproduce the exact byte count the original packer produced. Fixed with two changes, both
  automated in `tools/setup-build-deps.py` (see `AGENTS.md` → Gotchas for the full story):
  pin the build to Temurin **8u302** instead of system JDK 8, and repack the Litematica
  `.litemod` as fully **STORED/uncompressed** before it ever reaches Gradle. Also needed:
  `gradle.properties` with `org.gradle.jvmargs=-Xmx3G` (missing from the template scaffold,
  `genSrgs` OOMs without it) and `ext: "litemod"` on the flatDir dependency declaration
  (Gradle's flatDir resolver only guesses `.jar` by default).
- Litematica LiteLoader has no published Maven artifact for 1.12.2 (only a Fabric one on
  masa's maven); used the direct download at `masa.dy.fi/mcmods/litematica/` instead
  (version **0.31.4**, newer than the `1db931a6`/0.31.3 source PLAN.md's "Reference material"
  points at — no separate commit exists for 0.31.4 upstream, but its class list was checked
  and matches the API `docs/port-design.md` was verified against: `BaseSchematicBrowserScreen`,
  `SchematicInfoWidget`, `MaterialListScreen`, `MaterialListSchematic`/`Placement`,
  `SchematicPlacementManager` all present with the expected shapes).
- Decompiled a class from the downloaded litemod (`javap` on `ISchematic.class`) and confirmed
  vanilla types inside it are still Notch-obfuscated (e.g. `Vec3i` appears as `fq`,
  `NBTTagCompound` as `fy`) — deobfuscation really is required, not an optional step.

---

## Task: 3D preview renderer + side-panel preview

**Status:** pending

The core feature: replace the static thumbnail in Litematica's schematic info panel with a
live 3D render of the selected schematic. **Tessellation only on the client thread; every GL
resource freed when the screen closes; GL state restored after each draw.**

### Files to read

- `AGENTS.md` → Architecture steps 2–6 (the design is there).
- Litematica `gui/widget/SchematicInfoWidget.java` (`reCreateSubWidgets`, `createPreviewIconWidget`),
  `gui/util/SchematicInfoCache.java`, `schematic/ISchematic.java`, `schematic/ISchematicRegion.java`,
  `schematic/container/ILitematicaBlockStateContainer.java`, `schematic/SchematicType.java`.
- Litematica `render/schematic/ChunkCacheSchematic.java` and `world/WorldSchematic.java` —
  how they satisfy `IBlockAccess` for the block renderer (we do the same without a World).
- Vanilla 1.12.2 `RenderChunk.rebuildChunk`, `Framebuffer`, `VertexBuffer`, `GuiInventory.drawEntityOnScreen`
  (the GUI-embedded 3D render pattern).
- MaLiLib `gui/widget/BaseWidget.java`, `ContainerWidget.java`, `gui/BaseScreen.java`,
  `render/ScreenContext` — how widgets render/receive mouse events.

### Steps

1. `render/SchematicBlockAccess` — `IBlockAccess` over an `ISchematic` (all regions placed at
   their `getPosition()` offsets relative to the enclosing box origin), air outside, full light,
   plains biome, tile entities created from `getBlockEntityMap()` NBT on demand.
2. `render/PreviewRenderer` — `setup(ISchematic)`: per `BlockRenderLayer` a `BufferBuilder`,
   `BlockRendererDispatcher.renderBlock` for each non-air state, upload to `VertexBuffer`;
   budget tessellation to N blocks per frame (config-free constant, note ceiling with a
   `ponytail:` comment). `draw(width, height, fov, camera)`: FBO bind, projection/modelview,
   layer draw order, TEs, unbind, blit. `close()` frees everything.
3. `render/PreviewCache` — `CompletableFuture<ISchematic>` per path (loads NBT off-thread),
   small widgets per path, `tickClose()` from `ClientTickHandler`.
4. `gui/PreviewWidget` — malilib widget: owns a renderer + FBO, camera state (`distance`,
   `yRot`, `xRot` from configs, reset on new schematic), drag to rotate, scroll to zoom,
   overlay buttons `fullscreen` and `freecam` (freecam: drag pans / moves camera instead of
   orbiting). `gui/PreviewFullscreenScreen` wraps the same widget at full size.
5. `mixin/SchematicInfoWidgetMixin` — `reCreateSubWidgets` (TAIL, `SchematicInfoWidget.java:47`):
   when enabled, remove the vanilla `IconWidget`, add the `PreviewWidget` below the label
   (first sub-widget) fed from `@Shadow currentInfo.schematic` (already parsed, no async
   needed here); `clearCache()` (`:78`) TAIL → `PreviewCache.close()`. Design §3.2.

### Acceptance

- Load Schematics screen: selecting a `.litematic`, `.schematic`, `.schem` or `.nbt` file
  shows a rotating-able 3D preview; multi-region schematics render all regions at the right
  offsets; translucent blocks (glass, water) render after solids.
- Selecting a file with invalid contents shows a placeholder, no crash in the log.
- Opening/closing the screen 20 times does not grow VRAM (watch `Framebuffer`/VBO counts
  via a debug log line, then remove the line).
- `./gradlew build` exits 0.

### Notes / findings

---

## Task: Browser entry types (list preview + tile grid) and preview-type button

**Status:** pending

Per-entry previews and multi-column tile layouts in the schematic browser, cycled by a small
button next to the directory navigation bar (left click forward, right click backward).
**Do not break Litematica's keyboard navigation, search bar, file operations or
"remember scroll position".**

### Files to read

- MaLiLib `gui/widget/list/BaseFileBrowserWidget.java`, `BaseListWidget.java`
  (`ListEntryWidgetFactory`, `reCreateListEntryWidgets`, `getHeightForListEntryWidgetCreation`),
  `DataListWidget.java`, `gui/widget/list/entry/DirectoryEntryWidget.java`.
- Litematica `gui/BaseSchematicBrowserScreen.java` (`createListWidget`, `setCommonSchematicBrowserSettings`)
  and the subclasses `SchematicBrowserScreen`, `SchematicManagerScreen`, `BaseSaveSchematicScreen`.

### Steps

1. `config/PreviewType` enum (`OptionListConfigValue`): `LIST`(1 col), `LIST_PREVIEW`(1 col,
   small preview left of the name), `TILE_5/4/3` (N cols, preview above name). Heights from
   Menu configs.
2. `gui/PreviewDirectoryEntryWidget extends DirectoryEntryWidget` — renders preview (from
   `PreviewCache.getSmallWidget(path)`) when the type has one and the schematic volume is
   ≤ `previewMaxVolume`; otherwise the type icon. Clamp the name text to the entry width.
3. `gui/TileEntryWidgetFactory implements ListEntryWidgetFactory` — grid layout with
   `entryGapX/Y`, `getTotalListWidgetCount()` = rows so scrolling stays consistent.
4. `mixin/BaseSchematicBrowserScreenMixin` — `createListWidget` RETURN
   (`BaseSchematicBrowserScreen.java:72`, covers Manager/Save screens too): install
   `setDataListEntryWidgetFactory` (entry widget, icon provider from `@Shadow cachingIconProvider`),
   `setListEntryWidgetFactory` for tile types, entry height, and a preview-type
   `GenericButton` left of the navigation widget; on click cycle the config and
   `refreshEntries()`. Design §3.1.

### Acceptance

- All five types render correctly in Load Schematics, Schematic Manager and the Save screens;
  scrolling reaches the last row in tile mode; search filtering still works; double-click on a
  directory still navigates.
- `./gradlew build` exits 0.

### Notes / findings

---

## Task: Custom directory icons

**Status:** pending

Right-click a directory entry → screen with an item id text field and a position cycle
(`default`, `center`, `default with schematic`) → icon persisted in
`config/schematicpreview_icons.json`. Directories without a custom icon show the preview of
their first schematic file (when the type has previews).

### Files to read

- MaLiLib `gui/widget/ItemStackWidget.java`, `gui/widget/BaseTextFieldWidget.java`,
  `gui/BaseScreen.java`, `util/data/json/JsonUtils.java`, `util/FileUtils.java`
  (config directory).
- Vanilla `Item.REGISTRY`, `RenderItem.renderItemAndEffectIntoGUI`.

### Steps

1. `data/DirectoryIconStore` — load on init, `markDirty()`, save from the
   `ClientTickHandler` when dirty and `currentScreen == null` (malilib 0.53 has no
   client-shutdown event; saving on screen close is enough).
2. `gui/DirectoryIconEditScreen` — text field with live validation against `Item.REGISTRY`,
   position button, OK/Cancel.
3. Hook right-click in `PreviewDirectoryEntryWidget.onMouseClicked` (button 1 on the icon
   area) and render the custom icon / first-schematic preview.

### Acceptance

- Set an icon, restart the client, icon is still there; invalid id shows an error and does
  not save; deleting the JSON resets to defaults without errors.
- `./gradlew build` exits 0.

### Notes / findings

---

## Task: "Replace" button in the material list

**Status:** pending

Each material list row (for schematic- and placement-based lists only) gets a `Replace`
button that opens a searchable block picker and replaces every block of that type in the
schematic, keeping shared block-state properties. **Must mark placements for rebuild and
metadata as modified so Litematica's save prompt works.**

### Files to read

- Litematica `gui/widget/list/entry/MaterialListEntryWidget.java`, `gui/MaterialListScreen.java`,
  `materials/MaterialListSchematic.java`, `materials/MaterialListPlacement.java`,
  `materials/MaterialListBase.java` (`reCreateMaterialList`), `schematic/SchematicMetadata.java`,
  `schematic/placement/SchematicPlacementManager.java` (`getAllPlacementsOfSchematic`,
  `markChunksForRebuild`).
- MaLiLib `gui/widget/list/DataListWidget.java` for the block picker list.

### Steps

1. Accessor mixins: `MaterialListSchematicAccessor` (`schematic`, `regions`),
   `MaterialListPlacementAccessor` (`placement`).
2. `materials/BlockReplacer.replace(oldBlock, newBlock, schematic, regionNames)` → count;
   copies each property of `old.defaultState` that `new.defaultState` has; adjusts
   `totalBlocks` on air↔non-air; `setTimeModifiedToNow`, `setModifiedSinceSaved`; rebuild
   placements.
3. `gui/BlockSelectScreen` — `DataListWidget<Block>` over `Block.REGISTRY` with search,
   item icon + display name per row, click = pick.
4. `gui/ReplaceMaterialListEntryWidget extends MaterialListEntryWidget` — override
   `reAddSubWidgets`/`updateSubWidgetPositions` (`ignoreButton`, `materialList` are protected)
   to add a `Replace` button left of Ignore; on pick call the replacer, then
   `materialList.reCreateMaterialList()` and `MessageDispatcher.generic(...)` with the count.
5. `mixin/MaterialListScreenMixin` — `createListWidget` RETURN (`MaterialListScreen.java:141`):
   swap the factory for the widget above only for schematic/placement lists. Design §3.3.

### Acceptance

- Replace stone with dirt in a loaded schematic: count message shown, list refreshes,
  placement re-renders with dirt, Schematic Manager shows the schematic as modified,
  saving writes dirt. Stairs → other stairs keep `facing`/`half`.
- The button is absent in area-analyzer material lists.
- `./gradlew build` exits 0.

### Notes / findings

---

## Task: Polish and first release

**Status:** pending

Translations complete (`en_us.lang`), icon textures for the overlay buttons (own drawings,
not copied from the original), README with screenshots, LICENSE (pick one — LGPL-3.0 keeps
it compatible with Litematica/MaLiLib), `mod_version = 0.1.0`, build artifact tested in a
real LiteLoader 1.12.2 profile with Litematica 0.31.4 + MaLiLib 0.53.0.

### Acceptance

- Fresh MC 1.12.2 + LiteLoader install with the three `.litemod` files: no errors in
  `latest.log`, all features of the previous tasks work.

### Notes / findings

---

## Done

(nothing yet)

## Dropped / deferred

- ModMenu integration — dropped, Fabric-only; LiteLoader's mod panel (`Configurable`) covers it.
- Translations beyond `en_us` — deferred until features stabilize.
