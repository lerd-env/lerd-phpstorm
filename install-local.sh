#!/usr/bin/env bash
# Builds the plugin and drops it into the installed PhpStorm, so each phase can
# be driven in the real IDE rather than a sandbox. Restart PhpStorm afterwards.
set -euo pipefail

cd "$(dirname "$0")"

PLUGINS_DIR="${LERD_PLUGINS_DIR:-}"
if [[ -z "$PLUGINS_DIR" ]]; then
    PLUGINS_DIR=$(ls -d "$HOME"/.local/share/JetBrains/PhpStorm* 2>/dev/null | sort | tail -1 || true)
fi
[[ -n "$PLUGINS_DIR" ]] || { echo "no PhpStorm plugins directory found" >&2; exit 1; }

./gradlew buildPlugin -q
ZIP=$(ls -t build/distributions/*.zip | head -1)

# The folder inside the zip is the one to replace; guessing it would silently
# leave an older copy behind for the IDE to load instead.
NAME=$(unzip -Z1 "$ZIP" | head -1 | cut -d/ -f1)
rm -rf "${PLUGINS_DIR:?}/${NAME:?}"
unzip -q -o "$ZIP" -d "$PLUGINS_DIR"
echo "installed $NAME $(basename "$ZIP") into $PLUGINS_DIR"
echo "restart PhpStorm to load it"
