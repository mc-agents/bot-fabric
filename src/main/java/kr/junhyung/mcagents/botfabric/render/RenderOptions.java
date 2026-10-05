package kr.junhyung.mcagents.botfabric.render;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * What a bot's client should be drawing, which is much less than what a player's should.
 *
 * <p>A fresh client writes its own defaults -- render distance 16, four mipmap levels, every
 * particle -- and a bot inherits them. On a desktop that costs nothing to speak of because the
 * textures and the chunk meshes live on the graphics card. In a container there is no card: Mesa's
 * software rasteriser keeps all of it in system memory, and a measured bot pod sat at 1.7GiB with
 * 1.2GiB of that outside the Java heap.
 *
 * <p>None of this is a setting a caller would miss. The screenshot is 854x480 of whatever the bot
 * is standing in front of, and a client cannot see further than the server sends anyway.
 */
public final class RenderOptions {

    /**
     * Chunks. A fresh client picks sixteen; a bot has no use for them, and a client cannot see
     * further than the server sends anyway. Eight is past what most servers send and well past
     * what an 854x480 screenshot shows.
     */
    private static final int DEFAULT_DISTANCE = 8;

    /**
     * The lowest simulation distance the client will take, and the reason the two options below are
     * not set from the same number.
     *
     * <p>Render distance accepts 2..32 and simulation distance 5..32, which is a debug flag's doing:
     * the client builds the range as {@code DEBUG_ALLOW_LOW_SIM_DISTANCE ? 2 : 5} and the flag is
     * off in a release. {@code OptionInstance.set} does not clamp what falls outside a range -- it
     * validates, and on a failure keeps the option's own initial value, which for this one is
     * twelve. So asking for two gave a bot a simulation distance of twelve: higher than the eight
     * it would have had by saying nothing, in the one setting it was being lowered to save memory.
     *
     * <p>Clamping here rather than refusing the value, because the two are one knob to a caller and
     * the thing it is for is the chunk meshes, which are render distance's. A bot at 2 loads
     * twenty-five columns against a hundred and twenty-one at 5, and that is worth keeping even
     * where the simulation cannot follow it down.
     */
    private static final int SIMULATION_MINIMUM = 5;

    private RenderOptions() {
    }

    public static void apply(Options options, int renderDistance) {
        /*
        The preset first: it carries values for the individual options, so setting it afterwards
        put the render distance back to 8 and the mipmaps back to 2 -- half of what a fresh client
        picks, and not what was asked for either.
        */
        options.graphicsPreset().set(GraphicsPreset.FAST);

        options.renderDistance().set(renderDistance);
        options.simulationDistance().set(simulationFrom(renderDistance));
        /*
        Mipmaps are for a texture seen at a distance and they make every atlas a third larger
        again. A bot looks at what is in front of it.
        */
        options.mipmapLevels().set(0);
        options.cloudStatus().set(CloudStatus.OFF);
        options.particles().set(ParticleStatus.MINIMAL);
        options.ambientOcclusion().set(false);
        options.entityShadows().set(false);
        /* The read-effects feed is built from the packets, so drawing fewer of them reports none. */
    }

    /** The simulation distance to ask for alongside a render distance. */
    public static int simulationFrom(int renderDistance) {
        return Math.max(renderDistance, SIMULATION_MINIMUM);
    }

    public static int distanceFrom(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DISTANCE;
        }
        try {
            return Math.clamp(Integer.parseInt(configured.trim()), 2, 32);
        } catch (NumberFormatException ignored) {
            return DEFAULT_DISTANCE;
        }
    }
}
