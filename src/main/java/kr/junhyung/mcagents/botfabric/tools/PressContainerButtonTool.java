package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import kr.junhyung.mcagents.botfabric.tools.ContainerOptions.Option;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.LecternMenu;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Press a button the open menu draws for itself: an enchantment offer, a stonecutter result, a loom
 * pattern, a lectern's page turn.
 *
 * <p>None of these is a slot or a widget. The screen works out which button the mouse is over and
 * sends its number, so click-slot and press-dialog-button both walk straight past them.
 *
 * <p>The number alone is also accepted, for a menu this does not know how to name. It goes out
 * without the local prediction the named path makes, because that prediction is the menu's own
 * reading of the number and a menu this does not recognise may read it as something else.
 */
public final class PressContainerButtonTool extends ActionTool {

    private static final Pattern PAGE = Pattern.compile("page\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    public PressContainerButtonTool() {
        super("press-container-button");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        String option = parsed.string("option", null);
        int button = parsed.integer("button", -1);

        if ((option == null) == (button < 0)) {
            throw ToolException.badArgs("give either option, by name, or button, by number");
        }

        AbstractContainerMenu menu = ContainerOptions.require();
        String type = ContainerOptions.type(menu);

        if (option == null) {
            send(menu, button);
            return "Pressed button " + button + " on " + type + ".";
        }

        if (menu instanceof LecternMenu lectern) {
            Matcher page = PAGE.matcher(option.strip());
            if (page.matches()) {
                return turnTo(lectern, Integer.parseInt(page.group(1)), type);
            }
        }

        List<Option> options = ContainerOptions.of(menu);
        Option chosen = pick(options, option, menu, type);

        if (chosen.selected()) {
            return chosen.spoken() + " is already selected on " + type + ".";
        }
        if (!chosen.available()) {
            throw ToolException.refused("OPTION_UNAVAILABLE",
                    chosen.spoken() + " cannot be pressed" + why(chosen, menu) + ".");
        }

        /*
        The screen runs the menu's own clickMenuButton before it sends, and sends only if that
        agrees. Skipping it leaves a stonecutter's selection and result slot as they were until the
        server's echo lands, so a read straight after the press describes the menu before it.
        */
        if (!(menu instanceof LecternMenu) && !menu.clickMenuButton(Mc.requirePlayer(), chosen.button())) {
            throw ToolException.refused("OPTION_UNAVAILABLE",
                    chosen.spoken() + " was refused by the menu before it was sent.");
        }
        send(menu, chosen.button());

        return "Pressed " + chosen.spoken() + " (button " + chosen.button() + ") on " + type + ".";
    }

    /**
     * A lectern names its page jumps by page rather than listing a button per page. The page moves
     * when the server answers, not here, which is how the screen's own arrows behave too.
     */
    private static String turnTo(LecternMenu lectern, int page, String type) {
        int pages = ContainerOptions.pageCount(lectern);

        if (page < 1 || page > pages) {
            throw ToolException.refused("NO_SUCH_PAGE",
                    "the book on this lectern has pages 1-" + pages + ", and no page " + page);
        }
        if (page == lectern.getPage() + 1) {
            return "The lectern is already open at page " + page + ".";
        }

        int button = LecternMenu.BUTTON_PAGE_JUMP_RANGE_START + page - 1;
        send(lectern, button);

        return "Pressed \"page " + page + "\" (button " + button + ") on " + type + ".";
    }

    private static void send(AbstractContainerMenu menu, int button) {
        Mc.client().gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /**
     * Exact before partial, and a partial that matches more than one is refused. "Sharpness" is
     * part of both "Sharpness I" and "Sharpness III", and taking the first spends the levels on
     * whichever the table happened to list first.
     */
    private static Option pick(List<Option> options, String wanted, AbstractContainerMenu menu, String type) {
        String lowered = wanted.strip().toLowerCase(Locale.ROOT);

        for (Option option : options) {
            if (lowered.equalsIgnoreCase(option.label()) || lowered.equalsIgnoreCase(option.name())) {
                return option;
            }
        }

        List<Option> partial = options.stream()
                .filter(option -> contains(option.label(), lowered) || contains(option.name(), lowered))
                .toList();

        if (partial.size() == 1) {
            return partial.getFirst();
        }
        if (partial.size() > 1) {
            throw ToolException.refused("AMBIGUOUS_OPTION",
                    "\"" + wanted + "\" matches " + list(partial) + " on " + type + "; name one of them");
        }
        throw ToolException.refused("NO_SUCH_OPTION",
                "no option matching \"" + wanted + "\" on " + type + "; " + offers(options, menu));
    }

    private static boolean contains(String text, String lowered) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowered);
    }

    private static String offers(List<Option> options, AbstractContainerMenu menu) {
        if (menu instanceof LecternMenu lectern) {
            return "it offers " + list(options) + ", and \"page 1\" to \"page "
                    + ContainerOptions.pageCount(lectern) + "\"";
        }
        if (menu instanceof CartographyTableMenu) {
            return "a cartography table has nothing to press: its result appears in slot 2 once both "
                    + "inputs are in, and click-slot takes it";
        }
        if (options.isEmpty()) {
            return "it offers nothing to press right now";
        }
        return "it offers " + list(options);
    }

    private static String list(List<Option> options) {
        return options.stream().map(Option::spoken).collect(Collectors.joining(", "));
    }

    private static String why(Option option, AbstractContainerMenu menu) {
        if (option.levels() != null) {
            if (menu.getSlot(0).getItem().isEmpty()) {
                return ": there is nothing on the table to enchant";
            }
            return ": it costs " + option.levels() + " levels and " + option.lapis()
                    + " lapis, and the bot has " + Mc.requirePlayer().experienceLevel + " levels and "
                    + menu.getSlot(1).getItem().getCount() + " lapis";
        }
        if (menu instanceof LecternMenu) {
            return option.button() == LecternMenu.BUTTON_TAKE_BOOK
                    ? ": the bot may not build here"
                    : ": the book is already at that end";
        }
        return "";
    }
}
