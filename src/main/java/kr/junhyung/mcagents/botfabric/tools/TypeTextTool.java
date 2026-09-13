package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.BookEditScreenAccessor;
import kr.junhyung.mcagents.botfabric.mixin.SignEditScreenAccessor;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Type into a text field on the screen that is open.
 *
 * <p>The half of a dialog that press-dialog-button cannot reach. A dialog's text inputs never
 * appear in the packet that opened it -- the catalogue can say a dialog has two of them and not
 * what they say -- because their values exist only in the client until a button is pressed, and
 * until something can put values there the dialogs that ask a question cannot be answered at all.
 * The sign editor comes with it, and with the sign editor comes writing on a sign in the world.
 *
 * <p>Characters go in through the same entry point the keyboard uses, so a field's length limit and
 * the characters it refuses apply here exactly as they would to a player. Setting the value
 * directly would type text into a field that a player could not have typed it into, and the bug
 * that lets that happen is one worth finding.
 */
public final class TypeTextTool extends ActionTool {

    /** Enough to walk a screen's widgets without following a cycle out of one. */
    private static final int MAX_WIDGETS = 256;

    /** The most pages a book holds, and so how far forward this will turn to reach one. */
    private static final int MAX_PAGES = 100;

    public TypeTextTool() {
        super("type-text");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        String text = parsed.string("text");
        String field = parsed.string("field", null);
        boolean replace = parsed.bool("replace", true);

        Screen screen = Mc.screen();

        if (screen == null) {
            throw ToolException.refused("NO_SCREEN", "no screen is open, so there is nothing to type into");
        }
        if (screen instanceof AbstractSignEditScreen sign) {
            return typeIntoSign(sign, text, field, replace);
        }
        if (screen instanceof BookEditScreen book) {
            return typeIntoBook(book, text, field, replace);
        }
        return typeIntoField(screen, text, field, replace);
    }

    /** One editable box, under the name a player reads beside it. */
    private record Field(String label, int index, AbstractWidget widget, boolean multiline) {

        String value() {
            return multiline ? ((MultiLineEditBox) widget).getValue() : ((EditBox) widget).getValue();
        }

        void clear() {
            if (multiline) {
                ((MultiLineEditBox) widget).setValue("");
            } else {
                ((EditBox) widget).setValue("");
            }
        }

        String describe() {
            return label == null ? "field " + index : "\"" + label + "\"";
        }
    }

    private static String typeIntoField(Screen screen, String text, String wanted, boolean replace) {
        String where = screen.getClass().getSimpleName();
        List<Field> fields = fieldsOf(screen);

        if (fields.isEmpty()) {
            throw ToolException.refused("NO_TEXT_FIELD", where + " has no text field on it."
                    + " press-dialog-button presses a button and click-slot moves an item;"
                    + " this is only for somewhere text can be written.");
        }

        Field field = choose(fields, wanted, where);
        Written written = write(screen, field, text, replace);

        return "typed into " + field.describe() + " on " + where + ", which now reads \""
                + written.value() + "\"" + refusedNote(written.refused());
    }

    /** What the field holds afterwards, and how much of the text it would not take. */
    private record Written(String value, int refused) {}

    private static Written write(Screen screen, Field field, String text, boolean replace) {
        if (!field.widget().isActive()) {
            throw ToolException.refused("FIELD_NOT_EDITABLE", field.describe() + " cannot be typed into");
        }

        screen.setFocused(field.widget());

        if (replace) {
            field.clear();
        }

        String before = field.value();
        int refused = type(field.widget(), text, field.multiline(), field.describe());
        String after = field.value();

        if (after.equals(before) && !text.isEmpty()) {
            throw ToolException.refused("NOTHING_TYPED", field.describe() + " took none of \"" + text
                    + "\": either it is read-only, or every character in it is one the client will"
                    + " not send.");
        }
        return new Written(after, refused);
    }

