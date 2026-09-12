package kr.junhyung.mcagents.botfabric.nav;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Walks straight at a target, stepping up what it can and giving up when it cannot.
 *
 * <p>Deliberately not a pathfinder. Baritone's last official release is for 1.21.5, so following
 * 26.x would mean depending on an unofficial fork -- which is how the mineflayer problem started.
 * Nearly every caller is already within sight of what it wants to reach: a container across the
 * room, an NPC a few blocks away, a button on the wall. Something that walks the last eight blocks
 * covers that, and an A* over the real collision shapes can replace it behind this class without
 * any tool noticing.
 *
 * <p>What it does not do is go around anything. A wall in the way is reported as not having got
 * there, with how far it got, which is a caller's cue to teleport instead of a silent wait.
 */
public final class DirectNavigator {

    /** Under this in a second of walking is not walking. */
    private static final double PROGRESS_PER_TICK = 0.01;

    private static final int PATIENCE_TICKS = 20;

    private Vec3 noted;
    private int stuckTicks;

    /** True once the target is within range. The keys are released either way. */
    public boolean step(LocalPlayer player, Vec3 target, double range) {
        if (player.position().distanceTo(target) <= range) {
            Steering.release();
            return true;
        }

        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();

        /* Minecraft's yaw is 0 at south and turns the other way, hence the negated x. */
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot(0.0F);
        Steering.press(new Input(true, false, false, false, blocked(player), false, false));

        return false;
    }

    /**
     * Whether the walk has stopped making progress. Measured against the last place it actually got
     * to rather than against the last tick, because a bot walking into a wall still twitches by a
     * fraction of a block every tick and would never look stuck.
     */
    public boolean stuck(LocalPlayer player) {
        Vec3 now = player.position();

        if (noted == null || now.distanceTo(noted) > PROGRESS_PER_TICK * PATIENCE_TICKS) {
            noted = now;
            stuckTicks = 0;
            return false;
        }

        return ++stuckTicks > PATIENCE_TICKS;
    }

    public void stop() {
        Steering.release();
    }

    /**
     * A block at knee height with room above it is a step, and jumping takes it. Anything taller is
     * a wall, and jumping at a wall is what being stuck looks like.
     */
    private static boolean blocked(LocalPlayer player) {
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(0.6, 0.0, 0.6));
        BlockPos foot = BlockPos.containing(ahead.x, player.getY(), ahead.z);

        if (player.level().getBlockState(foot).getCollisionShape(player.level(), foot).isEmpty()) {
            return false;
        }

        BlockPos head = foot.above(2);

        return player.level().getBlockState(head).getCollisionShape(player.level(), head).isEmpty();
    }
}
