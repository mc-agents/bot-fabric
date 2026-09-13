package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.world.level.Level;

/**
 * The clock the world ticks on, for a feature that only happens at a certain time of day.
 *
 * <p>Not whatever a server draws on its own HUD: that is a scoreboard or an action bar, and it can
 * say anything it likes.
 */
public final class GetWorldStateTool extends ReadTool {

    private static final long DAY = 24000L;

    public GetWorldStateTool() {
        super("get-world-state");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Level level = Mc.requirePlayerEvenIfDead().level();
        long time = level.getDefaultClockTime();

        JsonObject data = new JsonObject();
        data.addProperty("timeOfDay", time % DAY);
        data.addProperty("day", time / DAY);
        /* The phase is the day count modulo the eight the textures cycle through. */
        data.addProperty("moonPhase", (int) ((time / DAY) % 8L));
        data.addProperty("isDay", level.isBrightOutside());
        data.addProperty("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");

        /*
        null, because a Minecraft client is never told the game rule. The clock's rate arrives in
        ClientboundSetTimePacket and is not exposed, and guessing "running" would have this bot
        contradict a mineflayer one that is told outright.
        */
        data.add("doDaylightCycle", com.google.gson.JsonNull.INSTANCE);
        return data;
    }
}
