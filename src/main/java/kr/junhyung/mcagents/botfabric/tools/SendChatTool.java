package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/**
 * Say something in chat as the bot.
 *
 * <p>A slash is refused rather than sent. run-command exists because the server's answer to a
 * command is chat that somebody has to collect, and a command sent through here is sent with nobody
 * watching for the reply.
 */
public final class SendChatTool extends ActionTool {

    public SendChatTool() {
        super("send-chat");
    }

    @Override
    protected String act(JsonObject args) {
        String message = new Args(args).string("message");

        if (message.startsWith("/")) {
            throw ToolException.refused("NOT_A_COMMAND",
                    "send-chat is for plain chat. Use run-command to send a slash command.");
        }

        Mc.requireConnection().sendChat(message);

        return "Sent as " + Mc.requirePlayerEvenIfDead().getGameProfile().name() + ": " + message;
    }
}
