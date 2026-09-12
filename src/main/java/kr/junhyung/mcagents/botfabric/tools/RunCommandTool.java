package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;

/**
 * Send a slash command as the bot.
 *
 * <p>Sending is the whole of the bot's half. What the server says back is chat, and the buffer that
 * catches it belongs to mcp-server, which marks it before the call and reads past the mark
 * afterwards -- that is what the catalogue's {@code compose} route means. This used to collect the
 * chat as well and wait a second for it, so a caller was shown the reply twice and paid for the
 * wait on both sides.
 */
public final class RunCommandTool extends ActionTool {

    public RunCommandTool() {
        super("run-command");
    }

    @Override
    protected String act(JsonObject args) {
        String command = new Args(args).string("command");
        String slashed = command.startsWith("/") ? command : "/" + command;

        Mc.requireConnection().sendCommand(slashed.substring(1));

        return "Ran " + slashed + ".";
    }
}
