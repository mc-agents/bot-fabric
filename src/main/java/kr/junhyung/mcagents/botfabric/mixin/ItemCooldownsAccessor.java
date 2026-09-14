package kr.junhyung.mcagents.botfabric.mixin;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The cooldowns the server has started, and the clock they are measured against.
 *
 * <p>The public way in answers "is it cooling down" and "how far through", and neither is how long
 * is left: that needs the tick a cooldown ends on, which only the map holds. The values are
 * {@link CooldownInstanceAccessor}s, typed loosely here because their class is not visible.
 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {

    @Accessor("cooldowns")
    Map<Identifier, ?> mcagents$cooldowns();

    @Accessor("tickCount")
    int mcagents$tickCount();
}
