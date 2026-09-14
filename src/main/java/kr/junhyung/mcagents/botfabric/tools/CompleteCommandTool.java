package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * What completes a partial command, which is how to find out what a plugin offers without asking.
 *
 * <p>Answered from the command tree the server sent at login where it can be. A vanilla client only
 * round-trips for the arguments a server marked as needing it, and the command names -- which is
 * what "/" is for -- are all here.
 *
 * <p>Not a {@link kr.junhyung.mcagents.botfabric.tool.ReadTool}. An argument the server completes
 * itself is a future that the answering packet completes, and packets are handled on the client
 * thread. Joining that future on the client thread waited for a packet the thread could no longer
 * handle: the client froze, every later call timed out behind it, and so did restart-bot. A server
 * that never answers -- a plugin with nothing to offer for this player -- froze it the same way.
 */
public final class CompleteCommandTool implements Tool {

    /** Short of the call's deadline, so a server that never answers is reported as that. */
    private static final long SPARE_MS = 500;

    @Override
    public String name() {
        return "complete-command";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        String text = args.get("text").getAsString();
        int limit = args.get("limit").getAsInt();
        long wait = Math.max(1, call.deadlineMs() - SPARE_MS);

        Mc.immediate(call, () -> {
            ClientPacketListener connection = Mc.requireConnection();
            String command = text.startsWith("/") ? text.substring(1) : text;
            ParseResults<ClientSuggestionProvider> parsed = connection.getCommands()
                    .parse(command, connection.getSuggestionsProvider());
            CompletableFuture<Suggestions> asked = connection.getCommands().getCompletionSuggestions(parsed);

            asked.orTimeout(wait, TimeUnit.MILLISECONDS).whenComplete((suggestions, thrown) -> {
                if (thrown instanceof TimeoutException) {
                    call.fail(ToolError.TOOL, "COMPLETION_TIMEOUT",
                            "the server did not answer what completes " + text + " within " + wait + "ms", true);
                } else if (thrown != null) {
                    call.fail(thrown);
                } else {
                    call.ok(name(), answer(text, limit, suggestions));
                }
            });
        });
    }

    private static JsonObject answer(String text, int limit, Suggestions suggestions) {
        JsonArray completions = new JsonArray();
        for (Suggestion suggestion : suggestions.getList()) {
            if (completions.size() >= limit) {
                break;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", suggestion.getText());
            entry.add("tooltip", suggestion.getTooltip() == null
                    ? JsonNull.INSTANCE
                    : new JsonPrimitive(suggestion.getTooltip().getString()));
            completions.add(entry);
        }

        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        data.addProperty("total", suggestions.getList().size());
        data.add("completions", completions);
        return data;
    }
}
