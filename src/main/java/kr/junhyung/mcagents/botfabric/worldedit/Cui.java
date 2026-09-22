package kr.junhyung.mcagents.botfabric.worldedit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * WorldEdit's selection, taken from the channel it draws one on.
 *
 * <p>WorldEdit describes the selection to a client that can draw it over the {@code worldedit:cui}
 * plugin channel, which is what the WorldEditCUI mod exists to receive. The messages are
 * pipe-separated ASCII: {@code s|<shape>} names the shape and starts a fresh description, and
 * {@code p|<0 or 1>|<x>|<y>|<z>|<volume>} gives a corner. Reading them is how a bot learns what it
 * has selected without the server having to say it in chat, which is slow, translated, and off
 * altogether on a server that silences the plugin.
 *
 * <p>Nothing arrives unasked. WorldEdit sends to a session it believes has a CUI client, and a
 * client says so by sending {@code v|<protocol version>} back on the same channel. It answers a
 * repeated announcement by describing the selection again, which is what turns the handshake into
 * a question: {@link #ask()} is sent, and the description that comes back is the selection as the
 * server holds it now.
 */
public final class Cui {

    /** The channel both WorldEdit and FastAsyncWorldEdit register, incoming and outgoing. */
    public static final CustomPacketPayload.Type<Payload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("worldedit:cui"));

    /**
     * What the announcement claims to speak. WorldEdit compares it against the selector's own
     * protocol version to decide between the current description and the legacy one; 4 is what
     * WorldEditCUI sends, and FastAsyncWorldEdit refuses an announcement longer than four
     * characters, so this is both current and short enough to be read.
     */
    private static final int PROTOCOL_VERSION = 4;

    private static final String SHAPE = "s";

    private static final String POINT = "p";

    /** A cuboid has two corners, and WorldEdit numbers them 0 and 1. */
    private static final int CORNERS = 2;

    private static String shape;

    private static final BlockPos[] points = new BlockPos[CORNERS];

    private static long volume = -1;

    /**
     * How many descriptions the server has sent. A reader takes this before it asks and watches for
     * it to move, which is the only sign that what it is about to read is an answer to its own
     * question rather than whatever was left from the last one.
     */
    private static int described;

    private Cui() {}

    /** One message on the channel, which is a line of text and not a structure. */
    public record Payload(String text) implements CustomPacketPayload {

        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC =
                CustomPacketPayload.codec(Payload::write, Payload::read);

        private static Payload read(FriendlyByteBuf buffer) {
            return new Payload(buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeCharSequence(text, StandardCharsets.UTF_8);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Both directions of the channel, and the handler that keeps what comes in.
     *
     * <p>Registering the clientbound half is what makes the client decode the payload at all: an
     * unregistered channel is dropped where it arrives. The serverbound half is what lets the
     * announcement be encoded.
     */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TYPE, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> accept(payload.text()));
    }

    /** Announce this client to WorldEdit, which answers by describing the selection. */
    public static void ask() {
        ClientPlayNetworking.send(new Payload("v|" + PROTOCOL_VERSION));
    }

    /** Nothing of one server's selection belongs to the next one. */
    public static void forget() {
        shape = null;
        points[0] = null;
        points[1] = null;
        volume = -1;
        described = 0;
    }

    public static int described() {
        return described;
    }

    public static String shape() {
        return shape;
    }

    public static long volume() {
        return volume;
    }

    /** The corners the server has named, in its own numbering, with the ones it has not left out. */
    public static List<Corner> corners() {
        List<Corner> named = new ArrayList<>(CORNERS);

        for (int index = 0; index < CORNERS; index++) {
            if (points[index] != null) {
                named.add(new Corner(index, points[index]));
            }
        }
        return named;
    }

    public record Corner(int index, BlockPos at) {}

    /**
     * One message, applied the way a CUI client applies it.
     *
     * <p>A shape starts a description over, so the corners from the one before it are dropped:
     * without that, clearing a selection and reading it back would answer with the corners of the
     * selection that is gone. A corner then lands in its own slot, since WorldEdit sends only the
     * corner that changed when a single {@code //pos1} moves it.
     */
    static void accept(String message) {
        String[] parts = message.split("\\|");

        if (parts.length == 0) {
            return;
        }
        switch (parts[0]) {
            case SHAPE -> {
                shape = parts.length > 1 ? parts[1] : null;
                points[0] = null;
                points[1] = null;
                volume = -1;
                described++;
            }
            case POINT -> {
                if (parts.length < 5) {
                    return;
                }
                try {
                    int index = Integer.parseInt(parts[1]);

                    if (index < 0 || index >= CORNERS) {
                        return;
                    }
                    points[index] = new BlockPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                            Integer.parseInt(parts[4]));
                    volume = parts.length > 5 ? Long.parseLong(parts[5]) : -1;
                    described++;
                } catch (NumberFormatException notANumber) {
                    /* A message this client cannot read is one it was not written for; the rest still stand. */
                }
            }
            /*
            Everything else a CUI client draws -- the colours, the ellipsoid and cylinder
            descriptions, the multi-region events -- says nothing about where a cuboid's corners
            are, and a bot has no selection box to paint.
            */
            default -> {
            }
        }
    }
}
