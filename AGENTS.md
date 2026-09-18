# SchematicPreview (LiteLoader 1.12.2)

Client-side Litematica addon for Minecraft 1.12.2 on LiteLoader. It re-implements, from
scratch, the features of the Fabric mod
[DimasKama/SchematicPreview](https://github.com/DimasKama/SchematicPreview): live 3D
previews of schematics in the Litematica schematic browser (side panel + per-entry
thumbnails in list/tile layouts, fullscreen + free camera), custom item icons for
directories, and a "Replace" button in the material list that swaps every block of one
type inside a schematic. Used by players who still run 1.12.2 Litematica (0.31.4) and
want the modern browser experience.

**The original is "All rights reserved". This project is a clean-room reimplementation:
port the behavior, never copy its source. The reference clone lives outside the repo and
is for reading only.**

> AI agents: `CLAUDE.md` imports this file and adds the working agreement. Task state is in
> `PLAN.md`. Path-specific rules are in `.claude/rules/`. The detailed port design (verified
> hook points, render pipeline, build setup, risks) is `docs/port-design.md`.

## Tech stack

- **Language:** Java 8 (`sourceCompatibility = 1.8`; no records, `var`, switch patterns,
  `List.of`, `Path.of` — the ForgeGradle 2.3 toolchain compiles with JDK 8).
- **Framework / runtime:** Minecraft 1.12.2 + LiteLoader 1.12.2 (`com.mumfrey.liteloader.LiteMod`),
  MCP mappings `stable_39`, LWJGL 2, SpongePowered Mixin 0.7.x (bundled with LiteLoader).
- **Package manager:** Gradle 2.14.1 wrapper + ForgeGradle 2.3-SNAPSHOT (`net.minecraftforge.gradle.liteloader`
  plugin) + MixinGradle 0.6-SNAPSHOT. Same setup Litematica/MaLiLib 1.12.2 use.
- **Key dependencies:**
  - `malilib-liteloader-1.12.2` **0.53.0** (`fi.dy.masa.malilib.*`, from `https://masa.dy.fi/maven`,
    `:deobf` classifier). Provides config system, hotkeys, the widget/screen framework and
    the file browser the addon extends.
  - `litematica-liteloader-1.12.2` **0.31.4** (`fi.dy.masa.litematica.*`). Not on any Maven —
    the `.litemod` goes in `libs/` (flatDir), remapped notch→MCP by the `remapLitematica` Gradle
    task (see Gotchas) and put on the compile classpath as a plain jar — **not** `deobfCompile`,
    which doesn't work for this dependency. Or build it from source at `maruohon/litematica`
    commit `1db931a6` and `publishToMavenLocal`. This is the post-rewrite Litematica
    (`BaseSchematicBrowserScreen`, `SchematicInfoWidget`, `MaterialListEntryWidget`), NOT the
    2020 `GuiSchematicBrowserBase`/`WidgetSchematicBrowser` API of older builds.
  - `SpecialSource` **1.8.3:shaded** (`net.md-5:SpecialSource`, Maven Central, build-time only).
    The notch→MCP remapping tool `remapLitematica` runs to fix the litematica dependency above;
    the same tool ForgeGradle uses internally for the vanilla jar. Never shipped in the litemod.
- **Datastore / external services:** none. Two JSON files in the MC `config/` dir (see Configuration).

## Commands

```bash
python3 tools/setup-build-deps.py         # once: fetch pinned JDK 8 + repack litematica dependency
export JAVA_HOME=.jdk-cache/jdk8u302-b08 # JDK 8; use the pinned 8u302 (see Gotchas), not system JDK 8
./gradlew build                 # build → build/libs/schematicpreview-liteloader-1.12.2-<ver>.litemod
./gradlew runClient             # run MC 1.12.2 dev client from ./minecraft (LiteLoader tweaker)
./gradlew compileJava           # fast check while iterating
tools/test-in-game.sh           # alternative to runClient - see Verification below
```

No unit tests. `compileJava` is the smallest check; `build` also runs mixin refmap generation
and packs the litemod. First run downloads and deobfuscates Minecraft 1.12.2 - slow, needs the
`org.gradle.jvmargs=-Xmx3G` in `gradle.properties`.

## Verification

The check that must pass before any change is called done:

```bash
./gradlew build      # must exit 0 with JAVA_HOME pointing at JDK 8
```

Then verify by hand: the acceptance items of the current `PLAN.md` task describe what to look
at. There are no automated tests for rendering. Two ways to get an in-game session:

- `./gradlew runClient` — the dev client, from `./minecraft` (LiteLoader mods menu → the mod is
  listed; open Litematica → Load Schematics). On this network its first-run asset download is
  unreliable (`resources.download.minecraft.net` returns HTTP 400 for most sound assets — a
  CDN/network issue, not this project's bug).
- `./gradlew build && tools/test-in-game.sh` — copies the freshly built litemod into the
  existing PrismLauncher instance at `~/.local/share/PrismLauncher/instances/1.12.2 test ai`
  (already has LiteLoader 1.12.2, litematica, and malilib installed, assets already downloaded,
  a folder of real schematics under `minecraft/schematics/`) and launches straight into it,
  offline, via `prismlauncher --launch`. Preferred when `runClient` stalls. That instance's
  malilib is **0.54.0**, one minor version ahead of this project's pinned `0.53.0` — noted as a
  possible source of divergence if something behaves differently there than in `runClient`, but
  **it does matter for popup screens** — see Gotchas, `PopupScreenCompat`.

This command is also what `.claude/hooks/verify-on-stop.sh` runs when enabled, and the
default `/goal` condition: "`./gradlew build` exits 0".

## Project structure

All seven PLAN.md tasks are done (first release 0.1.0). The `[task N]` tags below only record
which task introduced a file:

```
build.gradle / build.properties / settings.gradle / gradle.properties  # FG 2.3 liteloader build
tools/setup-build-deps.py         # fetches the pinned JDK + repacks the litematica dependency
tools/gen-icons.py                # draws textures/gui/icons.png (own pixel art); re-run after editing glyphs
libs/                             # litematica-liteloader-1.12.2-0.31.4.litemod (gitignored, fetched)
.jdk-cache/                       # pinned JDK 8u302 for the build (gitignored, fetched on demand)
src/main/java/dev/froyln/schematicpreview/
├── LiteModSchematicPreview.java  # LiteMod entry: registers InitHandler with malilib          [done]
├── Reference.java                # MOD_ID, MOD_NAME, MOD_VERSION (@MOD_VERSION@ replaced)      [done]
├── InitHandler.java              # malilib InitializationHandler: configs, hotkey provider, tick handler (all inline) [done]
├── config/Configs.java           # BooleanConfig/IntegerConfig/DoubleConfig/OptionListConfig    [done]
├── config/ConfigScreen.java      # BaseConfigScreen with tabs Generic / Menu / Preview / Hotkeys [done]
├── config/PreviewType.java       # LIST, LIST_PREVIEW, TILE_5, TILE_4, TILE_3 (OptionListConfigValue) [done]
├── config/SchematicPreviewConfigPanel.java  # RedirectingConfigPanel for LiteLoader's mod panel  [done]
├── data/DirectoryIconStore.java  # icons JSON (path → item id + position), dirty flag, save on exit  [task 4]
├── gui/PreviewWidget.java        # malilib widget: FBO preview, drag-rotate, scroll-zoom, fullscreen/freecam [done]
├── gui/PreviewFullscreenScreen.java  [done]
├── gui/DirectoryIconEditScreen.java  [task 4]
├── gui/BlockSelectScreen.java    # searchable block list for the Replace feature  [task 5]
├── gui/PopupScreenCompat.java    # keeps malilib popups popup-sized on malilib 0.54+ (see Gotchas)  [task 6]
├── gui/SchematicPreviewIcons.java  # BaseMultiIcons over textures/gui/icons.png (overlay/toolbar buttons)  [task 7]
├── gui/PreviewDirectoryEntryWidget.java  # replaces malilib DirectoryEntryWidget  [done]
├── gui/TileEntryWidgetFactory.java       # ListEntryWidgetFactory: N columns grid layout  [done]
├── gui/ReplaceMaterialListEntryWidget.java  # MaterialListEntryWidget + Replace button  [task 5]
├── render/SchematicBlockAccess.java      # IBlockAccess over ISchematic regions, full brightness  [done]
├── render/PreviewRenderer.java           # tessellate per BlockRenderLayer → VertexBuffer, Framebuffer  [done]
├── render/PreviewCache.java              # Path → loaded ISchematic (async) + tessellated renderers + shared small-preview FBO  [done]
├── render/PreviewRenderUtils.java        # shared FBO-blit/placeholder helpers (PreviewWidget + PreviewCache)  [done]
├── materials/BlockReplacer.java          # replace block in all region containers  [task 5]
├── materials/SchematicSaver.java         # Save/Save as a schematic read for a material list  [task 6]
└── mixin/                                # SchematicInfoWidgetMixin, BaseSchematicBrowserScreenMixin,
                                          # BaseFileBrowserWidgetAccessor, BaseListWidgetAccessor [done],
                                          # MaterialListScreenMixin, MaterialListSchematicAccessor,
                                          # MaterialListPlacementAccessor  [task 5]
src/main/resources/
├── litemod.json                  # static placeholder; the real one is generated by build.gradle's litemod{} DSL  [done]
├── mixins.schematicpreview.json  # package, compatibilityLevel JAVA_8, refmap, empty client[] until task 2  [done]
├── assets/schematicpreview/lang/en_us.lang   # 1.12 .lang format (key=value), not JSON      [done]
└── assets/schematicpreview/textures/gui/icons.png  # 64x64 icon sheet, generated by tools/gen-icons.py  [task 7]
```

## Architecture

Entry: LiteLoader instantiates `LiteModSchematicPreview` and calls `init()`. It registers an
`InitializationHandler` with `Registry.INITIALIZATION_DISPATCHER` (malilib) — everything else
(config handler via `JsonModConfig`, `HotkeyProvider`, `ClientTickHandler`, config screen
factory) is registered inside that callback, exactly like Litematica's `InitHandler`.

One operation end to end — the user selects a schematic in Litematica's browser:

1. `BaseSchematicBrowserScreen.createListWidget()` built a `BaseFileBrowserWidget`. Our mixin
   (TAIL) swaps its `DataListEntryWidgetFactory` for `PreviewDirectoryEntryWidget` and, for
   tile types, its `ListEntryWidgetFactory` for `TileEntryWidgetFactory` (N columns; the
   factory owns `getTotalListWidgetCount()` so the scrollbar stays correct).
2. Selection → `SchematicInfoWidget.onSelectionChange(entry)`. Our mixin adds a `PreviewWidget`
   below the metadata label instead of the stored 2D thumbnail `IconWidget`.
3. `PreviewWidget.renderAt()` asks `PreviewCache.getSchematic(path)`; loading happens off-thread
   via `SchematicType.tryCreateSchematicFrom(file)` (a `CompletableFuture`), result is joined
   only when done.
4. First render of a new schematic: `PreviewRenderer.setup(schematic)` wraps it in
   `SchematicBlockAccess` (an `IBlockAccess` reading `ISchematicRegion.getBlockStateContainer()`
   at region offsets, `getCombinedLight` = 0xF000F0, biome = plains), then walks every block
   and calls `BlockRendererDispatcher.renderBlock(state, pos, access, bufferBuilder)` for the
   layer `block.canRenderInLayer(state, layer)`; fluids go through the same call. Each layer's
   `BufferBuilder` is uploaded to a `VertexBuffer` (VBO). Tessellation runs on the client
   thread only (vanilla 1.12 `BlockRendererDispatcher` is not thread-safe) and is chunked
   across ticks when volume is large.
5. Every frame: bind a `net.minecraft.client.shader.Framebuffer` sized to the widget
   (scaled by `ScaledResolution` factor), `GlStateManager` perspective projection with config
   FOV, camera = orbit around schematic center at `(yRot, xRot, distance)`, draw the layer
   VBOs SOLID → CUTOUT_MIPPED → CUTOUT → TRANSLUCENT (translucent last, depth-mask off),
   optionally `TileEntityRendererDispatcher` for tile entities when `renderTileEntities` is on,
   then unbind, restore the MC framebuffer, and blit the FBO texture as a textured quad at the
   widget rect. GL state must be fully restored — the rest of the GUI renders after us.
6. `ClientTickHandler`: `PreviewCache.tickClose()` frees VBOs/FBOs/futures when
   `Minecraft.currentScreen == null`.

Directory icons: `PreviewDirectoryEntryWidget` right-click (on the icon area) opens
`DirectoryIconEditScreen`; result stored in `DirectoryIconStore` keyed by the directory's
absolute path with `/` separators; rendered with `RenderItem.renderItemAndEffectIntoGUI`.
Directories with no custom icon show a small preview of their first schematic file.

Replace: `MaterialListScreenMixin` (RETURN of `createListWidget`) swaps the entry widget
factory for `ReplaceMaterialListEntryWidget` when
`materialList instanceof MaterialListSchematic || MaterialListPlacement`; accessor mixins expose
the private `schematic`/`regions`/`placement` fields. `BlockReplacer` iterates every region
container (`getBlockState`/`setBlockState`), copies properties both blocks share, fixes
`SchematicMetadata.totalBlocks` for air↔non-air, `setTimeModifiedToNow()`,
`setModifiedSinceSaved()`, then marks all placements of that schematic for chunk rebuild via
`DataManager.getSchematicPlacementManager()`. Save: the same mixin adds Save/Save as buttons
next to `MaterialListScreen`'s Export button, for `MaterialListSchematic` lists whose schematic
has a file — covers `SchematicBrowserScreen`'s "Material list" button, which reads a schematic
straight from disk with no load/placement, so Replace edits there had no way back to disk;
`SchematicSaver.save` overwrites after a confirm screen and calls `PreviewCache.invalidate` so
the browser's cached preview isn't stale, `saveAs` writes a new file via Litematica's own
`ISchematic.writeToFile(dir, name, false)` (refuses an existing name itself).

State lives in: `Configs` statics (malilib persists them), `DirectoryIconStore` (static map +
dirty flag), `PreviewCache` (static, GL resources — must be released on the render thread).

## Configuration

- **Config file(s):** `config/schematicpreview.json` — written by malilib `JsonModConfig`
  (categories Generic / Menu / Preview / Hotkeys). `config/schematicpreview_icons.json` —
  `DirectoryIconStore`, `{ "icons": { "<abs path>": { "itemId": "minecraft:stone", "pos": "default" } } }`.
- **Environment variables:** `JAVA_HOME` (build only; must be JDK 8). No runtime env vars.
- **Secrets:** none. Never add publishing tokens to the build; if a release task is added,
  read them from env vars only.

## Security invariants

This is a client-side mod with no network surface. Trust boundaries are files on disk:

- **Input validation:** schematic files are parsed by Litematica (`SchematicType.tryCreateSchematicFrom`),
  never by us; a `null`/exception result renders an "unknown" placeholder, never crashes the
  screen. `schematicpreview_icons.json` is user-editable: unknown item ids fall back to the
  default icon and are dropped from the store; malformed JSON → log warning, empty store,
  file is NOT overwritten until the user changes an icon.
- **Preview volume cap:** `previewMaxVolume` (default 125 000 blocks) gates list/tile previews;
  the side panel and fullscreen preview ignore it but tessellate incrementally so a huge
  schematic cannot freeze the client for seconds. They have their own cap, `previewMaxBlocks`
  (default 1 000 000 non-air blocks, from `SchematicMetadata.totalBlocks`): every block's
  geometry sits in direct-memory `BufferBuilder`s until upload, so without it a multi-million
  block build could throw `OutOfMemoryError: Direct buffer memory` on selection.
- **GL resources:** every `Framebuffer`/`VertexBuffer` created has an owner that deletes it
  (`PreviewCache.close()`), otherwise VRAM leaks across screen opens.
- **External process:** `ScreenshotUtil.copyToClipboard` spawns `wl-copy --type image/png` on
  Wayland sessions — fixed argv, no shell, PNG bytes on stdin only, 10 s wait; nothing from the
  schematic (name, path, metadata) reaches the command line. Any other use of `ProcessBuilder`
  must keep that shape.
- **Known open items:** none yet.

## Conventions

- Match Litematica/MaLiLib 1.12.2 code style (Allman braces, `this.` on fields, 4 spaces) —
  the addon reads like an extension of those codebases.
- Mixins: one class per target, name `<Target>Mixin`, accessors `<Target>Accessor`; all in
  `dev.froyln.schematicpreview.mixin`; `remap = false` on every Litematica/MaLiLib target
  (those classes are not obfuscated), remap on for vanilla targets. Prefer subclassing or
  malilib's factory setters over injecting into private methods.
- Every new config option: add to `Configs`, to the category list, and an `en_us.lang` entry
  `schematicpreview.config.name.<key>` + `schematicpreview.config.comment.<key>`.
- New screen: extend malilib `BaseScreen`, open with `BaseScreen.openScreen(...)`, set parent
  with `setParent(GuiUtils.getCurrentScreen())`.
- No vanilla-only APIs from Forge (`ForgeHooksClient`, `net.minecraftforge.*`) — LiteLoader
  has no Forge.
- Git and commit rules: see `.claude/rules/git.md`. Testing rules: `.claude/rules/testing.md`.

## Gotchas

- **Casting to an accessor-mixin interface has two separate failure modes — the fix needs a
  plain class *outside* the whole `mixin` package, not just outside the `@Mixin` class.**
  `./gradlew build`/`compileJava` catch neither; both only surfaced at runtime via
  `tools/test-in-game.sh` (`./gradlew runClient` on this network never gets far enough to hit
  either). Found in two rounds against the real game:
  1. Casting `((BaseListWidgetAccessor) listWidget)...` straight inside
     `BaseSchematicBrowserScreenMixin`'s own `@Inject` method throws at game launch:
     `InvalidMixinException: Resolution error: unable to find corresponding type for
     dev/froyln/schematicpreview/mixin/BaseListWidgetAccessor in hierarchy of
     fi/dy/masa/litematica/gui/BaseSchematicBrowserScreen` (fatal, mod never finishes loading).
     The LiteLoader-bundled Mixin (0.7.4) recognizes `BaseListWidgetAccessor` as itself a
     registered `@Mixin` from its own global registry, and its descriptor-transform pass tries
     to resolve that reference as a target-hierarchy alias relative to whatever class is
     *currently* being transformed — which fails whenever the two mixins target unrelated
     classes.
  2. The obvious fix — move the cast into a plain helper class, `BrowserWidgetAccessors` —
     crashed differently the moment the affected screen was actually opened in-game (not at
     mod load; `compileJava`/`build` still both pass):
     `NoClassDefFoundError: dev/froyln/schematicpreview/mixin/BrowserWidgetAccessors is a mixin
     class and cannot be referenced directly`, even though that class has no `@Mixin`
     annotation at all. Cause: `mixins.schematicpreview.json`'s `"package"` value marks the
     *entire* `dev.froyln.schematicpreview.mixin` package as Mixin's root package, and Mixin
     excludes every class under it from normal classloading — not just the ones actually listed
     in `client[]`.
  Fix that actually works: put the plain helper class **outside the mixin package entirely**
  (`gui/BrowserWidgetAccessors.java`, not `mixin/BrowserWidgetAccessors.java`) and have the
  mixin call its static methods. The `mixin` package is reserved for classes Mixin itself
  processes (`@Mixin`-annotated targets and accessors) — nothing else can live there, even a
  class with no Mixin annotations, if anything needs to reference it normally afterward.
- **`deobfCompile` does not deobfuscate Litematica's vanilla type references — use the
  `remapLitematica` task's output instead.** Litematica's `.litemod` is compiled by its author
  directly against raw notch-obfuscated Minecraft (LiteLoader's normal dev workflow has no SRG
  stage, unlike Forge modding). ForgeGradle's `deobfCompile`/`TaskSingleDeobfBin` is built for
  the Forge-ecosystem case (third-party `:deobf` Maven artifacts are already SRG-named) and
  only remaps SRG → MCP; fed a raw-notch jar, it silently leaves *referenced* vanilla types
  notch-obfuscated in Litematica's own method signatures — e.g. `ISchematicRegion.getPosition()`
  would compile as returning a class literally named `et` instead of `BlockPos` (confirmed via
  `javap` on the `deobfCompile`-resolved jar; Litematica's own class/method names are fine,
  since they were never obfuscated — only foreign vanilla type references are affected). This
  didn't surface in task 1 because nothing there called a Litematica method with a vanilla type
  in its signature. Malilib is unaffected: masa publishes it under a `:deobf` Maven classifier
  that's already fully MCP-named. Fix, in `build.gradle`: a `remapLitematica` task runs
  `net.md_5.specialsource.SpecialSource` (`net.md-5:SpecialSource:1.8.3:shaded`, the same tool
  FG uses internally to deobfuscate the vanilla jar itself) against the raw litemod in `libs/`,
  using FG's own `notch-mcp.srg` (generated by the `genSrgs` task, `remapLitematica dependsOn`
  it) — producing a jar with real MCP names. That output then has to be forced onto the compile
  classpath in an `afterEvaluate` block, filtering out any `.litemod` file by extension first:
  the LiteLoader Gradle plugin *also* puts the raw litemod from `libs/` directly on
  `sourceSets.main.compileClasspath` (needed for `runClient`, which loads mods directly in their
  notch-compiled form), ahead of anything declared in the `dependencies {}` block, so without
  the filter javac silently resolves Litematica classes from the still-broken raw jar instead of
  the remapped one — this exact silent-shadowing is why the initial `compile files(...)` fix
  alone did not work. If a `litematica_version` bump ever fails to compile with unfamiliar
  single/double-letter class names (`et`, `fq`, `awt`, ...), this is almost certainly it: check
  it hasn't regressed before assuming the API changed.
- **JDK 8u504 (current Arch/CachyOS `jdk8-openjdk`) breaks FG2.3's deobfuscator.**
  `TaskSingleDeobfBin` copies each zip entry's compressed-size metadata from the *input*
  jar unchanged (`new JarEntry(oldEntry)`), then re-deflates the content with the
  running JVM's `Deflater`. A newer JDK's zlib produces a different compressed byte
  count than whatever packed the original `.litemod`, and the mismatch throws
  `java.util.zip.ZipException: invalid entry compressed size (expected X but got Y bytes)`
  — 100% reproducible, independent of which entry is "first". Fix used here: (1) pin the
  build to **Temurin JDK 8u302** (`.jdk-cache/`, fetched by `tools/setup-build-deps.py`;
  no system JDK change), which alone fixed the
  *malilib* deobf pass; (2) additionally repack the litematica `.litemod` fully
  **STORED/uncompressed** (`tools/setup-build-deps.py`) so there is never a compressed
  size to mismatch — needed because litematica's jar still failed even on 8u302.
  If a future JDK 8 update reintroduces this, try an even older Temurin build first.
- **`gradle.properties` needs `org.gradle.jvmargs=-Xmx3G`** (missing by default) or
  `genSrgs`/decompilation OOMs — `build.properties` (the project's own ConfigSlurper
  file) is unrelated and does not affect Gradle's own heap.
- flatDir dependencies with a non-`.jar` extension need `ext:` explicitly, e.g.
  `deobfCompile name: "litematica-liteloader-1.12.2-0.31.4", ext: "litemod"` — otherwise
  Gradle only looks for `.jar`/`-.jar` and fails with `ModuleVersionNotFoundException`.
- ForgeGradle 2.3 + Gradle 2.14 need JDK 8 and old TLS-capable Java; if `setupDecompWorkspace`
  fails on downloads, the forge maven moved to `https://maven.minecraftforge.net` (update
  `buildscript.repositories`), LiteLoader versions come from `http://dl.liteloader.com/versions/versions.json`.
- The `.litemod` you ship is obfuscated to Notch names; the mixin `refmap` is what maps
  vanilla targets. Forgetting `ext.refMap` in `sourceSets.main` → mixins silently fail in prod.
- `BlockRendererDispatcher.renderBlock` reads `IBlockAccess.getBlockState` for neighbors —
  `SchematicBlockAccess` must return `AIR` outside the region, not throw.
- `Framebuffer` in 1.12 allocates a depth texture only if `useDepth` is true — pass `true`.
- **Bind a `VertexBuffer` before `glVertexPointer`/`glColorPointer`/`glTexCoordPointer`, never
  after.** Those calls interpret their last argument as a byte offset into the *currently
  bound* `GL_ARRAY_BUFFER`, not a client-side pointer; calling them with no buffer bound throws
  `OpenGLException: Cannot use offsets when Array Buffer Object is disabled` — a runtime-only
  crash, invisible to `./gradlew build`/`compileJava` and only hit once something actually
  renders (found via `tools/test-in-game.sh`, not `runClient`). `PreviewRenderer.drawLayer()`
  had this backwards once already; the correct order is `vbo.bindBuffer()` →
  `glEnableClientState`/pointer setup → `vbo.drawArrays(...)`, matching
  `VboRenderListSchematic.renderBlocks` in the Litematica reference source.
- `Minecraft.getFramebuffer().bindFramebuffer(true)` must be called after rendering to the
  preview FBO, otherwise the rest of the GUI draws into the preview.
- **Clear the preview FBO to an opaque color, not alpha 0.** The blit in
  `PreviewRenderUtils.blitFramebuffer` has blending on, so wherever the FBO's alpha is 0 (no
  schematic geometry covering that pixel) the blit lets whatever was already drawn to the
  screen behind the widget show through instead — for this GUI, that's the live game world,
  not the widget's own background. Looked exactly like a badly broken camera (dark, oversized,
  out-of-place geometry filling most of the widget) until traced to this; the actual schematic
  render was fine the whole time. `PreviewRenderer.draw()` clears to opaque dark gray now.
