package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.Blob;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Screenshot;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One frame, taken off the render target a tick after asking.
 *
 * <p>The screenshot is handed over once the frame in flight has been drawn, which is a later tick
 * than the one that asked. A task starts the grab, polls it each tick, and closes it in cleanup so
 * a frame the call never collected is not left holding native memory.
 */
final class FrameGrab implements AutoCloseable {

    record Taken(JsonObject size, Blob blob) {
    }

    private final AtomicReference<NativeImage> captured = new AtomicReference<>();
    private final String fileName;
    private final int width;
    private final int height;

    private FrameGrab(String fileName, int width, int height) {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
    }

    static FrameGrab start(String fileName, int width, int height) {
        if (Mc.renderTarget().getColorTexture() == null) {
            throw ToolException.refused("NO_FRAMEBUFFER", "the client has no complete framebuffer");
        }
        FrameGrab grab = new FrameGrab(fileName, width, height);
        Screenshot.takeScreenshot(Mc.renderTarget(), grab.captured::set);
        return grab;
    }

    /** The frame once it has arrived, or null while it has not. */
    Taken poll() throws Exception {
        NativeImage taken = captured.getAndSet(null);
        if (taken == null) {
            return null;
        }
        Frames.Frame frame;
        try (NativeImage source = taken) {
            frame = Frames.of(source, width, height);
        }
        JsonObject size = new JsonObject();
        size.addProperty("width", frame.width());
        size.addProperty("height", frame.height());

        return new Taken(size, Blob.image("image/png", fileName, frame.width(), frame.height(), frame.png()));
    }

    @Override
    public void close() {
        NativeImage leftover = captured.getAndSet(null);
        if (leftover != null) {
            leftover.close();
        }
    }
}
