# bot-fabric

A real Minecraft client, driven over the mc-agents bot RPC protocol. It is one of the two bots
behind the same MCP surface; the other is [`bot-mineflayer`](https://github.com/mc-agents/bot-mineflayer).
The contract both speak lives in [`mcp-server/docs/bot-protocol.md`](https://github.com/mc-agents/mcp-server/blob/main/docs/bot-protocol.md),
and the plan it comes from is `mcp-server/docs/architecture.md`.

This bot exists for the things a reimplementation of the client cannot do: **see the screen** and
**press a dialog button**. Both are proved below. It answers every one of the forty-six tools a bot
is asked for.

## What was proved

Measured against Paper 26.1.2 and a Fabric client on 26.1.2, headless under `xvfb-run` with Mesa
llvmpipe.

### `screenshot` — yes

Under `xvfb-run` with a 1280x720 virtual screen and Mesa llvmpipe, `Screenshot.takeScreenshot`
on the main render target returns a complete frame and the tool delivers it as a PNG blob.
Requesting 854x480 gives 854x480: whatever the framebuffer is, the image is scaled to the size
asked for. The world, the HUD, chat, the hotbar, toasts and open screens are all there; the only
things llvmpipe cost were sound (no OpenAL device) and the narrator, neither of which a bot
needs.

Non-default fonts render. A title in `minecraft:illageralt` and a subtitle in `minecraft:alt`
both come back drawn as runes rather than as latin text, which is the thing the mineflayer bot
cannot check at all — it reads the text and has no idea what it looks like. A custom font from a
server resource pack goes through the same `FontManager` path; only the vanilla alternate fonts
were exercised here.

### `press-dialog-button` — yes, and the server receives it

`/dialog show fabric_bot botcheck:check` opens a `multi_action` dialog; `press-dialog-button`
with `{"label": "Ping back"}` finds the button inside the dialog's scroll container, presses it,
and the server logs `fabric_bot issued server command: /help` while the reply arrives back as
chat events. The click round-trips, headless as well as windowed.

The buttons are not direct children of the screen — a dialog puts its body inside a
`ScrollableLayout`, so the search walks nested `ContainerEventHandler`s.

A `dynamic/custom` action goes out as well and the connection survives it — that is the packet
the mineflayer bot could not encode, where sending it with the wrong definition dropped the
link.

There is one real limit, and it is Mojang's, not ours. Before sending a command from a dialog
the client runs `verifyCommand`; a command that needs permissions or carries a signable argument
opens a *Confirm Command Execution* screen instead. An offline-mode bot has no profile key, so
the accept button on that screen reads **Copy to Chat Screen** — it moves the command to the
chat box rather than running it. `press-dialog-button` presses the confirmation and reports
which button it pressed and where it landed, so the failure is visible:

```
pressed "Say hello", then "Copy to Chat Screen" on Confirm Command Execution
```

`run_command` actions that need no permission (`/help`) are unaffected, and a command the agent
wants to run directly should go through `send-chat` or `run-command`, which do not take that
path.

## What it costs

Headless under Xvfb at 1280x720 with Mesa llvmpipe, one bot idle in a flat world.

| | |
| --- | --- |
| Client JVM RSS | 253-347 MiB, settling around 280-310 MiB |
| Whole container RSS (client + Gradle + Xvfb) | 300-430 MiB |
| CPU while idle at 1fps | 10-20% of a core, with a ~1s burst near 100% each time a frame is drawn |
| `screenshot` round trip, call to result | 1.1-2.1s |
| Immediate tool (`get-position`) round trip | 0.05-0.94s |

The mineflayer baseline is 206 MiB for eight bots sharing one event loop. One fabric bot is
worth roughly ten of them in memory, which is the reason the `kind` argument exists — though it
is a good deal less than the 1-1.5 GiB the plan budgeted.

**The CPU figure is emulated.** The measurements above come from an `linux/amd64` container on an
ARM Mac, because 26.1.2 has no `linux-arm64` LWJGL natives; software rasterisation under binary
translation is the worst case for CPU and says little about a native x86_64 node. Memory is
unaffected by that.

For comparison, the same bot windowed on the macOS desktop with hardware GL: ~440 MiB RSS,
1.2-1.7% of a core idle, `screenshot` 1.33-1.38s, `get-position` 0.93-1.01s.

The second-long floor under most calls is the frame budget. Rendering cannot be turned off —
GLFW and GL are set up in the `Minecraft` constructor — so it runs at 1fps while idle, and a
loop iteration is where queued work runs. A call in flight raises the budget to 60fps and
settling lowers it again, so the cost is paid once per call rather than per tool, and a call
that arrives while the loop is still spinning fast returns in tens of milliseconds.

## How it works

```
mcp-server :8765  ◀── TCP, length-prefixed frames ── RpcClient (dials, reconnects)
                                                          │
                                                     Dispatcher            (RPC thread)
                                                     ├── immediate → client.submit(...)
                                                     └── multi-tick → TaskScheduler
                                                                          │
                                              ClientTickEvents.END_CLIENT_TICK  (client thread)
```

**Nothing on the RPC thread touches a Minecraft object.** A tool that only reads state hands a
body to `Mc.immediate`, which runs it through `client.submit(...)`. A tool that takes several
ticks — connecting, waiting, capturing a frame — is a `Task` on the `TaskScheduler`.

`Task` is three methods: `start`, `tick` returning whether it is done, and `cleanup`. The
scheduler guarantees `cleanup` runs **exactly once**, whether the task finished, threw, was
cancelled, ran past its deadline, or was abandoned because the link dropped. `ScreenshotTool`
depends on that: it raises the frame budget and hides the HUD in `start`, and `cleanup` is the
only thing that puts them back.

Exactly one `result` per call id is enforced in `CallContext` by a single compare-and-set, so a
deadline firing on the timer thread while a task completes on the client thread cannot produce
two.

### Mixins

Two, against a budget of eight. Everything else comes from Fabric API events, which do not break
when mappings move.

| mixin | why |
| --- | --- |
| `MinecraftAccessor` | makes `Minecraft.user` writable so `connect` can change the username, and resets `profileFuture` so the game profile follows it |
| `FramerateLimitTrackerMixin` | returns the frame budget from `getFramerateLimit` |

Chat, ticks, connection lifecycle and screens are all Fabric API.

## Tools

All forty-six a fabric bot is asked for. The rest of the catalogue's sixty-four are answered by
mcp-server from its own buffers and never reach a bot at all. What is not reported is not offered,
and the protocol treats unreported and unimplemented the same way on purpose.

Reading the world and the HUD, walking, digging and placing, windows and slots, crafting and
smelting, fishing, moving between backends, and the two this bot exists for.

The ones worth naming:

| tool | notes |
| --- | --- |
| `screenshot` | PNG blob, scaled to the requested size |
| `press-dialog-button` | matches a label exactly, then by substring; accepts the client's own confirmation, and refuses when that confirmation will not run the command |
| `craft-item` | the server places the recipe; a grid bigger than the player's own says so rather than placing where it cannot fit, and the answer is what the inventory gained |
| `find-blocks` | the whole cube read, sorted by real distance; the outward walk is what lets it stop early, not what orders the answer |
| `read-block-entity` | sign faces as components; anything else is a block entity the client holds decoded, with no tag to write out, and it says so |
| `fish` | the bite is the hook's own synced flag, not a splash somebody guessed at |
| `move-to-position` | A* over the client's own collision shapes; the reason is in the refusal |
| `can-craft`, `get-recipe`, `list-recipes` | the recipe book, and the DTO says so: a client is taught a recipe as the server unlocks it and never told the whole set |
| `switch-server` | the player object being replaced is the arrival; the proxy's refusals only ever arrive as chat |

Text crosses the wire as segments — `{text, font?, color?}` — with the font named, because
which font a piece of HUD is drawn in is game knowledge and joining it into a display string is
the server's job.

A `result.blobs[]` entry is `{id, mime, bytes}` with `name`, `width` and `height` added for an
image, and the blob frames go out before the result that names them, as invariant 4 requires.
A screenshot reports its own dimensions rather than making the server parse a PNG header.

## Building and running

Java 25. Everything goes through Stonecutter, so tasks are per-version.

```sh
./gradlew build       # the active version, currently 26.1.2
./gradlew buildAll    # every version
```

### Adding a Minecraft version

Two files. `settings.gradle.kts` gains the version in `stonecutter.create`, and
`versions/<version>/gradle.properties` gains four lines:

```properties
loader_version=0.19.5
fabric_api_version=0.155.3+26.1.2
java_version=25
mc_compat=~26.1.2
```

Loom's own version is build tooling rather than a game dependency, so it sits in the root
`gradle.properties` and a plugins block reads it from there.

### Running it

**Headless is the default.** The bot is meant to run without a display; `xvfb-run` plus Mesa
llvmpipe is the environment it is developed and checked in.

```sh
docker compose -f dev/compose.yml up -d                    # Paper 26.1.2 on :25578
python3 dev/rpc-harness.py dev/headless.txt --port 8766    # stand-in for mcp-server
docker build --platform linux/amd64 -f dev/Dockerfile.headless -t bot-fabric-headless dev
docker run --rm --platform linux/amd64 \
    -v "$PWD:/work" -v bot-fabric-gradle:/gradle \
    -e GRADLE_USER_HOME=/gradle -e HOME=/gradle -w /work \
    bot-fabric-headless dev/run-headless.sh
```

Screenshots land in `dev/out/<uuid>.png`; open them to judge anything visual. That loop runs the
mod out of the build directory, which is what you want while changing it — the image below is
what runs anywhere else.

`./gradlew runClient` opens a client with a window on the desktop. It is for looking at
something with your own eyes, not the normal loop — and a focused window means whatever is typed
at that keyboard goes into the game, which is its own source of confusion.

`dev/README.md` covers the Paper server, the datapack the dialog checks need, and the harness
script format.

### The bot image

```sh
./gradlew build
mkdir -p dist && cp versions/26.1.2/build/libs/*.jar dist/
docker build --platform linux/amd64 \
    --build-arg MINECRAFT_VERSION=26.1.2 \
    --build-arg LOADER_VERSION=0.19.5 \
    --build-arg FABRIC_API_VERSION=0.155.3+26.1.2 \
    -t bot-fabric:dev .
docker run --rm -v bot-fabric-mc:/mc -e MCP_SERVER_HOST=... bot-fabric:dev
```

The image carries the mod, Fabric API, Fabric loader and a virtual X server — **not Minecraft**.
`docker/fetch-minecraft.py` fills `/mc` from Mojang's version manifest: the client jar, the
libraries this platform's rules select, the asset objects, and Fabric's loader libraries, leaving
a `launch.json` that names the classpath and the main class. Run it as an init container against
a shared volume, or let the entrypoint do it when the volume it finds is empty.

CI publishes one image per Minecraft version, tagged the way the operator composes a reference —
`bot-fabric:<mod version>-mc<minecraft version>`, plus an immutable
`<version>-<timestamp>.g<sha>-mc<minecraft>` and a moving `latest-mc<minecraft>`. The version
matrix comes from `./gradlew printVersions`, so adding a version does not touch the workflow.

### Configuration

| variable | default | |
| --- | --- | --- |
| `MCP_SERVER_HOST` | `127.0.0.1` | where `mcp-server` listens |
| `MCP_SERVER_PORT` | `8765` | |
| `BOT_NAME` | `fabric_bot` | reported in `hello` |
| `RECONNECT_MIN_MS` | `2000` | |
| `BOT_RPC_ENABLED` | `true` | `false` runs a plain client |

Each has a system property twin (`mcagents.rpc.port` and so on) so the Gradle run configs can
set them: `./gradlew runClient -Prpc.port=8766`.

## Following a Minecraft version

26.1.2 and 26.2, and adding the second one is what the Stonecutter setup is for. It cost two files
-- `versions/26.2/gradle.properties` and one line in `settings.gradle.kts` -- and then fifteen
compile errors, because 26.2 moved four things: the screen from a field on `Minecraft` to `Gui`, the
boss bar overlay one level deeper into `Hud`, the render target to `GameRenderer`, and hiding the
HUD from an option to a method on `Hud`.

Those fifteen are now five methods on `Mc`, each with the version split inside it, so the next move
is one edit rather than fifteen. CI takes its matrix from Stonecutter, so a version added to
`settings.gradle.kts` is built, smoke-tested and published without touching the workflow.

All six mixins applied unchanged on 26.2, which is the number that decides what following a version
costs. The client walked, read its action bar, crafted, and took a screenshot on both.

## Known limits

- **A block entity that is not a sign cannot be written out.** A Minecraft client is handed the
  decoded block entity rather than the server's tag, so there is nothing honest to put in `raw`:
  `read-block-entity` on a chest says it carries one this bot cannot read. The other kind of bot
  is sent whatever tag the server chose to send and prints that, so the two answer differently
  about the same chest -- each truthfully about what it holds. Sign faces, which are the reason
  the tool exists, are read from the components the client was given.

- **The recipe tools answer about this bot.** A Minecraft client is sent a recipe as the server
  unlocks it and is never told the whole set, so "no recipe" from here means "not taught to me".
  The DTO carries `onlyWhatTheBotKnows` and mcp-server writes a sentence that says which.
- **Pathfinding is the client's own collision shapes, and nothing more.** `move-to-position` and
  everything that approaches a thing search a route over the blocks the client can see: round a
  wall, up one block, down three. What they will not do is anything a player cannot -- no flying,
  no swimming up, no breaking through -- and a target behind a door or in an unloaded chunk is
  reported as unreachable with the reason, not waited out. Baritone would mean an unofficial fork
  for 26.x, which is how the mineflayer problem started.
- **No authentication on the RPC link.** Same decision as `bot-mineflayer`: the port is meant to
  be reachable only by `mcp-server`.
- **Offline mode only**, and commands that need a chat signature cannot be run from a dialog
  button, as above.
- **`com.mojang:jtracy` has no arm64 build.** It is the profiler the client loads lazily, so on
  arm64 the x86_64 jar stays on the classpath and simply never loads. Nothing else needs it.
- **The client jar is not in any image.** `docker/fetch-minecraft.py` fetches it from Mojang's
  manifest into a cache volume, as an init container or on first start.
- **One bot per process.** `Minecraft.getInstance()`, `RenderSystem` and GLFW are all JVM-global.
- **Yarn does not exist for 26.x.** Mojang mappings, no remapping; see `docs/notes.md`.

`docs/notes.md` has the rest of what was measured, including the things that were guessed wrong
first.
