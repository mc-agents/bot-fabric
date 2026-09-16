package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.event.Feeds;
import kr.junhyung.mcagents.botfabric.event.ResourcePacks;
import kr.junhyung.mcagents.botfabric.session.Disconnects;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.core.Holder;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices what the client concluded about the server's resource pack.
 *
 * <p>There is no event for it and no field to read afterwards: the client decides, tells the server
 * in a packet, and forgets. Watching what goes out is therefore the only way to know, and it is the
 * client's own verdict rather than a guess from a log line.
 *
 * <p>On the way out of {@code send}, which every packet passes through. One {@code instanceof} per
 * packet is nothing next to writing one.
 *
 * <p>The dialogs are here as well, because this is the listener that shows them. A dialog is fed
 * when the client is asked to show it, whoever asked, rather than when a screen for it is drawn: a
 * reader that waited for a screen would be asking whether the client happened to have drawn it yet.
 * It used to be fed from the server's packet alone, and a dialog a chat line's click opened was on
 * screen with nothing in the feed to say so -- an agent reads whatever dialog is up, whoever opened it.
 *
 * <p>And the reason a connection ended. The DISCONNECT event is the socket closing and carries
 * none; what the server said arrives here, on the client thread, once the client has torn the
 * world down over it -- and this is the one listener both configuration and play go through.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerMixin {

    /** The one path the server's packet and a click event both take to put a dialog on screen. */
    @Inject(method = "showDialog(Lnet/minecraft/core/Holder;Lnet/minecraft/client/gui/screens/dialog/DialogConnectionAccess;Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"))
    private void botfabric$showDialog(Holder<Dialog> dialog, DialogConnectionAccess access, Screen activeScreen,
            CallbackInfo info) {
        Feeds.dialog(dialog.value());
    }

    @Inject(method = "handleClearDialog", at = @At("TAIL"))
    private void botfabric$clearDialog(ClientboundClearDialogPacket packet, CallbackInfo info) {
        Feeds.dialogClosed();
    }

    @Inject(method = "send", at = @At("HEAD"))
    private void botfabric$resourcePack(Packet<?> packet, CallbackInfo info) {
        if (packet instanceof ServerboundResourcePackPacket pack) {
            ResourcePacks.reported(pack.action().name());
        }
    }

    @Inject(method = "onDisconnect", at = @At("TAIL"))
    private void botfabric$disconnected(DisconnectionDetails details, CallbackInfo info) {
        Disconnects.noticed(details.reason().getString());
    }
}
