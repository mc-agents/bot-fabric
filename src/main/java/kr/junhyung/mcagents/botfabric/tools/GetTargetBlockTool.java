package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * What the bot is looking at.
 *
 * <p>A screenshot shows what a place looks like and carries no coordinates, so an agent that has
 * to say where -- put a sign here, dig this, stand an NPC there -- was left reading positions off
 * a picture. Pointing at a thing and asking what it is is how a person does it, and the client
 * already keeps the answer: this is the same pick the crosshair is drawn from.
 *
 * <p>The face matters as much as the block. A block placed from here goes against it, and getting
 * that wrong puts a sign on the floor rather than on the wall.
 */
public final class GetTargetBlockTool extends ReadTool {

    public GetTargetBlockTool() {
        super("get-target-block");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Entity player = Mc.requirePlayer();
        HitResult hit = Mc.client().hitResult;

        JsonObject data = new JsonObject();

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            data.addProperty("hit", "nothing");
            data.add("position", JsonNull.INSTANCE);
            data.addProperty("distance", 0.0);
            return data;
        }

        /* Rounded the way find-entity rounds one, so two tools do not name the same gap twice. */
        data.addProperty("distance",
                Math.round(hit.getLocation().distanceTo(player.getEyePosition()) * 10.0) / 10.0);

        if (hit instanceof BlockHitResult block) {
            data.addProperty("hit", "block");
            data.add("position", Positions.json(block.getBlockPos()));
            data.addProperty("block", BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(block.getBlockPos()).getBlock()).getPath());
            data.addProperty("face", block.getDirection().getName());
            return data;
        }

        Entity looked = ((EntityHitResult) hit).getEntity();

        data.addProperty("hit", "entity");
        data.add("position", Positions.json(looked.position()));
        data.addProperty("label", Entities.label(looked));
        data.addProperty("type", Entities.id(looked));
        data.add("labelComponent", Entities.labelComponent(looked));

        return data;
    }
}
