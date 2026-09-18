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
T1 → T2 → T3 → T4; T5 needs only T1; T6 (save buttons) needs T5; T7 (release) last.

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
- **"Bad quality" / "always wrong zoom and position" (user-reported after the fix above got
  the preview rendering at all): root cause was FBO transparency, not the camera.**
  Screenshots showed dark, oversized, out-of-place geometry filling most of the side-panel
  widget with only a small correctly-scaled part of the actual schematic visible in a corner —
  looked exactly like a broken camera, but the camera math (`getCenter()`/`getDefaultDistance()`/
  the orbit matrix) checked out fine on paper. Actual cause: `PreviewRenderer.draw()` cleared
  the FBO to alpha `0`, and `PreviewRenderUtils.blitFramebuffer` blits with blending on — so
  everywhere the FBO had no schematic geometry (i.e. most of the frame, for a small schematic
  viewed from a reasonable distance), the blit let the *live game world already on screen
  behind the widget* show through instead of a clean background. What looked like "the camera
  is somewhere else entirely" was literally a window into the real world. Fixed by clearing to
  an opaque color instead. `./gradlew build` green; confirmed clean relaunch via
  `tools/test-in-game.sh` — **user should check the preview again**; if the camera framing
  itself still looks off once the background is fixed, that's a separate, real issue to
  revisit (the math review above doesn't rule out a subtler bug, it just didn't find one).

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

**Status:** done (`3538b6b`)

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
  not save; deleting the JSON resets to defaults without errors. — **not verified in-game
  this session** (see notes; same `runClient` network gap as prior tasks). Code path reviewed
  and matches: `DirectoryIconStore.set`/`Item.getByNameOrId` gate blocks invalid ids before
  `applyValue()` returns true, so `DirectoryIconEditScreen` stays open and the text field's
  own validator shows the error inline; `DirectoryIconStore.load()` drops unknown item ids
  silently without rewriting the file; a missing/deleted JSON file parses to an empty store
  via `JsonUtils.parseJsonFile` with no exception.
- `./gradlew build` exits 0 — **PASS**, see notes.

### Notes / findings

- **Build verified programmatically**, twice (once before `/review`, once after its
  box-geometry finding was fixed): `JAVA_HOME=.jdk-cache/jdk8u302-b08 ./gradlew build` →
  `BUILD SUCCESSFUL` both times.
- Reused malilib's existing `BaseTextInputScreen` for `DirectoryIconEditScreen` instead of a
  bespoke screen (text field + OK/Reset/Cancel + validator/error-message wiring already
  built in), and its `TextFieldValidator` for live "unknown item id" feedback. Reused
  `Item.getByNameOrId` (vanilla) instead of hand-parsing `ResourceLocation`s against
  `Item.REGISTRY`.
