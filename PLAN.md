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

**Status:** done (`83d7812`)

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

**Status:** done (`0c12309`)

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
  offsets; translucent blocks (glass, water) render after solids. — **not verified in this
  session**, see notes (same `runClient` network limitation as task 1).
- Selecting a file with invalid contents shows a placeholder, no crash in the log. —
  **not verified in-game**; code path exists (`PreviewCache` catches `Throwable` around
  `SchematicType.tryCreateSchematicFrom`, resolves the future to `null`, `PreviewWidget`
  renders an "Invalid schematic" placeholder instead of touching a renderer).
- Opening/closing the screen 20 times does not grow VRAM (watch `Framebuffer`/VBO counts
  via a debug log line, then remove the line). — **not verified in-game**.
- `./gradlew build` exits 0 — **PASS**, see notes.

### Notes / findings

- **Build verified programmatically:** `JAVA_HOME=.jdk-cache/jdk8u302-b08 ./gradlew build` →
  `BUILD SUCCESSFUL`. Unzipped `build/libs/schematicpreview-liteloader-1.12.2-0.1.0.litemod`
  and confirmed all 6 new classes present, `mixins.schematicpreview.json` lists
  `SchematicInfoWidgetMixin` in `client`, refmap exists (empty mappings, expected — every
  mixin target is a non-obfuscated Litematica/MaLiLib class, `remap = false` throughout).
- **`./gradlew runClient` could not be verified end-to-end**, same as task 1: this network's
  connection to `resources.download.minecraft.net` returns HTTP 400 for most asset downloads
  (`DownloadAssetsTask` stalls around 29%). Bounded the attempt and moved on rather than fight
  a third-party CDN a second time. **Whoever picks up task 3 should run `./gradlew runClient`
  once on a normal network** and confirm every acceptance item above.
- **Real, project-wide build bug found and fixed, not anticipated in the design doc:**
  `deobfCompile` does not deobfuscate Litematica's *referenced vanilla types* — only its own
  class/method names (which were never obfuscated to begin with). Litematica's `.litemod` is
  compiled directly against raw notch Minecraft (LiteLoader's normal workflow has no SRG
  stage), but ForgeGradle's `deobfCompile`/`TaskSingleDeobfBin` is built for the Forge-
  ecosystem case (third-party `:deobf` artifacts are already SRG-named) and only remaps
  SRG → MCP. Fed raw notch input, it silently left things like
  `ISchematicRegion.getPosition()` compiling as returning a class named `et` instead of
  `BlockPos` — confirmed with `javap` on the resolved dependency jar. This didn't surface in
  task 1 because nothing there called a Litematica method with a vanilla type in its
  signature; task 2 is the first to touch `ISchematicRegion`/`ILitematicaBlockStateContainer`.
  Fixed in `build.gradle` with a new `remapLitematica` task that runs `SpecialSource`
  (`net.md-5:SpecialSource:1.8.3:shaded`, Maven Central — the exact tool FG uses internally to
  deobfuscate the vanilla jar itself) against the raw litemod using FG's own generated
  `notch-mcp.srg`, then forces the result onto the compile classpath via an `afterEvaluate`
  block that also strips any raw `.litemod` file from `compileClasspath` (the LiteLoader
  Gradle plugin puts the raw litemod there directly too, for `runClient`'s benefit, and it was
  silently shadowing the fix — javac resolves each class from the first matching classpath
  entry). Full writeup in `AGENTS.md` → Gotchas. **This was flagged before starting task 2 and
  fixed with the user's explicit go-ahead** (it's outside task 2's original scope but blocks
  tasks 2-5 entirely, since they all depend on Litematica schematic/GUI API surfaces with
  vanilla-typed signatures).
- **Design deviations from `docs/port-design.md` §4** (doc is prose/pseudocode, not verified
  hook points like §3.1/§3.3 — see task 2's own port-design.md caveat): lightmap handling
  doesn't set fixed `240,240` coordinates on the lightmap texture unit; it disables that
  texture unit entirely during the block draw (every block already tessellates against the
  constant `getCombinedLight` value from `SchematicBlockAccess`, so there is nothing
  meaningful to sample — disabling the unit is simpler than binding/managing the real dynamic
  lightmap texture and gives the same fully-lit result). VBO only, no display-list fallback
  (LWJGL2/OpenGL 2.1, which 1.12.2 already requires, guarantees VBO support).
- **Tile entity rendering is best-effort and unverified in-game.** `SchematicBlockAccess`
  builds each `TileEntity` via `TileEntity.create(null, tag)` (confirmed real signature via
  `javap` against the stable_39-mapped vanilla jar: `static TileEntity create(World,
  NBTTagCompound)`) since there is no real `World` to pass; `PreviewRenderer` renders via
  `TileEntityRendererDispatcher.instance.render(te, x, y, z, partialTicks)` wrapped in
  `try/catch (Throwable)` per tile entity, blacklisting the class on failure so a bad renderer
  doesn't retry every frame. Matches the `docs/port-design.md` §8 risk mitigation exactly, but
  which TE renderers actually tolerate a null world is untested pending `runClient`.
- **Reviewed with `/review` before marking this task done** (first review agent got cut off by
  a session restart mid-run with no salvageable partial output; re-ran fresh). Found one
  critical bug, fixed: `PreviewRenderer.drawLayer()` set up `glVertexPointer`/`glColorPointer`/
  `glTexCoordPointer` and called `vbo.drawArrays()` without ever calling
  `GlStateManager.glEnableClientState(...)` for `GL_VERTEX_ARRAY`/`GL_COLOR_ARRAY`/
  `GL_TEXTURE_COORD_ARRAY` first — malilib's own `VboRenderObject.draw()` (which this was
  modeled after) skips this too, but only because it always runs inside the constant world-
  render loop where those states are already on; a GUI widget draw has no such guarantee. As
  written this would have rendered a blank preview — exactly the class of bug the disclosed
  `runClient` network gap would hide from manual testing. Fixed by enabling the three states
  before the pointer setup. `./gradlew build` re-verified green after the fix (see above).
  No other correctness, requirement-gap, or security findings.
