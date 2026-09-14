package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.ClientPacketListenerAccessor;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Say something in chat as the bot.
 *
 * <p>A slash is refused rather than sent. run-command exists because the server's answer to a
 * command is chat that somebody has to collect, and a command sent through here is sent with nobody
 * watching for the reply.
 *
 * <p>A server that takes only signed chat is refused here too. An offline bot has no profile key to
 * sign with, so the server drops the message and answers "Chat disabled due to missing profile
 * public key" -- after this tool had already said it was sent, which left a caller unable to tell a
 * handler that swallowed the message from one that never got it. The server says whether it
 * enforces this when the player logs in.
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

        ClientPacketListener connection = Mc.requireConnection();
        ClientPacketListenerAccessor secure = (ClientPacketListenerAccessor) connection;
        if (secure.mcagents$serverEnforcesSecureChat() && secure.mcagents$chatSession() == null) {
            throw ToolException.refused("CHAT_UNSIGNED",
                    "the server takes only signed chat, and this bot has no profile key to sign with, so it"
                            + " would drop the message. Commands are not signed: run-command still works.");
        }
        connection.sendChat(message);

        return "Sent as " + Mc.requirePlayerEvenIfDead().getGameProfile().name() + ": " + message;
    }
}
