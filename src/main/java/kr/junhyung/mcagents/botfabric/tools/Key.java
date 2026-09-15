package kr.junhyung.mcagents.botfabric.tools;

import java.util.Locale;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/** A key press-input names, spelt on the wire the way the catalogue lists it. */
enum Key {
    JUMP, SNEAK, SPRINT, USE, ATTACK, HOTBAR, SCROLL_UP, SCROLL_DOWN, SWAP_OFFHAND, DROP;

    static Key of(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw ToolException.badArgs("unknown key " + name);
        }
    }

    String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
