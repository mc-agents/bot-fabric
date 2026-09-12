package kr.junhyung.mcagents.botfabric.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A route through the blocks the client can see.
 *
 * <p>Walking straight at a target is enough in an empty test world and useless in a built one: on a
 * real server's town the bot got three blocks from its spawn before a wall, whichever direction it
 * was sent. This is the search that gets round the wall.
 *
 * <p>It is the client's own collision shapes being asked, not a reimplementation of them, which is
 * why it is a few hundred lines rather than a few thousand. What it does not do is anything a
 * player cannot: no flying, no swimming up, no breaking through. A step up is one block and a drop
 * is three, because a longer drop hurts.
 */
public final class Path {

    /** How far from the start a route may wander. Past this a caller should teleport instead. */
    private static final int REACH = 64;

    /** Expansions before giving up. A wall with no way round it must not cost a whole tick. */
    private static final int BUDGET = 20_000;

    private static final int STEP_UP = 1;
    private static final int MAX_DROP = 3;

    /** A jump costs more than a stride, so a flat way round is preferred to a way over. */
    private static final double JUMP_COST = 0.6;

    private static final double DIAGONAL = Math.sqrt(2);

    private Path() {
    }

    /**
     * The route from one block to another, nearest-first and ending at the closest reachable block
     * when the target itself cannot be stood on.
     *
     * <p>Empty when there is nothing to walk, which is the caller already being there. Null when
     * the world cannot answer: a target outside the loaded chunks is not unreachable, it is unknown,
     * and the two deserve different sentences.
     */
    public static List<BlockPos> find(Level level, BlockPos from, BlockPos to) {
        if (!level.isLoaded(from) || !level.isLoaded(to)) {
            return null;
        }

        BlockPos start = standable(level, from);
        if (start == null) {
            return null;
        }

        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> cost = new HashMap<>();
        PriorityQueue<Step> queue = new PriorityQueue<>();

        cost.put(start, 0.0);
        queue.add(new Step(start, heuristic(start, to)));

        BlockPos closest = start;
        double closestDistance = heuristic(start, to);
        int expansions = 0;

        while (!queue.isEmpty() && expansions++ < BUDGET) {
            Step current = queue.poll();

            if (current.at.equals(to)) {
                return route(cameFrom, start, to);
            }

            double here = heuristic(current.at, to);
            if (here < closestDistance) {
                closest = current.at;
                closestDistance = here;
            }

            for (BlockPos next : neighbours(level, current.at)) {
                if (next.distManhattan(start) > REACH * 2) {
                    continue;
                }

                double walked = cost.get(current.at) + stride(current.at, next);
                Double known = cost.get(next);

                if (known == null || walked < known) {
                    cost.put(next, walked);
                    cameFrom.put(next, current.at);
                    queue.add(new Step(next, walked + heuristic(next, to)));
                }
            }
        }

        /*
        No way to the target, or the budget ran out. The closest block reached is still worth
        walking to: getting within sight of a thing is most of what a caller wants, and the tool
        says how far short it stopped.
        */
        return closest.equals(start) ? List.of() : route(cameFrom, start, closest);
    }

    private static List<BlockPos> route(Map<BlockPos, BlockPos> cameFrom, BlockPos start, BlockPos end) {
        List<BlockPos> route = new ArrayList<>();

        for (BlockPos at = end; at != null && !at.equals(start); at = cameFrom.get(at)) {
            route.add(at);
        }
        Collections.reverse(route);

        return route;
    }

    /** Octile distance: the cost of the cheapest way there with nothing in it. */
    private static double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());

        return Math.max(dx, dz) + (DIAGONAL - 1) * Math.min(dx, dz) + Math.abs(from.getY() - to.getY());
    }

    private static double stride(BlockPos from, BlockPos to) {
        boolean diagonal = from.getX() != to.getX() && from.getZ() != to.getZ();
        double flat = diagonal ? DIAGONAL : 1.0;

        return to.getY() > from.getY() ? flat + JUMP_COST : flat;
    }

    /**
     * Where a player standing here could stand next.
     *
     * <p>A diagonal needs both of the straight ways beside it to be clear, or the bot tries to cut
     * a corner it cannot fit through and stops dead against it.
     */
    private static List<BlockPos> neighbours(Level level, BlockPos at) {
        List<BlockPos> found = new ArrayList<>(8);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos straight = landing(level, at, at.relative(facing));
            if (straight != null) {
                found.add(straight);
            }
        }

        for (Direction first : Direction.Plane.HORIZONTAL) {
            Direction second = first.getClockWise();
            if (landing(level, at, at.relative(first)) == null || landing(level, at, at.relative(second)) == null) {
                continue;
            }
            BlockPos corner = landing(level, at, at.relative(first).relative(second));
            if (corner != null) {
                found.add(corner);
            }
        }

        return found;
    }

    /**
     * Where a step towards a column actually puts the player: level with it, one up, or down to
     * whatever is below. Null when there is no room or the drop is too far.
     */
    private static BlockPos landing(Level level, BlockPos from, BlockPos towards) {
        BlockPos level0 = towards.atY(from.getY());

        if (room(level, level0)) {
            if (solid(level, level0.below())) {
                return level0;
            }
            /* Nothing underfoot: fall to the first floor within reach, and no further. */
            for (int drop = 1; drop <= MAX_DROP; drop++) {
                BlockPos lower = level0.below(drop);
                if (solid(level, lower.below())) {
                    return room(level, lower) ? lower : null;
                }
                if (!room(level, lower)) {
                    return null;
                }
            }
            return null;
        }

        /* Blocked at foot height: a single step up, if there is headroom on both sides of it. */
        BlockPos up = level0.above(STEP_UP);

        if (solid(level, level0) && room(level, up) && clear(level, from.above(2))) {
            return up;
        }
        return null;
    }

    /** Two blocks of headroom, which is what a player takes up. */
    private static boolean room(Level level, BlockPos feet) {
        return clear(level, feet) && clear(level, feet.above());
    }

    private static boolean clear(BlockGetter level, BlockPos at) {
        return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
    }

    private static boolean solid(BlockGetter level, BlockPos at) {
        return !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
    }

    /**
     * The block a player standing here occupies.
     *
     * <p>Up as well as down. A position read off an entity resting exactly on a block boundary
     * comes back as the solid block underfoot rather than the air above it, and a search that only
     * looked down then found no ground at all and reported that it could not search -- which is a
     * confusing thing to be told while standing on grass.
     */
    private static BlockPos standable(Level level, BlockPos at) {
        for (int up = 0; up <= 2; up++) {
            BlockPos feet = at.above(up);
            if (room(level, feet) && solid(level, feet.below())) {
                return feet;
            }
        }
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos feet = at.below(drop);
            if (room(level, feet) && solid(level, feet.below())) {
                return feet;
            }
        }
        return room(level, at) ? at : null;
    }

    private record Step(BlockPos at, double estimate) implements Comparable<Step> {

        @Override
        public int compareTo(Step other) {
            return Double.compare(estimate, other.estimate);
        }
    }
}
