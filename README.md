# SchematicPreview for LiteLoader 1.12.2

A [Litematica](https://github.com/maruohon/litematica) addon for Minecraft 1.12.2 /
[LiteLoader](https://www.liteloader.com/) that brings the features of the Fabric mod
[DimasKama/SchematicPreview](https://github.com/DimasKama/SchematicPreview) to 1.12.2,
re-implemented from scratch:

- **Schematic previews** — live 3D preview of the selected schematic in the browser side
  panel (drag to rotate, scroll to zoom, fullscreen and free-camera modes) and small previews
  per entry in list or 3/4/5-column tile layouts.
- **Directory icons** — right-click a folder to give it any item as icon.
- **Replace button** in the material list — swap every block of one type in the schematic.

Status: **in progress**. Task 1 (mod skeleton: configs, hotkey, config screen) is done and
builds; the features above are not implemented yet. See `PLAN.md` for the task list and
`AGENTS.md` for the design.

## Requirements (runtime)

| Mod | Version |
|-----|---------|
| Minecraft | 1.12.2 |
| LiteLoader | 1.12.2 |
| MaLiLib (LiteLoader) | 0.53.0 |
| Litematica (LiteLoader) | 0.31.4 |

MaLiLib and Litematica for 1.12.2 are on [masa's download page](https://masa.dy.fi/mcmods/client_mods/?mcver=1.12.2),
[Modrinth](https://modrinth.com/mod/litematica/version/0.31.4) and CurseForge.

## Building

Toolchain is the same as Litematica 1.12.2: ForgeGradle 2.3 + Gradle 2.14.1 wrapper, **JDK 8**.

```bash
python3 tools/setup-build-deps.py    # once: fetches a pinned JDK 8 into .jdk-cache/ and
                                      # litematica's .litemod (repacked) into libs/
export JAVA_HOME=$PWD/.jdk-cache/jdk8u302-b08

./gradlew build                  # first run is slow: downloads + deobfuscates MC 1.12.2
                                  # → build/libs/schematicpreview-liteloader-1.12.2-<version>.litemod
./gradlew runClient              # dev client, run dir ./minecraft
```

**Use the pinned JDK, not your system's JDK 8** if it's a recent build (8u402+): ForgeGradle
2.3's deobfuscator hits a real `java.util.zip.ZipException` on newer JDK 8 point releases.
See `AGENTS.md` → Gotchas for why, and `tools/setup-build-deps.py` for the fix.

Litematica LiteLoader isn't published to a Maven repo, hence the script — it downloads
`litematica-liteloader-1.12.2-<version>.litemod` from
[masa's mod page](https://masa.dy.fi/mcmods/litematica/) and repacks it. Alternative: build
Litematica from source (`maruohon/litematica`, branch `liteloader_1.12.2`, commit `1db931a6`)
and publish it to your local Maven.

## Working with Claude Code

This repo uses the ClaudeNewProject template: `CLAUDE.md` (working agreement + canary word),
`AGENTS.md` (project facts, source of truth), `PLAN.md` (one task `in progress` at a time),
`.claude/rules/`, skills `/plan-task` `/finish-task` `/review` `/commit` `/sync-docs`, and
hooks that block destructive commands and warn when context degrades. Hooks need `jq`.

Optional per-machine files: copy `CLAUDE.local.md.example` → `CLAUDE.local.md` and
`.claude/settings.local.json.example` → `.claude/settings.local.json`
(set `CLAUDE_VERIFY_ON_STOP=1` there to have every turn end with a passing `./gradlew build`).

## License

Not chosen yet (see the last task in `PLAN.md`). The original Fabric mod is
"All rights reserved" and no code from it is used here.
