package kr.junhyung.mcagents.botfabric.rpc;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public final class FrameCodec {
    private FrameCodec() {
    }

    public static Frame read(DataInputStream in) throws IOException {
        long length = Integer.toUnsignedLong(in.readInt());
        if (length < 1) {
            throw new IOException("frame length below minimum: " + length);
        }
        if (length > Frame.MAX_FRAME) {
            throw new IOException("frame length above maximum: " + length);
        }
        byte type = in.readByte();
        byte[] payload = new byte[(int) length - 1];
        in.readFully(payload);
        return new Frame(type, payload);
    }

    public static byte[] encodeJson(byte[] payload) throws IOException {
        if (payload.length > Frame.MAX_JSON) {
            throw new IOException("json frame above maximum: " + payload.length);
        }
        return frame(Frame.TYPE_JSON, payload);
    }

    public static byte[] encodeBlob(UUID id, byte[] content) throws IOException {
        byte[] payload = new byte[16 + content.length];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        buffer.put(content);
        return frame(Frame.TYPE_BLOB, payload);
    }

    public static void write(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private static byte[] frame(byte type, byte[] payload) throws IOException {
        long length = 1L + payload.length;
        if (length > Frame.MAX_FRAME) {
            throw new EOFException("frame above maximum: " + length);
        }
        byte[] out = new byte[4 + (int) length];
        ByteBuffer buffer = ByteBuffer.wrap(out);
        buffer.putInt((int) length);
        buffer.put(type);
        buffer.put(payload);
        return out;
    }
}
