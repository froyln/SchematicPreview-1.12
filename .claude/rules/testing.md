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
