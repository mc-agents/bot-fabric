package kr.junhyung.mcagents.botfabric.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.serialization.JsonOps;
import kr.junhyung.mcagents.botfabric.Mc;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.dialog.Dialog;

/**
 * A dialog as the game serialises it.
 *
 * <p>The same bargain as a component: the bot sends what the game wrote and mcp-server reads the
 * parts worth reading. A dialog is a title, some body, a row of buttons and a count of inputs, and
 * which of those to say in what order is presentation -- so it is not a sentence this side builds.
 * The other kind of bot reads the same fields out of NBT, which is why both arrive in one shape.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static JsonElement raw(Dialog dialog) {
        ClientPacketListener connection = Mc.client().getConnection();

        if (dialog == null || connection == null) {
            return JsonNull.INSTANCE;
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess());

        return Dialog.DIRECT_CODEC.encodeStart(ops, dialog).result().orElse(JsonNull.INSTANCE);
    }
}
