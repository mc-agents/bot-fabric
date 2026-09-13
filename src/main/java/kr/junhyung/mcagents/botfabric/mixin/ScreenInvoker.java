package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The client's own handling of a click on a chat line.
 *
 * <p>A server writes a quest's choices and half its menus as chat with a click event on them, and
 * pressing one is a thing a player does. Running the command ourselves would be a guess at what
 * the client does with it; this is what it does.
 *
 * <p>The game variant on purpose. It handles the two events that happen inside the game -- running
 * a command and opening a dialog -- and knows nothing about opening a URL or a file, so a line
 * from a server cannot send this bot's host to a browser. That is a property of which method is
 * called rather than a check that could be forgotten.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("defaultHandleGameClickEvent")
    static void mcagents$handleGameClick(ClickEvent event, Minecraft minecraft, Screen screen) {
        throw new AssertionError("the mixin did not apply");
    }
}