    private static Field choose(List<Field> fields, String wanted, String where) {
        if (wanted == null) {
            if (fields.size() == 1) {
                return fields.getFirst();
            }
            throw ToolException.refused("FIELD_NOT_NAMED", where + " has " + fields.size()
                    + " text fields and none was asked for. They are " + describe(fields) + ".");
        }

        Field found = pick(fields, wanted);

        if (found == null) {
            throw ToolException.refused("NO_SUCH_FIELD", "no text field matching \"" + wanted
                    + "\" on " + where + ". What it has: " + describe(fields) + ".");
        }
        return found;
    }

    private static Field pick(List<Field> fields, String wanted) {
        for (Field field : fields) {
            if (wanted.equalsIgnoreCase(field.label()) || wanted.equals(String.valueOf(field.index()))) {
                return field;
            }
        }
        String lowered = wanted.toLowerCase(Locale.ROOT);

        for (Field field : fields) {
            if (field.label() != null && field.label().toLowerCase(Locale.ROOT).contains(lowered)) {
                return field;
            }
        }
        return null;
    }

    private static String describe(List<Field> fields) {
        return fields.stream().map(Field::describe).collect(Collectors.joining(", "));
    }

    private static List<Field> fieldsOf(Screen screen) {
        List<Field> found = new ArrayList<>();
        collect(screen.children(), found, new String[1]);

        return found;
    }

