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
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Craft something, on a nearby table when there is one and in the player's own grid otherwise.
 *
 * <p>A client does not know recipes: it knows the ones the server has unlocked for it and sent as
 * displays, which is the same reason list-recipes and get-recipe are not implemented on this kind
 * of bot. So the recipe book is the whole of what can be crafted here, and the server does the
 * placing -- the packet says which recipe, and it fills the grid out of the inventory.
 *
 * <p>One craft at a time rather than a shift-click on the result, because a shift-click makes as
 * many as the ingredients allow and the caller asked for a number.
 */
public final class CraftItemTool implements Tool {

    /** Roughly how far the other kind of bot will walk to a table before giving up on one. */
    private static final int TABLE_SEARCH = 8;

    private static final int RESULT_SLOT = 0;

    /** The grid and the result come first in the inventory menu; what the bot owns starts here. */
    private static final int BAG_START = 9;

    /** The server fills the grid when it gets round to it; a few ticks is plenty. */
    private static final int PATIENCE_TICKS = 40;

    private final TaskScheduler scheduler;

    public CraftItemTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "craft-item";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new CraftTask(parsed.string("outputItem"), parsed.integer("amount", 1)), call);
    }

    private enum Step { OPENING, PLACING, TAKING, SETTLING }

    private static final class CraftTask implements Task {
        private final String outputQuery;
        private final int amount;
        private final Approach approach = new Approach();

        private Item output;
        private RecipeDisplayEntry recipe;
        private int perCraft;
        private BlockPos table;
        private boolean clicked;
        private Step step = Step.OPENING;
        private int before;
        private int made;
        private int waited;

        private CraftTask(String outputQuery, int amount) {
            this.outputQuery = outputQuery;
            this.amount = amount;
        }

        @Override
        public String name() {
            return "craft-item";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            output = BuiltInRegistries.ITEM.getOptional(Identifier.parse(Items.namespaced(outputQuery)))
                    .orElseThrow(() -> ToolException.refused("NO_SUCH_ITEM",
                            "\"" + outputQuery + "\" is not an item in this version."));

            ContextMap context = SlotDisplayContext.fromLevel(player.level());
            recipe = known(player, context);

            if (recipe == null) {
                throw ToolException.refused("NO_RECIPE", "No recipe produces " + path(output)
                        + ", or the server has not unlocked one for this bot."
                        + " A client is only told the recipes its book holds.");
            }

            perCraft = recipe.resultItems(context).stream()
                    .filter(stack -> stack.is(output)).findFirst()
                    .map(ItemStack::getCount).orElse(1);

            if (!recipe.canCraft(inventory(player))) {
                throw ToolException.refused("MISSING_INGREDIENTS",
                        "Cannot craft " + path(output) + ". The recipe is known but the ingredients"
                                + " are not all in the inventory.");
            }

            before = held(player);
            table = Positions.nearest(player, Blocks.CRAFTING_TABLE, TABLE_SEARCH);
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            return switch (step) {
                case OPENING -> open(player);
                case PLACING -> place(player);
                case TAKING -> take(call, player);
                case SETTLING -> settle(call, player);
            };
        }

        /** No table in reach means the player's own two-by-two, which needs no window at all. */
        private boolean open(LocalPlayer player) {
            if (table == null) {
                step = Step.PLACING;
                return false;
            }
            if (!approach.reached(player, table)) {
                return false;
            }
            if (!clicked) {
                Vec3 middle = Vec3.atCenterOf(table);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
                Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(middle, Direction.UP, table, false));
                clicked = true;
                return false;
            }
            if (Windows.open() == null) {
                return false;
            }
            step = Step.PLACING;
            return false;
        }

        private boolean place(LocalPlayer player) {
            AbstractContainerMenu menu = player.containerMenu;

            Mc.requireConnection().send(
                    new ServerboundPlaceRecipePacket(menu.containerId, recipe.id(), false));

            waited = 0;
            step = Step.TAKING;
            return false;
        }

        private boolean take(CallContext call, LocalPlayer player) {
            AbstractContainerMenu menu = player.containerMenu;

            if (menu.getSlot(RESULT_SLOT).getItem().isEmpty()) {
                if (++waited > PATIENCE_TICKS) {
                    throw ToolException.refused("NOTHING_CRAFTED", "The server placed "
                            + path(output) + "'s recipe but no result appeared."
                            + (made == 0 ? "" : " " + made * perCraft + " were made before that."));
                }
                return false;
            }

            Mc.client().gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0,
                    ContainerInput.QUICK_MOVE, player);
            made++;

            if (made < amount) {
                step = Step.PLACING;
                return false;
            }

            if (table != null) {
                player.closeContainer();
            }
            waited = 0;
            step = Step.SETTLING;
            return false;
        }

        /**
         * Counted rather than predicted. The quick-move that takes the result reaches the inventory
         * a tick or two later, and a sentence that reports what the inventory actually gained cannot
         * claim a craft that did not happen -- which is a thing the other kind of bot has done.
         */
        private boolean settle(CallContext call, LocalPlayer player) {
            int gained = held(player) - before;

            if (gained <= 0) {
                if (++waited <= PATIENCE_TICKS) {
                    return false;
                }
                throw ToolException.refused("NOTHING_CRAFTED", "Nothing was crafted. The recipe for "
                        + path(output) + " was found and placed, but the inventory did not gain any.");
            }

            call.ok("Crafted " + path(output) + " x" + gained + ".");
            return true;
        }

        private int held(LocalPlayer player) {
            int total = 0;
            for (Slot slot : player.inventoryMenu.slots) {
                if (slot.index >= BAG_START && slot.getItem().is(output)) {
                    total += slot.getItem().getCount();
                }
            }
            return total;
        }

        private RecipeDisplayEntry known(LocalPlayer player, ContextMap context) {
            for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    if (entry.resultItems(context).stream().anyMatch(stack -> stack.is(output))) {
                        return entry;
                    }
                }
            }
            return null;
        }

        private static StackedItemContents inventory(LocalPlayer player) {
            StackedItemContents contents = new StackedItemContents();
            for (Slot slot : player.inventoryMenu.slots) {
                contents.accountSimpleStack(slot.getItem());
            }
            return contents;
        }

        private static String path(Item item) {
            return BuiltInRegistries.ITEM.getKey(item).getPath();
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();

            LocalPlayer player = Mc.client().player;
            if (player != null && table != null && Windows.open() != null) {
                player.closeContainer();
            }
        }
    }
}
