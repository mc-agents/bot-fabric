package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * What the open menu lets a player press, as opposed to click.
 *
 * <p>An enchanting table's offers, a stonecutter's results, a loom's patterns and a lectern's page
 * turns are drawn by the screen and sent as a button number, and the number means something
 * different on every menu: 0 is the cheapest enchantment on one and the first recipe in a list
 * that reorders with the input on another. So a caller chooses by name, and the number is looked up
 * here, from the same fields the screen draws from.
 *
 * <p>A cartography table is not in this list because it has nothing to press: its menu never
 * overrides {@code clickMenuButton}, and the result appears in slot 2 as soon as both inputs are in.
 *
 * <p>A beacon is not in it either, though its effects are drawn the same way. They are not buttons
 * the menu numbers: the screen keeps the chosen pair to itself and sends both at once, so they are
 * described apart, by {@link Beacons}, and set with set-beacon-effects.
 */
final class ContainerOptions {

    record Option(int button, String name, String label, Integer count, Integer levels, Integer lapis,
                  boolean available, boolean selected) {

        JsonObject json() {
            JsonObject entry = new JsonObject();
            entry.addProperty("button", button);
            entry.addProperty("name", name);
            entry.addProperty("label", label);
            entry.addProperty("count", count);
            entry.addProperty("levels", levels);
            entry.addProperty("lapis", lapis);
            entry.addProperty("available", available);
            entry.addProperty("selected", selected);
            return entry;
        }

        /** How a refusal names it: the words a caller would type to choose it. */
        String spoken() {
            if (label != null) {
                return "\"" + label + "\"";
            }
            return name == null ? "button " + button : "\"" + name + "\"";
        }
    }

    private ContainerOptions() {
    }

    /**
     * The menu behind the open screen, or null.
     *
     * <p>Through {@link MenuAccess} and not {@code AbstractContainerScreen}: a lectern is drawn by
     * the book screen, which is not a container screen, so a lookup through {@link Windows#open()}
     * says nothing is open while a lectern plainly is.
     */
    static AbstractContainerMenu open() {
        return Mc.screen() instanceof MenuAccess<?> access ? access.getMenu() : null;
    }

    static AbstractContainerMenu require() {
        AbstractContainerMenu menu = open();
        if (menu == null) {
            throw ToolException.refused("NO_WINDOW",
                    "No window is open. Run the command that opens the menu first, "
                            + "then use wait-for-window before pressing anything on it.");
        }
        return menu;
    }

    static String type(AbstractContainerMenu menu) {
        return Windows.type(menu);
    }

    static List<Option> of(AbstractContainerMenu menu) {
        return switch (menu) {
            case EnchantmentMenu enchanting -> enchanting(enchanting);
            case StonecutterMenu stonecutter -> stonecutter(stonecutter);
            case LoomMenu loom -> loom(loom);
            case LecternMenu lectern -> lectern(lectern);
            default -> List.of();
        };
    }

    static JsonObject describe(AbstractContainerMenu menu) {
        Screen screen = Mc.screen();
        JsonObject window = new JsonObject();

        window.addProperty("title", screen.getTitle().getString());
        window.add("titleComponent", Segments.raw(screen.getTitle()));
        window.addProperty("type", type(menu));

        JsonArray options = new JsonArray();
        for (Option option : of(menu)) {
            options.add(option.json());
        }
        window.add("options", options);

        if (menu instanceof LecternMenu lectern) {
            window.addProperty("page", lectern.getPage() + 1);
            window.addProperty("pageCount", pageCount(lectern));
        } else {
            window.add("page", JsonNull.INSTANCE);
            window.add("pageCount", JsonNull.INSTANCE);
        }

        window.add("beacon", menu instanceof BeaconMenu beacon ? Beacons.describe(beacon) : JsonNull.INSTANCE);
        return window;
    }

