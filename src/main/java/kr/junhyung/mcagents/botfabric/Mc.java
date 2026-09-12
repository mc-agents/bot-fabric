package kr.junhyung.mcagents.botfabric;

import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class Mc {
    private Mc() {
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    /**
     * The four places the client moved between 26.1.2 and 26.2, behind names that do not move.
     *
     * <p>Screens, the boss bar overlay and the render target all changed owner in 26.2: the screen
     * went from a field on Minecraft to Gui, the overlay went one level deeper into Hud, and the
     * render target went to GameRenderer. Every tool that touched one of them broke, which is
     * fifteen errors for four renames. Behind these they are one edit each, and adding a version
     * stays the two files and five lines it is supposed to be.
     */
    public static Screen screen() {
        //? if >=26.2 {
        /*return client().gui.screen();
        *///?}
        //? if <26.2 {
        return client().screen;
        //?}
    }

    public static void setScreen(Screen screen) {
        //? if >=26.2 {
        /*client().gui.setScreen(screen);
        *///?}
        //? if <26.2 {
        client().setScreen(screen);
        //?}
    }

    public static BossHealthOverlay bossOverlay() {
        //? if >=26.2 {
        /*return client().gui.hud.getBossOverlay();
        *///?}
        //? if <26.2 {
        return client().gui.getBossOverlay();
        //?}
    }

    /** Whether the HUD is drawn. A screenshot of the world wants it off and then back as it was. */
    public static boolean hudHidden() {
        //? if >=26.2 {
        /*return client().gui.hud.isHidden();
        *///?}
        //? if <26.2 {
        return client().options.hideGui;
        //?}
    }

    public static void hideHud(boolean hidden) {
        //? if >=26.2 {
        /*if (client().gui.hud.isHidden() != hidden) {
            client().gui.hud.toggle();
        }
        *///?}
        //? if <26.2 {
        client().options.hideGui = hidden;
        //?}
    }

    public static RenderTarget renderTarget() {
        //? if >=26.2 {
        /*return client().gameRenderer.mainRenderTarget();
        *///?}
        //? if <26.2 {
        return client().getMainRenderTarget();
        //?}
    }

    public static LocalPlayer requirePlayer() {
        LocalPlayer player = client().player;
        if (player == null) {
            throw ToolException.notInGame();
        }
        return player;
    }

    public static ClientPacketListener requireConnection() {
        ClientPacketListener connection = client().getConnection();
        if (connection == null) {
            throw ToolException.notInGame();
        }
        return connection;
    }

    public static void immediate(CallContext call, ClientBody body) {
        client().submit(() -> {
            if (call.settled()) {
                return;
            }
            try {
                body.run();
            } catch (Throwable thrown) {
                call.fail(thrown);
            }
        }).exceptionally(thrown -> {
            call.fail(thrown);
            return null;
        });
    }

    @FunctionalInterface
    public interface ClientBody {
        void run() throws Exception;
    }
}
