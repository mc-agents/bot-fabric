package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * What completes a partial command, which is how to find out what a plugin offers without asking.
 *
 * <p>Answered from the command tree the server sent at login rather than by asking it again. A
 * vanilla client only round-trips for the arguments a server marked as needing it, and the command
 * names -- which is what "/" is for -- are all here.
 */
public final class CompleteCommandTool extends ReadTool {

    public CompleteCommandTool() {
        super("complete-command");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String text = args.get("text").getAsString();
        int limit = args.get("limit").getAsInt();

        ClientPacketListener connection = Mc.requireConnection();
        String command = text.startsWith("/") ? text.substring(1) : text;

        ParseResults<ClientSuggestionProvider> parsed = connection.getCommands()
                .parse(command, connection.getSuggestionsProvider());
        Suggestions suggestions = connection.getCommands()
                .getCompletionSuggestions(parsed).join();

        JsonArray completions = new JsonArray();
        for (Suggestion suggestion : suggestions.getList()) {
            if (completions.size() >= limit) {
                break;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", suggestion.getText());
            entry.add("tooltip", suggestion.getTooltip() == null
                    ? JsonNull.INSTANCE
                    : new com.google.gson.JsonPrimitive(suggestion.getTooltip().getString()));
            completions.add(entry);
        }

        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        data.addProperty("total", suggestions.getList().size());
        data.add("completions", completions);
        return data;
    }
}
