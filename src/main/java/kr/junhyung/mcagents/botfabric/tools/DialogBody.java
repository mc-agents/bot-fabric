package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.DialogScreenAccessor;
import kr.junhyung.mcagents.botfabric.mixin.ItemDisplayWidgetAccessor;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.font.ActiveArea;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * What can be pressed or hovered in the body of the open dialog, and where.
 *
 * <p>A server that draws a whole screen into a dialog's body -- tabs, a close mark, list rows, all
 * in its resource pack's glyphs -- leaves no button to press by label. What is there is runs of text
 * with a click or hover event on them, and items. A piece is one such run on one line, or one item.
 *
 * <p>Every rectangle comes from the glyphs the client draws, laid out by the widget's own
 * {@code visitLines}, and every point is checked against the client's own hit test: the one
 * {@code ClickableStyleFinder} runs when a widget is clicked, where the last style drawn under the
 * cursor wins. A glyph wider than its advance, a run pulled back over another by negative spacing,
 * a glyph hanging outside its widget -- each of those is something a player's click lands on or
 * misses, and the point given is one a player's click would land on, or none.
 *
 * <p>Indices follow the body's own order and do not depend on where it is scrolled, so a piece
 * keeps its index while the dialog stays the same.
 */
final class DialogBody {

    /** Enough of what is on offer to tell a misspelling from a piece that is not there. */
    private static final int MAX_LISTED = 8;

    /** The parameters a widget's lines are visited with outside a frame: no transform, no clip. */
    private static final ActiveTextCollector.Parameters UNTRANSFORMED = new ActiveTextCollector.Parameters(new Matrix3x2f());

    record Rect(int x, int y, int width, int height) {

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }

