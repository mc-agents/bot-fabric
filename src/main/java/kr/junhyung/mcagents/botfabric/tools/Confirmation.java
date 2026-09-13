package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.ConfirmScreenAccessor;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/**
 * The client's own "are you sure", which anything that runs a command can raise.
 *
 * <p>Pressing a dialog button raises it and so does clicking a chat line, and the two used to know
 * different amounts about it: one accepted it and the other left it on the screen, where it stayed
 * in front of everything the next call tried to do.
 */
final class Confirmation {

    /*
    The client will not run every command it is asked to. One that sends chat as the player needs a
    signature it can only produce from the chat screen, so the confirmation offers to copy the
    command somewhere instead of running it: pressing what is on offer runs nothing. These two say
    whether the confirmation is about a command and whether the button on it is the one that runs.
    */
    private static final Component CONFIRM_TITLE = Component.translatable("multiplayer.confirm_command.title");
    private static final Component RUN_COMMAND = Component.translatable("multiplayer.confirm_command.run_command");

    /** What the client asked about, and what it took to say yes. */
    record Accepted(String button, String title) {

        String describe() {
            return "\"" + button + "\" on " + title;
        }
    }

    private Confirmation() {
    }

    static ConfirmScreen showing() {
        return Mc.screen() instanceof ConfirmScreen confirm ? confirm : null;
    }

    /**
     * Say yes to it, or refuse in the words of what is actually on offer.
     *
     * <p>Null while the button is not clickable yet, which it is not for the first moments of a
     * confirmation the client wants read. The caller decides how long that is worth waiting.
     */
    static Accepted accept(ConfirmScreen screen, String what) {
        Button yes = ((ConfirmScreenAccessor) screen).mcagents$yesButton();

        if (yes == null) {
            throw ToolException.refused("NO_CONFIRM_BUTTON",
                    "the client asked to confirm \"" + what + "\" but offered no button");
        }
        if (!yes.isActive()) {
            return null;
        }

        String offered = yes.getMessage().getString();

        if (screen.getTitle().getString().equals(CONFIRM_TITLE.getString())
                && !offered.equals(RUN_COMMAND.getString())) {
            Mc.setScreen(null);
            throw ToolException.refused("COMMAND_NOT_RUN", "\"" + what + "\" asks the client to run"
                    + " a command and it will not: all it offers is \"" + offered + "\". A command"
                    + " that sends chat as the player can only be run from the chat screen, so an"
                    + " action built on one does nothing when pressed. This is the server's to fix,"
                    + " not the bot's.");
        }

        Accepted accepted = new Accepted(offered, screen.getTitle().getString());
        yes.onPress(new MouseButtonInfo(0, 0));

        return accepted;
    }
}
