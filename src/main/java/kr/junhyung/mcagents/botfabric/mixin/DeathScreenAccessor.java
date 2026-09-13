package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What killed the bot, as the death screen says it.
 *
 * <p>The line is drawn under "You Died!" and kept nowhere else on the client: the chat copy is
 * subject to the server's showDeathMessages rule, and a refusal that says "dead" without saying of
 * what leaves a caller guessing whether the check they were running is what killed it.
 */
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
    @Accessor("causeOfDeath")
    Component mcagents$causeOfDeath();
}
