#!/usr/bin/env bash
#
# Build the bot image from the working tree, the way CI builds it.
#
#     ./hack/image.sh              # tagged bot-fabric:local-mc<minecraft_version>
#     ./hack/image.sh my:tag       # or a tag of your own
#
# Testing against `runClient` tests a client with a graphics card, a window and this machine's
# fonts. What ships is a container with none of those, and the difference is not small: the same
# client is 287MiB on a desktop and 1.7GiB under Xvfb with software rendering. So the image is
# what a change gets verified against.
#
# dist/ is refreshed rather than added to. It is gitignored, so a jar from an old version sits
# there until something removes it -- and the image takes whatever it finds, which meant an image
# built after a day's work shipped a mod from a fortnight before it.
set -euo pipefail

cd "$(dirname "$0")/.."

value() { sed -n "s/^$1=//p" gradle.properties | tr -d '[:space:]'; }

mod_version=$(value mod_version)
minecraft=$(value minecraft_version)
loader=$(value loader_version)
fabric_api=$(value fabric_api_version)
tag=${1:-bot-fabric:local-mc${minecraft}}

echo "building ${tag}: mod ${mod_version}, minecraft ${minecraft}, loader ${loader}, api ${fabric_api}"

./gradlew -q build

rm -rf dist
mkdir -p dist
cp "build/libs/botfabric-${mod_version}+${minecraft}.jar" dist/

docker build \
    --platform "linux/$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')" \
    --build-arg "MINECRAFT_VERSION=${minecraft}" \
    --build-arg "LOADER_VERSION=${loader}" \
    --build-arg "FABRIC_API_VERSION=${fabric_api}" \
    -t "${tag}" .

echo "built ${tag}"
