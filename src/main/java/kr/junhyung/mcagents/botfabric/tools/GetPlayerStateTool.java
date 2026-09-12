package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/** Health, food, experience and where the bot is, for deciding whether a check can even run. */
public final class GetPlayerStateTool extends ReadTool {

    public GetPlayerStateTool() {
        super("get-player-state");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        LocalPlayer player = Mc.requirePlayer();
        BlockPos block = player.blockPosition();

        JsonObject experience = new JsonObject();
        experience.addProperty("level", player.experienceLevel);
        experience.addProperty("progress", player.experienceProgress);
        experience.addProperty("points", player.totalExperience);

        JsonObject position = new JsonObject();
        position.addProperty("x", block.getX());
        position.addProperty("y", block.getY());
        position.addProperty("z", block.getZ());

        JsonObject data = new JsonObject();
        data.addProperty("health", player.getHealth());
        data.addProperty("food", player.getFoodData().getFoodLevel());
        data.addProperty("saturation", player.getFoodData().getSaturationLevel());
        data.add("experience", experience);
        data.addProperty("gameMode", Mc.client().gameMode.getPlayerMode().getName());
        data.addProperty("dimension", player.level().dimension().identifier().getPath());
        data.add("position", position);

        /*
        Only while it is being spent. A player who is not underwater has a full air supply and no
        reason to be told about it, and mcp-server renders null as "full".
        */
        int air = player.getAirSupply();
        if (air < player.getMaxAirSupply()) {
            data.addProperty("oxygen", air);
        } else {
            data.add("oxygen", com.google.gson.JsonNull.INSTANCE);
        }
        return data;
    }
}
