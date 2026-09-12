package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.ResourcePacks;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.rpc.Blob;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class ScreenshotTool implements Tool {
    private static final long SETTLE_MS = 250;

    private final TaskScheduler scheduler;

    public ScreenshotTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "screenshot";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new ScreenshotTask(
                parsed.integer("width", 854),
                parsed.integer("height", 480),
                parsed.bool("hud", true)), call);
    }

    private static final class ScreenshotTask implements Task {
        private final int width;
        private final int height;
        private final boolean hud;
        private final AtomicReference<NativeImage> captured = new AtomicReference<>();

        private long startedAt;
        private boolean hideGuiBefore;
        private boolean requested;
        private boolean boosted;

        private ScreenshotTask(int width, int height, boolean hud) {
            this.width = width;
            this.height = height;
            this.hud = hud;
        }

        @Override
        public String name() {
            return "screenshot";
        }

        @Override
        public void start(CallContext call) {
            startedAt = System.currentTimeMillis();
            FrameBudget.boost();
            boosted = true;
            hideGuiBefore = Mc.hudHidden();
            Mc.hideHud(!hud);
        }

        @Override
        public boolean tick(CallContext call) throws Exception {
            if (System.currentTimeMillis() - startedAt < SETTLE_MS) {
                return false;
            }
            if (!requested) {
                requested = true;
                Minecraft minecraft = Mc.client();
                if (Mc.renderTarget().getColorTexture() == null) {
                    throw ToolException.refused("NO_FRAMEBUFFER", "the client has no complete framebuffer");
                }
                Screenshot.takeScreenshot(Mc.renderTarget(), captured::set);
                return false;
            }

            NativeImage image = captured.getAndSet(null);
            if (image == null) {
                return false;
            }
            try (NativeImage source = image) {
                deliver(call, source);
            }
            return true;
        }

        private void deliver(CallContext call, NativeImage source) throws Exception {
            byte[] png;
            int outWidth = source.getWidth();
            int outHeight = source.getHeight();
            if (outWidth != width || outHeight != height) {
                try (NativeImage scaled = new NativeImage(width, height, false)) {
                    source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
                    outWidth = width;
                    outHeight = height;
                    png = encode(scaled);
                }
            } else {
                png = encode(source);
            }

            call.ok("captured a %dx%d frame (%d bytes)%s"
                            .formatted(outWidth, outHeight, png.length, packWarning()),
                    null, List.of(Blob.image("image/png", "screenshot.png", outWidth, outHeight, png)));
        }

        /**
         * A server that draws its interface with custom glyphs has none of it without the pack, and
         * the client carries on with the missing-character box in place of every glyph. The picture
         * then looks like a broken interface rather than like a pack that never arrived, so the
         * picture says which.
         */
        private static String packWarning() {
            String failure = ResourcePacks.failure();

            return failure == null ? "" : ". The server's resource pack is not loaded ("
                    + failure.toLowerCase(Locale.ROOT).replace('_', ' ')
                    + "), so anything the server draws with custom glyphs is a missing-character box"
                    + " in this frame rather than what a player would see";
        }

        private static byte[] encode(NativeImage image) throws Exception {
            Path file = Files.createTempFile("botfabric-screenshot", ".png");
            try {
                image.writeToFile(file);
                return Files.readAllBytes(file);
            } finally {
                Files.deleteIfExists(file);
            }
        }

        @Override
        public void cleanup(CallContext call) {
            if (boosted) {
                FrameBudget.release();
                boosted = false;
            }
            Mc.hideHud(hideGuiBefore);
            NativeImage leftover = captured.getAndSet(null);
            if (leftover != null) {
                leftover.close();
            }
        }
    }
}
