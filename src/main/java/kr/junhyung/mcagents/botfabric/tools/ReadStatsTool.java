package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.StatsCounterAccessor;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;

/**
 * The player's statistics, as the server counts them: kills per mob, blocks mined, distance walked.
 *
 * <p>A task and not a read, because the client does not keep them current. The server sends them
 * when asked and at no other time, so what the client holds is whatever it was last told -- right
 * after joining, nothing past the join. Reading that answers a quest check with a count from before
 * the kill it is checking. This asks, waits for the answer to that request, and reads then.
 *
 * <p>Vanilla alone keeps hundreds of statistics, so without a filter only the general ones are
 * listed -- mob kills, deaths, distance, time played -- with how many of each other type are nonzero
 * beside them, which is where a caller learns there are kills per mob to ask for.
 */
public final class ReadStatsTool implements Tool {

    private static final long ANSWER_MS = 5_000;

    private final TaskScheduler scheduler;

    public ReadStatsTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "read-stats";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new ReadTask(
                idOf(parsed.string("type", null), "type"),
                idOf(parsed.string("target", null), "target"),
                parsed.integer("count", 30)), call);
    }

    /** "killed" and "minecraft:killed" are one type, the same as they are to a command. */
    private static Identifier idOf(String raw, String what) {
        if (raw == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            throw ToolException.badArgs("\"" + raw + "\" is not a valid " + what + " id");
        }
        return id;
    }

    private record Found(Identifier type, Identifier target, int value, String formatted) {}

    private static final class ReadTask implements Task {
        private final Identifier type;
        private final Identifier target;
        private final int count;
        private final long startedNanos = System.nanoTime();

        private ClientPacketListener connection;
        private int awaited;

        private ReadTask(Identifier type, Identifier target, int count) {
            this.type = type;
            this.target = target;
            this.count = count;
        }

        @Override
        public String name() {
            return "read-stats";
        }

        /**
         * Checked before asking, so a typo is refused as one. Asked for by id, a statistic nobody
         * has earned is a zero, and "minecraft:zombi" would have been a zero too.
         */
        @Override
        public void start(CallContext call) {
            Mc.requirePlayerEvenIfDead();
            StatType<?> statType = type == null ? null : statType(type);
            if (target != null) {
                boolean counted = statType == null ? anyTypeHas(target) : statType.getRegistry().containsKey(target);
                if (!counted) {
                    throw ToolException.badArgs(target + " is not something "
                            + (statType == null ? "any statistic" : type.toString()) + " counts");
                }
            }

            connection = Mc.requireConnection();
            awaited = StatsAnswers.CLIENT.asked(connection);
            connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }

        @Override
        public boolean tick(CallContext call) {
            if (Mc.client().getConnection() != connection) {
                throw ToolException.refused("CONNECTION_CHANGED",
                        "the connection the statistics were asked on went before the server answered");
            }
            if (StatsAnswers.CLIENT.arrived(connection, awaited)) {
                call.ok("statistics", read());
                return true;
            }
            if ((System.nanoTime() - startedNanos) / 1_000_000L >= ANSWER_MS) {
                throw ToolException.refused("NO_STATS_ANSWER",
                        "the server did not send the statistics within " + ANSWER_MS + "ms of being asked");
            }
            return false;
        }

        @Override
        public void cleanup(CallContext call) {
            if (connection != null) {
                StatsAnswers.CLIENT.settled(connection);
            }
        }

        private JsonObject read() {
            Object2IntMap<Stat<?>> sent =
                    ((StatsCounterAccessor) Mc.requirePlayerEvenIfDead().getStats()).mcagents$stats();
            boolean general = type == null && target == null;
            Identifier listed = general ? BuiltInRegistries.STAT_TYPE.getKey(Stats.CUSTOM) : type;

            List<Found> found = new ArrayList<>();
            Map<String, Integer> otherTypes = new TreeMap<>();

            for (Object2IntMap.Entry<Stat<?>> entry : sent.object2IntEntrySet()) {
                int value = entry.getIntValue();
                if (value == 0) {
                    continue;
                }
                Stat<?> stat = entry.getKey();
                Identifier statType = BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
                Identifier statTarget = targetOf(stat);

                if (listed != null && !listed.equals(statType)) {
                    if (general) {
                        otherTypes.merge(statType.toString(), 1, Integer::sum);
                    }
                    continue;
                }
                if (target == null || target.equals(statTarget)) {
                    found.add(describe(stat, statType, statTarget, value));
                }
            }

            /*
            One statistic asked for by name is answered even at zero. The server sends only those it
            has counted, so a kill never made is absent rather than zero, and an empty list would
            leave a caller unsure whether the id was right.
            */
            if (type != null && target != null && found.isEmpty()) {
                found.add(describe(statOf(statType(type), target), type, target, 0));
            }

            found.sort(Comparator.comparingInt(Found::value).reversed()
                    .thenComparing(each -> each.type().toString())
                    .thenComparing(each -> each.target().toString()));

            JsonArray stats = new JsonArray();
            for (Found each : found.subList(0, Math.min(count, found.size()))) {
                JsonObject stat = new JsonObject();
                stat.addProperty("type", each.type().toString());
                stat.addProperty("target", each.target().toString());
                stat.addProperty("value", each.value());
                stat.addProperty("formatted", each.formatted());
                stats.add(stat);
            }

            JsonArray others = new JsonArray();
            otherTypes.forEach((id, nonZero) -> {
                JsonObject other = new JsonObject();
                other.addProperty("type", id);
                other.addProperty("nonZero", nonZero);
                others.add(other);
            });

            JsonObject data = new JsonObject();
            data.addProperty("type", type == null ? null : type.toString());
            data.addProperty("target", target == null ? null : target.toString());
            data.addProperty("matched", found.size());
            data.add("stats", stats);
            data.add("otherTypes", others);
            return data;
        }

        /**
         * The unit, when the statistic has one. Distances are kept in centimetres and times in
         * ticks, and "walk_one_cm: 152340" read as a number of blocks is off by a hundred.
         */
        private static Found describe(Stat<?> stat, Identifier type, Identifier target, int value) {
            String formatted = stat.format(value);
            return new Found(type, target, value,
                    formatted.equals(StatFormatter.DEFAULT.format(value)) ? null : formatted);
        }
    }

    private static StatType<?> statType(Identifier id) {
        return BuiltInRegistries.STAT_TYPE.getOptional(id).orElseThrow(() -> ToolException.badArgs(
                id + " is not a statistic type. The types are " + BuiltInRegistries.STAT_TYPE.keySet()));
    }

    private static boolean anyTypeHas(Identifier target) {
        for (StatType<?> each : BuiltInRegistries.STAT_TYPE) {
            if (each.getRegistry().containsKey(target)) {
                return true;
            }
        }
        return false;
    }

    private static <T> Identifier targetOf(Stat<T> stat) {
        return stat.getType().getRegistry().getKey(stat.getValue());
    }

    private static <T> Stat<T> statOf(StatType<T> type, Identifier target) {
        return type.get(type.getRegistry().getValue(target));
    }
}
