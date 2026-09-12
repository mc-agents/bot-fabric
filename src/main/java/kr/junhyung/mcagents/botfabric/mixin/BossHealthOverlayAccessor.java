package kr.junhyung.mcagents.botfabric.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The boss bars the HUD is holding.
 *
 * <p>BossHealthOverlay keeps them privately and only draws them; there is no way to ask what is on
 * screen. read-boss-bars is a reading tool, so it needs the list rather than the pixels.
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> mcagents$events();
}
