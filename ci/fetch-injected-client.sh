#!/bin/bash
# Downloads RuneLite's published injected client into data/<target>.jar, the file the
# Android build packages (see `val target` in android/build.gradle.kts). data/ is
# gitignored, so CI and release builds fetch it instead of committing it.
#
# The Maven version comes from gradle.properties (project.build.version), so it follows
# upstream's version bumps automatically. A jar already in place is left alone.

set -euo pipefail

cd "$(dirname "$0")/.."

version=$(sed -n 's/^project\.build\.version=//p' gradle.properties)
target=$(sed -n 's/^val target = "\(runelite-[^"]*\)".*/\1/p' android/build.gradle.kts)
if [ -z "$version" ] || [ -z "$target" ]; then
  echo "could not read project.build.version or android target (got '$version' / '$target')" >&2
  exit 1
fi

dest="data/$target.jar"
if [ -f "$dest" ]; then
  echo "$dest already present"
else
  url="https://repo.runelite.net/net/runelite/injected-client/$version/injected-client-$version.jar"
  echo "Fetching $url -> $dest"
  mkdir -p data
  curl -fSL --retry 3 -o "$dest" "$url"
fi
ls -l "$dest"