- **Real in-game crash found in task 3's testing, root cause lives here**: the `/review` fix
  above added the three `glEnableClientState` calls in the wrong place relative to
  `vbo.bindBuffer()` — `drawLayer()` called `glVertexPointer`/`glColorPointer`/
  `glTexCoordPointer` (which interpret their last argument as a byte offset into the
  *currently bound* `GL_ARRAY_BUFFER`, not a client-side pointer) *before* binding the VBO.
  Selecting any schematic in the browser (side panel preview, task 2) or showing a
  `LIST_PREVIEW`/tile row (task 3) both call this same method, and both crashed the game:
  `org.lwjgl.opengl.OpenGLException: Cannot use offsets when Array Buffer Object is disabled`.
  Reported directly by the user running the built litemod via `tools/test-in-game.sh`, with
  the full crash log pinpointing `PreviewRenderer.java:233`. Fixed by moving
  `vbo.bindBuffer()` to before the pointer setup, matching the established
  `VboRenderListSchematic.renderBlocks` pattern in the Litematica reference source (bind
  first, then set up pointers, then draw) that this code was supposed to mirror but didn't
  quite. `./gradlew build` re-verified green; confirmed clean relaunch with no crash in
  `latest.log` via `tools/test-in-game.sh` — **user should retry selecting a schematic now**.

---

## Task: Browser entry types (list preview + tile grid) and preview-type button

**Status:** done (`8b1650c`)

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
  directory still navigates. — **mod now loads in-game without crashing (see notes); the actual
  click-through checks (all 5 types, scrolling, search) still need a human at the keyboard**.
  Note: real behavior is single-click-to-navigate (confirmed in
  `DirectoryEntryWidget.onMouseClicked`), not double-click as originally worded above; unchanged
  by this task either way.
- `./gradlew build` exits 0 — **PASS**, see notes.

### Notes / findings

- **Build verified programmatically**, three times (once before `/review`, once after its
  `previewMaxVolume` finding was fixed, once after a real in-game crash was found and fixed —
  both below): `JAVA_HOME=.jdk-cache/jdk8u302-b08 ./gradlew build` → `BUILD SUCCESSFUL` each
  time. Confirmed all new classes packaged and `mixins.schematicpreview.json` lists
  `BaseSchematicBrowserScreenMixin`, `BaseFileBrowserWidgetAccessor`, `BaseListWidgetAccessor`
  alongside task 2's `SchematicInfoWidgetMixin`.
