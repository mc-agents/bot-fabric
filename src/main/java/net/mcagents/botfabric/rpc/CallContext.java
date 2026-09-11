package net.mcagents.botfabric.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mcagents.botfabric.tool.ToolError;
import net.mcagents.botfabric.tool.ToolException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CallContext {
    private final RpcClient client;
    private final String id;
    private final String tool;
    private final long deadlineMs;
    private final long startedNanos = System.nanoTime();
    private final AtomicBoolean settled = new AtomicBoolean();
    private volatile Runnable onSettled = () -> {
    };

    public CallContext(RpcClient client, String id, String tool, long deadlineMs) {
        this.client = client;
        this.id = id;
        this.tool = tool;
        this.deadlineMs = deadlineMs;
    }

    public String id() {
        return id;
    }

    public String tool() {
        return tool;
    }

    public long deadlineMs() {
        return deadlineMs;
    }

    public long elapsedMs() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public boolean settled() {
        return settled.get();
    }

    public void onSettled(Runnable action) {
        this.onSettled = action;
    }

    public void ok(String text) {
        ok(text, null, List.of());
    }

    public void ok(String text, JsonObject data) {
        ok(text, data, List.of());
    }

    public void ok(String text, JsonObject data, List<Blob> blobs) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        for (Blob blob : blobs) {
            client.sendBlob(blob);
        }
        JsonObject result = base(true);
        result.addProperty("text", text);
        if (data != null) {
            result.add("data", data);
        }
        if (!blobs.isEmpty()) {
            JsonArray array = new JsonArray();
            for (Blob blob : blobs) {
                JsonObject descriptor = new JsonObject();
                descriptor.addProperty("id", blob.id().toString());
                descriptor.addProperty("mime", blob.mime());
                descriptor.addProperty("bytes", blob.content().length);
                if (blob.name() != null) {
                    descriptor.addProperty("name", blob.name());
                }
                if (blob.width() != null && blob.height() != null) {
                    descriptor.addProperty("width", blob.width());
                    descriptor.addProperty("height", blob.height());
                }
                array.add(descriptor);
            }
            result.add("blobs", array);
        }
        client.send(result);
        onSettled.run();
    }

    public void fail(ToolError errorClass, String code, String message, boolean retryable) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        JsonObject error = new JsonObject();
        error.addProperty("class", errorClass.wireName());
        error.addProperty("code", code);
        error.addProperty("message", message);
        error.addProperty("retryable", retryable);

        JsonObject result = base(false);
        result.addProperty("text", message);
        result.add("error", error);
        client.send(result);
        onSettled.run();
    }

    public void fail(Throwable thrown) {
        if (thrown instanceof ToolException tool) {
            fail(tool.errorClass(), tool.code(), tool.getMessage(), tool.retryable());
            return;
        }
        String message = thrown.getMessage() == null ? thrown.getClass().getSimpleName() : thrown.getMessage();
        fail(ToolError.INTERNAL, "THREW", message, false);
    }

    public void timedOut() {
        fail(ToolError.TIMEOUT, "DEADLINE", tool + " did not finish within " + deadlineMs + "ms", true);
    }

    public void cancelled(String reason) {
        fail(ToolError.CANCELLED, "CANCELLED", reason == null ? "cancelled" : reason, false);
    }

    private JsonObject base(boolean ok) {
        JsonObject result = new JsonObject();
        result.addProperty("t", "result");
        result.addProperty("id", id);
        result.addProperty("ok", ok);
        result.addProperty("elapsedMs", elapsedMs());
        return result;
    }
}
