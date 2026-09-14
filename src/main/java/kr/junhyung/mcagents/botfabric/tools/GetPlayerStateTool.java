package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Health, food, experience and where the bot is, for deciding whether a check can even run.
 *
 * <p>Answers for a dead bot too. It is the tool a caller reaches for after every other one has said
 * the bot is dead, and refusing it as well would leave nothing that says of what.
 */
public final class GetPlayerStateTool extends ReadTool {

    public GetPlayerStateTool() {
        super("get-player-state");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        LocalPlayer player = Mc.requirePlayerEvenIfDead();
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

        boolean dead = Mc.dead(player);
        Component cause = dead ? Mc.causeOfDeath() : null;
        data.addProperty("dead", dead);
        data.addProperty("causeOfDeath", cause == null ? null : cause.getString());
        data.add("causeOfDeathComponent", Segments.raw(cause));

        /*
        Only while it is being spent. A player who is not underwater has a full air supply and no
        reason to be told about it, and mcp-server renders null as "full".
        */
        int air = player.getAirSupply();
        if (air < player.getMaxAirSupply()) {
            data.addProperty("oxygen", air);
        } else {
            data.add("oxygen", JsonNull.INSTANCE);
        }

        data.add("vehicle", vehicle(player));
        return data;
    }

    /**
     * The bottom of the stack the bot rides, and the entity it actually sits on when that is another
     * one. A plugin seats a player on an invisible entity riding the mount, and the mount is what
     * moves while the seat is what the player was put on; either one alone describes half of it.
     */
    private static JsonElement vehicle(LocalPlayer player) {
        Entity seat = player.getVehicle();
        if (seat == null) {
            return JsonNull.INSTANCE;
        }
        Entity root = player.getRootVehicle();

        JsonObject vehicle = ridden(root);
        vehicle.add("seat", seat == root ? JsonNull.INSTANCE : ridden(seat));
        return vehicle;
    }

    private static JsonObject ridden(Entity entity) {
        JsonObject ridden = new JsonObject();
        ridden.addProperty("type", Entities.id(entity));
        ridden.addProperty("id", entity.getId());
        return ridden;
    }
}
