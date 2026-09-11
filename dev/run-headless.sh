#!/bin/sh
set -eu

exec xvfb-run -a --server-args="-screen 0 ${SCREEN:-1280x720x24}" \
    ./gradlew --no-daemon runClient \
    -Prpc.host="${RPC_HOST:-host.docker.internal}" \
    -Prpc.port="${RPC_PORT:-8766}" \
    -Pbot.name="${BOT_NAME:-fabric_bot}"
