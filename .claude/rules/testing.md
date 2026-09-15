---
paths:
  - "**/*.test.*"
  - "**/*.spec.*"
  - "**/*_test.*"
  - "**/test_*.*"
  - "tests/**"
  - "test/**"
  - "__tests__/**"
---
# Testing rules

- There is no test suite: this is a Minecraft client mod whose behavior is GL rendering
  and GUI. `./gradlew compileJava` while iterating, `./gradlew build` before finishing,
  then check the PLAN.md acceptance items by hand in `./gradlew runClient`.
- **`./gradlew runClient`'s first-run asset download is unreliable on this network**
  (`resources.download.minecraft.net` returns HTTP 400 for most sound assets — a network/CDN
  issue, not a bug in this project). When it stalls, use the existing PrismLauncher instance at
  `~/.local/share/PrismLauncher/instances/1.12.2 test ai` instead — already has LiteLoader
  1.12.2, litematica and malilib installed, Minecraft assets already downloaded, and a folder
  full of real schematics under `minecraft/schematics/` for manual checks. Run
  `./gradlew build && tools/test-in-game.sh` — it copies the freshly built litemod into that
  instance's `mods/1.12.2/` (replacing only this mod's own previous build, nothing else in
  `mods/`) and launches PrismLauncher straight into it, offline, via `prismlauncher --launch`.
  Watch that instance's `minecraft/logs/latest.log` for LiteLoader's mod list and any crash,
  and use its GUI directly for the actual click-through checks — this is a real, hands-on
  Minecraft session, not headless, so it still needs a human (or an agent with display/input
  access) at the keyboard for anything beyond "did it load without crashing".
- Pure logic that does not touch Minecraft classes (block replacement property copying,
  tile grid math, icon store JSON round-trip) may get a plain JUnit 4 test under
  `src/test/java` — JUnit 4 is what ForgeGradle 2.3 / Gradle 2.14 can run. Add the
  `testCompile 'junit:junit:4.12'` dependency the first time a test is written; then
  `./gradlew test` becomes part of `build` automatically.
- Bug fix in that pure logic = a failing test first that reproduces the symptom, then the fix.
  Rendering bugs: describe the repro steps (screen, schematic file, action) in the PLAN.md
  notes instead.
- Test schematics for manual checks live in `minecraft/schematics/` (run dir, gitignored):
  keep one single-region `.litematic`, one multi-region one, one `.schematic`, one `.nbt`
  and one corrupt file.
- Never delete or skip a failing test to get green. Report it and stop.
