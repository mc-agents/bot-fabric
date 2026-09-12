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
        options.simulationDistance().set(renderDistance);
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
