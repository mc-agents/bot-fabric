package net.mcagents.botfabric.rpc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class Json {
    private static final Gson GSON = new Gson();

    private Json() {
    }

    public static JsonObject parse(byte[] utf8) {
        JsonElement element = JsonParser.parseString(new String(utf8, java.nio.charset.StandardCharsets.UTF_8));
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("frame payload is not a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static byte[] encode(JsonObject object) {
        return GSON.toJson(object).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    public static String requireString(JsonObject object, String key) {
        String value = string(object, key, null);
        if (value == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return value;
    }

    public static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    public static long number(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsLong();
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    public static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray array(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