    /**
     * The three offers, from the numbers the server pushed into the menu.
     *
     * <p>An offer with no cost is a slot the table has nothing in, not a free one. Whether one is
     * affordable is decided the way the menu decides it before the screen sends anything, so a
     * press the server would ignore is refused here with the reason instead of vanishing.
     */
    private static List<Option> enchanting(EnchantmentMenu menu) {
        LocalPlayer player = Mc.requirePlayer();
        Registry<Enchantment> registry = Mc.client().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean hasItem = !menu.getSlot(0).getItem().isEmpty();
        List<Option> options = new ArrayList<>();

        for (int button = 0; button < menu.costs.length; button++) {
            int cost = menu.costs[button];
            if (cost <= 0) {
                continue;
            }

            Holder<Enchantment> clue = registry.get(menu.enchantClue[button]).orElse(null);
            int lapis = button + 1;
            boolean affordable = player.hasInfiniteMaterials()
                    || (menu.getGoldCount() >= lapis && player.experienceLevel >= cost);

            options.add(new Option(button,
                    clue == null ? null : id(clue),
                    clue == null ? null : Enchantment.getFullname(clue, menu.levelClue[button]).getString(),
                    null, cost, lapis, hasItem && affordable, false));
        }
        return options;
    }

    /**
     * Resolved the way the screen draws each button. The client is sent the list without its
     * recipes, so the result item on the button is all there is to name one by.
     */
    private static List<Option> stonecutter(StonecutterMenu menu) {
        ContextMap context = SlotDisplayContext.fromLevel(Mc.client().level);
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries = menu.getVisibleRecipes().entries();
        List<Option> options = new ArrayList<>(entries.size());

        for (int button = 0; button < entries.size(); button++) {
            ItemStack result = entries.get(button).recipe().optionDisplay().resolveForFirstStack(context);
            options.add(new Option(button,
                    BuiltInRegistries.ITEM.getKey(result.getItem()).getPath(),
                    result.getHoverName().getString(),
                    result.getCount(), null, null, true,
                    menu.getSelectedRecipeIndex() == button));
        }
        return options;
    }

    /**
     * Every pattern on the result is the same banner item, so a pattern is named by its own id and
     * by the name the banner's tooltip would give it in the dye that is in the loom.
     */
    private static List<Option> loom(LoomMenu menu) {
        DyeColor dye = menu.getDyeSlot().getItem().get(DataComponents.DYE);
        List<Holder<BannerPattern>> patterns = menu.getSelectablePatterns();
        List<Option> options = new ArrayList<>(patterns.size());

        for (int button = 0; button < patterns.size(); button++) {
            Holder<BannerPattern> pattern = patterns.get(button);
            options.add(new Option(button,
                    id(pattern),
                    dye == null ? null : new BannerPatternLayers.Layer(pattern, dye).description().getString(),
                    null, null, null, true,
                    menu.getSelectedBannerPatternIndex() == button));
        }
        return options;
    }

    /**
     * Turning a page is a button and not a widget click: the screen's arrows only send it, and the
     * page moves when the server says so. A jump to a page is a range of buttons starting at
     * {@link LecternMenu#BUTTON_PAGE_JUMP_RANGE_START}, which {@code page N} reaches without listing
     * a hundred of them.
     */
    private static List<Option> lectern(LecternMenu menu) {
        int page = menu.getPage();
        int pages = pageCount(menu);

        return List.of(
                new Option(LecternMenu.BUTTON_PREV_PAGE, "previous page", null, null, null, null, page > 0, false),
                new Option(LecternMenu.BUTTON_NEXT_PAGE, "next page", null, null, null, null, page < pages - 1, false),
                new Option(LecternMenu.BUTTON_TAKE_BOOK, "take book", null, null, null, null,
                        Mc.requirePlayer().mayBuild(), false));
    }

    /** The path alone, the way a slot names its item: "unbreaking", not "minecraft:unbreaking". */
    static String id(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier().getPath()).orElse(null);
    }

    static int pageCount(LecternMenu menu) {
        BookViewScreen.BookAccess book = BookViewScreen.BookAccess.fromItem(menu.getBook());
        return book == null ? 0 : book.getPageCount();
    }
}
