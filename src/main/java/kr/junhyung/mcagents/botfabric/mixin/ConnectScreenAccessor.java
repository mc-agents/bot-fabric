package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The connection a join in progress is on, which only the connect screen holds: during the login
 * and the configuration there is no level and no play listener to ask, and the screen's own Cancel
 * button is the one thing that closes it.
 */
@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Accessor("connection")
    Connection mcagents$connection();

    @Accessor("aborted")
    void mcagents$setAborted(boolean aborted);
}
