# Dev environment

Two pieces: a Paper server to join, and a stand-in for `mcp-server` so the bot has something
to dial.

## Paper

```sh
docker compose -f dev/compose.yml up -d
```

Paper 26.1.2, offline mode, creative, flat world, port 25578. `fabric_bot` is opped on startup.

The dialog checks need a datapack, and the dialog registry is only read when the world loads,
so `/reload` is not enough:

```sh
docker cp dev/datapack/botcheck bot-fabric-dev-minecraft-1:/data/world/datapacks/botcheck
docker compose -f dev/compose.yml restart
```

`botcheck:check` is a `multi_action` dialog with three buttons: one `run_command` the client runs
outright (`/help`), one it will not (`/say ...`), and one `dynamic/custom` action.

The middle one is meant to fail. A command that sends chat as the player needs a signature the
client will only produce from the chat screen, so the confirmation it opens offers to copy the
command somewhere rather than to run it, and `press-dialog-button` refuses and says so. It used to
press whatever button was first on that screen and report success for an action that never
happened.

## The RPC harness

`rpc-harness.py` listens on `:8765`, answers `hello` with `helloOk`, then runs a script of
`connect` / `call` / `press` lines and prints every frame it gets back. Blobs land in `dev/out/`,
renamed by the mime type the result gives them, so a screenshot is `<uuid>.png`.

```sh
python3 dev/rpc-harness.py dev/smoke.txt --port 8766
```

No dependencies. It is how you drive the bot without `mcp-server`.

## Headless client from the image

The published image is the real thing and needs no Gradle; see the README. `dev/ci-smoke.txt` is
the script CI runs against it.

## Headless client from a Gradle run

```sh
docker build --platform linux/amd64 -f dev/Dockerfile.headless -t bot-fabric-headless dev
docker run --rm --platform linux/amd64 \
    -v "$PWD:/work" -v bot-fabric-gradle:/gradle \
    -e GRADLE_USER_HOME=/gradle -e HOME=/gradle -w /work \
    bot-fabric-headless dev/run-headless.sh
```

`linux/amd64` is not a preference. Mojang's version manifest for 26.1.2 ships LWJGL natives for
`linux` (x86_64), `macos`, `macos-arm64` and `windows` — there is no `linux-arm64`. On an ARM
host the container runs under emulation until the image substitutes LWJGL's own arm64 natives.
