package kr.junhyung.mcagents.botfabric.mixin;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The progress the server has sent, per advancement.
 *
 * <p>The public way in is a listener, and there is one slot for it: the advancements screen takes
 * it when it opens and drops it when it closes. Registering one here would be undone the first
 * time anybody looked at that screen, and would break the screen while it lasted.
 */
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {
    @Accessor("progress")
    Map<AdvancementHolder, AdvancementProgress> mcagents$progress();
}
