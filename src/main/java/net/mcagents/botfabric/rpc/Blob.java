package net.mcagents.botfabric.rpc;

import java.util.UUID;

public record Blob(UUID id, String mime, String name, byte[] content) {
    public static Blob png(String name, byte[] content) {
        return new Blob(UUID.randomUUID(), "image/png", name, content);
    }
}
