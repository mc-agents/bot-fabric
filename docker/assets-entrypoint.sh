#!/bin/sh
# Fills a cache volume with a Minecraft client for a fabric bot to launch.
#
# The client jar is Mojang's to distribute, not ours, so it cannot live in a bot image. This runs
# as an init container, or as a Job that fills one PVC per Minecraft version, and the bot mounts
# what it left behind.
#
# The contract is the operator's:
#
#     mc-assets --version 26.1.2 --dest /mc
#
# and the same two as MC_VERSION and MC_ASSETS_DIR. The Fabric loader version is not in it: the
# image knows which loader its bot was built against, and a caller guessing one would produce a
# cache that bot cannot launch.
set -eu

version="${MC_VERSION:-$BOT_FABRIC_MINECRAFT}"
dest="${MC_ASSETS_DIR:-/mc}"

while [ $# -gt 0 ]; do
    case "$1" in
        --version) version="$2"; shift 2 ;;
        --dest) dest="$2"; shift 2 ;;
        --version=*) version="${1#--version=}"; shift ;;
        --dest=*) dest="${1#--dest=}"; shift ;;
        *) echo "mc-assets: unknown argument $1" >&2; exit 2 ;;
    esac
done

echo "filling $dest with minecraft $version and fabric loader $BOT_FABRIC_LOADER" >&2

exec /opt/mc-assets/fetch-minecraft.py \
    --minecraft "$version" \
    --loader "$BOT_FABRIC_LOADER" \
    --cache "$dest"
