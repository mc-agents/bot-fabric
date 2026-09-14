package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.nav.Steering;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * Sneak and sprint, which change what a server lets the bot do: a sneaking player does not
 * trigger a pressure plate, and some plugins read the crouch to mean something.
 *
 * <p>Held as keys, which is how the client tells the server. Setting the player's shift flag made
 * the answer say "sneaking: false" and the server see nothing: what the client reads back and what
 * it sends both come from the keys, and the flag is neither.
 *
 * <p>A field left null is left alone. The server sends null when the caller said nothing, so
 * "start sneaking" does not quietly stop a sprint that was already running.
 */
public final class SetStanceTool extends ActionTool {

    public SetStanceTool() {
        super("set-stance");
    }

    @Override
    protected String act(JsonObject args) {
        LocalPlayer player = Mc.requirePlayer();

        if (!args.get("sneak").isJsonNull()) {
            Steering.sneak(args.get("sneak").getAsBoolean());
        }
        if (!args.get("sprint").isJsonNull()) {
            boolean sprint = args.get("sprint").getAsBoolean();
            Steering.sprint(sprint);
            /* Letting go of the key does not stop a sprint already running; the game only starts one with it. */
            if (!sprint) {
                player.setSprinting(false);
            }
        }
        /*
        Now as well as on the next tick, so the answer is the state the server is about to be sent.
        Laid over the keys with the stance taken off, or a crouch let go of would still read as held.
        */
        Input keys = player.input.keyPresses;
        player.input.keyPresses = Steering.over(
                new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), false, false));

        /* Reporting the state and not the change is what makes the tool usable with no arguments. */
        return "sneaking: " + player.isShiftKeyDown()
                + ", sprinting: " + (player.input.keyPresses.sprint() || player.isSprinting());
    }
}
