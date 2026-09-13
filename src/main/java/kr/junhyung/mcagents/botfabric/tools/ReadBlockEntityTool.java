package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * What a block is carrying: sign text, mostly.
 *
 * <p>Both faces, and an empty line stays an empty line. A server that writes a shop sign leaves
 * one blank on purpose, and a reader that drops it reads the line below as the line above.
 */
public final class ReadBlockEntityTool extends ReadTool {

    public ReadBlockEntityTool() {
        super("read-block-entity");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        BlockPos at = Positions.of(args);

        JsonObject data = new JsonObject();
        data.addProperty("block", BuiltInRegistries.BLOCK
                .getKey(Mc.requirePlayerEvenIfDead().level().getBlockState(at).getBlock()).getPath());
        data.add("position", Positions.json(at));

        BlockEntity entity = Mc.client().level.getBlockEntity(at);
        data.addProperty("present", entity != null);

        /*
        An array either way. Null here made mcp-server refuse the whole answer for anything that is
        not a sign, because the renderer asks whether the list is empty -- so read-block-entity on a
        chest came back as an internal error rather than as a sentence.
        */
        JsonArray faces = new JsonArray();
        if (entity instanceof SignBlockEntity sign) {
            faces.add(face("front_text", sign.getFrontText()));
            faces.add(face("back_text", sign.getBackText()));
        }
        data.add("signFaces", faces);

        /*
        The raw NBT is the server's, and only a client that was sent it has any. A vanilla client
        is sent the decoded block entity and not its tag, so there is nothing honest to put here.
        */
        data.add("raw", JsonNull.INSTANCE);
        return data;
    }

    private static JsonObject face(String name, SignText text) {
        JsonArray lines = new JsonArray();
        JsonArray components = new JsonArray();

        for (Component line : text.getMessages(false)) {
            lines.add(line.getString());
            components.add(Segments.raw(line));
        }

        JsonObject face = new JsonObject();
        face.addProperty("face", name);
        face.add("lines", lines);
        /* A shop sign's price line is drawn in the pack's own font, the same as a HUD. */
        face.add("lineComponents", components);
        return face;
    }
}
