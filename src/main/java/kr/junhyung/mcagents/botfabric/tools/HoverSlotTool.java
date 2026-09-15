package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.render.Cursor;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.rpc.Blob;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hover one slot of the open window and capture the frame with its tooltip drawn.
 *
 * <p>The cursor is put over the slot's centre through {@link Cursor}, and the screen does what it
 * does for a person: the next frame's extraction takes the slot as hovered and draws the tooltip
 * it builds. The tooltip's lines go back as text beside the frame, read off the screen's own
 * builder, so what is said and what is drawn come from the same place.
 *
 * <p>The frame is taken once the screen reports the slot hovered. A frame is extracted and drawn
 * before the tick that reads the result, so the render target holds a frame that hovered the slot
 * by then, and its tooltip with it. A slot the screen never takes as hovered -- inactive, or under
 * another creative tab -- is given {@link #HOVER_TICKS} and then answered as such, with the lines
 * it would have drawn.
 */
public final class HoverSlotTool implements Tool {

    /** Two seconds: several frames even at the few per second a software rasteriser manages. */
    private static final int HOVER_TICKS = 40;

    private final TaskScheduler scheduler;

    public HoverSlotTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "hover-slot";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        int slot = parsed.integer("slot", -1);
        if (slot < 0) {
            throw ToolException.badArgs("hover-slot needs a slot");
        }
        scheduler.submit(new HoverTask(slot, parsed.integer("width", 854), parsed.integer("height", 480),
                parsed.bool("image", true)), call);
    }

    private static final class HoverTask implements Task {
        private enum Phase { HOVERING, CAPTURING }

        private final int slot;
        private final int width;
        private final int height;
        private final boolean image;
        private final AtomicReference<NativeImage> captured = new AtomicReference<>();

        private AbstractContainerScreen<?> screen;
        private Slot target;
        private boolean boosted;
        private Phase phase = Phase.HOVERING;
        private int ticksHovering;
        private JsonObject data;

        private HoverTask(int slot, int width, int height, boolean image) {
            this.slot = slot;
            this.width = width;
            this.height = height;
            this.image = image;
        }

        @Override
        public String name() {
            return "hover-slot";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayer();
            screen = Windows.require();
            AbstractContainerMenu menu = screen.getMenu();
            if (slot >= menu.slots.size()) {
                throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + slot
                        + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
            }
            target = menu.getSlot(slot);
            FrameBudget.boost();
            boosted = true;
            /* The slot's centre: the screen hovers the box [x-1, x+17) around its 16x16 icon. */
            Cursor.set(Windows.left(screen) + target.x + 8, Windows.top(screen) + target.y + 8);
        }

        @Override
        public boolean tick(CallContext call) throws Exception {
            if (Mc.screen() != screen) {
                throw ToolException.refused("WINDOW_GONE", "the window closed or was replaced while hovering slot "
                        + slot + "; read-window shows what is open now");
            }
            return switch (phase) {
                case HOVERING -> hover(call);
                case CAPTURING -> capture(call);
            };
        }

        private boolean hover(CallContext call) {
            boolean hovered = Windows.hovered(screen) == target;
            if (!hovered && ++ticksHovering < HOVER_TICKS) {
                return false;
            }
            data = read(hovered);
            if (!image) {
                data.add("frame", JsonNull.INSTANCE);
                call.ok("hovered slot " + slot, data);
                return true;
            }
            if (Mc.renderTarget().getColorTexture() == null) {
                throw ToolException.refused("NO_FRAMEBUFFER", "the client has no complete framebuffer");
            }
            Screenshot.takeScreenshot(Mc.renderTarget(), captured::set);
            phase = Phase.CAPTURING;
            return false;
        }

        private boolean capture(CallContext call) throws Exception {
            NativeImage taken = captured.getAndSet(null);
            if (taken == null) {
                return false;
            }
            Frames.Frame frame;
            try (NativeImage source = taken) {
                frame = Frames.of(source, width, height);
            }
            JsonObject size = new JsonObject();
            size.addProperty("width", frame.width());
            size.addProperty("height", frame.height());
            data.add("frame", size);

            call.ok("hovered slot " + slot, data,
                    List.of(Blob.image("image/png", "hover.png", frame.width(), frame.height(), frame.png())));
            return true;
        }

        /**
         * What the frame shows and why, if a tooltip is missing from it. The lines are filled in
         * whenever the slot holds something, drawn or not, so a slot the screen would not hover
         * still says what it would have read.
         */
        private JsonObject read(boolean hovered) {
            ItemStack stack = target.getItem();
            ItemStack carried = screen.getMenu().getCarried();

            String hidden;
            if (stack.isEmpty()) {
                hidden = "empty";
            } else if (!hovered) {
                hidden = "inactive";
            } else if (!carried.isEmpty() && !Windows.tooltipWithCursor(screen, stack)) {
                hidden = "cursor";
            } else {
                hidden = null;
            }

            JsonObject read = new JsonObject();
            read.addProperty("slot", slot);
            read.add("item", Windows.held(stack));
            read.add("cursor", Windows.held(carried));
            read.add("tooltip", stack.isEmpty() ? JsonNull.INSTANCE : tooltip(Windows.tooltip(screen, stack)));
            read.addProperty("hidden", hidden);
            return read;
        }

        private static JsonObject tooltip(List<Component> lines) {
            JsonArray text = new JsonArray();
            JsonArray components = new JsonArray();
            for (Component line : lines) {
                text.add(line.getString());
                components.add(Segments.raw(line));
            }
            JsonObject tooltip = new JsonObject();
            tooltip.add("lines", text);
            tooltip.add("lineComponents", components);
            return tooltip;
        }

        @Override
        public void cleanup(CallContext call) {
            Cursor.clear();
            if (boosted) {
                FrameBudget.release();
                boosted = false;
            }
            NativeImage leftover = captured.getAndSet(null);
            if (leftover != null) {
                leftover.close();
            }
        }
    }
}