    /**
     * Walk the screen in the order its widgets were added, carrying the last label seen.
     *
     * <p>A one-line box is built with the input's label as its own message, but a multiline one is
     * built with an empty message and its label added beside it as a separate widget. So the label
     * has to come from the widget immediately before -- and only from that one, because the widget
     * before an unlabelled box is as likely to be a paragraph of the dialog's body text.
     */
    private static void collect(List<? extends GuiEventListener> children, List<Field> out, String[] above) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractStringWidget label) {
                above[0] = label.getMessage().getString();
                continue;
            }
            if (child instanceof EditBox box) {
                String own = box.getMessage().getString();
                out.add(new Field(own.isBlank() ? above[0] : own, out.size() + 1, box, false));
            } else if (child instanceof MultiLineEditBox box) {
                out.add(new Field(above[0], out.size() + 1, box, true));
            } else if (child instanceof ContainerEventHandler container && out.size() < MAX_WIDGETS) {
                collect(container.children(), out, above);
            }
            above[0] = null;
        }
    }

    /**
     * A book is written a page at a time, so the page has to be turned to before it can be typed
     * on. Turning forward past the last page adds one, which is how a book grows to the page being
     * asked for; nothing is closed afterwards, because a book takes several calls and then a name.
     */
    private static String typeIntoBook(BookEditScreen book, String text, String wanted, boolean replace) {
        BookEditScreenAccessor editor = (BookEditScreenAccessor) book;
        int page = wanted == null ? editor.mcagents$currentPage() : pageOf(wanted);

        turnTo(editor, page);

        List<Field> fields = fieldsOf(book);

        if (fields.isEmpty()) {
            throw ToolException.refused("NO_TEXT_FIELD", "the book editor has no page to write on");
        }

        Written written = write(book, fields.getFirst(), text, replace);

        return "wrote page " + (page + 1) + " of " + editor.mcagents$pages().size()
                + ", which now reads \"" + written.value() + "\"" + refusedNote(written.refused())
                + " The book is still open: press \"Sign\" to name and finish it, or \"Done\" to keep"
                + " the draft.";
    }

    private static void turnTo(BookEditScreenAccessor editor, int page) {
        for (int step = 0; step <= MAX_PAGES && editor.mcagents$currentPage() != page; step++) {
            if (editor.mcagents$currentPage() < page) {
                editor.mcagents$pageForward();
            } else {
                editor.mcagents$pageBack();
            }
        }
        if (editor.mcagents$currentPage() != page) {
            throw ToolException.refused("BOOK_PAGE_STUCK", "the editor stopped on page "
                    + (editor.mcagents$currentPage() + 1) + " of " + editor.mcagents$pages().size()
                    + " and would not turn to page " + (page + 1));
        }
    }

    private static int pageOf(String wanted) {
        String digits = wanted.toLowerCase(Locale.ROOT).replace("page", "").trim();

        if (digits.matches("[0-9]{1,3}")) {
            int number = Integer.parseInt(digits);

            if (number >= 1 && number <= MAX_PAGES) {
                return number - 1;
            }
        }
        throw ToolException.refused("NO_SUCH_FIELD", "a book's fields are its pages, numbered 1 to "
                + MAX_PAGES + ", and \"" + wanted + "\" is not one of them");
    }

    private static String typeIntoSign(AbstractSignEditScreen sign, String text, String wanted, boolean replace) {
        SignEditScreenAccessor editor = (SignEditScreenAccessor) sign;
        String[] messages = editor.mcagents$messages();
        String[] lines = text.split("\n", -1);
        int first = wanted == null ? 0 : lineOf(wanted, messages.length);

        if (first + lines.length > messages.length) {
            throw ToolException.refused("SIGN_TOO_SHORT", "a sign has " + messages.length
                    + " lines, and this writes " + lines.length + " of them from line " + (first + 1));
        }

        int refused = 0;

        for (int at = 0; at < lines.length; at++) {
            moveTo(sign, editor, first + at, messages.length);

            if (replace) {
                for (int left = messages[first + at].length(); left > 0; left--) {
                    sign.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, 0, 0));
                }
            }
            refused += type(sign, lines[at], false, "line " + (first + at + 1));
        }

        String written = render(messages);

        /*
        Closing is what sends it: the editor holds the four lines locally and the packet goes out
        from removed(), so typing and stopping there writes a sign nobody but this client ever sees.
        */
        sign.onClose();

        /*
        The face is named because the editor opens on whichever side was clicked: a bot that walked
        round behind a sign wrote on its back, and a sentence saying only "wrote the sign" read the
        same as one that had written the front.
        */
        String face = editor.mcagents$isFrontText() ? "front" : "back";

        return "wrote the " + face + " of the sign, which now reads " + written
                + ". Closing the editor is what sends it, and it is closed" + refusedNote(refused);
    }

    private static int lineOf(String wanted, int lines) {
        String digits = wanted.toLowerCase(Locale.ROOT).replace("line", "").trim();

        if (digits.length() == 1 && digits.charAt(0) - '0' >= 1 && digits.charAt(0) - '0' <= lines) {
            return digits.charAt(0) - '1';
        }
        throw ToolException.refused("NO_SUCH_FIELD", "a sign's fields are its lines, numbered 1 to "
                + lines + ", and \"" + wanted + "\" is not one of them");
    }

    /** Down wraps around, so any line is at most three presses away from any other. */
    private static void moveTo(AbstractSignEditScreen sign, SignEditScreenAccessor editor, int line, int lines) {
        for (int step = 0; step < lines && editor.mcagents$line() != line; step++) {
            sign.keyPressed(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
        }
        if (editor.mcagents$line() != line) {
            throw ToolException.refused("SIGN_LINE_STUCK",
                    "the sign editor would not move the cursor to line " + (line + 1));
        }
    }

    private static String render(String[] messages) {
        return Arrays.stream(messages)
                .map(line -> "\"" + line + "\"")
                .collect(Collectors.joining(" / "));
    }

    /** How many characters the target would not take, which is not the same as a failure. */
    private static int type(GuiEventListener target, String text, boolean multiline, String what) {
        int refused = 0;

        for (int at = 0; at < text.length(); ) {
            int codepoint = text.codePointAt(at);
            at += Character.charCount(codepoint);

            if (codepoint == '\n') {
                if (!multiline) {
                    throw ToolException.refused("FIELD_IS_ONE_LINE",
                            what + " holds one line, and the text asked for more than one");
                }
                target.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                continue;
            }
            if (!target.charTyped(new CharacterEvent(codepoint))) {
                refused++;
            }
        }
        return refused;
    }

    private static String refusedNote(int refused) {
        if (refused == 0) {
            return ".";
        }
        return ". " + refused + (refused == 1 ? " character" : " characters")
                + " did not go in: a field takes what a player could type and no more.";
    }
}
