package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;

import java.util.Optional;

/**
 * Choose a beacon's effects and confirm them, as its screen's confirm button does.
 *
 * <p>Not press-container-button. That sends one number for the menu to read, and a beacon's menu
 * reads none: the two effects travel together in their own packet, and a primary alone is a
 * different request from a primary with a secondary, not half of one.
 *
 * <p>The confirm button sends the packet and closes the window in the same press, so this does
 * too. The window left open would describe the effects from before until the server's data came
 * back, and a second set from it would pay a second time.
 */
public final class SetBeaconEffectsTool extends ActionTool {

    public SetBeaconEffectsTool() {
        super("set-beacon-effects");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        String primaryName = parsed.string("primary");
        String secondaryName = parsed.string("secondary", null);

        AbstractContainerMenu open = ContainerOptions.require();
        if (!(open instanceof BeaconMenu menu)) {
            throw ToolException.refused("NOT_A_BEACON",
                    "the open window is " + ContainerOptions.type(open) + ", not a beacon");
        }

        Holder<MobEffect> primary = Beacons.effect(primaryName);
        Holder<MobEffect> secondary = secondaryName == null ? null : Beacons.effect(secondaryName);
        Beacons.check(menu, primary, secondary);

        String payment = Items.name(menu.getSlot(Beacons.PAYMENT_SLOT).getItem());

        Mc.requireConnection().send(new ServerboundSetBeaconPacket(Optional.of(primary), Optional.ofNullable(secondary)));
        Mc.requirePlayer().closeContainer();

        return "Set the beacon's primary effect to " + Beacons.named(primary) + secondary(primary, secondary)
                + ", paying one " + payment + ". The window is closed, as the beacon's confirm button closes it.";
    }

    private static String secondary(Holder<MobEffect> primary, Holder<MobEffect> secondary) {
        if (secondary == null) {
            return " with no secondary effect";
        }
        if (secondary.equals(primary)) {
            return " at level II";
        }
        return " and its secondary effect to " + Beacons.named(secondary);
    }
}
