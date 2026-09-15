package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.FishingHookAccessor;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cast, wait for the bite, reel in.
 *
 * <p>The bite is not guessed at. A hook carries a synced flag saying whether a fish is on it, so the
 * server tells the client outright and this reads it. A plugin running its own fishing never sets the
 * flag, and gives its bite the way vanilla also does, by pulling the floating bobber under; that pull
 * is a packet too, and {@link HookYanks} keeps it. Listening for the splash sound instead -- which is
 * what an auto-fisher usually does -- would call a bite whenever anything else landed in water nearby.
 *
 * <p>The count of items is how the answer describes what was caught, because what a server's loot
 * table hands over is its business and the tool's job is to say that something arrived.
 */
public final class FishTool implements Tool {

    /**
     * How long the hook has to turn up before the cast is called a failure. The bobber is spawned by
     * the server and arrives as a packet, so it does not exist the moment the rod is used: refusing
     * on the first tick that could not see it reported "no bobber went out" for casts that had gone
     * out perfectly well.
     */
    private static final int CAST_TICKS = 40;

    /** The catch flies at the bot and lands a moment later, so the count is taken after it. */
    private static final int LANDING_TICKS = 30;

    /**
     * How long the bobber floats before a pull under counts. It lands in the water still falling, and
     * the server's last word on that fall is a motion as steep as a bite's.
     */
    private static final int FLOATING_TICKS = 10;

    /** Where the bot's own items start in its inventory menu. */
    private static final int BAG_START = 9;

    private final TaskScheduler scheduler;

    public FishTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "fish";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new FishTask(new Args(args).integer("timeoutMs", 60_000)), call);
    }

    private static final class FishTask implements Task {
        private final long timeoutMs;

        private int before;
        private int ticks;
        private int reeling;
        private long castAtNanos;
        private long floatingSince = Long.MAX_VALUE;

        private FishTask(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "fish";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            hold(player);
            before = count(player);
            HookYanks.forget();

            Mc.client().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            castAtNanos = System.nanoTime();
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (reeling > 0) {
                return settle(call, player);
            }
            FishingHook hook = hookOf(player);

            if (hook == null) {
                if (++ticks < CAST_TICKS) {
                    return false;
                }
                throw ToolException.refused("NO_HOOK", "The rod was used but this bot has no bobber."
                        + " " + hooks() + " Facing a block rather than water is the usual reason.");
            }

            long now = Mc.client().level.getGameTime();
            /* The hook's own test for water. Entity.isInWater stayed false for a bobber floating in the pool. */
            boolean floating = Mc.client().level.getFluidState(hook.blockPosition()).is(FluidTags.WATER);
            if (!floating) {
                floatingSince = Long.MAX_VALUE;
            } else if (floatingSince == Long.MAX_VALUE) {
                floatingSince = now;
            }
            boolean pulledUnder = floatingSince != Long.MAX_VALUE
                    && HookYanks.pulledAt(hook) >= floatingSince + FLOATING_TICKS;

            if (((FishingHookAccessor) hook).botfabric$biting() || pulledUnder) {
                /* Reeling in is the same click as casting, and it is what brings the catch over. */
                Mc.client().gameMode.useItem(player, InteractionHand.MAIN_HAND);
                reeling = 1;
                return false;
            }

            if ((System.nanoTime() - castAtNanos) / 1_000_000L > timeoutMs) {
                Mc.client().gameMode.useItem(player, InteractionHand.MAIN_HAND);
                throw ToolException.refused("NO_BITE", "No bite within " + timeoutMs + "ms.");
            }
            return false;
        }

        private boolean settle(CallContext call, LocalPlayer player) {
            if (++reeling < LANDING_TICKS) {
                return false;
            }
            call.ok("Reeled in. The inventory went from " + before + " to " + count(player) + " items.");
            return true;
        }

        /** What the client can see, for a refusal that says which half went wrong. */
        private static String hooks() {
            int seen = 0;
            for (Entity entity : Mc.client().level.entitiesForRendering()) {
                if (entity instanceof FishingHook) {
                    seen++;
                }
            }
            return seen == 0 ? "No bobber is in the world." : seen + " bobber(s) are, owned by somebody else.";
        }

        /**
         * The bot's own hook, found in the world rather than read off the player.
         *
         * <p>{@code LocalPlayer.fishing} is set by the code path a person's click takes and stays
         * null for a cast made this way, so the hook was there and the field said there was none.
         */
        private static FishingHook hookOf(LocalPlayer player) {
            for (Entity entity : Mc.client().level.entitiesForRendering()) {
                if (entity instanceof FishingHook hook && hook.getPlayerOwner() == player) {
                    return hook;
                }
            }
            return null;
        }

        /** A rod already in hand is left alone; one in the bag is swapped into the held slot. */
        private static void hold(LocalPlayer player) {
            if (player.getMainHandItem().is(Items.FISHING_ROD)) {
                return;
            }

            InventoryMenu menu = player.inventoryMenu;
            for (Slot slot : menu.slots) {
                if (slot.index >= BAG_START && slot.getItem().is(Items.FISHING_ROD)) {
                    Mc.client().gameMode.handleContainerInput(menu.containerId, slot.index,
                            player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player);
                    return;
                }
            }
            throw ToolException.refused("NO_ROD", "No fishing rod is in the inventory.");
        }

        private static int count(LocalPlayer player) {
            int total = 0;
            for (Slot slot : player.inventoryMenu.slots) {
                if (slot.index >= BAG_START) {
                    ItemStack stack = slot.getItem();
                    total += stack.isEmpty() ? 0 : stack.getCount();
                }
            }
            return total;
        }

        /** A rod left cast keeps a hook in the world for a call that ended. */
        @Override
        public void cleanup(CallContext call) {
            LocalPlayer player = Mc.client().player;

            if (player != null && hookOf(player) != null) {
                Mc.client().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
        }
    }
}
