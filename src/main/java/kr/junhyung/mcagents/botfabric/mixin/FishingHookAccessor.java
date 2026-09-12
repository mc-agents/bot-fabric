package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a fish is on the hook.
 *
 * <p>The server syncs this as entity data, so it is a fact the client is told rather than something
 * inferred. The field behind it is private and has no getter, which is the only reason this exists:
 * listening for the splash sound instead would call a bite whenever anything else landed in water
 * nearby, and would miss one whose sound arrived while the client was busy.
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {

    @Accessor("biting")
    boolean botfabric$biting();
}
