package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.text.Dialogs;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.world.item.ItemStack;

/**
 * Every feed the bot sees, pushed as it arrives.
 *
 * <p>Nothing is kept here. The ring buffer a caller reads is mcp-server's, and it is the server that
 * decides what counts as arriving after a command was sent. A second buffer on this side used to
 * exist for run-command, which made the caller read the server's reply twice.
 */
public final class EventPump {

    private final FeedFolder folder;

    public EventPump(RpcClient client) {
        this.folder = new FeedFolder(client::send, System::currentTimeMillis);
    }

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                emit("chat", sender == null ? "system" : sender.name(), message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                emit(overlay ? "actionBar" : "chat", "system", message));

        /* Titles, sounds and particles have no Fabric API event; a mixin hands them over here. */
        Feeds.listen(this);
    }

    public void emit(String kind, String source, Component message) {
        emit(kind, source, message, null);
    }

    /**
     * A dialog, as structure rather than as a sentence.
     *
     * <p>mcp-server reads the title, the body and the buttons out of it and writes the line: a
     * dialog is not one piece of text, and the order its parts are read in is presentation. The
     * title rides along as the text field so a server that changed nothing else still has a
     * fallback to show.
     */
    public void dialog(Dialog shown) {
        emit("dialog", "dialog", shown.common().title(), Dialogs.raw(shown));
    }

    /**
     * An advancement's toast, with the id beside the title.
     *
     * <p>The title is what the toast draws and the id is what a caller can name: a server that
     * grants one when a quest is done draws its title in the pack's own font.
     *
     * <p>The client only puts one up for an advancement with a display, but the toast itself does
     * not insist, and this runs inside the toast manager: a missing display is skipped rather than
     * thrown, because a feed that took the client down with it would cost more than the line.
     */
    public void advancement(AdvancementHolder advancement) {
        DisplayInfo display = advancement.value().display().orElse(null);
        if (display == null) {
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("id", advancement.id().toString());
        data.addProperty("frame", display.getType().getSerializedName());
        data.addProperty("description", display.getDescription().getString());
        data.add("descriptionComponent", Segments.raw(display.getDescription()));

        emit("toast", "advancement", display.getTitle(), data);
    }

    public void recipe(ItemStack result) {
        JsonObject data = new JsonObject();
        data.addProperty("item", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());

        emit("toast", "recipe", result.getHoverName(), data);
    }

    /** The server's fold cadence and feed valves, from its {@code helloOk}. */
    public void configure(JsonObject helloOk) {
        folder.configure(helloOk);
    }

    /** Re-send the runs that are due, and close the ones nothing has repeated in a while. Called every client tick. */
    public void flush() {
        folder.flush();
    }

    /** The bot left the world, so nothing it was showing is showing any more. */
    public void closeAll() {
        folder.closeAll();
    }

    private void emit(String kind, String source, Component message, JsonElement data) {
        /* A proxy answers a command in chat and nowhere else, so a task can ask to hear it. */
        if (kind.equals("chat")) {
            ChatWatch.heard(message.getString());
            /* And the component itself, because a line with a click event on it can be pressed. */
            ChatLines.heard(message);
        }
        /* Before the line is built: a muted effect feed is muted because it is a firehose. */
        if (!folder.wants(kind)) {
            return;
        }

        /* A press waiting on a line reacts here, before the tick that reads the keys, not a round trip later. */
        if (kind.equals("actionBar") || kind.equals("title") || kind.equals("effect")) {
            FeedWatch.saw(kind, kind.equals("effect") ? message.getString() : Segments.readable(message));
        }

        JsonObject event = new JsonObject();
        event.addProperty("t", "event");
        event.addProperty("kind", kind);
        event.addProperty("source", source);
        event.addProperty("text", message.getString());
        /*
        Only the feeds a server draws with stacked glyphs. Chat is prose, and splitting it at every
        style change turns one sentence into a dozen fragments joined by separators.
        */
        if (kind.equals("actionBar") || kind.equals("title")) {
            event.add("segments", Segments.of(message));
        }
        /*
        And the component itself, for every feed. mcp-server flattens it, so the rule lives in one
        place; the segments above stay for the one thing it cannot do, which is resolve a translate
        key without the game's language table.
        */
        event.add("component", Segments.raw(message));
        if (data != null) {
            event.add("data", data);
        }

        folder.push(kind, event);
    }
}