- `BaseFileBrowserWidget.getFileFilter()` turned out to be `protected`, not public as assumed
  from a first read of the malilib source — added a `fileFilter` field accessor to the
  existing `BaseFileBrowserWidgetAccessor` mixin (alongside the task 3 `navigationWidget`
  one) and a matching static getter in `gui/BrowserWidgetAccessors` (the existing
  outside-the-mixin-package helper class from task 3's Gotchas fix), rather than
  reimplementing the schematic-extension list by hand — directory rows now find their first
  schematic file using the exact same filter Litematica configured the browser with.
- **`/review` caught a real correctness bug, fixed**: the `CENTER` icon position sized its
  box as the *entire row width* (`this.getWidth()`) in `LIST`/`LIST_PREVIEW` mode, not the
  `previewSize` square that `textOffset` was actually reserved for (and that
  `renderPreviewBox` draws schematic previews into) — the icon rendered centered in the
  middle of a much wider row, disconnected from its own icon column, most visible in the
  default `LIST` view. Fixed by giving the `CENTER` branch the same box geometry
  (`getHeight() - PREVIEW_PADDING * 2` square, offset by `PREVIEW_PADDING`) as
  `renderPreviewBox`'s own non-tile branch. `./gradlew build` re-verified green after the fix.
  Two non-blocking notes from the same review, not acted on: `findFirstSchematic` does a
  synchronous `Files.list` per directory row on the GUI thread (fine at normal schematic-folder
  sizes, could hitch on a folder with very many subdirectories); right-clicking specifically
  over a directory's icon area now opens `DirectoryIconEditScreen` instead of the vanilla
  context menu for that hit region (right-click elsewhere on the row is unaffected) — matches
  the new hover tooltip and is the intended behavior.
- **Whoever picks up task 5**: run `tools/test-in-game.sh` (or `./gradlew runClient` on a
  normal network) and confirm the acceptance items above plus the position-cycle rendering
  (`default`/`center`/`default_with_schematic`) actually looks right in all five preview
  types — this session could not verify any of it visually (same network limitation as tasks
  1-3).

---

## Task: "Replace" button in the material list

**Status:** done (`bb244dd`)

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

- **Build verified programmatically:** `JAVA_HOME=.jdk-cache/jdk8u302-b08 ./gradlew build` →
  `BUILD SUCCESSFUL`, exit 0. Unzipped the litemod and confirmed all 7 new classes packaged
  (`BlockReplacer`, `MaterialListAccessors`, `ReplaceMaterialListEntryWidget`,
  `BlockSelectScreen` + its nested entry widget, `MaterialListScreenMixin`,
  `MaterialListSchematicAccessor`, `MaterialListPlacementAccessor`) and
  `mixins.schematicpreview.json` lists the three new mixins alongside the existing four.
- **Reviewed with `/review` before marking this task done.** Verdict: ship, no correctness,
  security, or scope-creep findings. Verified against the actual litematica/malilib reference
  sources: `MaterialListScreenMixin` shadows a genuinely `protected` field and injects at the
  right point; the two new accessor mixins match real private field names (`schematic`,
  `regions` on `MaterialListSchematic`; `placement` on `MaterialListPlacement`); `BlockReplacer`
  correctly scopes to the passed `regionNames` (respecting `MaterialListSchematic`'s own
  sub-region filter when the list was created for a subset via shift-click), only adjusts
  `SchematicMetadata.totalBlocks` on air↔non-air transitions, calls `setTimeModifiedToNow`/
  `setModifiedSinceSaved`, and calls the real `markAllPlacementsOfSchematicForRebuild`; the
  Replace button installs only for `MaterialListSchematic`/`MaterialListPlacement`, correctly
  excluding the area analyzer; picking Replace on a material-list row whose item has no block
  form (`Block.getBlockFromItem` returns air) shows an error instead of opening the picker.
- Accessor casts to `MaterialListSchematicAccessor`/`MaterialListPlacementAccessor` live in a
  new plain helper, `materials/MaterialListAccessors`, outside the mixin package — same pattern
  as task 3's `gui/BrowserWidgetAccessors`, for the same reason (see AGENTS.md → Gotchas).
- Reused malilib's `DataListWidget<Block>` + `addDefaultSearchBar()` for `BlockSelectScreen`
  instead of a bespoke picker (same pattern `MaterialListScreen` itself uses for its own list),
  and `MaterialListEntryWidget`'s existing protected `ignoreButton`/`materialList` fields to
  place the Replace button and reach the backing list without a new accessor.
- **Real in-game bugs found by the user after this landed, fixed in `fc14b5e`**: (1) the block
  picker (`BlockSelectScreen`) listed `Block.REGISTRY` directly, one row per Java `Block` class -
  since most vanilla variant families (slabs, wool, wood, stone, sand, sandstone, prismarine,
  quartz block, ...) are a single `Block` with a metadata property, only that block's *default*
  variant ever appeared (e.g. "Stone Slab", never "Quartz Slab"), so most of what the user
  expected to pick from was simply missing from the list; (2) `BlockReplacer` matched/replaced
  at the `Block` granularity too, so replacing one slab variant would have matched *every*
  variant of that slab block, and - because vanilla slabs additionally pack their placement-only
  top/bottom half into the raw state metadata (needed for the old single-nibble world storage
  format) - copying "shared properties" naively would have discarded that half on any variant
  swap. Root-cause fix, not two patches: switched to `Block.getSubBlocks()` for listing (every
  real placeable `(Block, metadata)` pair, not just one per class) and to
  `Block.damageDropped(state)` for both matching and identity (the same "what item does this
  state require" mapping the material list itself already uses to build its rows), which
  generically separates "placement/orientation properties" (safe to copy from the old block,
  e.g. slab half, stair facing) from "identity properties" (must come from the picked variant,
  e.g. slab stone/quartz type) with no per-block special-casing. `./gradlew build` re-verified
  green after the fix; confirmed clean relaunch via `tools/test-in-game.sh` (mod loads, no
  `FATAL`/`Mixin apply failed`/`NoClassDefFoundError`) - **user should retry replacing a mixed
  up/down slab with a different slab type now**.
- **Known remaining limitation, not fixed**: `damageDropped` won't line up with the row's item
  for the handful of vanilla blocks whose "required build item" isn't a simple 1:1 function of
  their own state (beds, banners, and anything `MaterialCache.requiresMultipleItems` treats
  specially) - out of scope for what was asked (stone/wool/slab/wood-style variant families),
  and no worse than the block-granularity bug it replaces.
- **Whoever picks up task 6**: run `tools/test-in-game.sh` (or `./gradlew runClient` on a
  normal network) and confirm this task's acceptance items — replacing stone with dirt shows
  the count message, the list refreshes, the placement re-renders with dirt, Schematic Manager
  shows the schematic as modified, saving writes dirt, a stairs→stairs replace keeps
  `facing`/`half`, and a slab→slab replace keeps top/bottom half — this session confirmed only
  that the mod loads cleanly after both the feature and the fix above; the user should retry the
  actual replace behavior now that the picker/replace granularity bug is fixed.
---

## Task: Save / Save as buttons in the material list

**Status:** done (`665e3b6`)

Finding (2026-09-16): "replace without loading" already works — Litematica's schematic browser
has a `Material list` button (`SchematicBrowserScreen.createMaterialList`) that reads the file
into a fresh `ISchematic` (not added to `SchematicHolder`, no placement) and opens
`MaterialListScreen` over a `MaterialListSchematic`, where task 5's Replace rows already show.
What is missing: a way to write the replaced result back to disk without loading/placing it.
Add two buttons to `MaterialListScreen`, next to `Export`, for schematic-backed lists whose
schematic has a file (`schematic.getFile() != null`):

- `Save` — overwrite the source file after a `ConfirmActionScreen` (malilib) naming the file.
  On success: `metadata.clearModifiedSinceSaved()`, `PreviewCache.invalidate(path)` (the
  browser's cached preview for that path is now stale), success message.
- `Save as` — `TextInputScreen` (malilib) pre-filled with `<stem>_replaced`, writes
  `schematic.writeToFile(sourceDir, name, false)`; Litematica appends the extension and
  refuses an existing name with its own error (the input screen stays open, return the
  write result as the consumer's boolean). In-memory schematic keeps pointing at the
  original file; modified flag untouched.

The same screen class serves the Loaded Schematics → Material list flow, so the buttons appear
there too (no way to tell the entry points apart, and overwriting a loaded schematic's file is
the same operation). Placement-backed lists (`MaterialListPlacement`) are not covered — not
asked for; add later by widening the `instanceof` gate if wanted.

### Files to read

- Litematica `gui/MaterialListScreen.java` (`reAddActiveWidgets`, `updateWidgetPositions`,
  protected `exportButton`; second button row is at `this.y + 39`),
  `schematic/ISchematic.java` (`writeToFile(Path dir, String name, boolean override)`,
  `writeToFile(Path, boolean)` — shows its own `MessageDispatcher` errors),
  `schematic/SchematicMetadata.java` (`clearModifiedSinceSaved`),
  `gui/SchematicBrowserScreen.java` (`createMaterialList` — the existing entry point).
- MaLiLib `gui/ConfirmActionScreen.java`, `gui/TextInputScreen.java`
  (`ResultingStringConsumer` returns whether to close), `gui/SchematicVcsProjectManagerScreen`
  for the confirm-screen usage pattern.
- Ours: `mixin/MaterialListScreenMixin.java`, `materials/MaterialListAccessors.java`,
  `render/PreviewCache.java`.

### Steps

1. `render/PreviewCache.invalidate(Path)`: close + remove the renderer and drop the schematic
   future for that path (runs on the client thread, same as `close()`).
2. `materials/SchematicSaver.java` (plain class, outside the mixin package — see AGENTS.md
   Gotchas): `save(ISchematic)` opens the confirm screen (parent = current screen) and on
   confirm does the overwrite + post-save steps above; `saveAs(ISchematic)` opens the text
   input screen with the default name and writes with `override = false`.
3. `mixin/MaterialListScreenMixin`: `@Shadow exportButton`; two mixin-owned `GenericButton`
   fields created lazily; `@Inject(at = TAIL, remap = false)` into `reAddActiveWidgets`
   (add both when `materialList instanceof MaterialListSchematic` and its schematic has a
   file) and `updateWidgetPositions` (`save.setPosition(exportButton.getRight() + 2,
   exportButton.getY())`, `saveAs` right of it). Clicks call `SchematicSaver`.
4. `en_us.lang`: `schematicpreview.gui.save_schematic`, `.save_schematic_as`,
   `.save_schematic.confirm_title`, `.save_schematic.confirm_message` (`%s` = file name),
   `.save_schematic_as.title`, `schematicpreview.message.schematic_saved` (`%s` = path).
5. `AGENTS.md`: add `SchematicSaver` to the tree and one sentence under Architecture → Replace.

### Acceptance

- Litematica browser → select a schematic → `Material list` → Replace stone with dirt →
  `Save as` → default name `<name>_replaced`, confirm → new file appears in the browser on
  return (browser refreshes on reopen), its preview shows dirt; original file unchanged.
- Same flow → `Save` → confirm dialog → original file now contains dirt; the browser's
  preview for it shows dirt (cache invalidated); no "modified" mark on a loaded copy.
- `Save as` with an existing name shows Litematica's "exists" error and keeps the input open.
- Buttons absent on area-analyzer and placement material lists and for schematics with no file.
- `./gradlew build` exits 0.

### Notes / findings

- **Build verified programmatically:** `JAVA_HOME=.jdk-cache/jdk8u302-b08 ./gradlew compileJava`
  and `./gradlew build` both `BUILD SUCCESSFUL`, exit 0. Unzipped the litemod and confirmed
  `SchematicSaver.class`, the rebuilt `MaterialListScreenMixin.class`, and the rebuilt
  `PreviewCache.class` are packaged.
- **Reviewed with `/review` before marking this task done.** Verdict: ship, no findings.
  Confirmed against the real sources that `MaterialListScreen → BaseListScreen →
  BaseTabbedScreen → BaseScreen` makes `extends BaseScreen` a valid shadow-free route to
  `addWidget` (same trick as `SchematicInfoWidgetMixin extends ContainerWidget`), that
  `MaterialListSchematic.schematic` is `final` and non-null so the savable-schematic gate can't
  NPE, and that the `writeToFile`/`ConfirmActionScreen`/`TextInputScreen` call shapes match the
  real APIs.
- Implemented via `MaterialListScreenMixin` gains (not a separate mixin): TAIL injects into
  `reAddActiveWidgets`/`updateWidgetPositions` add and position the two buttons; a `@Unique`
  private helper computes the savable `ISchematic` (or `null`) once per check, reusing
  `MaterialListAccessors.getSchematic` from task 5 rather than a new accessor.
- **Real in-game bug found by the user right after this landed, fixed in `ae3642b`**: the
  Save confirm dialog rendered as a black box hanging off the bottom/right screen edges with
  no buttons. Not our code and not the GUI scale (user tried) — malilib **0.54** (the test
  instance's version; we compile against 0.53) resizes every popup to the window on open
  unless a new `useWindowDimensions` flag is cleared, which its own `ConfirmActionScreen`/
  `BaseTextInputScreen` never do. Diagnosed with a temporary tick-handler log of the live
  screen (`280x80` after construction → `938x503` once open), then removed. Fix:
  `gui/PopupScreenCompat.keepPopupSize()` clears the flag reflectively where it exists, applied
  to the Save confirm, the Save-as input and task 4's `DirectoryIconEditScreen` (same base
  class). Full story in AGENTS.md → Gotchas.
- **Verified in-game by the user after the fix** ("now works perfect"): Save overwrite flow
  works end to end on the 0.54 instance. Save-as, cache invalidation and the no-button gating
  on area-analyzer/placement lists were not separately confirmed — should be spot-checked in
  the task 7 fresh-profile test.

---

## Task: Polish and first release

**Status:** done (`5c419d0`)

Translations complete (`en_us.lang`), icon textures for the overlay buttons (own drawings,
not copied from the original), README with screenshots, LICENSE (pick one — LGPL-3.0 keeps
it compatible with Litematica/MaLiLib), `mod_version = 0.1.0`, build artifact tested in a
real LiteLoader 1.12.2 profile with Litematica 0.31.4 + MaLiLib 0.53.0.

### Acceptance

- Fresh MC 1.12.2 + LiteLoader install with the three `.litemod` files: no errors in
  `latest.log`, all features of the previous tasks work.

### Notes / findings

- Done in this session (2026-09-16/17): overlay/toolbar icons (`7cbb377` — `tools/gen-icons.py`
  draws a 64x64 sheet, `gui/SchematicPreviewIcons` exposes five `BaseMultiIcon`s with
  disabled/normal/hovered variants; the fullscreen/freecam corner buttons, the fullscreen
  Save/Copy buttons and the browser preview-type button use them), LICENSE = LGPL-3.0 verbatim
  text (`23e772a`, user's choice), README rewritten for release (features, usage per feature,
  screenshot table pointing at `docs/images/side-panel.png` / `tiles.png` / `replace.png`,
  malilib 0.53 or 0.54). `mod_version` was already `0.1.0`. Lang audit: every
  `schematicpreview.*` key referenced in code exists in `en_us.lang`.
- Screenshots added by the user (`acbd3f7`): side panel, fullscreen, material list — taken
  with the pre-icon build, so they show the old letter buttons; a tile-layout shot is still
  wanted (`docs/images/tiles.png`, README has the slot commented).
- **Verified by the user in the `1.12.2 test ai` instance after relaunch** ("icons work"):
  icons render, previous features intact. Not a literally fresh profile — that instance has
  ~15 other LiteLoader mods and malilib 0.54 — but it is the real-world target; the user
  chose to close the task on that. Icon commit reviewed with `/review` (verdict: ship).

---

## Task: Apply ponytail-audit cuts

**Status:** in progress (branch `refactor/ponytail-audit`)

Pure deletions/refactors from a `/ponytail-audit` pass, each verified equivalent against
malilib 0.53 bytecode (`javap` on the deobf jar) before being applied. **No UI, layout,
file-name or feature change is allowed** — that is the acceptance bar, not "less code".

### Steps (one commit each, `./gradlew build` between)

1. Dead code: `Generic.HOTKEYS`, `PreviewType.isList()`, `PreviewWidget.setPath`/`fboScale`,
   unreachable `renderer == null` guards, default `Redirect.PIPE` — `65f43f9`.
2. Fold `SchematicPreviewTickHandler` (lambda; `ClientTickHandler`'s other two methods are
   defaults) and `SchematicPreviewHotkeyProvider` (anonymous class) into `InitHandler`;
   drop `ConfigScreen.getConfigTabs()` wrapper — `9a1279c`.
3. `FileNameUtils.getFileNameWithoutExtension` / `getDateTimeString` (same
   `yyyy-MM-dd_HH-mm-ss` pattern) in `ScreenshotUtil.save` and `SchematicSaver.saveAs` —
   `3cbbb1c`.
4. Shrinks: mixin list-height ternary → `type.getHeight(0)`, single `PreviewRenderer.draw`,
   inline thread factory, one `DirectoryIconStore.tickSave`, `previewBox()` helper in the
   entry widget, plain method names in `PreviewFullscreenScreen` — `0d59242`.
5. `build.gradle`: drop the Forge-template `processResources` block (no `mcmod.info`/`.xcf`
   exist); litemod resource entries diffed identical — `682bb3b`.

### Acceptance

- `./gradlew build` exit 0 after every step.
- Bug-hunt fixes (added after the audit, user-approved): a schematic over `previewMaxBlocks`
  shows "Too large to preview" in the side panel, a spawner schematic leaves the GUI intact,
  Replace chest→stone / stone→chest previews and saves correctly, Replace on a door /
  redstone dust / repeater row works, `enabled=false` removes every addon element, a
  directory icon edit lands in `schematicpreview_icons.json` immediately.
- `tools/test-in-game.sh`: every acceptance item of tasks 2–7 still passes — list /
  list-preview / tile-5/4/3 row heights, side panel + fullscreen, screenshot file name keeps
  the schematic's casing, directory icon edit in all three positions, Replace + Save / Save as
  popups sized correctly, config screen tabs, no GL/lighting leak after closing screens.

### Notes / findings

- **Rejected on inspection, do not re-propose:**
  - `src/main/resources/litemod.json` looks redundant next to the `litemod{}` DSL but is the
    same static stub malilib's own jar ships — LiteLoader in dev (`runClient`) reads
    `mixinConfigs` from it on the classpath; the DSL output only lands in the packed litemod.
  - `FileNameUtils.generateSimpleSafeFileName` for the screenshot name: it lowercases
    (`[^a-z0-9_.-]`), so `MyFarm` would become `myfarm`. Own `replaceAll` kept.
- Net -110 lines, 0 dependency changes.
- Bug hunt afterwards (whole tree, before in-game testing) — fixed on the same branch:
  `previewMaxBlocks` cap so the side panel can't OOM direct memory on multi-million block
  builds (`e06b421`); modelview stack unwind + static blacklist when a TESR throws (mob
  spawners NPE on the null world after two pushes, which broke the rest of the GUI frame)
  (`e3177a7`); Replace now drops/creates tile-entity NBT and pending ticks at changed
  positions. Found but **not** fixed (user's call, see session notes): `Generic.enabled` only
  gates the side panel; Replace can't target item-placed blocks (doors, redstone dust,
  repeaters...) since `MaterialListEntry` only carries the ItemStack; `wl-copy` process not
  destroyed on timeout; `Files.list` per directory row per widget rebuild; icon store only
  saved once no screen is open.

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

- Custom directory icons — done (`3538b6b`), `DirectoryIconStore` (Gson-backed
  `config/schematicpreview_icons.json`) + `IconPosition` + `DirectoryIconEditScreen`
  (built on malilib's `BaseTextInputScreen`), wired into `PreviewDirectoryEntryWidget` via a
  new `fileFilter` accessor on `BaseFileBrowserWidgetAccessor`. `./gradlew build` verified
  green (twice — once after a `/review`-caught `CENTER`-position box-sizing bug was fixed);
  `runClient`/in-game check still needed on a normal network (same limitation as tasks 1-3).

- "Replace" button in the material list — done (`bb244dd`), `BlockReplacer` +
  `MaterialListAccessors` + `BlockSelectScreen` + `ReplaceMaterialListEntryWidget`, wired into
  schematic- and placement-backed material lists via `MaterialListScreenMixin` +
  `MaterialListSchematicAccessor`/`MaterialListPlacementAccessor` (area analyzer lists
  unaffected). `./gradlew build` verified green; reviewed with `/review` (verdict: ship, no
  findings); `runClient`/in-game check still needed on a normal network (same limitation as
  tasks 1-4).

- Save / Save as buttons in the material list — done (`665e3b6`), `SchematicSaver` +
  `PreviewCache.invalidate` wired into `MaterialListScreenMixin`, letting a schematic read
  straight from disk via `SchematicBrowserScreen`'s "Material list" button (no load, no
  placement) have its Replace edits written back without loading it. `./gradlew build` verified
  green; reviewed with `/review` (verdict: ship, no findings); Save flow verified in-game by the
  user after the malilib-0.54 popup fix `ae3642b` (`PopupScreenCompat`).

- Polish and first release — done (`5c419d0`), icon sheet + `SchematicPreviewIcons` for the
  overlay/toolbar buttons, LGPL-3.0 `LICENSE`, release README with usage and screenshots,
  `mod_version` 0.1.0. Verified by the user in the test instance (icons + features work);
  `/review` on the icon commit: ship.

## Dropped / deferred

- ModMenu integration — dropped, Fabric-only; LiteLoader's mod panel (`Configurable`) covers it.
- Translations beyond `en_us` — deferred until features stabilize.
