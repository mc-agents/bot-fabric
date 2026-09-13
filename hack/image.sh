#!/usr/bin/env bash
#
# Build the bot image from the working tree, the way CI builds it.
#
#     ./hack/image.sh                 # the first Minecraft version in settings.gradle.kts
#     ./hack/image.sh 26.2            # a particular one
#     ./hack/image.sh 26.1.2 my:tag   # and a tag of your own
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

version=${1:-$(sed -n 's/.*versions("\([^"]*\)".*/\1/p' settings.gradle.kts | head -1)}
tag=${2:-bot-fabric:local-mc${version}}

properties="versions/${version}/gradle.properties"
[ -f "$properties" ] || { echo "no such version: $version" >&2; exit 1; }

value() { sed -n "s/^$1=//p" "$properties" | tr -d '[:space:]'; }

mod_version=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '[:space:]')
loader=$(value loader_version)
fabric_api=$(value fabric_api_version)

echo "building ${tag}: mod ${mod_version}, minecraft ${version}, loader ${loader}, api ${fabric_api}"

./gradlew -q "${version}:build"

rm -rf dist
mkdir -p dist
cp "versions/${version}/build/libs/botfabric-${mod_version}+${version}.jar" dist/

docker build \
    --platform "linux/$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')" \
    --build-arg "MINECRAFT_VERSION=${version}" \
    --build-arg "LOADER_VERSION=${loader}" \
    --build-arg "FABRIC_API_VERSION=${fabric_api}" \
    -t "${tag}" .

echo "built ${tag}"
