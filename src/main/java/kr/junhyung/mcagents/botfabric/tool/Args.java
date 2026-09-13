package kr.junhyung.mcagents.botfabric.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Args {
    private final JsonObject raw;

    public Args(JsonObject raw) {
        this.raw = raw == null ? new JsonObject() : raw;
    }

    public String string(String key) {
        JsonElement element = raw.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            throw ToolException.badArgs("expected a string for " + key);
        }
        return element.getAsString();
    }

    public String string(String key, String fallback) {
        JsonElement element = raw.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return string(key);
    }

    public int integer(String key, int fallback) {
        JsonElement element = raw.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            throw ToolException.badArgs("expected an integer for " + key);
        }
    }

    public boolean bool(String key, boolean fallback) {
        JsonElement element = raw.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            throw ToolException.badArgs("expected a boolean for " + key);
        }
    }
}
