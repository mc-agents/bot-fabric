#!/bin/sh
# Launches the Fabric client under a virtual X server. Minecraft itself is not in this image; it
# is expected in MC_ASSETS_DIR, filled either by an init container running fetch-minecraft.py or,
# when that directory is empty, by this script.
#
# The names are mcp-server's docs/bot-protocol.md, under "How a bot is told where to dial". They
# were this image's own, which meant the operator set MC_ASSETS_DIR and this read MC_CACHE, and a
# bot pod went looking for a client in a directory nothing had filled.
set -eu

MC_ASSETS_DIR="${MC_ASSETS_DIR:-/mc}"
BOT_WORK_DIR="${BOT_WORK_DIR:-/data}"
MC_VERSION="${MC_VERSION:-$BOT_FABRIC_MINECRAFT}"
LOADER_VERSION="${LOADER_VERSION:-$BOT_FABRIC_LOADER}"
BOT_SCREEN="${BOT_SCREEN:-1280x720x24}"
JAVA_OPTS="${JAVA_OPTS:--Xmx1G}"
# Fabric loader opens a Swing window to report a failed mod resolution, which in a container
# turns a clear error into an UnsatisfiedLinkError on top of it.
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"

launch="$MC_ASSETS_DIR/versions/$MC_VERSION/launch.json"
if [ ! -f "$launch" ]; then
    echo "no minecraft $MC_VERSION in $MC_ASSETS_DIR, fetching it" >&2
    /opt/bot-fabric/fetch-minecraft.py \
        --minecraft "$MC_VERSION" \
        --loader "$LOADER_VERSION" \
        --cache "$MC_ASSETS_DIR"
fi

mkdir -p "$BOT_WORK_DIR/mods"
cp -f /opt/bot-fabric/mods/*.jar "$BOT_WORK_DIR/mods/"

classpath=$(python3 - "$launch" "$MC_ASSETS_DIR" <<'PY'
import json, sys
launch, cache = sys.argv[1], sys.argv[2]
entries = json.load(open(launch))["classpath"]
print(":".join(f"{cache}/{entry}" for entry in entries))
PY
)
asset_index=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["assetIndex"])' "$launch")
main_class=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mainClass"])' "$launch")

DISPLAY_NUMBER="${DISPLAY_NUMBER:-99}"
Xvfb ":$DISPLAY_NUMBER" -screen 0 "$BOT_SCREEN" -nolisten tcp &
for _ in $(seq 1 50); do
    [ -e "/tmp/.X11-unix/X$DISPLAY_NUMBER" ] && break
    sleep 0.2
done
[ -e "/tmp/.X11-unix/X$DISPLAY_NUMBER" ] || { echo "Xvfb did not come up" >&2; exit 1; }
export DISPLAY=":$DISPLAY_NUMBER"

exec java $JAVA_OPTS -cp "$classpath" "$main_class" \
    --username "${BOT_NAME:-fabric_bot}" \
    --uuid 00000000-0000-0000-0000-000000000000 \
    --accessToken 0 \
    --userType legacy \
    --versionType release \
    --version "$MC_VERSION" \
    --gameDir "$BOT_WORK_DIR" \
    --assetsDir "$MC_ASSETS_DIR/assets" \
    --assetIndex "$asset_index" \
    "$@"
