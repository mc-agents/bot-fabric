package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import kr.junhyung.mcagents.botfabric.text.Readings;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/** A feed and a pattern to watch it for, as press-input's after and until and run-inputs' waitFor give them. */
record Watch(String feed, String source, Pattern pattern) {

    static Watch of(JsonElement given) {
        if (given == null || given.isJsonNull()) {
            return null;
        }
        Args args = new Args(given.getAsJsonObject());
        return of(args.string("feed"), args.string("pattern"));
    }

    static Watch of(String feed, String source) {
        try {
            return new Watch(feed, source, Pattern.compile(source));
        } catch (PatternSyntaxException invalid) {
            throw ToolException.refused("BAD_PATTERN",
                    "\"" + source + "\" is not a valid regular expression: " + invalid.getDescription());
        }
    }

    /** The reading that matched, or null: the line is matched every way a caller can have read it. */
    String matched(String kind, Readings line) {
        return feed.equals(kind) ? line.matched(pattern) : null;
    }

    JsonObject describe(String matched) {
        JsonObject watched = new JsonObject();
        watched.addProperty("feed", feed);
        watched.addProperty("pattern", source);
        watched.addProperty("matched", matched);
        return watched;
    }
}
