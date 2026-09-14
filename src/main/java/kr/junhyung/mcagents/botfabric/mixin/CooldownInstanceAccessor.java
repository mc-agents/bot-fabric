package kr.junhyung.mcagents.botfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tick one cooldown ends on. The record is package-private, so it is reached by name. */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {

    @Accessor("endTime")
    int mcagents$endTime();
}