- **New: `tools/test-in-game.sh`, a real in-game test path that isn't blocked by this
  network's `runClient` asset-download problem** — installs the built litemod into an existing,
  already-set-up PrismLauncher instance (`~/.local/share/PrismLauncher/instances/1.12.2 test
  ai`) and launches it directly. Using it caught two real crashes `./gradlew build` could not,
  in two rounds (full story in `AGENTS.md` → Gotchas):
  1. `BaseSchematicBrowserScreenMixin` casting straight to `BaseListWidgetAccessor`/
     `BaseFileBrowserWidgetAccessor` inside its own injected method compiled fine but threw
     `InvalidMixinException` at **mod load** (the bundled Mixin 0.7.4 mistakes the accessor
     interface for a target-hierarchy alias when it's itself a registered mixin) — fatal, the
     mod never finished loading.
  2. The first fix (move the casts into a plain `mixin/BrowserWidgetAccessors` helper) loaded
     fine but crashed the moment the **user actually opened the Load Schematics screen**
     in-game (reported directly by the user, with the full crash log):
     `NoClassDefFoundError: ... BrowserWidgetAccessors is a mixin class and cannot be
     referenced directly` — the whole `dev.froyln.schematicpreview.mixin` package is Mixin's
     declared root package, so *anything* placed in it is excluded from normal classloading,
     not just `@Mixin`-annotated classes. Real fix: moved the helper to
     `gui/BrowserWidgetAccessors.java`, outside the mixin package entirely.
  **Confirmed working after both fixes**: relaunched via `tools/test-in-game.sh`, log shows
  `Successfully added mod SchematicPreview version 0.1.0`, clean boot to
  `Sound engine started` with zero `FATAL`/`Mixin apply failed`/`NoClassDefFoundError` lines,
  process stayed up. This proves the mod *loads*; the acceptance items themselves (5 preview
  types, scrolling, search) still need someone to actually open Load Schematics and look —
  **the user should retry that now that round 2 is fixed**.
- **Whoever picks up task 4**: run `tools/test-in-game.sh` (or `./gradlew runClient` on a
  normal network) and confirm every acceptance item above, plus the accepted gaps noted below.
- **No multi-column list layout exists anywhere in malilib** (confirmed by reading the real
  0.53.0 sources, extracted from the Gradle-cached sources jar since the `/tmp/ref` clones from
  earlier sessions were gone after a sandbox restart) — `TileEntryWidgetFactory` is a
  from-scratch `ListEntryWidgetFactory`, not an extension of anything. It can't call
  `BaseListWidget.createListEntryWidget` (protected, different package) so it's handed the same
  `DataListEntryWidgetFactory` lambda the widget's own default single-column path uses, and
  calls it directly with a manually-built `DataListEntryWidgetData`.
- **`/review` caught a real security-invariant violation, fixed**: the first pass set
  `showPreview` purely from `previewType.hasPreview()` and unconditionally nulled the file-type
  icon, meaning list/tile row previews ignored `Configs.Preview.PREVIEW_MAX_VOLUME` entirely —
  directly contradicting AGENTS.md's Security invariants ("previewMaxVolume gates list/tile
  previews") and this task's own step 2. Fixed by restructuring the fallback: the vanilla
  type icon is no longer cleared in the constructor; `PreviewCache.renderSmallPreview` now
  returns `false` (drawing nothing, leaving the icon visible underneath) whenever the schematic
  is still loading, failed to parse, **or exceeds `previewMaxVolume`** (checked via
  `schematic.getMetadata().getTotalVolume()`), and only draws over the icon once a preview is
  actually going to render. `./gradlew build` re-verified green after the fix.
- **Accepted, out-of-scope gaps** (both noted to the user before implementation, per the
  approved plan):
  - `BaseListWidget.updateScrollBarHeight()`'s per-widget-height branch indexes the flat
    entry-widget list by row number once tile mode has more than one row — this only affects
    the scrollbar thumb's rendered *size* (cosmetic); the actual scroll range comes from
    `clampScrollBarPosition()`, which correctly uses `TileEntryWidgetFactory`'s row-based
    `getTotalListWidgetCount()` independently, so scrolling itself is unaffected.
  - The preview-type button's position is computed once, right after the list widget is
    constructed, and won't track a live in-session game-window resize.
  - Directory rows/tiles never show a preview in this task (matches the existing split in
    AGENTS.md → Architecture: "directories with no custom icon show a small preview of their
    first schematic file" is task 4's job).

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

- Bootstrap the LiteLoader mod skeleton — done (`83d7812`), buildable/loadable mod shell:
  LiteMod entry, malilib configs (Generic/Menu/Preview) + hotkey + config screen, empty tick
  handler. `./gradlew build` verified from a clean tree; `runClient` in-game check still
  needed on a normal network (see task notes above).
- 3D preview renderer + side-panel preview — done (`0c12309`), live rotatable 3D schematic
  preview replacing the static thumbnail: `SchematicBlockAccess`/`PreviewRenderer`/
  `PreviewCache`/`PreviewWidget`/`PreviewFullscreenScreen` + `SchematicInfoWidgetMixin`. Also
  fixed a real ForgeGradle deobfuscation gap for the litematica dependency (see AGENTS.md →
  Gotchas). `./gradlew build` verified green (twice — once after a `/review`-caught missing
  `glEnableClientState` bug was fixed); `runClient` in-game check still needed on a normal
  network (same asset-CDN limitation as task 1).
- Browser entry types (list preview + tile grid) and preview-type button — done (`8b1650c`),
  `PreviewDirectoryEntryWidget` + from-scratch `TileEntryWidgetFactory` (malilib has no
  multi-column list layout to extend) wired via `BaseSchematicBrowserScreenMixin` +
  `BaseFileBrowserWidgetAccessor`/`BaseListWidgetAccessor`; row/tile previews render through a
  new shared FBO in `PreviewCache` rather than one per entry. `./gradlew build` verified green
  (twice — once after a `/review`-caught missing `previewMaxVolume` gate was fixed); `runClient`
  in-game check still needed on a normal network (same limitation as tasks 1-2).

## Dropped / deferred

- ModMenu integration — dropped, Fabric-only; LiteLoader's mod panel (`Configurable`) covers it.
- Translations beyond `en_us` — deferred until features stabilize.
