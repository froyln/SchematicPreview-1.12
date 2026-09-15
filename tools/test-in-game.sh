#!/usr/bin/env bash
# Manual in-game verification via an existing, already-set-up PrismLauncher instance -
# the go-to alternative to `./gradlew runClient` when its asset download stalls (this
# network's connection to resources.download.minecraft.net returns HTTP 400s for most
# sound assets, see AGENTS.md -> Gotchas). The instance already has LiteLoader 1.12.2,
# litematica and malilib installed and its Minecraft assets already downloaded.
#
# Copies the freshly built litemod into the instance's mods folder (replacing any older
# build of this mod, and only this mod - nothing else in mods/ is touched) and launches
# PrismLauncher straight into that instance, offline, for hands-on testing.
#
# Usage: ./gradlew build && tools/test-in-game.sh
set -euo pipefail

INSTANCE="1.12.2 test ai"
INSTANCE_DIR="$HOME/.local/share/PrismLauncher/instances/$INSTANCE"
MODS_DIR="$INSTANCE_DIR/minecraft/mods/1.12.2"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -d "$MODS_DIR" ]; then
    echo "Instance mods folder not found: $MODS_DIR" >&2
    exit 1
fi

litemod="$(ls "$ROOT"/build/libs/schematicpreview-liteloader-*.litemod 2>/dev/null | head -n1)"

if [ -z "$litemod" ]; then
    echo "No built litemod found in build/libs/ - run ./gradlew build first." >&2
    exit 1
fi

rm -f "$MODS_DIR"/schematicpreview-liteloader-*.litemod
cp "$litemod" "$MODS_DIR/"
echo "Installed $(basename "$litemod") into $MODS_DIR"

exec prismlauncher --launch "$INSTANCE" --offline SchematicPreviewTester --show-window
