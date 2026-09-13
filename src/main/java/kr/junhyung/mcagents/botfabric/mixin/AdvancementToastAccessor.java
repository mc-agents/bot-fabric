package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The advancement a toast was put up for.
 *
 * <p>The toast only draws it. Its title is what a player reads, but the id is what a caller can
 * name, and a server that draws its quest titles in the pack's own glyphs leaves the id as the only
 * part of the toast worth matching on.
 */
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
    @Accessor("advancement")
    AdvancementHolder mcagents$advancement();
}
