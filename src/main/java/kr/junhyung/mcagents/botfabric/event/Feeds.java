package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.world.item.ItemStack;

/**
 * Where a mixin hands an event to whatever is pumping them.
 *
 * <p>A mixin cannot be given a constructor argument, so the pump registers itself here when it
 * starts and clears it when the link goes. Before that, and after, these calls do nothing: the
 * client runs perfectly well with nobody listening, and a mod that crashed the game because an
 * RPC link was not up yet would be worse than a missing feed.
 */
public final class Feeds {

    private static volatile EventPump pump;

    private Feeds() {
    }

    public static void listen(EventPump listener) {
        pump = listener;
    }

    public static void actionBar(Component text) {
        EventPump listener = pump;
        if (listener != null) {
            listener.emit("actionBar", "actionbar", text);
        }
    }

    public static void title(String source, Component text) {
        EventPump listener = pump;
        if (listener != null) {
            listener.emit("title", source, text);
        }
    }

    /** A dialog the server put on screen, and the fact of one going away. */
    public static void dialog(Dialog shown) {
        EventPump listener = pump;
        if (listener != null) {
            listener.dialog(shown);
        }
    }

    /** The dialog that is open again, with what its inputs hold once one was set. */
    public static void dialogValues(Dialog shown, JsonObject values) {
        EventPump listener = pump;
        if (listener != null) {
            listener.dialog(shown, values);
        }
    }

    public static void dialogClosed() {
        EventPump listener = pump;
        if (listener != null) {
            listener.emit("dialog", "closed", Component.literal("the dialog was closed"));
        }
    }

    /** An advancement toast, as it is put up. */
    public static void advancement(AdvancementHolder advancement) {
        EventPump listener = pump;
        if (listener != null) {
            listener.advancement(advancement);
        }
    }

    /** A recipe the server unlocked with a toast, one line per recipe. */
    public static void recipe(ItemStack result) {
        EventPump listener = pump;
        if (listener != null) {
            listener.recipe(result);
        }
    }

    public static void effect(String source, String id) {
        EventPump listener = pump;
        if (listener != null) {
            listener.emit("effect", source, Component.literal(id));
        }
    }
}
