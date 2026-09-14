#!/usr/bin/env python3
"""One-time setup: fetch the pinned JDK 8 and the Litematica LiteLoader dependency this
project needs but can't get from a normal Gradle repository.

1. JDK 8 (Temurin 8u302) into .jdk-cache/ - the system's JDK 8u504+ hits a ForgeGradle
   2.3 deobfuscation bug (java.util.zip.ZipException: invalid entry compressed size),
   see AGENTS.md -> Gotchas. Skipped if .jdk-cache/jdk8u302-b08 already exists.
2. Litematica's .litemod into libs/, repacked STORED (uncompressed) - the extra fix that
   was still needed on top of the JDK pin, same bug, see AGENTS.md -> Gotchas.

Run once before the first build, or whenever bumping build.properties -> litematica_version:
    python3 tools/setup-build-deps.py
Then: export JAVA_HOME=$PWD/.jdk-cache/jdk8u302-b08 && ./gradlew build
"""
import pathlib
import re
import sys
import tarfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUILD_PROPS = ROOT / "build.properties"
LIBS = ROOT / "libs"
JDK_CACHE = ROOT / ".jdk-cache"
JDK_DIR_NAME = "jdk8u302-b08"
JDK_URL = (
    "https://github.com/adoptium/temurin8-binaries/releases/download/"
    "jdk8u302-b08/OpenJDK8U-jdk_x64_linux_hotspot_8u302b08.tar.gz"
)


def read_version(key: str) -> str:
    text = BUILD_PROPS.read_text()
    m = re.search(rf"^{key}\s*=\s*(\S+)", text, re.MULTILINE)
    if not m:
        raise SystemExit(f"{key} not found in build.properties")
    return m.group(1)


def fetch_jdk() -> None:
    dest = JDK_CACHE / JDK_DIR_NAME
    if dest.exists():
        print(f"JDK already present at {dest}, skipping")
        return
    JDK_CACHE.mkdir(exist_ok=True)
    print(f"Downloading {JDK_URL}")
    tmp = JDK_CACHE / "jdk8.tar.gz"
    urllib.request.urlretrieve(JDK_URL, tmp)
    print(f"Extracting into {JDK_CACHE}")
    with tarfile.open(tmp) as tf:
        tf.extractall(JDK_CACHE)
    tmp.unlink()
    print(f"JDK ready at {dest}")


def fetch_litematica() -> None:
    version = read_version("litematica_version")
    mc = read_version("minecraft_version_out")
    filename = f"litematica-liteloader-{mc}-{version}.litemod"
    url = f"https://masa.dy.fi/mcmods/litematica/{filename}"
    LIBS.mkdir(exist_ok=True)
    dest = LIBS / filename

    print(f"Downloading {url}")
    tmp = dest.with_suffix(".download")
    urllib.request.urlretrieve(url, tmp)

    print("Repacking as STORED (uncompressed) to avoid the FG2 deobf zip bug")
    with zipfile.ZipFile(tmp) as zin, zipfile.ZipFile(dest, "w", zipfile.ZIP_STORED) as zout:
        for item in zin.infolist():
            if item.is_dir():
                continue
            info = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = item.external_attr
            zout.writestr(info, zin.read(item.filename))
    tmp.unlink()
    print(f"Wrote {dest} ({dest.stat().st_size} bytes)")


def main() -> None:
    fetch_jdk()
    fetch_litematica()


if __name__ == "__main__":
    sys.exit(main())