- `en_us.lang` (properties style) — 1.12 does not read `.json` lang files.
- Litematica's browser remembers directory per `browserContext`; our entry widget must keep
  using `BaseFileBrowserWidget.DirectoryEntry.getFullPath()` and not cache paths across
  directory switches.
- **`PreviewRenderer.draw()` must set *and* restore every GL state it depends on — never trust
  what the caller left behind.** It runs from two very different contexts (the normal per-frame
  GUI render, and a one-off capture triggered from the Save/Copy button's click handler, which
  runs during input processing — before that frame's own render pass), and vanilla tile entity
  renderers (`TileEntityEndPortalRenderer` especially, used by `minecraft:end_portal` — schematics
  with an end-portal-based duper hit this) leave GL state changed on the assumption that the next
  *world* render frame will reset it: `GlStateManager.enableLighting()` unconditionally at the end,
  a foreign blend func, texgen coords still enabled. Two real bugs from this, both found via
  `tools/test-in-game.sh` (invisible to `./gradlew build`): (1) selecting an end-portal schematic
  left lighting on, so the rest of that GUI frame — every widget malilib drew afterward — came out
  flat grey/white; (2) capturing from the click handler inherited whatever the *previous* frame's
  last GL calls were (often `Framebuffer`'s own post-render blit, which disables alpha test) rather
  than the sane state the widget's own render pass sets up every frame, so a capture could silently
  drop content an alpha-tested cutout layer (e.g. redstone wire) needed alpha test enabled to draw
  correctly, while the very same frame looked correct on screen. Fixed by having `draw()` pin every
  state it uses at the top (alpha test, fog, color material, rescale normal, texgen, blend, depth
  func) and reset the lighting/blend/texgen state again after `drawTileEntities()`, and — separately
  — by moving the Save/Copy capture itself off the click handler and into the widget's own next
  render frame (`PreviewWidget.requestCapture` / `serviceCaptureRequest`), so it always runs with
  the same state and tessellation progress that frame's on-screen draw just used.
- **Switch texture units with `GlStateManager.setActiveTexture`, never the raw
  `OpenGlHelper.setActiveTexture`, when the calls that follow go through `GlStateManager`.**
  `GlStateManager` caches bound-texture and `texture2D` state *per unit*, indexed by the unit it
  last switched to; `OpenGlHelper.setActiveTexture` is a bare `glActiveTexture` that leaves that
  index untouched. `PreviewRenderer.draw()` used the raw switch around its lightmap-unit setup, so
  `bindTexture(white)` / `enableTexture2D()` / `disableTexture2D()` were recorded against unit 0's
  cache entry while GL applied them to unit 1 — and unit 1's own entry kept claiming the vanilla
  lightmap was bound. Every following world frame, `EntityRenderer.enableLightmap()`'s
  `bindTexture(lightmap)` was then a cached no-op and the world rendered lit by our 1x1 white
  texture: with the schematic browser open the whole world behind the GUI went 4–13x darker /
  wrong ("all the lights change"). Proven with a probe reading `GL_TEXTURE_BINDING_2D` on unit 1
  at the start of every `draw()`: 789/789 frames wrong before, 0/739 after. Same rule applies to
  the client-side array states — disable them and `GlStateManager.resetColor()` after a VBO draw,
  exactly as `RenderGlobal.renderBlockLayer` does.
- **Never hand the AWT clipboard a `TYPE_INT_ARGB` image — convert to `TYPE_INT_RGB` first.**
  AWT advertises the image in every format it can encode (PNG, JPEG, GIF, ...) and re-encodes on
  demand; the JDK's JPEG writer mangles 4-channel ARGB input, so any app that pastes the JPEG
  flavor (most non-image-editors) gets inverted pink/cyan colors on black while `xclip -t
  image/png` looks perfect. Verified on the game's own JRE with a standalone test: ARGB
  (220,200,150) reads back as (200,100,142), RGB exact. `ScreenshotUtil.copyToClipboard`
  composites the capture over the preview's dark background into an RGB image for the AWT path
  only; the `wl-copy` path (Wayland) and the saved PNG file keep the transparent capture.
- **On a Wayland session, the AWT clipboard is unusable for images — `ScreenshotUtil` hands the
  PNG to `wl-copy` instead.** The game (Java 8 AWT + LWJGL 2) is an X11 client under XWayland;
  native Wayland apps (Discord, any Chromium/Electron app, browsers, Wayland terminals) read the
  clipboard through the compositor's X11→Wayland bridge, and that bridge drops AWT's chunked
  (INCR) selection transfer partway for anything beyond a few hundred KB. Symptom: the saved PNG
  file is perfect and X11 readers (`xclip`) get the whole image, but a paste into Discord shows
  only the top strip and WhatsApp Web shows a blank white image. Measured on Hyprland with a
  6.25 MB AWT-owned PNG: three `wl-paste` reads returned 4.8 / 2.9 / 5.5 MB, all corrupt; with
  `wl-copy` as owner, 3/3 intact; in-game after the fix, 3/3 identical 370 KB reads. Gate is
  `WAYLAND_DISPLAY` being set; if `wl-copy` isn't on PATH it falls back to AWT. Hyprland does not
  bridge a Wayland-owned selection back to XWayland at all (also true for a bare `wl-copy`), so
  `xclip` sees nothing afterwards — that's the compositor, not us, and no X11 app is a realistic
  paste target on such a desktop. Diagnose clipboard bugs from the *consumer's* display protocol:
  `wl-paste -t image/png | wc -c` vs `xclip -selection clipboard -t image/png -o | wc -c`.
- **Every malilib popup screen we open goes through `PopupScreenCompat.keepPopupSize()` — malilib
  0.54 resizes popups to the whole window otherwise.** 0.53 (what we compile against) decides
  "is this a full-screen screen or a popup" with `isFullScreen()` (`screenWidth == window
  width`). 0.54 replaced that with a `useWindowDimensions` flag defaulting to `true`, and its own
  `ConfirmActionScreen`/`BaseTextInputScreen` never clear it — so on open,
  `onScreenResolutionSet` sets `screenWidth/Height` to the window size while `x/y` stay where
  the constructor centered the *small* box. Symptom: a black box hanging off the bottom/right
  edges with the title and message visible and no buttons — they're placed at
  `y + screenHeight - 26`, i.e. off-screen. Independent of the vanilla GUI scale (user tried).
  Proven with a tick-handler log of the live screen: `280x80` right after construction,
  `938x503` (= window) once open. `./gradlew build` can't catch it (0.53 has no such method);
  `tools/test-in-game.sh`'s instance runs 0.54 and does. The helper calls
  `setUseWindowDimensions(false)` reflectively when it exists, so one litemod works on both.
  Any new `ConfirmActionScreen`/`TextInputScreen`/`BaseTextInputScreen` subclass must go through
  it.
- **The translucent block layer's blend func must use `(ONE, ONE_MINUS_SRC_ALPHA)` for the alpha
  channel, not `(ONE, ZERO)`.** `(ONE, ZERO)` *replaces* the framebuffer's existing alpha with the
  translucent quad's own alpha instead of compositing it (`outA = srcA + dstA*(1-srcA)`) — on the
  background-free capture FBO (`transparentBackground = true`), a translucent block (water, a
  portal) sitting in front of an opaque one made the *opaque* block read back as semi-transparent
  in the saved/copied PNG. Invisible on the normal (opaque-background) widget FBO, since that
  background is fully opaque already and blending its alpha with anything still reads as opaque.
