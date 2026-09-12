package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click an entity, which is what opens an NPC's dialogue or a villager's trades.
 *
 * <p>The hit result is not optional: the client's interact sends where on the entity the click
 * landed, and dereferences it without checking. Aiming at the eyes is where a player looking at an
 * NPC would click.
 */
public final class InteractEntityTool extends ActionTool {

    public InteractEntityTool() {
        super("interact-entity");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        LocalPlayer player = Mc.requirePlayer();
        Entity target = Entities.require(player, parsed.string("name"),
                args.get("maxDistance").getAsDouble());

        Reach.require(player, target);

        Vec3 eyes = target.getEyePosition();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, eyes);
        Mc.client().gameMode.interact(player, target, new EntityHitResult(target, eyes),
                InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);

        return "Right-clicked " + Entities.named(target) + ".";
    }
}
