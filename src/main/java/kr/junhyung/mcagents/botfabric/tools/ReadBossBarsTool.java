package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.gui.components.LerpingBossEvent;

/** Boss bars the server has put on the HUD, which servers use as a progress display. */
public final class ReadBossBarsTool extends ReadTool {

    public ReadBossBarsTool() {
        super("read-boss-bars");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        BossHealthOverlayAccessor overlay =
                (BossHealthOverlayAccessor) Mc.bossOverlay();

        JsonArray bars = new JsonArray();
        for (LerpingBossEvent event : overlay.mcagents$events().values()) {
            JsonObject bar = new JsonObject();
            bar.addProperty("title", event.getName().getString());
            bar.addProperty("progress", event.getProgress());
            bar.addProperty("color", event.getColor().getName());
            bar.addProperty("dividers", dividers(event.getOverlay()));
            /* Stacked labels, the same as an action bar: joined into one string they run together. */
            bar.add("segments", Segments.of(event.getName()));
            /* And the component itself, so the flattening lives in one place rather than two. */
            bar.add("component", Segments.raw(event.getName()));
            bars.add(bar);
        }

        JsonObject data = new JsonObject();
        data.add("bars", bars);
        return data;
    }

    /** The notch count a style draws, which is what a reader counts. Zero for a plain bar. */
    private static int dividers(net.minecraft.world.BossEvent.BossBarOverlay overlay) {
        return switch (overlay) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            case PROGRESS -> 0;
        };
    }
}
