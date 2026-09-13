package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Middle-click a block: put the item it drops into the hand.
 *
 * <p>The client sends only the position and whether Ctrl was held; the server decides the rest. It
 * ignores a block further than the reach attribute plus one from the eyes, or in a chunk it has not
 * loaded, and it never looks at the crosshair. A creative player gets the item made if the
 * inventory has none, and gets the block's data with it when Ctrl was held. A survival player only
 * gets one moved out of the inventory onto the hotbar, and nothing at all when there is none, which
 * the server answers by resending the held slot as it was.
 *
 * <p>So the bot walks into reach and faces the block first, as a player's crosshair would, and the
 * answer is what the hand holds once the server has said so: the held slot and the stack in it
 * arrive a tick or so after the request, and reading them straight away reports the hand from
 * before.
 */
public final class PickBlockTool implements Tool {

    /** Local play answers in a tick or two; this is for a server with a real ping. */
    private static final int SETTLE_TICKS = 20;

    private final TaskScheduler scheduler;

    public PickBlockTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "pick-block";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new PickTask(Positions.of(args), new Args(args).bool("includeData", false)), call);
    }

    private static final class PickTask implements Task {
        private final BlockPos at;
        private final boolean includeData;
        private final Approach approach = new Approach();

        private String picked;
        private boolean withData;
        private int selectedBefore;
        private ItemStack heldBefore;
        private int waited = -1;
        private int changedAt = -1;

        private PickTask(BlockPos at, boolean includeData) {
            this.at = at;
            this.includeData = includeData;
        }

        @Override
        public String name() {
            return "pick-block";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (!player.level().isLoaded(at)) {
                throw ToolException.refused("NOT_LOADED", Positions.point(at)
                        + " is outside the loaded chunks, so there is no block to pick");
            }
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (waited >= 0) {
                return settle(call, player);
            }
            if (!approach.reached(player, at)) {
                return false;
            }

            BlockState state = player.level().getBlockState(at);
            ItemStack clone = state.getCloneItemStack(player.level(), at, false);

            /* The server stops at an empty clone without answering, which would read as a pick that did nothing. */
            if (clone.isEmpty()) {
                throw ToolException.refused("NOTHING_TO_PICK", Positions.point(at) + " is "
                        + state.getBlock().getName().getString() + ", which has no item to pick");
            }

            Inventory inventory = player.getInventory();
            if (!player.hasInfiniteMaterials() && inventory.findSlotMatchingItem(clone) < 0) {
                throw ToolException.refused("NOT_IN_INVENTORY", "outside creative, picking a block only moves one "
                        + "that is already in the inventory onto the hotbar, and the bot has no " + Items.name(clone));
            }

            picked = Items.name(clone);
            /* Held Ctrl is ignored outside creative, and saying the data came along would be a guess. */
            withData = includeData && player.hasInfiniteMaterials();
            selectedBefore = inventory.getSelectedSlot();
            heldBefore = player.getMainHandItem().copy();

            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(at));
            Mc.client().gameMode.handlePickItemFromBlock(at, includeData);
            waited = 0;
            return false;
        }

        /*
        Done when the hand changes, one tick after, so the stack that comes with a new held slot has
        landed too. A pick of what is already in hand changes nothing, and that waits out the settle.
        */
        private boolean settle(CallContext call, LocalPlayer player) {
            boolean changed = player.getInventory().getSelectedSlot() != selectedBefore
                    || !ItemStack.matches(player.getMainHandItem(), heldBefore);

            waited++;
            if (changed && changedAt < 0) {
                changedAt = waited;
            }
            if (changedAt >= 0 ? waited <= changedAt : waited < SETTLE_TICKS) {
                return false;
            }

            call.ok("Picked " + picked + " from " + Positions.point(at) + (withData ? " with its block data" : "")
                    + "; the bot now holds " + held(player) + (changedAt >= 0 ? "" : ", as it did before") + ".");
            return true;
        }

        private static String held(LocalPlayer player) {
            ItemStack stack = player.getMainHandItem();
            String slot = " in hotbar slot " + (player.getInventory().getSelectedSlot() + 1);
            return stack.isEmpty() ? "nothing" + slot : Items.name(stack) + " x" + stack.getCount() + slot;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
