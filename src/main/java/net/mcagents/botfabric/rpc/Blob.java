package net.mcagents.botfabric.rpc;

import java.util.UUID;

public record Blob(UUID id, String mime, String name, Integer width, Integer height, byte[] content) {
    public static Blob image(String mime, String name, int width, int height, byte[] content) {
        return new Blob(UUID.randomUUID(), mime, name, width, height, content);
    }
}
