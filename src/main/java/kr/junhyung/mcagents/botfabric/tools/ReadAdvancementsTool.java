package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.ClientAdvancementsAccessor;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.Identifier;

/**
 * The advancements the server has sent, and how far each has got.
 *
 * <p>A server tracks quests as advancements, and vanilla alone sends over a hundred of them, so the
 * whole list is noise around the three a check is about. Without a prefix only a server's own are
 * listed -- any namespace but minecraft -- and the most recently progressed come first, because
 * what a caller wants after doing something is whether it counted.
 */
public final class ReadAdvancementsTool extends ReadTool {

    private static final String VANILLA = "minecraft";

    public ReadAdvancementsTool() {
        super("read-advancements");
    }

    private record Found(AdvancementHolder holder, AdvancementProgress progress, Instant last) {}

    @Override
    protected JsonObject read(JsonObject args) {
        Args parsed = new Args(args);
        String prefix = parsed.string("prefix", null);
        String status = parsed.string("status", "any");
        int count = parsed.integer("count", 10);

        Map<AdvancementHolder, AdvancementProgress> sent =
                ((ClientAdvancementsAccessor) Mc.requireConnection().getAdvancements()).mcagents$progress();

        List<Found> found = new ArrayList<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : sent.entrySet()) {
            Identifier id = entry.getKey().id();
            AdvancementProgress progress = entry.getValue();

            boolean inScope = prefix == null ? !id.getNamespace().equals(VANILLA) : id.toString().startsWith(prefix);
            boolean wanted = switch (status) {
                case "done" -> progress.isDone();
                case "inProgress" -> !progress.isDone() && progress.hasProgress();
                default -> true;
            };
            if (inScope && wanted) {
                found.add(new Found(entry.getKey(), progress, lastProgress(progress)));
            }
        }

        /* Never progressed goes last, and a tie is broken by id so two reads of one state agree. */
        found.sort(Comparator.comparing(Found::last, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(each -> each.holder().id().toString()));

        JsonArray advancements = new JsonArray();
        for (Found each : found.subList(0, Math.min(count, found.size()))) {
            advancements.add(describe(each));
        }

        JsonObject data = new JsonObject();
        data.addProperty("prefix", prefix);
        data.addProperty("status", status);
        data.addProperty("matched", found.size());
        data.add("advancements", advancements);
        return data;
    }

    private static JsonObject describe(Found found) {
        DisplayInfo display = found.holder().value().display().orElse(null);
        AdvancementProgress progress = found.progress();

        int done = 0;
        for (String ignored : progress.getCompletedCriteria()) {
            done++;
        }
        int remaining = 0;
        for (String ignored : progress.getRemainingCriteria()) {
            remaining++;
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("id", found.holder().id().toString());
        /*
        An advancement with no display is still listed. A server uses one as a trigger a player
        never sees, and it is often the very thing a check is about.
        */
        if (display == null) {
            entry.add("title", JsonNull.INSTANCE);
            entry.add("titleComponent", JsonNull.INSTANCE);
            entry.add("description", JsonNull.INSTANCE);
            entry.add("descriptionComponent", JsonNull.INSTANCE);
            entry.add("frame", JsonNull.INSTANCE);
        } else {
            entry.addProperty("title", display.getTitle().getString());
            entry.add("titleComponent", Segments.raw(display.getTitle()));
            entry.addProperty("description", display.getDescription().getString());
            entry.add("descriptionComponent", Segments.raw(display.getDescription()));
            entry.addProperty("frame", display.getType().getSerializedName());
        }
        entry.addProperty("done", progress.isDone());
        entry.addProperty("criteriaDone", done);
        entry.addProperty("criteriaTotal", done + remaining);
        entry.addProperty("lastProgressAt", found.last() == null ? null : found.last().toEpochMilli());
        return entry;
    }

    /**
     * When the latest criterion was met. The game only offers the first, which for a quest with
     * five steps says when it was started rather than when it last moved.
     */
    private static Instant lastProgress(AdvancementProgress progress) {
        Instant last = null;
        for (String criterion : progress.getCompletedCriteria()) {
            Instant obtained = progress.getCriterion(criterion).getObtained();
            if (obtained != null && (last == null || obtained.isAfter(last))) {
                last = obtained;
            }
        }
        return last;
    }
}
