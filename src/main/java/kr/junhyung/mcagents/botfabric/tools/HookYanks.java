package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.Mc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * When the server last pulled the bot's own bobber under.
 *
 * <p>A vanilla bite does two things to the hook: it sets the synced biting flag and it throws the
 * bobber down, at between 0.24 and 0.4 blocks a tick. A plugin that runs its own fishing does the second
 * and not the first -- the flag belongs to vanilla's loot roll, which such a plugin has turned off --
 * so a bot reading only the flag waited out every bite. The pull arrives as a motion packet for the
 * hook, which is the server saying so, and nothing else sends a bobber that is already floating down
 * that fast.
 */
public final class HookYanks {

    /** Half of vanilla's weakest bite. A floating bobber bobs at a few hundredths. */
    private static final double DOWN = -0.12;

    /** Client thread only. By entity id, the game time of the last pull. */
    private static final Map<Integer, Long> PULLED = new HashMap<>();

    private HookYanks() {
    }

    /** Called on the client thread once the server's motion for an entity has been applied. */
    public static void moved(int entityId, Vec3 movement) {
        if (movement.y > DOWN || Mc.client().level == null || Mc.client().player == null) {
            return;
        }
        Entity entity = Mc.client().level.getEntity(entityId);
        if (entity instanceof FishingHook hook && hook.getPlayerOwner() == Mc.client().player) {
            PULLED.put(entityId, Mc.client().level.getGameTime());
        }
    }

    /** The game time the hook was last pulled under, or {@link Long#MIN_VALUE} if it never was. */
    static long pulledAt(FishingHook hook) {
        return PULLED.getOrDefault(hook.getId(), Long.MIN_VALUE);
    }

    static void forget() {
        PULLED.clear();
    }
}
