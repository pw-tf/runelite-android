#!/bin/bash
# Installs the Android NDK version pinned in android-awt/build.gradle.kts (ndkVersion)
# if the SDK doesn't already have it. GitHub's runner images only carry a few NDK
# versions and drop older ones over time, which fails the build at configuration
# ("NDK is not installed").

set -euo pipefail

cd "$(dirname "$0")/.."

version=$(sed -n 's/^ *ndkVersion = "\([^"]*\)".*/\1/p' android-awt/build.gradle.kts | head -n 1)
if [ -z "$version" ]; then
  echo "no ndkVersion in android-awt/build.gradle.kts; nothing to install"
  exit 0
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then
  echo "ANDROID_HOME is not set" >&2
  exit 1
fi

if [ -d "$sdk/ndk/$version" ]; then
  echo "NDK $version already installed"
  exit 0
fi

echo "Installing NDK $version"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
# `yes` answers the licence prompts. It is killed by SIGPIPE when sdkmanager exits,
# which pipefail would report as a failure, so check the result directly instead.
set +o pipefail
yes | "$sdkmanager" --install "ndk;$version" > /dev/null || true
set -o pipefail
if [ ! -d "$sdk/ndk/$version" ]; then
  echo "NDK $version did not install" >&2
  exit 1
fi
echo "Installed $sdk/ndk/$version"
