package kr.junhyung.mcagents.botfabric.rpc;

public record Frame(byte type, byte[] payload) {
    public static final byte TYPE_JSON = 0x00;
    public static final byte TYPE_BLOB = 0x01;
    public static final int MAX_FRAME = 16 * 1024 * 1024;
    public static final int MAX_JSON = 1024 * 1024;
}
