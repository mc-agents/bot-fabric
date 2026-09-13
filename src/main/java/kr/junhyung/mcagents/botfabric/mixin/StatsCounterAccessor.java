package kr.junhyung.mcagents.botfabric.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Every statistic the server has sent, so they can be listed.
 *
 * <p>The public way in asks for one statistic by name, which answers a question a caller has to
 * already know how to ask. The statistics screen gets its list by walking every registry; that
 * turns up thousands of zeros to find the few dozen the server actually sent.
 */
@Mixin(StatsCounter.class)
public interface StatsCounterAccessor {
    @Accessor("stats")
    Object2IntMap<Stat<?>> mcagents$stats();
}
