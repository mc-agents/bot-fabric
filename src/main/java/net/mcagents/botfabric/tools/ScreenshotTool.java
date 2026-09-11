package net.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.mcagents.botfabric.Mc;
import net.mcagents.botfabric.render.FrameBudget;
import net.mcagents.botfabric.rpc.Blob;
import net.mcagents.botfabric.rpc.CallContext;
import net.mcagents.botfabric.task.Task;
import net.mcagents.botfabric.task.TaskScheduler;
import net.mcagents.botfabric.tool.Args;
import net.mcagents.botfabric.tool.Tool;
import net.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        return "sha256:14c6fbf3f34294fb5f822199cd97c9a6347985795cb30a13949cc53646b9b26f";
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
            hideGuiBefore = Mc.client().options.hideGui;
            Mc.client().options.hideGui = !hud;
        }

        @Override
        public boolean tick(CallContext call) throws Exception {
            if (System.currentTimeMillis() - startedAt < SETTLE_MS) {
                return false;
            }
            if (!requested) {
                requested = true;
                Minecraft minecraft = Mc.client();
                if (minecraft.getMainRenderTarget().getColorTexture() == null) {
                    throw ToolException.refused("NO_FRAMEBUFFER", "the client has no complete framebuffer");
                }
                Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), captured::set);
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

            JsonObject data = new JsonObject();
            data.addProperty("width", outWidth);
            data.addProperty("height", outHeight);
            data.addProperty("bytes", png.length);

            call.ok("captured a %dx%d frame (%d bytes)".formatted(outWidth, outHeight, png.length),
                    data, List.of(Blob.png("screenshot.png", png)));
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
            Mc.client().options.hideGui = hideGuiBefore;
            NativeImage leftover = captured.getAndSet(null);
            if (leftover != null) {
                leftover.close();
            }
        }
    }
}
