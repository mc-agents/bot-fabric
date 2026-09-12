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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Load a furnace and, unless told not to, wait for what comes out.
 *
 * <p>The longest thing a client does through a window, and every step of it is clicks: walk to the
 * block, open it, move fuel into one slot and input into another, wait for the bar to finish, then
 * shift-click the result out. A furnace's three slots are 0 input, 1 fuel, 2 output, and those are
 * the server's numbers.
 *
 * <p>Part of a stack is right-clicks. A left click moves the whole stack, and a caller asking to
 * smelt one iron ore out of a stack of sixty-four means one.
 */
public final class SmeltItemTool implements Tool {

    private static final Set<String> FURNACES = Set.of("furnace", "blast_furnace", "smoker");

    private static final int INPUT = 0;
    private static final int FUEL = 1;
    private static final int OUTPUT = 2;

    /** The furnace's own slots come first, so the bag starts after them. */
    private static final int BAG_START = 3;

    private final TaskScheduler scheduler;

    public SmeltItemTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "smelt-item";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new SmeltTask(Positions.of(args),
                parsed.string("inputItem"), parsed.integer("inputCount", 1),
                parsed.string("fuelItem"), parsed.integer("fuelCount", 1),
                parsed.bool("takeOutput", true), parsed.integer("timeoutMs", 60_000)), call);
    }

    private enum Step { OPENING, LOADING, BURNING }

    private static final class SmeltTask implements Task {
        private final BlockPos at;
        private final String inputQuery;
        private final int inputCount;
        private final String fuelQuery;
        private final int fuelCount;
        private final boolean takeOutput;
        private final long timeoutMs;
        private final Approach approach = new Approach();

        private Step step = Step.OPENING;
        private boolean clicked;
        private String inputName;
        private String fuelName;
        private int inputMoved;
        private int fuelMoved;
        private long loadedAtNanos;

        private SmeltTask(BlockPos at, String inputQuery, int inputCount, String fuelQuery,
                int fuelCount, boolean takeOutput, long timeoutMs) {
            this.at = at;
            this.inputQuery = inputQuery;
            this.inputCount = inputCount;
            this.fuelQuery = fuelQuery;
            this.fuelCount = fuelCount;
            this.takeOutput = takeOutput;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "smelt-item";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();
            String block = BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(at).getBlock()).getPath();

            if (!FURNACES.contains(block)) {
                throw ToolException.refused("NOT_A_FURNACE",
                        Positions.point(at) + " holds " + block + ", not a furnace");
            }
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            return switch (step) {
                case OPENING -> open(player);
                case LOADING -> load(call, player);
                case BURNING -> burn(call, player);
            };
        }

        private boolean open(LocalPlayer player) {
            if (!approach.reached(player, at)) {
                return false;
            }
            if (!clicked) {
                Vec3 middle = Vec3.atCenterOf(at);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
                Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(middle, Direction.UP, at, false));
                clicked = true;
                return false;
            }
            if (Windows.open() == null) {
                return false;
            }
            step = Step.LOADING;
            return false;
        }

        private boolean load(CallContext call, LocalPlayer player) {
            AbstractContainerMenu menu = Windows.require().getMenu();

            int fuelSlot = find(menu, fuelQuery, "fuel");
            fuelName = pathOf(menu.getSlot(fuelSlot).getItem());
            fuelMoved = Math.min(fuelCount, menu.getSlot(fuelSlot).getItem().getCount());

            int inputSlot = find(menu, inputQuery, "input");
            inputName = pathOf(menu.getSlot(inputSlot).getItem());
            inputMoved = Math.min(inputCount, menu.getSlot(inputSlot).getItem().getCount());

            occupied(menu, FUEL, fuelName, "fuel");
            occupied(menu, INPUT, inputName, "input");

            /* Fuel first, the way the other kind of bot loads it: a furnace lit before it is fed
               starts burning the moment the input lands, and the two orders finish at different
               times. Finding the input again afterwards is for the case where one stack was both. */
            move(player, menu, fuelSlot, FUEL, fuelMoved);
            move(player, menu, find(menu, inputQuery, "input"), INPUT, inputMoved);

            if (!takeOutput) {
                String text = loaded() + ", left it burning.";
                player.closeContainer();
                call.ok(text);
                return true;
            }

            loadedAtNanos = System.nanoTime();
            step = Step.BURNING;
            return false;
        }

        private boolean burn(CallContext call, LocalPlayer player) {
            AbstractContainerScreen<?> screen = Windows.open();

            if (screen == null) {
                throw ToolException.refused("WINDOW_CLOSED",
                        "the furnace window closed before anything came out");
            }

            AbstractContainerMenu menu = screen.getMenu();
            ItemStack output = menu.getSlot(OUTPUT).getItem();

            if (!output.isEmpty()) {
                String taken = output.getCount() + " " + pathOf(output);
                Mc.client().gameMode.handleContainerInput(menu.containerId, OUTPUT, 0,
                        ContainerInput.QUICK_MOVE, player);
                player.closeContainer();
                call.ok("Smelted " + taken + ".");
                return true;
            }

            if ((System.nanoTime() - loadedAtNanos) / 1_000_000L > timeoutMs) {
                String text = loaded() + ", but nothing came out within " + timeoutMs + "ms.";
                player.closeContainer();
                call.ok(text);
                return true;
            }
            return false;
        }

        private String loaded() {
            return "Loaded " + inputMoved + " " + inputName + " and " + fuelMoved + " " + fuelName;
        }

        private int find(AbstractContainerMenu menu, String query, String role) {
            for (Slot slot : menu.slots) {
                if (slot.index >= BAG_START && !slot.getItem().isEmpty()
                        && Items.matches(slot.getItem(), query)) {
                    return slot.index;
                }
            }
            throw ToolException.refused("NO_SUCH_ITEM",
                    "No inventory item matches " + role + " \"" + query + "\"");
        }

        private void occupied(AbstractContainerMenu menu, int slot, String wanted, String role) {
            ItemStack there = menu.getSlot(slot).getItem();

            if (!there.isEmpty() && !pathOf(there).equals(wanted)) {
                throw ToolException.refused("SLOT_TAKEN",
                        "The " + role + " slot already holds " + pathOf(there));
            }
        }

        /**
         * Pick the stack up, drop the asked-for number in one at a time, and put the rest back. A
         * plain click would move all sixty-four ores into a furnace asked to smelt one.
         */
        private void move(LocalPlayer player, AbstractContainerMenu menu, int from, int to, int count) {
            click(player, menu, from, 0, ContainerInput.PICKUP);

            for (int placed = 0; placed < count; placed++) {
                click(player, menu, to, 1, ContainerInput.PICKUP);
            }

            if (!menu.getCarried().isEmpty()) {
                click(player, menu, from, 0, ContainerInput.PICKUP);
            }
        }

        private void click(LocalPlayer player, AbstractContainerMenu menu, int slot, int button,
                ContainerInput input) {
            Mc.client().gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
        }

        private String pathOf(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        }

        /** A window left open, or a stack left on the cursor, is what the next tool trips over. */
        @Override
        public void cleanup(CallContext call) {
            approach.stop();

            LocalPlayer player = Mc.client().player;
            if (player != null && Windows.open() != null) {
                player.closeContainer();
            }
        }
    }
}
