package kr.junhyung.mcagents.botfabric.nav;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Walks a route, one block at a time, and works out a new one when the world turns out not to
 * match the old one.
 *
 * <p>The search is {@link Path}; this is the part that holds a key down. It aims at the middle of
 * the next block, presses forward, and jumps when the next block is a step up -- which is what a
 * person does, and what a server believes.
 *
 * <p>Recomputing is throttled. A door that opens, a lift that moves, another player in the way: the
 * route stops matching and the answer is a new route, but asking for one every tick would spend the
 * tick on searching rather than on walking.
 */
public final class PathNavigator {

    /** Close enough to a waypoint to start on the next one. */
    private static final double REACHED = 0.65;

    /** Ticks between searches. Twenty is a second, which is faster than anything walks. */
    private static final int RECOMPUTE_TICKS = 20;

    /** Under this in a second of walking is not walking. */
    private static final double PROGRESS = 0.2;

    private static final int PATIENCE_TICKS = 40;

    private List<BlockPos> route;
    private int index;
    private int sinceSearch = RECOMPUTE_TICKS;
    private Vec3 noted;
    private int stuckTicks;

    /** True once the target is within range. The keys are released either way. */
    public boolean step(LocalPlayer player, Vec3 target, double range) {
        if (player.position().distanceTo(target) <= range) {
            Steering.release();
            return true;
        }

        sinceSearch++;

        if (route == null || index >= route.size() || sinceSearch >= RECOMPUTE_TICKS && !onTrack(player)) {
            search(player, target);
        }
        if (route == null || route.isEmpty()) {
            Steering.release();
            return false;
        }

        BlockPos waypoint = route.get(Math.min(index, route.size() - 1));
        Vec3 middle = Vec3.atBottomCenterOf(waypoint);

        if (arrived(player, middle)) {
            index++;
            if (index >= route.size()) {
                /* The route ran out short of the target: the search will say whether there is more. */
                sinceSearch = RECOMPUTE_TICKS;
            }
            return false;
        }

        double dx = middle.x - player.getX();
        double dz = middle.z - player.getZ();

        /* Minecraft's yaw is 0 at south and turns the other way, hence the negated x. */
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot(0.0F);

        boolean up = waypoint.getY() > player.blockPosition().getY();
        Steering.press(new Input(true, false, false, false, up, false, false));

        return false;
    }

    /**
     * Whether the walk has stopped making progress. Measured against the last place it actually got
     * to rather than against the last tick, because a bot pressed against a wall still twitches by
     * a fraction of a block every tick and would never look stuck.
     */
    public boolean stuck(LocalPlayer player) {
        Vec3 now = player.position();

        if (noted == null || now.distanceTo(noted) > PROGRESS) {
            noted = now;
            stuckTicks = 0;
            return false;
        }

        return ++stuckTicks > PATIENCE_TICKS;
    }

    /**
     * Why the walk is not getting anywhere, for the sentence a refusal carries. "No route" and "the
     * route ran out" send a caller to different places: the first is a world the client cannot see
     * through, the second is a way that exists and stops short.
     */
    public String trouble() {
        if (route == null) {
            return "the client could not search a route: the target or the ground under this bot is"
                    + " outside the chunks it has";
        }
        if (route.isEmpty()) {
            return "no route to it over the blocks the client can see";
        }
        return "followed a route of " + route.size() + " block(s) and stopped at step " + index;
    }

    public void stop() {
        Steering.release();
        route = null;
        index = 0;
    }

    private void search(LocalPlayer player, Vec3 target) {
        sinceSearch = 0;
        index = 0;
        route = Path.find(player.level(), player.blockPosition(), BlockPos.containing(target));
    }

    /** Whether the next waypoint is still somewhere this player could walk to from here. */
    private boolean onTrack(LocalPlayer player) {
        if (route == null || index >= route.size()) {
            return false;
        }

        BlockPos waypoint = route.get(index);
        BlockPos feet = player.blockPosition();

        return Math.abs(waypoint.getX() - feet.getX()) <= 2
                && Math.abs(waypoint.getZ() - feet.getZ()) <= 2
                && Math.abs(waypoint.getY() - feet.getY()) <= 2;
    }

    /**
     * Horizontal distance only, and the same height. A waypoint one block up is not arrived at
     * until the jump has landed, or the bot walks on aiming at a block it is standing under.
     */
    private static boolean arrived(LocalPlayer player, Vec3 middle) {
        double dx = middle.x - player.getX();
        double dz = middle.z - player.getZ();

        return Math.sqrt(dx * dx + dz * dz) < REACHED && Math.abs(middle.y - player.getY()) < 1.0;
    }
}
