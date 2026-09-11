# Notes from building this

Things measured against a real 26.1.2 client and a real Paper server. Each one was a guess
before it was run, and several guesses were wrong.

## Yarn is gone on 26.x

The plan said "Yarn mapping names change between versions, so compile and find out". Yarn does
not have 26.x at all — `meta.fabricmc.net/v2/versions/yarn` stops at `1.21.11`, and
`v2/versions/intermediary/26.1.2` answers `0.0.0`. 26.1+ ships with Mojang's own names and Loom
applies no remapping, which is why this build has no `mappings` line.

So it is `net.minecraft.client.Minecraft`, not `MinecraftClient`. Everything else follows:
`Component`, `ClientPacketListener`, `LocalPlayer`, `AbstractContainerMenu`. Access wideners are
now class tweakers (`classTweaker v2 official` in a `.ct` file); this mod needs neither.

Names that moved and cost a compile round each: `ResourceKey.location()` is now
`identifier()`, `com.mojang.authlib.GameProfile` is a record (`name()`, not `getName()`),
`Style.getFont()` returns a `FontDescription` rather than an identifier, and
`AbstractButton.onPress` takes an `InputWithModifiers` — `new MouseButtonInfo(0, 0)` is the
cheapest one to hand it.

## The frame limiter is on FramerateLimitTracker, not Minecraft

`Minecraft` has no `getFramerateLimit()`. It has `getFramerateLimitTracker()`, and
`FramerateLimitTracker.getFramerateLimit()` is what the main loop consults every frame, after
folding in its own throttle reasons (out of a level: 10fps, iconified: 10, AFK: min(limit, 30)).
`setFramerateLimit(int)` is public but the tracker overwrites it from options, so the mixin
returns the budget from `HEAD` and cancels.

## One fps costs a second of latency per tool call

Tick and frame are separated in the sense that the game still averages 20 ticks a second, but
`Minecraft.run()` runs ticks and a frame in the same loop iteration. At 1fps the loop iterates
once a second and executes ~20 ticks in a burst. Everything queued with `client.submit(...)`
waits for the next iteration, so every immediate tool measured ~950ms.

The fix is in `Dispatcher.track`: a call in flight boosts the budget and settling releases it.
Idle stays at 1fps, work runs at 60. That is also why `screenshot` waits on wall-clock time
rather than tick count — ticks arrive in bursts, frames do not.

## Screenshot readback is asynchronous

`Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` issues
`CommandEncoder.copyTextureToBuffer(..., Runnable, int)`; the consumer fires when the copy
lands, a frame or more later. It is a task, not a read. `NativeImage` has no `asByteArray()` on
26.1.2 — `writeToFile(Path)` is the only PNG encoder, so the tool writes a temp file and reads
it back.

## A dialog button press can open a second screen

`DialogScreen.runAction` sends a `run_command` action through
`ClientPacketListener.sendUnattendedCommand`, which first calls `verifyCommand`:

| result | what the client does |
| --- | --- |
| `NO_ISSUES` | sends `ServerboundChatCommandPacket` |
| `PARSE_ERRORS` | opens a `ConfirmScreen` |
| `PERMISSIONS_REQUIRED` | opens a `ConfirmScreen` |
| `SIGNATURE_REQUIRED` | opens a `ConfirmScreen` whose accept button depends on `ChatAbilities` |

`/help` from a dialog button goes straight out. `/say something` does not: it is op-only and its
message argument is signable, so the client opens *Confirm Command Execution*. An offline-mode
bot has no profile key, so `canSendCommands()` is false and the accept button on that screen is
**Copy to Chat Screen** — it puts the command in the chat box instead of running it.

`press-dialog-button` therefore presses the confirmation and reports exactly which button it
pressed and what screen it landed on, rather than claiming the command ran.

## Dialogs are a datapack registry

`data/<ns>/dialog/*.json` is a dynamic registry, and `/reload` does not rebuild it. The server
has to restart before `/dialog show <player> <ns>:<id>` resolves. `docker cp` of a directory
into an existing directory nests it one level deeper, which looks exactly like the same failure.

## There are no linux-arm64 natives

Mojang's 26.1.2 manifest lists LWJGL 3.4.1 natives for `linux`, `macos`, `macos-arm64`,
`windows`, `windows-arm64` and `windows-x86`. No `linux-arm64`. A bot image on ARM has to
substitute LWJGL's own arm64 builds from Maven Central; everything here ran `linux/amd64`.

## Stray keyboard input reaches a windowed client

A desktop `runClient` has a focused window, and whatever the person at the keyboard types goes
into the game — a stray `/` opens the chat screen and every screen-reading tool then sees
`ChatScreen`. The tick handler closes a chat screen the bot did not open. Headless under Xvfb
there is no keyboard at all, which is the other reason headless is the default.