        JsonObject json() {
            JsonObject rect = new JsonObject();
            rect.addProperty("x", x);
            rect.addProperty("y", y);
            rect.addProperty("width", width);
            rect.addProperty("height", height);
            return rect;
        }
    }

    record Point(int x, int y) {

        JsonObject json() {
            JsonObject point = new JsonObject();
            point.addProperty("x", x);
            point.addProperty("y", y);
            return point;
        }
    }

    /**
     * One run of text or one item. {@code style} is null for an item. {@code point} is where a
     * player's cursor reaches it, and when nothing does {@code blocked} says why: {@code empty}
     * when the run draws nothing to press, {@code scrolled} when it is out of the body's view,
     * {@code outside} when it is drawn outside the widget that would take the press, and
     * {@code covered} when a later piece takes every point of it.
     */
    record Piece(int index, AbstractWidget widget, Style style, String text, Component component, Rect rect,
                 Point point, String blocked, ItemStack item, boolean showTooltip, List<Component> tooltip) {

        ClickEvent click() {
            return style == null ? null : style.getClickEvent();
        }

        boolean hovers() {
            return style == null ? showTooltip : style.getHoverEvent() != null;
        }

        String describe() {
            return "#" + index + " \"" + text + "\"";
        }
    }

    record Body(DialogScreen<?> screen, Rect view, double scroll, int maxScroll, List<Piece> pieces) {
    }

    /** A point to put the cursor on, and the piece it lands on there, or null for none. */
    record Target(Point point, Piece piece) {
    }

    private DialogBody() {
    }

    static DialogScreen<?> require() {
        Screen screen = Mc.screen();
        if (screen instanceof DialogScreen<?> dialog) {
            return dialog;
        }
        throw ToolException.refused("NO_DIALOG", screen == null
                ? "no dialog is open"
                : "no dialog is open; the screen is " + screen.getClass().getSimpleName());
    }

    static Body read(DialogScreen<?> screen) {
        AbstractScrollArea area = scrollArea(screen);
        Rect view = new Rect(area.getX(), area.getY(), area.getWidth(), area.getHeight());
        Font font = screen.getFont();
        int guiWidth = Mc.client().getWindow().getGuiScaledWidth();

        List<Piece> pieces = new ArrayList<>();
        for (GuiEventListener child : ((ContainerEventHandler) area).children()) {
            if (child instanceof FocusableTextWidget text) {
                for (Run run : runsOf(text, font)) {
                    pieces.add(textPiece(pieces.size(), text, font, run, view, guiWidth));
                }
            } else if (child instanceof ItemDisplayWidget item) {
                pieces.add(itemPiece(pieces.size(), item, view));
            }
        }
        return new Body(screen, view, area.scrollAmount(), area.maxScrollAmount(), List.copyOf(pieces));
    }

    static JsonObject json(Body body) {
        JsonObject dialog = new JsonObject();
        dialog.addProperty("title", body.screen().getTitle().getString());
        dialog.add("titleComponent", Segments.raw(body.screen().getTitle()));

        JsonObject gui = new JsonObject();
        gui.addProperty("width", Mc.client().getWindow().getGuiScaledWidth());
        gui.addProperty("height", Mc.client().getWindow().getGuiScaledHeight());
        dialog.add("gui", gui);

        JsonObject view = body.view().json();
        view.addProperty("scroll", body.scroll());
        view.addProperty("maxScroll", body.maxScroll());
        dialog.add("body", view);

        JsonArray pieces = new JsonArray();
        body.pieces().forEach(piece -> pieces.add(json(piece)));
        dialog.add("pieces", pieces);

        return dialog;
    }

    static JsonElement json(Piece piece) {
        if (piece == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("index", piece.index());
        json.addProperty("type", piece.style() == null ? "item" : "text");
        json.addProperty("text", piece.text());
        json.add("component", Segments.raw(piece.component()));
        json.add("rect", piece.rect().json());
        json.add("point", piece.point() == null ? JsonNull.INSTANCE : piece.point().json());
        json.addProperty("blocked", piece.blocked());
        json.add("click", click(piece.click()));
        json.add("hover", piece.hovers() ? lines(piece.tooltip()) : JsonNull.INSTANCE);
        if (piece.style() == null) {
            json.add("item", Windows.held(piece.item()));
            json.addProperty("showTooltip", piece.showTooltip());
        } else {
            json.add("item", JsonNull.INSTANCE);
            json.add("showTooltip", JsonNull.INSTANCE);
        }
        return json;
    }

    static JsonObject lines(List<Component> lines) {
        JsonArray text = new JsonArray();
        JsonArray components = new JsonArray();
        for (Component line : lines) {
            text.add(line.getString());
            components.add(Segments.raw(line));
        }
        JsonObject tooltip = new JsonObject();
        tooltip.add("lines", text);
        tooltip.add("lineComponents", components);
        return tooltip;
    }

    /**
     * The point and piece a call names: an index from read-dialog-body, a point, or part of a
     * piece's text. {@code fits} is what the call can do with a piece, and {@code wanted} the
     * events the client's hit test looks for at a point -- a press looks for a click and a hover
     * for a tooltip, and the two can land on different pieces where they overlap.
     */
    static Target target(Body body, Args args, Predicate<Piece> fits, Predicate<Style> wanted, String lacking) {
        int index = args.integer("index", -1);
        int x = args.integer("x", -1);
        int y = args.integer("y", -1);
        String match = args.string("match", null);

        if ((x < 0) != (y < 0)) {
            throw ToolException.badArgs("a point takes both x and y");
        }
        int named = (index >= 0 ? 1 : 0) + (x >= 0 ? 1 : 0) + (match != null ? 1 : 0);
        if (named != 1) {
            throw ToolException.badArgs("name the piece one way: index, x and y, or match");
        }

        if (x >= 0) {
            int width = Mc.client().getWindow().getGuiScaledWidth();
            int height = Mc.client().getWindow().getGuiScaledHeight();
            if (x >= width || y >= height) {
                throw ToolException.badArgs("(" + x + ", " + y + ") is off the screen, which is "
                        + width + "x" + height + " in GUI units");
            }
            return new Target(new Point(x, y), under(body, x, y, fits, wanted));
        }

        Piece piece = index >= 0 ? byIndex(body, index) : byText(body, match, fits, lacking);
        if (!fits.test(piece)) {
            throw ToolException.refused("NOTHING_TO_DO", piece.describe() + " " + lacking);
        }
        if (piece.point() == null) {
            throw ToolException.refused("UNREACHABLE", piece.describe() + " cannot be reached: " + why(piece));
        }
        return new Target(piece.point(), piece);
    }

    /**
     * The piece the client's hit test finds at a point, among those {@code fits} takes and carrying
     * what {@code wanted} asks for: an item is under the cursor for a hover and never for a press.
     */
    static Piece under(Body body, int x, int y, Predicate<Piece> fits, Predicate<Style> wanted) {
        if (!body.view().contains(x, y)) {
            return null;
        }
        Piece found = null;
        for (Piece piece : body.pieces()) {
            if (!fits.test(piece) || !piece.widget().isMouseOver(x + 0.5, y + 0.5) || !piece.rect().contains(x, y)) {
                continue;
            }
            if (piece.style() == null) {
                found = piece;
            } else if (piece.style().equals(styleAt((AbstractStringWidget) piece.widget(), x, y, wanted))) {
                found = piece;
            }
        }
        return found;
    }

    /** Only the two kinds a press may set off from inside the game; the rest reach the host. */
    static boolean leavesTheGame(ClickEvent event) {
        return event instanceof ClickEvent.OpenUrl || event instanceof ClickEvent.OpenFile
                || event instanceof ClickEvent.CopyToClipboard;
    }

    static JsonElement click(ClickEvent event) {
        if (event == null) {
            return JsonNull.INSTANCE;
        }
        String value = switch (event) {
            case ClickEvent.RunCommand run -> run.command();
            case ClickEvent.SuggestCommand suggest -> suggest.command();
            case ClickEvent.OpenUrl url -> url.uri().toString();
            case ClickEvent.OpenFile file -> file.path();
            case ClickEvent.ChangePage page -> Integer.toString(page.page());
            case ClickEvent.CopyToClipboard copy -> copy.value();
            case ClickEvent.ShowDialog show -> show.dialog().unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("inline");
            case ClickEvent.Custom custom -> custom.id() + custom.payload().map(payload -> " " + payload).orElse("");
            default -> "";
        };
        JsonObject click = new JsonObject();
        click.addProperty("action", event.action().getSerializedName());
        click.addProperty("value", value);
        return click;
    }

    private static Piece byIndex(Body body, int index) {
        if (index >= body.pieces().size()) {
            throw ToolException.refused("NO_SUCH_PIECE", body.pieces().isEmpty()
                    ? "the dialog's body has nothing that can be pressed or hovered"
                    : "the dialog's body has pieces 0-" + (body.pieces().size() - 1) + ", not " + index);
        }
        return body.pieces().get(index);
    }

    private static Piece byText(Body body, String match, Predicate<Piece> fits, String lacking) {
        List<Piece> offered = body.pieces().stream().filter(fits).toList();
        String wanted = match.toLowerCase(Locale.ROOT);

        for (Piece piece : offered) {
            if (piece.text().equalsIgnoreCase(match)) {
                return piece;
            }
        }
        for (Piece piece : offered) {
            if (piece.text().toLowerCase(Locale.ROOT).contains(wanted)) {
                return piece;
            }
        }
        throw ToolException.refused("NO_SUCH_PIECE", "nothing in the dialog's body reads \"" + match + "\". "
                + (offered.isEmpty()
                ? "No piece of it is one this can use, because every piece " + lacking + "."
                : "What there is: " + offered.stream().limit(MAX_LISTED)
                        .map(Piece::describe)
                        .collect(Collectors.joining(", "))
                        + (offered.size() > MAX_LISTED ? ", and " + (offered.size() - MAX_LISTED) + " more" : "")
                        + "."));
    }

    private static String why(Piece piece) {
        return switch (piece.blocked()) {
            case "empty" -> "it draws nothing a cursor could rest on";
            case "scrolled" -> "it is scrolled out of the body's view; turn the wheel over the body with use-mouse";
            case "outside" -> "every glyph of it is drawn outside the widget that would take the press";
            case "covered" -> "a piece drawn after it takes every point of it";
            default -> piece.blocked();
        };
    }

    private static AbstractScrollArea scrollArea(DialogScreen<?> screen) {
        AbstractScrollArea[] area = new AbstractScrollArea[1];
        ((DialogScreenAccessor) screen).mcagents$bodyScroll().visitChildren(child -> area[0] = (AbstractScrollArea) child);
        return area[0];
    }

    private static Piece itemPiece(int index, ItemDisplayWidget widget, Rect view) {
        ItemDisplayWidgetAccessor item = (ItemDisplayWidgetAccessor) widget;
        ItemStack stack = item.mcagents$itemStack();
        Rect rect = new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        Point centre = new Point(rect.x() + rect.width() / 2, rect.y() + rect.height() / 2);
        boolean inView = view.contains(centre.x(), centre.y());
        List<Component> tooltip = item.mcagents$tooltip() ? Screen.getTooltipFromItem(Mc.client(), stack) : List.of();

        return new Piece(index, widget, null, stack.getHoverName().getString(), stack.getHoverName(), rect,
                inView ? centre : null, inView ? null : "scrolled", stack, item.mcagents$tooltip(), tooltip);
    }

    /**
     * A run, its glyphs, and the first of them a cursor reaches the run through: the middle of the
     * run first, because that is where a player aims, then the middle of each glyph in turn.
     */
    private static Piece textPiece(int index, AbstractStringWidget widget, Font font, Run run, Rect view, int guiWidth) {
        Style bare = run.style().withClickEvent(null).withHoverEvent(null);
        Component component = Component.literal(run.text()).setStyle(bare);
        List<Component> tooltip = run.style().getHoverEvent() == null ? List.of() : tooltip(run.style().getHoverEvent(), font, guiWidth);

        if (run.areas().isEmpty()) {
            Rect rect = new Rect(run.left(), run.top(), 0, 0);
            return new Piece(index, widget, run.style(), run.text(), component, rect, null, "empty", null, false, tooltip);
        }

        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        List<Point> candidates = new ArrayList<>();
        for (ActiveArea area : run.areas()) {
            left = Math.min(left, area.activeLeft());
            top = Math.min(top, area.activeTop());
            right = Math.max(right, area.activeRight());
            bottom = Math.max(bottom, area.activeBottom());
            candidates.add(new Point((int) Math.floor((area.activeLeft() + area.activeRight()) / 2),
                    (int) Math.floor((area.activeTop() + area.activeBottom()) / 2)));
        }
        int x = (int) Math.floor(left);
        int y = (int) Math.floor(top);
        Rect rect = new Rect(x, y, (int) Math.ceil(right) - x, (int) Math.ceil(bottom) - y);
        candidates.addFirst(new Point(x + rect.width() / 2, y + rect.height() / 2));

        String blocked = "scrolled";
        Point reached = null;
        for (Point point : candidates) {
            if (!view.contains(point.x(), point.y())) {
                continue;
            }
            if (!widget.isMouseOver(point.x() + 0.5, point.y() + 0.5)) {
                blocked = "outside";
                continue;
            }
            blocked = "covered";
            if (reaches(widget, run.style(), point)) {
                reached = point;
                blocked = null;
                break;
            }
        }
        return new Piece(index, widget, run.style(), run.text(), component, rect, reached, blocked, null, false, tooltip);
    }

    /** Whether the cursor at a point lands on this style for each event the style carries. */
    private static boolean reaches(AbstractStringWidget widget, Style style, Point point) {
        if (style.getClickEvent() != null
                && !style.equals(styleAt(widget, point.x(), point.y(), candidate -> candidate.getClickEvent() != null))) {
            return false;
        }
        return style.getHoverEvent() == null
                || style.equals(styleAt(widget, point.x(), point.y(), candidate -> candidate.getHoverEvent() != null));
    }

    /**
     * The style the client's hit test finds at a point of a widget, among the ones {@code wanted}
     * takes. The last one drawn there wins, as it does when the client looks.
     */
    private static Style styleAt(AbstractStringWidget widget, int x, int y, Predicate<Style> wanted) {
        Style[] found = new Style[1];
        widget.visitLines(new Lines((text, left, top, parameters) -> {
            GuiTextRenderState state = new GuiTextRenderState(Mc.client().font, text, parameters.pose(), left, top,
                    -1, 0, true, true, parameters.scissor());
            ActiveTextCollector.findElementUnderCursor(state, x, y, style -> {
                if (wanted.test(style)) {
                    found[0] = style;
                }
            });
        }));
        return found[0];
    }

    /**
     * The pressable and hoverable runs of a widget's lines, each laid out by itself at the pen
     * position it starts at. A glyph's quad depends on nothing but that position and its style,
     * so a run prepared alone covers what it covers inside the whole line.
     */
    private static List<Run> runsOf(AbstractStringWidget widget, Font font) {
        List<Run> runs = new ArrayList<>();
        widget.visitLines(new Lines((line, left, top, parameters) -> {
            int pen = left;
            for (Run run : split(line)) {
                FormattedCharSequence sequence = FormattedCharSequence.forward(run.text(), run.style());
                if (run.style().getClickEvent() != null || run.style().getHoverEvent() != null) {
                    runs.add(run.at(pen, top, areas(font, sequence, pen, top, parameters)));
                }
                pen += font.width(sequence);
            }
        }));
        return runs;
    }

    private static List<ActiveArea> areas(Font font, FormattedCharSequence run, int x, int y,
                                          ActiveTextCollector.Parameters parameters) {
        List<ActiveArea> areas = new ArrayList<>();
        new GuiTextRenderState(font, run, parameters.pose(), x, y, -1, 0, true, true, null)
                .ensurePrepared()
                .visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptGlyph(TextRenderable.Styled glyph) {
                        keep(glyph);
                    }

                    @Override
                    public void acceptEmptyArea(EmptyArea empty) {
                        keep(empty);
                    }

                    /* A negative space has an area that runs backwards, and nothing can land in it. */
                    private void keep(ActiveArea area) {
                        if (area.activeRight() > area.activeLeft() && area.activeBottom() > area.activeTop()) {
                            areas.add(area);
                        }
                    }
                });
        return areas;
    }

    /** A line cut where its style changes. */
    private static List<Run> split(FormattedCharSequence line) {
        List<Run> runs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Style[] style = new Style[1];
        line.accept((position, next, codepoint) -> {
            if (style[0] != null && !style[0].equals(next)) {
                runs.add(new Run(style[0], text.toString(), 0, 0, List.of()));
                text.setLength(0);
            }
            style[0] = next;
            text.appendCodePoint(codepoint);
            return true;
        });
        if (style[0] != null) {
            runs.add(new Run(style[0], text.toString(), 0, 0, List.of()));
        }
        return runs;
    }

    /**
     * What a hover event draws, line by line, as {@code GuiGraphicsExtractor} draws it: text
     * wrapped to half the screen, an item's whole tooltip, and an entity only with advanced
     * tooltips on.
     */
    private static List<Component> tooltip(HoverEvent hover, Font font, int guiWidth) {
        return switch (hover) {
            case HoverEvent.ShowText text -> font.split(text.value(), Math.max(guiWidth / 2, 200)).stream()
                    .map(DialogBody::component)
                    .toList();
            case HoverEvent.ShowItem item -> Screen.getTooltipFromItem(Mc.client(), item.item().create());
            case HoverEvent.ShowEntity entity -> Mc.client().options.advancedItemTooltips
                    ? entity.entity().getTooltipLines()
                    : List.of();
            default -> List.of();
        };
    }

    private static Component component(FormattedCharSequence line) {
        MutableComponent built = Component.empty();
        for (Run run : split(line)) {
            built.append(Component.literal(run.text()).setStyle(run.style()));
        }
        return built;
    }

    /** A stretch of one line in one style, and once laid out, where it starts and what it draws. */
    private record Run(Style style, String text, int left, int top, List<ActiveArea> areas) {

        Run at(int pen, int line, List<ActiveArea> drawn) {
            return new Run(style, text, pen, line, drawn);
        }
    }

    @FunctionalInterface
    private interface LineSink {
        void line(FormattedCharSequence text, int left, int top, ActiveTextCollector.Parameters parameters);
    }

    /**
     * A widget's lines with where each one starts, the way {@code ClickableStyleFinder} takes them:
     * outside a frame, so with no transform and no clip.
     */
    private static final class Lines implements ActiveTextCollector {
        private final Font font = Mc.client().font;
        private final LineSink sink;
        private Parameters parameters = UNTRANSFORMED;

        private Lines(LineSink sink) {
            this.sink = sink;
        }

        @Override
        public Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(Parameters newParameters) {
            parameters = newParameters;
        }

        @Override
        public void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text) {
            sink.line(text, alignment.calculateLeft(anchorX, font, text), y, parameters);
        }

        @Override
        public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom,
                                    Parameters parameters) {
            defaultScrollingHelper(message, centerX, left, right, top, bottom, font.width(message), 9, parameters);
        }
    }
}
